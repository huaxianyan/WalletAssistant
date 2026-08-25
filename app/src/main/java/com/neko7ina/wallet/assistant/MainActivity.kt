package com.neko7ina.wallet.assistant

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient

class MainActivity : ComponentActivity() {
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
    private val walletClient by lazy { Pay.getClient(this) }
    private val alarmManager by lazy { getSystemService(AlarmManager::class.java) }
    private var gmailAuthorizationCallback: ((Result<String>) -> Unit)? = null
    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null
    private var exactReminderPermissionCallback: ((Boolean) -> Unit)? = null

    private val exactReminderPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        exactReminderPermissionCallback?.invoke(
            Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms(),
        )
        exactReminderPermissionCallback = null
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionCallback?.invoke(granted)
        notificationPermissionCallback = null
    }
    private val gmailAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) {
            deliverGmailAuthorization(Result.failure(IllegalStateException("Authorization canceled")))
            return@registerForActivityResult
        }
        val result = runCatching {
            authorizationClient.getAuthorizationResultFromIntent(
                requireNotNull(activityResult.data),
            ).accessToken ?: error("Missing access token")
        }
        deliverGmailAuthorization(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelWalletApp(
                requestGmailAuthorization = ::requestGmailAuthorization,
                checkGoogleWalletAvailability = ::checkGoogleWalletAvailability,
                addToGoogleWallet = ::addToGoogleWallet,
                requestNotificationPermission = ::requestNotificationPermission,
                requestExactReminderPermission = ::requestExactReminderPermission,
                setDarkSystemBars = ::setDarkSystemBars,
            )
        }
    }

    @Deprecated("Google Wallet SDK reports save results through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ADD_TO_GOOGLE_WALLET_REQUEST_CODE) return

        when (resultCode) {
            Activity.RESULT_OK -> Toast.makeText(
                this,
                "已添加至 Google Wallet",
                Toast.LENGTH_SHORT,
            ).show()

            Activity.RESULT_CANCELED -> Unit
            PayClient.SavePassesResult.SAVE_ERROR -> {
                Log.e(
                    "GoogleWallet",
                    data?.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE) ?: "Unknown save error",
                )
                showWalletError()
            }

            else -> showWalletError()
        }
    }

    private fun setDarkSystemBars(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun checkGoogleWalletAvailability(callback: (Boolean) -> Unit) {
        walletClient.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
            .addOnSuccessListener { status ->
                callback(status == PayApiAvailabilityStatus.AVAILABLE)
            }
            .addOnFailureListener { callback(false) }
    }

    private fun addToGoogleWallet(unsignedPass: String) {
        walletClient.savePasses(unsignedPass, this, ADD_TO_GOOGLE_WALLET_REQUEST_CODE)
    }

    private fun requestExactReminderPermission(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            callback(true)
            return
        }
        exactReminderPermissionCallback = callback
        exactReminderPermissionLauncher.launch(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (
            Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            callback(true)
            return
        }
        notificationPermissionCallback = callback
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestGmailAuthorization(callback: (Result<String>) -> Unit) {
        gmailAuthorizationCallback = callback
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_READONLY_SCOPE)))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        deliverGmailAuthorization(
                            Result.failure(IllegalStateException("Missing resolution")),
                        )
                    } else {
                        gmailAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    val accessToken = result.accessToken
                    if (accessToken == null) {
                        deliverGmailAuthorization(
                            Result.failure(IllegalStateException("Missing access token")),
                        )
                    } else {
                        deliverGmailAuthorization(Result.success(accessToken))
                    }
                }
            }
            .addOnFailureListener { error ->
                deliverGmailAuthorization(Result.failure(error))
            }
    }

    private fun deliverGmailAuthorization(result: Result<String>) {
        gmailAuthorizationCallback?.invoke(result)
        gmailAuthorizationCallback = null
    }

    private fun showWalletError() {
        Toast.makeText(
            this,
            "暂时无法添加，请稍后重试。",
            Toast.LENGTH_LONG,
        ).show()
    }

    private companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        const val ADD_TO_GOOGLE_WALLET_REQUEST_CODE = 1000
    }
}

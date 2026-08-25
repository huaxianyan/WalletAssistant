package com.neko7ina.wallet.assistant

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.data.SavedTravelDocument
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.core.parser.RawDocument
import com.neko7ina.wallet.assistant.wallet.GoogleWalletPassFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
        setContent {
            TravelWalletApp(
                requestGmailAuthorization = ::requestGmailAuthorization,
                checkGoogleWalletAvailability = ::checkGoogleWalletAvailability,
                addToGoogleWallet = ::addToGoogleWallet,
                requestNotificationPermission = ::requestNotificationPermission,
                requestExactReminderPermission = ::requestExactReminderPermission,
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
                "已添加到 Google Wallet",
                Toast.LENGTH_SHORT,
            ).show()

            Activity.RESULT_CANCELED -> Unit
            PayClient.SavePassesResult.SAVE_ERROR -> {
                Log.e(
                    "GoogleWallet",
                    data?.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE) ?: "Unknown save error",
                )
                Toast.makeText(
                    this,
                    "暂时无法添加，请稍后重试。",
                    Toast.LENGTH_LONG,
                ).show()
            }

            else -> Toast.makeText(
                this,
                "暂时无法添加，请稍后重试。",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun checkGoogleWalletAvailability(callback: (Boolean) -> Unit) {
        walletClient.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
            .addOnSuccessListener { status ->
                callback(status == PayApiAvailabilityStatus.AVAILABLE)
            }
            .addOnFailureListener {
                callback(false)
            }
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
                        deliverGmailAuthorization(Result.failure(IllegalStateException("Missing resolution")))
                    } else {
                        gmailAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    val accessToken = result.accessToken
                    if (accessToken == null) {
                        deliverGmailAuthorization(Result.failure(IllegalStateException("Missing access token")))
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

    private companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        const val ADD_TO_GOOGLE_WALLET_REQUEST_CODE = 1000
    }
}

private enum class WalletAvailability {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
}

private enum class Screen {
    HOME,
    GMAIL_IMPORT,
    TEXT_IMPORT,
    CONFIRM,
}

@Composable
private fun TravelWalletApp(
    requestGmailAuthorization: ((Result<String>) -> Unit) -> Unit,
    checkGoogleWalletAvailability: ((Boolean) -> Unit) -> Unit,
    addToGoogleWallet: (String) -> Unit,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    requestExactReminderPermission: ((Boolean) -> Unit) -> Unit,
    viewModel: TravelWalletViewModel = viewModel(),
) {
    val savedDocuments by viewModel.documents.collectAsStateWithLifecycle()
    val newTripsReminderEnabled by viewModel.newTripsReminderEnabled.collectAsStateWithLifecycle()
    val gmailImportState by viewModel.gmailImportState.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var emailBody by rememberSaveable { mutableStateOf("") }
    var parseError by rememberSaveable { mutableStateOf<String?>(null) }
    var parsedDocument by remember { mutableStateOf<TravelDocument?>(null) }
    var confirmationSource by rememberSaveable { mutableStateOf(Screen.TEXT_IMPORT) }
    var walletAvailability by remember { mutableStateOf(WalletAvailability.CHECKING) }
    val walletPassFactory = remember { GoogleWalletPassFactory() }
    val context = LocalContext.current

    fun changeReminder(enabled: Boolean, apply: () -> Unit) {
        if (!enabled) {
            apply()
            return
        }
        requestNotificationPermission { notificationGranted ->
            if (!notificationGranted) {
                Toast.makeText(
                    context,
                    "请允许通知后再开启乘车提醒。",
                    Toast.LENGTH_LONG,
                ).show()
                return@requestNotificationPermission
            }
            requestExactReminderPermission { exactReminderGranted ->
                if (exactReminderGranted) {
                    apply()
                } else {
                    Toast.makeText(
                        context,
                        "请允许精确提醒后再开启乘车提醒。",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkGoogleWalletAvailability { available ->
            walletAvailability = if (available) {
                WalletAvailability.AVAILABLE
            } else {
                WalletAvailability.UNAVAILABLE
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    savedDocuments = savedDocuments,
                    newTripsReminderEnabled = newTripsReminderEnabled,
                    onNewTripsReminderChange = { enabled ->
                        changeReminder(enabled) {
                            viewModel.setNewTripsReminderEnabled(enabled)
                        }
                    },
                    onReminderChange = { saved, enabled ->
                        changeReminder(enabled) {
                            viewModel.setReminderEnabled(saved, enabled)
                        }
                    },
                    onTestReminder = viewModel::scheduleReminderTest,
                    onGmailImport = {
                        screen = Screen.GMAIL_IMPORT
                        viewModel.beginGmailAuthorization()
                        requestGmailAuthorization { result ->
                            result.fold(
                                onSuccess = viewModel::loadFromGmail,
                                onFailure = { viewModel.gmailAuthorizationFailed() },
                            )
                        }
                    },
                    onTextImport = {
                        emailBody = ""
                        parseError = null
                        screen = Screen.TEXT_IMPORT
                    },
                    walletAvailability = walletAvailability,
                    onAddToGoogleWallet = { document ->
                        addToGoogleWallet(walletPassFactory.createUnsignedPass(document))
                    },
                )

                Screen.GMAIL_IMPORT -> GmailImportScreen(
                    state = gmailImportState,
                    onBack = { screen = Screen.HOME },
                    onSelect = { document ->
                        parsedDocument = document
                        confirmationSource = Screen.GMAIL_IMPORT
                        screen = Screen.CONFIRM
                    },
                )

                Screen.TEXT_IMPORT -> ImportScreen(
                    emailBody = emailBody,
                    error = parseError,
                    onBodyChange = {
                        emailBody = it
                        parseError = null
                    },
                    onUseSample = if (BuildConfig.DEBUG) {
                        {
                            emailBody = createUpcomingReminderSample()
                            parseError = null
                        }
                    } else {
                        null
                    },
                    onBack = { screen = Screen.HOME },
                    onParse = {
                        when (val result = ChinaRailwayEmailParser().parse(RawDocument(body = emailBody))) {
                            is ParseResult.Success -> {
                                parsedDocument = result.document
                                parseError = null
                                confirmationSource = Screen.TEXT_IMPORT
                                screen = Screen.CONFIRM
                            }

                            is ParseResult.Failure -> parseError = result.message
                        }
                    },
                )

                Screen.CONFIRM -> ConfirmationScreen(
                    document = requireNotNull(parsedDocument),
                    onBack = { screen = confirmationSource },
                    onSave = {
                        viewModel.save(requireNotNull(parsedDocument))
                        screen = Screen.HOME
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    savedDocuments: List<SavedTravelDocument>,
    newTripsReminderEnabled: Boolean,
    onNewTripsReminderChange: (Boolean) -> Unit,
    onReminderChange: (SavedTravelDocument, Boolean) -> Unit,
    onTestReminder: (TravelDocument) -> Unit,
    onGmailImport: () -> Unit,
    onTextImport: () -> Unit,
    walletAvailability: WalletAvailability,
    onAddToGoogleWallet: (TravelDocument) -> Unit,
) {
    if (savedDocuments.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("还没有行程", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "导入购票邮件后，行程信息会显示在这里。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            NewTripsReminderSetting(
                enabled = newTripsReminderEnabled,
                onEnabledChange = onNewTripsReminderChange,
                modifier = Modifier.padding(vertical = 20.dp),
            )
            Button(onClick = onGmailImport) {
                Text("从 Gmail 导入")
            }
            TextButton(onClick = onTextImport) {
                Text("粘贴邮件正文")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("我的行程", style = MaterialTheme.typography.headlineMedium)
            NewTripsReminderSetting(
                enabled = newTripsReminderEnabled,
                onEnabledChange = onNewTripsReminderChange,
                modifier = Modifier.padding(top = 20.dp),
            )
            savedDocuments.forEach { saved ->
                val document = saved.document
                TripCard(
                    document = document,
                    modifier = Modifier.padding(top = 24.dp),
                    walletAvailability = walletAvailability,
                    onAddToGoogleWallet = { onAddToGoogleWallet(document) },
                    reminderEnabled = saved.reminderEnabled,
                    onReminderChange = { enabled -> onReminderChange(saved, enabled) },
                    onTestReminder = if (BuildConfig.DEBUG && saved.reminderEnabled) {
                        { onTestReminder(document) }
                    } else {
                        null
                    },
                )
            }
            Button(
                onClick = onGmailImport,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp),
            ) {
                Text("从 Gmail 导入")
            }
            TextButton(
                onClick = onTextImport,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("粘贴邮件正文")
            }
        }
    }
}

@Composable
private fun NewTripsReminderSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("新行程默认提醒", style = MaterialTheme.typography.titleMedium)
            Text(
                "导入新行程时自动开启乘车提醒",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

@Composable
private fun GmailImportScreen(
    state: GmailImportState,
    onBack: () -> Unit,
    onSelect: (TravelDocument) -> Unit,
) {
    when (state) {
        GmailImportState.Idle,
        GmailImportState.Authorizing,
        GmailImportState.Loading,
        -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = if (state == GmailImportState.Loading) {
                    "正在查找购票邮件…"
                } else {
                    "正在连接 Gmail…"
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        is GmailImportState.Error -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("无法导入邮件", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = state.message,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
        }

        is GmailImportState.Success -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("选择行程", style = MaterialTheme.typography.headlineMedium)
            if (state.documents.isEmpty()) {
                Text(
                    text = "最近两年没有找到可识别的购票成功邮件，可以改用粘贴邮件正文。",
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                )
            } else {
                state.documents.forEach { document ->
                    TripCard(
                        document = document,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Button(
                        onClick = { onSelect(document) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp),
                    ) {
                        Text("选择此行程")
                    }
                }
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun ImportScreen(
    emailBody: String,
    error: String?,
    onBodyChange: (String) -> Unit,
    onUseSample: (() -> Unit)?,
    onBack: () -> Unit,
    onParse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("导入邮件正文", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "粘贴购票成功邮件的正文，解析后请核对乘车信息。",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = emailBody,
            onValueChange = onBodyChange,
            label = { Text("邮件正文") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .padding(top = 20.dp),
        )
        if (onUseSample != null) {
            TextButton(
                onClick = onUseSample,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("填入提醒测试数据")
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Button(
                onClick = onParse,
                enabled = emailBody.isNotBlank(),
            ) {
                Text("解析邮件")
            }
        }
    }
}

@Composable
private fun ConfirmationScreen(
    document: TravelDocument,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("确认行程", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "请确认邮件中的信息是否识别正确。",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        TripCard(
            document = document,
            modifier = Modifier.padding(top = 24.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回修改")
            }
            Button(onClick = onSave) {
                Text("保存行程")
            }
        }
    }
}

@Composable
private fun TripCard(
    document: TravelDocument,
    modifier: Modifier = Modifier,
    walletAvailability: WalletAvailability? = null,
    onAddToGoogleWallet: (() -> Unit)? = null,
    reminderEnabled: Boolean? = null,
    onReminderChange: ((Boolean) -> Unit)? = null,
    onTestReminder: (() -> Unit)? = null,
) {
    val segment = document.segments.first()
    val seat = segment.seatAssignments.first()
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${segment.serviceNumber} · ${segment.origin.name} → ${segment.destination.name}",
                style = MaterialTheme.typography.titleLarge,
            )
            DetailRow("出发", segment.departureTime.format(DEPARTURE_FORMAT))
            DetailRow("座位", "${seat.section} 车 ${seat.seat}")
            DetailRow("席别", seat.category)
            DetailRow("乘车人", document.travelers.first().name)
            DetailRow("订单", document.reservation.reference)
            if (reminderEnabled != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("乘车提醒", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "发车前 3 小时提醒，临近发车时显示实时状态",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = requireNotNull(onReminderChange),
                    )
                }
            }
            if (onTestReminder != null) {
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        onTestReminder()
                        Toast.makeText(
                            context,
                            "测试提醒将在 2 分钟后显示。",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("测试锁屏提醒")
                }
            }
            when (walletAvailability) {
                WalletAvailability.AVAILABLE -> GoogleWalletButton(
                    onClick = requireNotNull(onAddToGoogleWallet),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 20.dp),
                )

                WalletAvailability.UNAVAILABLE -> Text(
                    text = "请安装或更新 Google Wallet 后重试。",
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )

                WalletAvailability.CHECKING,
                null,
                -> Unit
            }
        }
    }
}

@Composable
private fun GoogleWalletButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(min = 270.dp)
            .height(49.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1F1F1F))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.add_to_google_wallet_button_foreground),
            contentDescription = "添加到 Google Wallet",
            modifier = Modifier
                .width(227.dp)
                .height(26.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Gray)
        Text(value)
    }
}

private val DEPARTURE_FORMAT = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 HH:mm")
private val SAMPLE_EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
private val SAMPLE_EMAIL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH:mm")
private val SAMPLE_EMAIL_ORDER_FORMAT = DateTimeFormatter.ofPattern("MMddHHmm")
private val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

private fun createUpcomingReminderSample(): String {
    val departure = ZonedDateTime.now(SHANGHAI_ZONE)
        .plusHours(25)
        .withSecond(0)
        .withNano(0)
    val reservationReference = "E9${departure.format(SAMPLE_EMAIL_ORDER_FORMAT)}"
    return """
        尊敬的 测试乘客先生：
        您好！
        您于${departure.minusDays(1).format(SAMPLE_EMAIL_DATE_FORMAT)}在中国铁路客户服务中心网站(12306.cn)成功购买了1张车票，票款共计120.00元，订单号码 $reservationReference。所购车票信息如下：
        1.测试乘客，${departure.format(SAMPLE_EMAIL_DATE_TIME_FORMAT)}开，镇江站-上海站，G7229次列车，2车17C号，二等座，成人票，票价120.0元，电子客票。
    """.trimIndent()
}

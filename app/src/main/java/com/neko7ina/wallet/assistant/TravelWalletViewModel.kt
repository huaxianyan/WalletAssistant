package com.neko7ina.wallet.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.data.SavedTravelDocument
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import com.neko7ina.wallet.assistant.gmail.GmailAccessException
import com.neko7ina.wallet.assistant.gmail.GmailClient
import com.neko7ina.wallet.assistant.reminder.TripReminderScheduler
import com.neko7ina.wallet.assistant.settings.AppPreferences
import com.neko7ina.wallet.assistant.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TravelWalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TravelDocumentRepository(
        TravelWalletDatabase.getInstance(application).travelDocumentDao(),
    )
    private val gmailClient = GmailClient()
    private val railwayParser = ChinaRailwayEmailParser()
    private val appPreferences = AppPreferences(application)
    private val reminderScheduler = TripReminderScheduler(application)
    private val mutableGmailImportState = MutableStateFlow<GmailImportState>(GmailImportState.Idle)
    private val mutableNewTripsReminderEnabled = MutableStateFlow(
        appPreferences.newTripsReminderEnabled,
    )
    private val mutableIgnoreDepartedTripsOnImport = MutableStateFlow(
        appPreferences.ignoreDepartedTripsOnImport,
    )
    private val mutableDepartureReminderMinutes = MutableStateFlow(
        appPreferences.departureReminderMinutes,
    )
    private val mutableLiveStatusMinutes = MutableStateFlow(appPreferences.liveStatusMinutes)
    private val mutableGoogleWalletActionVisible = MutableStateFlow(
        appPreferences.googleWalletActionVisible,
    )
    private val mutableThemeMode = MutableStateFlow(appPreferences.themeMode)

    val gmailImportState = mutableGmailImportState.asStateFlow()
    val newTripsReminderEnabled = mutableNewTripsReminderEnabled.asStateFlow()
    val ignoreDepartedTripsOnImport = mutableIgnoreDepartedTripsOnImport.asStateFlow()
    val departureReminderMinutes = mutableDepartureReminderMinutes.asStateFlow()
    val liveStatusMinutes = mutableLiveStatusMinutes.asStateFlow()
    val googleWalletActionVisible = mutableGoogleWalletActionVisible.asStateFlow()
    val themeMode = mutableThemeMode.asStateFlow()
    val documents = repository.observeDocuments().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val archivedDocuments = repository.observeArchivedDocuments().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun beginGmailAuthorization() {
        mutableGmailImportState.value = GmailImportState.Authorizing
    }

    fun loadFromGmail(accessToken: String) {
        mutableGmailImportState.value = GmailImportState.Loading
        viewModelScope.launch {
            try {
                val documents = gmailClient.searchMessages(
                    accessToken = accessToken,
                    query = GMAIL_RAILWAY_QUERY,
                ).mapNotNull { rawDocument ->
                    when (val result = railwayParser.parse(rawDocument)) {
                        is ParseResult.Success -> result.document.takeUnless { document ->
                            appPreferences.ignoreDepartedTripsOnImport && document.hasDeparted()
                        }
                        is ParseResult.Failure -> null
                    }
                }
                mutableGmailImportState.value = GmailImportState.Success(documents)
            } catch (error: GmailAccessException) {
                mutableGmailImportState.value = GmailImportState.Error(requireNotNull(error.message))
            } catch (_: Exception) {
                mutableGmailImportState.value = GmailImportState.Error(
                    "暂时无法读取 Gmail，请检查网络后重试。",
                )
            }
        }
    }

    fun gmailAuthorizationFailed() {
        mutableGmailImportState.value = GmailImportState.Error(
            "暂时无法连接 Gmail，请改用粘贴邮件正文导入。",
        )
    }

    fun save(document: TravelDocument) {
        viewModelScope.launch {
            val saved = repository.save(
                document = document,
                defaultReminderEnabled = appPreferences.newTripsReminderEnabled,
            )
            if (saved.reminderEnabled) reminderScheduler.schedule(saved.document)
        }
    }

    fun setNewTripsReminderEnabled(enabled: Boolean) {
        appPreferences.newTripsReminderEnabled = enabled
        mutableNewTripsReminderEnabled.value = enabled
    }

    fun setIgnoreDepartedTripsOnImport(ignore: Boolean) {
        appPreferences.ignoreDepartedTripsOnImport = ignore
        mutableIgnoreDepartedTripsOnImport.value = ignore
    }

    fun setDepartureReminderMinutes(minutes: Int) {
        appPreferences.departureReminderMinutes = minutes
        mutableDepartureReminderMinutes.value = appPreferences.departureReminderMinutes
        rescheduleEnabledDocuments()
    }

    fun setLiveStatusMinutes(minutes: Int) {
        appPreferences.liveStatusMinutes = minutes
        mutableLiveStatusMinutes.value = appPreferences.liveStatusMinutes
        rescheduleEnabledDocuments()
    }

    fun setGoogleWalletActionVisible(visible: Boolean) {
        appPreferences.googleWalletActionVisible = visible
        mutableGoogleWalletActionVisible.value = visible
    }

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.themeMode = mode
        mutableThemeMode.value = mode
    }

    fun setReminderEnabled(saved: SavedTravelDocument, enabled: Boolean) {
        viewModelScope.launch {
            val updated = repository.setReminderEnabled(saved.document.stableId(), enabled) ?: return@launch
            if (enabled) {
                reminderScheduler.schedule(updated.document)
            } else {
                reminderScheduler.cancel(updated.document.stableId())
            }
        }
    }

    fun setArchived(saved: SavedTravelDocument, archived: Boolean) {
        viewModelScope.launch {
            repository.setArchived(saved.document.stableId(), archived) ?: return@launch
            if (archived) reminderScheduler.cancel(saved.document.stableId())
        }
    }

    fun scheduleReminderTest(document: TravelDocument) {
        reminderScheduler.scheduleDebugSequence(document)
    }

    private fun rescheduleEnabledDocuments() {
        viewModelScope.launch {
            repository.getReminderEnabledDocuments().forEach(reminderScheduler::schedule)
        }
    }

    private companion object {
        const val GMAIL_RAILWAY_QUERY = "\"成功购买了\" \"12306.cn\" newer_than:2y"
    }
}

sealed interface GmailImportState {
    data object Idle : GmailImportState
    data object Authorizing : GmailImportState
    data object Loading : GmailImportState
    data class Success(val documents: List<TravelDocument>) : GmailImportState
    data class Error(val message: String) : GmailImportState
}

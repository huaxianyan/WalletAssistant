package com.neko7ina.wallet.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neko7ina.wallet.assistant.archive.TripAutoArchiveScheduler
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.data.SavedTravelDocument
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import com.neko7ina.wallet.assistant.email.EmailAccountStore
import com.neko7ina.wallet.assistant.email.ImapAccessException
import com.neko7ina.wallet.assistant.email.ImapAccountConfig
import com.neko7ina.wallet.assistant.email.ImapClient
import com.neko7ina.wallet.assistant.email.ImapSyncCheckpoint
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
    private val imapClient = ImapClient()
    private val emailAccountStore = EmailAccountStore(application)
    private val railwayParser = ChinaRailwayEmailParser()
    private val appPreferences = AppPreferences(application)
    private val reminderScheduler = TripReminderScheduler(application)
    private val autoArchiveScheduler = TripAutoArchiveScheduler(application)
    private val mutableEmailImportState = MutableStateFlow<EmailImportState>(EmailImportState.Idle)
    private val mutableEmailAccountSummary = MutableStateFlow(emailAccountStore.summary())
    private val mutableEmailAccountTestState =
        MutableStateFlow<EmailAccountTestState>(EmailAccountTestState.Idle)
    private val mutableNewTripsReminderEnabled = MutableStateFlow(
        appPreferences.newTripsReminderEnabled,
    )
    private val mutableIgnoreDepartedTripsOnImport = MutableStateFlow(
        appPreferences.ignoreDepartedTripsOnImport,
    )
    private val mutableAutoArchiveDepartedTrips = MutableStateFlow(
        appPreferences.autoArchiveDepartedTrips,
    )
    private val mutableDepartureReminderMinutes = MutableStateFlow(
        appPreferences.departureReminderMinutes,
    )
    private val mutableLiveStatusMinutes = MutableStateFlow(appPreferences.liveStatusMinutes)
    private val mutableGoogleWalletActionVisible = MutableStateFlow(
        appPreferences.googleWalletActionVisible,
    )
    private val mutableThemeMode = MutableStateFlow(appPreferences.themeMode)

    val emailImportState = mutableEmailImportState.asStateFlow()
    val emailAccountSummary = mutableEmailAccountSummary.asStateFlow()
    val emailAccountTestState = mutableEmailAccountTestState.asStateFlow()
    val newTripsReminderEnabled = mutableNewTripsReminderEnabled.asStateFlow()
    val ignoreDepartedTripsOnImport = mutableIgnoreDepartedTripsOnImport.asStateFlow()
    val autoArchiveDepartedTrips = mutableAutoArchiveDepartedTrips.asStateFlow()
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

    init {
        viewModelScope.launch { autoArchiveScheduler.reconcile() }
    }

    fun loadFromEmail() {
        val account = emailAccountStore.load()
        if (account == null) {
            mutableEmailImportState.value = EmailImportState.Error(
                "请先在设置中配置邮箱。",
            )
            return
        }
        mutableEmailImportState.value = EmailImportState.Loading
        viewModelScope.launch {
            try {
                val ignoreDepartedTrips = appPreferences.ignoreDepartedTripsOnImport
                val checkpoint = appPreferences.imapSyncCheckpoint(
                    accountFingerprint = account.fingerprint,
                    parserVersion = railwayParser.version,
                    ignoreDepartedTrips = ignoreDepartedTrips,
                )
                val searchResult = imapClient.searchRailwayMessages(
                    config = account,
                    checkpoint = checkpoint,
                )
                if (searchResult.messages.isEmpty()) {
                    appPreferences.saveImapSyncCheckpoint(
                        accountFingerprint = account.fingerprint,
                        parserVersion = railwayParser.version,
                        ignoreDepartedTrips = ignoreDepartedTrips,
                        checkpoint = searchResult.nextCheckpoint,
                    )
                    mutableEmailImportState.value = EmailImportState.Success(
                        documents = emptyList(),
                        warnings = listOf("没有发现新的铁路订单通知。"),
                        pendingCheckpoint = null,
                        accountFingerprint = account.fingerprint,
                        ignoreDepartedTrips = ignoreDepartedTrips,
                    )
                    return@launch
                }
                when (
                    val result = railwayParser.parseAll(
                        documents = searchResult.messages,
                        baselineDocuments = if (searchResult.fullScan) {
                            emptyList()
                        } else {
                            repository.allDocuments()
                        },
                    )
                ) {
                    is ParseResult.Success -> {
                        val documents = result.documents.filterNot { document ->
                            ignoreDepartedTrips && document.hasDeparted()
                        }
                        if (documents.isEmpty()) {
                            appPreferences.saveImapSyncCheckpoint(
                                accountFingerprint = account.fingerprint,
                                parserVersion = railwayParser.version,
                                ignoreDepartedTrips = ignoreDepartedTrips,
                                checkpoint = searchResult.nextCheckpoint,
                            )
                        }
                        mutableEmailImportState.value = EmailImportState.Success(
                            documents = documents,
                            warnings = result.warnings,
                            pendingCheckpoint = searchResult.nextCheckpoint.takeIf {
                                documents.isNotEmpty()
                            },
                            accountFingerprint = account.fingerprint,
                            ignoreDepartedTrips = ignoreDepartedTrips,
                        )
                    }

                    is ParseResult.Failure -> {
                        appPreferences.saveImapSyncCheckpoint(
                            accountFingerprint = account.fingerprint,
                            parserVersion = railwayParser.version,
                            ignoreDepartedTrips = ignoreDepartedTrips,
                            checkpoint = searchResult.nextCheckpoint,
                        )
                        mutableEmailImportState.value = EmailImportState.Success(
                            documents = emptyList(),
                            warnings = listOf("本次同步没有发现新的可识别行程。"),
                            pendingCheckpoint = null,
                            accountFingerprint = account.fingerprint,
                            ignoreDepartedTrips = ignoreDepartedTrips,
                        )
                    }
                }
            } catch (error: ImapAccessException) {
                mutableEmailImportState.value = EmailImportState.Error(requireNotNull(error.message))
            } catch (_: Exception) {
                mutableEmailImportState.value = EmailImportState.Error(
                    "暂时无法读取邮箱，请检查网络和邮箱配置后重试。",
                )
            }
        }
    }

    fun testAndSaveEmailAccount(config: ImapAccountConfig) {
        mutableEmailAccountTestState.value = EmailAccountTestState.Testing
        viewModelScope.launch {
            try {
                imapClient.testConnection(config)
                emailAccountStore.load()?.takeIf { it.fingerprint != config.fingerprint }?.let {
                    appPreferences.clearImapSyncCheckpoints(it.fingerprint)
                }
                emailAccountStore.save(config)
                mutableEmailAccountSummary.value = emailAccountStore.summary()
                mutableEmailAccountTestState.value = EmailAccountTestState.Success
            } catch (error: ImapAccessException) {
                mutableEmailAccountTestState.value = EmailAccountTestState.Error(
                    requireNotNull(error.message),
                )
            }
        }
    }

    fun deleteEmailAccount() {
        emailAccountStore.load()?.let {
            appPreferences.clearImapSyncCheckpoints(it.fingerprint)
        }
        emailAccountStore.delete()
        mutableEmailAccountSummary.value = null
        mutableEmailAccountTestState.value = EmailAccountTestState.Idle
    }

    fun resetEmailAccountTestState() {
        mutableEmailAccountTestState.value = EmailAccountTestState.Idle
    }

    fun save(documents: List<TravelDocument>, emailImport: Boolean = false) {
        viewModelScope.launch {
            repository.getByReservations(documents).forEach { existing ->
                val id = existing.document.stableId()
                reminderScheduler.cancel(id)
                autoArchiveScheduler.cancel(id)
            }
            val savedDocuments = repository.replaceReservations(
                documents = documents,
                defaultReminderEnabled = appPreferences.newTripsReminderEnabled,
            )
            savedDocuments.forEach { saved ->
                if (
                    saved.document.status == TravelDocumentStatus.CONFIRMED &&
                    saved.reminderEnabled
                ) {
                    reminderScheduler.schedule(saved.document)
                }
                if (saved.document.status == TravelDocumentStatus.CONFIRMED) {
                    autoArchiveScheduler.scheduleOrArchive(saved)
                }
            }
            if (emailImport) {
                val emailState = mutableEmailImportState.value as? EmailImportState.Success
                    ?: return@launch
                val checkpoint = emailState.pendingCheckpoint ?: return@launch
                appPreferences.saveImapSyncCheckpoint(
                    accountFingerprint = emailState.accountFingerprint,
                    parserVersion = railwayParser.version,
                    ignoreDepartedTrips = emailState.ignoreDepartedTrips,
                    checkpoint = checkpoint,
                )
            }
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

    fun setAutoArchiveDepartedTrips(enabled: Boolean) {
        appPreferences.autoArchiveDepartedTrips = enabled
        mutableAutoArchiveDepartedTrips.value = enabled
        viewModelScope.launch { autoArchiveScheduler.reconcile() }
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
        if (saved.document.status != TravelDocumentStatus.CONFIRMED) return
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
        if (!archived && saved.document.status != TravelDocumentStatus.CONFIRMED) return
        viewModelScope.launch {
            val documentId = saved.document.stableId()
            val updated = repository.setArchived(documentId, archived) ?: return@launch
            if (archived) {
                reminderScheduler.cancel(documentId)
                autoArchiveScheduler.cancel(documentId)
            } else {
                autoArchiveScheduler.scheduleOrArchive(updated)
            }
        }
    }

    fun delete(saved: SavedTravelDocument) {
        viewModelScope.launch {
            val documentId = saved.document.stableId()
            reminderScheduler.cancel(documentId)
            autoArchiveScheduler.cancel(documentId)
            repository.delete(documentId)
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

}

sealed interface EmailImportState {
    data object Idle : EmailImportState
    data object Loading : EmailImportState
    data class Success(
        val documents: List<TravelDocument>,
        val warnings: List<String>,
        val pendingCheckpoint: ImapSyncCheckpoint?,
        val accountFingerprint: String,
        val ignoreDepartedTrips: Boolean,
    ) : EmailImportState
    data class Error(val message: String) : EmailImportState
}

sealed interface EmailAccountTestState {
    data object Idle : EmailAccountTestState
    data object Testing : EmailAccountTestState
    data object Success : EmailAccountTestState
    data class Error(val message: String) : EmailAccountTestState
}

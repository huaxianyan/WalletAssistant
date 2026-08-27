package com.neko7ina.wallet.assistant.email

import android.content.Context
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.data.PendingEmailImport
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import com.neko7ina.wallet.assistant.hasDeparted
import com.neko7ina.wallet.assistant.settings.AppPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmailSyncCoordinator(context: Context) {
    private val applicationContext = context.applicationContext
    private val repository = TravelDocumentRepository(
        TravelWalletDatabase.getInstance(applicationContext).travelDocumentDao(),
    )
    private val accountStore = EmailAccountStore(applicationContext)
    private val preferences = AppPreferences(applicationContext)
    private val parser = ChinaRailwayEmailParser()
    private val imapClient = ImapClient()

    fun canEnableAutomaticSync(): Boolean {
        val account = accountStore.load() ?: return false
        return preferences.imapSyncCheckpoint(
            accountFingerprint = account.fingerprint,
            parserVersion = parser.version,
        ) != null
    }

    suspend fun sync(
        requireExistingCheckpoint: Boolean,
        includeHistoricalTrips: Boolean = false,
        onProgress: (EmailSyncProgress) -> Unit = {},
    ): EmailSyncOutcome = syncMutex.withLock {
        repository.pendingEmailImport()?.let { return EmailSyncOutcome.PendingConfirmation(it) }
        val account = accountStore.load() ?: return EmailSyncOutcome.NoAccount
        val checkpoint = preferences.imapSyncCheckpoint(
            accountFingerprint = account.fingerprint,
            parserVersion = parser.version,
        )
        if (requireExistingCheckpoint && checkpoint == null) {
            return EmailSyncOutcome.InitialSyncRequired
        }
        val searchResult = try {
            imapClient.searchRailwayMessages(
                config = account,
                checkpoint = checkpoint,
                allowFullScan = !requireExistingCheckpoint,
                onProgress = { progress ->
                    onProgress(
                        when (progress) {
                            ImapSyncProgress.Connecting -> EmailSyncProgress.Connecting
                            ImapSyncProgress.CheckingMessages -> EmailSyncProgress.CheckingMessages
                            is ImapSyncProgress.ReadingRailwayMessage ->
                                EmailSyncProgress.ReadingRailwayMessages(
                                    completed = progress.completed,
                                    total = progress.total,
                                )
                        },
                    )
                },
            )
        } catch (_: ImapFullSyncRequiredException) {
            preferences.clearImapSyncCheckpoints(account.fingerprint)
            return EmailSyncOutcome.InitialSyncRequired
        }
        if (searchResult.messages.isEmpty()) {
            saveCheckpoint(account.fingerprint, searchResult.nextCheckpoint)
            return EmailSyncOutcome.NoNewRailwayMessages
        }

        onProgress(EmailSyncProgress.OrganizingTrips)
        return when (
            val result = parser.parseAll(
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
                    searchResult.fullScan && !includeHistoricalTrips && document.hasDeparted()
                }
                if (documents.isEmpty()) {
                    saveCheckpoint(account.fingerprint, searchResult.nextCheckpoint)
                    EmailSyncOutcome.NoRecognizableTrips
                } else {
                    val pending = PendingEmailImport(
                        documents = documents,
                        warnings = result.warnings,
                        checkpoint = searchResult.nextCheckpoint,
                        accountFingerprint = account.fingerprint,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    )
                    repository.savePendingEmailImport(pending)
                    EmailSyncOutcome.PendingConfirmation(pending)
                }
            }

            is ParseResult.Failure -> {
                saveCheckpoint(account.fingerprint, searchResult.nextCheckpoint)
                EmailSyncOutcome.NoRecognizableTrips
            }
        }
    }

    suspend fun completePendingImport(pending: PendingEmailImport) = syncMutex.withLock {
        saveCheckpoint(
            accountFingerprint = pending.accountFingerprint,
            checkpoint = pending.checkpoint,
        )
        repository.deletePendingEmailImport()
    }

    private fun saveCheckpoint(
        accountFingerprint: String,
        checkpoint: ImapSyncCheckpoint,
    ) {
        preferences.saveImapSyncCheckpoint(
            accountFingerprint = accountFingerprint,
            parserVersion = parser.version,
            checkpoint = checkpoint,
        )
    }

    private companion object {
        val syncMutex = Mutex()
    }
}

sealed interface EmailSyncProgress {
    data object Connecting : EmailSyncProgress
    data object CheckingMessages : EmailSyncProgress
    data class ReadingRailwayMessages(val completed: Int, val total: Int) : EmailSyncProgress
    data object OrganizingTrips : EmailSyncProgress
}

sealed interface EmailSyncOutcome {
    data object NoAccount : EmailSyncOutcome
    data object InitialSyncRequired : EmailSyncOutcome
    data object NoNewRailwayMessages : EmailSyncOutcome
    data object NoRecognizableTrips : EmailSyncOutcome
    data class PendingConfirmation(val pending: PendingEmailImport) : EmailSyncOutcome
}

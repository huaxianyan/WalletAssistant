package com.neko7ina.wallet.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import com.neko7ina.wallet.assistant.gmail.GmailAccessException
import com.neko7ina.wallet.assistant.gmail.GmailClient
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
    private val mutableGmailImportState = MutableStateFlow<GmailImportState>(GmailImportState.Idle)

    val gmailImportState = mutableGmailImportState.asStateFlow()
    val documents = repository.observeDocuments().stateIn(
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
                        is ParseResult.Success -> result.document
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
            repository.save(document)
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

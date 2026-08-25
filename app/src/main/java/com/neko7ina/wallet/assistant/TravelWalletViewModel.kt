package com.neko7ina.wallet.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TravelWalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TravelDocumentRepository(
        TravelWalletDatabase.getInstance(application).travelDocumentDao(),
    )

    val documents = repository.observeDocuments().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun save(document: TravelDocument) {
        viewModelScope.launch {
            repository.save(document)
        }
    }
}

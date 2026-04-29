package com.example.safefnow2.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.data.repository.HistoryRepository
import com.example.safefnow2.ui.common.UiState
import com.example.safefnow2.util.ConnectivityObserver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HistoryRepository(app)
    private val isOnline = ConnectivityObserver(app).isOnlineFlow()

    fun uiState(userId: String): LiveData<UiState<List<HistoryItemUi>>> {
        val out = MediatorLiveData<UiState<List<HistoryItemUi>>>()
        out.value = UiState.Loading

        val source = repo.history(userId).asLiveData()
        out.addSource(source) { list ->
            val ui = list.map { HistoryItemUi.from(it, userId) }
            out.value = if (ui.isEmpty()) UiState.Empty else UiState.Success(ui)
        }

        viewModelScope.launch {
            if (userId.isBlank()) {
                out.value = UiState.Empty
                return@launch
            }
            val online = isOnline.first()
            if (!online) return@launch
            val result = repo.syncNow(userId)
            if (result.isFailure) {
                out.value = UiState.Error("Loading error")
            }
        }

        return out
    }
}


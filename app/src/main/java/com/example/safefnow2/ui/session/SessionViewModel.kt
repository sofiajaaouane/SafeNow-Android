package com.example.safefnow2.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.ui.common.Event
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.SessionGuard
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val isOnline = ConnectivityObserver(app).isOnlineFlow()

    private val _forceLogout = MutableLiveData<Event<Unit>>()
    val forceLogout: LiveData<Event<Unit>> = _forceLogout

    fun validateSessionAndLogoutIfDeleted() {
        viewModelScope.launch {
            val userId = SessionManager.getCurrentUserId(getApplication()).orEmpty()
            if (userId.isEmpty()) return@launch
            val online = isOnline.first()
            if (!online) return@launch
            if (SessionGuard.isSessionUserDeleted(getApplication())) {
                SessionManager.clear(getApplication())
                _forceLogout.value = Event(Unit)
            }
        }
    }

    suspend fun isSessionDeletedOnline(): Boolean {
        val userId = SessionManager.getCurrentUserId(getApplication()).orEmpty()
        if (userId.isEmpty()) return false
        val online = isOnline.first()
        if (!online) return false
        return SessionGuard.isSessionUserDeleted(getApplication())
    }
}


package com.example.safefnow2.ui.sos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.data.remote.SosRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class SosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SosRepository(application)

    private val _events = MutableSharedFlow<SosUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SosUiEvent> = _events

    fun syncDeviceRegistration(displayName: String, phone: String, appUserId: String) {
        viewModelScope.launch {
            try {
                repository.syncMyDeviceToCloud(displayName, phone, appUserId)
            } catch (_: Exception) {
            }
        }
    }

    fun sendSos(displayName: String) {
        viewModelScope.launch {
            try {
                repository.sendSosToPeer(displayName)
                _events.emit(SosUiEvent.Sent)
            } catch (e: Exception) {
                if (e is IllegalStateException && e.message == "peer_missing") {
                    _events.emit(SosUiEvent.PeerMissing)
                } else {
                    _events.emit(SosUiEvent.Error(e.message ?: ""))
                }
            }
        }
    }

    fun sendSosToContactPhone(contactPhone: String, senderDisplayName: String, contactDisplayName: String) {
        viewModelScope.launch {
            try {
                repository.sendSosToContactPhone(contactPhone, senderDisplayName, contactDisplayName)
                _events.emit(SosUiEvent.Sent)
            } catch (e: Exception) {
                if (e is IllegalStateException && e.message == "contact_device_unknown") {
                    _events.emit(SosUiEvent.Error("contact_device_unknown"))
                } else {
                    _events.emit(SosUiEvent.Error(e.message ?: ""))
                }
            }
        }
    }
}

sealed class SosUiEvent {
    data object Sent : SosUiEvent()
    data object PeerMissing : SosUiEvent()
    data class Error(val message: String) : SosUiEvent()
}

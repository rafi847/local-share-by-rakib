package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ServerConfig
import com.example.model.SharedFile
import com.example.server.ServerManager
import com.example.utils.FileUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

class LocalDropViewModel(application: Application) : AndroidViewModel(application) {

    val uiState = ServerManager.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ServerManager.state.value
    )

    val events = ServerManager.events

    init {
        ServerManager.init(application)
    }

    fun toggleServer() {
        if (uiState.value.isRunning) {
            ServerManager.stopServer()
        } else {
            ServerManager.startServer()
        }
    }

    fun refreshNetwork() {
        ServerManager.refreshNetworkInfo()
    }

    fun refreshFiles() {
        ServerManager.refreshSharedFiles()
    }

    fun setPort(port: Int) {
        val current = uiState.value.config
        if (port in 1024..65535 && port != current.port) {
            val wasRunning = uiState.value.isRunning
            if (wasRunning) ServerManager.stopServer()
            ServerManager.updateConfig(current.copy(port = port))
            if (wasRunning) ServerManager.startServer()
        }
    }

    fun setPinRequired(required: Boolean) {
        val current = uiState.value.config
        ServerManager.updateConfig(current.copy(pinRequired = required))
    }

    fun regeneratePin() {
        val newPin = String.format("%04d", Random.nextInt(1000, 9999))
        val current = uiState.value.config
        ServerManager.updateConfig(current.copy(pinCode = newPin))
    }

    fun setPinCode(pin: String) {
        if (pin.length in 4..8) {
            val current = uiState.value.config
            ServerManager.updateConfig(current.copy(pinCode = pin))
        }
    }

    fun clearTransferHistory() {
        ServerManager.clearHistory()
    }

    fun deleteFile(file: SharedFile) {
        ServerManager.deleteSharedFile(file)
    }

    fun copyServerUrlToClipboard() {
        val url = uiState.value.serverUrl ?: return
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LocalDrop URL", url)
        clipboard.setPrimaryClip(clip)
    }

    fun copyPinToClipboard() {
        val pin = uiState.value.config.pinCode
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LocalDrop PIN", pin)
        clipboard.setPrimaryClip(clip)
    }

    fun openInBrowser() {
        val url = uiState.value.serverUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {}
    }

    fun addFilesFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val targetDir = FileUtils.getStorageDir(ctx)
            for (uri in uris) {
                FileUtils.copyUriToLocalDrop(ctx, uri, targetDir)
            }
            ServerManager.refreshSharedFiles()
        }
    }
}

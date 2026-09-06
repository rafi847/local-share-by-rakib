package com.example.server

import android.content.Context
import android.content.Intent
import com.example.model.ClientInfo
import com.example.model.ServerConfig
import com.example.model.SharedFile
import com.example.model.TransferItem
import com.example.model.TransferStatus
import com.example.model.TransferType
import com.example.service.FileServerService
import com.example.utils.FileUtils
import com.example.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

object ServerManager : LocalDropServer.ServerListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var server: LocalDropServer? = null
    private var appContext: Context? = null

    data class UiState(
        val isRunning: Boolean = false,
        val ipAddress: String? = null,
        val port: Int = 8080,
        val ssid: String? = null,
        val isWifi: Boolean = false,
        val isHotspotOrOther: Boolean = false,
        val config: ServerConfig = ServerConfig(),
        val activeTransfers: List<TransferItem> = emptyList(),
        val transferHistory: List<TransferItem> = emptyList(),
        val connectedClients: List<ClientInfo> = emptyList(),
        val totalBytesSent: Long = 0L,
        val totalBytesReceived: Long = 0L,
        val sharedFiles: List<SharedFile> = emptyList(),
        val currentFolder: String = "",
        val errorMessage: String? = null
    ) {
        val serverUrl: String?
            get() = if (isRunning && ipAddress != null) "http://$ipAddress:$port" else null

        val activeSpeed: Long
            get() = activeTransfers.filter { it.status == TransferStatus.IN_PROGRESS }.sumOf { it.speedBytesPerSec }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            refreshNetworkInfo()
            refreshSharedFiles()
        }
    }

    fun refreshNetworkInfo() {
        val ctx = appContext ?: return
        val netInfo = NetworkUtils.getLocalIpAddress(ctx)
        _state.update {
            it.copy(
                ipAddress = netInfo.ipAddress,
                ssid = netInfo.ssid,
                isWifi = netInfo.isWifi,
                isHotspotOrOther = netInfo.isHotspotOrOther
            )
        }
    }

    fun refreshSharedFiles(folder: String = _state.value.currentFolder) {
        val ctx = appContext ?: return
        val rootDir = FileUtils.getStorageDir(ctx)
        val list = FileUtils.listSharedFiles(rootDir, folder)
        _state.update {
            it.copy(
                sharedFiles = list,
                currentFolder = folder
            )
        }
    }

    fun startServer() {
        val ctx = appContext ?: return
        refreshNetworkInfo()
        if (server == null) {
            server = LocalDropServer(ctx, _state.value.config, this)
        } else {
            server?.config = _state.value.config
        }
        server?.start()

        try {
            val serviceIntent = Intent(ctx, FileServerService::class.java).apply {
                action = FileServerService.ACTION_START
            }
            ctx.startService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopServer() {
        val ctx = appContext ?: return
        server?.stop()
        server = null
        try {
            val serviceIntent = Intent(ctx, FileServerService::class.java).apply {
                action = FileServerService.ACTION_STOP
            }
            ctx.startService(serviceIntent)
        } catch (_: Exception) {}
    }

    fun updateConfig(newConfig: ServerConfig) {
        _state.update { it.copy(config = newConfig, port = newConfig.port) }
        server?.config = newConfig
    }

    fun clearHistory() {
        _state.update { it.copy(transferHistory = emptyList()) }
    }

    fun deleteSharedFile(file: SharedFile) {
        val ctx = appContext ?: return
        val rootDir = FileUtils.getStorageDir(ctx)
        val target = FileUtils.resolveSafeFile(rootDir, file.relativePath)
        if (target.exists()) {
            target.deleteRecursively()
            refreshSharedFiles()
        }
    }

    // ServerListener Callbacks
    override fun onServerStarted(port: Int) {
        scope.launch {
            _state.update { it.copy(isRunning = true, port = port, errorMessage = null) }
            _events.emit("LocalDrop Server started on port $port")
        }
    }

    override fun onServerStopped() {
        scope.launch {
            _state.update { it.copy(isRunning = false) }
            _events.emit("LocalDrop Server stopped")
        }
    }

    override fun onServerError(error: String) {
        scope.launch {
            _state.update { it.copy(isRunning = false, errorMessage = error) }
            _events.emit(error)
        }
    }

    override fun onClientConnected(client: ClientInfo) {
        scope.launch {
            _state.update { current ->
                val clients = current.connectedClients.toMutableList()
                val idx = clients.indexOfFirst { it.ip == client.ip }
                if (idx >= 0) {
                    clients[idx] = client
                } else {
                    clients.add(0, client)
                }
                current.copy(connectedClients = clients)
            }
        }
    }

    override fun onTransferStarted(item: TransferItem) {
        scope.launch {
            _state.update { current ->
                val active = current.activeTransfers.toMutableList()
                active.removeAll { it.id == item.id }
                active.add(0, item)
                current.copy(activeTransfers = active)
            }
        }
    }

    override fun onTransferProgress(item: TransferItem) {
        scope.launch {
            _state.update { current ->
                val active = current.activeTransfers.map {
                    if (it.id == item.id) item else it
                }
                current.copy(activeTransfers = active)
            }
        }
    }

    override fun onTransferCompleted(item: TransferItem) {
        scope.launch {
            _state.update { current ->
                val active = current.activeTransfers.filterNot { it.id == item.id }
                val history = current.transferHistory.toMutableList()
                history.add(0, item)

                val sent = if (item.type == TransferType.DOWNLOAD) current.totalBytesSent + item.bytesTransferred else current.totalBytesSent
                val recv = if (item.type == TransferType.UPLOAD) current.totalBytesReceived + item.bytesTransferred else current.totalBytesReceived

                current.copy(
                    activeTransfers = active,
                    transferHistory = history,
                    totalBytesSent = sent,
                    totalBytesReceived = recv
                )
            }
            refreshSharedFiles()
        }
    }

    override fun onTransferFailed(item: TransferItem) {
        scope.launch {
            _state.update { current ->
                val active = current.activeTransfers.filterNot { it.id == item.id }
                val history = current.transferHistory.toMutableList()
                history.add(0, item)
                current.copy(activeTransfers = active, transferHistory = history)
            }
        }
    }
}

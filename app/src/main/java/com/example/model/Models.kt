package com.example.model

enum class TransferType {
    UPLOAD,
    DOWNLOAD
}

enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long,
    val type: TransferType,
    val status: TransferStatus = TransferStatus.IN_PROGRESS,
    val timestamp: Long = System.currentTimeMillis(),
    val speedBytesPerSec: Long = 0L,
    val clientIp: String = "",
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (fileSize > 0) (bytesTransferred.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()
}

data class ClientInfo(
    val ip: String,
    val userAgent: String,
    val deviceName: String,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val requestsCount: Int = 1
)

data class SharedFile(
    val name: String,
    val relativePath: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String,
    val mimeType: String
)

data class ServerConfig(
    val port: Int = 8080,
    val pinRequired: Boolean = true,
    val pinCode: String = "4892",
    val allowDeletions: Boolean = true,
    val serverName: String = "LocalDrop"
)

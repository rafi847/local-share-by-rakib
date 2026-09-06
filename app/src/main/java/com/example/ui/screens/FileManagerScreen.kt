package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.SharedFile
import com.example.server.ServerManager
import com.example.ui.theme.VibrantBorder
import com.example.ui.theme.VibrantError
import com.example.ui.theme.VibrantOnPrimaryContainer
import com.example.ui.theme.VibrantPrimary
import com.example.ui.theme.VibrantPrimaryContainer
import com.example.ui.theme.VibrantSecondaryContainer
import com.example.ui.theme.VibrantSurfaceCard
import com.example.ui.theme.VibrantSurfaceElevated
import com.example.ui.theme.VibrantTextPrimary
import com.example.ui.theme.VibrantTextSecondary
import com.example.utils.FileUtils
import com.example.utils.NetworkUtils
import com.example.viewmodel.LocalDropViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileManagerScreen(
    state: ServerManager.UiState,
    viewModel: LocalDropViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    var fileToDelete by remember { mutableStateOf<SharedFile?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.addFilesFromUris(uris)
        }
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File?", color = VibrantTextPrimary) },
            text = { Text("Are you sure you want to delete '${fileToDelete!!.name}' from LocalDrop?", color = VibrantTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(fileToDelete!!)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Storage Bar Card
            StorageUsageCard(context = context)

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Shared Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VibrantTextPrimary
                    )
                    Text(
                        text = "(${state.sharedFiles.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VibrantTextSecondary
                    )
                }

                Text(
                    text = "Sync on change",
                    style = MaterialTheme.typography.labelSmall,
                    color = VibrantPrimary
                )
            }

            if (state.sharedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(VibrantSecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = VibrantPrimary
                            )
                        }
                        Text(
                            text = "No files shared yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VibrantTextPrimary
                        )
                        Text(
                            text = "Tap 'Share Files' below or upload from PC browser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("shared_files_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.sharedFiles, key = { it.relativePath }) { file ->
                        SharedFileItemView(
                            file = file,
                            dateFormat = dateFormat,
                            onOpen = { openFile(context, file) },
                            onShare = { shareFile(context, file) },
                            onDelete = { fileToDelete = file }
                        )
                    }
                }
            }
        }

        // Floating Action Button to Add Files
        FloatingActionButton(
            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_files_fab"),
            shape = RoundedCornerShape(20.dp),
            containerColor = VibrantPrimary,
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Share Files", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StorageUsageCard(context: Context) {
    val storageDir = remember { FileUtils.getStorageDir(context) }
    val freeSpace = remember(storageDir) { storageDir.freeSpace }
    val localDropSpace = remember(storageDir) { FileUtils.getFolderSize(storageDir) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VibrantSecondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = VibrantPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LocalDrop Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibrantTextPrimary
                )
                Text(
                    text = "${NetworkUtils.formatBytes(localDropSpace)} used • ${NetworkUtils.formatBytes(freeSpace)} free on phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibrantTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SharedFileItemView(
    file: SharedFile,
    dateFormat: SimpleDateFormat,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("file_item_${file.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (file.isDirectory) VibrantSecondaryContainer
                            else VibrantPrimaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFileIcon(file),
                        contentDescription = null,
                        tint = VibrantOnPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VibrantTextPrimary,
                        maxLines = 1
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (file.isDirectory) "Folder" else NetworkUtils.formatBytes(file.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = dateFormat.format(Date(file.lastModified)),
                            style = MaterialTheme.typography.labelSmall,
                            color = VibrantTextSecondary
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = VibrantTextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            showMenu = false
                            onOpen()
                        },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = VibrantError) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VibrantError) }
                    )
                }
            }
        }
    }
}

private fun getFileIcon(file: SharedFile): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder
    val ext = (file.extension ?: "").lowercase()
    return when {
        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") -> Icons.Default.Image
        ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> Icons.Default.VideoFile
        ext in listOf("mp3", "wav", "flac", "ogg", "m4a") -> Icons.Default.AudioFile
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.Folder
        else -> Icons.Default.Description
    }
}

private fun openFile(context: Context, file: SharedFile) {
    try {
        val baseDir = FileUtils.getStorageDir(context)
        val f = File(baseDir, file.relativePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            f
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open file with"))
    } catch (_: Exception) {}
}

private fun shareFile(context: Context, file: SharedFile) {
    try {
        val baseDir = FileUtils.getStorageDir(context)
        val f = File(baseDir, file.relativePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            f
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (_: Exception) {}
}

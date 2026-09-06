package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TransferItem
import com.example.model.TransferStatus
import com.example.model.TransferType
import com.example.server.ServerManager
import com.example.ui.theme.VibrantBorder
import com.example.ui.theme.VibrantError
import com.example.ui.theme.VibrantOnPrimaryContainer
import com.example.ui.theme.VibrantPrimary
import com.example.ui.theme.VibrantPrimaryContainer
import com.example.ui.theme.VibrantSecondaryContainer
import com.example.ui.theme.VibrantSuccess
import com.example.ui.theme.VibrantSurfaceCard
import com.example.ui.theme.VibrantSurfaceElevated
import com.example.ui.theme.VibrantTextPrimary
import com.example.ui.theme.VibrantTextSecondary
import com.example.ui.theme.VibrantWarning
import com.example.utils.NetworkUtils
import com.example.viewmodel.LocalDropViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransfersScreen(
    state: ServerManager.UiState,
    viewModel: LocalDropViewModel,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Transfers Section (if any)
        if (state.activeTransfers.isNotEmpty()) {
            Text(
                text = "In Progress (${state.activeTransfers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VibrantPrimary
            )

            for (transfer in state.activeTransfers) {
                ActiveTransferItemView(transfer = transfer)
            }
        }

        // Transfer History Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transfer History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VibrantTextPrimary
            )

            if (state.transferHistory.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearTransferHistory() },
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = VibrantTextSecondary
                    )
                }
            }
        }

        if (state.transferHistory.isEmpty() && state.activeTransfers.isEmpty()) {
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
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = VibrantPrimary
                        )
                    }
                    Text(
                        text = "No transfers yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VibrantTextPrimary
                    )
                    Text(
                        text = "Files sent or received over Wi-Fi will appear here.",
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
                    .testTag("transfers_history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.transferHistory, key = { it.id }) { item ->
                    HistoryItemView(item = item, dateFormat = dateFormat)
                }
            }
        }
    }
}

@Composable
private fun ActiveTransferItemView(transfer: TransferItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_transfer_${transfer.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (transfer.type == TransferType.UPLOAD) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (transfer.type == TransferType.UPLOAD) VibrantSuccess else VibrantPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (transfer.type == TransferType.UPLOAD) "PC → Phone" else "Phone → PC",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (transfer.type == TransferType.UPLOAD) VibrantSuccess else VibrantPrimary
                    )
                }

                Text(
                    text = "${transfer.progressPercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = VibrantPrimary
                )
            }

            Text(
                text = transfer.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VibrantTextPrimary,
                maxLines = 1
            )

            LinearProgressIndicator(
                progress = { transfer.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = VibrantPrimary,
                trackColor = VibrantPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${NetworkUtils.formatBytes(transfer.bytesTransferred)} / ${NetworkUtils.formatBytes(transfer.fileSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VibrantTextSecondary
                )

                Text(
                    text = NetworkUtils.formatSpeed(transfer.speedBytesPerSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = VibrantPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HistoryItemView(item: TransferItem, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (item.status) {
                                TransferStatus.COMPLETED -> VibrantSecondaryContainer
                                TransferStatus.FAILED -> Color(0xFFF9DEDC)
                                else -> Color(0xFFFFECC2)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            item.status == TransferStatus.FAILED -> Icons.Default.Error
                            item.type == TransferType.UPLOAD -> Icons.Default.ArrowDownward
                            else -> Icons.Default.ArrowUpward
                        },
                        contentDescription = null,
                        tint = when (item.status) {
                            TransferStatus.COMPLETED -> if (item.type == TransferType.UPLOAD) VibrantSuccess else VibrantPrimary
                            TransferStatus.FAILED -> VibrantError
                            else -> VibrantWarning
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.fileName,
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
                            text = NetworkUtils.formatBytes(item.fileSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = dateFormat.format(Date(item.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = VibrantTextSecondary
                        )
                    }
                }
            }

            // Status chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (item.status) {
                            TransferStatus.COMPLETED -> VibrantPrimaryContainer
                            TransferStatus.FAILED -> Color(0xFFF9DEDC)
                            else -> Color(0xFFFFECC2)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (item.status) {
                        TransferStatus.COMPLETED -> "Done"
                        TransferStatus.FAILED -> "Failed"
                        else -> "Cancelled"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (item.status) {
                        TransferStatus.COMPLETED -> VibrantOnPrimaryContainer
                        TransferStatus.FAILED -> VibrantError
                        else -> VibrantWarning
                    }
                )
            }
        }
    }
}

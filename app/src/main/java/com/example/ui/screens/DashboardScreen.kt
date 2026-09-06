package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.ServerManager
import com.example.ui.theme.VibrantBadgeBg
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
import com.example.utils.NetworkUtils
import com.example.utils.QrCodeGenerator
import com.example.viewmodel.LocalDropViewModel

@Composable
fun DashboardScreen(
    state: ServerManager.UiState,
    viewModel: LocalDropViewModel,
    modifier: Modifier = Modifier
) {
    var showQrDialog by remember { mutableStateOf(false) }

    if (showQrDialog && state.serverUrl != null) {
        QrDialog(
            url = state.serverUrl!!,
            pin = state.config.pinCode,
            pinRequired = state.config.pinRequired,
            onDismiss = { showQrDialog = false },
            onCopyUrl = { viewModel.copyServerUrlToClipboard() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Server Card with Vibrant Palette styling
        VibrantServerHeroCard(
            state = state,
            onToggle = { viewModel.toggleServer() }
        )

        // QR Code Direct Portal Card
        if (state.serverUrl != null) {
            QrPortalCard(
                state = state,
                onCopyUrl = { viewModel.copyServerUrlToClipboard() },
                onOpenBrowser = { viewModel.openInBrowser() },
                onShowFullQr = { showQrDialog = true }
            )
        } else {
            NetworkStatusCard(
                state = state,
                onRefresh = { viewModel.refreshNetwork() }
            )
        }

        // Active Transfers Card (if transfers running)
        AnimatedVisibility(visible = state.activeTransfers.isNotEmpty()) {
            ActiveTransfersCard(state = state)
        }

        // 2x2 Vibrant Stats Grid
        VibrantStatsGrid(state = state)

        // Security / PIN Settings Card
        SecurityCard(
            state = state,
            onTogglePin = { viewModel.setPinRequired(it) },
            onRegeneratePin = { viewModel.regeneratePin() },
            onCopyPin = { viewModel.copyPinToClipboard() }
        )

        // Active Connections Card
        if (state.connectedClients.isNotEmpty()) {
            ConnectedClientsCard(state = state)
        }

        // Server Power Control Action
        ServerControlButton(
            isRunning = state.isRunning,
            onToggle = { viewModel.toggleServer() }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VibrantServerHeroCard(
    state: ServerManager.UiState,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server_hero_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isRunning) VibrantPrimaryContainer else VibrantSurfaceCard
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (state.isRunning) VibrantBadgeBg else Color(0xFFE0E0E0))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (state.isRunning) "SERVER ACTIVE" else "SERVER STOPPED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (state.isRunning) VibrantOnPrimaryContainer else VibrantTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // IP Address Display
            Text(
                text = state.ipAddress ?: "127.0.0.1",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isRunning) VibrantOnPrimaryContainer else VibrantTextPrimary,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle with Port and PIN
            Text(
                text = if (state.isRunning) {
                    "Port: ${state.port} • PIN: ${if (state.config.pinRequired) state.config.pinCode else "None"}"
                } else {
                    "Connect to Wi-Fi & tap Start Server"
                },
                fontSize = 14.sp,
                color = VibrantTextSecondary,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QrPortalCard(
    state: ServerManager.UiState,
    onCopyUrl: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShowFullQr: () -> Unit
) {
    val qrContent = remember(state.serverUrl, state.config.pinRequired, state.config.pinCode) {
        if (state.config.pinRequired) "${state.serverUrl}/?pin=${state.config.pinCode}" else state.serverUrl!!
    }

    val qrBitmap = remember(qrContent) {
        QrCodeGenerator.generateQrBitmap(
            content = qrContent,
            sizePx = 360,
            darkColor = android.graphics.Color.parseColor("#21005D"),
            lightColor = android.graphics.Color.TRANSPARENT
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qr_portal_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // QR Code Box with Vibrant Border
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VibrantSurfaceCard)
                    .border(4.dp, VibrantPrimary, RoundedCornerShape(20.dp))
                    .clickable { onShowFullQr() }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = VibrantPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Scan QR to open the transfer portal on your browser",
                fontSize = 14.sp,
                color = VibrantTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyUrl,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_url_btn"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VibrantPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy URL")
                }

                Button(
                    onClick = onOpenBrowser,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_browser_btn"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun NetworkStatusCard(
    state: ServerManager.UiState,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(VibrantSecondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.ipAddress != null) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = VibrantPrimary
                    )
                }
                Column {
                    Text(
                        text = if (state.ipAddress != null) "Wi-Fi Connected" else "No Local Wi-Fi",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = VibrantTextPrimary
                    )
                    Text(
                        text = state.ssid?.let { "Network: $it" } ?: "Enable Wi-Fi or Mobile Hotspot",
                        fontSize = 13.sp,
                        color = VibrantTextSecondary
                    )
                }
            }

            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = VibrantPrimary)
            }
        }
    }
}

@Composable
private fun VibrantStatsGrid(state: ServerManager.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VibrantStatCard(
                modifier = Modifier.weight(1f),
                label = "UPLOAD SPEED",
                value = NetworkUtils.formatSpeed(state.activeSpeed)
            )
            VibrantStatCard(
                modifier = Modifier.weight(1f),
                label = "TOTAL SENT",
                value = NetworkUtils.formatBytes(state.totalBytesSent)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VibrantStatCard(
                modifier = Modifier.weight(1f),
                label = "TOTAL RECEIVED",
                value = NetworkUtils.formatBytes(state.totalBytesReceived)
            )
            VibrantStatCard(
                modifier = Modifier.weight(1f),
                label = "ACTIVE CLIENTS",
                value = "${state.connectedClients.size} online"
            )
        }
    }
}

@Composable
private fun VibrantStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = VibrantTextSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = VibrantTextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ActiveTransfersCard(state: ServerManager.UiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_transfers_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Transfers (${state.activeTransfers.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = VibrantPrimary
                )

                Text(
                    text = NetworkUtils.formatSpeed(state.activeSpeed),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VibrantPrimary
                )
            }

            for (item in state.activeTransfers) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.fileName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            color = VibrantTextPrimary
                        )
                        Text(
                            text = "${item.progressPercent}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = VibrantPrimary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { item.progress },
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
                            text = "${NetworkUtils.formatBytes(item.bytesTransferred)} / ${NetworkUtils.formatBytes(item.fileSize)}",
                            fontSize = 12.sp,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = NetworkUtils.formatSpeed(item.speedBytesPerSec),
                            fontSize = 12.sp,
                            color = VibrantPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityCard(
    state: ServerManager.UiState,
    onTogglePin: (Boolean) -> Unit,
    onRegeneratePin: () -> Unit,
    onCopyPin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VibrantSecondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = VibrantPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PIN Authentication",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VibrantTextPrimary
                        )
                        Text(
                            text = "Require PIN on web browser",
                            fontSize = 12.sp,
                            color = VibrantTextSecondary
                        )
                    }
                }

                Switch(
                    checked = state.config.pinRequired,
                    onCheckedChange = onTogglePin,
                    modifier = Modifier.testTag("pin_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VibrantPrimary,
                        checkedTrackColor = VibrantPrimaryContainer
                    )
                )
            }

            AnimatedVisibility(visible = state.config.pinRequired) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Access PIN",
                            fontSize = 11.sp,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = state.config.pinCode,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 4.sp,
                            color = VibrantOnPrimaryContainer
                        )
                    }

                    Row {
                        IconButton(onClick = onCopyPin, modifier = Modifier.testTag("copy_pin_btn")) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy PIN", tint = VibrantPrimary)
                        }
                        IconButton(onClick = onRegeneratePin, modifier = Modifier.testTag("regen_pin_btn")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate PIN", tint = VibrantPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedClientsCard(state: ServerManager.UiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connected_clients_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VibrantSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE CONNECTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VibrantTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(VibrantPrimary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${state.connectedClients.size} online",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VibrantBorder)
            )

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (client in state.connectedClients) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(VibrantSecondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = VibrantOnPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = client.deviceName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VibrantTextPrimary
                                )
                                Text(
                                    text = client.ip,
                                    fontSize = 12.sp,
                                    color = VibrantTextSecondary
                                )
                            }
                        }

                        Text(
                            text = "${client.requestsCount} reqs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerControlButton(
    isRunning: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("server_power_btn"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) VibrantError else VibrantPrimary,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "Stop Server" else "Start Server",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

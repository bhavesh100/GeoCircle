package com.bhavesh.geocircle.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bhavesh.geocircle.service.LocationTrackingService
import com.bhavesh.geocircle.ui.GeoCircleViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    groupId: String,
    viewModel: GeoCircleViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeGroup by viewModel.activeGroup.collectAsState()
    val members by viewModel.members.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val myUid by viewModel.uid.collectAsState()

    val isTracking by LocationTrackingService.isTracking.collectAsState()
    val trackedGroupId by LocationTrackingService.currentTrackedGroupId.collectAsState()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    // Auto-start tracking service for this group if not already running for it
    LaunchedEffect(groupId) {
        viewModel.loadGroupDetails(groupId)
        if (!isTracking || trackedGroupId != groupId) {
            LocationTrackingService.start(context, groupId)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    fun copyInviteCode(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Invite Code", code)
        clipboard.setPrimaryClip(clip)
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Invite code $code copied to clipboard")
        }
    }

    if (activeGroup == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Group Details") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Loading group details...")
                }
            }
        }
        return
    }

    val group = activeGroup!!

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(group.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner (Alert if Android 13+ notification is disabled)
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Notifications disabled! You won't receive breach alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                }
            }

            // Tracking Service Status Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTracking && trackedGroupId == groupId)
                            Color(0xFFE8F5E9)
                        else
                            Color(0xFFFFF3E0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTracking && trackedGroupId == groupId)
                                    "🟢 Geofence Service Active"
                                else
                                    "🟠 Geofence Service Inactive",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isTracking && trackedGroupId == groupId)
                                    Color(0xFF2E7D32)
                                else
                                    Color(0xFFE65100)
                            )
                            Text(
                                text = if (isTracking && trackedGroupId == groupId)
                                    "Listening for GPS & member breaches"
                                else
                                    "Tap to start monitoring alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                if (isTracking && trackedGroupId == groupId) {
                                    LocationTrackingService.stop(context)
                                } else {
                                    LocationTrackingService.start(context, groupId)
                                }
                            }
                        ) {
                            Text(if (isTracking && trackedGroupId == groupId) "Stop" else "Start")
                        }
                    }
                }
            }

            // Join Code card with copy button
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Join Code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.code,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = { copyInviteCode(group.code) }) {
                                Text("Copy")
                            }
                        }
                    }
                }
            }

            // Geofence Radius Info
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Geofence Radius",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${group.radiusMeters.toInt()} meters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (group.centerLat != 0.0 || group.centerLng != 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Center: %.5f, %.5f".format(group.centerLat, group.centerLng),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Test Simulation Controls (Indispensable for assignment testing)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🧪 Instant Test Simulation",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap on Device 2 to simulate walking outside. Device 1 will immediately alert!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.simulateMemberStatus(groupId, isInside = false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Simulate Exit")
                            }

                            Button(
                                onClick = {
                                    viewModel.simulateMemberStatus(groupId, isInside = true)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Simulate Enter")
                            }
                        }
                    }
                }
            }

            // Member List header
            item {
                Text(
                    text = "Members (${members.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Members with Inside/Outside badge and last-updated time
            if (members.isEmpty()) {
                item {
                    Text(
                        text = "No members joined yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(members, key = { it.uid }) { member ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.name + if (member.uid == myUid) " (You)" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Updated: ${formatTimestamp(member.updatedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Inside / Outside Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (member.isInside) Color(0xFF2E7D32) else Color(0xFFC62828),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = if (member.isInside) "Inside" else "Outside",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Leave Group button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.leaveGroup(
                            groupId = groupId,
                            onSuccess = {
                                LocationTrackingService.stop(context)
                                onBackClick()
                            }
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Leave Group")
                }
            }
        }
    }
}

private fun formatTimestamp(timeMs: Long): String {
    if (timeMs <= 0L) return "Never"
    val diff = System.currentTimeMillis() - timeMs
    return when {
        diff < 60_000L -> "Just now"
        diff < 3600_000L -> "${diff / 60_000L}m ago"
        else -> {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(timeMs))
        }
    }
}

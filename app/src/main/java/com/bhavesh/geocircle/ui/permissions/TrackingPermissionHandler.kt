package com.bhavesh.geocircle.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object PermissionUtils {

    fun hasForegroundLocation(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasBackgroundLocation(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasAllTrackingPermissions(context: Context): Boolean {
        return hasForegroundLocation(context) &&
                hasBackgroundLocation(context) &&
                hasNotificationPermission(context)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

private enum class PermissionStep {
    CHECK,
    FOREGROUND_LOCATION,
    BACKGROUND_LOCATION_RATIONALE,
    BACKGROUND_LOCATION,
    NOTIFICATION,
    SETTINGS_RATIONALE,
    COMPLETED
}

/**
 * Sequential permission flow in Compose:
 * Step 1: Foreground Location (Fine + Coarse)
 * Step 2: Background Location (with rationale explaining "Allow all the time" requirement)
 * Step 3: Notification permission (Android 13+)
 */
@Composable
fun TrackingPermissionFlow(
    onPermissionsGranted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(PermissionStep.CHECK) }

    // Launcher 1: Foreground Location (Fine + Coarse)
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PermissionUtils.hasBackgroundLocation(context)) {
                currentStep = PermissionStep.BACKGROUND_LOCATION_RATIONALE
            } else if (!PermissionUtils.hasNotificationPermission(context)) {
                currentStep = PermissionStep.NOTIFICATION
            } else {
                currentStep = PermissionStep.COMPLETED
                onPermissionsGranted()
            }
        } else {
            currentStep = PermissionStep.SETTINGS_RATIONALE
        }
    }

    // Launcher 2: Background Location
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted || PermissionUtils.hasBackgroundLocation(context)) {
            if (!PermissionUtils.hasNotificationPermission(context)) {
                currentStep = PermissionStep.NOTIFICATION
            } else {
                currentStep = PermissionStep.COMPLETED
                onPermissionsGranted()
            }
        } else {
            // On Android 11+, user must choose "Allow all the time" in Settings
            currentStep = PermissionStep.SETTINGS_RATIONALE
        }
    }

    // Launcher 3: Notifications (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        currentStep = PermissionStep.COMPLETED
        onPermissionsGranted()
    }

    // Initial check
    LaunchedEffect(Unit) {
        when {
            !PermissionUtils.hasForegroundLocation(context) -> {
                foregroundLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PermissionUtils.hasBackgroundLocation(context) -> {
                currentStep = PermissionStep.BACKGROUND_LOCATION_RATIONALE
            }
            !PermissionUtils.hasNotificationPermission(context) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onPermissionsGranted()
                }
            }
            else -> {
                onPermissionsGranted()
            }
        }
    }

    // Background location rationale dialog
    if (currentStep == PermissionStep.BACKGROUND_LOCATION_RATIONALE) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Background Location Access") },
            text = {
                Text(
                    "GeoCircle needs background location access to alert your group if you leave the safe zone while the app is minimized or your phone is locked.\n\n" +
                    "Please select \"Allow all the time\" in the next screen."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            currentStep = PermissionStep.NOTIFICATION
                        }
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    // Notification launcher trigger
    if (currentStep == PermissionStep.NOTIFICATION) {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentStep = PermissionStep.COMPLETED
                onPermissionsGranted()
            }
        }
    }

    // Fallback dialog when permission is denied or requires settings
    if (currentStep == PermissionStep.SETTINGS_RATIONALE) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Permission Required") },
            text = {
                Text(
                    "Location permissions are required for Geofence tracking. Please enable Location (Allow all the time) and Notifications in App Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PermissionUtils.openAppSettings(context)
                        onDismiss()
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

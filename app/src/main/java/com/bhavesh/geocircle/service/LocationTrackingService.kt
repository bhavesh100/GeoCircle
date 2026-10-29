package com.bhavesh.geocircle.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bhavesh.geocircle.MainActivity
import com.bhavesh.geocircle.R
import com.bhavesh.geocircle.data.Group
import com.bhavesh.geocircle.data.GroupRepository
import com.bhavesh.geocircle.geofence.GeofenceMonitor
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val repository = GroupRepository()
    private val firestore = FirebaseFirestore.getInstance()

    private var geofenceMonitor: GeofenceMonitor? = null
    private var groupListenerRegistration: ListenerRegistration? = null
    private var activeGroupId: String? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            Log.d(TAG, "Location received: lat=${location.latitude}, lng=${location.longitude}")

            serviceScope.launch {
                geofenceMonitor?.onLocation(location.latitude, location.longitude)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val groupId = intent.getStringExtra(EXTRA_GROUP_ID)
                if (!groupId.isNullOrBlank()) {
                    startTracking(groupId)
                } else {
                    Log.w(TAG, "Start command received without valid groupId")
                    stopTracking()
                }
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTracking(groupId: String) {
        activeGroupId = groupId
        _currentTrackedGroupId.value = groupId
        _isTracking.value = true

        val notification = createNotification("Tracking group: Connecting...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        listenToGroupDetails(groupId)
        requestLocationUpdates()
    }

    private fun listenToGroupDetails(groupId: String) {
        groupListenerRegistration?.remove()
        groupListenerRegistration = firestore.collection("groups")
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to listen to group $groupId", error)
                    return@addSnapshotListener
                }

                val group = snapshot?.toObject(Group::class.java)?.copy(id = snapshot.id)
                if (group != null) {
                    if (geofenceMonitor == null) {
                        geofenceMonitor = GeofenceMonitor(group, repository)
                    } else {
                        geofenceMonitor?.group = group
                    }

                    updateNotification("Monitoring geofence for: ${group.name}")
                }
            }
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permissions missing. Stopping tracking service.")
            stopTracking()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates successfully requested")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while requesting location updates", e)
            stopTracking()
        }
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping location tracking service")
        groupListenerRegistration?.remove()
        groupListenerRegistration = null
        geofenceMonitor = null
        activeGroupId = null
        _currentTrackedGroupId.value = null
        _isTracking.value = false

        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while GeoCircle tracks geofence coordinates"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoCircle Geofence Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.bhavesh.geocircle.action.START_TRACKING"
        const val ACTION_STOP = "com.bhavesh.geocircle.action.STOP_TRACKING"
        const val EXTRA_GROUP_ID = "extra_group_id"

        private const val CHANNEL_ID = "geocircle_location_tracking"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "LocationTrackingService"

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _currentTrackedGroupId = MutableStateFlow<String?>(null)
        val currentTrackedGroupId: StateFlow<String?> = _currentTrackedGroupId.asStateFlow()

        fun start(context: Context, groupId: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GROUP_ID, groupId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

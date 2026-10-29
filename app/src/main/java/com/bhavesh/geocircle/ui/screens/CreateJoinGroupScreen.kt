package com.bhavesh.geocircle.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.bhavesh.geocircle.service.LocationTrackingService
import com.bhavesh.geocircle.ui.GeoCircleViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJoinGroupScreen(
    initialTab: Int = 0,
    viewModel: GeoCircleViewModel,
    onBackClick: () -> Unit,
    onGroupSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // Create Group state
    var groupName by remember { mutableStateOf("") }
    var creatorName by remember { mutableStateOf("") }
    var centerLat by remember { mutableDoubleStateOf(0.0) }
    var centerLng by remember { mutableDoubleStateOf(0.0) }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var radiusMeters by remember { mutableFloatStateOf(100f) }

    // Join Group state
    var inviteCode by remember { mutableStateOf("") }
    var joinUserName by remember { mutableStateOf("") }

    val hasCenterLocation = (centerLat != 0.0 || centerLng != 0.0) ||
            (latText.toDoubleOrNull() != null && lngText.toDoubleOrNull() != null)

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    fun applyLocation(lat: Double, lng: Double) {
        centerLat = lat
        centerLng = lng
        latText = "%.5f".format(lat)
        lngText = "%.5f".format(lng)
        isFetchingLocation = false
    }

    fun requestFreshGpsFix() {
        val hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            isFetchingLocation = false
            return
        }

        val singleRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMaxUpdates(1)
            .setDurationMillis(10_000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    applyLocation(loc.latitude, loc.longitude)
                } else {
                    isFetchingLocation = false
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(singleRequest, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            isFetchingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    applyLocation(loc.latitude, loc.longitude)
                } else {
                    requestFreshGpsFix()
                }
            }.addOnFailureListener {
                requestFreshGpsFix()
            }
        } else {
            isFetchingLocation = false
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Location permission required to get your GPS center.")
            }
        }
    }

    fun fetchCurrentLocation() {
        isFetchingLocation = true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        if (!isGpsEnabled) {
            isFetchingLocation = false
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Device Location is OFF. Please turn ON Location in Quick Settings or use Quick Location.")
            }
            return
        }

        val hasFine = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        applyLocation(lastLoc.latitude, lastLoc.longitude)
                    } else {
                        val netLoc = try { lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
                        val gpsLoc = try { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
                        val best = netLoc ?: gpsLoc
                        if (best != null) {
                            applyLocation(best.latitude, best.longitude)
                        } else {
                            requestFreshGpsFix()
                        }
                    }
                }
                .addOnFailureListener {
                    requestFreshGpsFix()
                }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        fetchCurrentLocation()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Create Group" else "Join Group") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
//            TabRow(selectedTabIndex = selectedTab) {
//                Tab(
//                    selected = selectedTab == 0,
//                    onClick = { selectedTab = 0 },
//                    text = { Text("Create Group") }
//                )
//                Tab(
//                    selected = selectedTab == 1,
//                    onClick = { selectedTab = 1 },
//                    text = { Text("Join Group") }
//                )
//            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                Text(
                    text = "Create a New Group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = creatorName,
                    onValueChange = { creatorName = it },
                    label = { Text("Your Name (e.g. Admin)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Geofence Center",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { fetchCurrentLocation() },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Locking...")
                        } else {
                            Text("📍 GPS Location")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            // Instant test coordinates (Mumbai / Central India)
                            applyLocation(19.0760, 72.8777)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚡ Quick Coords")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            it.toDoubleOrNull()?.let { lat -> centerLat = lat }
                        },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = {
                            lngText = it
                            it.toDoubleOrNull()?.let { lng -> centerLng = lng }
                        },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Geofence Radius: ${radiusMeters.toInt()} meters",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = radiusMeters,
                    onValueChange = { radiusMeters = it },
                    valueRange = 5f..2000f,
                    steps = 38,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val finalLat = latText.toDoubleOrNull() ?: centerLat
                        val finalLng = lngText.toDoubleOrNull() ?: centerLng

                        if (groupName.isNotBlank() && (finalLat != 0.0 || finalLng != 0.0)) {
                            viewModel.createGroup(
                                name = groupName,
                                lat = finalLat,
                                lng = finalLng,
                                radius = radiusMeters.toDouble(),
                                userName = creatorName.ifBlank { "Admin" },
                                onSuccess = { createdGroup ->
                                    LocationTrackingService.start(context, createdGroup.id)
                                    onGroupSelected(createdGroup.id)
                                }
                            )
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Please enter or fetch valid Latitude & Longitude.")
                            }
                        }
                    },
                    enabled = groupName.isNotBlank() && hasCenterLocation && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else if (!hasCenterLocation) {
                        Text("Lock GPS or Tap 'Quick Coords' Above")
                    } else {
                        Text("Create Group")
                    }
                }
            } else {
                Text(
                    text = "Join with Invite Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase() },
                    label = { Text("6-Char Invite Code (e.g. AB12CD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = joinUserName,
                    onValueChange = { joinUserName = it },
                    label = { Text("Your Name (e.g. Alex)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (inviteCode.isNotBlank()) {
                            viewModel.joinGroup(
                                code = inviteCode,
                                userName = joinUserName.ifBlank { "Member" },
                                onSuccess = { joinedGroup ->
                                    LocationTrackingService.start(context, joinedGroup.id)
                                    onGroupSelected(joinedGroup.id)
                                }
                            )
                        }
                    },
                    enabled = inviteCode.isNotBlank() && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Join Group")
                    }
                }
            }
        }
    }
}

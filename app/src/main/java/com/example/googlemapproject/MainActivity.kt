package com.example.googlemapproject

import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.googlemapproject.ui.theme.GoogleMapProjectTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
            // Permissions granted
            scheduleLocationSyncWorker() // Schedule only after permissions are granted
        } else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        // Schedule the WorkManager
        scheduleLocationSyncWorker()
        setContent {

            GoogleMapComposeApp()
            GeofencingExampleApp()
        }
    }



    @Composable
    fun GeofencingExampleApp() {
        val context = LocalContext.current
        val geofenceId = "ExampleGeofence"
        val geofenceCenter = LatLng(43.6532, -79.3832)
        val geofenceRadius = 200f // 200 meters

        // Setup Geofence when the app starts
        LaunchedEffect(Unit) {
            val geofence = createGeofence(geofenceId, geofenceCenter, geofenceRadius)
            addGeofence(context, geofence)
        }
    }



    @SuppressLint("MissingPermission")
    @Composable
    fun GoogleMapComposeApp() {

        val context = LocalContext.current
        var userLocation by remember { mutableStateOf<LatLng?>(null) }
        val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
        val locationRequest = remember {
            LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 10000 // Update every 10 seconds
                fastestInterval = 5000
            }
        }
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 15f)
        }
        // Request location updates
        LaunchedEffect(Unit) {
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let {
                        location ->
                        val newLocation = LatLng(location.latitude, location.longitude)
                        userLocation = newLocation
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLng(newLocation)
                        )
                    }
                }
            }

            if (userLocation == null) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    context.mainLooper
                )
            }
        }

        GoogleMapView(userLocation,cameraPositionState)
    }



    @Composable
    fun GoogleMapView(userLocation: LatLng?, cameraPositionState: CameraPositionState) {
        val context = LocalContext.current
        // State to store the marker's position
        var markerPosition by remember { mutableStateOf<LatLng?>(null) }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng ->
                // Handle map tap and update the marker position
                markerPosition = latLng
                Toast.makeText(context, "Tapped at: $latLng", Toast.LENGTH_LONG).show()
            }
        ) {
            // If there is a marker position, add the marker
            markerPosition?.let { position ->
                Marker(
                    state = MarkerState(position = position),
                    title = "New Marker",
                    snippet = "You tapped here!"
                )
            }
            userLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "You are here",
                    snippet = "This is a custom marker",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE) // You can customize the marker color
                )
            }
        }
    }

    private fun scheduleLocationSyncWorker() {
        val workRequest = PeriodicWorkRequestBuilder<LocationSyncWorker>(15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LocationSyncWork",
            ExistingPeriodicWorkPolicy.REPLACE, // Replace any existing work with the same name
            workRequest
        )
    }
}

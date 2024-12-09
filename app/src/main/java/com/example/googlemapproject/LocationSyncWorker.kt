package com.example.googlemapproject

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Date

class LocationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Check for permissions before accessing location
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permissions not granted, return a failure result
            return Result.failure()
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location = fusedLocationClient.lastLocation.await() // Await the location result

            location?.let {
                val locationData = LocationData(location.latitude, location.longitude, Date().time)
                val gson = Gson()
                val locationJson = gson.toJson(locationData)
                // Save the location data to a file
                val file = File(applicationContext.filesDir, "location_data.json")
                FileOutputStream(file).use { fos ->
                    OutputStreamWriter(fos).use { writer ->
                        writer.write(locationJson)
                    }
                }
                // Handle the location, e.g., log or sync it
                Log.d("LocationSyncWorker", "Location: ${it.latitude}, ${it.longitude} saved")
            } ?: Log.d("LocationSyncWorker", "No location found")

            return Result.success() // Task completed successfully
        } catch (e: Exception) {
            Log.e("LocationSyncWorker", "Error syncing location: ${e.message}")
            return Result.retry() // Retry if something goes wrong
        }
    }

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long
    )
}
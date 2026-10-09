package com.poc.safemode.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.poc.safemode.domain.LocationReading
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface LocationProvider {
    fun isLocationEnabled(): Boolean

    suspend fun current(): LocationReading?
}

class FusedLocationProvider(context: Context) : LocationProvider {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    override fun isLocationEnabled(): Boolean {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        return locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(): LocationReading? = suspendCancellableCoroutine { continuation ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(TIMEOUT_MILLIS)
            .setMaxUpdateAgeMillis(MAX_AGE_MILLIS)
            .build()
        val cancellation = CancellationTokenSource()
        try {
            client.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    val reading = location?.let {
                        LocationReading(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracyMeters = if (it.hasAccuracy()) it.accuracy else null
                        )
                    }
                    if (continuation.isActive) continuation.resume(reading)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (_: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
        continuation.invokeOnCancellation { cancellation.cancel() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val MAX_AGE_MILLIS = 10_000L
    }
}

package com.poc.safemode

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private val itauCeicLocation = LatLng(-23.6355252, -46.6425453)
private const val DISTANCE_TOLERANCE_METERS = 0f
private const val FENCE_RADIUS_METERS = 10f // Raio da geofence

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val view = remember {
        LayoutInflater.from(context).inflate(R.layout.screen_map, FrameLayout(context), false)
    }
    val mapView = remember { view.findViewById<MapView>(R.id.mapView) }

    MapLifecycle(mapView)

    AndroidView(
        factory = {
            mapView.getMapAsync { googleMap ->
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(itauCeicLocation, 16f))

                //googleMap.uiSettings.setAllGesturesEnabled(false)

                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(itauCeicLocation)
                        .title("Endereço")
                        .snippet("Carregando endereço...")
                        .draggable(true),
                )
                marker?.showInfoWindow()

                // Cerca geográfica (raio visual ao redor do pino de 100 metros)
                val geofenceCircle = googleMap.addCircle(
                    CircleOptions()
                        .center(itauCeicLocation)
                        .radius(FENCE_RADIUS_METERS.toDouble())
                        .strokeWidth(3f)
                        .strokeColor(0xFF1E88E5.toInt())
                        .fillColor(0x331E88E5.toInt()),
                )

                var lastFetchedLocation = itauCeicLocation

                // Busca inicial do endereço na posição padrão
                coroutineScope.launch {
                    val initialAddress = fetchAddressFromLocation(context, itauCeicLocation)
                    marker?.snippet = initialAddress
                    marker?.showInfoWindow()
                }

                var searchJob: Job? = null

                fun updateAddressIfMovedFarEnough(targetMarker: Marker, force: Boolean = false) {
                    val distance = distanceInMeters(lastFetchedLocation, targetMarker.position)
                    if (force || distance >= DISTANCE_TOLERANCE_METERS) {
                        lastFetchedLocation = targetMarker.position
                        searchJob?.cancel()
                        searchJob = coroutineScope.launch {
                            targetMarker.snippet = "Buscando endereço..."
                            targetMarker.showInfoWindow()
                            val address = fetchAddressFromLocation(context, targetMarker.position)
                            targetMarker.snippet = address
                            targetMarker.showInfoWindow()
                        }
                    }
                }

                googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) {
                        geofenceCircle.center = marker.position
                    }

                    override fun onMarkerDrag(marker: Marker) {
                        geofenceCircle.center = marker.position
                        searchJob?.cancel()
                        searchJob = coroutineScope.launch {
                            delay(400.milliseconds)
                            updateAddressIfMovedFarEnough(marker)
                        }
                    }

                    override fun onMarkerDragEnd(marker: Marker) {
                        geofenceCircle.center = marker.position
                        searchJob?.cancel()
                        updateAddressIfMovedFarEnough(marker)
                    }
                })
            }
            view
        },
        modifier = modifier.fillMaxSize(),
    )
}

private fun distanceInMeters(start: LatLng, end: LatLng): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        start.latitude, start.longitude,
        end.latitude, end.longitude,
        results,
    )
    return results[0]
}

private suspend fun fetchAddressFromLocation(context: Context, latLng: LatLng): String =
    withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latLng.latitude,
                        latLng.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val address = addresses.firstOrNull()
                                val text = address?.getAddressLine(0) ?: "Endereço não encontrado"
                                if (continuation.isActive) continuation.resume(text)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume("Endereço não encontrado")
                            }
                        },
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                addresses?.firstOrNull()?.getAddressLine(0) ?: "Endereço não encontrado"
            }
        } catch (_: Exception) {
            "Não foi possível obter o endereço"
        }
    }

@Composable
fun MapLifecycle(mapView: MapView) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

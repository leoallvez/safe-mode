package com.poc.safemode

import android.content.Context
import android.location.Address
import android.location.Geocoder
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

private val saoPauloLocation = LatLng(-23.561414, -46.655881)

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
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(saoPauloLocation, 15f))

                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(saoPauloLocation)
                        .title("Endereço Selecionado")
                        .snippet("Carregando endereço...")
                        .draggable(true),
                )
                marker?.showInfoWindow()

                // Busca inicial do endereço na posição padrão
                coroutineScope.launch {
                    val initialAddress = fetchAddressFromLocation(context, saoPauloLocation)
                    marker?.snippet = initialAddress
                    marker?.showInfoWindow()
                }

                var searchJob: Job? = null

                googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) {
                        searchJob?.cancel()
                        marker.snippet = "Buscando endereço..."
                        marker.showInfoWindow()
                    }

                    override fun onMarkerDrag(marker: Marker) {
                        // Debounce de 400ms durante o movimento
                        searchJob?.cancel()
                        searchJob = coroutineScope.launch {
                            delay(400)
                            val address = fetchAddressFromLocation(context, marker.position)
                            marker.snippet = address
                            marker.showInfoWindow()
                        }
                    }

                    override fun onMarkerDragEnd(marker: Marker) {
                        searchJob?.cancel()
                        coroutineScope.launch {
                            marker.snippet = "Buscando endereço..."
                            marker.showInfoWindow()
                            val address = fetchAddressFromLocation(context, marker.position)
                            marker.snippet = address
                            marker.showInfoWindow()
                        }
                    }
                })
            }
            view
        },
        modifier = modifier.fillMaxSize(),
    )
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

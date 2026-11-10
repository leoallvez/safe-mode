package com.poc.safemode

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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

private val saoPauloLocation = LatLng(-23.561414, -46.655881)

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

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
                        .title("São Paulo")
                        .snippet("Pressione e arraste para mover")
                        .draggable(true),
                )
                marker?.showInfoWindow()

                googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) {
                        marker.snippet = "Arrastando..."
                        marker.showInfoWindow()
                    }

                    override fun onMarkerDrag(marker: Marker) {
                        // Atualização contínua durante o arraste se necessário
                    }

                    override fun onMarkerDragEnd(marker: Marker) {
                        val pos = marker.position
                        marker.snippet = "Nova posição: %.4f, %.4f".format(pos.latitude, pos.longitude)
                        marker.showInfoWindow()
                    }
                })
            }
            view
        },
        modifier = modifier.fillMaxSize(),
    )
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

package com.loresuelvo.serviceprovider.ui.screens.profile

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.FeatureLayer
import com.google.android.gms.maps.model.FeatureLayerOptions
import com.google.android.gms.maps.model.FeatureStyle
import com.google.android.gms.maps.model.FeatureType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PlaceFeature
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone
import com.loresuelvo.serviceprovider.R
import androidx.compose.ui.res.stringResource

@Composable
internal fun CoverageZoneMap(
    mapId: String,
    zones: List<CoverageZone>,
    selectedZoneIds: List<Int>,
    enabled: Boolean,
    onCheckedChange: (Int, Boolean) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapDescription = stringResource(R.string.provider_profile_coverage_map_description)
    val mapView = remember(mapId) { MapView(context, GoogleMapOptions().mapId(mapId)) }
    var googleMap by remember(mapView) { mutableStateOf<GoogleMap?>(null) }

    DisposableEffect(mapView) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { map ->
            googleMap = map
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(CABA_CENTER, CABA_ZOOM))
        }
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    CoverageMapLayerEffect(
        map = googleMap,
        zones = zones,
        selectedZoneIds = selectedZoneIds,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        onUnavailable = onUnavailable,
    )

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = mapDescription },
    )
}

@Composable
private fun CoverageMapLayerEffect(
    map: GoogleMap?,
    zones: List<CoverageZone>,
    selectedZoneIds: List<Int>,
    enabled: Boolean,
    onCheckedChange: (Int, Boolean) -> Unit,
    onUnavailable: () -> Unit,
) {
    DisposableEffect(map, zones, selectedZoneIds, enabled) {
        if (map == null) return@DisposableEffect onDispose {}
        if (!map.mapCapabilities.isDataDrivenStylingAvailable) {
            onUnavailable()
            return@DisposableEffect onDispose {}
        }

        val layer = map.getFeatureLayer(
            FeatureLayerOptions.builder()
                .featureType(FeatureType.ADMINISTRATIVE_AREA_LEVEL_2)
                .build(),
        )
        if (!layer.isAvailable) {
            onUnavailable()
            return@DisposableEffect onDispose {}
        }

        val zonesByPlaceId = zones.associateBy(CoverageZone::boundaryPlaceId)
        val selectedPlaceIds = selectedCoveragePlaceIds(zones, selectedZoneIds)
        layer.setFeatureStyle { feature ->
            val placeId = (feature as? PlaceFeature)?.placeId
            coverageFeatureStyle(placeId in selectedPlaceIds, placeId in zonesByPlaceId)
        }
        val listener = FeatureLayer.OnFeatureClickListener { event ->
            if (!enabled) return@OnFeatureClickListener
            val zoneId = event.features
                .asSequence()
                .filterIsInstance<PlaceFeature>()
                .mapNotNull { coverageZoneIdForPlaceId(zones, it.placeId) }
                .firstOrNull() ?: return@OnFeatureClickListener
            onCheckedChange(zoneId, zoneId !in selectedZoneIds)
        }
        layer.addOnFeatureClickListener(listener)
        onDispose { layer.removeOnFeatureClickListener(listener) }
    }
}

private fun coverageFeatureStyle(selected: Boolean, available: Boolean): FeatureStyle =
    FeatureStyle.Builder()
        .fillColor(
            when {
                selected -> Color.argb(120, 33, 150, 243)
                available -> Color.argb(40, 33, 150, 243)
                else -> Color.TRANSPARENT
            },
        )
        .strokeColor(if (available) Color.rgb(25, 118, 210) else Color.TRANSPARENT)
        .strokeWidth(if (available) 2f else 0f)
        .build()

private val CABA_CENTER = LatLng(-34.6037, -58.3816)
private const val CABA_ZOOM = 10.5f

internal fun selectedCoveragePlaceIds(zones: List<CoverageZone>, selectedZoneIds: List<Int>): Set<String> =
    zones.filter { it.id in selectedZoneIds }.mapTo(mutableSetOf(), CoverageZone::boundaryPlaceId)

internal fun coverageZoneIdForPlaceId(zones: List<CoverageZone>, placeId: String): Int? =
    zones.firstOrNull { it.boundaryPlaceId == placeId }?.id

internal fun isCoverageMapConfigured(mapId: String): Boolean = mapId.isNotBlank()

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.rememberCameraPositionState

/** Where a map starts, and stays when the device's position is unknown: Lausanne. */
val DEFAULT_MAP_CENTER = Location(46.5197, 6.6323)

/**
 * The area a map still has to frame once its camera shows [framed]: none, unless this is a newer
 * area that arrived while the camera moved, which must still be framed.
 */
fun GeoBounds?.afterFraming(framed: GeoBounds): GeoBounds? = if (this == framed) null else this

/**
 * Moves the camera to [area], if any, then reports it to [onFramed]. It only works inside a map's
 * content, which only runs once the map exists, as camera updates need. [width] and [height] are
 * the map's size in pixels.
 */
@GoogleMapComposable
@Composable
internal fun FrameArea(
    area: GeoBounds?,
    cameraPositionState: CameraPositionState,
    width: Int,
    height: Int,
    onFramed: (GeoBounds) -> Unit,
) {
  LaunchedEffect(area) {
    if (area == null) return@LaunchedEffect
    cameraPositionState.move(
        CameraUpdateFactory.newLatLngBounds(area.toLatLngBounds(), width, height, 0)
    )
    onFramed(area)
  }
}

/**
 * A map's camera, saved with its screen: switching tabs or rotating keeps it where the user left
 * it.
 */
@Composable internal fun rememberMapCamera(): CameraPositionState = rememberCameraPositionState()

/** The Maps SDK's version of these bounds; it also wraps across the antimeridian. */
fun GeoBounds.toLatLngBounds(): LatLngBounds {
  // LatLng turns longitude 180 into -180, which would collapse bounds spanning every longitude, as
  // near a pole, to no width at all; the largest longitude below 180 keeps them whole.
  val eastEdge = if (west == -180.0 && east == 180.0) Math.nextDown(180.0) else east
  return LatLngBounds(LatLng(south, west), LatLng(north, eastEdge))
}

/** These bounds, as the [GeoBounds] the rest of the app uses; wrapping ones keep wrapping. */
fun LatLngBounds.toGeoBounds(): GeoBounds =
    GeoBounds(southwest.latitude, southwest.longitude, northeast.latitude, northeast.longitude)

/** This location as the Maps SDK's [LatLng]. */
fun Location.toLatLng(): LatLng = LatLng(lat, lng)

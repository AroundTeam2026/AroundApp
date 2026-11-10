// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint

/**
 * Reads the mandatory [field] of this document with [read], which is given the field's name and
 * returns null when the field is absent or of another type.
 *
 * The Firestore mappers use it for the fields a stored document must have: a document without one
 * can't be mapped, and the mappers log and skip it.
 *
 * @throws IllegalArgumentException if [read] returns null.
 */
internal fun <T : Any> DocumentSnapshot.required(
    field: String,
    read: DocumentSnapshot.(String) -> T?,
): T = read(field) ?: throw IllegalArgumentException("Missing field $field")

/** This location as the `GeoPoint` the Firestore mappers store. */
internal fun Location.toGeoPoint(): GeoPoint = GeoPoint(lat, lng)

/** This stored `GeoPoint` as the domain's [Location]. */
internal fun GeoPoint.toLocation(): Location = Location(latitude, longitude)

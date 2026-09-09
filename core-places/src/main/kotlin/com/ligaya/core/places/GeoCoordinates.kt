package com.ligaya.core.places

/**
 * A device location, as needed for a Places Nearby Search center point. Deliberately a small,
 * module-local duplicate of core-location's identical type (Step 15) rather than a dependency on
 * that module: the two feature modules would otherwise couple to each other over a two-field
 * value type, and this codebase has consistently kept feature modules decoupled from one another
 * (see core-permissions' own build script comment). Whichever step wires this module into a live
 * episode converts core-location's GeoCoordinates into this one at the call site.
 */
data class GeoCoordinates(val latitude: Double, val longitude: Double)

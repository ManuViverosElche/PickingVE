package com.vivero.pickingve.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** Posicion one-shot por registro (D-221): sin tracking continuo, sin Play Services. */
object GpsFix {

    /** Ultima posicion conocida fresca (NETWORK -> GPS -> PASSIVE); null sin permiso o sin fix. */
    fun ultimaPosicion(context: Context): Pair<Double, Double>? {
        val fino = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val grueso = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fino && !grueso) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var mejor: Location? = null
        for (provider in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (mejor == null || loc.time > mejor!!.time) mejor = loc
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        return mejor?.let { Pair(it.latitude, it.longitude) }
    }
}

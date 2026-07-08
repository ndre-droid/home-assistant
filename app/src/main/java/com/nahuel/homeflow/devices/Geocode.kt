package com.nahuel.homeflow.devices

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Turns an address string into (lat, lon) using Android's built-in Geocoder. */
object Geocode {
    suspend fun addressToCoords(ctx: Context, address: String): Result<Pair<Double, Double>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(address.isNotBlank()) { "Keine Adresse eingegeben" }
                require(Geocoder.isPresent()) { "Geocoder nicht verfügbar auf diesem Gerät" }
                val results = Geocoder(ctx, Locale.getDefault()).getFromLocationName(address, 1)
                val loc = results?.firstOrNull() ?: error("Adresse nicht gefunden")
                loc.latitude to loc.longitude
            }
        }
}

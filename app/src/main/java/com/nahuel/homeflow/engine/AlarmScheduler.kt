package com.nahuel.homeflow.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TriggerType
import java.util.Calendar
import kotlin.math.*

/**
 * Schedules TIME and SUN triggers with AlarmManager. Each enabled routine with such a
 * trigger gets the next occurrence armed; the receiver runs it and re-arms for the next day.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "com.nahuel.homeflow.ALARM_FIRE"

    fun rescheduleAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cfg = Store.config.value
        Store.routines.value.filter { it.enabled }.forEach { r ->
            r.triggers.forEachIndexed { ti, t ->
                if (t.type == TriggerType.TIME || t.type == TriggerType.SUN) {
                    val at = nextFireMillis(t.type, t, cfg.latitude, cfg.longitude) ?: return@forEachIndexed
                    val code = (r.id.hashCode() * 31 + ti)
                    val pi = PendingIntent.getBroadcast(
                        ctx, code,
                        Intent(ctx, AlarmReceiver::class.java).apply {
                            action = ACTION_FIRE
                            putExtra("routineId", r.id)
                            data = android.net.Uri.parse("homeflow://alarm/${r.id}/$ti")
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    runCatching {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    }.recoverCatching {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); Unit
                    }
                }
            }
        }
    }

    fun nextFireMillis(type: TriggerType, t: com.nahuel.homeflow.data.Trigger, lat: Double, lon: Double): Long? {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        for (dayOffset in 0..1) {
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val minuteOfDay = when (type) {
                TriggerType.TIME -> {
                    val p = t.time.split(":")
                    (p.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
                }
                TriggerType.SUN -> {
                    val base = sunMinuteOfDay(c, lat, lon, t.sunEvent == "SUNRISE") ?: return null
                    base + t.sunOffsetMin
                }
                else -> return null
            }
            c.set(Calendar.HOUR_OF_DAY, (minuteOfDay / 60).coerceIn(0, 23))
            c.set(Calendar.MINUTE, (minuteOfDay % 60 + 60) % 60)
            if (c.timeInMillis > now) return c.timeInMillis
        }
        return null
    }

    /** Sunrise/sunset local minute-of-day via NOAA approximation. Null if polar day/night. */
    private fun sunMinuteOfDay(day: Calendar, lat: Double, lon: Double, sunrise: Boolean): Int? {
        val n = day.get(Calendar.DAY_OF_YEAR)
        val lngHour = lon / 15.0
        val t = if (sunrise) n + (6 - lngHour) / 24 else n + (18 - lngHour) / 24
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
        l = (l + 360) % 360
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = (ra + 360) % 360
        val lQuad = floor(l / 90) * 90
        val raQuad = floor(ra / 90) * 90
        ra = (ra + (lQuad - raQuad)) / 15
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val zenith = 90.833
        val cosH = (cos(Math.toRadians(zenith)) - sinDec * sin(Math.toRadians(lat))) /
            (cosDec * cos(Math.toRadians(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = if (sunrise) (360 - Math.toDegrees(acos(cosH))) / 15 else Math.toDegrees(acos(cosH)) / 15
        val localT = h + ra - 0.06571 * t - 6.622
        var ut = (localT - lngHour) % 24
        ut = (ut + 24) % 24
        // convert UT to local minute-of-day using device offset
        val offsetMin = day.get(Calendar.ZONE_OFFSET) / 60000 + day.get(Calendar.DST_OFFSET) / 60000
        val localMin = (ut * 60).roundToInt() + offsetMin
        return ((localMin % 1440) + 1440) % 1440
    }
}

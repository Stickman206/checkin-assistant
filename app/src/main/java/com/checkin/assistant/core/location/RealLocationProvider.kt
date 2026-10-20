package com.checkin.assistant.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** 一次定位结果：lat/lng 已是 GCJ-02，wgs 为原始值，便于排查 */
data class Fix(
    val lat: Double,
    val lng: Double,
    val wgsLat: Double,
    val wgsLng: Double,
    val accuracy: Float,
    val time: Long
)

/**
 * 读取设备真实定位，不做任何改写。
 *
 * 策略：先看 60 秒内的缓存定位（省电、够用），否则请求一次单次定位，
 * 超时后回退到任意历史定位。
 *
 * 注意：Android 原生返回 WGS-84，学校后端用 GCJ-02，
 * 因此对外统一通过 [currentGcj02] 提供已转换的坐标。
 */
@Singleton
class RealLocationProvider @Inject constructor(
    @ApplicationContext private val ctx: Context
) {

    private val manager: LocationManager? =
        ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean =
        manager?.let { m ->
            runCatching {
                m.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        } ?: false

    /**
     * 后台定位是否可用。
     *
     * Android 10 起，「使用期间允许」的权限在后台读不到定位——WorkManager 里
     * 请求定位会直接拿不到任何 provider 数据。必须让用户在系统设置里选「始终允许」。
     * Android 9 及以下没有这个区分，前台权限就够。
     */
    fun hasBackgroundLocation(): Boolean {
        if (!hasPermission()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 当前是否只拿到了「使用期间允许」 */
    fun needsBackgroundLocation(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            hasPermission() && !hasBackgroundLocation()

    suspend fun current(timeoutMs: Long = 15_000L): Location? = withContext(Dispatchers.IO) {
        val m = manager ?: return@withContext null
        if (!hasPermission()) return@withContext null

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { m.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return@withContext null

        val last = providers
            .mapNotNull { p -> runCatching { m.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 60_000L) {
            return@withContext last
        }

        val deferred = CompletableDeferred<Location?>()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!deferred.isCompleted) deferred.complete(location)
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        try {
            providers.forEach { p ->
                runCatching { m.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper()) }
            }
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: last
        } finally {
            runCatching { m.removeUpdates(listener) }
        }
    }

    /** 返回与目标点的距离（米），任一坐标缺失时返回 null */
    fun distanceTo(lat: Double, lng: Double, targetLat: Double, targetLng: Double): Float? {
        if (targetLat == 0.0 && targetLng == 0.0) return null
        val out = FloatArray(1)
        Location.distanceBetween(lat, lng, targetLat, targetLng, out)
        return out[0]
    }

    /**
     * 取一次定位，并把 WGS-84 转成 GCJ-02（与服务端坐标系一致）。
     */
    suspend fun currentGcj02(timeoutMs: Long = 15_000L): Fix? {
        val raw = current(timeoutMs) ?: return null
        val (gLat, gLng) = CoordTransform.wgs84ToGcj02(raw.latitude, raw.longitude)
        return Fix(
            lat = gLat,
            lng = gLng,
            wgsLat = raw.latitude,
            wgsLng = raw.longitude,
            accuracy = raw.accuracy,
            time = raw.time
        )
    }
}

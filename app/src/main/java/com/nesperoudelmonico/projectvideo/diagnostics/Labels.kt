package com.nesperoudelmonico.projectvideo.diagnostics

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Traducción de constantes de Camera2 / MediaCodec a texto legible.
 * Se usan literales en vez de las constantes del SDK para que estas funciones
 * sean puras y se puedan probar en la JVM sin Android.
 */
object Labels {

    fun hardwareLevel(level: Int?): String = when (level) {
        0 -> "LIMITED"
        1 -> "FULL"
        2 -> "LEGACY"
        3 -> "LEVEL_3"
        4 -> "EXTERNAL"
        null -> "desconocido"
        else -> "otro ($level)"
    }

    /** Ordena los niveles de menor a mayor capacidad: LEGACY < EXTERNAL < LIMITED < FULL < LEVEL_3. */
    fun hardwareLevelRank(level: Int?): Int = when (level) {
        2 -> 0
        4 -> 1
        0 -> 2
        1 -> 3
        3 -> 4
        else -> -1
    }

    private val capabilityNames = mapOf(
        0 to "BACKWARD_COMPATIBLE",
        1 to "MANUAL_SENSOR",
        2 to "MANUAL_POST_PROCESSING",
        3 to "RAW",
        4 to "PRIVATE_REPROCESSING",
        5 to "READ_SENSOR_SETTINGS",
        6 to "BURST_CAPTURE",
        7 to "YUV_REPROCESSING",
        8 to "DEPTH_OUTPUT",
        9 to "CONSTRAINED_HIGH_SPEED_VIDEO",
        10 to "MOTION_TRACKING",
        11 to "LOGICAL_MULTI_CAMERA",
        12 to "MONOCHROME",
        13 to "SECURE_IMAGE_DATA",
        14 to "SYSTEM_CAMERA",
        15 to "OFFLINE_PROCESSING",
        16 to "ULTRA_HIGH_RESOLUTION_SENSOR",
        17 to "REMOSAIC_REPROCESSING",
        18 to "DYNAMIC_RANGE_TEN_BIT",
        19 to "STREAM_USE_CASE",
        20 to "COLOR_SPACE_PROFILES",
    )

    fun capability(value: Int): String = capabilityNames[value] ?: "CAPABILITY_$value"

    fun facing(value: Int?): String = when (value) {
        0 -> "Frontal"
        1 -> "Trasera"
        2 -> "Externa"
        else -> "Desconocida"
    }

    fun aeMode(value: Int): String = when (value) {
        0 -> "OFF (manual)"
        1 -> "ON"
        2 -> "ON_AUTO_FLASH"
        3 -> "ON_ALWAYS_FLASH"
        4 -> "ON_AUTO_FLASH_REDEYE"
        5 -> "ON_EXTERNAL_FLASH"
        6 -> "ON_LOW_LIGHT_BOOST"
        else -> "AE_$value"
    }

    fun awbMode(value: Int): String = when (value) {
        0 -> "OFF (manual)"
        1 -> "AUTO"
        2 -> "INCANDESCENT"
        3 -> "FLUORESCENT"
        4 -> "WARM_FLUORESCENT"
        5 -> "DAYLIGHT"
        6 -> "CLOUDY_DAYLIGHT"
        7 -> "TWILIGHT"
        8 -> "SHADE"
        else -> "AWB_$value"
    }

    fun afMode(value: Int): String = when (value) {
        0 -> "OFF (manual)"
        1 -> "AUTO"
        2 -> "MACRO"
        3 -> "CONTINUOUS_VIDEO"
        4 -> "CONTINUOUS_PICTURE"
        5 -> "EDOF"
        else -> "AF_$value"
    }

    fun videoStabilization(value: Int): String = when (value) {
        0 -> "OFF"
        1 -> "ON (EIS)"
        2 -> "PREVIEW_STABILIZATION"
        else -> "VSTAB_$value"
    }

    fun opticalStabilization(value: Int): String = when (value) {
        0 -> "OFF"
        1 -> "ON (OIS)"
        else -> "OIS_$value"
    }

    fun thermalStatus(value: Int): String = when (value) {
        0 -> "NONE (normal)"
        1 -> "LIGHT"
        2 -> "MODERATE"
        3 -> "SEVERE"
        4 -> "CRITICAL"
        5 -> "EMERGENCY"
        6 -> "SHUTDOWN"
        else -> "desconocido ($value)"
    }

    fun dynamicRangeProfile(value: Long): String = when (value) {
        1L -> "STANDARD (SDR)"
        2L -> "HLG10"
        4L -> "HDR10"
        8L -> "HDR10_PLUS"
        else -> "0x" + value.toString(16)
    }

    /** Tiempo de exposición en nanosegundos a notación de fotografía: 1/8000 s, 1/30 s, 2.00 s. */
    fun exposureTime(nanos: Long): String {
        if (nanos <= 0L) return "—"
        val denominator = (1_000_000_000.0 / nanos).roundToLong()
        return if (denominator <= 1L) {
            String.format(Locale.US, "%.2f s", nanos / 1_000_000_000.0)
        } else {
            "1/$denominator s"
        }
    }

    /** FPS máximos a partir de la duración mínima de frame (ns). */
    fun fpsFromFrameDuration(nanos: Long): Double =
        if (nanos > 0L) 1_000_000_000.0 / nanos else 0.0

    fun fps(value: Double): String =
        if (value <= 0.0) "—" else String.format(Locale.US, "%.0f fps", value)

    /** Bytes a gigabytes decimales (como los reporta el sistema). */
    fun gigabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)

    fun glyph(support: Support?): String = when (support) {
        Support.YES -> "✅"
        Support.NO -> "❌"
        Support.PARTIAL -> "⚠️"
        Support.UNKNOWN -> "❔"
        null -> "•"
    }

    fun yesNo(value: Boolean): String = if (value) "sí" else "no"
}

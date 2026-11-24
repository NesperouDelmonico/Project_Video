package com.nesperoudelmonico.projectvideo.diagnostics

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.util.Size
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lee todo lo que Camera2 y MediaCodec reportan sobre el equipo.
 * No abre ninguna cámara: solo consulta CameraCharacteristics, así que es seguro y rápido.
 */
class CameraDiagnostics(private val context: Context) {

    private val cameraManager: CameraManager =
        context.getSystemService(CameraManager::class.java)

    fun collect(): DiagnosticsReport {
        val notes = mutableListOf<String>()
        val cameras = mutableListOf<CameraReport>()
        var mainBack: CameraCharacteristics? = null
        var mainBackId: String? = null

        val ids = try {
            cameraManager.cameraIdList.toList()
        } catch (e: Exception) {
            notes += "No se pudo listar las cámaras: ${e.message}"
            emptyList()
        }
        if (ids.isEmpty()) notes += "El sistema no reportó ninguna cámara."

        val seen = mutableSetOf<String>()
        for (id in ids) {
            val chars = characteristicsOrNull(id, notes) ?: continue
            seen += id
            if (mainBack == null &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            ) {
                mainBack = chars
                mainBackId = id
            }
            cameras += describeCamera(id, chars, parentId = null)

            // Cámaras físicas detrás de una lógica (ultra gran angular, macro…).
            for (physicalId in chars.physicalCameraIds) {
                if (physicalId in seen) continue
                val physical = characteristicsOrNull(physicalId, notes) ?: continue
                seen += physicalId
                cameras += describeCamera(physicalId, physical, parentId = id)
            }
        }

        val encoders = describeEncoders()

        return DiagnosticsReport(
            generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            verdict = buildVerdict(mainBackId, mainBack, encoders),
            device = describeDevice(),
            cameras = cameras,
            encoders = encoders.section,
            notes = notes,
            mainCameraId = mainBackId,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Veredicto
    // ---------------------------------------------------------------------------------------

    private fun buildVerdict(
        id: String?,
        chars: CameraCharacteristics?,
        encoders: EncoderSummary,
    ): InfoSection {
        val title = "Veredicto (cámara trasera principal${id?.let { ", id $it" } ?: ""})"
        if (chars == null) {
            return InfoSection(title, listOf(InfoRow("Cámara trasera", "No encontrada", Support.NO)))
        }

        val caps = capabilities(chars)
        val manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps
        val manualPost = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in caps
        val level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val modes = videoModes(map)
        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
        val maxAeFps = fpsRanges.maxOfOrNull { it.upper } ?: 0
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: FloatArray(0)
        val ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0)
        val vstab = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
        val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val aeLock = chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
        val awbLock = chars.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true
        val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val tenBit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT in caps
        val highSpeed = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO in caps
        val maxHighSpeedFps = map?.highSpeedVideoSizes.orEmpty()
            .flatMap { size -> map?.getHighSpeedVideoFpsRangesFor(size).orEmpty().map { it.upper } }
            .maxOrNull()

        val rows = mutableListOf<InfoRow>()

        rows += InfoRow(
            "Nivel Camera2",
            Labels.hardwareLevel(level),
            when (Labels.hardwareLevelRank(level)) {
                3, 4 -> Support.YES
                2 -> Support.PARTIAL
                else -> Support.NO
            },
        )

        rows += if (manualSensor) {
            val iso = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exp = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            InfoRow(
                "ISO y obturación manuales",
                "Sí · ISO ${iso?.lower}–${iso?.upper} · " +
                    "${exp?.let { Labels.exposureTime(it.lower) }} a ${exp?.let { Labels.exposureTime(it.upper) }}",
                Support.YES,
            )
        } else {
            InfoRow(
                "ISO y obturación manuales",
                "No declarado (sin MANUAL_SENSOR). Puede funcionar igual: ejecuta la prueba activa.",
                Support.PARTIAL,
            )
        }

        rows += InfoRow(
            "Enfoque manual (distancia)",
            when {
                minFocus <= 0f -> "Lente de foco fijo"
                manualSensor -> "sí"
                else -> "No declarado: ejecuta la prueba activa"
            },
            when {
                minFocus <= 0f -> Support.NO
                manualSensor -> Support.YES
                else -> Support.PARTIAL
            },
        )
        rows += InfoRow(
            "Balance de blancos manual",
            if (manualPost) "Sí (MANUAL_POST_PROCESSING)" else "Presets garantizados; ganancias manuales: ejecuta la prueba activa",
            if (manualPost) Support.YES else Support.PARTIAL,
        )
        val evAvailable = evRange != null && (evRange.lower != 0 || evRange.upper != 0)
        rows += InfoRow(
            "Compensación de exposición",
            if (evAvailable) "Pasos ${evRange?.lower} a ${evRange?.upper}" else "No",
            if (evAvailable) Support.YES else Support.NO,
        )
        rows += InfoRow(
            "Bloqueo AE / AWB",
            "AE ${Labels.yesNo(aeLock)} · AWB ${Labels.yesNo(awbLock)}",
            when {
                aeLock && awbLock -> Support.YES
                aeLock || awbLock -> Support.PARTIAL
                else -> Support.NO
            },
        )
        rows += InfoRow(
            "Apertura",
            when {
                apertures.size > 1 -> "Variable: " + apertures.joinToString(", ") { "f/$it" }
                apertures.size == 1 -> "Fija f/${apertures[0]}"
                else -> "No reportada"
            },
            if (apertures.size > 1) Support.YES else Support.NO,
        )

        fun videoRow(label: String, width: Int, height: Int, fps: Int): InfoRow {
            val sizeFps = maxFpsFor(modes, width, height)
            val ok = sizeFps != null && sizeFps >= fps - 1 && maxAeFps >= fps
            val value = when {
                sizeFps == null -> "Resolución no ofrecida a MediaRecorder"
                ok -> "Sí"
                else -> "No (máx ${Labels.fps(sizeFps)} en esa resolución)"
            }
            return InfoRow(label, value, if (ok) Support.YES else Support.NO)
        }
        rows += videoRow("4K @ 30 fps", 3840, 2160, 30)
        rows += videoRow("4K @ 60 fps", 3840, 2160, 60)
        rows += videoRow("1080p @ 60 fps", 1920, 1080, 60)

        rows += InfoRow(
            "Alta velocidad (cámara lenta)",
            if (highSpeed && maxHighSpeedFps != null) "Sí, hasta $maxHighSpeedFps fps" else "No expuesta a terceros",
            if (highSpeed) Support.YES else Support.NO,
        )
        rows += InfoRow(
            "Estabilización óptica (OIS)",
            Labels.yesNo(1 in ois),
            if (1 in ois) Support.YES else Support.NO,
        )
        rows += InfoRow(
            "Estabilización de video (EIS)",
            "EIS ${Labels.yesNo(1 in vstab)} · Preview estabilizado ${Labels.yesNo(2 in vstab)}",
            when {
                1 in vstab -> Support.YES
                2 in vstab -> Support.PARTIAL
                else -> Support.NO
            },
        )
        rows += InfoRow(
            "HDR 10-bit",
            Labels.yesNo(tenBit),
            if (tenBit) Support.YES else Support.NO,
        )
        rows += InfoRow(
            "RAW",
            Labels.yesNo(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps),
            if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps) Support.YES else Support.NO,
        )
        rows += InfoRow(
            "Encoder HEVC por hardware",
            when {
                encoders.hevcMain10 -> "Sí, con Main10 (10-bit)"
                encoders.hevc -> "Sí (8-bit)"
                else -> "No"
            },
            if (encoders.hevc) Support.YES else Support.NO,
        )

        return InfoSection(title, rows)
    }

    // ---------------------------------------------------------------------------------------
    // Cámaras
    // ---------------------------------------------------------------------------------------

    private fun describeCamera(id: String, chars: CameraCharacteristics, parentId: String?): CameraReport {
        val facing = Labels.facing(chars.get(CameraCharacteristics.LENS_FACING))
        val physicalIds = chars.physicalCameraIds
        val kind = when {
            parentId != null -> "física de la $parentId"
            physicalIds.isNotEmpty() -> "lógica"
            else -> "independiente"
        }
        val title = "Cámara $id — $facing ($kind)"

        val sections = listOf(
            generalSection(chars, physicalIds),
            capabilitiesSection(chars),
            exposureSection(chars),
            colorFocusSection(chars),
            stabilizationSection(chars),
            videoSection(chars),
        )
        return CameraReport(id = id, title = title, sections = sections, rawKeys = rawKeys(chars))
    }

    private fun generalSection(chars: CameraCharacteristics, physicalIds: Set<String>): InfoSection {
        val rows = mutableListOf<InfoRow>()
        rows += InfoRow("Nivel de hardware", Labels.hardwareLevel(chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)))
        if (physicalIds.isNotEmpty()) rows += InfoRow("Cámaras físicas", physicalIds.joinToString(", "))
        chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.let { lengths ->
            rows += InfoRow("Distancia focal", lengths.joinToString(", ") { "$it mm" })
        }
        chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.let { apertures ->
            val kind = if (apertures.size > 1) "variable" else "fija"
            rows += InfoRow("Apertura ($kind)", apertures.joinToString(", ") { "f/$it" })
        }
        chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let {
            rows += InfoRow("Tamaño físico del sensor", String.format(Locale.US, "%.2f × %.2f mm", it.width, it.height))
        }
        chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let {
            val mp = it.width.toLong() * it.height / 1_000_000.0
            rows += InfoRow("Resolución del sensor", String.format(Locale.US, "%d × %d (%.1f MP)", it.width, it.height, mp))
        }
        chars.get(CameraCharacteristics.SENSOR_ORIENTATION)?.let {
            rows += InfoRow("Orientación del sensor", "$it°")
        }
        return InfoSection("General", rows)
    }

    private fun capabilitiesSection(chars: CameraCharacteristics): InfoSection {
        val caps = capabilities(chars)
        val rows = caps.sorted().map { InfoRow(Labels.capability(it), "presente", Support.YES) }
        return InfoSection("Capacidades (${caps.size})", rows.ifEmpty { listOf(InfoRow("Capacidades", "ninguna reportada")) })
    }

    private fun exposureSection(chars: CameraCharacteristics): InfoSection {
        val rows = mutableListOf<InfoRow>()
        chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
            rows += InfoRow("Rango ISO", "${it.lower} – ${it.upper}")
        }
        chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)?.let {
            rows += InfoRow("ISO analógico máximo", "$it")
        }
        chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
            rows += InfoRow("Rango de obturación", "${Labels.exposureTime(it.lower)} – ${Labels.exposureTime(it.upper)}")
        }
        chars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)?.let {
            rows += InfoRow("Duración máxima de frame", Labels.exposureTime(it))
        }
        chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.let { modes ->
            rows += InfoRow("Modos AE", modes.joinToString(", ") { Labels.aeMode(it) })
        }
        val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        if (evRange != null && evStep != null) {
            val step = evStep.toFloat()
            rows += InfoRow(
                "Compensación EV",
                String.format(Locale.US, "%+.1f a %+.1f EV (paso %s)", evRange.lower * step, evRange.upper * step, evStep.toString()),
            )
        }
        rows += InfoRow("Bloqueo AE disponible", Labels.yesNo(chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true))
        return InfoSection("Exposición", rows)
    }

    private fun colorFocusSection(chars: CameraCharacteristics): InfoSection {
        val rows = mutableListOf<InfoRow>()
        chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.let { modes ->
            rows += InfoRow("Modos de balance de blancos", modes.joinToString(", ") { Labels.awbMode(it) })
        }
        rows += InfoRow("Bloqueo AWB disponible", Labels.yesNo(chars.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true))
        chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.let { modes ->
            rows += InfoRow("Modos de enfoque", modes.joinToString(", ") { Labels.afMode(it) })
        }
        chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let {
            rows += InfoRow(
                "Distancia mínima de enfoque",
                if (it <= 0f) "Foco fijo" else String.format(Locale.US, "%.2f dioptrías (≈ %.1f cm)", it, 100f / it),
            )
        }
        return InfoSection("Color y enfoque", rows)
    }

    private fun stabilizationSection(chars: CameraCharacteristics): InfoSection {
        val rows = mutableListOf<InfoRow>()
        chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.let { modes ->
            rows += InfoRow("Estabilización de video", modes.joinToString(", ") { Labels.videoStabilization(it) })
        }
        chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.let { modes ->
            rows += InfoRow("Estabilización óptica", modes.joinToString(", ") { Labels.opticalStabilization(it) })
        }
        return InfoSection("Estabilización", rows)
    }

    private fun videoSection(chars: CameraCharacteristics): InfoSection {
        val rows = mutableListOf<InfoRow>()
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val modes = videoModes(map)
        if (modes.isEmpty()) {
            rows += InfoRow("Resoluciones de video", "ninguna reportada")
        } else {
            for ((size, fps) in modes) {
                rows += InfoRow("Video ${size.width}×${size.height}", "hasta ${Labels.fps(fps)}")
            }
        }

        chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.let { ranges ->
            rows += InfoRow("Rangos de FPS (AE)", ranges.joinToString(", ") { "[${it.lower}, ${it.upper}]" })
        }

        if (map != null) {
            val highSpeed = map.highSpeedVideoSizes.orEmpty()
            if (highSpeed.isNotEmpty()) {
                for (size in highSpeed.sortedByDescending { it.width * it.height }) {
                    val ranges = map.getHighSpeedVideoFpsRangesFor(size).orEmpty()
                        .map { it.upper }.distinct().sorted()
                    rows += InfoRow("Alta velocidad ${size.width}×${size.height}", ranges.joinToString(", ") { "$it fps" })
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                rows += InfoRow("Rango de zoom", String.format(Locale.US, "%.2fx – %.2fx", it.lower, it.upper))
            }
        }
        chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let {
            rows += InfoRow("Zoom digital máximo", String.format(Locale.US, "%.1fx", it))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            chars.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)?.let { profiles ->
                rows += InfoRow(
                    "Perfiles de rango dinámico",
                    profiles.supportedProfiles.sorted().joinToString(", ") { Labels.dynamicRangeProfile(it) },
                )
            }
        }
        return InfoSection("Video", rows)
    }

    private fun rawKeys(chars: CameraCharacteristics): List<InfoRow> {
        val rows = chars.keys
            .sortedBy { it.name }
            .filterNot { key ->
                // Objetos enormes cuyo contenido ya se resume en la sección de video.
                key.name.contains("streamConfigurationMap", ignoreCase = true) ||
                    key.name.contains("mandatory", ignoreCase = true)
            }
            .map { key ->
                val value = try {
                    formatValue(rawValue(chars, key))
                } catch (e: Exception) {
                    "error: ${e.javaClass.simpleName}"
                }
                InfoRow(key.name, value)
            }
            .toMutableList()

        val needingPermission = chars.keysNeedingPermission.map { it.name }
        if (needingPermission.isNotEmpty()) {
            rows += InfoRow("(claves que requieren permiso de cámara)", needingPermission.joinToString(", "))
        }
        return rows
    }

    // ---------------------------------------------------------------------------------------
    // Dispositivo
    // ---------------------------------------------------------------------------------------

    private fun describeDevice(): InfoSection {
        val rows = mutableListOf<InfoRow>()
        rows += InfoRow("Fabricante", Build.MANUFACTURER)
        rows += InfoRow("Modelo", "${Build.MODEL} (${Build.DEVICE})")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rows += InfoRow("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        }
        rows += InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        val power = context.getSystemService(PowerManager::class.java)
        if (power != null) {
            val status = power.currentThermalStatus
            rows += InfoRow(
                "Estado térmico",
                Labels.thermalStatus(status),
                if (status <= PowerManager.THERMAL_STATUS_LIGHT) Support.YES else Support.PARTIAL,
            )
        }

        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(dir.absolutePath)
            val free = stat.availableBytes
            rows += InfoRow("Almacenamiento libre", "${Labels.gigabytes(free)} de ${Labels.gigabytes(stat.totalBytes)}")
            val loopNeed = 1_400_000_000L // 10 min a 1080p30 + un segmento extra (ver docs/viabilidad.md)
            rows += InfoRow(
                "Espacio para bucle de 10 min a 1080p (~1,4 GB)",
                Labels.yesNo(free >= loopNeed),
                if (free >= loopNeed) Support.YES else Support.NO,
            )
        } catch (e: Exception) {
            rows += InfoRow("Almacenamiento", "no disponible: ${e.message}", Support.UNKNOWN)
        }
        return InfoSection("Dispositivo", rows)
    }

    // ---------------------------------------------------------------------------------------
    // Encoders
    // ---------------------------------------------------------------------------------------

    private data class EncoderSummary(
        val section: InfoSection,
        val hevc: Boolean,
        val hevcMain10: Boolean,
    )

    private fun describeEncoders(): EncoderSummary {
        val rows = mutableListOf<InfoRow>()
        var hevc = false
        var hevcMain10 = false

        val encoders = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        } catch (e: Exception) {
            rows += InfoRow("Encoders", "no se pudo listar: ${e.message}", Support.UNKNOWN)
            emptyList()
        }

        val mimes = listOf(
            "video/avc" to "H.264",
            "video/hevc" to "HEVC",
            "video/av01" to "AV1",
            "audio/mp4a-latm" to "AAC",
        )
        for ((mime, label) in mimes) {
            val matching = encoders.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            if (matching.isEmpty()) {
                rows += InfoRow(label, "no disponible", Support.NO)
                continue
            }
            for (info in matching) {
                val caps = try {
                    info.getCapabilitiesForType(mime)
                } catch (e: Exception) {
                    null
                } ?: continue

                val hardware = info.isHardwareAccelerated
                val parts = mutableListOf(if (hardware) "hardware" else "software")
                val video = caps.videoCapabilities
                if (video != null) {
                    parts += "máx ${video.supportedWidths.upper}×${video.supportedHeights.upper}"
                    parts += "hasta ${video.bitrateRange.upper / 1_000_000} Mbps"
                    parts += "4K30 ${Labels.yesNo(video.areSizeAndRateSupported(3840, 2160, 30.0))}"
                    parts += "4K60 ${Labels.yesNo(video.areSizeAndRateSupported(3840, 2160, 60.0))}"
                    parts += "1080p60 ${Labels.yesNo(video.areSizeAndRateSupported(1920, 1080, 60.0))}"
                }
                val main10 = mime == "video/hevc" && caps.profileLevels.any {
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                }
                if (main10) parts += "Main10 (10-bit)"
                if (mime == "video/hevc" && hardware) {
                    hevc = true
                    if (main10) hevcMain10 = true
                }
                rows += InfoRow("$label · ${info.name}", parts.joinToString(" · "), if (hardware) Support.YES else Support.PARTIAL)
            }
        }
        return EncoderSummary(InfoSection("Encoders", rows), hevc, hevcMain10)
    }

    // ---------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------

    private fun characteristicsOrNull(id: String, notes: MutableList<String>): CameraCharacteristics? =
        try {
            cameraManager.getCameraCharacteristics(id)
        } catch (e: Exception) {
            notes += "No se pudieron leer las características de la cámara $id: ${e.message}"
            null
        }

    private fun capabilities(chars: CameraCharacteristics): Set<Int> =
        chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()

    /** Resoluciones ≥ 720p que acepta un encoder de video, con sus FPS máximos. */
    private fun videoModes(map: StreamConfigurationMap?): List<Pair<Size, Double>> {
        if (map == null) return emptyList()
        val sizes = map.getOutputSizes(MediaRecorder::class.java) ?: return emptyList()
        return sizes
            .filter { it.width.toLong() * it.height >= 1280L * 720L }
            .sortedByDescending { it.width.toLong() * it.height }
            .map { size ->
                size to Labels.fpsFromFrameDuration(map.getOutputMinFrameDuration(MediaRecorder::class.java, size))
            }
    }

    private fun maxFpsFor(modes: List<Pair<Size, Double>>, width: Int, height: Int): Double? =
        modes.firstOrNull { it.first.width == width && it.first.height == height }?.second

    @Suppress("UNCHECKED_CAST")
    private fun rawValue(chars: CameraCharacteristics, key: CameraCharacteristics.Key<*>): Any? =
        chars.get(key as CameraCharacteristics.Key<Any>)

    private fun formatValue(value: Any?): String {
        val text = when (value) {
            null -> "—"
            is IntArray -> value.joinToString(", ")
            is LongArray -> value.joinToString(", ")
            is FloatArray -> value.joinToString(", ")
            is DoubleArray -> value.joinToString(", ")
            is BooleanArray -> value.joinToString(", ")
            is ByteArray -> "${value.size} bytes"
            is Array<*> -> value.joinToString(", ") { formatValue(it) }
            else -> value.toString()
        }
        return if (text.length > 400) text.take(400) + "…" else text
    }
}

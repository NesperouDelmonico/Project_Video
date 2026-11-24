package com.nesperoudelmonico.projectvideo.diagnostics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Prueba activa: abre la cámara (sin grabar a disco), envía ajustes manuales y compara lo pedido
 * con lo que el HAL reporta en CaptureResult y con el brillo real de la imagen. Además mide los
 * FPS reales alimentando un encoder HEVC de hardware, igual que lo hará la grabación.
 *
 * Motivo: en equipos LIMITED como el Galaxy A36 falta la capacidad MANUAL_SENSOR, que es una
 * garantía del conjunto completo, pero el equipo anuncia AE/AF/AWB en OFF y
 * READ_SENSOR_SETTINGS. Algunos controles pueden funcionar por separado; solo se sabe probando.
 *
 * Cada bloque abre y cierra la cámara por separado, para que un error del HAL en una prueba no
 * arrastre a las siguientes.
 */
class ManualControlProbe(context: Context) {

    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)
    private val lastLuma = AtomicReference<Double?>(null)
    private val deviceErrors = Collections.synchronizedList(mutableListOf<String>())

    private lateinit var handler: Handler
    private lateinit var executor: Executor

    private data class FpsCase(val cameraId: String, val cameraLabel: String, val size: Size, val fps: Int)

    @SuppressLint("MissingPermission")
    suspend fun run(mainCameraId: String): InfoSection {
        val rows = mutableListOf<InfoRow>()
        val thread = HandlerThread("manual-probe").apply { start() }
        handler = Handler(thread.looper)
        executor = Executor { handler.post(it) }

        try {
            val ultraWideId = findUltraWide(mainCameraId)

            withCamera(rows, mainCameraId, "Principal · controles manuales") { device, chars ->
                probeManualControls(device, chars, "Principal")
            }
            if (ultraWideId != null) {
                withCamera(rows, ultraWideId, "Gran angular · controles manuales") { device, chars ->
                    probeManualControls(device, chars, "Gran angular (cámara $ultraWideId)")
                }
            }

            val fpsCases = buildList {
                add(FpsCase(mainCameraId, "Principal", Size(3840, 2160), 30))
                add(FpsCase(mainCameraId, "Principal", Size(3840, 2160), 24))
                add(FpsCase(mainCameraId, "Principal", Size(1920, 1080), 30))
                if (ultraWideId != null) add(FpsCase(ultraWideId, "Gran angular", Size(1920, 1080), 30))
            }
            for (case in fpsCases) {
                val label = "${case.cameraLabel} · ${case.size.width}×${case.size.height} pidiendo ${case.fps} fps"
                withCamera(rows, case.cameraId, label) { device, _ ->
                    listOf(probeFrameRate(device, label, case.size, case.fps))
                }
            }

            if (deviceErrors.isNotEmpty()) {
                rows += InfoRow("Errores reportados por la cámara", deviceErrors.joinToString("; "), Support.PARTIAL)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rows += InfoRow("Prueba activa", "Se interrumpió: ${e.javaClass.simpleName}: ${e.message}", Support.UNKNOWN)
        } finally {
            thread.quitSafely()
        }
        return InfoSection("Prueba activa", rows)
    }

    /** Trasera distinta de la principal y con focal más corta: la ultra gran angular. */
    private fun findUltraWide(mainId: String): String? {
        val mainFocal = focal(mainId) ?: return null
        return manager.cameraIdList
            .filter { it != mainId }
            .filter { id ->
                runCatching { manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) }
                    .getOrNull() == CameraCharacteristics.LENS_FACING_BACK
            }
            .mapNotNull { id -> focal(id)?.let { id to it } }
            .filter { (_, f) -> f < mainFocal }
            .minByOrNull { (_, f) -> f }
            ?.first
    }

    private fun focal(id: String): Float? = runCatching {
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
    }.getOrNull()

    /** Abre la cámara, ejecuta el bloque y la cierra; un fallo queda anotado en [rows]. */
    @SuppressLint("MissingPermission")
    private suspend fun withCamera(
        rows: MutableList<InfoRow>,
        cameraId: String,
        label: String,
        block: suspend (CameraDevice, CameraCharacteristics) -> List<InfoRow>,
    ) {
        var device: CameraDevice? = null
        val errorsBefore = deviceErrors.size
        try {
            val chars = manager.getCameraCharacteristics(cameraId)
            val opened = withTimeout(STEP_TIMEOUT_MS) { openCamera(cameraId) }
            device = opened
            rows += block(opened, chars)
        } catch (e: SecurityException) {
            rows += InfoRow(label, "falta el permiso de cámara", Support.UNKNOWN)
        } catch (e: TimeoutCancellationException) {
            rows += InfoRow(label, "tiempo agotado esperando a la cámara${lastErrorSince(errorsBefore)}", Support.UNKNOWN)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rows += InfoRow(label, "error: ${e.javaClass.simpleName}: ${e.message}${lastErrorSince(errorsBefore)}", Support.UNKNOWN)
        } finally {
            device?.close()
            delay(CLOSE_GRACE_MS)
        }
    }

    private fun lastErrorSince(index: Int): String =
        synchronized(deviceErrors) { deviceErrors.drop(index).lastOrNull() }?.let { " ($it)" } ?: ""

    // ---------------------------------------------------------------------------------------
    // Exposición, enfoque y balance de blancos
    // ---------------------------------------------------------------------------------------

    private suspend fun probeManualControls(
        device: CameraDevice,
        chars: CameraCharacteristics,
        prefix: String,
    ): List<InfoRow> {
        val rows = mutableListOf<InfoRow>()
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val yuvSize = map?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.filter { it.width.toLong() * it.height <= 1280L * 720L }
            ?.maxByOrNull { it.width.toLong() * it.height }
            ?: Size(640, 480)

        val reader = ImageReader.newInstance(yuvSize.width, yuvSize.height, ImageFormat.YUV_420_888, 4)
        lastLuma.set(null)
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                lastLuma.set(meanLuma(image))
            } finally {
                image.close()
            }
        }, handler)

        try {
            val session = createSession(device, listOf(reader.surface))
            fun base(): CaptureRequest.Builder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }

            // 1) Referencia en automático.
            val auto = settle(session, base())
            rows += InfoRow(
                "$prefix · referencia en automático",
                "ISO ${auto.get(CaptureResult.SENSOR_SENSITIVITY) ?: "?"} · " +
                    "${auto.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { Labels.exposureTime(it) } ?: "?"} · " +
                    "brillo ${luma()}",
            )

            // 2) Exposición manual: dos combinaciones; la segunda deja entrar ~2x más luz.
            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val targets = listOf(400 to 2_000_000L, 100 to 16_666_666L).map { (iso, exp) ->
                clamp(iso, isoRange) to clamp(exp, expRange)
            }
            var matched = 0
            val details = mutableListOf<String>()
            val lumas = mutableListOf<Double?>()
            for ((iso, exp) in targets) {
                val result = settle(session, base().apply {
                    set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                    set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
                    set(CaptureRequest.SENSOR_FRAME_DURATION, FRAME_DURATION_30FPS)
                })
                val gotIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                val gotExp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                val ok = gotIso != null && gotExp != null &&
                    near(gotIso.toDouble(), iso.toDouble()) && near(gotExp.toDouble(), exp.toDouble())
                if (ok) matched++
                lumas += lastLuma.get()
                details += "pedido ISO $iso · ${Labels.exposureTime(exp)} → obtenido ISO ${gotIso ?: "?"} · " +
                    "${gotExp?.let { Labels.exposureTime(it) } ?: "?"} (brillo ${luma()})"
            }
            rows += InfoRow(
                "$prefix · ISO y obturación manuales (AE OFF)",
                details.joinToString("; "),
                when (matched) {
                    targets.size -> Support.YES
                    0 -> Support.NO
                    else -> Support.PARTIAL
                },
            )
            rows += brightnessRow(prefix, lumas.getOrNull(0), lumas.getOrNull(1))

            // 3) Enfoque manual: infinito y luego cerca.
            val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            if (minFocus > 0f) {
                val focusTargets = listOf(0f, minOf(5f, minFocus))
                var focusMatched = 0
                val focusDetails = mutableListOf<String>()
                for (target in focusTargets) {
                    val result = settle(session, base().apply {
                        set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                        set(CaptureRequest.LENS_FOCUS_DISTANCE, target)
                    })
                    val got = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
                    if (got != null && abs(got - target) <= FOCUS_TOLERANCE_DIOPTERS) focusMatched++
                    focusDetails += String.format(
                        Locale.US,
                        "pedido %.1f D → obtenido %s",
                        target,
                        got?.let { String.format(Locale.US, "%.2f D", it) } ?: "?",
                    )
                }
                rows += InfoRow(
                    "$prefix · enfoque manual (AF OFF)",
                    focusDetails.joinToString("; "),
                    when (focusMatched) {
                        focusTargets.size -> Support.YES
                        0 -> Support.NO
                        else -> Support.PARTIAL
                    },
                )
            } else {
                rows += InfoRow("$prefix · enfoque manual", "lente de foco fijo (no aplica)", null)
            }

            // 4) Balance de blancos manual con ganancias RGGB.
            val gains = RggbChannelVector(2.2f, 1f, 1f, 1.4f)
            val wb = settle(session, base().apply {
                set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
                set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
                set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, IDENTITY_TRANSFORM)
            })
            val gotGains = wb.get(CaptureResult.COLOR_CORRECTION_GAINS)
            val gainsOk = gotGains != null &&
                near(gotGains.red.toDouble(), gains.red.toDouble()) &&
                near(gotGains.blue.toDouble(), gains.blue.toDouble())
            rows += InfoRow(
                "$prefix · balance de blancos manual (ganancias)",
                "pedido R ${gains.red} · B ${gains.blue} → obtenido " +
                    (gotGains?.let { String.format(Locale.US, "R %.2f · B %.2f", it.red, it.blue) } ?: "?") +
                    " · modo AWB reportado ${wb.get(CaptureResult.CONTROL_AWB_MODE)?.let { Labels.awbMode(it) } ?: "?"}",
                if (gainsOk) Support.YES else Support.NO,
            )

            session.stopRepeating()
            session.close()
        } finally {
            delay(CLOSE_GRACE_MS)
            reader.close()
        }
        return rows
    }

    private fun brightnessRow(prefix: String, first: Double?, second: Double?): InfoRow {
        val label = "$prefix · el brillo responde a la exposición"
        if (first == null || second == null) return InfoRow(label, "sin datos de imagen", Support.UNKNOWN)
        val value = String.format(Locale.US, "%.0f → %.0f (se esperaba ~2x más luz)", first, second)
        return when {
            first > 235 || second < 3 -> InfoRow(label, "$value · escena muy clara u oscura, repetir", Support.UNKNOWN)
            second > first * 1.2 -> InfoRow(label, value, Support.YES)
            else -> InfoRow(label, value, Support.NO)
        }
    }

    // ---------------------------------------------------------------------------------------
    // FPS reales alimentando un encoder HEVC de hardware
    // ---------------------------------------------------------------------------------------

    private suspend fun probeFrameRate(device: CameraDevice, label: String, size: Size, fps: Int): InfoRow {
        val sink = EncoderSink(size.width, size.height, fps)
        try {
            val session = createSession(device, listOf(sink.surface))
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(sink.surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
            }
            val framesBefore = sink.encodedFrames
            val start = System.nanoTime()
            val cameraFps = measureFps(session, builder)
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            val encoderFps = if (seconds > 0) (sink.encodedFrames - framesBefore) / seconds else 0.0

            session.stopRepeating()
            session.abortCaptures()
            session.close()

            val ok = cameraFps >= fps - 1.5
            return InfoRow(
                label,
                "cámara ${Labels.fps(cameraFps)} · encoder HEVC ${Labels.fps(encoderFps)}",
                if (ok) Support.YES else Support.NO,
            )
        } finally {
            delay(CLOSE_GRACE_MS)
            sink.close()
        }
    }

    /** Encoder HEVC de hardware que descarta lo que produce: solo cuenta frames. */
    private class EncoderSink(width: Int, height: Int, fps: Int) : AutoCloseable {
        private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        val surface: Surface

        @Volatile
        private var running = true

        @Volatile
        var encodedFrames = 0
            private set

        private val drainer: Thread

        init {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, if (width >= 3840) 40_000_000 else 16_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
            drainer = thread(name = "probe-encoder") {
                val info = MediaCodec.BufferInfo()
                while (running) {
                    val index = try {
                        codec.dequeueOutputBuffer(info, 10_000L)
                    } catch (e: IllegalStateException) {
                        break
                    }
                    if (index >= 0) {
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && info.size > 0) encodedFrames++
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        }

        override fun close() {
            running = false
            drainer.join(500)
            try {
                codec.stop()
            } catch (e: Exception) {
                // El codec ya pudo quedar en estado de error; se libera igual.
            }
            codec.release()
            surface.release()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Utilidades de Camera2 con corrutinas
    // ---------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(id: String): CameraDevice =
        suspendCancellableCoroutine { cont ->
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (cont.isActive) cont.resume(camera) else camera.close()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    deviceErrors += "cámara $id desconectada"
                    camera.close()
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("cámara desconectada"))
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    deviceErrors += "cámara $id: ${deviceErrorName(error)}"
                    camera.close()
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(deviceErrorName(error)))
                }
            }, handler)
        }

    private suspend fun createSession(device: CameraDevice, surfaces: List<Surface>): CameraCaptureSession =
        withTimeout(STEP_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val callback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cont.isActive) cont.resume(session) else session.close()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("el HAL rechazó la configuración de la sesión"))
                    }
                }
                device.createCaptureSession(
                    SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        surfaces.map { OutputConfiguration(it) },
                        executor,
                        callback,
                    ),
                )
            }
        }

    /** Repite la petición y devuelve el resultado tras [frames] cuadros, cuando 3A ya convergió. */
    private suspend fun settle(
        session: CameraCaptureSession,
        builder: CaptureRequest.Builder,
        frames: Int = SETTLE_FRAMES,
    ): TotalCaptureResult = withTimeout(STEP_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            var count = 0
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    count++
                    if (count == frames && cont.isActive) cont.resume(result)
                }
            }, handler)
        }
    }

    /** Mide los FPS reales con la mediana de los intervalos entre SENSOR_TIMESTAMP. */
    private suspend fun measureFps(session: CameraCaptureSession, builder: CaptureRequest.Builder): Double =
        withTimeout(FPS_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val stamps = ArrayList<Long>()
                var seen = 0
                session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        seen++
                        if (seen <= FPS_WARMUP_FRAMES || !cont.isActive) return
                        result.get(CaptureResult.SENSOR_TIMESTAMP)?.let { stamps += it }
                        if (stamps.size >= FPS_SAMPLE_FRAMES) {
                            val deltas = stamps.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
                            val median = deltas.getOrNull(deltas.size / 2) ?: 0L
                            cont.resume(Labels.fpsFromFrameDuration(median))
                        }
                    }
                }, handler)
            }
        }

    private fun deviceErrorName(error: Int): String = when (error) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "ERROR_CAMERA_IN_USE"
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "ERROR_MAX_CAMERAS_IN_USE"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "ERROR_CAMERA_DISABLED"
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "ERROR_CAMERA_DEVICE"
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "ERROR_CAMERA_SERVICE"
        else -> "error $error"
    }

    private fun luma(): String = lastLuma.get()?.let { String.format(Locale.US, "%.0f", it) } ?: "?"

    /** Brillo medio del plano Y (0–255), muestreando 1 de cada 16 píxeles por eje. */
    private fun meanLuma(image: Image): Double {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var sum = 0L
        var count = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val index = y * rowStride + x * pixelStride
                if (index < buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    count++
                }
                x += 16
            }
            y += 16
        }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }

    private fun clamp(value: Int, range: Range<Int>?): Int =
        if (range == null) value else value.coerceIn(range.lower, range.upper)

    private fun clamp(value: Long, range: Range<Long>?): Long =
        if (range == null) value else value.coerceIn(range.lower, range.upper)

    private fun near(actual: Double, target: Double, tolerance: Double = 0.12): Boolean =
        abs(actual - target) <= abs(target) * tolerance

    private companion object {
        const val STEP_TIMEOUT_MS = 6_000L
        const val FPS_TIMEOUT_MS = 10_000L
        const val CLOSE_GRACE_MS = 300L
        const val SETTLE_FRAMES = 20
        const val FPS_WARMUP_FRAMES = 15
        const val FPS_SAMPLE_FRAMES = 60
        const val FRAME_DURATION_30FPS = 33_333_333L
        const val FOCUS_TOLERANCE_DIOPTERS = 0.5f

        val IDENTITY_TRANSFORM = ColorSpaceTransform(
            intArrayOf(
                1, 1, 0, 1, 0, 1,
                0, 1, 1, 1, 0, 1,
                0, 1, 0, 1, 1, 1,
            ),
        )
    }
}

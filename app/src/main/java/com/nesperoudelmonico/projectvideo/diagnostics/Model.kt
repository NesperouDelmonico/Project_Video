package com.nesperoudelmonico.projectvideo.diagnostics

/** Grado de soporte de una función, para pintar el veredicto. */
enum class Support { YES, NO, PARTIAL, UNKNOWN }

data class InfoRow(
    val label: String,
    val value: String,
    val support: Support? = null,
)

data class InfoSection(
    val title: String,
    val rows: List<InfoRow>,
)

data class CameraReport(
    val id: String,
    val title: String,
    val sections: List<InfoSection>,
    val rawKeys: List<InfoRow>,
)

data class DiagnosticsReport(
    val generatedAt: String,
    val verdict: InfoSection,
    val device: InfoSection,
    val cameras: List<CameraReport>,
    val encoders: InfoSection,
    val notes: List<String>,
    /** Id de la cámara trasera principal, objetivo de la prueba activa. */
    val mainCameraId: String? = null,
    /** Resultado de [ManualControlProbe], si el usuario la ejecutó. */
    val activeProbe: InfoSection? = null,
)

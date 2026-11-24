package com.nesperoudelmonico.projectvideo.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatterTest {

    private val report = DiagnosticsReport(
        generatedAt = "2026-11-23 22:00:00",
        verdict = InfoSection(
            "Veredicto",
            listOf(
                InfoRow("ISO y obturación manuales", "No", Support.NO),
                InfoRow("4K @ 30 fps", "Sí", Support.YES),
            ),
        ),
        device = InfoSection("Dispositivo", listOf(InfoRow("Modelo", "SM-A366"))),
        cameras = listOf(
            CameraReport(
                id = "0",
                title = "Cámara 0 — Trasera (lógica)",
                sections = listOf(InfoSection("General", listOf(InfoRow("Nivel de hardware", "LIMITED")))),
                rawKeys = listOf(InfoRow("android.lens.facing", "1")),
            ),
        ),
        encoders = InfoSection("Encoders", listOf(InfoRow("HEVC · c2.qti.hevc.encoder", "hardware", Support.YES))),
        notes = listOf("Nota de prueba"),
    )

    @Test
    fun `incluye veredicto con iconos de soporte`() {
        val text = ReportFormatter.toText(report)
        assertTrue(text.startsWith("# Diagnóstico Camera2"))
        assertTrue(text.contains("- ❌ ISO y obturación manuales: No"))
        assertTrue(text.contains("- ✅ 4K @ 30 fps: Sí"))
    }

    @Test
    fun `incluye camaras, encoders y notas`() {
        val text = ReportFormatter.toText(report)
        assertTrue(text.contains("## Cámara 0 — Trasera (lógica)"))
        assertTrue(text.contains("### General"))
        assertTrue(text.contains("- Nivel de hardware: LIMITED"))
        assertTrue(text.contains("## Encoders"))
        assertTrue(text.contains("- Nota de prueba"))
    }

    @Test
    fun `las claves crudas se pueden omitir`() {
        assertTrue(ReportFormatter.toText(report).contains("android.lens.facing"))
        assertFalse(ReportFormatter.toText(report, includeRawKeys = false).contains("android.lens.facing"))
    }
}

package com.nesperoudelmonico.projectvideo.diagnostics

/** Convierte el reporte en texto Markdown para compartirlo o pegarlo en un issue. */
object ReportFormatter {

    fun toText(report: DiagnosticsReport, includeRawKeys: Boolean = true): String = buildString {
        appendLine("# Diagnóstico Camera2 — Project Video")
        appendLine("Generado: ${report.generatedAt}")
        appendLine()
        appendSection(report.verdict)
        report.activeProbe?.let { appendSection(it) }
        appendSection(report.device)
        for (camera in report.cameras) {
            appendLine("## ${camera.title}")
            appendLine()
            for (section in camera.sections) {
                appendSection(section, level = "###")
            }
            if (includeRawKeys && camera.rawKeys.isNotEmpty()) {
                appendSection(InfoSection("Claves crudas (${camera.rawKeys.size})", camera.rawKeys), level = "###")
            }
        }
        appendSection(report.encoders)
        if (report.notes.isNotEmpty()) {
            appendLine("## Notas")
            report.notes.forEach { appendLine("- $it") }
            appendLine()
        }
    }.trimEnd() + "\n"

    private fun StringBuilder.appendSection(section: InfoSection, level: String = "##") {
        appendLine("$level ${section.title}")
        for (row in section.rows) {
            val prefix = if (row.support != null) "${Labels.glyph(row.support)} " else ""
            appendLine("- $prefix${row.label}: ${row.value}")
        }
        appendLine()
    }
}

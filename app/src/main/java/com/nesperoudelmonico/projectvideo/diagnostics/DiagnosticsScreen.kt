package com.nesperoudelmonico.projectvideo.diagnostics

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface DiagnosticsState {
    data object Loading : DiagnosticsState
    data class Loaded(val report: DiagnosticsReport, val text: String) : DiagnosticsState
    data class Failed(val message: String) : DiagnosticsState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        refreshKey++
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val state by produceState<DiagnosticsState>(DiagnosticsState.Loading, refreshKey) {
        value = DiagnosticsState.Loading
        value = withContext(Dispatchers.Default) {
            try {
                val report = CameraDiagnostics(context.applicationContext).collect()
                DiagnosticsState.Loaded(report, ReportFormatter.toText(report))
            } catch (e: Exception) {
                DiagnosticsState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    val loaded = state as? DiagnosticsState.Loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico") },
                actions = {
                    TextButton(onClick = { refreshKey++ }) { Text("Actualizar") }
                    TextButton(
                        enabled = loaded != null,
                        onClick = { loaded?.let { copyToClipboard(context, it.text) } },
                    ) { Text("Copiar") }
                    TextButton(
                        enabled = loaded != null,
                        onClick = { loaded?.let { shareReport(context, it.text) } },
                    ) { Text("Compartir") }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            DiagnosticsState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DiagnosticsState.Failed -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text("No se pudo generar el diagnóstico: ${current.message}") }

            is DiagnosticsState.Loaded -> ReportList(
                report = current.report,
                hasCameraPermission = hasCameraPermission,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ReportList(
    report: DiagnosticsReport,
    hasCameraPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasCameraPermission) {
            item {
                NoticeCard("Sin permiso de cámara: algunas claves de Camera2 no estarán disponibles. Concede el permiso y toca Actualizar.")
            }
        }
        item { SectionCard(report.verdict, initiallyExpanded = true, highlight = true) }
        item { SectionCard(report.device, initiallyExpanded = true) }
        items(report.cameras, key = { it.id }) { camera -> CameraCard(camera) }
        item { SectionCard(report.encoders, initiallyExpanded = false) }
        if (report.notes.isNotEmpty()) {
            item { SectionCard(InfoSection("Notas", report.notes.map { InfoRow("Nota", it) }), initiallyExpanded = true) }
        }
        item {
            Text(
                text = "Generado ${report.generatedAt} · Usa Compartir para enviar el reporte completo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionCard(
    section: InfoSection,
    initiallyExpanded: Boolean,
    highlight: Boolean = false,
) {
    var expanded by rememberSaveable(section.title) { mutableStateOf(initiallyExpanded) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            ExpandableHeader(
                title = section.title,
                expanded = expanded,
                highlight = highlight,
                onToggle = { expanded = !expanded },
            )
            if (expanded) {
                Spacer(Modifier.padding(top = 8.dp))
                section.rows.forEach { InfoRowView(it) }
            }
        }
    }
}

@Composable
private fun CameraCard(camera: CameraReport) {
    var expanded by rememberSaveable(camera.id) { mutableStateOf(false) }
    var showRaw by rememberSaveable(camera.id + "-raw") { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            ExpandableHeader(camera.title, expanded, highlight = false, onToggle = { expanded = !expanded })
            if (expanded) {
                camera.sections.forEach { section ->
                    Spacer(Modifier.padding(top = 12.dp))
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    section.rows.forEach { InfoRowView(it) }
                }
                Spacer(Modifier.padding(top = 8.dp))
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Ocultar claves crudas" else "Ver claves crudas (${camera.rawKeys.size})")
                }
                if (showRaw) camera.rawKeys.forEach { InfoRowView(it) }
            }
        }
    }
}

@Composable
private fun ExpandableHeader(
    title: String,
    expanded: Boolean,
    highlight: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun InfoRowView(row: InfoRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (row.support != null) {
            Text(Labels.glyph(row.support), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(row.value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun shareReport(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Camera2 — Project Video")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartir diagnóstico"))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Camera2", text))
    Toast.makeText(context, "Reporte copiado", Toast.LENGTH_SHORT).show()
}

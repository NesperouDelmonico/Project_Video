# Project Video

[![Android APK](https://github.com/NesperouDelmonico/Project_Video/actions/workflows/android.yml/badge.svg)](https://github.com/NesperouDelmonico/Project_Video/actions/workflows/android.yml)

App Android que convierte el celular en una **cámara de acción** tipo GoPro / Insta360: control profesional de la grabación, modo bucle y manejo completo por voz.

> **Estado:** Fase 0. La app tiene una pantalla de **diagnóstico de cámara** que muestra qué controles soporta realmente el hardware del celular.
> Análisis técnico completo en [`docs/viabilidad.md`](docs/viabilidad.md).

## Descargar e instalar

1. Desde el celular, abre **[Releases → nightly](https://github.com/NesperouDelmonico/Project_Video/releases/tag/nightly)**. Ahí siempre está el APK del último commit en `main`.
2. Descarga `ProjectVideo-<build>-<commit>.apk` y ábrelo. Android pedirá permitir la instalación desde el navegador o el gestor de archivos.
3. Los APK de otras ramas quedan como *artifacts* en la pestaña **Actions** (requiere iniciar sesión en GitHub).

## Qué hace hoy (Fase 0)

**Diagnóstico Camera2**, sin abrir la cámara:

- **Veredicto** de la cámara trasera principal: nivel Camera2, ISO/obturación manuales, enfoque manual, balance de blancos manual, compensación EV, bloqueos AE/AWB, apertura, 4K30 / 4K60 / 1080p60, cámara lenta, OIS / EIS, HDR 10-bit, RAW y encoder HEVC.
- **Dispositivo:** modelo, SoC, versión de Android, estado térmico y espacio libre (con aviso si no alcanza para el modo bucle).
- **Cada cámara**, lógica y física: sensor, focales, aperturas, rangos de ISO y obturación, modos AE/AWB/AF, estabilización, resoluciones con sus FPS máximos, alta velocidad, zoom y perfiles HDR. Incluye todas las claves crudas de `CameraCharacteristics`.
- **Encoders** H.264 / HEVC / AV1 / AAC: si son de hardware, resolución y bitrate máximos, y soporte de 4K30, 4K60 y 1080p60.
- **Prueba activa:** abre la cámara unos segundos sin grabar y verifica qué ajustes manuales aplica realmente el hardware, aunque no anuncie `MANUAL_SENSOR`. Compara lo pedido con lo que reporta `CaptureResult` y con el brillo real de la imagen, y mide los FPS reales en 4K y 1080p.
- **Exportar:** *Compartir* envía el reporte como archivo adjunto, *Guardar* lo deja en `Descargas/ProjectVideo/` y *Copiar* lo pone en el portapapeles.

Resultados del dispositivo de referencia en [`docs/dispositivos/galaxy-a36.md`](docs/dispositivos/galaxy-a36.md): Camera2 LIMITED, video 1080p30 / 4K24 / 720p240 y sin estabilización expuesta.

## Funcionalidades planeadas

### Esenciales
- **Estabilización propia:** el A36 no expone EIS ni OIS a terceros. Primero se guardará un registro del giroscopio (`.gcsv`) junto a cada video para estabilizarlo en [Gyroflow]; después vendrá un EIS en tiempo real por GPU.
- **Controles de video:** resolución, FPS, códec (H.264/HEVC), bitrate, ISO, obturación, compensación de exposición, balance de blancos, enfoque, estabilización y zoom. Solo se muestran los controles que el hardware soporta (según el diagnóstico).
- La apertura se muestra como dato informativo: en los celulares es fija.

### Específicas
- **Modo bucle:** graba continuamente con una ventana deslizante de **5, 10 o 15 minutos**. Al superarla, sigue grabando y descarta el principio, como una tubería de largo fijo. Al detener, guarda un único video con los últimos *N* minutos. Incluye "guardar momento" para proteger un fragmento.
- **Modo voz:** comandos hablados offline con [Vosk](https://alphacephei.com/vosk/) ("cámara, graba", "cámara, modo bucle", "cámara, guarda"…). La app responde en voz alta con frases pre-generadas con [Fish Audio](https://fish.audio).

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje / UI | Kotlin 2.2 + Jetpack Compose (Material 3) |
| Build | Gradle 8.14 + Android Gradle Plugin 8.13 · `minSdk 29` · `targetSdk 36` |
| Cámara y grabación | Camera2 + MediaCodec + MediaMuxer |
| Reconocimiento de voz | Vosk offline (`vosk-model-small-es`) |
| Voz de respuesta | Fish Audio (pre-generada en CI) + TTS nativo como respaldo |
| CI/CD | GitHub Actions: APK por commit + release `nightly` ([detalles](docs/ci.md)) |

## Estructura

```
app/src/main/java/com/nesperoudelmonico/projectvideo/
├── MainActivity.kt
├── diagnostics/
│   ├── CameraDiagnostics.kt   # Lee Camera2 y MediaCodec y arma el reporte
│   ├── DiagnosticsScreen.kt   # UI Compose del diagnóstico
│   ├── ManualControlProbe.kt  # Prueba activa: aplica ajustes manuales y verifica el resultado
│   ├── ReportStorage.kt       # Guarda el reporte en Descargas
│   ├── Labels.kt              # Traducción de constantes a texto (con tests)
│   ├── Model.kt
│   └── ReportFormatter.kt     # Exportación a texto Markdown (con tests)
└── ui/theme/Theme.kt
docs/
├── viabilidad.md              # Análisis técnico y decisiones
├── dispositivos/galaxy-a36.md # Resultados del diagnóstico en el equipo de referencia
└── ci.md                      # Workflow, firma del APK, compilación local
```

## Compilar localmente

Requiere JDK 17+ y Android SDK (Android Studio).

```bash
./gradlew assembleRelease      # APK en app/build/outputs/apk/release/
./gradlew testDebugUnitTest    # tests unitarios
```

## Hoja de ruta

- [x] Exploración de viabilidad
- [x] **Fase 0:** proyecto base + GitHub Actions (APK por commit) + diagnóstico Camera2
- [ ] **Fase 1:** preview, grabación, panel de controles según capacidades y registro de giroscopio para Gyroflow
- [ ] **Fase 2:** modo bucle (ventana 5 / 10 / 15 min)
- [ ] **Fase 3:** comandos de voz (Vosk)
- [ ] **Fase 4:** feedback por voz con Fish Audio
- [ ] **Fase 5:** estabilización en tiempo real (EIS por GPU)

## Dispositivo de pruebas

Samsung Galaxy A36 (Snapdragon 6 Gen 3, Android 15+).

[Gyroflow]: https://gyroflow.xyz

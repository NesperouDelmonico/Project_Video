# Project Video

App Android que convierte el celular en una **cámara de acción** tipo GoPro / Insta360: control profesional de la grabación, modo bucle y manejo completo por voz.

> **Estado:** exploración de viabilidad completada · Sin código todavía.
> Ver el análisis completo en [`docs/viabilidad.md`](docs/viabilidad.md).

## Funcionalidades planeadas

### Esenciales
- **Controles de video:** resolución, FPS, códec (H.264/HEVC), bitrate, ISO, obturación, compensación de exposición, balance de blancos, enfoque, estabilización y zoom. Se muestran solo los controles que el hardware del equipo soporta realmente.
- La apertura se muestra como dato informativo: en los celulares es fija.

### Específicas
- **Modo bucle:** graba continuamente y conserva solo los últimos 10 minutos. Al detener, guarda ese tramo como un único video. Incluye "guardar momento" para proteger un fragmento.
- **Modo voz:** comandos hablados offline ("cámara, graba", "cámara, modo bucle", "cámara, guarda"…) para no tocar el celular. La app responde en voz alta, con voces generadas con [Fish Audio](https://fish.audio).

## Stack (propuesto, pendiente de confirmar)

| Capa | Tecnología |
|---|---|
| Lenguaje / UI | Kotlin + Jetpack Compose |
| Cámara y grabación | Camera2 + MediaCodec + MediaMuxer |
| Reconocimiento de voz | Vosk offline (`vosk-model-small-es`) |
| Voz de respuesta | Fish Audio (pre-generada en CI) + TTS nativo como respaldo |
| CI/CD | GitHub Actions → APK por cada commit |

## Hoja de ruta

- [x] Exploración de viabilidad
- [ ] **Fase 0:** proyecto base + GitHub Actions (APK por commit) + pantalla de diagnóstico Camera2
- [ ] **Fase 1:** preview, grabación y panel de controles
- [ ] **Fase 2:** modo bucle
- [ ] **Fase 3:** comandos de voz
- [ ] **Fase 4:** feedback por voz con Fish Audio

## Descargar el APK

Cuando exista la Fase 0, cada commit generará un APK. El último de `main` estará en la sección **Releases → nightly** del repositorio.

## Dispositivo de pruebas

Samsung Galaxy A36 (Snapdragon 6 Gen 3, Android 15+).

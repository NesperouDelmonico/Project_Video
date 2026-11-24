# Exploración de viabilidad — Project Video

> Fecha: 2026-11-23 · Estado: exploración inicial, antes de escribir código.
> Dispositivo de referencia para pruebas: **Samsung Galaxy A36** (Snapdragon 6 Gen 3, Android 15+, One UI 7).

## Decisiones tomadas (2026-11-23)

- **Stack:** Kotlin + Jetpack Compose + Camera2 (opción A del §1).
- **Apertura:** fija según el hardware; se muestra como dato informativo.
- **ISO / obturación:** se habilitan solo si el diagnóstico (Fase 0) confirma `MANUAL_SENSOR` o si la prueba activa demuestra que el HAL los aplica.
- **Modo bucle:** ventana deslizante configurable de 5 / 10 / 15 min (§3).
- **Reconocimiento de voz:** Vosk offline.
- **Voz de respuesta:** frases pre-generadas con Fish Audio en CI.

## Resultados en el dispositivo de referencia

El diagnóstico del Galaxy A36 confirmó las restricciones esperadas y agregó una importante. Detalle en [`docs/dispositivos/galaxy-a36.md`](dispositivos/galaxy-a36.md).
- Camera2 **LIMITED**, sin `MANUAL_SENSOR`, pero con AE/AF/AWB en OFF anunciados: falta la prueba activa.
- Video para terceros: **1080p30 y 4K24**, sin 60 fps. Cámara lenta 720p a 120/240 fps.
- **Sin EIS ni OIS para terceros:** la estabilización pasa a ser trabajo propio (registro de giroscopio para Gyroflow y, más adelante, EIS por GPU).

## Veredicto

**El proyecto es viable en Android**, con cuatro ajustes respecto a la idea original:

| # | Idea original | Realidad | Ajuste |
|---|---|---|---|
| 1 | Controlar la **apertura** como en una cámara profesional | Casi todos los celulares tienen apertura **fija** (el A36 principal es f/1.8 fijo). Camera2 solo permite cambiarla si `LENS_INFO_AVAILABLE_APERTURES` lista más de un valor. | Mostrar la apertura como dato de solo lectura. Ofrecer ISO, obturación, compensación de exposición, balance de blancos, enfoque, zoom, FPS, resolución, bitrate y códec. |
| 2 | ISO y velocidad de obturación manuales | Requieren la capacidad `MANUAL_SENSOR` de Camera2. La gama A de Samsung suele exponer Camera2 en nivel **LIMITED**, y hay quejas de usuarios del A36 por restricciones (sobre todo en ultra gran angular, macro y frontal). | **Verificar en el A36 lo primero** (app de sondeo, Fase 0). La UI se arma según las capacidades que reporte cada cámara: si no hay modo manual, se cae a compensación EV + bloqueos AE/AWB. |
| 3 | Modo bucle de 5/10/15 min | Es viable, pero **no** con `MediaRecorder` ni con el `Recorder` de CameraX: `setNextOutputFile()` corta por tamaño de archivo, no por tiempo, y reiniciar la grabación deja huecos. | Pipeline propio: Camera2 → `MediaCodec` (H.264/HEVC) → `MediaMuxer`, rotando segmentos de 30 s en keyframes, y uniendo al final sin recodificar. |
| 4 | Comandos de voz con wake word (tipo Porcupine) | **El free tier de Picovoice se cerró el 30 de junio de 2026** y las claves gratuitas dejaron de funcionar. El siguiente plan cuesta miles de USD al año. | **Vosk** offline (Apache 2.0) con el modelo `vosk-model-small-es-0.42` (39 MB) y una gramática cerrada de comandos. |

Fish Audio sirve para la voz de respuesta, pero conviene usarlo **offline primero**: la voz se pre-genera en CI y se empaqueta en el APK (ver §5).

---

## 1. Stack recomendado

Casi todo lo difícil de este proyecto es nativo de Android: Camera2, `MediaCodec`, `AudioRecord`, foreground service y reconocimiento de voz. La UI es un visor con HUD.

| Opción | A favor | En contra |
|---|---|---|
| **A. Kotlin + Jetpack Compose** (recomendada) | Una sola capa: el motor de cámara, el servicio y la UI comparten proceso y estado sin puentes. Ejemplos y documentación de Camera2 directamente aplicables. | No reutiliza Flutter. Solo Android (el APK es el objetivo, así que da igual). |
| **B. Flutter (UI) + plugin nativo Kotlin** | Reutiliza experiencia en Flutter. Deja la puerta abierta a iOS. | El plugin oficial `camera` no expone ISO/obturación; los plugins "manual" de pub.dev están abandonados o son experimentales. Igual habría que escribir el motor completo en Kotlin, más un puente (Pigeon/MethodChannel) y el preview por `Texture`. Doble trabajo. |

**Recomendación:** opción A. Se decide antes de crear el proyecto, porque cambia la estructura del repo y el workflow de CI.

Base técnica propuesta:
- `minSdk 29` (Android 10: captura de audio concurrente, API térmica) · `targetSdk` actual.
- Camera2 directo para el motor de grabación. CameraX solo para lo que facilite sin bloquear el pipeline propio.
- Arquitectura: `CameraEngine` (sesión Camera2 + encoders) dentro de un `RecordingService` (foreground service `camera|microphone`), y la UI Compose observa su estado.

## 2. Controles de cámara

Todo se consulta en tiempo de ejecución con `CameraCharacteristics`; **nada se asume por modelo**.

| Control | Clave Camera2 | Disponibilidad esperada en el A36 |
|---|---|---|
| Resolución / FPS | `StreamConfigurationMap`, `CONTROL_AE_TARGET_FPS_RANGE` | 4K@30, 1080p@30/60 (cámara trasera principal). |
| Códec / bitrate | `MediaCodecList` | H.264 seguro; HEVC muy probable; 10-bit HDR por verificar. |
| ISO | `SENSOR_SENSITIVITY` + `CONTROL_AE_MODE_OFF` | ⚠️ Depende de `MANUAL_SENSOR`. **Verificar.** |
| Obturación | `SENSOR_EXPOSURE_TIME` | ⚠️ Igual que ISO. Debe ser ≤ duración del frame (1/30 s a 30 fps). |
| Compensación EV | `CONTROL_AE_EXPOSURE_COMPENSATION` | ✅ Disponible incluso en nivel LIMITED. |
| Bloqueo AE / AWB | `CONTROL_AE_LOCK`, `CONTROL_AWB_LOCK` | ✅ Muy probable. |
| Balance de blancos | `CONTROL_AWB_MODE` (presets) / `COLOR_CORRECTION_*` (manual) | Presets ✅; Kelvin manual depende de `MANUAL_POST_PROCESSING`. |
| Enfoque | `CONTROL_AF_MODE`, `LENS_FOCUS_DISTANCE` | AF continuo ✅; distancia manual por verificar. |
| Estabilización | `CONTROL_VIDEO_STABILIZATION_MODE`, `LENS_OPTICAL_STABILIZATION_MODE` | EIS ✅ (el A36 anuncia gyro-EIS; la trasera tiene OIS). |
| Apertura | `LENS_APERTURE` | ❌ Fija. Solo lectura. |
| Zoom / lente | `CONTROL_ZOOM_RATIO`, cámaras lógicas/físicas | Zoom digital ✅; acceso a ultra gran angular por verificar. |

Las versiones recientes de Android agregaron modos de prioridad de exposición (`CONTROL_AE_PRIORITY_MODE`: prioridad a obturación o a ISO), cercanos a los modos S/Tv de una cámara. Habría que ver si el A36 los expone.

## 3. Modo bucle: ventana deslizante de 5 / 10 / 15 minutos

**Definición (confirmada):** la cámara graba sin parar. Mientras lo grabado no llega a la duración elegida (5, 10 o 15 min), se conserva todo. Cuando la supera, se sigue grabando y se descarta el **principio**: funciona como una tubería de largo fijo, donde lo que entra por delante empuja y saca lo más viejo por detrás. Al detener, se guarda un único video con exactamente los últimos *N* minutos.

Funciona igual que el bucle de una dashcam: un buffer circular **en disco**. En RAM no cabe: 10 minutos a 1080p ocupan más de 1 GB.

```
Camera2 ──► MediaCodec (video) ─┐
AudioRecord ─► MediaCodec (AAC) ┴─► SegmentMuxer ─► seg_0001.mp4, seg_0002.mp4, …
                                          │
                       Ventana = N min (5/10/15). Cuando lo grabado supera N + 1 segmento,
                       borra el segmento más viejo (la "cola" de la tubería)
                                          │
                     Al detener ─► une los segmentos (remux sin recodificar)
                                   y recorta el inicio para dejar exactamente N:00 ─► galería
```

La "tubería" se corta por segmentos, no frame a frame. Reescribir un MP4 continuamente para quitarle el principio no es viable en un celular: habría que reescribir gigas de datos en cada corte. Con segmentos, borrar la cola cuesta un `delete()`. El recorte fino al segundo exacto se hace una sola vez, al detener.

Decisiones:
- **Duración configurable: 5, 10 o 15 min** (y cualquier otro valor después, si se quiere).
- **Segmentos de 30 s**, con keyframe cada 1 s. Con segmentos más cortos, la tubería avanza a pasos más finos y el disco nunca guarda más de *N* min + 30 s.
- Se conservan **⌈N / 30 s⌉ + 1 segmentos**: 11 para 5 min, 21 para 10 min y 31 para 15 min.
- La unión final usa `MediaExtractor` + `MediaMuxer`, sin recodificar: es rápida y no pierde calidad.
- **"Guardar momento"** (como los clips protegidos de dashcam): un comando marca los segmentos actuales para que no se sobrescriban. Es poco esfuerzo extra y muy útil.

Espacio necesario para el buffer (ventana de 10 min; para 5 min es la mitad y para 15 min, 1,5 veces):

| Perfil | Bitrate aprox. | Espacio |
|---|---|---|
| 1080p30 H.264 | 17 Mbps | ~1,4 GB |
| 1080p60 H.264 | 25 Mbps | ~2,1 GB |
| 4K30 HEVC | 35 Mbps | ~2,9 GB |
| 4K30 H.264 | 50 Mbps | ~4,1 GB |

La app debe verificar el espacio libre antes de iniciar el bucle y negarse (con aviso por voz) si no alcanza.

## 4. Modo voz (comandos)

### Motor de reconocimiento
| Opción | Veredicto |
|---|---|
| `SpeechRecognizer` de Android | ❌ Pelea por el micrófono con la grabación, suele requerir red, hace pitidos y no está pensado para escucha continua. |
| Picovoice Porcupine/Rhino | ❌ Sin free tier desde el 30/06/2026. |
| **Vosk** (`vosk-model-small-es-0.42`, 39 MB, Apache 2.0) | ✅ Offline, escucha continua, y los modelos pequeños permiten **restringir el vocabulario** a una gramática. Con pocos comandos, la precisión sube mucho frente a dictado libre. |
| Whisper on-device | Más preciso, pero pesado para escucha continua en un gama media que además está codificando video. Queda como alternativa futura. |

### Diseño
- Palabra de activación + comando, con gramática cerrada, por ejemplo: *"cámara, graba"*, *"cámara, para"*, *"cámara, modo bucle"*, *"cámara, guarda"*, *"cámara, foto"*, *"cámara, 4K"*, *"cámara, sesenta cuadros"*, *"cámara, batería"*, *"cámara, estado"*.
- **Un solo `AudioRecord`** alimenta a la vez el encoder AAC (audio del video) y Vosk (PCM 16 kHz remuestreado). Así no hay disputa por el micrófono. En Android 10+, dos `AudioRecord` de la misma app pueden coexistir, pero eso no está documentado oficialmente; conviene no depender de ello.
- Mientras la app habla, se **pausa el reconocimiento** para que no se dispare con su propia voz.
- Funciona con pantalla apagada mientras el foreground service esté activo.

### Riesgos
- **Ruido y viento** en exteriores (el uso típico de una action cam). Mitigaciones: gramática cerrada, umbral de confianza, confirmación hablada para comandos destructivos ("¿Detengo la grabación?").
- **Las respuestas habladas quedan grabadas en el video**, igual que los pitidos de una GoPro. Opciones a definir: volumen bajo, solo tonos durante la grabación, o salida por audífonos Bluetooth.

## 5. Feedback por voz con Fish Audio

Datos actuales de la API:
- `s2.1-pro`: USD 15 por millón de bytes UTF-8 (≈12 h de voz). `s2.1-pro-free`: USD 0, bajo política de uso justo.
- Límite de 5 peticiones concurrentes en el plan inicial; al excederlo responde 429.
- Tiene REST y WebSocket con streaming.

Problemas de llamarla en vivo desde el celular:
1. **Sin red no hay voz**, y una action cam se usa justo donde la red falla.
2. **Latencia** de red en cada respuesta.
3. **La API key quedaría dentro del APK**, y un APK se descompila en minutos.

**Diseño propuesto: "voice pack" pre-generado**
- Un archivo `voice/frases.yaml` en el repo con todas las respuestas fijas ("Grabando", "Modo bucle activado, diez minutos", "Batería al veinte por ciento", "Espacio insuficiente"…).
- Un paso de **GitHub Actions** genera los audios con Fish Audio, usando la key guardada como *secret* del repo (`FISH_API_KEY`), y los empaqueta en el APK. Se cachea por hash de `frases.yaml`, así solo se llama a la API cuando cambian las frases.
- Las respuestas con datos variables (números, porcentajes) se arman concatenando clips ("batería" + "veinte" + "por ciento"). Como respaldo queda el `TextToSpeech` nativo de Android, offline.
- Si más adelante se quieren respuestas realmente dinámicas (por ejemplo, con un LLM), se pone un backend mínimo propio que guarde la key. Eso queda fuera del alcance actual.

Costo: un pack de unas 200 frases es del orden de kilobytes de texto, así que con el modelo pago son centavos y con el gratuito, cero.

## 6. Otros riesgos del sistema

| Riesgo | Impacto | Mitigación |
|---|---|---|
| **Sin estabilización del fabricante** (A36: EIS y OIS no expuestos) | Video tembloroso, justo el caso de uso de una action cam | Registro de giroscopio para Gyroflow (Fase 1) y EIS propio por GPU (Fase 5). |
| **Sobrecalentamiento** en grabaciones largas (sobre todo 4K) | El sistema puede bajar FPS o cerrar la cámara | Escuchar `PowerManager.addThermalStatusListener`, bajar a 1080p automáticamente y avisar por voz. Bucle en 1080p por defecto. |
| **Restricciones de foreground service** (Android 14+) | Un servicio `camera`/`microphone` no puede arrancar con la app en segundo plano | El servicio se inicia siempre desde la UI visible. Luego se puede apagar la pantalla. |
| **Consumo de batería** | Grabación + reconocimiento continuo | Indicador en HUD, aviso por voz en umbrales, modo "solo voz" con preview apagado. |
| **Fragmentación de Camera2** entre fabricantes | Funciones que no existen en otros equipos | UI dirigida por capacidades y una pantalla de diagnóstico que muestre qué soporta el equipo. |
| Desgaste de almacenamiento por escritura continua | Bajo en sesiones normales | Informativo; nada que hacer en v1. |

## 7. CI/CD (GitHub Actions)

- **En cada push:** compilar, correr tests y lint, y generar el APK.
- El APK se sube como *artifact* del workflow. Además, en `main` se publica en un **pre-release rodante** (`nightly`), para poder descargarlo directo desde el celular sin entrar a la pestaña Actions.
- **Firma:** el keystore va en GitHub Secrets (base64). Si es el mismo en todos los builds, el APK se actualiza encima de la versión instalada sin desinstalar.
- El paso del voice pack (§5) entra al workflow cuando exista `voice/frases.yaml`.
- Implementado en `.github/workflows/android.yml`; detalles y configuración de la firma en [`docs/ci.md`](ci.md).

## 8. Plan por fases

| Fase | Entregable | Valida |
|---|---|---|
| **0 — Sondeo + CI** (implementada) | Proyecto base, workflow de APK y una pantalla de **diagnóstico Camera2** que liste el nivel de hardware, las capacidades (`MANUAL_SENSOR`, etc.), los rangos de ISO y obturación, las resoluciones y FPS, y los códecs. | Qué controles manuales reales tiene el A36. **Decide el alcance del §2.** |
| 1 — Cámara base | Preview + grabación normal + panel de controles según capacidades + registro de giroscopio `.gcsv` | Pipeline Camera2 → MediaCodec → MediaMuxer |
| 2 — Bucle | Segmentación, ventana deslizante 5/10/15 min, unión, "guardar momento" | El núcleo diferencial |
| 3 — Voz | Vosk + gramática de comandos + tee de audio | Precisión en ruido real |
| 4 — Feedback | Voice pack Fish Audio en CI + reproducción | Experiencia manos libres completa |
| 5 — Estabilización en vivo | EIS por GPU (OpenGL ES) con giroscopio, entre cámara y encoder | Video estabilizado sin pasar por Gyroflow |

## Fuentes principales

- Android Developers — `CameraCharacteristics`, `CaptureRequest`, `MediaRecorder`, tipos de foreground service, Thermal API, "Sharing audio input".
- Android Developers Blog — *Introducing CameraX 1.5* (nov 2025).
- Fish Audio Docs — *Pricing and rate limits*, *Real-time streaming*.
- Vosk — tabla de modelos (alphacephei.com/vosk/models).
- Picovoice — anuncio del cierre del Free Tier (30/06/2026), reportado en Home Assistant Community.
- GSMArena — especificaciones del Galaxy A36.
- Samsung Community — reportes de soporte Camera2 limitado en la gama A.

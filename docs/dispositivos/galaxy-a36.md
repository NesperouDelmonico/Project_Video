# Galaxy A36 (SM-A366E) — resultados del diagnóstico

> Diagnóstico Camera2 y prueba activa del 2026-11-23 · Android 16 (API 36) · SoC QTI SM6475 (Snapdragon 6 Gen 3).

## Resumen

| | |
|---|---|
| ✅ **Controles manuales reales** | ISO, obturación, enfoque y balance de blancos manuales **funcionan** en la cámara principal, aunque Camera2 no declare `MANUAL_SENSOR`. |
| ✅ **Ultra gran angular disponible** | La cámara `2` (≈96° horizontales) es accesible para terceros. Es el lente natural de una cámara de acción. |
| ⚠️ **Video limitado** | 1080p hasta 30 fps; 4K solo hasta 24 fps (pedir 30 bloqueó la cámara, ver incidente). Sin 60 fps. Cámara lenta 720p a 120/240 fps. |
| ❌ **Sin estabilización expuesta** | Ni EIS ni OIS para terceros. La estabilización es trabajo propio. |
| ✅ **Encoders sobrados** | HEVC y H.264 por hardware hasta 100 Mbps, 4K30 y 1080p60: el límite está en la cámara, no en el encoder. |

## Cámaras

| Id | Lente | Focal | Apertura | Sensor | Campo de visión | Enfoque | Uso previsto |
|---|---|---|---|---|---|---|---|
| `0` | Trasera principal | 4,69 mm | f/1.8 | 6,53 × 4,90 mm · 12,5 MP (binning 2×2 de 50 MP) | ≈70° H · 77° diagonal 16:9 | AF + manual (mín. 10 cm) | Calidad, poca luz, 4K24 |
| `2` | **Trasera ultra gran angular** | 1,64 mm | f/2.2 | 3,66 × 2,74 mm · 8 MP | **≈96° H · 104° diagonal 16:9** | Fijo | **Modo acción por defecto** |
| `1` | Frontal | 3,3 mm | f/2.2 | 4,48 × 3,36 mm · 12 MP | ≈68° H | Fijo | Vlog / selfie |
| `3` | Frontal (variante) | 3,3 mm | f/2.2 | 3,67 × 2,76 mm · 12 MP | menor | Fijo | Sin uso previsto |

La macro de 5 MP no está disponible para terceros. Todas las cámaras son nivel **LIMITED** y su `timestampSource` es `REALTIME`, el mismo reloj que el giroscopio. Por eso sincronizar los datos de movimiento con los frames es directo (ver estabilización).

## Prueba activa (cámara principal)

| Prueba | Pedido | Obtenido | Resultado |
|---|---|---|---|
| Referencia automática | — | ISO 479 · 1/30 s · brillo 103 | — |
| Exposición manual A | ISO 400 · 1/500 s | ISO 402 · 1/506 s · brillo 7 | ✅ |
| Exposición manual B (~2,1× más luz) | ISO 100 · 1/60 s | ISO 100 · 1/60 s · brillo 16 (2,3×) | ✅ El brillo real sigue a la exposición |
| Enfoque manual | 0,0 D (infinito) / 5,0 D (20 cm) | 0,13 D / 5,00 D | ✅ |
| Balance de blancos por ganancias | R 2,2 · B 1,4 | R 2,20 · B 1,40 · AWB OFF | ✅ |
| FPS con 4K | 4K a 30 fps (no declarado) | Tiempo agotado + `ERROR_CAMERA_DEVICE` | ❌ Ver incidente |

La gran angular (cámara `2`) también confirmó ISO, obturación y balance de blancos manuales (ISO 400 · 1/500 s y ISO 100 · 1/60 s exactos; ganancias R 2,20 · B 1,40).

### Incidente: cámara bloqueada para todo el sistema (2026-11-23)

1. **Prueba v1 (nightly #3):** después de los controles manuales, que funcionaron, se pidió **4K a 30 fps**. Camera2 solo declara 24 fps en esa resolución. La sesión no entregó frames (tiempo agotado) y la cámara quedó en error.
2. **Prueba v2 (nightly #4):** la cámara principal devolvió `ERROR_CAMERA_DEVICE` desde la primera prueba, incluso con los controles manuales que antes funcionaban. Su HAL seguía roto. La gran angular funcionó al principio y luego también falló.
3. **Resultado:** la app de cámara de Samsung tampoco pudo abrir la cámara. Se recupera con **forzar detención + reiniciar el teléfono**.

**Causa probable:** pedir una combinación tamaño × FPS que el HAL no declara. Un HAL LIMITED de Samsung no la recorta a lo soportado: entra en error grave y no se recupera solo.

**Medidas (nightly #5):**
- La prueba **solo pide combinaciones declaradas** (tamaño en `MediaRecorder`, FPS ≤ el que declara `getOutputMinFrameDuration` y rango presente en `aeAvailableTargetFpsRanges`). 4K a 30 fps ya no se prueba.
- Al primer `ERROR_CAMERA_DEVICE` / `ERROR_CAMERA_SERVICE` **la prueba se detiene** y pide reiniciar, en vez de seguir abriendo cámaras.
- Regla agregada a `CLAUDE.md` para todo el código de cámara futuro.

**Conclusión práctica:** 4K a 30 fps no está disponible para terceros en el A36. El perfil 4K es **4K24**.

## Lo que Camera2 declara

| Área | Principal (`0`) | Gran angular (`2`) |
|---|---|---|
| Capacidades | `BACKWARD_COMPATIBLE`, `READ_SENSOR_SETTINGS`, `BURST_CAPTURE`, `CONSTRAINED_HIGH_SPEED_VIDEO` | `BACKWARD_COMPATIBLE`, `READ_SENSOR_SETTINGS`, `BURST_CAPTURE` |
| ISO | 35 – 4491 (analógico hasta 2245) | 40 – 1280 (analógico hasta 640) |
| Obturación | 1/10773 s – 3,33 s | 1/19110 s – 1/4 s |
| Compensación EV | ±2,0 EV, pasos de 0,1 | ±2,0 EV, pasos de 0,1 |
| Balance de blancos | 8 presets + OFF | 8 presets + OFF |
| Video (MediaRecorder) | 1080p ≤ 30 fps · 4K ≤ 24 fps | 1080p ≤ 30 fps · máx. 3264×2448 a 24 fps |
| Alta velocidad | 720p a 120 / 240 fps | No |
| Estabilización | OFF | OFF |
| Zoom | 1× – 8× digital | 1× – 8× digital |
| Prioridad de exposición | No (`aeAvailablePriorityModes: 0`) | No |

Encoders por hardware: `c2.qti.hevc.encoder` y `c2.qti.avc.encoder`, ambos hasta 4096×4096 y 100 Mbps, con 4K30 y 1080p60. AAC solo por software, que basta para audio.

## Decisiones para la Fase 1

### Panel de controles

Como la prueba activa lo confirmó, el panel será de **cámara profesional completa**:
- **Exposición:** modo Auto / Manual. En manual hay ISO (35–4491) y obturación (1/10000 s hasta 1/fps). En auto hay compensación EV ±2 y bloqueo AE.
- **Balance de blancos:** Auto, presets y **Kelvin manual**, convertido a ganancias RGGB, más bloqueo AWB.
- **Enfoque:** continuo, macro y **manual por distancia** (solo en la principal; la gran angular es de foco fijo).
- **Lente:** gran angular (por defecto) o principal. **Zoom** digital.
- **Perfil de video:** 1080p30 HEVC (por defecto), 4K24 HEVC (solo principal) y 720p120/240 cámara lenta (solo principal).

Cada control se habilita por cámara según lo que reporten sus características y la prueba activa; nada va escrito a mano para el A36.

### Estabilización

1. **Fase 1 — registro de giroscopio `.gcsv`** junto a cada video, para estabilizar en Gyroflow. Como las marcas de tiempo de la cámara y del giroscopio usan el mismo reloj, la sincronización es exacta. La calibración intrínseca del lente (`lens.intrinsicCalibration`) también está disponible para el perfil de lente.
2. **Fase 5 — EIS en tiempo real** por GPU. Conviene hacerlo sobre la gran angular: tras recortar un 10–15 % sigue quedando un campo de visión amplio.

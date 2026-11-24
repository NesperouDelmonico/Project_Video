# Galaxy A36 (SM-A366E) — resultados del diagnóstico

> Diagnóstico Camera2 del 2026-11-23 · Android 16 (API 36) · SoC QTI SM6475 (Snapdragon 6 Gen 3).
> Cámara analizada: trasera principal, id `0`. El reporte recibido llegó cortado después de las primeras claves crudas, así que faltan las demás cámaras y los encoders. Pendiente: reporte completo y prueba activa.

## Lo que Camera2 expone a apps de terceros

| Área | Resultado | Comentario |
|---|---|---|
| Nivel de hardware | **LIMITED** | Igual que el resto de la gama A de Samsung. |
| Capacidades | `BACKWARD_COMPATIBLE`, `READ_SENSOR_SETTINGS`, `BURST_CAPTURE`, `CONSTRAINED_HIGH_SPEED_VIDEO` | Faltan `MANUAL_SENSOR`, `MANUAL_POST_PROCESSING` y `RAW`. |
| Modos AE / AF / AWB | Los tres incluyen **OFF** | Sin `MANUAL_SENSOR` no hay garantía de que funcionen, pero el HAL los anuncia. **La prueba activa lo resuelve.** |
| Rango ISO / obturación | ISO 35–4491 (analógico hasta 2245) · 1/10773 s – 3,33 s | Solo sirve si la exposición manual funciona de verdad. |
| Prioridad de exposición | `aeAvailablePriorityModes: 0` | No hay prioridad a obturación ni a ISO. |
| Compensación EV | −2,0 a +2,0 EV, pasos de 1/10 | ✅ Garantizado. |
| Bloqueo AE / AWB | Sí / Sí | ✅ |
| Balance de blancos | 8 presets + OFF · `colorCorrection.availableModes: 0,1,2` | Las ganancias manuales se verifican en la prueba activa. |
| Enfoque | AF continuo de video, macro, OFF · mínimo 10 cm | Distancia manual por verificar. |
| Apertura | Fija f/1.8 · focal 4,69 mm · sensor 6,53 × 4,90 mm | El sensor de 50 MP se entrega en 12,5 MP (binning 2×2). |
| **Video** | **1080p hasta 30 fps · 4K (3840×2160) hasta 24 fps** · rangos AE hasta [30, 30] | Sin 60 fps en ninguna resolución. La app de Samsung graba 4K30 y 1080p60, pero eso no está disponible para terceros. |
| Alta velocidad | 1280×720 a 120 y 240 fps | Sesión *constrained high speed*: sirve para cámara lenta. |
| **Estabilización** | **EIS: solo OFF · OIS: solo OFF** | La estabilización de Samsung no está disponible para terceros. |
| HDR 10-bit | No | |
| Encoder | HEVC por hardware, 8-bit | |
| Zoom | 1× – 8× (digital) | La cámara 0 no es lógica: falta ver si la ultra gran angular aparece con otro id. |
| Almacenamiento | 49,3 GB libres | Alcanza para el bucle de 15 min a 1080p. |

## Consecuencias para el diseño

### 1. Perfiles de video de la v1

| Perfil | Uso |
|---|---|
| **1080p30 HEVC** (por defecto) | Grabación normal y modo bucle. |
| 4K24 HEVC | Modo "cine", sin bucle largo por espacio y temperatura. |
| 720p120 / 720p240 | Cámara lenta. |

La medición de FPS de la prueba activa confirmará si 4K entrega 24 fps aunque se pidan 30.

### 2. Estabilización: hay que hacerla nosotros

Una cámara de acción sin estabilización pierde gran parte de su valor, y el A36 no expone EIS ni OIS a terceros. Hay dos caminos, que se complementan:

| Opción | Cómo funciona | Esfuerzo | Cuándo |
|---|---|---|---|
| **A. Registro de giroscopio para Gyroflow** | Mientras se graba, se guardan el giroscopio y el acelerómetro con marcas de tiempo alineadas al video, en un archivo `.gcsv` junto al `.mp4`. Gyroflow (gratuito y de código abierto, con versión para Android) estabiliza después, con corrección de *rolling shutter* y nivelado de horizonte. | Bajo | Fase 1, junto con la grabación |
| **B. EIS en tiempo real por GPU** | Entre la cámara y el encoder se pasa cada frame por OpenGL ES: recorte de ~10–15 % y rotación compensada con el giroscopio. El video sale ya estabilizado. | Alto (y suma calor) | Fase propia, después del modo voz |

### 3. Panel de controles (Fase 1)

- **Siempre disponibles:** compensación EV, bloqueo AE/AWB, presets de balance de blancos, modo de enfoque (continuo / macro), zoom, perfil de video.
- **Solo si la prueba activa da ✅:** ISO y obturación manuales, enfoque por distancia y balance de blancos por ganancias o Kelvin.

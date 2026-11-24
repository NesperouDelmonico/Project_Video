# Project Video — reglas para Claude Code

App Android tipo cámara de acción (GoPro/Insta360). Idioma del proyecto: español (docs, commits, UI); identificadores de código en inglés.

## Reglas de trabajo
- **Mantener `README.md` actualizado** en el mismo commit que cambie funcionalidades, stack, hoja de ruta o forma de instalar/compilar.
- Las decisiones técnicas relevantes se documentan en `docs/` (empezando por `docs/viabilidad.md`).
- Cada commit debe dejar el proyecto compilando: GitHub Actions genera un APK por commit.
- Nunca commitear claves (Fish Audio, keystore). Van en GitHub Secrets.
- La UI de cámara se construye a partir de las capacidades reportadas por `CameraCharacteristics`; no asumir soporte por modelo.
- **Nunca pedir al HAL combinaciones no declaradas** (tamaño × FPS por encima de `getOutputMinFrameDuration`, rangos fuera de `aeAvailableTargetFpsRanges`). En el A36, pedir 4K a 30 fps coincidió con `ERROR_CAMERA_DEVICE` y dejó la cámara inutilizable para todo el sistema hasta reiniciar.
- Ante `ERROR_CAMERA_DEVICE`, `ERROR_CAMERA_SERVICE`, `ERROR_CAMERA_DISABLED` o `CAMERA_IN_USE`, cerrar la cámara y no reintentar en bucle: avisar al usuario.
- Vigilar `PowerManager.currentThermalStatus`: con `MODERATE` o más, no iniciar trabajos opcionales de cámara (pruebas, 4K).

## Dispositivo de referencia
Samsung Galaxy A36 (Snapdragon 6 Gen 3).

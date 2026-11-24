# Project Video — reglas para Claude Code

App Android tipo cámara de acción (GoPro/Insta360). Idioma del proyecto: español (docs, commits, UI); identificadores de código en inglés.

## Reglas de trabajo
- **Mantener `README.md` actualizado** en el mismo commit que cambie funcionalidades, stack, hoja de ruta o forma de instalar/compilar.
- Las decisiones técnicas relevantes se documentan en `docs/` (empezando por `docs/viabilidad.md`).
- Cada commit debe dejar el proyecto compilando: GitHub Actions genera un APK por commit.
- Nunca commitear claves (Fish Audio, keystore). Van en GitHub Secrets.
- La UI de cámara se construye a partir de las capacidades reportadas por `CameraCharacteristics`; no asumir soporte por modelo.

## Dispositivo de referencia
Samsung Galaxy A36 (Snapdragon 6 Gen 3).

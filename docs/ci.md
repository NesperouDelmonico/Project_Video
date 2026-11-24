# CI/CD — APK por commit

El workflow `.github/workflows/android.yml` corre en cada push (a cualquier rama), en cada pull request y a mano desde la pestaña Actions.

## Qué hace

1. Compila el APK `release` (`./gradlew assembleRelease`).
2. Corre los tests unitarios (`testDebugUnitTest`) y lint (`lintDebug`).
3. Sube el APK como *artifact* con el nombre `ProjectVideo-<número de build>-<commit>.apk`.
4. Sube los reportes de tests y lint como otro artifact.
5. **Solo en `main`:** reemplaza el pre-release **`nightly`** con el APK nuevo. Desde el celular basta con abrir la página de Releases del repo y descargarlo.

Si la compilación falla, los errores aparecen como anotaciones en el resumen del workflow.

`versionCode` toma el número de ejecución del workflow, así cada APK nuevo se instala encima del anterior.

## Firma del APK

| Situación | Firma |
|---|---|
| Sin secrets configurados (estado actual) | Keystore de **debug**, cacheado entre builds. Funciona para probar, pero si la caché expira (7 días sin uso) cambia la firma y hay que desinstalar la app antes de instalar el APK nuevo. |
| Con secrets configurados | Keystore de **release** propio. Firma estable para siempre. |

### Configurar el keystore de release (recomendado, una sola vez)

1. Generar el keystore en tu máquina (no se sube al repo):

   ```bash
   keytool -genkeypair -v \
     -keystore projectvideo-release.jks \
     -alias projectvideo \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Convertirlo a base64:

   ```bash
   base64 -w 0 projectvideo-release.jks > keystore.b64   # Linux
   base64 -i projectvideo-release.jks -o keystore.b64    # macOS
   ```

3. En GitHub: **Settings → Secrets and variables → Actions → New repository secret**, crear:

   | Secret | Valor |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | contenido de `keystore.b64` |
   | `SIGNING_STORE_PASSWORD` | contraseña del keystore |
   | `SIGNING_KEY_ALIAS` | `projectvideo` |
   | `SIGNING_KEY_PASSWORD` | contraseña de la clave |

4. Guardar el `.jks` y las contraseñas en un lugar seguro: si se pierden, ninguna actualización futura se podrá instalar encima de la app.

Al cambiar de la firma de debug a la de release hay que desinstalar la app una vez.

## Compilar localmente

Requiere JDK 17+ y el Android SDK (Android Studio lo instala).

```bash
./gradlew assembleRelease        # APK en app/build/outputs/apk/release/
./gradlew testDebugUnitTest      # tests unitarios
./gradlew installRelease         # instalar en un celular conectado por USB
```

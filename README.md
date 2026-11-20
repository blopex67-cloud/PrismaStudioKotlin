# Prisma Studio 3D (prototipo independiente)

Editor 3D Android original, hecho en Kotlin y OpenGL ES 2.0. No es una app oficial ni está afiliada a Prisma3D.

## Funciones incluidas
- Visor 3D con cuadrícula, iluminación sencilla y órbita táctil; pellizco para zoom.
- Añadir cubos y esferas, cambiar objeto activo, mover sobre X, girar, escalar y borrar.
- Guardar y recuperar una escena JSON en el almacenamiento privado de la app.
- Interfaz horizontal en español.

## Compilar con GitHub Actions
El workflow `.github/workflows/android-apk.yml` compila un APK de depuración en GitHub Actions al subir cambios a `main` o al ejecutarlo manualmente. Al terminar, descarga el artefacto llamado `PrismaStudio-debug-apk` desde la ejecución del workflow.

También se puede compilar localmente con JDK 17, Gradle 8.9 y Android SDK Platform 35 / Build Tools 35.0.0:

```sh
gradle --no-daemon assembleDebug
```

El APK resultante queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Alcance
Es una primera base funcional: aún no importa formatos Prisma/GLB, no anima rigs ni exporta video/imagen. El APK solo debe considerarse listo tras compilar y probar su arranque y controles en un Android real.

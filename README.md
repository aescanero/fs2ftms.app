# fs2ftms

App Android que conecta con una elíptica Merach (protocolo FitShow) y publica FTMS (0x1826)
para que un reloj Amazfit grabe el entrenamiento directamente en Zepp.

## Estado
- **Hito 1 (actual):** emulador FTMS con datos simulados y telemetría de lo que hace el reloj.
- Hito 2: cliente FitShow con depurador opcional.
- Hito 3: puente completo.

## Instalación
Descarga la APK de la última [Release](../../releases) e instálala en el móvil (Android 13+).

## Prueba del hito 1
1. Abre la app, concede permisos y pulsa «Iniciar emulador».
2. En el reloj: añade un dispositivo de entrenamiento y busca el móvil.
3. Inicia un entrenamiento de elíptica en el reloj y comprueba cadencia y distancia.
4. En la pestaña «Telemetría», pulsa «Exportar telemetría (JSON)» y comparte el fichero.

## Compilación
GitHub Actions (`.github/workflows/build.yml`) ejecuta los tests y genera la APK firmada.
Secrets necesarios: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

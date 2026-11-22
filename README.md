# FTMS Lab

App Android que graba los entrenamientos de una elíptica Merach (FTMS) junto con el pulso que
emite un reloj Amazfit, con histórico propio y exportación JSON.

## Estado
- **Hecho:** grabación de sesiones (máquina + pulso), historial con detalle y gráficos, exportación JSON.
- Siguiente: escritura en Health Connect, dashboard diario/semanal/mensual y calorías por pulso.

## Instalación
Descarga la APK de la última [Release](../../releases) e instálala (Android 13+).

## Uso
1. Dispositivos → Buscar → pulsa la elíptica → «Usar como elíptica» (cierra antes FitShow).
2. Inicia un entrenamiento en el reloj con la emisión de pulso activada; búscalo y elige «Usar como fuente de pulso».
3. Entreno → «Iniciar sesión». Al terminar, «Finalizar y guardar».

## Compilación
GitHub Actions (`.github/workflows/build.yml`) ejecuta los tests y genera la APK firmada.
Secrets necesarios: `KEYSTORE_BASE64` y `KEYSTORE_PASSWORD` (alias por defecto `fs2ftms`).

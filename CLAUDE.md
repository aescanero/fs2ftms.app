# fs2ftms — puente FitShow → FTMS (Android)

App Android que se conecta como central BLE a la elíptica Merach (protocolo FitShow:
servicio FFF0, notify FFF1, write FFF2, tramas `02 … XOR 03`) y publica un servidor
GATT FTMS (0x1826, Cross Trainer Data 0x2ACE) para el reloj Amazfit Active 3.

## Entorno
- Sin ordenador ni Android Studio: solo Claude Code (nube) y el móvil (Samsung A33).
- GitHub Actions compila la APK firmada y la publica como Release. No hay adb:
  la depuración se hace con la pantalla de diagnóstico y exportación de logs.
- Idioma: código e identificadores en inglés; textos de UI, commits y docs en español.

## Hitos
1. Emulador FTMS con datos simulados + telemetría del reloj (conexiones, MTU,
   suscripciones, escrituras al Control Point, notificaciones enviadas/entregadas).
2. Cliente FitShow + depurador opcional (mapa GATT, tramas en bruto, sonda de
   consultas, detector de cambios, FTMS nativo de la Merach, exportación).
3. Puente completo con servicio en primer plano y reconexión.

## Arquitectura y prácticas
- Kotlin + Jetpack Compose (Material 3), actividad única, navegación Compose.
- UDF/MVVM: ViewModel + StateFlow; corrutinas y Flow; sin lógica en composables.
- Módulos: `:app` (UI), `:core:protocol` (Kotlin puro: tramas FitShow y codificación
  FTMS, sin dependencias Android, 100 % testeable), `:ble:fitshow`, `:ble:ftms`,
  `:data` (Room para sesiones/telemetría, DataStore para ajustes).
- Hilt para inyección de dependencias. Version catalog (`libs.versions.toml`), Gradle Kotlin DSL.
- BLE: permisos de ejecución BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT y
  BLUETOOTH_ADVERTISE; servicio en primer plano tipo `connectedDevice`; operaciones
  GATT serializadas en cola; reconexión con backoff.
- Calidad en CI: ktlint, detekt, Android Lint, tests unitarios del protocolo con
  tramas reales como fixtures. R8 en release. Firma con secret de GitHub.
- Seguridad: la sonda FitShow solo envía consultas; los comandos de control solo a mano.
- Evitar la palabra "focus" en textos.

## Estado real (hito 1)
- Módulos existentes: `:app`, `:core:protocol`, `:ble:ftms`, `:ble:central` (escáner y volcado GATT de solo consulta). minSdk 33.
- Resultado de las pruebas del hito 1: el Active 3 lista el emulador, conecta ~2,4 s sin leer ni suscribirse y
  corta; tras 3 intentos da error (igual en móvil y tablet, sin emparejar). Plan: volcar la Toputure
  (bicicleta, aceptada por el reloj) y replicar su anuncio y GATT en el emulador.
- Hallazgo clave (14-11-2026): la Merach MRK-E27 SÍ publica FTMS válido (0x2ACE a ~1,25 Hz, sin pedir
  control, partido en 2 paquetes por MTU 23; incluye resistencia). El reloj (Zepp OS 6) no admite elípticas:
  solo acepta el perfil Indoor Bike, sin leer Feature ni negociar MTU (máx. 20 bytes por notificación).
- El puente actual es FTMS→FTMS: `FtmsSourceClient` (:ble:central) lee 0x2ACE de la Merach y `FtmsServer`
  lo republica como Indoor Bike Data (0x2AD2). El protocolo FitShow ya no es necesario para el puente;
  queda como opción de diagnóstico. Detalle de tramas en `docs/hallazgos-previos.md`.
- Aplazado al hito 2, cuando haga falta persistencia: Hilt, Room/DataStore, `:ble:fitshow`, `:data`.
- Aplazado: ktlint y detekt (pendiente de versiones compatibles con Kotlin 2.2). CI ejecuta tests y assembleRelease.
- Sin compilación local: Maven y Google están bloqueados en la sesión; cada cambio se valida en Actions
  (`gh run list`, `gh run view <id> --log-failed`). Los secrets no se pueden escribir desde la sesión.

## Delegación a modelos de menor coste
Subagentes en `.claude/agents/`. Regla: delegar siempre que la tarea encaje; el modelo
principal se reserva para arquitectura, código BLE/GATT, concurrencia y diagnóstico de fallos.

| Nivel | Agentes | Uso |
|---|---|---|
| haiku | git-ops, ci-triage, lint-fix, deps-update, docs-sync, boilerplate, frame-fixtures, log-digest, release-notes | Tareas mecánicas con entrada y salida cerradas |
| sonnet | test-writer, code-review, ui-scaffold, ref-research | Trabajo con criterio acotado |
| principal | — | Diseño, BLE, decisiones y revisión final |

Contexto técnico previo: `docs/hallazgos-previos.md`.

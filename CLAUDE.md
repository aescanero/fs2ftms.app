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

## Delegación a modelos de menor coste
Subagentes en `.claude/agents/`. Regla: delegar siempre que la tarea encaje; el modelo
principal se reserva para arquitectura, código BLE/GATT, concurrencia y diagnóstico de fallos.

| Nivel | Agentes | Uso |
|---|---|---|
| haiku | git-ops, ci-triage, lint-fix, deps-update, docs-sync, boilerplate, frame-fixtures, log-digest, release-notes | Tareas mecánicas con entrada y salida cerradas |
| sonnet | test-writer, code-review, ui-scaffold, ref-research | Trabajo con criterio acotado |
| principal | — | Diseño, BLE, decisiones y revisión final |

Contexto técnico previo: `docs/hallazgos-previos.md`.

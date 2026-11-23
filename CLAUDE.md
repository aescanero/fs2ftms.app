# FTMS Lab (ftmslab) — registrador de entrenamientos FTMS (Android)

App Android que lee por FTMS los datos de la elíptica Merach MRK-E27 (Cross Trainer Data 0x2ACE),
recibe el pulso que emite el reloj Amazfit Active 3 (Heart Rate Service 0x180D) y graba la sesión.
Sustituye a la app FitShow. Objetivo: histórico propio, dashboard, exportación JSON y Health Connect.

## Entorno
- Sin ordenador ni Android Studio: solo Claude Code (nube) y el móvil (Samsung A33).
- GitHub Actions compila la APK firmada y la publica como Release. No hay adb:
  la depuración se hace con la pantalla de diagnóstico y exportación de logs.
- Idioma: código e identificadores en inglés; textos de interfaz en recursos, inglés por defecto
  (`values/`) y español (`values-es/`); mensajes técnicos de diagnóstico solo en inglés; commits,
  comentarios y docs en español. `DemoWorkout.NAME` se guarda en la base de datos: no traducirlo.

## Entregas
1. Grabación de la sesión (máquina + pulso), histórico con detalle y exportación JSON. [hecho, sin validar en dispositivo]
2. Escritura en Health Connect (connect-client 1.1.0, solo escritura): sesión de ejercicio (elíptica o bici
   estática), distancia, calorías activas y series de pulso, potencia y velocidad cada 5 s. clientRecordId
   por sesión y versión creciente, así que reenviar actualiza. Automática al finalizar (opción en Ajustes) y
   envío de pendientes. `PrivacyActivity` atiende la justificación de permisos. [sin validar en dispositivo]
3. Dashboard: resumen de últimos 7 y 30 días y por meses en Historial; en Entreno, comparativa cíclica de
   cada parámetro (sesión en curso o última) frente a las medias de 7 y 30 días de la misma clase de máquina.
4. Calorías: estimación por pulso (peso, edad, sexo) y corrección manual.

UX (15-11-2026): indicador circular de pulso por zonas (FC máx. por edad o medida, en Ajustes), pantalla
de entreno sin scroll con jerarquía de métricas, pantalla encendida y confirmación al finalizar, pestañas
bloqueadas mientras se graba, barra lateral en tablet horizontal, detalle de sesión con tiempo por zona.
Todo sin validar en dispositivo. Revisión repetible con el agente `ux-review` (capturas en `docs/ux/`).

Decisiones del usuario (15-11-2026): se retira el puente hacia el reloj (el reloj no admite elípticas y
Android no permite fijar la dirección BLE del anuncio); la capa de depuración (volcado GATT, registro de
conexiones, exportaciones) se mantiene como opción en Ajustes. El reloj solo emite pulso con un
entrenamiento iniciado, así que Zepp también grabará su sesión.

## Arquitectura y prácticas
- Kotlin + Jetpack Compose (Material 3), actividad única, navegación Compose.
- UDF/MVVM: ViewModel + StateFlow; corrutinas y Flow; sin lógica en composables.
- Módulos por capa (ver "Estado real"). Version catalog (`libs.versions.toml`), Gradle Kotlin DSL.
- BLE: permisos de ejecución BLUETOOTH_SCAN (neverForLocation) y BLUETOOTH_CONNECT; servicio en
  primer plano tipo `connectedDevice`; reconexión con backoff; la app solo lee, nunca escribe en la máquina.
- Calidad en CI: ktlint, detekt, Android Lint, tests unitarios del protocolo con
  tramas reales como fixtures. R8 en release. Firma con secret de GitHub.
- Evitar la palabra "focus" en textos.

## Estado real
- Nombre (22-11-2026): FTMS Lab, paquete `io.github.aescanero.ftmslab` (antes fs2ftms; nueva app, no
  actualiza a la anterior). El alias de la clave de firma sigue siendo `fs2ftms`. Icono: rayo gris claro sobre gris oscuro.
- Sincronización (23-11-2026): un fichero SQLite elegido con el selector de Android (SAF, como Keepass2Android);
  en Drive lo sube la app de Drive, sin OAuth ni Google Cloud. Room sigue siendo la base local de trabajo;
  `SyncEngine` (:data) fusiona por startedAtMs con `updatedAtMs` y bajas (`tombstones`); `SyncPlanner` es la
  lógica pura con tests. Se sincroniza al arrancar, al finalizar sesión, al borrar y al corregir calorías.
  Las sesiones recibidas se escriben también en el Health Connect del dispositivo. Esquema del fichero
  documentado en `SyncEngine` (consultable con SQL). Drive no permite elegir carpeta para crear ficheros
  dentro (OPEN_DOCUMENT_TREE), por eso es un único fichero.
- Perfiles (21-11-2026): cada máquina se da de alta capturando su FTMS (Dispositivos → pulsar mientras se
  usa). `ProfileBuilder` (:core:protocol) decide tipo y estado (SUPPORTED, NO_FTMS, UNKNOWN_TYPE,
  TYPE_NOT_SUPPORTED, NO_DATA) y las métricas observadas en las tramas; la pantalla de entreno muestra solo
  esas. Decodificadores: elíptica (validado con la Merach) y bicicleta (según especificación, pendiente de
  validar con la Toputure). Perfiles en preferencias (JSON, `ProfileStore`); sesiones con `machineType`.
- Módulos: `:app` (UI y servicio de grabación), `:core:protocol` (Kotlin puro: parser 0x2ACE, pulso,
  acumulador de sesión; con tests sobre tramas reales), `:ble:central` (escáner, volcado GATT,
  `GattNotifyClient` con reconexión, `EventLog`), `:data` (Room: sesiones y muestras). minSdk 33.
- La Merach emite 0x2ACE a ~1,25 Hz sin pedir control, en 2 paquetes por MTU 23; incluye resistencia y
  calorías. Detalle de tramas en `docs/hallazgos-previos.md`. El protocolo FitShow no se usa.
- Health Connect es por dispositivo: la app debe ejecutarse en el móvil (A33) para convivir con Zepp.
- Sin Hilt (contenedor manual en `FtmsLabApp`). Sin ktlint/detekt todavía. CI: tests y assembleRelease.
- Sin compilación local: Maven y Google están bloqueados en la sesión; cada cambio se valida en Actions
  (`gh run list`, `gh run view <id> --log-failed`). Los secrets no se pueden escribir desde la sesión.

## Delegación a modelos de menor coste
Subagentes en `.claude/agents/`. Regla: delegar siempre que la tarea encaje; el modelo
principal se reserva para arquitectura, código BLE/GATT, concurrencia y diagnóstico de fallos.

| Nivel | Agentes | Uso |
|---|---|---|
| haiku | git-ops, ci-triage, lint-fix, deps-update, docs-sync, boilerplate, frame-fixtures, log-digest, release-notes | Tareas mecánicas con entrada y salida cerradas |
| sonnet | test-writer, code-review, ui-scaffold, ref-research, ux-review | Trabajo con criterio acotado |
| principal | — | Diseño, BLE, decisiones y revisión final |

Contexto técnico previo: `docs/hallazgos-previos.md`.

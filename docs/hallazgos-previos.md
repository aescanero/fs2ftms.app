# Hallazgos previos (del histórico del proyecto)

## Hechos comprobados por el usuario
- El reloj descubre la Merach pero no puede añadirla; la Toputure (módulo FitShow híbrido) se añade y funciona.
- La resistencia de la Merach es manual: la app FitShow la pregunta al terminar. El puente es de solo lectura.
- La Merach admite una sola conexión BLE: el puente y la app FitShow son excluyentes.
- Health Connect no fusiona sesiones; con el puente solo existirá la sesión de Zepp.

## Hipótesis sobre el FTMS nativo de la Merach (a comprobar con el depurador)
1. Anuncia 0x1826 pero faltan 2ACC (Feature) o 2ACE (Cross Trainer Data).
2. 2ACE existe pero no notifica sin un handshake previo por el canal propietario.
3. Rechaza el Control Point 2AD9 ("Control Not Permitted") y el reloj aborta.

## Requisitos del servidor FTMS derivados
- 2ACC declarando solo lo disponible; 2ACE con notificaciones a 1 Hz.
- 2AD9: responder Success a Request Control y Start aunque no se actúe.
- 2ADA (Machine Status) opcional; Battery Service simulado como opción (QZ lo usa para relojes exigentes).
- Riesgo: tipo de máquina anunciado. Zepp podría exigir Cross Trainer; debe ser configurable.
- La Toputure sirve de patrón: el depurador debe poder volcar su advertising y GATT para
  replicar en el emulador el perfil que el reloj ya acepta.

## Lado FitShow
- La app sondea el estado escribiendo en FFF2; no basta con suscribirse a FFF1.
- Tramas vistas en la Toputure (vía QZ): 02 42, 02 43, 02 44.
- Pendiente: trama de estado real de la elíptica (el depurador sustituye a la captura HCI).

## Referencias
- cagnulein/qdomyos-zwift (soporte Merach MRK y driver FitShow; PRs #5354 y #5355 sobre Toputure TBE5)
- FloF317/ha-merach-treadmill · tyge68/fitshow-treadmill · sstjohn/milltender
- hubwerk/smartfitness-bridge (ESP32-S3; sin adaptador de elíptica Merach)

## Sin confirmar
- Si Zepp acepta un FTMS virtual emitido por el móvil (la prueba con QZ no consta como hecha).

## Volcado real de la Merach MRK-E27 (14-11-2026)
- Módulo ThinkFIT (servicio 0xF8C0: marca "ThinkFIT", tipo "CRTR", producto "MRK-E27"), MTU fijo 23.
- Anuncio: service data 0x1826 = `01 02 00` (Cross Trainer disponible). Igual que nuestro emulador de elíptica.
- FTMS completo: 2ACC = `C7 52 00 00 04 00 00 00`; rangos 2AD4–2AD8; resistencia 1,0–16,0 paso 1,0 (2AD6 = `0A 00 A0 00 0A 00`).
- 0x2ACE emite sin escribir en el Control Point, en dos paquetes:
  - `9F 00 00` + vel. media (u16, 0,01 km/h) + distancia (u24, m) + pasos/min y media (u16×2) + zancadas (u16, 0,1) + resistencia (s16, 0,1).
  - `00 2F 00` + velocidad (u16) + potencia y media (s16×2) + energía (u16+u16+u8) + pulso (u8) + tiempo (u16).
- 2ADA: `04` al empezar a moverse; `07 xx xx` al cambiar la resistencia a mano.
- FitShow (FFF1) también emite sin sondeo, a 1 Hz:
  - `02 42 02` + velocidad (u16) + resistencia (u8) + cadencia (u16) + 00 + potencia×10 (u16) + … + XOR + `03`.
  - `02 43 01` + tiempo (u16) + distancia (u16) + energía×10 (u16) + contador (u16) + XOR + `03`.
- Otros servicios: `59554c55-…` "MRK-HR" (entrada de pulso) y OTA Telink (`00010203-…1912`): no tocar.
- El Active 3 se suscribe solo a 0x2AD2 cuando el servidor se anuncia como bicicleta; rechaza Cross Trainer.

## Primera sesión real (16-11-2026, 40 min, v0.1.21)
- Muestreo estable: una muestra cada 0,8 s (2819 en 2262 s), sin huecos; pulso del reloj en el 100 % de las muestras.
- La máquina actualiza sus valores a 1 Hz: una de cada cinco muestras repite la anterior.
- **Salto inicial:** las dos primeras muestras traen contadores a cero y la tercera salta a los reales
  (142 s, 353 m, 6 kcal, 33 zancadas acumulados antes de grabar). Corregido en `SessionAccumulator`
  comparando el avance del tiempo de la máquina con el reloj; reparación única de lo ya guardado.
- Velocidad = cadencia × 0,317 (constante): la distancia es virtual y no depende de la resistencia.
- La cadencia cuenta pasos; una zancada son dos pasos (zancadas ≈ integral de cadencia / 2).
- Calorías de la máquina: 226 kcal para 130 kJ de trabajo mecánico (rendimiento implícito ≈ 14 %).

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

---
name: frame-fixtures
description: Convierte logs exportados del depurador FitShow o de la telemetría FTMS en fixtures y tests unitarios parametrizados de :core:protocol.
tools: Read, Write, Edit, Grep, Glob
model: haiku
---
Entrada: un export JSON/CSV de tramas. Por cada trama distinta crea un fixture (hex,
sentido, campos esperados si se conocen) y un caso de test siguiendo el patrón de los
tests existentes. Verifica el XOR de cada trama; descarta y lista las inválidas.
No inventes el significado de bytes desconocidos: márcalos como `unknown`.

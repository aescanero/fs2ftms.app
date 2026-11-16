package io.github.aescanero.fs2ftms.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.DeviceSettings
import io.github.aescanero.fs2ftms.protocol.HeartRateZone
import io.github.aescanero.fs2ftms.protocol.HeartRateZones

@Composable
internal fun SettingsTab(
    settings: DeviceSettings,
    versionName: String,
    onDebug: (Boolean) -> Unit,
    onDemo: (Boolean) -> Unit,
    onCreateDemoSession: () -> Unit,
    onAge: (Int?) -> Unit,
    onMaxHeartRate: (Int?) -> Unit,
    onWeight: (Int?) -> Unit,
    onMale: (Boolean?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // En pantallas anchas el contenido se reparte en dos columnas de lectura cómoda.
        if (maxWidth >= 900.dp) {
            Row(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(Modifier.widthIn(max = 480.dp).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZonesSection(settings, onAge, onMaxHeartRate)
                    HorizontalDivider()
                    CaloriesSection(settings, onWeight, onMale)
                }
                Column(Modifier.widthIn(max = 560.dp).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionsSection(settings, onDebug, onDemo, onCreateDemoSession)
                    HorizontalDivider()
                    PrivacySection(versionName)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ZonesSection(settings, onAge, onMaxHeartRate)
                HorizontalDivider()
                CaloriesSection(settings, onWeight, onMale)
                HorizontalDivider()
                OptionsSection(settings, onDebug, onDemo, onCreateDemoSession)
                HorizontalDivider()
                PrivacySection(versionName)
            }
        }
    }
}

@Composable
private fun ZonesSection(settings: DeviceSettings, onAge: (Int?) -> Unit, onMaxHeartRate: (Int?) -> Unit) {
    Text("Zonas de pulso", style = MaterialTheme.typography.titleMedium)
    var ageText by rememberSaveable { mutableStateOf(settings.age?.toString().orEmpty()) }
    var maxText by rememberSaveable { mutableStateOf(settings.measuredMaxHeartRate?.toString().orEmpty()) }
    OutlinedTextField(
        value = ageText,
        onValueChange = { text ->
            ageText = text.filter(Char::isDigit).take(3)
            onAge(ageText.toIntOrNull())
        },
        label = { Text("Edad") },
        suffix = { Text("años") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(160.dp),
    )
    OutlinedTextField(
        value = maxText,
        onValueChange = { text ->
            maxText = text.filter(Char::isDigit).take(3)
            onMaxHeartRate(maxText.toIntOrNull())
        },
        label = { Text("FC máxima medida") },
        suffix = { Text("ppm") },
        supportingText = { Text("Opcional. Vacío: se usa 220 − edad") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(260.dp),
    )
    val max = settings.maxHeartRate
    if (max == null) {
        Text(
            "Sin estos datos no se calculan las zonas.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        val zones = HeartRateZones(max)
        val dark = isSystemInDarkTheme()
        val origin = if (settings.measuredMaxHeartRate != null) "medida" else "estimada: 220 − edad"
        Text("FC máxima: $max ppm ($origin)", style = MaterialTheme.typography.bodyLarge)
        HeartRateZone.entries.forEach { zone ->
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Z${zone.number} ${zone.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = zoneColor(zone, dark),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${zones.lowerBound(zone)}–${zones.upperBound(zone)} ppm",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CaloriesSection(settings: DeviceSettings, onWeight: (Int?) -> Unit, onMale: (Boolean?) -> Unit) {
    Text("Calorías por pulso", style = MaterialTheme.typography.titleMedium)
    var weightText by rememberSaveable { mutableStateOf(settings.weightKg?.toString().orEmpty()) }
    OutlinedTextField(
        value = weightText,
        onValueChange = { text ->
            weightText = text.filter(Char::isDigit).take(3)
            onWeight(weightText.toIntOrNull())
        },
        label = { Text("Peso") },
        suffix = { Text("kg") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(160.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Sexo (para la fórmula):", style = MaterialTheme.typography.bodyMedium)
        FilterChip(selected = settings.male == true, onClick = { onMale(true) }, label = { Text("Hombre") })
        FilterChip(selected = settings.male == false, onClick = { onMale(false) }, label = { Text("Mujer") })
    }
    Text(
        if (settings.calorieEstimator != null) {
            "Al finalizar cada sesión con pulso se calculan las calorías con la edad, el peso y el sexo " +
                "(fórmula de Keytel). Es una aproximación; puedes corregirla a mano en cada sesión."
        } else {
            "Indica edad, peso y sexo para calcular las calorías a partir del pulso. Sin ellos se usa la " +
                "cifra de la máquina."
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun OptionsSection(
    settings: DeviceSettings,
    onDebug: (Boolean) -> Unit,
    onDemo: (Boolean) -> Unit,
    onCreateDemoSession: () -> Unit,
) {
    SwitchRow(
        title = "Modo depuración",
        description = "Muestra el volcado GATT completo, el registro de conexiones y sus exportaciones.",
        checked = settings.debug,
        onChange = onDebug,
    )
    if (settings.debug) {
        SwitchRow(
            title = "Modo demostración",
            description = "«Iniciar sesión» graba datos simulados de elíptica y pulso, sin conectar con ningún " +
                "dispositivo. Las sesiones quedan marcadas como «Demostración».",
            checked = settings.demo,
            onChange = onDemo,
        )
        OutlinedButton(onClick = onCreateDemoSession, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Crear sesión de ejemplo (25 min)")
        }
    }
}

/** Toda la fila conmuta el interruptor, no solo el propio control. */
@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PrivacySection(versionName: String) {
    Text("Política de privacidad", style = MaterialTheme.typography.titleMedium)
    Text(
        "fs2ftms funciona íntegramente en este dispositivo. No tiene acceso a Internet, " +
            "no crea cuentas y no envía datos a ningún servidor.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Usa Bluetooth para buscar dispositivos cercanos y leer los datos de la elíptica y el pulso " +
            "que emite el reloj. No usa esos datos para obtener tu ubicación.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Las sesiones (tiempo, distancia, cadencia, potencia, resistencia, calorías y frecuencia cardíaca) " +
            "y los datos que indiques para las zonas de pulso se guardan solo en este dispositivo. Salen de él " +
            "únicamente cuando exportas un fichero y eliges con qué aplicación compartirlo. Puedes borrar cada " +
            "sesión desde el historial.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("Versión $versionName", style = MaterialTheme.typography.bodySmall)
}

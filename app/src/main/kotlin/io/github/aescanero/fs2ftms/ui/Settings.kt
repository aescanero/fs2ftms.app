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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.DeviceSettings
import io.github.aescanero.fs2ftms.R
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
    Text(stringResource(R.string.settings_zones), style = MaterialTheme.typography.titleMedium)
    var ageText by rememberSaveable { mutableStateOf(settings.age?.toString().orEmpty()) }
    var maxText by rememberSaveable { mutableStateOf(settings.measuredMaxHeartRate?.toString().orEmpty()) }
    OutlinedTextField(
        value = ageText,
        onValueChange = { text ->
            ageText = text.filter(Char::isDigit).take(3)
            onAge(ageText.toIntOrNull())
        },
        label = { Text(stringResource(R.string.settings_age)) },
        suffix = { Text(stringResource(R.string.unit_years)) },
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
        label = { Text(stringResource(R.string.settings_max_hr)) },
        suffix = { Text(stringResource(R.string.unit_bpm)) },
        supportingText = { Text(stringResource(R.string.settings_max_hr_help)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(260.dp),
    )
    val max = settings.maxHeartRate
    if (max == null) {
        Text(
            stringResource(R.string.settings_zones_missing),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        val zones = HeartRateZones(max)
        val dark = isSystemInDarkTheme()
        val origin = if (settings.measuredMaxHeartRate != null) {
            stringResource(R.string.settings_max_hr_measured)
        } else {
            stringResource(R.string.settings_max_hr_estimated)
        }
        Text(stringResource(R.string.settings_max_hr_value, max, origin), style = MaterialTheme.typography.bodyLarge)
        HeartRateZone.entries.forEach { zone ->
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.zone_name, zone.number, stringResource(zoneLabelRes(zone))),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = zoneColor(zone, dark),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.settings_zone_range, zones.lowerBound(zone), zones.upperBound(zone)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CaloriesSection(settings: DeviceSettings, onWeight: (Int?) -> Unit, onMale: (Boolean?) -> Unit) {
    Text(stringResource(R.string.settings_calories), style = MaterialTheme.typography.titleMedium)
    var weightText by rememberSaveable { mutableStateOf(settings.weightKg?.toString().orEmpty()) }
    OutlinedTextField(
        value = weightText,
        onValueChange = { text ->
            weightText = text.filter(Char::isDigit).take(3)
            onWeight(weightText.toIntOrNull())
        },
        label = { Text(stringResource(R.string.settings_weight)) },
        suffix = { Text("kg") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(160.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_sex), style = MaterialTheme.typography.bodyMedium)
        FilterChip(selected = settings.male == true, onClick = { onMale(true) }, label = { Text(stringResource(R.string.settings_male)) })
        FilterChip(selected = settings.male == false, onClick = { onMale(false) }, label = { Text(stringResource(R.string.settings_female)) })
    }
    Text(
        if (settings.calorieEstimator != null) {
            stringResource(R.string.settings_calories_on)
        } else {
            stringResource(R.string.settings_calories_off)
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
        title = stringResource(R.string.settings_debug),
        description = stringResource(R.string.settings_debug_help),
        checked = settings.debug,
        onChange = onDebug,
    )
    if (settings.debug) {
        SwitchRow(
            title = stringResource(R.string.settings_demo),
            description = stringResource(R.string.settings_demo_help),
            checked = settings.demo,
            onChange = onDemo,
        )
        OutlinedButton(onClick = onCreateDemoSession, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.settings_demo_create))
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
    Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.privacy_local),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        stringResource(R.string.privacy_bluetooth),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        stringResource(R.string.privacy_storage),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodySmall)
}

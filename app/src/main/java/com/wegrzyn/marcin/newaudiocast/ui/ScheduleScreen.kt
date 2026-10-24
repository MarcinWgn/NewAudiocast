package com.wegrzyn.marcin.newaudiocast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormatSymbols
import java.util.Calendar

// Calendar.DAY_OF_WEEK values in display order, Monday first.
private val WEEK = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)
private val WEEKDAYS = WEEK.take(5).toSet()
private val WEEKEND = WEEK.takeLast(2).toSet()

private const val DEFAULT_HOUR = 20

/** Full-screen list of schedule entries with add/edit dialog; replaces the station grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val entries by Schedule.entries(context).collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ScheduleEntry?>(null) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Exact alarms first (otherwise up to 10 min late); the notification permission is asked next time.
    val askPermissions = {
        if (!Alarms.canScheduleExact(context)) {
            Alarms.openExactAlarmSettings(context)
        } else if (needsNotificationPermission(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.arrow_back_24),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = ScheduleEntry(
                    id = System.currentTimeMillis(),
                    stationName = (LastStation.get(context) ?: Stations.stationsList.first()).name,
                    hour = DEFAULT_HOUR,
                    minute = 0,
                    days = emptySet()
                )
            }) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.add_24),
                    contentDescription = stringResource(R.string.schedule_add)
                )
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(text = stringResource(R.string.schedule_empty), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                // Bottom space so the FAB doesn't cover the last entry's switch.
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(entries.sortedWith(compareBy({ it.hour }, { it.minute })), key = { it.id }) { entry ->
                    ScheduleEntryRow(
                        entry = entry,
                        onClick = { editing = entry },
                        onEnabledChange = { enabled ->
                            Schedule.setEnabled(context, entry.id, enabled)
                            if (enabled) askPermissions()
                        },
                        onDelete = { Schedule.remove(context, entry.id) }
                    )
                }
            }
        }
    }

    editing?.let { entry ->
        ScheduleEntryDialog(
            initial = entry,
            isNew = entries.none { it.id == entry.id },
            onDismiss = { editing = null },
            onSave = {
                Schedule.save(context, it)
                editing = null
                askPermissions()
            }
        )
    }
}

@Composable
private fun ScheduleEntryRow(
    entry: ScheduleEntry,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = formatTime(entry.hour, entry.minute), style = AppTypography.headlineSmall)
        },
        supportingContent = { Text("${entry.stationName} · ${daysLabel(entry.days)}") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = entry.enabled, onCheckedChange = onEnabledChange)
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.delete_24),
                        contentDescription = stringResource(R.string.schedule_delete)
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEntryDialog(
    initial: ScheduleEntry,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntry) -> Unit
) {
    var stationName by remember { mutableStateOf(initial.stationName) }
    var days by remember { mutableStateOf(initial.days) }
    val timeState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.schedule_new else R.string.schedule_edit)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                StationDropdown(selected = stationName, onSelect = { stationName = it })
                // The clock dial doesn't fit a dialog on a low landscape screen.
                if (isLandscape) TimeInput(state = timeState) else TimePicker(state = timeState)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WEEK.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(shortDayName(day)) }
                        )
                    }
                }
                Text(text = stringResource(R.string.schedule_days_hint), style = AppTypography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        stationName = stationName,
                        hour = timeState.hour,
                        minute = timeState.minute,
                        days = days,
                        enabled = true
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.schedule_station)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Stations.stationsList.forEach { station ->
                DropdownMenuItem(
                    text = { Text(station.name) },
                    onClick = {
                        onSelect(station.name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() -> stringResource(R.string.schedule_once)
    days.size == WEEK.size -> stringResource(R.string.schedule_daily)
    days == WEEKDAYS -> stringResource(R.string.schedule_weekdays)
    days == WEEKEND -> stringResource(R.string.schedule_weekend)
    else -> WEEK.filter { it in days }.joinToString(", ") { shortDayName(it) }
}

// Locale's short weekday name without the trailing dot ("pon." -> "pon").
private fun shortDayName(day: Int): String =
    DateFormatSymbols.getInstance().shortWeekdays[day].trimEnd('.')

private fun formatTime(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

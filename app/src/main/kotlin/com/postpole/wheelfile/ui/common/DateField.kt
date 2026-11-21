package com.postpole.wheelfile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ui.theme.FieldShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * A filled date field that opens the Material date picker. [onPick] receives the chosen day at the time of day of
 * [epochMillis] (or the start of the day when [epochMillis] is null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochMillis: Long?, onPick: (Long) -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    var open by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    Surface(
        onClick = { open = true },
        shape = FieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.fieldMinHeight),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = Spacing.s), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                epochMillis?.let { formatDate(it, zone) } ?: stringResource(R.string.form_pick_date),
                style = MaterialTheme.typography.bodyLarge,
                color = if (epochMillis == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = epochMillis?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val picked = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = epochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: java.time.LocalTime.MIDNIGHT
                        onPick(picked.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

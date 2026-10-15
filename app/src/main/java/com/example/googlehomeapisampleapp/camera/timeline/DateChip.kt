package com.example.googlehomeapisampleapp.camera.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateChip(
    currentTime: Instant,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentDate = currentTime.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    val yesterday = today.minusDays(1)
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= Instant.now().toEpochMilli()
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli(),
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(millis)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val dateText = remember(currentDate, today, yesterday) {
        when (currentDate) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> currentDate.format(
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
            )
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FilterChip(
            selected = true,
            onClick = { showDatePicker = true },
            label = { Text(dateText) },
            modifier = Modifier.testTag("DateChip"),
        )
    }
}
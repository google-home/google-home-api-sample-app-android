/* Copyright 2026 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.example.googlehomeapisampleapp.history

import android.util.Log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Doorbell
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.rememberCoroutineScope
import com.example.googlehomeapisampleapp.view.shared.TabbedMenuView
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.matter.standard.DoorLockDevice
import com.google.home.matter.standard.ThermostatDevice
import kotlinx.coroutines.launch

private const val TAG = "HistoryView"

@Composable
fun HistoryView(
    viewModel: HomeAppViewModel,
    historyPlayerViewModel: HistoryPlayerViewModel = hiltViewModel(),
) {
    val historyItems = viewModel.historyFlow.collectAsLazyPagingItems()
    val selectedDevice by viewModel.selectedHistoryDeviceVM.collectAsStateWithLifecycle()
    val selectedDeviceName by remember(selectedDevice) {
        selectedDevice?.name ?: kotlinx.coroutines.flow.flowOf("")
    }.collectAsStateWithLifecycle(initialValue = "")
    val primaryDeviceType by remember(selectedDevice) {
        selectedDevice?.type ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val homeBriefs by viewModel.homeBriefs.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedDeviceTypeFilter.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Only fetch HomeBriefs when viewing Structure-Wide History (selectedDevice == null)
    androidx.compose.runtime.LaunchedEffect(selectedDevice == null) {
        if (selectedDevice == null) {
            viewModel.loadHomeBriefs()
        }
    }

    BackHandler(enabled = selectedDevice != null) {
        viewModel.clearHistorySelection()
    }

    val imageLoader = historyPlayerViewModel.cameraMediaAuth.imageLoader

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerTitle = if (selectedDevice != null) {
                    (selectedDeviceName.takeIf { it.isNotBlank() } ?: "Unknown Device") + ": History"
                } else {
                    "Home Activity"
                }
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }

            // Device Type Filter Chips (Only shown on Structure-Wide History)
            if (selectedDevice == null) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(HistoryDeviceTypeFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { viewModel.selectDeviceTypeFilter(filter) },
                            label = { Text(filter.label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            // Event list (HomeBriefs only included on Structure-Wide History when All filter is selected)
            Box(modifier = Modifier.weight(1f)) {
                val shouldShowHomeBriefs = selectedDevice == null &&
                    selectedFilter == HistoryDeviceTypeFilter.All
                val emptyIcon = when {
                    selectedDevice != null -> {
                        when (primaryDeviceType?.factory) {
                            DoorLockDevice -> Icons.Outlined.Lock
                            ThermostatDevice -> Icons.Outlined.Thermostat
                            GoogleCameraDevice, GoogleDoorbellDevice -> Icons.Outlined.Videocam
                            else -> Icons.Outlined.History
                        }
                    }
                    selectedFilter == HistoryDeviceTypeFilter.DoorLock -> Icons.Outlined.Lock
                    selectedFilter == HistoryDeviceTypeFilter.Thermostat -> Icons.Outlined.Thermostat
                    selectedFilter == HistoryDeviceTypeFilter.Camera -> Icons.Outlined.Videocam
                    else -> Icons.Outlined.History
                }
                HistoryList(
                    events = historyItems,
                    homeBriefs = if (shouldShowHomeBriefs) homeBriefs else emptyList(),
                    emptyIcon = emptyIcon,
                    imageLoader = imageLoader,
                    onEventSelected = { event -> viewModel.openVideoPlayer(event) },
                    onHomeBriefEventClick = { event -> viewModel.openHomeBriefVideo(event) },
                )
            }

            // Bottom Navigation Bar (Only shown on Structure-Wide History)
            if (selectedDevice == null) {
                TabbedMenuView(viewModel)
            }
        }
    }
}

@Composable
fun HistoryList(
    events: LazyPagingItems<HistoryEventUi>,
    homeBriefs: List<HistoryUiDataModel.HomeBriefEvent> = emptyList(),
    emptyIcon: ImageVector = Icons.Outlined.History,
    imageLoader: ImageLoader,
    onEventSelected: (HistoryUiDataModel.CameraEvent) -> Unit,
    onHomeBriefEventClick: (HomeBriefCameraEvent) -> Unit = {},
) {
    val errorPainter = rememberVectorPainter(Icons.Outlined.Image)
    val today = LocalDate.now()

    val isFinishedLoading = events.loadState.refresh is LoadState.NotLoading
    val isEmpty = events.itemCount == 0 && homeBriefs.isEmpty()

    if (events.loadState.refresh is LoadState.Loading && isEmpty) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (events.loadState.refresh is LoadState.Error && isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Failed to load history",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { events.retry() }) {
                    Text("Retry")
                }
            }
        }
    } else if (isFinishedLoading && isEmpty) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = emptyIcon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No events",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black
                )
                Text(
                    text = "Activity will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White)) {

            // HomeBrief cards pinned at the top of the feed.
            // No date separator needed — each card's own header shows the date.
            if (homeBriefs.isNotEmpty()) {
                items(
                    items = homeBriefs,
                    key = { it.briefId },
                ) { brief ->
                    HomeBriefCard(
                        brief = brief,
                        imageLoader = imageLoader,
                        onEventClick = { event -> onHomeBriefEventClick(event) },
                    )
                }
            }

            // Paginated chronological history events (Cameras, Locks, Thermostats, etc.)
            items(
                count = events.itemCount,
                key = events.itemKey { it.id }
            ) { index ->
                when (val item = events[index]) {
                    is HistoryEventUi.DateSeparatorModel -> DateSeparatorRow(item.date, today)
                    is HistoryUiDataModel.CameraEvent -> {
                        HistoryItemRow(
                            uiModel = item,
                            errorPainter = errorPainter,
                            imageLoader = imageLoader,
                            onClick = { onEventSelected(item) },
                        )
                    }
                    is HistoryUiDataModel.DoorLockEvent,
                    is HistoryUiDataModel.StateChangeUiModel,
                    is HistoryUiDataModel.DefaultEvent -> {
                        HistoryItemRow(
                            uiModel = item,
                            errorPainter = errorPainter,
                            imageLoader = imageLoader,
                            onClick = {},
                        )
                    }
                    is HistoryUiDataModel.HomeBriefEvent -> {} // Handled separately in pinned cards above
                    null -> {}
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    uiModel: HistoryUiDataModel,
    errorPainter: Painter,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val (title, icon, details) = getEventMetadata(uiModel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(enabled = uiModel is HistoryUiDataModel.CameraEvent) { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.size(24.dp))

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = title,
                    color = Color.Black,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = uiModel.displaySubtitle,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    details.forEach { detail ->
                        Text(
                            text = "• $detail",
                            color = Color.DarkGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (uiModel is HistoryUiDataModel.CameraEvent) {
                AsyncImage(
                    model = uiModel.mediaUrl.thumbnailUrl,
                    imageLoader = imageLoader,
                    contentDescription = "Event thumbnail",
                    modifier = Modifier
                        .size(width = 80.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop,
                    error = errorPainter,
                    onError = { state ->
                        Log.w(TAG, "AsyncImage load error for ${uiModel.mediaUrl.thumbnailUrl}", state.result.throwable)
                    }
                )
            }
        }
    }
}

private data class HistoryEventMetadata(
    val title: String,
    val icon: ImageVector,
    val details: List<String> = emptyList(),
)

private fun getEventMetadata(model: HistoryUiDataModel): HistoryEventMetadata {
    return when (model) {
        is HistoryUiDataModel.CameraEvent -> {
            val defaultTitleIcon = when (model.eventType) {
                HistoryUiEventType.Person -> "Person" to Icons.Outlined.Person
                HistoryUiEventType.Motion -> "Motion" to Icons.Outlined.MotionPhotosOn
                HistoryUiEventType.Doorbell -> "Doorbell" to Icons.Outlined.Doorbell
                HistoryUiEventType.Vehicle -> "Vehicle" to Icons.Outlined.DirectionsCar
                HistoryUiEventType.Animal -> "Animal" to Icons.Outlined.Pets
                HistoryUiEventType.Unknown -> "Camera Event" to Icons.Outlined.Videocam
            }
            // Prioritize shortCaption over generic event label
            val title = model.shortCaption?.takeIf { it.isNotBlank() } ?: defaultTitleIcon.first
            HistoryEventMetadata(title, defaultTitleIcon.second)
        }
        is HistoryUiDataModel.DoorLockEvent -> {
            val icon = if (model.isUnlock) Icons.Outlined.LockOpen else Icons.Outlined.Lock
            HistoryEventMetadata(model.eventTitle, icon, model.details)
        }
        is HistoryUiDataModel.StateChangeUiModel -> {
            val icon = when (model) {
                is HistoryUiDataModel.ThermostatStateChange,
                is HistoryUiDataModel.ExtendedThermostatStateChange -> Icons.Outlined.Thermostat
                else -> Icons.Outlined.History
            }
            HistoryEventMetadata(
                title = model.eventTitle,
                icon = icon,
                details = model.details,
            )
        }
        is HistoryUiDataModel.DefaultEvent -> {
            HistoryEventMetadata(model.eventName ?: "Activity", Icons.Outlined.History)
        }
        is HistoryUiDataModel.HomeBriefEvent -> {
            HistoryEventMetadata("Home Brief", Icons.Outlined.History)
        }
    }
}

@Composable
fun DateSeparatorRow(date: LocalDate, today: LocalDate) {
    val label = if (date == today) "Today" else if (date == today.minusDays(1)) "Yesterday" else date.format(
        DateTimeFormatter.ofPattern("EEEE, MMM d"))
    Text(
        text = label,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Color.Gray,
        fontWeight = FontWeight.Bold
    )
}

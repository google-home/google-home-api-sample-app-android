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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import coil3.compose.AsyncImage
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HistoryView(viewModel: HomeAppViewModel) {
    val historyItems = viewModel.historyFlow.collectAsLazyPagingItems()
    val selectedDevice by viewModel.selectedHistoryDeviceVM.collectAsStateWithLifecycle()
    val homeBriefs by viewModel.homeBriefs.collectAsStateWithLifecycle()

    // Fetch HomeBriefs every time the History screen is opened
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadHomeBriefs()
    }

    BackHandler(enabled = true) {
        viewModel.clearHistorySelection()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column {
            if (selectedDevice != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (selectedDevice?.name?.collectAsState()?.value
                            ?.takeIf { it.isNotBlank() } ?: "Unknown Device") + ": History",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Event list
            HistoryList(
                events = historyItems,
                homeBriefs = homeBriefs,
                onEventSelected = { event -> viewModel.openVideoPlayer(event) },
                onHomeBriefEventClick = { event -> viewModel.openHomeBriefVideo(event) },
            )
        }
    }
}

@Composable
fun HistoryList(
    events: LazyPagingItems<HistoryEventUi>,
    homeBriefs: List<HistoryUiDataModel.HomeBriefEvent> = emptyList(),
    onEventSelected: (HistoryUiDataModel.CameraEvent) -> Unit,
    onHomeBriefEventClick: (HomeBriefCameraEvent) -> Unit = {},
) {
    val errorPainter = rememberVectorPainter(Icons.Outlined.Image)
    val today = LocalDate.now()

    val isFinishedLoading = events.loadState.refresh is LoadState.NotLoading
    val isEmpty = events.itemCount == 0 && homeBriefs.isEmpty()

    if (isFinishedLoading && isEmpty) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No camera events",
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
                        onEventClick = { event -> onHomeBriefEventClick(event) },
                    )
                }
            }

            // Camera / device history items
            items(
                count = events.itemCount,
                key = events.itemKey { it.id }
            ) { index ->
                when (val item = events[index]) {
                    is HistoryEventUi.DateSeparatorModel -> DateSeparatorRow(item.date, today)
                    is HistoryUiDataModel.HomeBriefEvent -> {
                        HomeBriefCard(
                            brief = item,
                            onEventClick = { event -> onHomeBriefEventClick(event) },
                        )
                    }
                    is HistoryUiDataModel.CameraEvent -> {
                        HistoryItemRow(
                            uiModel = item,
                            errorPainter = errorPainter,
                            onClick = { onEventSelected(item) },
                        )
                    }
                    is HistoryUiDataModel.DefaultEvent -> {
                        HistoryItemRow(
                            uiModel = item,
                            errorPainter = errorPainter,
                            onClick = {},
                        )
                    }
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
    onClick: () -> Unit,
) {
    val (title, icon) = getEventMetadata(uiModel)

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
            }

            if (uiModel is HistoryUiDataModel.CameraEvent) {
                AsyncImage(
                    model = uiModel.mediaUrl.thumbnailUrl,
                    contentDescription = "Event thumbnail",
                    modifier = Modifier
                        .size(width = 80.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop,
                    error = errorPainter
                )
            }
        }
    }
}

private fun getEventMetadata(model: HistoryUiDataModel): Pair<String, ImageVector> {
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
            // Prioritize shortCaption if available and not blank
            val title = model.shortCaption?.takeIf { it.isNotBlank() } ?: defaultTitleIcon.first
            title to defaultTitleIcon.second // Keep the original icon
        }
        is HistoryUiDataModel.DefaultEvent -> {
            (model.eventName ?: "Activity") to Icons.Outlined.History
        }
        is HistoryUiDataModel.HomeBriefEvent -> {
            "Home Brief" to Icons.Outlined.History
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

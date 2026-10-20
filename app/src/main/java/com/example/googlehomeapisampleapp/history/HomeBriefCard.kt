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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.imageLoader
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Card composable for displaying a Home Brief (AI-generated daily summary) in the
 * activity feed. Collapsed by default showing truncated text; expandable via
 * "See more" to reveal the full brief and associated camera event thumbnails.
 *
 * @param brief The [HistoryUiDataModel.HomeBriefEvent] to display.
 * @param onEventClick Called when a camera event thumbnail is tapped.
 * @param modifier Optional modifier for the outer card.
 */
@Composable
fun HomeBriefCard(
    brief: HistoryUiDataModel.HomeBriefEvent,
    onEventClick: (HomeBriefCameraEvent) -> Unit = {},
    imageLoader: ImageLoader? = null,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember(brief.briefId) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row: icon + title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "Home Brief",
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = brief.generateTime?.let { formatBriefDate(it) } ?: "Home Brief",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A73E8),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Brief body text — truncated when collapsed
            Text(
                text = brief.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF202124),
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Camera event thumbnails — visible only when expanded
            AnimatedVisibility(
                visible = isExpanded && brief.keyCameraEvents.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Related clips",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        items(brief.keyCameraEvents) { event ->
                            HomeBriefThumbnail(
                                event = event,
                                onClick = { onEventClick(event) },
                                imageLoader = imageLoader,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // See more / See less toggle
            TextButton(
                onClick = { isExpanded = !isExpanded },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isExpanded) "See less" else "See more",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF1A73E8),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Thumbnail for a single camera event associated with a Home Brief.
 * Tappable to play the clip video. Uses thumbnailUrl for the still image,
 * falling back to previewUrl.
 */
@Composable
private fun HomeBriefThumbnail(
    event: HomeBriefCameraEvent,
    onClick: () -> Unit = {},
    imageLoader: ImageLoader? = null,
    modifier: Modifier = Modifier,
) {
    val errorPainter = rememberVectorPainter(Icons.Outlined.Image)
    // Use thumbnailUrl (still image) for display, previewUrl as fallback
    val imageUrl = event.thumbnailUrl.ifBlank { event.previewUrl }

    Box(
        modifier = modifier
            .size(width = 100.dp, height = 72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE8EAED))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                imageLoader = imageLoader ?: LocalContext.current.imageLoader,
                contentDescription = "Camera event thumbnail",
                modifier = Modifier
                    .size(width = 100.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = errorPainter,
                onError = { state ->
                    Log.e("HomeBriefThumbnail", "Failed to load: $imageUrl")
                    Log.e("HomeBriefThumbnail", "Error: ${state.result.throwable}")
                },
                onSuccess = {
                    Log.d("HomeBriefThumbnail", "Successfully loaded: $imageUrl")
                }
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "No thumbnail",
                tint = Color.Gray,
                modifier = Modifier.size(28.dp),
            )
        }

        // Timestamp chip overlaid on bottom-left of thumbnail
        event.startTime?.let { time ->
            Text(
                text = time.formatToTime(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** Formats a brief's generateTime to a human-readable label. */
private fun formatBriefDate(instant: java.time.Instant): String {
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today)
    return when {
        date == today -> "Today's Home Brief"
        date == today.minusDays(1) -> "Yesterday's Home Brief"
        daysAgo < 7 -> {
            val dayName = DateTimeFormatter.ofPattern("EEEE").withZone(zone).format(instant)
            "$dayName's Home Brief"
        }
        else -> {
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d").withZone(zone)
            "${dateFormatter.format(instant)}'s Home Brief"
        }
    }
}
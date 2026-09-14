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

package com.example.googlehomeapisampleapp.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.example.googlehomeapisampleapp.R
import com.google.home.google.ZoneManagementTrait

object ActivityZoneDefaults {
  const val DEFAULT_CANVAS_WIDTH = 1920
  const val DEFAULT_CANVAS_HEIGHT = 1080
}

/**
 * Screen composable for viewing camera snapshot canvas and configuring motion detection Activity Zones.
 */
@Composable
fun ActivityZoneView(
  snapshotUrl: String?,
  authenticatedImageLoader: ImageLoader?,
  activityZones: List<ActivityZone>,
  twoDCartesianMax: ZoneManagementTrait.TwoDCartesianVertexStruct?,
  zoneUpdateStatus: ZoneUpdateStatus,
  isCameraOn: Boolean,
  isFetchingLiveSnapshot: Boolean,
  onRefreshLiveSnapshot: () -> Unit,
  onAddActivityZone: () -> Unit,
  onDeleteActivityZone: (Int) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val maxX = (twoDCartesianMax?.x?.toInt() ?: ActivityZoneDefaults.DEFAULT_CANVAS_WIDTH).coerceAtLeast(1)
  val maxY = (twoDCartesianMax?.y?.toInt() ?: ActivityZoneDefaults.DEFAULT_CANVAS_HEIGHT).coerceAtLeast(1)
  val containerAspectRatio = maxX.toFloat() / maxY.toFloat()

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize()) {
      Spacer(Modifier.height(16.dp))

      ActivityZoneTopBar(onNavigateBack = onNavigateBack)

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      ActivityZoneCanvasOverlay(
        snapshotUrl = snapshotUrl,
        authenticatedImageLoader = authenticatedImageLoader,
        activityZones = activityZones,
        maxX = maxX,
        maxY = maxY,
        containerAspectRatio = containerAspectRatio,
        isCameraOn = isCameraOn,
        isFetchingLiveSnapshot = isFetchingLiveSnapshot,
        onRefreshLiveSnapshot = onRefreshLiveSnapshot
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      ActivityZoneControlsPanel(
        activityZones = activityZones,
        zoneUpdateStatus = zoneUpdateStatus,
        onAddActivityZone = onAddActivityZone,
        onDeleteActivityZone = onDeleteActivityZone
      )
    }
  }
}

/**
 * Top app bar header with title and back navigation.
 */
@Composable
private fun ActivityZoneTopBar(
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = onNavigateBack) {
      Icon(
        Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.activity_zone_nav_back)
      )
    }
    Text(
      text = stringResource(R.string.activity_zone_config_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

/**
 * Camera snapshot canvas container overlaying activity zone polygon shapes and refresh action button.
 */
@Composable
private fun ActivityZoneCanvasOverlay(
  snapshotUrl: String?,
  authenticatedImageLoader: ImageLoader?,
  activityZones: List<ActivityZone>,
  maxX: Int,
  maxY: Int,
  containerAspectRatio: Float,
  isCameraOn: Boolean,
  isFetchingLiveSnapshot: Boolean,
  onRefreshLiveSnapshot: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Pre-parse hex colors to avoid string parsing overhead during recomposition/redraw
  val zoneColors = remember(activityZones) {
    activityZones.associate { zone ->
      zone.zoneId to try {
        Color(android.graphics.Color.parseColor(zone.color.hexString))
      } catch (e: Exception) {
        Color.Cyan
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(containerAspectRatio)
      .background(Color.Black),
    contentAlignment = Alignment.Center
  ) {
    // Snapshot Image Background (or dark placeholder)
    if (!snapshotUrl.isNullOrBlank()) {
      val context = LocalContext.current
      val loader = authenticatedImageLoader ?: context.imageLoader
      val imageRequest = remember(snapshotUrl) {
        coil3.request.ImageRequest.Builder(context)
          .data(snapshotUrl)
          .build()
      }
      AsyncImage(
        model = imageRequest,
        contentDescription = stringResource(R.string.activity_zone_canvas_desc),
        imageLoader = loader,
        // Use FillBounds so the snapshot fills the exact coordinate bounds of the container
        // (which is already constrained to containerAspectRatio = maxX / maxY). This ensures
        // the normalized polygon vertices (x/maxX, y/maxY) drawn on the overlay Canvas align
        // 1:1 with the underlying image without letterbox/pillarbox offset drift.
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (!isCameraOn) {
            stringResource(R.string.activity_zone_camera_off)
          } else {
            stringResource(R.string.activity_zone_no_snapshot)
          },
          color = Color.Gray,
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }

    // Zone Polygon Canvas Overlay
    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      activityZones.forEach { zone ->
        if (zone.vertices.size >= 3) {
          val zoneMaxX = if (zone.maxX > 0) zone.maxX else maxX
          val zoneMaxY = if (zone.maxY > 0) zone.maxY else maxY

          val path = Path()
          zone.vertices.forEachIndexed { i, vertex ->
            val px = (vertex.x.toFloat() / zoneMaxX.toFloat()) * canvasWidth
            val py = (vertex.y.toFloat() / zoneMaxY.toFloat()) * canvasHeight
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
          }
          path.close()

          val parsedColor = zoneColors[zone.zoneId] ?: Color.Cyan

          // Draw filled polygon
          drawPath(
            path = path,
            color = parsedColor.copy(alpha = 0.35f),
            style = Fill
          )
          // Draw stroke outline
          drawPath(
            path = path,
            color = parsedColor,
            style = Stroke(width = 3.dp.toPx())
          )
          // Draw vertices
          zone.vertices.forEach { vertex ->
            val px = (vertex.x.toFloat() / zoneMaxX.toFloat()) * canvasWidth
            val py = (vertex.y.toFloat() / zoneMaxY.toFloat()) * canvasHeight
            drawCircle(
              color = parsedColor,
              radius = 5.dp.toPx(),
              center = Offset(px, py)
            )
          }
        }
      }
    }

    // Refresh Live Snapshot Floating Icon Button (top-end)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
      contentAlignment = Alignment.TopEnd
    ) {
      IconButton(
        onClick = onRefreshLiveSnapshot,
        enabled = isCameraOn && !isFetchingLiveSnapshot,
        modifier = Modifier
          .background(Color.Black.copy(alpha = 0.6f), CircleShape)
          .size(36.dp)
      ) {
        if (isFetchingLiveSnapshot) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White
          )
        } else {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.activity_zone_refresh_snapshot),
            tint = if (isCameraOn) Color.White else Color.Gray,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

/**
 * Lower controls panel for managing configured activity zones and creating new zones.
 */
@Composable
private fun ActivityZoneControlsPanel(
  activityZones: List<ActivityZone>,
  zoneUpdateStatus: ZoneUpdateStatus,
  onAddActivityZone: () -> Unit,
  onDeleteActivityZone: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val modifiableZones = activityZones.filter { it.modifiable }
  val canAddZone = modifiableZones.size < MAX_ACTIVITY_ZONES && zoneUpdateStatus != ZoneUpdateStatus.InProgress

  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.activity_zone_configured_count, modifiableZones.size, MAX_ACTIVITY_ZONES),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )

      Button(
        onClick = onAddActivityZone,
        enabled = canAddZone
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(4.dp))
        Text(stringResource(R.string.activity_zone_add_zone))
      }
    }

    Spacer(Modifier.height(12.dp))

    if (modifiableZones.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(R.string.activity_zone_empty_state),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(modifiableZones) { zone ->
          ActivityZoneListItem(
            zone = zone,
            zoneUpdateStatus = zoneUpdateStatus,
            onDeleteActivityZone = onDeleteActivityZone
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
    }
  }
}

/**
 * List item for displaying a configured activity zone.
 */
@Composable
private fun ActivityZoneListItem(
  zone: ActivityZone,
  zoneUpdateStatus: ZoneUpdateStatus,
  onDeleteActivityZone: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val parsedColor = remember(zone.color.hexString) {
    try {
      Color(android.graphics.Color.parseColor(zone.color.hexString))
    } catch (e: Exception) {
      Color.Gray
    }
  }

  val defaultZoneName = zone.zoneId?.let { stringResource(R.string.activity_zone_unnamed, it) } ?: ""
  val headline = zone.zoneName.ifBlank { defaultZoneName }
  val supporting = stringResource(R.string.activity_zone_vertices_count, zone.vertices.size, zone.color.displayName)

  ListItem(
    modifier = modifier,
    headlineContent = { Text(headline) },
    supportingContent = { Text(supporting) },
    leadingContent = {
      Box(
        modifier = Modifier
          .size(16.dp)
          .background(color = parsedColor, shape = CircleShape)
      )
    },
    trailingContent = {
      if (zoneUpdateStatus == ZoneUpdateStatus.InProgress) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      } else {
        IconButton(onClick = { zone.zoneId?.let { onDeleteActivityZone(it) } }) {
          Icon(
            Icons.Default.Delete,
            contentDescription = stringResource(R.string.activity_zone_delete_zone),
            tint = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  )
}

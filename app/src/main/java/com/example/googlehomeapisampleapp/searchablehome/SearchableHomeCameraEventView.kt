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

package com.example.googlehomeapisampleapp.searchablehome

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.imageLoader
import android.util.Log
import com.example.googlehomeapisampleapp.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

import coil3.ImageLoader

private const val TAG = "SearchableHomeCameraEventView"
private const val REQUESTED_IMAGE_WIDTH_PX = 96

/**
 * Composable that renders a camera event card, showing the event caption,
 * camera name, timestamp, and a thumbnail of the event.
 */
@Composable
fun SearchableHomeCameraEventView(
  event: CameraEventData,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader? = null,
) {
  val errorPainter = rememberVectorPainter(Icons.Outlined.Image)
  val formatter = remember {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
  }

  val headline = event.shortCaption ?: stringResource(R.string.search_home_camera_event_headline)
  val startTime = event.startTime
  val timeStampStr = startTime?.let { formatter.format(it) } ?: ""
  val supportingContent =
    if (timeStampStr.isNotEmpty()) {
      "$timeStampStr • ${event.cameraName}"
    } else {
      event.cameraName
    }

  Row(modifier = modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = Icons.Outlined.Videocam,
      contentDescription = stringResource(R.string.camera_content_description),
      modifier = Modifier.size(24.dp),
    )

    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
      Text(text = headline, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = supportingContent,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    CameraThumbnail(
      previewUrl = event.previewUrl,
      thumbnailUrl = event.thumbnailUrl,
      errorPainter = errorPainter,
      imageLoader = imageLoader,
    )
  }
}

/**
 * Renders a camera thumbnail image.
 * Attempts to load the [previewUrl] first (requesting WebP format). If that fails,
 * it falls back to the [thumbnailUrl]. If both fail, it displays the [errorPainter] placeholder.
 */
@Composable
private fun CameraThumbnail(
  previewUrl: String?,
  thumbnailUrl: String?,
  errorPainter: Painter,
  imageLoader: ImageLoader? = null,
) {
  val previewUri =
    previewUrl?.toUriWithQueryParameters(
      mapOf("format" to "webp", "width" to REQUESTED_IMAGE_WIDTH_PX.toString())
    )
  val thumbnailUri =
    thumbnailUrl?.toUriWithQueryParameters(mapOf("width" to REQUESTED_IMAGE_WIDTH_PX.toString()))

  var hasLoadingAssetFailed by remember(previewUrl, thumbnailUrl) { mutableStateOf(false) }
  val uri = if (previewUri == null || hasLoadingAssetFailed) thumbnailUri else previewUri

  AsyncImage(
    model = uri,
    contentDescription = stringResource(R.string.camera_thumbnail_description),
    contentScale = ContentScale.Crop,
    modifier = Modifier.clip(RoundedCornerShape(8.dp)).size(64.dp),
    imageLoader = imageLoader ?: LocalContext.current.imageLoader,
    error = errorPainter,
    fallback = errorPainter,
    onError = {
      Log.w(TAG, "CameraThumbnail onError: ${it.result.throwable}")
      if (uri == previewUri && thumbnailUri != null) {
        hasLoadingAssetFailed = true
      }
    },
  )
}

/**
 * Helper extension to convert a URL String to an Android Uri, appending
 * the provided query parameters. Returns null if the string is empty.
 */
private fun String.toUriWithQueryParameters(queryParams: Map<String, String>): Uri? {
  if (isEmpty()) {
    return null
  }
  return toUri()
    .buildUpon()
    .apply {
      for ((key, value) in queryParams) {
        appendQueryParameter(key, value)
      }
    }
    .build()
}

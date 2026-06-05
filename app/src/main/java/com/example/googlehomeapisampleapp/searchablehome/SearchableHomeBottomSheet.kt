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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.googlehomeapisampleapp.R

/**
 * Top-level Composable for the Searchable Home feature.
 * Displays a ModalBottomSheet that allows the user to query their home's event history using AI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableHomeBottomSheetView(
  structureId: String,
  onDismissRequest: () -> Unit,
  viewModel: SearchableHomeViewModel = hiltViewModel(key = structureId),
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val isSending by viewModel.isSending.collectAsStateWithLifecycle()

  LaunchedEffect(structureId) {
    viewModel.setStructureId(structureId)
  }

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    modifier = Modifier.fillMaxHeight(),
  ) {
    SearchableHomeBottomSheetContent(
      messages = messages,
      isSending = isSending,
      onSendMessage = { viewModel.sendMessage(it) },
    )
  }
}

/**
 * Content of the Searchable Home bottom sheet, including the message history list
 * and the input text field with a send button.
 */
@Composable
internal fun SearchableHomeBottomSheetContent(
  messages: List<ChatMessage>,
  isSending: Boolean,
  onSendMessage: (String) -> Unit,
) {
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(16.dp)
        .fillMaxHeight()
        .testTag("SearchableHomeBottomSheetContent")
  ) {
    Text(
      text = stringResource(id = R.string.search_home),
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(bottom = 16.dp),
    )

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(messages) { message -> ChatBubble(message) }
      if (isSending) {
        item {
          ChatBubbleContainer(isOutgoing = false, isError = false) {
            TypingIndicator()
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text(stringResource(id = R.string.search_home_placeholder)) },
        shape = RoundedCornerShape(24.dp),
        enabled = !isSending,
      )
      Spacer(modifier = Modifier.width(8.dp))
      IconButton(
        onClick = {
          if (inputText.isNotBlank() && !isSending) {
            onSendMessage(inputText)
            inputText = ""
          }
        },
        enabled = inputText.isNotBlank() && !isSending,
      ) {
        Icon(
          imageVector = Icons.Default.Send,
          contentDescription = stringResource(id = R.string.send_description),
          tint =
            if (inputText.isNotBlank() && !isSending) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.outline
            },
          modifier = Modifier.size(24.dp),
        )
      }
    }
  }
}

/**
 * A container that styles a chat bubble, handling alignment, background color,
 * shape, and padding based on whether the message is outgoing or an error.
 */
@Composable
fun ChatBubbleContainer(
  isOutgoing: Boolean,
  isError: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
  val bubbleColor =
    if (isError) {
      MaterialTheme.colorScheme.errorContainer
    } else if (isOutgoing) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.secondaryContainer
    }
  val shape =
    if (isOutgoing) {
      RoundedCornerShape(topStart = 16.dp, topEnd = 2.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
      RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

  val paddingModifier =
    if (isOutgoing) Modifier.padding(start = 48.dp) else Modifier.padding(end = 48.dp)

  Box(modifier = modifier.fillMaxWidth().then(paddingModifier), contentAlignment = alignment) {
    Box(
      modifier =
        Modifier.clip(shape).background(bubbleColor).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      content()
    }
  }
}

/**
 * Renders a [ChatMessage] inside a [ChatBubbleContainer].
 * Handles different message types: Text (including errors) and CameraEvents.
 */
@Composable
fun ChatBubble(message: ChatMessage, modifier: Modifier = Modifier) {
  val textColor =
    if (message.isError) {
      MaterialTheme.colorScheme.onErrorContainer
    } else if (message.isOutgoing) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSecondaryContainer
    }

  ChatBubbleContainer(
    isOutgoing = message.isOutgoing,
    isError = message.isError,
    modifier = modifier
  ) {
    if (message.isError) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Error,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(24.dp).padding(end = 8.dp),
        )
        Text(
          text = stringResource(id = R.string.search_home_error, message.text),
          color = textColor,
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    } else if (message is ChatMessage.Text) {
      Text(text = message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
    } else if (message is ChatMessage.CameraEvent) {
      SearchableHomeCameraEventView(event = message.event)
    }
  }
}

/**
 * Renders a pulsing typing indicator with three dots.
 * Used to indicate that the AI is processing the query.
 */
@Composable
fun TypingIndicator() {
  val infiniteTransition = rememberInfiniteTransition(label = "typing")
  val alpha1 by
    infiniteTransition.animateFloat(
      initialValue = 0.2f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
      label = "dot1",
    )
  val alpha2 by
    infiniteTransition.animateFloat(
      initialValue = 0.2f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(600, delayMillis = 200),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "dot2",
    )
  val alpha3 by
    infiniteTransition.animateFloat(
      initialValue = 0.2f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(600, delayMillis = 400),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "dot3",
    )

  Row(
    modifier = Modifier.padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Dot(alpha1)
    Dot(alpha2)
    Dot(alpha3)
  }
}

/**
 * A single dot in the typing indicator, with a configurable alpha value for animation.
 */
@Composable
private fun Dot(alpha: Float) {
  Box(
    modifier =
      Modifier.size(8.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha))
  )
}

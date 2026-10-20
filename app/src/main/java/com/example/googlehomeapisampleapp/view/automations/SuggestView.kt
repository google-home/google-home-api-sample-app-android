package com.example.googlehomeapisampleapp.view.automations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.SuggestViewModel
import com.google.home.automation.FeedbackType
import kotlinx.coroutines.launch

@Composable
fun SuggestListSection(
    homeAppVM: HomeAppViewModel,
    suggestVM: SuggestViewModel = viewModel()
) {
    val structureVM = homeAppVM.selectedStructureVM.collectAsStateWithLifecycle().value
    val suggestions = suggestVM.suggestions.collectAsStateWithLifecycle().value
    val isLoading = suggestVM.isLoading.collectAsStateWithLifecycle().value
    val error = suggestVM.error.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()

    // Automatically trigger loading when the structure is available or changes
    LaunchedEffect(structureVM?.structure) {
        suggestVM.loadSuggestions(structureVM?.structure)
    }

    LaunchedEffect(error) {
        error?.let {
            MainActivity.showError(suggestVM, it)
            suggestVM.clearError()
        }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {

        // Render loading indicator or the list header
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
        } else if (suggestions.isNotEmpty()) {
            Text("Suggested Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

            // Render each suggestion
            suggestions.forEach { suggestion ->
                val metadata = suggestion.suggestionMetadata
                val feedbackType = metadata.feedbackType
                val suggestionId = suggestion.getSuggestionId()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .clickable {
                            scope.launch {
                                homeAppVM.selectedDraftVM.emit(suggestVM.createDraftViewModel(suggestion))
                            }
                        }
                ) {
                    Column(Modifier.fillMaxWidth()) {

                        // Name
                        Text(
                            text = metadata.name,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Description - Max 2 lines to prevent it from becoming 3+ lines, wrapped at the edge
                        Text(
                            text = metadata.description,
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom Row: Source/Type on the left, feedback buttons aligned to the right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            // Left side: Display source and type metadata
                            Text(
                                text = "${metadata.source.name} • ${metadata.type.joinToString { it.name }}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp) // Ensure text doesn't overlap with buttons
                            )

                            // Right side: Small feedback controls
                            Row(verticalAlignment = Alignment.CenterVertically) {

                                // Like Button
                                IconButton(
                                    onClick = { suggestVM.likeSuggestion(structureVM?.structure, suggestionId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (feedbackType == FeedbackType.LIKE) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = "Like",
                                        tint = if (feedbackType == FeedbackType.LIKE) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Dislike Button
                                IconButton(
                                    onClick = { suggestVM.dislikeSuggestion(structureVM?.structure, suggestionId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (feedbackType == FeedbackType.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                        contentDescription = "Dislike",
                                        tint = if (feedbackType == FeedbackType.DISLIKE) MaterialTheme.colorScheme.error else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Clear Feedback Button
                                IconButton(
                                    onClick = { suggestVM.clearSuggestionFeedback(structureVM?.structure, suggestionId) },
                                    enabled = feedbackType != FeedbackType.UNSPECIFIED,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear Feedback",
                                        tint = if (feedbackType != FeedbackType.UNSPECIFIED) MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
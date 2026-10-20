package com.example.googlehomeapisampleapp.viewmodel.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.home.Structure
import com.google.home.automation.AutomationSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SuggestViewModel : ViewModel() {

    private val _suggestions = MutableStateFlow<List<AutomationSuggestion>>(emptyList())
    val suggestions: StateFlow<List<AutomationSuggestion>> = _suggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _error.value = null
    }

    private suspend fun fetchSuggestions(structure: Structure) {
        _suggestions.value = structure.suggestions().toList()
    }

    /**
     * Fetches the automation suggestions from the provided structure.
     */
    fun loadSuggestions(structure: Structure?) {
        if (structure == null) {
            _suggestions.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                fetchSuggestions(structure)
            } catch (e: Exception) {
                _error.value = e.message ?: "loadSuggestions failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sends a LIKE feedback for a specific suggestion and reloads the list.
     */
    fun likeSuggestion(structure: Structure?, suggestionId: String) {
        if (structure == null) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                structure.likeSuggestion(suggestionId)
                fetchSuggestions(structure)
            } catch (e: Exception) {
                _error.value = e.message ?: "likeSuggestion failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sends a DISLIKE feedback for a specific suggestion and reloads the list.
     */
    fun dislikeSuggestion(structure: Structure?, suggestionId: String) {
        if (structure == null) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                structure.dislikeSuggestion(suggestionId)
                fetchSuggestions(structure)
            } catch (e: Exception) {
                _error.value = e.message ?: "dislikeSuggestion failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clears the user's feedback for a specific suggestion and reloads the list.
     */
    fun clearSuggestionFeedback(structure: Structure?, suggestionId: String) {
        if (structure == null) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                structure.clearSuggestionFeedback(suggestionId)
                fetchSuggestions(structure)
            } catch (e: Exception) {
                _error.value = e.message ?: "clearSuggestionFeedback failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Creates a DraftViewModel from a suggested automation.
     */
    fun createDraftViewModel(suggestion: AutomationSuggestion): DraftViewModel {
        return DraftViewModel(
            candidateVM = null,
            presetDraft = suggestion.suggestionInstance,
            isLocked = true, // set true because not implement full automation edit yet
            automationType = DraftViewModel.AutomationType.CUSTOM
        )
    }
}
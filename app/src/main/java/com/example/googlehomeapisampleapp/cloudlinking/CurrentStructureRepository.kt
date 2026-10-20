package com.example.googlehomeapisampleapp.cloudlinking

import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository acting as the single source of truth for the currently selected structure.
 *
 * This is shared across different ViewModels (e.g. [HomeAppViewModel], [CloudLinkingViewModel]) to
 * ensure they all operate on the same active structure.
 */
@Singleton
class CurrentStructureRepository @Inject constructor() {
  private val _selectedStructureVM = MutableStateFlow<StructureViewModel?>(null)

  // Flow of the currently selected structure.
  val selectedStructureVM: StateFlow<StructureViewModel?> = _selectedStructureVM.asStateFlow()

  /** Updates the selected structure. */
  fun setSelectedStructure(structureViewModel: StructureViewModel?) {
    _selectedStructureVM.value = structureViewModel
  }
}

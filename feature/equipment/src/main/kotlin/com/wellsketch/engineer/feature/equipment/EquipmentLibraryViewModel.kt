package com.wellsketch.engineer.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellsketch.engineer.core.data.repository.EquipmentCatalogRepository
import com.wellsketch.engineer.core.model.equipment.EquipmentItem
import com.wellsketch.engineer.domain.equipment.EquipmentGroup
import com.wellsketch.engineer.domain.equipment.EquipmentLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EquipmentUiState(
    val query: String = "",
    val groups: List<EquipmentGroup> = emptyList(),
    val selected: EquipmentItem? = null,
)

class EquipmentLibraryViewModel(repository: EquipmentCatalogRepository) : ViewModel() {
    val library = EquipmentLibrary()
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<EquipmentItem?>(null)

    val uiState: StateFlow<EquipmentUiState> =
        combine(repository.observeItems(), query, selected) { items, q, sel ->
            EquipmentUiState(q, library.group(items, q), sel)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquipmentUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun select(item: EquipmentItem?) {
        selected.value = item
    }
}

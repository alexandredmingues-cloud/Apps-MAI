package com.example.ui.advisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AcquereurEntity
import com.example.data.local.AcquereurRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AcquereursViewModel(
    private val repository: AcquereurRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow("Tous")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    val allAcquereurs: StateFlow<List<AcquereurEntity>> = repository.allAcquereurs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val filteredAcquereurs: StateFlow<List<AcquereurEntity>> = combine(
        allAcquereurs,
        _searchQuery,
        _statusFilter
    ) { list, query, status ->
        val q = query.trim()
        list.filter { item ->
            val matchesStatus = status == "Tous" || item.status == status
            val matchesQuery = q.isEmpty() ||
                item.fullName.contains(q, ignoreCase = true) ||
                item.sectorsSummary.contains(q, ignoreCase = true) ||
                item.propertyType.contains(q, ignoreCase = true) ||
                item.essentialCriteria.contains(q, ignoreCase = true) ||
                item.status.contains(q, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(status: String) {
        _statusFilter.value = status
    }

    fun saveAcquereur(entity: AcquereurEntity, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (entity.id == 0L) {
                repository.insert(entity.copy(updatedAtMillis = System.currentTimeMillis()))
                onDone("Acquéreur « ${entity.fullName} » enregistré localement.")
            } else {
                repository.update(entity)
                onDone("Fiche de « ${entity.fullName} » mise à jour.")
            }
        }
    }

    fun updateStatus(id: Long, newStatus: String, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.updateStatus(id, newStatus)
            onDone("Statut actualisé : $newStatus")
        }
    }

    fun deleteAcquereur(item: AcquereurEntity, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteById(item.id)
            onDone("Acquéreur « ${item.fullName} » supprimé.")
        }
    }

    fun eraseAllLocalAcquereurs(onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAll()
            onDone("Base locale acquéreurs purgée conformément à l'article 17 RGPD.")
        }
    }
}

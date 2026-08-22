package app.promise.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.domain.GlobalSearchResult
import app.promise.android.domain.SearchRepository
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val result: GlobalSearchResult) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val haptics: PromiseHaptics,
    private val appEventBus: AppEventBus,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<SearchUiState> = _query
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { text ->
            flow {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    emit(SearchUiState.Idle)
                    return@flow
                }
                emit(SearchUiState.Loading)
                try {
                    val result = searchRepository.search(trimmed)
                    if (result.isEmpty) {
                        emit(SearchUiState.Empty)
                    } else {
                        emit(SearchUiState.Success(result))
                    }
                } catch (t: Throwable) {
                    emit(SearchUiState.Error(t.message ?: "Failed to load search results"))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState.Idle,
        )

    init {
        viewModelScope.launch {
            appEventBus.events.collect { event ->
                if (event is AppMutationEvent.CommitmentUpdated ||
                    event is AppMutationEvent.CommitmentCreated ||
                    event is AppMutationEvent.GoalUpdated ||
                    event is AppMutationEvent.GoalCreated
                ) {
                    if (_query.value.isNotBlank()) {
                        _refreshTrigger.value += 1
                    }
                }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun clearQuery() {
        _query.value = ""
        haptics.light()
    }

    fun retry() {
        val current = _query.value
        _query.value = ""
        _query.value = current
    }
}

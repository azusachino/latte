package com.azusachino.latte.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.network.YandeApi
import com.azusachino.latte.data.settings.LattePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExploreUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val searchTags: String = "",
    val popularPeriod: PopularPeriod? = null,
    val popularDate: LocalDate = LocalDate.now(),
    val page: Int = 1,
    val hasMore: Boolean = true,
)

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val api = YandeApi()
    val preferences = LattePreferences(application)

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val columnCount: StateFlow<Int> = preferences.columnCount

    init {
        loadInitial()
    }

    fun cycleColumns(): Int {
        return preferences.cycleColumnCount()
    }

    fun setColumnCount(count: Int) {
        preferences.setColumnCount(count)
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, page = 1) }
            try {
                val state = _uiState.value
                val posts = if (state.popularPeriod != null) {
                    api.getPopular(state.popularPeriod, state.popularDate)
                } else {
                    api.getPosts(page = 1, tags = state.searchTags.ifBlank { null })
                }
                _uiState.update {
                    it.copy(
                        posts = posts,
                        isLoading = false,
                        hasMore = posts.isNotEmpty() && state.popularPeriod == null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load posts")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val state = _uiState.value
                val posts = if (state.popularPeriod != null) {
                    api.getPopular(state.popularPeriod, state.popularDate)
                } else {
                    api.getPosts(page = 1, tags = state.searchTags.ifBlank { null })
                }
                _uiState.update {
                    it.copy(
                        posts = posts,
                        isRefreshing = false,
                        page = 1,
                        hasMore = posts.isNotEmpty() && state.popularPeriod == null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRefreshing = false, error = e.message ?: "Failed to refresh")
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore || state.popularPeriod != null) return

        viewModelScope.launch {
            val nextPage = state.page + 1
            try {
                val newPosts = api.getPosts(page = nextPage, tags = state.searchTags.ifBlank { null })
                _uiState.update {
                    it.copy(
                        posts = it.posts + newPosts,
                        page = nextPage,
                        hasMore = newPosts.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                // Silently fail load more or notify
            }
        }
    }

    fun search(tags: String) {
        _uiState.update {
            it.copy(
                searchTags = tags.trim(),
                popularPeriod = null,
                page = 1,
            )
        }
        loadInitial()
    }

    fun selectPopular(period: PopularPeriod?) {
        _uiState.update {
            it.copy(
                popularPeriod = period,
                searchTags = "",
                page = 1,
            )
        }
        loadInitial()
    }
}

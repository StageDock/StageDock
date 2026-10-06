package com.stagedock.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stagedock.app.data.Stage
import com.stagedock.app.data.StageApi
import com.stagedock.app.data.StageSort
import com.stagedock.app.install.InstallRegistry
import com.stagedock.app.install.StageInstaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class UiState(
    val stages: List<Stage> = emptyList(),
    val query: String = "",
    val sort: StageSort = StageSort.Top,
    val loading: Boolean = false,
    val loadError: String? = null,
    val endReached: Boolean = false,
    val hasAccess: Boolean = false,
    val installedFiles: Set<String> = emptySet(),
    val installedByStageId: Map<String, List<String>> = emptyMap(),
    val progress: Map<String, Float> = emptyMap(),
    val message: String? = null,
    val showInstalledOnly: Boolean = false,
) {
    fun filesFor(stage: Stage): List<String> {
        val tracked = installedByStageId[stage.id].orEmpty().filter { it in installedFiles }
        if (tracked.isNotEmpty()) return tracked
        return listOfNotNull(stage.fileName?.takeIf { it in installedFiles })
    }

    fun isInstalled(stage: Stage): Boolean = filesFor(stage).isNotEmpty()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    private val api = StageApi(client)
    private val installer = StageInstaller(client, app.cacheDir)
    private val registry = InstallRegistry(app)

    private val _state = MutableStateFlow(UiState(installedByStageId = registry.load()))
    val state: StateFlow<UiState> = _state

    private var page = 1
    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        refreshAccess()
        loadNext(reset = true)
    }

    fun refreshAccess() {
        viewModelScope.launch {
            val access = installer.hasAccess()
            val files = installer.installedFileNames()
            _state.update { it.copy(hasAccess = access, installedFiles = files) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadNext(reset = true)
        }
    }

    fun setSort(sort: StageSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        loadNext(reset = true)
    }

    fun setInstalledOnly(value: Boolean) {
        _state.update { it.copy(showInstalledOnly = value) }
        refreshAccess()
    }

    fun loadNext(reset: Boolean = false) {
        val current = _state.value
        if (!reset && (current.loading || current.endReached || current.loadError != null)) return
        loadJob?.cancel()
        if (reset) {
            page = 1
            _state.update { it.copy(stages = emptyList(), endReached = false, loadError = null) }
        }
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            val snapshot = _state.value
            runCatching { api.fetchPage(page, snapshot.query, snapshot.sort) }
                .onSuccess { result ->
                    page++
                    _state.update {
                        val merged = (it.stages + result.stages).distinctBy { s -> s.id }
                        val lastPage = result.pageCount?.let { count -> result.page >= count } ?: false
                        it.copy(
                            stages = merged,
                            endReached = lastPage || result.stages.isEmpty() || merged.size == it.stages.size,
                            loading = false,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(loading = false, loadError = e.message ?: "Could not load stages") }
                }
        }
    }

    fun retry() {
        _state.update { it.copy(loadError = null) }
        loadNext(reset = _state.value.stages.isEmpty())
    }

    fun install(stage: Stage) {
        if (_state.value.progress.containsKey(stage.id)) return
        viewModelScope.launch {
            _state.update { it.copy(progress = it.progress + (stage.id to 0f)) }
            val result = installer.install(stage) { p ->
                _state.update { it.copy(progress = it.progress + (stage.id to p)) }
            }
            val files = installer.installedFileNames()
            _state.update { current ->
                val written = result.getOrNull()
                val mapping = if (written != null) current.installedByStageId + (stage.id to written) else current.installedByStageId
                current.copy(
                    progress = current.progress - stage.id,
                    installedFiles = files,
                    installedByStageId = mapping,
                    message = result.exceptionOrNull()?.let { "Install failed: ${it.message}" }
                        ?: "Installed ${stage.name}. Restart Synth Riders to see it.",
                )
            }
            registry.save(_state.value.installedByStageId)
        }
    }

    fun remove(fileName: String) {
        viewModelScope.launch {
            val removed = installer.remove(fileName)
            val files = installer.installedFileNames()
            _state.update { current ->
                current.copy(
                    installedFiles = files,
                    installedByStageId = current.installedByStageId
                        .mapValues { (_, v) -> v - fileName }
                        .filterValues { it.isNotEmpty() },
                    message = if (removed) "Removed $fileName" else "Could not remove $fileName",
                )
            }
            registry.save(_state.value.installedByStageId)
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val USER_AGENT = "StageDock/0.1 (Meta Quest; Android)"
    }
}

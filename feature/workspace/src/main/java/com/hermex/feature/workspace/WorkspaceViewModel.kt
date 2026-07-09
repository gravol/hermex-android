// FileBrowserViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data classes matching the expected structure
data class WorkspaceEntry(
    val name: String,
    val path: String?,
    val isBrowsableDirectory: Boolean,
    val isFile: Boolean, // Derived or separate property
    val searchableText: String = "$name ${path ?: ""}"
)

data class Breadcrumb(
    val title: String,
    val path: String
)

data class FileBrowserState(
    val isLoading: Boolean = false,
    val entries: List<WorkspaceEntry> = emptyList(),
    val errorMessage: String? = null,
    val currentPath: String = "/",
    val displayPath: String = "/",
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val isAtRoot: Boolean = false,
    val parentPath: String? = null,
    val lastError: Error? = null
)

class FileBrowserViewModel(
    private val session: SessionSummary, // Assuming this exists in your project
    private val server: String // URL string instead of URL object for simplicity
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    init {
        loadInitialRootIfNeeded()
    }

    // Simulated API calls - Replace with your actual networking logic
    private suspend fun fetchFiles(path: String): List<WorkspaceEntry> {
        // TODO: Implement actual network call
        return emptyList() 
    }

    fun load(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val entries = fetchFiles(path)
                // Logic to calculate breadcrumbs and parent path would go here
                val breadcrumbs = calculateBreadcrumbs(path)
                val isAtRoot = path == "/"
                val parentPath = if (path == "/") null else path.substringBeforeLast("/", "/")

                _state.update {
                    it.copy(
                        isLoading = false,
                        entries = entries,
                        currentPath = path,
                        displayPath = path,
                        breadcrumbs = breadcrumbs,
                        isAtRoot = isAtRoot,
                        parentPath = parentPath,
                        lastError = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message,
                        lastError = e
                    )
                }
            }
        }
    }

    fun loadRoot() {
        load("/")
    }

    fun loadInitialRootIfNeeded() {
        if (_state.value.entries.isEmpty()) {
            loadRoot()
        }
    }

    fun reloadCurrentPath() {
        load(_state.value.currentPath)
    }

    private fun calculateBreadcrumbs(path: String): List<Breadcrumb> {
        // Simplified breadcrumb logic
        if (path == "/") return listOf(Breadcrumb("/", "/"))
        return path.split("/").filter { it.isNotEmpty() }.mapIndexed { index, segment ->
            val segmentPath = "/" + path.split("/").take(index + 1).joinToString("/")
            Breadcrumb(segment, segmentPath)
        }
    }
}
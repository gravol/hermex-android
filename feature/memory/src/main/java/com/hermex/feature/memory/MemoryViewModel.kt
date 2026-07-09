import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URL
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

// Mocking the enum and error types for completeness
enum class MemorySection(val title: String, val systemImage: String, val emptyMessage: String) {
    NOTES("Notes", "note.text", "No notes found."),
    SETTINGS("Settings", "gearshape", "No settings configured."),
    // Add other sections as needed
}

data class MemoryState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val actionErrorMessage: String? = null,
    val sections: Map<MemorySection, String> = emptyMap(),
    val modifiedAts: Map<MemorySection, Date?> = emptyMap()
)

class MemoryViewModel(
    private val server: URL,
    private val onApiError: (Error) -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryState())
    val state: StateFlow<MemoryState> = _state.asStateFlow()

    // Private state to hold last error before it's consumed
    private var lastError: Error? = null

    init {
        loadMemory()
    }

    fun clearActionError() {
        _state.value = _state.value.copy(actionErrorMessage = null)
    }

    fun loadMemory() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, hasLoaded = false, errorMessage = null) }
            
            try {
                // Simulate network call
                // val data = repository.fetchMemory(server) 
                // For this demo, we simulate success after delay
                kotlinx.coroutines.delay(1000) 
                
                val newSections = mapOf(
                    MemorySection.NOTES to "This is a sample note content.\nIt supports Markdown syntax.\n\n- Item 1\n- Item 2",
                    MemorySection.SETTINGS to "" // Empty section
                )
                
                val newModifiedAts = newSections.mapKeys { it.key } 
                    .mapValues { Date() }

                _state.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        sections = newSections,
                        modifiedAts = newModifiedAts
                    )
                }
            } catch (e: Exception) {
                lastError = e
                onApiError(e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = false,
                        errorMessage = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    fun save(section: MemorySection, content: String): Boolean {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionErrorMessage = null) }
            
            try {
                // Simulate save
                kotlinx.coroutines.delay(500)
                
                val newModifiedAts = _state.value.modifiedAts.toMutableMap()
                newModifiedAts[section] = Date()
                
                val newSections = _state.value.sections.toMutableMap()
                newSections[section] = content

                _state.update {
                    it.copy(
                        isSaving = false,
                        sections = newSections,
                        modifiedAts = newModifiedAts
                    )
                }
                true
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        actionErrorMessage = e.message ?: "Save failed"
                    )
                }
                false
            }
        }
        return false // Returns immediately, result is in StateFlow
    }
}
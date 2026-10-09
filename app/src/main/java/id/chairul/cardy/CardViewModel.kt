package id.chairul.cardy

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

data class UiState(
    val entryId: String? = null,
    val imagePath: String? = null,
    val busy: Boolean = false,
    val card: CardData = CardData(),
    val rawText: String = "",
    val error: String? = null,
    val scanned: Boolean = false,
    val savedToContacts: Boolean = false,
)

class CardViewModel(app: Application) : AndroidViewModel(app) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val repo = ArchiveRepo(app)
    val prefs = Prefs(app)

    val archive: StateFlow<List<ArchiveEntry>> = repo.entries

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var persistJob: Job? = null

    /** Photo taken with the in-app camera (already cropped to the guide frame). */
    fun scanCaptured(file: File) = process { id -> repo.adoptFile(file, id) }

    /** Image from gallery or shared from another app. */
    fun scanUri(uri: Uri) = process { id -> repo.importUri(uri, id) }

    private fun process(store: suspend (String) -> File) {
        flushPersist()
        _state.update { UiState(busy = true) }
        viewModelScope.launch {
            try {
                val id = repo.newId()
                val file = store(id)
                _state.update { it.copy(imagePath = file.absolutePath) }

                val image = InputImage.fromFilePath(getApplication(), Uri.fromFile(file))
                val result = recognizer.process(image).await()
                val lines = result.textBlocks
                    .sortedBy { it.boundingBox?.top ?: 0 }
                    .flatMap { it.lines }
                    .map { OcrLine(it.text, it.boundingBox?.height() ?: 0) }
                val card = CardParser.parse(lines)

                // Archive every scan automatically
                repo.upsert(
                    ArchiveEntry(
                        id = id, createdAt = System.currentTimeMillis(), imagePath = file.absolutePath,
                        card = card, rawText = result.text,
                    )
                )
                _state.update {
                    it.copy(
                        entryId = id, busy = false, card = card, rawText = result.text, scanned = true,
                        error = if (lines.isEmpty()) "No text found. Try better lighting or move closer." else null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Scan failed: ${e.message}") }
            }
        }
    }

    /** Re-open an archived card in the review screen. */
    fun open(e: ArchiveEntry) {
        flushPersist()
        _state.value = UiState(
            entryId = e.id, imagePath = e.imagePath, card = e.card, rawText = e.rawText,
            scanned = true, savedToContacts = e.savedToContacts,
        )
    }

    fun edit(transform: (CardData) -> CardData) {
        _state.update { it.copy(card = transform(it.card)) }
        persistJob?.cancel()
        persistJob = viewModelScope.launch { delay(600); persistNow() }
    }

    private fun flushPersist() {
        if (persistJob?.isActive == true) {
            persistJob?.cancel()
            val snapshot = _state.value
            viewModelScope.launch { persistNow(snapshot) }
        }
    }

    private suspend fun persistNow(s: UiState = _state.value) {
        val id = s.entryId ?: return
        repo.update(id) { it.copy(card = s.card) }
    }

    fun markSaved() {
        _state.update { it.copy(savedToContacts = true) }
        val id = _state.value.entryId ?: return
        val card = _state.value.card
        viewModelScope.launch { repo.update(id) { it.copy(card = card, savedToContacts = true) } }
    }

    fun markWhatsApp() {
        val id = _state.value.entryId ?: return
        val card = _state.value.card
        viewModelScope.launch { repo.update(id) { it.copy(card = card, lastWhatsAppAt = System.currentTimeMillis()) } }
    }

    fun delete(id: String) {
        if (_state.value.entryId == id) { persistJob?.cancel(); _state.value = UiState() }
        viewModelScope.launch { repo.delete(id) }
    }

    fun reset() {
        flushPersist()
        _state.value = UiState()
    }

    override fun onCleared() {
        recognizer.close()
    }
}

package id.chairul.cardy

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UiState(
    val imageUri: Uri? = null,
    val busy: Boolean = false,
    val card: CardData = CardData(),
    val rawText: String = "",
    val error: String? = null,
    val scanned: Boolean = false,
)

class CardViewModel(app: Application) : AndroidViewModel(app) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun scan(uri: Uri) {
        _state.update { it.copy(imageUri = uri, busy = true, error = null) }
        viewModelScope.launch {
            try {
                // fromFilePath honours EXIF rotation
                val image = InputImage.fromFilePath(getApplication(), uri)
                val result = recognizer.process(image).await()

                // Use line-level output (keeps label + number together) with heights
                val lines = result.textBlocks
                    .sortedBy { it.boundingBox?.top ?: 0 }
                    .flatMap { block -> block.lines }
                    .map { OcrLine(it.text, it.boundingBox?.height() ?: 0) }

                val card = CardParser.parse(lines)
                _state.update {
                    it.copy(
                        busy = false, card = card, rawText = result.text, scanned = true,
                        error = if (lines.isEmpty()) "No text found. Try better lighting or move closer." else null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "OCR failed: ${e.message}") }
            }
        }
    }

    fun edit(transform: (CardData) -> CardData) = _state.update { it.copy(card = transform(it.card)) }

    fun reset() = _state.update { UiState() }

    override fun onCleared() {
        recognizer.close()
    }
}

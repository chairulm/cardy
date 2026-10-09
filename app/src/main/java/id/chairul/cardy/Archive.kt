package id.chairul.cardy

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ArchiveEntry(
    val id: String,
    val createdAt: Long,
    val imagePath: String,
    val card: CardData,
    val rawText: String = "",
    val savedToContacts: Boolean = false,
    val lastWhatsAppAt: Long = 0L,
)

/**
 * Local archive of every scanned card: image file + extracted data.
 * Stored privately in the app's files dir (archive/index.json + archive/<id>.jpg).
 */
class ArchiveRepo(private val context: Context) {

    private val dir = File(context.filesDir, "archive").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val lock = Mutex()

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<ArchiveEntry>> = _entries

    fun newId(): String = UUID.randomUUID().toString()

    /** Move a freshly captured file into the archive. */
    suspend fun adoptFile(src: File, id: String): File = withContext(Dispatchers.IO) {
        val dst = File(dir, "$id.jpg")
        if (!src.renameTo(dst)) { src.copyTo(dst, overwrite = true); src.delete() }
        dst
    }

    /** Copy an image picked from gallery/shared into the archive (keeps EXIF orientation). */
    suspend fun importUri(uri: Uri, id: String): File = withContext(Dispatchers.IO) {
        val dst = File(dir, "$id.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open image" }
            dst.outputStream().use { input.copyTo(it) }
        }
        dst
    }

    suspend fun upsert(e: ArchiveEntry) = mutate { list ->
        val i = list.indexOfFirst { it.id == e.id }
        if (i >= 0) list.toMutableList().also { it[i] = e } else listOf(e) + list
    }

    suspend fun update(id: String, f: (ArchiveEntry) -> ArchiveEntry) = mutate { list ->
        list.map { if (it.id == id) f(it) else it }
    }

    suspend fun delete(id: String) = mutate { list ->
        list.find { it.id == id }?.let { File(it.imagePath).delete() }
        list.filterNot { it.id == id }
    }

    fun get(id: String): ArchiveEntry? = _entries.value.find { it.id == id }

    private suspend fun mutate(f: (List<ArchiveEntry>) -> List<ArchiveEntry>) = lock.withLock {
        val next = f(_entries.value)
        _entries.value = next
        withContext(Dispatchers.IO) { save(next) }
    }

    // ---- JSON persistence ----

    private fun load(): List<ArchiveEntry> = try {
        if (!index.exists()) emptyList() else {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
                .filter { File(it.imagePath).exists() }
        }
    } catch (e: Exception) { emptyList() }

    private fun save(list: List<ArchiveEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(index)) { index.writeText(arr.toString()); tmp.delete() }
    }

    private fun toJson(e: ArchiveEntry) = JSONObject().apply {
        put("id", e.id); put("createdAt", e.createdAt); put("imagePath", e.imagePath)
        put("rawText", e.rawText); put("saved", e.savedToContacts); put("wa", e.lastWhatsAppAt)
        val c = e.card
        put("name", c.name); put("title", c.title); put("company", c.company)
        put("mobile", c.mobile); put("phone", c.phone); put("fax", c.fax)
        put("email", c.email); put("website", c.website); put("address", c.address)
    }

    private fun fromJson(o: JSONObject) = ArchiveEntry(
        id = o.getString("id"),
        createdAt = o.optLong("createdAt"),
        imagePath = o.getString("imagePath"),
        rawText = o.optString("rawText"),
        savedToContacts = o.optBoolean("saved"),
        lastWhatsAppAt = o.optLong("wa"),
        card = CardData(
            name = o.optString("name"), title = o.optString("title"), company = o.optString("company"),
            mobile = o.optString("mobile"), phone = o.optString("phone"), fax = o.optString("fax"),
            email = o.optString("email"), website = o.optString("website"), address = o.optString("address"),
        ),
    )
}

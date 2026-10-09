package id.chairul.cardy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { Home, Camera, Archive }

class MainActivity : ComponentActivity() {

    private val vm: CardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { App(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        uri?.let { vm.scanUri(it) }
    }
}

@Composable
fun App(vm: CardViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != Screen.Home || state.scanned) {
        when {
            screen != Screen.Home -> screen = Screen.Home
            else -> vm.reset()
        }
    }

    when (screen) {
        Screen.Camera -> CameraScreen(
            onCaptured = { f -> vm.scanCaptured(f); screen = Screen.Home },
            onClose = { screen = Screen.Home },
        )
        Screen.Archive -> ArchiveScreen(
            vm,
            onOpen = { e -> vm.open(e); screen = Screen.Home },
            onBack = { screen = Screen.Home },
        )
        Screen.Home -> HomeScreen(
            vm,
            onCamera = { screen = Screen.Camera },
            onArchive = { screen = Screen.Archive },
        )
    }
}

// ---------------------------------------------------------------- Home / Review

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: CardViewModel, onCamera: () -> Unit, onArchive: () -> Unit) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val archive by vm.archive.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showRaw by remember { mutableStateOf(false) }
    var showWa by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::scanUri)
    }
    val saveContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            vm.markSaved()
            val hasPhone = state.card.mobile.isNotBlank() || state.card.phone.isNotBlank()
            scope.launch {
                val r = snackbar.showSnackbar(
                    "Saved to contacts", actionLabel = if (hasPhone) "WhatsApp" else null,
                    duration = SnackbarDuration.Long,
                )
                if (r == SnackbarResult.ActionPerformed) showWa = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Cardy") },
                actions = {
                    if (state.scanned) IconButton(onClick = vm::reset) { Icon(Icons.Outlined.AddAPhoto, "New scan") }
                    IconButton(onClick = onArchive) {
                        BadgedBox(badge = { if (archive.isNotEmpty()) Badge { Text("${archive.size}") } }) {
                            Icon(Icons.Outlined.Inventory2, "Archive")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
        bottomBar = {
            if (state.scanned) Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasPhone = state.card.mobile.isNotBlank() || state.card.phone.isNotBlank()
                    FilledTonalButton(
                        onClick = { showWa = true }, enabled = hasPhone, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                    ) { Icon(Icons.Outlined.Forum, null); Spacer(Modifier.width(6.dp)); Text("WhatsApp") }
                    IconButton(onClick = { shareVCard(ctx, state.card) }, enabled = state.card.name.isNotBlank()) {
                        Icon(Icons.Outlined.Share, "Share vCard")
                    }
                    Button(
                        onClick = {
                            try { saveContact.launch(ContactSaver.intent(state.card)) }
                            catch (e: Exception) { Toast.makeText(ctx, "No contacts app found", Toast.LENGTH_LONG).show() }
                        },
                        modifier = Modifier.weight(1.3f),
                        enabled = state.card.name.isNotBlank() || hasPhone,
                    ) {
                        Icon(if (state.savedToContacts) Icons.Outlined.HowToReg else Icons.Outlined.PersonAdd, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.savedToContacts) "Saved ✓" else "Save contact", maxLines = 1)
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth().aspectRatio(1.75f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.imagePath != null) {
                        AsyncImage(
                            model = File(state.imagePath!!), contentDescription = "Business card",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ContactPage, null, Modifier.size(56.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Scan a business card", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.busy) CircularProgressIndicator()
                }
            }

            if (!state.scanned) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCamera, modifier = Modifier.weight(1f), enabled = !state.busy) {
                    Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Camera")
                }
                FilledTonalButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f), enabled = !state.busy,
                ) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text("Gallery") }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            if (state.scanned) {
                Text("Review & edit", style = MaterialTheme.typography.titleMedium)
                val c = state.card
                Field("Name", c.name, Icons.Outlined.Person) { v -> vm.edit { it.copy(name = v) } }
                Field("Job title", c.title, Icons.Outlined.Badge) { v -> vm.edit { it.copy(title = v) } }
                Field("Company", c.company, Icons.Outlined.Business) { v -> vm.edit { it.copy(company = v) } }
                Field("Mobile", c.mobile, Icons.Outlined.Smartphone, KeyboardType.Phone) { v -> vm.edit { it.copy(mobile = v) } }
                Field("Office phone", c.phone, Icons.Outlined.Call, KeyboardType.Phone) { v -> vm.edit { it.copy(phone = v) } }
                Field("Fax", c.fax, Icons.Outlined.Fax, KeyboardType.Phone) { v -> vm.edit { it.copy(fax = v) } }
                Field("Email", c.email, Icons.Outlined.Email, KeyboardType.Email) { v -> vm.edit { it.copy(email = v) } }
                Field("Website", c.website, Icons.Outlined.Language, KeyboardType.Uri) { v -> vm.edit { it.copy(website = v) } }
                Field("Address", c.address, Icons.Outlined.Place, singleLine = false) { v -> vm.edit { it.copy(address = v) } }

                Text(
                    "Card is archived automatically — find it under the archive icon.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Hide raw OCR text" else "Show raw OCR text") }
                if (showRaw) Text(state.rawText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (showWa) WhatsAppDialog(vm, state.card, onDismiss = { showWa = false })
    if (showSettings) SettingsDialog(vm.prefs, onDismiss = { showSettings = false })
}

@Composable
private fun Field(
    label: String, value: String, icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, leadingIcon = { Icon(icon, null) },
        singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- WhatsApp

@Composable
fun WhatsAppDialog(vm: CardViewModel, card: CardData, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val numbers = listOf(card.mobile to "Mobile", card.phone to "Office").filter { it.first.isNotBlank() }
    var selected by remember { mutableStateOf(numbers.firstOrNull()?.first.orEmpty()) }
    var message by remember { mutableStateOf(WhatsApp.fill(vm.prefs.template, card)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Forum, null) },
        title = { Text("WhatsApp ${card.name.ifBlank { "contact" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                numbers.forEach { (num, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = num },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == num, onClick = { selected = num })
                        Text("$label: $num")
                    }
                }
                OutlinedTextField(
                    value = message, onValueChange = { message = it },
                    label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (WhatsApp.open(ctx, selected, message, vm.prefs.countryCode)) vm.markWhatsApp()
                onDismiss()
            }, enabled = selected.isNotBlank()) { Text("Open WhatsApp") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SettingsDialog(prefs: Prefs, onDismiss: () -> Unit) {
    var cc by remember { mutableStateOf(prefs.countryCode) }
    var tpl by remember { mutableStateOf(prefs.template) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cc, onValueChange = { cc = it.filter(Char::isDigit).take(4) },
                    label = { Text("Default country code") }, prefix = { Text("+") },
                    supportingText = { Text("Used when a number starts with 0 (e.g. 62 Indonesia, 60 Malaysia, 61 Australia)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tpl, onValueChange = { tpl = it },
                    label = { Text("WhatsApp greeting") }, minLines = 3,
                    supportingText = { Text("Placeholders: {name} first name, {fullname}, {company}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { tpl = Prefs.DEFAULT_TEMPLATE }) { Text("Reset greeting") }
            }
        },
        confirmButton = {
            Button(onClick = { prefs.countryCode = cc.ifBlank { "62" }; prefs.template = tpl; onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Archive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(vm: CardViewModel, onOpen: (ArchiveEntry) -> Unit, onBack: () -> Unit) {
    val entries by vm.archive.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<ArchiveEntry?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    val filtered = remember(entries, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) entries else entries.filter { e ->
            with(e.card) { listOf(name, company, title, email, mobile, phone).any { it.lowercase().contains(q) } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive (${entries.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search name, company, email…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (entries.isEmpty()) "No scanned cards yet" else "No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { e ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(e) },
                        leadingContent = {
                            AsyncImage(
                                model = File(e.imagePath), contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 84.dp, height = 48.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        },
                        headlineContent = { Text(e.card.name.ifBlank { "(no name)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                if (e.card.company.isNotBlank()) Text(e.card.company, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(fmt.format(Date(e.createdAt)), style = MaterialTheme.typography.labelSmall)
                                    if (e.savedToContacts) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Outlined.HowToReg, "Saved", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (e.lastWhatsAppAt > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Outlined.Forum, "WhatsApp sent", Modifier.size(14.dp), tint = Color(0xFF25D366))
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { toDelete = e }) { Icon(Icons.Outlined.Delete, "Delete") }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete card?") },
            text = { Text("Remove ${e.card.name.ifBlank { "this card" }} and its image from the archive. Saved phone contacts are not affected.") },
            confirmButton = { TextButton(onClick = { vm.delete(e.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Share

private fun shareVCard(ctx: Context, c: CardData) {
    val dir = File(ctx.cacheDir, "cards").apply { mkdirs() }
    val safe = c.name.ifBlank { "contact" }.replace(Regex("[^A-Za-z0-9]+"), "_")
    val f = File(dir, "$safe.vcf").apply { writeText(ContactSaver.toVCard(c)) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/x-vcard"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share contact"))
}

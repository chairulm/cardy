package id.chairul.cardy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: CardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { CardScreen(vm) }
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
        uri?.let { vm.scan(it) }
    }
}

private fun newPhotoUri(ctx: Context): Uri {
    val dir = File(ctx.cacheDir, "cards").apply { mkdirs() }
    val file = File(dir, "card_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(vm: CardViewModel) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showRaw by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let(vm::scan)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::scan)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cardy") },
                actions = {
                    if (state.scanned) IconButton(onClick = vm::reset) {
                        Icon(Icons.Outlined.Refresh, "New scan")
                    }
                },
            )
        },
        bottomBar = {
            if (state.scanned) Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { shareVCard(ctx, state.card) },
                        modifier = Modifier.weight(1f),
                        enabled = state.card.name.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("vCard")
                    }
                    Button(
                        onClick = { ContactSaver.open(ctx, state.card) },
                        modifier = Modifier.weight(2f),
                        enabled = state.card.name.isNotBlank() || state.card.mobile.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Save to Contacts")
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Preview
            Card(Modifier.fillMaxWidth().aspectRatio(1.75f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.imageUri != null) {
                        AsyncImage(
                            model = state.imageUri, contentDescription = "Business card",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ContactPage, null, Modifier.size(56.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Photograph a business card", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.busy) CircularProgressIndicator()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { newPhotoUri(ctx).also { pendingUri = it; camera.launch(it) } },
                    modifier = Modifier.weight(1f), enabled = !state.busy,
                ) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Camera") }
                FilledTonalButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f), enabled = !state.busy,
                ) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text("Gallery") }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

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

                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide raw OCR text" else "Show raw OCR text")
                }
                if (showRaw) {
                    Text(state.rawText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

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

package com.example.androidreclip

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
import java.io.File

// --- Data Models ---
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val alwaysRecord: Boolean = true,
    val customSaveLocationUri: Uri? = null
)

sealed class AppScreen {
    object Main : AppScreen()
    object Editor : AppScreen()
    object Settings : AppScreen()
}

class MainActivity : ComponentActivity() {

    private var audioService: AudioService? = null
    private var isBound by mutableStateOf(false)
    private var isEngineRunning by mutableStateOf(false)

    private var currentScreen by mutableStateOf<AppScreen>(AppScreen.Main)
    private var rawClippedFile by mutableStateOf<File?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioService.LocalBinder
            audioService = binder.getService()
            isBound = true
            isEngineRunning = audioService?.isRecording() == true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startAudioService()
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissionsAndStart()

        setContent {
            var settings by remember { mutableStateOf(AppSettings()) }

            val isDarkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        is AppScreen.Main -> MainScreenUI(
                            isEngineRunning = isEngineRunning,
                            onStartClick = { checkPermissionsAndStart() },
                            onStopClick = { stopAudioService() },
                            onClipClick = { processClipAndOpenEditor() },
                            onOpenSettings = { currentScreen = AppScreen.Settings }
                        )

                        is AppScreen.Editor -> {
                            rawClippedFile?.let { file ->
                                EditorScreenUI(
                                    inputFile = file,
                                    customSaveUri = settings.customSaveLocationUri,
                                    onBackToMain = { currentScreen = AppScreen.Main }
                                )
                            }
                        }

                        is AppScreen.Settings -> SettingsScreenUI(
                            settings = settings,
                            onSettingsChange = { updated ->
                                settings = updated
                                if (updated.alwaysRecord) checkPermissionsAndStart() else stopAudioService()
                            },
                            onBack = { currentScreen = AppScreen.Main }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun checkPermissionsAndStart() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            startAudioService()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            )
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isEngineRunning = true
    }

    private fun stopAudioService() {
        val intent = Intent(this, AudioService::class.java)
        stopService(intent)
        isEngineRunning = false
    }

    private fun processClipAndOpenEditor() {
        if (isBound && audioService?.isRecording() == true) {
            val fileName = "TempClip_${System.currentTimeMillis()}.wav"
            val outputFile = File(getExternalFilesDir(null), fileName)
            val success = audioService?.clipAudio(outputFile)

            if (success == true) {
                rawClippedFile = outputFile
                currentScreen = AppScreen.Editor
            } else {
                Toast.makeText(this, "Failed to capture clip", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Start recording first!", Toast.LENGTH_SHORT).show()
        }
    }
}

// --- 1. HOME DASHBOARD UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenUI(
    isEngineRunning: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onClipClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Android Reclip",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Status Card
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Buffer Status",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isEngineRunning) "Active Buffer (2 min)" else "Buffer Idle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEngineRunning) Color(0xFF4CAF50) else Color(0xFFE53935)
                            )
                    )
                }
            }

            // Central Hero Clip Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FilledIconButton(
                    onClick = onClipClick,
                    enabled = isEngineRunning,
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = "Clip",
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CLIP",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tap to save the last audio segment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Engine Controls & Copyright Notice Footer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onStartClick,
                        enabled = !isEngineRunning,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Engine")
                    }

                    Button(
                        onClick = onStopClick,
                        enabled = isEngineRunning,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Engine")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "© 2026 Timothy Lockley. All rights reserved.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- 2. AUDIO EDITOR SCREEN UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreenUI(inputFile: File, customSaveUri: Uri?, onBackToMain: () -> Unit) {
    val context = LocalContext.current
    var clipName by remember { mutableStateOf("Reclip_${System.currentTimeMillis()}") }
    var isSaved by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var totalDurationMs by remember { mutableIntStateOf(1) }

    var cropRange by remember { mutableStateOf(0f..1f) }
    var currentScrubberPos by remember { mutableFloatStateOf(0f) }

    fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%d:%02d", mins, secs)
    }

    DisposableEffect(inputFile) {
        val player = MediaPlayer().apply {
            setDataSource(inputFile.absolutePath)
            prepare()
            totalDurationMs = duration.coerceAtLeast(1)
            start()
        }
        mediaPlayer = player
        isPlaying = true

        onDispose {
            player.stop()
            player.release()
        }
    }

    fun playFromPosition(fraction: Float) {
        mediaPlayer?.let { player ->
            val seekPos = (fraction * totalDurationMs).toInt().coerceIn(0, totalDurationMs)
            player.seekTo(seekPos)
            player.start()
            isPlaying = true
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentScrubberPos = (player.currentPosition.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    if (currentScrubberPos >= cropRange.endInclusive) {
                        player.pause()
                        isPlaying = false
                    }
                } else {
                    isPlaying = false
                }
            }
            delay(30)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Clip", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        mediaPlayer?.stop()
                        onBackToMain()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = clipName,
                    onValueChange = { clipName = it },
                    label = { Text("Clip File Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Waveform & Scrubber Container
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                RangeSlider(
                                    value = cropRange,
                                    onValueChange = { range -> cropRange = range },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                                )

                                // Interactive Scrubber Layer
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures { offset ->
                                                val tappedFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                                currentScrubberPos = tappedFraction
                                                playFromPosition(tappedFraction)
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, _ ->
                                                val draggedFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                                currentScrubberPos = draggedFraction
                                                playFromPosition(draggedFraction)
                                            }
                                        }
                                ) {
                                    val lineX = size.width * currentScrubberPos
                                    drawLine(
                                        color = Color.Red,
                                        start = Offset(lineX, 0f),
                                        end = Offset(lineX, size.height),
                                        strokeWidth = 6f
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dynamic Timestamps
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val currentMs = (currentScrubberPos * totalDurationMs).toLong()

                            Text(
                                text = formatTime(currentMs),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )

                            Text(
                                text = formatTime(totalDurationMs.toLong()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Playback Controller
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FilledIconButton(
                        onClick = { togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Bottom Action Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = {
                        val finalFileName = if (clipName.endsWith(".wav")) clipName else "$clipName.wav"

                        if (customSaveUri != null) {
                            val pickedDir = DocumentFile.fromTreeUri(context, customSaveUri)
                            val newFile = pickedDir?.createFile("audio/wav", finalFileName)
                            if (newFile != null) {
                                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                    inputFile.inputStream().use { input -> input.copyTo(output) }
                                }
                            }
                        } else {
                            val destination = File(context.getExternalFilesDir(null), finalFileName)
                            inputFile.copyTo(destination, overwrite = true)
                            savedFile = destination
                        }

                        isSaved = true
                        Toast.makeText(context, "Saved as $finalFileName", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSaved) "Saved" else "Save Clip")
                }

                AnimatedVisibility(visible = isSaved) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                mediaPlayer?.stop()
                                onBackToMain()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
                        }

                        Button(
                            onClick = {
                                val shareFile = savedFile ?: inputFile
                                val fileUri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    shareFile
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(Intent.createChooser(shareIntent, "Share Clip"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

// --- 3. SETTINGS SCREEN UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenUI(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            onSettingsChange(settings.copy(customSaveLocationUri = uri))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSettingsChange(settings.copy(themeMode = mode)) }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = { onSettingsChange(settings.copy(themeMode = mode)) }
                            )
                            Text(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Always Record Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Record buffer continuously in background",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.alwaysRecord,
                        onCheckedChange = { isChecked ->
                            onSettingsChange(settings.copy(alwaysRecord = isChecked))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Save Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = settings.customSaveLocationUri?.path ?: "Default Internal App Storage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Folder")
                    }
                }
            }
        }
    }
}
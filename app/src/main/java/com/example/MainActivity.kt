package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import coil.compose.AsyncImage
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var db: SongDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = Room.databaseBuilder(applicationContext, SongDatabase::class.java, "song-db")
            .fallbackToDestructiveMigration()
            .build()

        setContent {
            MyApplicationTheme {
                MainScreen(
                    historyFlow = db.songDao().getAllSongs(),
                    onClearHistory = {
                        lifecycleScope.launch {
                            db.songDao().deleteAllSongs()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(historyFlow: Flow<List<Song>>, onClearHistory: () -> Unit) {
    val context = LocalContext.current
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var isServiceActive by remember { mutableStateOf(AmbientRecognitionService.isServiceRunning) }
    val serviceStatus by AmbientRecognitionService.serviceStatus.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black

    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)

    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()
        
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Ambient", fontWeight = FontWeight.Bold, color = textColor) },
                    actions = {
                        IconButton(onClick = { showPrivacyDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Privacy and Security",
                                tint = textColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Background Music Identifier",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Keep it running in the background. It will notify you when it detects a song.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(40.dp))
                }

                item {
                    if (!permissionsState.allPermissionsGranted) {
                        Button(
                            onClick = { permissionsState.launchMultiplePermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(56.dp).fillMaxWidth()
                        ) {
                            Text("Grant Required Permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    } else {
                        GlassCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                            Text(
                                text = if (isServiceActive) serviceStatus else "Service is Stopped",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = textColor,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            val buttonColor by animateColorAsState(
                                targetValue = if (isServiceActive) Color(0xFFBDBDBD) else Color(0xFF00E5FF),
                                animationSpec = tween(500), label = "buttonColor"
                            )
                            
                            Button(
                                onClick = {
                                    val intent = Intent(context, AmbientRecognitionService::class.java)
                                    if (isServiceActive) {
                                        intent.action = "STOP"
                                        context.startService(intent)
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(intent)
                                        } else {
                                            context.startService(intent)
                                        }
                                    }
                                    isServiceActive = !isServiceActive
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.height(56.dp).fillMaxWidth()
                            ) {
                                AnimatedContent(
                                    targetState = isServiceActive,
                                    label = "buttonText",
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                                    }
                                ) { active ->
                                    Text(
                                        if (active) "Stop Ambient Recognition" else "Start Ambient Recognition",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent History (Last 10)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (history.isNotEmpty()) {
                            TextButton(onClick = { showClearDialog = true }) {
                                Text("Clear", color = Color.Red)
                            }
                        }
                    }
                    
                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text("Clear History") },
                            text = { Text("Are you sure you want to delete all saved tracks?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    onClearHistory()
                                    showClearDialog = false
                                }) {
                                    Text("Yes")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearDialog = false }) {
                                    Text("No")
                                }
                            }
                        )
                    }

                    if (showPrivacyDialog) {
                        AlertDialog(
                            onDismissRequest = { showPrivacyDialog = false },
                            title = { Text("Privacy & Security") },
                            text = { Text("This application requests microphone access to capture short audio samples (10 seconds) of ambient music. This audio is temporarily saved to your device cache and securely sent to a music recognition service. The audio is not stored or shared permanently, and your microphone is only accessed while the service is actively running. All song history is saved locally on your device.") },
                            confirmButton = {
                                TextButton(onClick = { showPrivacyDialog = false }) {
                                    Text("Got it")
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (history.isEmpty()) {
                        Text(
                            text = "No songs identified yet.",
                            color = textColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                if (history.isNotEmpty()) {
                    items(history, key = { it.id }) { song ->
                        val alpha = remember { Animatable(0f) }
                        LaunchedEffect(song.id) {
                            alpha.animateTo(1f, animationSpec = tween(500))
                        }
                        SongItem(
                            song = song,
                            textColor = textColor,
                            modifier = Modifier.animateItem().alpha(alpha.value).padding(bottom = 12.dp)
                        ) {
                            val searchUrl = "https://www.google.com/search?q=${Uri.encode(song.title + " " + song.artist)}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
                            context.startActivity(intent)
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Developed by Himank.J",
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SongItem(song: Song, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val time = formatter.format(Date(song.timestamp))
    
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!song.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = "Album Cover",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = song.artist, color = textColor.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            Text(text = time, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun GlassBackground() {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF0D0D12) else Color(0xFFF0F4F8)
    val blob1 = if (isDark) Color(0x5000E5FF) else Color(0x6000E5FF)
    val blob2 = if (isDark) Color(0x40B388FF) else Color(0x50B388FF)
    val blob3 = if (isDark) Color(0x301DE9B6) else Color(0x401DE9B6)

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopStart)
                .offset(x = (-100).dp, y = (-100).dp)
                .background(Brush.radialGradient(listOf(blob1, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(450.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 150.dp)
                .background(Brush.radialGradient(listOf(blob2, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.CenterStart)
                .offset(x = (-150).dp)
                .background(Brush.radialGradient(listOf(blob3, Color.Transparent)))
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x60FFFFFF)
    val borderColor = if (isDark) Color(0x15FFFFFF) else Color(0x40FFFFFF)

    val shape = RoundedCornerShape(24.dp)
    var finalModifier = modifier
        .clip(shape)
        .background(containerColor)
        .border(1.dp, borderColor, shape)
        
    if (onClick != null) {
        finalModifier = finalModifier.clickable(onClick = onClick)
    }

    Column(
        modifier = finalModifier.padding(20.dp),
        content = content
    )
}

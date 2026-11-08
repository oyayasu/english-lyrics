package com.oyayasu.englishlyrics

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext

private val Ink = Color(0xFF243247)
private val Muted = Color(0xFF728197)
private val Blue = Color(0xFF3F6B91)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EnglishLyricsHome() }
    }
}

private fun launchOverlay(context: Context) {
    val intent = Intent(context, FloatingLyricsService::class.java)
        .setAction(FloatingLyricsService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
}

private fun refreshOverlay(context: Context) {
    if (FloatingLyricsService.isRunning) {
        context.startService(Intent(context, FloatingLyricsService::class.java)
            .setAction(FloatingLyricsService.ACTION_REFRESH))
    }
}

@Composable
private fun EnglishLyricsHome() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var dark by remember { mutableStateOf(Prefs.isDark(context)) }
    var transparency by remember { mutableFloatStateOf(Prefs.opacity(context)) }
    var fontSize by remember { mutableFloatStateOf(Prefs.textSize(context)) }
    var permissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var running by remember { mutableStateOf(FloatingLyricsService.isRunning) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* A permissão é opcional para iniciar o leitor. */ }

    fun activate() {
        launchOverlay(context)
        running = true
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val overlayPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionGranted = Settings.canDrawOverlays(context)
        if (permissionGranted) activate()
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = Settings.canDrawOverlays(context)
                running = FloatingLyricsService.isRunning
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val bgTop = if (dark) Color(0xFF121923) else Color(0xFFF5F8FB)
    val bgBottom = if (dark) Color(0xFF1F2A37) else Color(0xFFE8F0F5)
    val panel = if (dark) Color(0xFF273342) else Color(0xF7FFFFFF)
    val titleColor = if (dark) Color(0xFFF5F7FB) else Ink
    val mutedColor = if (dark) Color(0xFFB4C0D0) else Muted
    val secondaryPanel = if (dark) Color(0xFF334155) else Color(0xFFF1F5F9)

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFA6D5F1)) else lightColorScheme(primary = Blue)) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
                .statusBarsPadding().navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 570.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 23.dp, vertical = 30.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier.size(62.dp).clip(RoundedCornerShape(21.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF477B9F), Color(0xFF294862)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Column {
                        Text("English Lyrics", fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                        Text("Leitor flutuante", fontSize = 14.sp, color = mutedColor)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(28.dp), color = panel, shadowElevation = 9.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(19.dp)) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(210.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.verticalGradient(
                                    if (dark) listOf(Color(0xFF172230), Color(0xFF3B4D5E))
                                    else listOf(Color(0xFFDCEAF3), Color(0xFFC9DAE7))
                                ))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.size(44.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                                    Text("▶", fontSize = 18.sp, color = Ink)
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (dark) Color(0xE82A3645) else Color(0xF5FFFFFF))
                                    .padding(start = 19.dp, end = 19.dp, top = 12.dp, bottom = 17.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 33.dp, height = 4.dp)
                                    .clip(CircleShape).background(mutedColor.copy(alpha = 0.4f)))
                                Text("Título da música", color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Artista", color = mutedColor, fontSize = 12.sp)
                                Text("A letra aparece aqui, sobre o vídeo.", color = titleColor, fontSize = 14.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Outlined.CheckCircleOutline, null,
                                tint = if (running) Color(0xFF418D77) else mutedColor, modifier = Modifier.size(19.dp))
                            Text(
                                when {
                                    running -> "Leitor ativo"
                                    permissionGranted -> "Pronto para ativar"
                                    else -> "Permissão de sobreposição necessária"
                                },
                                fontSize = 13.sp, color = mutedColor
                            )
                        }

                        Button(
                            onClick = {
                                if (permissionGranted) {
                                    activate()
                                } else {
                                    overlayPermission.launch(Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(53.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFF618DAC) else Blue)
                        ) {
                            Icon(Icons.Outlined.Search, null, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (running) "Mostrar lupa" else "Ativar lupa", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }

                        if (running) {
                            TextButton(onClick = {
                                context.stopService(Intent(context, FloatingLyricsService::class.java))
                                running = false
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Encerrar sobreposição", color = mutedColor)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(26.dp), color = panel, shadowElevation = 7.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(21.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Outlined.Tune, null, tint = titleColor, modifier = Modifier.size(22.dp))
                            Text("Aparência", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = titleColor)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tema escuro", fontSize = 15.sp, color = titleColor)
                                Text("Ideal para vídeos à noite", fontSize = 12.sp, color = mutedColor)
                            }
                            Switch(checked = dark, onCheckedChange = {
                                dark = it
                                Prefs.setDark(context, it)
                                refreshOverlay(context)
                            })
                        }
                        HorizontalDivider(color = mutedColor.copy(alpha = 0.16f))
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Opacidade do leitor", fontSize = 15.sp, color = titleColor)
                                Text("${(transparency * 100).toInt()}%", fontSize = 13.sp, color = mutedColor)
                            }
                            Slider(value = transparency, onValueChange = {
                                transparency = it
                                Prefs.setOpacity(context, it)
                            }, onValueChangeFinished = { refreshOverlay(context) }, valueRange = 0.78f..1f)
                        }
                        HorizontalDivider(color = mutedColor.copy(alpha = 0.16f))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tamanho da letra", fontSize = 15.sp, color = titleColor)
                                Text("${fontSize.toInt()} sp", fontSize = 12.sp, color = mutedColor)
                            }
                            TextButton(onClick = {
                                fontSize = (fontSize - 1f).coerceAtLeast(14f)
                                Prefs.setTextSize(context, fontSize)
                                refreshOverlay(context)
                            }) { Text("−", fontSize = 22.sp, color = titleColor) }
                            TextButton(onClick = {
                                fontSize = (fontSize + 1f).coerceAtMost(32f)
                                Prefs.setTextSize(context, fontSize)
                                refreshOverlay(context)
                            }) { Text("+", fontSize = 22.sp, color = titleColor) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Layers, null, tint = mutedColor, modifier = Modifier.size(18.dp))
                    Text(
                        "Toque na lupa sobre qualquer aplicativo para pesquisar. Arraste o painel pela barra superior para ajustar a altura.",
                        fontSize = 13.sp, color = mutedColor, lineHeight = 19.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

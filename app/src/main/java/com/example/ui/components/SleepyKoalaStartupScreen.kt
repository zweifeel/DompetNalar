package com.example.ui.components
import com.example.util.AppStrings

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import kotlinx.coroutines.delay

/**
 * SleepyKoala DompetNalar Fullscreen Startup Loading Screen.
 * 
 * Features:
 * - Truly Fullscreen: Covers entire window before main app Scaffold is composed.
 * - Zero Leakage: No Home, FAB, or bottom navigation displayed during startup.
 * - Proportional Video: 1080x1920 video renders with RESIZE_MODE_ZOOM (crop-to-fill) without squishing.
 * - Muted Playback: Zero sound, background matches Deep Navy theme.
 * - Resilient Error Handling: Automatically falls back to static logo if playback errors occur.
 * - Orientation-Aware: Landscape orientation gracefully falls back to static vector/raster logo.
 * - Clean Resource Release: Releases ExoPlayer via DisposableEffect when dismissed.
 */
@Composable
fun SleepyKoalaStartupScreen(
    modifier: Modifier = Modifier,
    durationMs: Long = 2000L,
    onLoadingComplete: () -> Unit
) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(true) }
    var hasPlaybackError by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Breathing Animation for Static Fallback
    val infiniteTransition = rememberInfiniteTransition(label = "SleepyKoalaBreathTransition")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathScale"
    )

    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraPulse"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatOffset"
    )

    // Startup Timer (Non-blocking coroutine)
    LaunchedEffect(Unit) {
        delay(durationMs)
        isVisible = false
        delay(300) // Allow smooth fadeout
        onLoadingComplete()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier.testTag("sleepykoala_startup_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF051329)), // SleepyKoala Deep Navy
            contentAlignment = Alignment.Center
        ) {
            if (isLandscape || hasPlaybackError) {
                // Static Fallback Screen for Landscape or Player Errors
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Turquoise Aura Glow
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .scale(breathScale * 1.05f)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF59E6E2).copy(alpha = 0.22f * auraPulse),
                                        Color(0xFF38B6FF).copy(alpha = 0.08f * auraPulse),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.dompetnalar_startup_logo),
                            contentDescription = "SleepyKoala Logo Fallback",
                            modifier = Modifier
                                .size(240.dp)
                                .scale(breathScale)
                                .offset(y = floatOffset.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "DOMPETNALAR",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 3.sp
                        )

                        Text(
                            text = "Cerdas Finansial Tanpa Beban",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF59E6E2),
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        LinearProgressIndicator(
                            modifier = Modifier
                                .width(160.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF59E6E2),
                            trackColor = Color(0xFF0F3260)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Menyiapkan DompetNalar...",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else {
                // Portrait: Truly Fullscreen ExoPlayer Video Screen (Proportionally Scaled)
                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        val mediaItem = MediaItem.fromUri(
                            "android.resource://${context.packageName}/${R.raw.loading_screen_sleepykoala}"
                        )
                        setMediaItem(mediaItem)
                        volume = 0f
                        addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                hasPlaybackError = true
                            }
                        })
                        prepare()
                        playWhenReady = true
                    }
                }

                DisposableEffect(player) {
                    onDispose {
                        player.stop()
                        player.release()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setBackgroundColor(android.graphics.Color.parseColor("#051329"))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

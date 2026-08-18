package com.example.ui.components

import android.net.Uri
import java.io.File

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimatedWord
import com.example.data.model.Clip
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkCanvas
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusDarkSurfaceHighlight
import com.example.ui.theme.OpusDarkSurfaceVariant
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusGlassDark
import com.example.ui.theme.OpusGold
import com.example.ui.theme.OpusHotPink
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import com.example.ui.theme.OpusVioletGlow
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
private fun RealClipPlayer(
    clip: Clip,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = androidx.compose.runtime.remember(clip.exportPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(File(clip.exportPath))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("real_video_player_container"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "المقطع الحقيقي الناتج من Gemini + Media3",
            color = OpusElectricCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun NoRealExportState(
    clip: Clip,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OpusDarkSurface)
            .border(1.dp, OpusHotPink.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "لا توجد معاينة MP4 حقيقية لهذا المقطع",
            color = OpusHotPink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "المدة المستخرجة: ${clip.durationSec} ثانية. أعد التصدير بعد اكتمال المعالجة.",
            color = OpusTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun VideoSimPlayer(
    clip: Clip,
    selectedCaptionTheme: String,
    layoutType: String,
    onLayoutChange: (String) -> Unit,
    captionPosition: String = "Bottom (Safe Zone)",
    fontSizeSp: Int = 14,
    showAutoEmojis: Boolean = true,
    isUppercase: Boolean = false,
    externalSeekSec: Float? = null,
    onSeekComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (clip.exportPath.isNotBlank() && File(clip.exportPath).exists()) {
        RealClipPlayer(clip = clip, modifier = modifier)
        return
    }

    NoRealExportState(clip = clip, modifier = modifier)
    return

    /* Legacy preview intentionally disabled: only exported MP4 files may be shown. */
    var isPlaying by remember { mutableStateOf(true) }
    var currentPlaybackSec by remember { mutableFloatStateOf(0f) }
    val durationSec = maxOf(15, clip.durationSec).toFloat()

    LaunchedEffect(externalSeekSec) {
        if (externalSeekSec != null) {
            currentPlaybackSec = externalSeekSec.coerceIn(0f, durationSec)
            onSeekComplete?.invoke()
        }
    }

    // Moshi deserializer for word-level captions
    val moshi = remember { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    val wordsList = remember(clip.animatedCaptionsJson) {
        try {
            val adapter = moshi.adapter<List<AnimatedWord>>(
                Types.newParameterizedType(List::class.java, AnimatedWord::class.java)
            )
            adapter.fromJson(clip.animatedCaptionsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Playback loop timer
    LaunchedEffect(isPlaying, clip.id) {
        while (isPlaying) {
            delay(100)
            currentPlaybackSec += 0.1f
            if (currentPlaybackSec >= durationSec) {
                currentPlaybackSec = 0f
            }
        }
    }

    // Audio waveform animation
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "w1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "w2"
    )
    val waveScale3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "w3"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_player_container"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Layout Mode Switcher Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Dynamic Reframe",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OpusTextSecondary
            )

            Row {
                listOf("9:16 Full Screen", "Split Screen", "1:1 Square").forEach { mode ->
                    val isSel = layoutType == mode
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) OpusPrimaryViolet else OpusDarkSurfaceVariant)
                            .border(1.dp, if (isSel) OpusVioletGlow else OpusBorder, RoundedCornerShape(6.dp))
                            .clickable { onLayoutChange(mode) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("layout_btn_${mode.take(4)}")
                    ) {
                        Text(
                            text = mode.substringBefore(" "),
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) OpusTextPrimary else OpusTextSecondary
                        )
                    }
                }
            }
        }

        // 9:16 Vertical Video Screen Simulation
        val aspect = when (layoutType) {
            "1:1 Square" -> 1f
            "16:9 Landscape" -> 16f / 9f
            else -> 9f / 16f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(if (layoutType == "1:1 Square") 0.85f else 0.72f)
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF161233),
                            Color(0xFF0D0B18),
                            Color(0xFF1B1438)
                        )
                    )
                )
                .border(2.dp, OpusBorder, RoundedCornerShape(16.dp))
                .clickable { isPlaying = !isPlaying },
            contentAlignment = Alignment.Center
        ) {
            // Simulated Video Scene: Speaker Frames & Waveforms
            if (layoutType == "Split Screen") {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Speaker 1 (Host)
                    SpeakerFrame(
                        speakerName = "Host / Interviewer",
                        avatarColor = OpusPrimaryViolet,
                        isActive = (currentPlaybackSec.toInt() % 8) < 4,
                        isPlaying = isPlaying,
                        waveScale1 = waveScale1,
                        waveScale2 = waveScale2
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(OpusVioletGlow.copy(alpha = 0.3f))
                    )

                    // Speaker 2 (Guest)
                    SpeakerFrame(
                        speakerName = "Guest Expert",
                        avatarColor = OpusElectricCyan,
                        isActive = (currentPlaybackSec.toInt() % 8) >= 4,
                        isPlaying = isPlaying,
                        waveScale1 = waveScale2,
                        waveScale2 = waveScale3
                    )
                }
            } else {
                // Single Focused Speaker Scene with Animated Ambient Glow
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient pulsing halo behind avatar
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        OpusVioletGlow.copy(alpha = if (isPlaying) 0.35f else 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(OpusPrimaryViolet)
                                .border(2.dp, OpusElectricCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker Voice",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "AI Auto-Framed Speaker",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OpusElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        // Live audio equalizer bar animation
                        if (isPlaying) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(modifier = Modifier.width(3.dp).height((16 * waveScale1).dp).background(OpusViralEmerald, RoundedCornerShape(2.dp)))
                                Box(modifier = Modifier.width(3.dp).height((24 * waveScale2).dp).background(OpusElectricCyan, RoundedCornerShape(2.dp)))
                                Box(modifier = Modifier.width(3.dp).height((18 * waveScale3).dp).background(OpusVioletGlow, RoundedCornerShape(2.dp)))
                                Box(modifier = Modifier.width(3.dp).height((22 * waveScale1).dp).background(OpusHotPink, RoundedCornerShape(2.dp)))
                                Box(modifier = Modifier.width(3.dp).height((12 * waveScale2).dp).background(OpusGold, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }

            // Top Video Overlay: Virality Badge & Format Badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "9:16 Shorts",
                        color = OpusElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusViralEmerald.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${clip.viralityScore} Score",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Subtitle Alignment based on user selection
            val subtitleAlignment = when (captionPosition) {
                "Top" -> Alignment.TopCenter
                "Center" -> Alignment.Center
                else -> Alignment.BottomCenter
            }
            val subtitlePadding = when (captionPosition) {
                "Top" -> Modifier.padding(horizontal = 14.dp, vertical = 32.dp)
                "Center" -> Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                else -> Modifier.padding(horizontal = 14.dp, vertical = 24.dp)
            }

            // Central Animated Dynamic Karaoke Captions Overlay
            Box(
                modifier = Modifier
                    .align(subtitleAlignment)
                    .then(subtitlePadding)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xB3090A0F))
                    .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                RenderAnimatedCaptions(
                    words = wordsList,
                    currentSec = currentPlaybackSec,
                    theme = selectedCaptionTheme,
                    fontSizeSp = fontSizeSp,
                    showAutoEmojis = showAutoEmojis,
                    isUppercase = isUppercase
                )
            }

            // Play/Pause Floating Action Overlay on Click
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(OpusGlassDark)
                        .border(2.dp, OpusElectricCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Playback Controls & Timeline Scrubber
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(36.dp).testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = OpusElectricCyan
                )
            }

            IconButton(
                onClick = { currentPlaybackSec = 0f },
                modifier = Modifier.size(36.dp).testTag("restart_clip_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = "Restart",
                    tint = OpusTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Slider(
                value = currentPlaybackSec,
                onValueChange = { currentPlaybackSec = it },
                valueRange = 0f..durationSec,
                modifier = Modifier
                    .weight(1f)
                    .testTag("timeline_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = OpusElectricCyan,
                    activeTrackColor = OpusPrimaryViolet,
                    inactiveTrackColor = OpusDarkSurfaceHighlight
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${currentPlaybackSec.toInt()}s / ${durationSec.toInt()}s",
                fontSize = 11.sp,
                color = OpusTextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SpeakerFrame(
    speakerName: String,
    avatarColor: Color,
    isActive: Boolean,
    isPlaying: Boolean,
    waveScale1: Float,
    waveScale2: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = if (isActive) 1f else 0.4f))
                .border(
                    width = if (isActive) 2.5.dp else 1.dp,
                    color = if (isActive) OpusElectricCyan else OpusBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = speakerName,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = speakerName,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) OpusTextPrimary else OpusTextSecondary
        )

        if (isActive && isPlaying) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(modifier = Modifier.width(2.dp).height((8 * waveScale1).dp).background(OpusElectricCyan))
                Box(modifier = Modifier.width(2.dp).height((12 * waveScale2).dp).background(OpusViralEmerald))
                Box(modifier = Modifier.width(2.dp).height((8 * waveScale1).dp).background(OpusVioletGlow))
            }
        }
    }
}

@Composable
private fun RenderAnimatedCaptions(
    words: List<AnimatedWord>,
    currentSec: Float,
    theme: String,
    fontSizeSp: Int = 14,
    showAutoEmojis: Boolean = true,
    isUppercase: Boolean = false
) {
    if (words.isEmpty()) {
        Text(
            text = "AI Dynamic Captions Generating...",
            color = OpusTextPrimary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    // Determine current word index
    val activeIndex = words.indexOfFirst { currentSec in it.startSec..it.endSec }.let {
        if (it == -1) (currentSec / (words.lastOrNull()?.endSec ?: 1f) * words.size).toInt().coerceIn(0, words.size - 1)
        else it
    }

    // Window of words around active
    val startIdx = maxOf(0, activeIndex - 3)
    val endIdx = minOf(words.size, activeIndex + 4)
    val visibleWords = words.subList(startIdx, endIdx)

    val annotatedString = buildAnnotatedString {
        visibleWords.forEach { wordItem ->
            val isActive = (wordItem.startSec <= currentSec && currentSec <= wordItem.endSec) ||
                    (words.indexOf(wordItem) == activeIndex)

            val color = when (theme) {
                "Opus Neon" -> if (isActive) OpusElectricCyan else if (wordItem.isHighlight) OpusVioletGlow else Color.White
                "MrBeast Yellow" -> if (isActive) OpusGold else if (wordItem.isHighlight) OpusGold else Color.White
                "Ali Abdaal" -> if (isActive) OpusHotPink else if (wordItem.isHighlight) OpusElectricCyan else Color(0xFFE2E8F0)
                "Cyber Green" -> if (isActive) OpusViralEmerald else if (wordItem.isHighlight) OpusViralEmerald else Color.White
                "Hormozi Bold" -> if (isActive) OpusVioletGlow else if (wordItem.isHighlight) OpusGold else Color.White
                else -> if (isActive) OpusVioletGlow else Color.White
            }

            val fontWeight = if (isActive || wordItem.isHighlight) FontWeight.Black else FontWeight.Bold
            val background = if (isActive) OpusPrimaryViolet.copy(alpha = 0.45f) else Color.Transparent
            val displayWord = if (isUppercase) wordItem.word.uppercase() else wordItem.word

            withStyle(
                SpanStyle(
                    color = color,
                    fontWeight = fontWeight,
                    background = background,
                    fontSize = if (isActive) (fontSizeSp + 2).sp else fontSizeSp.sp
                )
            ) {
                append("$displayWord ")
                if (showAutoEmojis && wordItem.emoji.isNotBlank() && (isActive || wordItem.isHighlight)) {
                    append("${wordItem.emoji} ")
                }
            }
        }
    }

    Text(
        text = annotatedString,
        textAlign = TextAlign.Center,
        lineHeight = (fontSizeSp + 6).sp,
        modifier = Modifier.fillMaxWidth()
    )
}

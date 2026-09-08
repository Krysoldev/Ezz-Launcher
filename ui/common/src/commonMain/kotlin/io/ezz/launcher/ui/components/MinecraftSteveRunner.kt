package io.ezz.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.runtime.LaunchProgressState
import io.ezz.launcher.core.model.runtime.computeDisplayInterpolationDuration
import io.ezz.launcher.core.model.runtime.computeDisplayPercentage

// In-memory cache of display progress per operationId to prevent resetting when navigating away and returning
internal val displayProgressHistory = mutableMapOf<String, Float>()

/**
 * Authentic Minecraft Chibi Steve Walking Character.
 *
 * Implements the exact 6-frame walking cycle inspired by the reference:
 * https://custom-progress-bar.com/en/collection/minecraft/mnc-chibi-steve-walk
 *
 * Features:
 * - Alternating swinging arms and legs
 * - Subtle body bounce and head movement
 * - 100% offline bundled assets (zero runtime network dependency)
 * - Sharp pixel-art scaling across any DPI scaling (100%, 125%, 150%, 200%)
 */
@Composable
fun MinecraftChibiSteveCharacter(
    isWalking: Boolean = true,
    isVictory: Boolean = false,
    modifier: Modifier = Modifier,
    characterHeight: Dp = 40.dp
) {
    // 6 authentic walk-cycle frames bundled locally in resources
    val frame0 = painterResource("chibi_steve_0.png")
    val frame1 = painterResource("chibi_steve_1.png")
    val frame2 = painterResource("chibi_steve_2.png")
    val frame3 = painterResource("chibi_steve_3.png")
    val frame4 = painterResource("chibi_steve_4.png")
    val frame5 = painterResource("chibi_steve_5.png")

    val frames = remember(frame0, frame1, frame2, frame3, frame4, frame5) {
        listOf(frame0, frame1, frame2, frame3, frame4, frame5)
    }

    // Authentic walk-cycle animation loop: 700ms total
    // 0..100ms: frame 0 (neutral passing step)
    // 100..200ms: frame 1 (subtle forward lean / head bob)
    // 200..300ms: frame 2 (right leg forward, arm swing)
    // 300..500ms: frame 3 (center return, 200ms duration)
    // 500..600ms: frame 4 (neutral step)
    // 600..700ms: frame 5 (left leg forward, alternate arm swing)
    val infiniteTransition = rememberInfiniteTransition(label = "chibi_steve_walk_cycle")
    val cycleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 700f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "walk_cycle_time"
    )

    // Gentle celebratory bounce when Minecraft starts successfully
    val victoryBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "victory_bounce"
    )

    val currentFrame = when {
        isVictory -> frame3
        !isWalking -> frame0
        else -> {
            val t = cycleTime.toInt() % 700
            when {
                t < 100 -> frames[0]
                t < 200 -> frames[1]
                t < 300 -> frames[2]
                t < 500 -> frames[3]
                t < 600 -> frames[4]
                else -> frames[5]
            }
        }
    }

    val offsetY = if (isVictory) victoryBounce.dp else 0.dp

    Box(
        modifier = modifier
            .size(characterHeight)
            .offset(y = offsetY),
        contentAlignment = Alignment.BottomCenter
    ) {
        Image(
            painter = currentFrame,
            contentDescription = "Minecraft Chibi Steve Walking",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Backward-compatible wrapper for the character runner.
 */
@Composable
fun MinecraftSteveCharacter(
    isRunning: Boolean = true,
    isVictory: Boolean = false,
    modifier: Modifier = Modifier,
    characterHeight: Dp = 40.dp
) {
    MinecraftChibiSteveCharacter(
        isWalking = isRunning,
        isVictory = isVictory,
        modifier = modifier,
        characterHeight = characterHeight
    )
}

/**
 * Authoritative Minecraft-style launch progress track and Chibi Steve walking component.
 *
 * Directly connects to the real-time launch progress pipeline:
 * - Steve's position accurately mirrors the launch percentage (0% to 100%)
 * - Authentic Minecraft terrain gradient fill track from reference
 * - Indeterminate subtle patrolling when operation does not expose byte counts
 * - Smooth spring interpolation prevents jitter or stutter during rapid updates
 */
@Composable
fun LaunchProgressTrack(
    progressState: LaunchProgressState,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    characterHeight: Dp = 40.dp
) {
    val isFinished = progressState.percentage >= 100 || (progressState.progress >= 1.0f && progressState.displayProgress >= 1.0f)
    val hasError = progressState.error != null

    // Indeterminate subtle travel wave when stage cannot expose measurable bytes
    val infiniteTransition = rememberInfiniteTransition(label = "indeterminate_runner")
    val indeterminateTravel by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "indeterminate_travel"
    )

    // Display progress is authoritatively and smoothly managed by LaunchEngine & AppViewModel at 60 FPS
    val effectiveProgress = if (progressState.isIndeterminate && !isFinished) {
        indeterminateTravel
    } else {
        progressState.displayProgress.coerceIn(0f, 1f)
    }

    // Crisp integer percentage (0–100) directly synchronized with Chibi Steve and track
    val displayPercentage = progressState.percentage.coerceIn(0, 100)

    // True completion occurs when BOTH the real operation finished AND display progress has arrived at 100%
    val isDisplayFinished = isFinished && displayPercentage >= 100

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131122))
            .border(
                1.dp,
                when {
                    hasError -> Color(0xFFEF4444).copy(alpha = 0.6f)
                    isDisplayFinished -> Color(0xFF10B981).copy(alpha = 0.6f)
                    else -> Color(0xFF2F2648)
                },
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Status Dot + Stage Name + Detail + Integer Percentage + Cancel Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Status Beacon Dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hasError -> Color(0xFFEF4444)
                                    isDisplayFinished -> Color(0xFF10B981)
                                    else -> Color(0xFFA78BFA)
                                }
                            )
                            .shadow(
                                elevation = 6.dp,
                                shape = CircleShape,
                                ambientColor = if (isDisplayFinished) Color(0xFF10B981) else Color(0xFFA78BFA)
                            )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isDisplayFinished) "MINECRAFT STARTED" else progressState.stage.uppercase(),
                                color = if (hasError) Color(0xFFFCA5A5) else if (isDisplayFinished) Color(0xFF10B981) else Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                            if (isDisplayFinished) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isDisplayFinished) "Ready! Have fun playing." else progressState.status.ifBlank { "Processing..." },
                            color = if (isDisplayFinished) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Crisp integer percentage (0–100) synchronized with Steve and track
                    Text(
                        text = if (progressState.isIndeterminate && !isDisplayFinished) "" else "$displayPercentage%",
                        color = when {
                            hasError -> Color(0xFFEF4444)
                            isDisplayFinished -> Color(0xFF10B981)
                            else -> Color(0xFFA78BFA)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )

                    // Optional Cancel Button
                    if (onCancel != null && progressState.isCancellable && !isFinished && !hasError) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF221A36))
                                .border(1.dp, Color(0xFF3B2E5C), RoundedCornerShape(6.dp))
                                .clickable(onClick = onCancel),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Launch",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Chibi Steve Runner & Progress Track Area
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(characterHeight + 8.dp)
            ) {
                val totalWidth = maxWidth
                val runnerWidth = characterHeight
                val finishMargin = 28.dp
                val maxTravel = (totalWidth - runnerWidth - finishMargin).coerceAtLeast(0.dp)

                val runnerX = maxTravel * effectiveProgress.coerceIn(0f, 1f)

                // 1. Bottom Track Base
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(3.5.dp))
                        .background(Color(0xFF1B1629))
                        .border(1.dp, Color(0xFF2E2442), RoundedCornerShape(3.5.dp))
                ) {
                    // Filled Track Progress Line
                    val fillFraction = effectiveProgress.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .height(7.dp)
                            .clip(RoundedCornerShape(3.5.dp))
                            .background(
                                when {
                                    hasError -> Brush.horizontalGradient(
                                        listOf(Color(0xFFDC2626), Color(0xFFEF4444))
                                    )
                                    isDisplayFinished -> Brush.horizontalGradient(
                                        listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399))
                                    )
                                    else -> Brush.horizontalGradient(
                                        // Authentic Minecraft grass-dirt terrain gradient directly from reference mnc-chibi-steve-walk
                                        listOf(
                                            Color(0xFF477A1E),
                                            Color(0xFF70B237),
                                            Color(0xFF8FCA5C),
                                            Color(0xFF61371F),
                                            Color(0xFF854F2B),
                                            Color(0xFFC28340)
                                        )
                                    )
                                }
                            )
                    )
                }

                // 2. Finish Line Marker (right end)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = (-2).dp)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsScore,
                        contentDescription = "Finish Line",
                        tint = if (isDisplayFinished) Color(0xFF10B981) else Color(0xFF64748B),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 3. Minecraft Chibi Steve Character (walking scrubber on top of the track line)
                Box(
                    modifier = Modifier
                        .offset(x = runnerX, y = 0.dp)
                        .align(Alignment.TopStart)
                ) {
                    MinecraftChibiSteveCharacter(
                        isWalking = !hasError && (!isDisplayFinished || progressState.isIndeterminate),
                        isVictory = isDisplayFinished,
                        characterHeight = characterHeight
                    )
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppStrings
import com.example.R
import com.example.ui.theme.FinSmartPrimary

/**
 * Modern FinSmart Brand Logo Component
 * Incorporates glowing sapphire-cyan-emerald gradients, wallet vector, AI sparkle, and trend graph.
 * Also supports image fallback to res/drawable/finsmart_logo_1790471910026.
 */
@Composable
fun FinSmartLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    showGlow: Boolean = true,
    useImageLogoIfAvailable: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val gradientColors = listOf(
        Color(0xFF1E3A8A), // Deep Royal Navy
        Color(0xFF2563EB), // FinSmart Electric Blue
        Color(0xFF06B6D4), // Bright Cyan
        Color(0xFF10B981)  // Growth Emerald
    )

    Box(
        modifier = modifier
            .size(size)
            .testTag("finsmart_logo_component"),
        contentAlignment = Alignment.Center
    ) {
        if (showGlow) {
            // Ambient glowing backlight
            Box(
                modifier = Modifier
                    .size(size * 1.15f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF06B6D4).copy(alpha = glowAlpha * 0.45f),
                                Color(0xFF2563EB).copy(alpha = glowAlpha * 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        if (useImageLogoIfAvailable) {
            // Image Logo from drawable resources
            Box(
                modifier = Modifier
                    .size(size)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(size * 0.28f))
                    .clip(RoundedCornerShape(size * 0.28f))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF34D399))),
                        shape = RoundedCornerShape(size * 0.28f)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.finsmart_logo_1790471910026),
                    contentDescription = "DompetNalar Logo",
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            // Modern Vector/Canvas Graphic FinSmart Logo
            Box(
                modifier = Modifier
                    .size(size)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(size * 0.28f), spotColor = Color(0xFF2563EB))
                    .clip(RoundedCornerShape(size * 0.28f))
                    .background(
                        brush = Brush.linearGradient(
                            colors = gradientColors,
                            start = Offset(0f, 0f),
                            end = Offset.Infinite
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.8f),
                                Color(0xFF93C5FD).copy(alpha = 0.4f),
                                Color(0xFF6EE7B7).copy(alpha = 0.6f)
                            )
                        ),
                        shape = RoundedCornerShape(size * 0.28f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background subtle financial grid / chart line pattern
                Box(
                    modifier = Modifier
                        .size(size)
                        .drawBehind {
                            val strokeWidth = 1.2.dp.toPx()
                            val lineAlpha = 0.25f
                            // Diagonal decorative lines
                            drawLine(
                                color = Color.White.copy(alpha = lineAlpha),
                                start = Offset(0f, this.size.height * 0.75f),
                                end = Offset(this.size.width * 0.75f, 0f),
                                strokeWidth = strokeWidth
                            )
                        }
                )

                // Central Icon Group (Wallet + Trending Growth + AI Sparkle)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(size * 0.72f)
                ) {
                    // Smart Wallet Base Icon
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(size * 0.52f)
                            .offset(y = (size * 0.03f))
                    )

                    // Glowing AI Sparkle Indicator on top-right corner
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFDE047), // Radiant Gold/Yellow
                        modifier = Modifier
                            .size(size * 0.28f)
                            .align(Alignment.TopEnd)
                            .offset(x = (size * 0.04f), y = (-size * 0.04f))
                    )

                    // Small upward trend arrow inside
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF67E8F9), // Aqua Cyan
                        modifier = Modifier
                            .size(size * 0.22f)
                            .align(Alignment.BottomStart)
                            .offset(x = (-size * 0.02f), y = (size * 0.02f))
                    )
                }
            }
        }
    }
}

/**
 * Full Brand Header with Logo, Title, and Subtitle
 */
@Composable
fun FinSmartBrandHeader(
    modifier: Modifier = Modifier,
    title: String = "DompetNalar",
    subtitle: String = AppStrings.t("Kelola finansial cerdas dengan AI & Analitik", "Smart financial management with AI & Analytics")
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FinSmartLogo(
            size = 64.dp,
            showGlow = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF06B6D4))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "PRO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

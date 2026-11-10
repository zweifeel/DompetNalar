package com.example.ui.components
import com.example.util.AppStrings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppIconEdition
import com.example.data.model.UserGamificationState
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent
import com.example.util.GamificationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamificationHubDialog(
    state: UserGamificationState,
    onDismiss: () -> Unit,
    onSelectAppIcon: (AppIconEdition) -> Unit,
    onToggleNoSpendChallenge: (Boolean) -> Unit,
    onUpdateCustomBackground: (String?, Float) -> Unit = { _, _ -> },
    onTriggerPersonalityTest: () -> Unit,
    canClaimStreakReward: Boolean = false,
    streakRewardRemainingHours: Long = 0L,
    onClaimStreakReward: () -> Unit = {},
    canClaim30DayStreakReward: Boolean = false,
    streak30RewardRemainingHours: Long = 0L,
    onClaim30DayStreakReward: () -> Unit = {},
    isWallpaperUnlocked: Boolean = false
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val badges = GamificationManager.getAllBadges(state.dailyStreak, state.level, state.unlockedBadgeIds)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("gamification_hub_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DOMPETNALAR GAMIFICATION & REWARDS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFD97706)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = com.example.util.AppStrings.t("Tutup", "Close"))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Level & XP Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "L${state.level}",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Level ${state.level} / 50",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${state.currentXp} XP / ${state.xpForNextLevel} XP",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Streak Badge
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🔥 ${state.dailyStreak} Hari", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFB45309))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val progress = (state.currentXp.toFloat() / state.xpForNextLevel.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = GoldAccent,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 7-Day Streak Reward Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = BorderStroke(1.5.dp, Color(0xFFF59E0B))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎁 Reward Streak 7 Hari:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "5 Jam Smart Member Pass + 3x Action Pass Credits",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309)
                        )
                    }

                    val buttonText = when {
                        state.dailyStreak < 7 -> "Streak < 7"
                        !canClaimStreakReward -> if (streakRewardRemainingHours > 0) "Klaim (${streakRewardRemainingHours}j)" else "Sudah Diklaim"
                        else -> "Klaim"
                    }

                    Button(
                        onClick = {
                            if (canClaimStreakReward) {
                                onClaimStreakReward()
                            } else {
                                Toast.makeText(context, "Reward streak 7 hari belum dapat diklaim saat ini.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = canClaimStreakReward,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            disabledContainerColor = Color(0xFFD97706).copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 30-Day Streak Reward Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = BorderStroke(1.5.dp, Color(0xFFD97706))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "👑 Reward Streak 30 Hari:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Akses Penuh Smart Member Gratis Selama 24 Jam ⚡",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309)
                        )
                    }

                    val button30Text = when {
                        state.dailyStreak < 30 -> "Streak < 30"
                        !canClaim30DayStreakReward -> if (streak30RewardRemainingHours > 0) "Klaim (${streak30RewardRemainingHours}j)" else "Sudah Diklaim"
                        else -> "Klaim"
                    }

                    Button(
                        onClick = {
                            if (canClaim30DayStreakReward) {
                                onClaim30DayStreakReward()
                            } else {
                                Toast.makeText(context, "Reward streak 30 hari belum dapat diklaim saat ini.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = canClaim30DayStreakReward,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            disabledContainerColor = Color(0xFFD97706).copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(button30Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Financial Personality Badge Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTriggerPersonalityTest() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profil Finansial Anda:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.personality.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = onTriggerPersonalityTest,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Tes Ulang", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Badges Section
            Text(
                text = "Lencana Digital (Badges):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(badges) { badge ->
                    Card(
                        modifier = Modifier
                            .width(130.dp)
                            .height(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (badge.isUnlocked) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = if (badge.isUnlocked) BorderStroke(1.5.dp, Color(0xFFD97706)) else null
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (badge.isUnlocked) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(28.dp))
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = badge.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (badge.isUnlocked) Color(0xFF92400E) else Color.Gray,
                                maxLines = 1
                            )
                            Text(
                                text = if (badge.isUnlocked) "Terbuka" else "Terkunci",
                                fontSize = 9.sp,
                                color = if (badge.isUnlocked) Color(0xFFB45309) else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic App Icons Section (Streak 14 Days)
            Text(
                text = "Dynamic App Icons (Streak 14+ Hari):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIconEdition.values().forEach { icon ->
                    val isUnlocked = state.dailyStreak >= icon.requiredStreak || !icon.isLockedByDefault
                    val isSelected = state.activeIconEdition == icon

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = isUnlocked) {
                                onSelectAppIcon(icon)
                            },
                        shape = RoundedCornerShape(14.dp),
                        border = if (isSelected) BorderStroke(2.dp, FinSmartPrimary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUnlocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isUnlocked) {
                                    Icon(Icons.Default.Diamond, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = icon.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else Color.Gray
                                    )
                                    if (!isUnlocked) {
                                        Text(text = "Butuh Streak ${icon.requiredStreak} Hari", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }

                            if (isSelected) {
                                Surface(
                                    color = FinSmartPrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(18.dp))

            // No-Spend Day Challenge Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "No-Spend Day Challenge 🦸", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            text = AppStrings.t("Tantangan 24 jam tanpa transaksi keluar. Dapatkan Streak Multiplier 2x!", "24-hour challenge without spending. Earn a 2x Streak Multiplier!"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.isNoSpendChallengeActive,
                        onCheckedChange = onToggleNoSpendChallenge
                    )
                }
            }
        }
    }
}

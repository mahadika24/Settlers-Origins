package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.GameState
import com.example.game.model.BuildingType
import com.example.game.model.GameSpeed
import com.example.game.model.SimulationEvent

@Composable
fun TopHudBar(
    gameState: GameState,
    onSetSpeed: (GameSpeed) -> Unit,
    modifier: Modifier = Modifier
) {
    val time = gameState.timeSystem
    val inventory = gameState.inventory
    val totalCapacity = gameState.buildings.filter { it.isConstructed }.sumOf { it.type.housingCapacity }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("top_hud_bar")
    ) {
        // Upper row: Time & Speed control
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xEEFFFFFF),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day and Clock Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F5E9), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Hari ${time.dayNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF2E6B38)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${time.phase.icon} ${time.clockString} • ${time.phase.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF37474F)
                    )
                }

                // Speed Selector Pill (Outlanders style: ⏸️, 1x, 2x, 3.5x)
                Row(
                    modifier = Modifier
                        .background(Color(0xFFF0F4F8), RoundedCornerShape(14.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val speeds = listOf(
                        Pair(GameSpeed.PAUSED, "⏸"),
                        Pair(GameSpeed.NORMAL, "1×"),
                        Pair(GameSpeed.FAST, "2×"),
                        Pair(GameSpeed.TURBO, "3.5×")
                    )

                    for ((speed, label) in speeds) {
                        val isSelected = time.speed == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF2E6B38) else Color.Transparent)
                                .clickable { onSetSpeed(speed) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("speed_${label.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF455A64)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Lower row: Resource Counters
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xD9FFFFFF),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResourcePill(
                    icon = "👥",
                    label = "Warga",
                    value = "${gameState.villagers.size}/$totalCapacity",
                    isWarning = gameState.villagers.size >= totalCapacity
                )
                ResourcePill(
                    icon = "🍞",
                    label = "Pangan",
                    value = "${inventory.food}/${inventory.maxFood}",
                    isWarning = inventory.food < 10
                )
                ResourcePill(
                    icon = "🪵",
                    label = "Kayu",
                    value = "${inventory.wood}/${inventory.maxWood}",
                    isWarning = inventory.wood < 5
                )
                ResourcePill(
                    icon = "🪨",
                    label = "Batu",
                    value = "${inventory.stone}/${inventory.maxStone}",
                    isWarning = false
                )
                val happyEmoji = when {
                    gameState.averageHappiness >= 75f -> "😊"
                    gameState.averageHappiness >= 45f -> "😐"
                    else -> "😢"
                }
                ResourcePill(
                    icon = happyEmoji,
                    label = "Bahagia",
                    value = "${gameState.averageHappiness.toInt()}%",
                    isWarning = gameState.averageHappiness < 40f
                )
            }
        }

        // Recent Event Alert Toast (if any)
        val latestEvent = gameState.events.firstOrNull()
        if (latestEvent != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xCC263238),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = latestEvent.iconEmoji, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = latestEvent.text,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourcePill(
    icon: String,
    label: String,
    value: String,
    isWarning: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWarning) Color(0xFFD32F2F) else Color(0xFF2C3E50)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = Color(0xFF7F8C8D)
            )
        }
    }
}

/**
 * Bottom Action Dock
 */
@Composable
fun BottomActionDock(
    onOpenBuildCatalog: () -> Unit,
    onOpenJobAssignment: () -> Unit,
    onOpenVillagersRoster: () -> Unit,
    onOpenCommunityGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("bottom_action_dock"),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xEEFFFFFF),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockButton(
                icon = "🏗️",
                title = "Bangun",
                testTag = "btn_dock_build",
                onClick = onOpenBuildCatalog
            )
            DockButton(
                icon = "📋",
                title = "Pekerjaan",
                testTag = "btn_dock_jobs",
                onClick = onOpenJobAssignment
            )
            DockButton(
                icon = "👥",
                title = "Warga",
                testTag = "btn_dock_roster",
                onClick = onOpenVillagersRoster
            )
            DockButton(
                icon = "📜",
                title = "Misi Desa",
                testTag = "btn_dock_goals",
                onClick = onOpenCommunityGoals
            )
        }
    }
}

@Composable
private fun DockButton(
    icon: String,
    title: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFFF7F4EC), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2C3E50)
        )
    }
}

/**
 * Banner shown during building placement mode
 */
@Composable
fun PendingBuildBanner(
    buildingType: BuildingType,
    selectedTile: Pair<Int, Int>?,
    canPlace: Boolean,
    onConfirmPlace: () -> Unit,
    onCancelPlace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("pending_build_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${buildingType.iconEmoji} Menempatkan: ${buildingType.title}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF2C3E50)
                )
                Text(
                    text = if (selectedTile != null) {
                        if (canPlace) "Lokasi sesuai! Tekan Centang untuk membangun."
                        else "Lokasi terhalang atau tidak sesuai!"
                    } else "Ketuk petak tanah di peta untuk menentukan posisi.",
                    fontSize = 11.sp,
                    color = if (canPlace) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCancelPlace,
                    modifier = Modifier.testTag("btn_cancel_place")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color(0xFF757575))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onConfirmPlace,
                    enabled = canPlace && selectedTile != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6B38)),
                    modifier = Modifier.testTag("btn_confirm_place")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Dirikan")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Dirikan", fontSize = 12.sp)
                }
            }
        }
    }
}

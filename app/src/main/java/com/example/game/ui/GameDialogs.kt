package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.CommunityGoal
import com.example.game.model.JobType
import com.example.game.model.VillageInventory
import com.example.game.model.Villager

/**
 * Building Catalog Menu
 */
@Composable
fun BuildCatalogSheet(
    inventory: VillageInventory,
    onSelectBuilding: (BuildingType) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("build_catalog_sheet"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🏗️ Katalog Pembangunan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "Pilih struktur untuk memperluas komunitas Havenfold",
                        fontSize = 12.sp,
                        color = Color(0xFF7F8C8D)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_build_catalog")) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val buildings = listOf(
                BuildingType.COZY_HUT,
                BuildingType.FAMILY_HOMESTEAD,
                BuildingType.RECREATION_PARK,
                BuildingType.COMMUNITY_PLAZA,
                BuildingType.WHEAT_FIELD,
                BuildingType.PUMPKIN_PATCH,
                BuildingType.WOODCUTTER_CAMP,
                BuildingType.STONE_QUARRY,
                BuildingType.FORAGER_HUT,
                BuildingType.GRANARY,
                BuildingType.WELL
            )

            LazyColumn(modifier = Modifier.height(340.dp)) {
                items(buildings) { b ->
                    val canAfford = inventory.wood >= b.woodCost && inventory.stone >= b.stoneCost

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (canAfford) Color(0xFFF9F6EE) else Color(0xFFECEFF1)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = b.iconEmoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = b.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2C3E50)
                                )
                                Text(
                                    text = b.description,
                                    fontSize = 11.sp,
                                    color = Color(0xFF5A6B7C),
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (b.woodCost > 0) {
                                        Text(
                                            text = "🪵 ${b.woodCost}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (inventory.wood >= b.woodCost) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    if (b.stoneCost > 0) {
                                        Text(
                                            text = "🪨 ${b.stoneCost}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (inventory.stone >= b.stoneCost) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = { onSelectBuilding(b) },
                                enabled = canAfford,
                                modifier = Modifier.testTag("btn_build_${b.name.lowercase()}"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3E7B44)
                                )
                            ) {
                                Text(text = "Pilih", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Outlanders-style Job Allocation Sheet
 */
@Composable
fun JobAssignmentSheet(
    villagers: List<Villager>,
    onAdjustJobCount: (JobType, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val ableWorkers = villagers.filter { it.canWork }
    val unassignedCount = ableWorkers.count { it.job == JobType.UNASSIGNED }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("job_assignment_sheet"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📋 Alokasi Pekerjaan Warga",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "Tenaga Kerja Siap: ${ableWorkers.size} | Warga Bebas: $unassignedCount",
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_jobs_sheet")) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val jobs = listOf(
                JobType.FARMER,
                JobType.WOODCUTTER,
                JobType.MINER,
                JobType.BUILDER,
                JobType.FORAGER
            )

            for (job in jobs) {
                val currentAssigned = ableWorkers.count { it.job == job }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(Color(0xFFF7F4EC), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = job.iconEmoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = job.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF2C3E50)
                            )
                            val desc = when (job) {
                                JobType.FARMER -> "Menanam & memanen bahan pangan"
                                JobType.WOODCUTTER -> "Menebang pohon & suplai kayu"
                                JobType.MINER -> "Mengumpulkan & memahat batu di Tempat Pengumpulan Batu"
                                JobType.BUILDER -> "Mendirikan bangunan desa"
                                JobType.FORAGER -> "Memetik buah beri & tanaman liar"
                                else -> ""
                            }
                            Text(text = desc, fontSize = 10.sp, color = Color(0xFF7F8C8D))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onAdjustJobCount(job, currentAssigned - 1) },
                            enabled = currentAssigned > 0,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White, CircleShape)
                                .testTag("btn_decrease_${job.name.lowercase()}")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Kurang", tint = Color(0xFFC62828))
                        }

                        Text(
                            text = "$currentAssigned",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = Color(0xFF2C3E50)
                        )

                        IconButton(
                            onClick = { onAdjustJobCount(job, currentAssigned + 1) },
                            enabled = unassignedCount > 0,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White, CircleShape)
                                .testTag("btn_increase_${job.name.lowercase()}")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Community Roster Sheet
 */
@Composable
fun VillagersRosterSheet(
    villagers: List<Villager>,
    onSelectVillager: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("roster_sheet"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "👥 Direktori Penduduk (${villagers.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "Daftar seluruh warga pemukiman Havenfold",
                        fontSize = 12.sp,
                        color = Color(0xFF7F8C8D)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_roster")) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(villagers) { v ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectVillager(v.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4EC))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(v.tunicColorHex), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (v.isFemale) "👩" else if (v.isChild) "🧒" else "👨",
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${v.name} ${v.happinessEmoji}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF2C3E50)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${v.lifeStageLabel} (${v.ageDays.toInt()} thn)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF7F8C8D)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Bahagia: ${v.happiness.toInt()}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (v.happiness >= 70f) Color(0xFF2E7D32) else if (v.happiness >= 40f) Color(0xFFF57F17) else Color(0xFFD32F2F)
                                    )
                                }
                                Text(
                                    text = "${v.job.iconEmoji} ${v.job.title} — ${v.action.label}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5D6D7E)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🍞", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    LinearProgressIndicator(
                                        progress = { v.hunger / 100f },
                                        modifier = Modifier
                                            .width(70.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = if (v.hunger > 30f) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(text = "⚡", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    LinearProgressIndicator(
                                        progress = { v.energy / 100f },
                                        modifier = Modifier
                                            .width(70.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFFFFA000)
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { onSelectVillager(v.id) },
                                modifier = Modifier.size(width = 65.dp, height = 32.dp)
                            ) {
                                Text(text = "Pilih", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Community Goals / Ledger Sheet
 */
@Composable
fun CommunityGoalsSheet(
    goals: List<CommunityGoal>,
    events: List<com.example.game.model.SimulationEvent>,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("goals_sheet"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📜 Catatan & Misi Desa",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "Panduan pencapaian komunitas pulau",
                        fontSize = 12.sp,
                        color = Color(0xFF7F8C8D)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_goals")) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "TARGET KOMUNITAS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E6B38)
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(goals) { g ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (g.isCompleted) Color(0xFFE8F5E9) else Color(0xFFF7F4EC)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${if (g.isCompleted) "✅ " else "🎯 "}${g.title}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF2C3E50)
                                )
                                Text(
                                    text = "${g.current} / ${g.target}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (g.isCompleted) Color(0xFF2E7D32) else Color(0xFF1976D2)
                                )
                            }
                            Text(
                                text = g.description,
                                fontSize = 11.sp,
                                color = Color(0xFF5D6D7E),
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                            LinearProgressIndicator(
                                progress = { (g.current.toFloat() / g.target).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (g.isCompleted) Color(0xFF2E7D32) else Color(0xFFFFA000)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hadiah: ${g.rewardText}",
                                fontSize = 10.sp,
                                color = Color(0xFF8D6E63)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "WARTA PERISTIWA TERKINI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E6B38)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(events) { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = ev.iconEmoji, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "[Hari ${ev.day}] ${ev.text}",
                            fontSize = 11.sp,
                            color = Color(0xFF455A64)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Villager Inspector Bottom Card
 */
@Composable
fun VillagerInspectorCard(
    villager: Villager,
    onAssignJob: (JobType) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("villager_inspector_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
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
                            .size(46.dp)
                            .background(Color(villager.tunicColorHex), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (villager.isFemale) "👩" else if (villager.isChild) "🧒" else "👨",
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = villager.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50)
                        )
                        Text(
                            text = "${villager.lifeStageLabel} • Usia ${villager.ageDays.toInt()} Tahun",
                            fontSize = 12.sp,
                            color = Color(0xFF7F8C8D)
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_inspector")) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status message
            Surface(
                color = Color(0xFFF1F8E9),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Aktivitas: ${villager.action.emoji} ${villager.statusMessage}",
                    fontSize = 12.sp,
                    color = Color(0xFF33691E),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Composite Happiness & Productivity Card
            Surface(
                color = when {
                    villager.happiness >= 75f -> Color(0xFFE8F5E9)
                    villager.happiness >= 45f -> Color(0xFFFFF8E1)
                    else -> Color(0xFFFFEBEE)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${villager.happinessEmoji} Kebahagiaan: ${villager.happiness.toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                villager.happiness >= 75f -> Color(0xFF2E7D32)
                                villager.happiness >= 45f -> Color(0xFFF57F17)
                                else -> Color(0xFFC62828)
                            }
                        )
                        Text(
                            text = "Status: ${villager.happinessLabel}",
                            fontSize = 11.sp,
                            color = Color(0xFF5D6D7E)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "⚡ Produktivitas: ${(villager.productivityMultiplier * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                villager.productivityMultiplier > 1.0f -> Color(0xFF2E7D32)
                                villager.productivityMultiplier == 1.0f -> Color(0xFF1976D2)
                                else -> Color(0xFFD32F2F)
                            }
                        )
                        val prodLabel = when {
                            villager.productivityMultiplier > 1.2f -> "Bonus Semangat! (+35%)"
                            villager.productivityMultiplier == 1.0f -> "Normal (100%)"
                            villager.productivityMultiplier >= 0.7f -> "Lesu (-30%)"
                            else -> "Sangat Lambat (-55%)"
                        }
                        Text(
                            text = prodLabel,
                            fontSize = 10.sp,
                            color = Color(0xFF7F8C8D)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Modular Needs
            Text(
                text = "KEBUTUHAN PENDUDUK (MODULAR NEEDS)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E6B38)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 1: Makanan & Tempat Tinggal
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "🍞 Pangan", fontSize = 11.sp, color = Color(0xFF555555))
                        Text(text = "${villager.hunger.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { villager.hunger / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (villager.hunger > 35f) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "🛖 Tempat Tinggal", fontSize = 11.sp, color = Color(0xFF555555))
                        Text(text = "${villager.housingNeed.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { villager.housingNeed / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (villager.housingNeed > 40f) Color(0xFF8D6E63) else Color(0xFFD32F2F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Rekreasi & Sosialisasi
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "🌸 Rekreasi", fontSize = 11.sp, color = Color(0xFF555555))
                        Text(text = "${villager.recreationNeed.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { villager.recreationNeed / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (villager.recreationNeed > 40f) Color(0xFFE91E63) else Color(0xFFD32F2F)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "💬 Sosialisasi", fontSize = 11.sp, color = Color(0xFF555555))
                        Text(text = "${villager.socialNeed.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { villager.socialNeed / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (villager.socialNeed > 40f) Color(0xFF1976D2) else Color(0xFFD32F2F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Job Assignment
            Text(
                text = "TUGASKAN PEKERJAAN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val availableJobs = listOf(
                    JobType.UNASSIGNED,
                    JobType.FARMER,
                    JobType.WOODCUTTER,
                    JobType.MINER,
                    JobType.BUILDER,
                    JobType.FORAGER
                )

                for (j in availableJobs) {
                    val isSelected = villager.job == j
                    Button(
                        onClick = { onAssignJob(j) },
                        enabled = villager.canWork || j == JobType.UNASSIGNED,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF2E6B38) else Color(0xFFE0E0E0),
                            contentColor = if (isSelected) Color.White else Color(0xFF424242)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("job_btn_${j.name.lowercase()}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = j.iconEmoji, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Building Inspector Bottom Card
 */
@Composable
fun BuildingInspectorCard(
    building: Building,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("building_inspector_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = building.type.iconEmoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = building.type.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50)
                        )
                        Text(
                            text = if (building.isConstructed) "Bangunan Berfungsi" else "Dalam Tahap Pembangunan (${building.constructionProgress.toInt()}%)",
                            fontSize = 12.sp,
                            color = if (building.isConstructed) Color(0xFF2E7D32) else Color(0xFFE65100),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_building_inspector")) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = building.type.description,
                fontSize = 12.sp,
                color = Color(0xFF5D6D7E)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!building.isConstructed) {
                Column {
                    Text(
                        text = "Kemajuan Konstruksi: ${building.constructionProgress.toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { building.constructionProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFFFA000)
                    )
                }
            } else if (building.type == BuildingType.WHEAT_FIELD || building.type == BuildingType.PUMPKIN_PATCH) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (building.cropGrowth >= 100f) "🌾 Panen Siap Dituai!" else "🌱 Pertumbuhan Tanaman",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (building.cropGrowth >= 100f) Color(0xFF2E7D32) else Color(0xFF424242)
                        )
                        Text(text = "${building.cropGrowth.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { building.cropGrowth / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (building.cropGrowth >= 100f) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                }
            } else if (building.type == BuildingType.STONE_QUARRY) {
                Text(
                    text = "⛏️ Tempat Pengumpulan Batu: Menugaskan penambang untuk memahat batuan alami pulau dan mengumpulkan stok batu untuk pembangunan.",
                    fontSize = 12.sp,
                    color = Color(0xFF455A64),
                    fontWeight = FontWeight.Medium
                )
            } else if (building.type == BuildingType.RECREATION_PARK) {
                Text(
                    text = "🌸 Fasilitas Rekreasi: Memberikan kesegaran batin, meningkatkan kebutuhan rekreasi warga hingga 100%, serta memicu produktivitas tinggi.",
                    fontSize = 12.sp,
                    color = Color(0xFFC2185B),
                    fontWeight = FontWeight.Medium
                )
            } else if (building.type == BuildingType.COMMUNITY_PLAZA) {
                Text(
                    text = "🎪 Fasilitas Sosialisasi: Tempat berkumpul dan bercengkerama warga, menjaga keterikatan sosial dan kerukunan komunitas.",
                    fontSize = 12.sp,
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.Medium
                )
            } else if (building.type.housingCapacity > 0) {
                Text(
                    text = "Kapasitas Hunian: ${building.type.housingCapacity} Orang (Mendukung keluarga & kelahiran bayi)",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

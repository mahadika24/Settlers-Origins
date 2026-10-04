package com.example.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.example.game.engine.GameState
import com.example.game.engine.SimulationEngine
import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.CarryType
import com.example.game.model.DayPhase
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.TileType
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import kotlin.math.sin

@Composable
fun WorldCanvas(
    gameState: GameState,
    onTileTap: (Int, Int) -> Unit,
    onVillagerTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var panOffsetX by remember { mutableFloatStateOf(100f) }
    var panOffsetY by remember { mutableFloatStateOf(140f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    val baseTileSize = 48f
    val tileSize = baseTileSize * zoomScale

    // Animation transitions for water shimmer, swaying crops, and smoke puffs
    val infiniteTransition = rememberInfiniteTransition(label = "world_anim")
    val waveAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF264653))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.65f, 2.2f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
            .pointerInput(zoomScale, panOffsetX, panOffsetY, gameState.pendingBuildType) {
                detectTapGestures { tapOffset ->
                    val clickedTileX = ((tapOffset.x - panOffsetX) / tileSize).toInt()
                    val clickedTileY = ((tapOffset.y - panOffsetY) / tileSize).toInt()

                    if (clickedTileX in 0 until SimulationEngine.MAP_SIZE &&
                        clickedTileY in 0 until SimulationEngine.MAP_SIZE
                    ) {
                        // Check if a villager is near the tap point
                        val tappedVillager = gameState.villagers.find {
                            val vx = panOffsetX + it.posX * tileSize + tileSize * 0.5f
                            val vy = panOffsetY + it.posY * tileSize + tileSize * 0.5f
                            val dist = Math.hypot((tapOffset.x - vx).toDouble(), (tapOffset.y - vy).toDouble())
                            dist < tileSize * 0.75
                        }

                        if (tappedVillager != null && gameState.pendingBuildType == null) {
                            onVillagerTap(tappedVillager.id)
                        } else {
                            onTileTap(clickedTileX, clickedTileY)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mapPixelWidth = SimulationEngine.MAP_SIZE * tileSize
            val mapPixelHeight = SimulationEngine.MAP_SIZE * tileSize

            // Draw Terrain Island Background (Deep Water border)
            drawRect(
                color = Color(0xFF3B7A99),
                topLeft = Offset(panOffsetX - 200f, panOffsetY - 200f),
                size = Size(mapPixelWidth + 400f, mapPixelHeight + 400f)
            )

            // 1. Draw Grid Tiles
            for (x in 0 until SimulationEngine.MAP_SIZE) {
                for (y in 0 until SimulationEngine.MAP_SIZE) {
                    val tile = gameState.tiles[x][y]
                    val px = panOffsetX + x * tileSize
                    val py = panOffsetY + y * tileSize

                    drawTile(
                        tile = tile,
                        px = px,
                        py = py,
                        size = tileSize,
                        wave = waveAnim,
                        x = x,
                        y = y
                    )
                }
            }

            // 2. Draw Natural Resources (Under buildings)
            for (res in gameState.resources) {
                val rx = panOffsetX + res.x * tileSize
                val ry = panOffsetY + res.y * tileSize
                drawNaturalResource(res, rx, ry, tileSize, waveAnim)
            }

            // 3. Draw Buildings
            for (building in gameState.buildings) {
                val bx = panOffsetX + building.x * tileSize
                val by = panOffsetY + building.y * tileSize
                drawBuilding(building, bx, by, tileSize, waveAnim, gameState.timeSystem.phase)
            }

            // 4. Draw Villagers
            for (v in gameState.villagers) {
                val vx = panOffsetX + v.posX * tileSize
                val vy = panOffsetY + v.posY * tileSize
                val isSelected = v.id == gameState.selectedVillagerId
                drawVillager(v, vx, vy, tileSize, waveAnim, isSelected)
            }

            // 5. Draw Building Placement Ghost Preview
            val pendingType = gameState.pendingBuildType
            val selectedCoord = gameState.selectedTile
            if (pendingType != null && selectedCoord != null) {
                val gx = panOffsetX + selectedCoord.first * tileSize
                val gy = panOffsetY + selectedCoord.second * tileSize
                val isValid = canPlaceCheck(pendingType, selectedCoord.first, selectedCoord.second, gameState)

                drawRoundRect(
                    color = if (isValid) Color(0x994CAF50) else Color(0x99F44336),
                    topLeft = Offset(gx, gy),
                    size = Size(pendingType.width * tileSize, pendingType.height * tileSize),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                drawRoundRect(
                    color = if (isValid) Color.White else Color(0xFFFFCDD2),
                    topLeft = Offset(gx, gy),
                    size = Size(pendingType.width * tileSize, pendingType.height * tileSize),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 3f)
                )
            }

            // 6. Draw Selected Tile Marker
            if (selectedCoord != null && pendingType == null) {
                val sx = panOffsetX + selectedCoord.first * tileSize
                val sy = panOffsetY + selectedCoord.second * tileSize
                drawRoundRect(
                    color = Color(0x88FFD54F),
                    topLeft = Offset(sx, sy),
                    size = Size(tileSize, tileSize),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 3.5f)
                )
            }

            // 7. Day / Night Atmosphere Lighting Overlay
            drawAtmosphereOverlay(
                phase = gameState.timeSystem.phase,
                progress = gameState.timeSystem.progressOfDay,
                buildings = gameState.buildings,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                tileSize = tileSize,
                viewSize = size
            )
        }
    }
}

private fun canPlaceCheck(type: BuildingType, x: Int, y: Int, state: GameState): Boolean {
    if (x + type.width > SimulationEngine.MAP_SIZE || y + type.height > SimulationEngine.MAP_SIZE) return false
    if (x < 0 || y < 0) return false

    for (tx in x until (x + type.width)) {
        for (ty in y until (y + type.height)) {
            val tile = state.tiles[tx][ty]
            if (!tile.type.canBuild || tile.isOccupied) return false
            if (state.resources.any { it.x == tx && it.y == ty && it.type == ResourceType.ROCK }) return false
        }
    }
    return true
}

private fun DrawScope.drawTile(
    tile: com.example.game.model.WorldTile,
    px: Float,
    py: Float,
    size: Float,
    wave: Float,
    x: Int,
    y: Int
) {
    when (tile.type) {
        TileType.GRASS -> {
            val isChecker = (x + y) % 2 == 0
            val grassColor = if (isChecker) Color(0xFF6DA343) else Color(0xFF64973B)
            drawRoundRect(
                color = grassColor,
                topLeft = Offset(px, py),
                size = Size(size, size),
                cornerRadius = CornerRadius(size * 0.12f, size * 0.12f)
            )
            // Cute tiny grass tufts
            if ((x * 7 + y * 13) % 5 == 0) {
                val tuftX = px + size * 0.3f
                val tuftY = py + size * 0.4f
                drawLine(
                    color = Color(0xFF7CB84C),
                    start = Offset(tuftX, tuftY),
                    end = Offset(tuftX - size * 0.08f, tuftY - size * 0.15f),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color(0xFF7CB84C),
                    start = Offset(tuftX, tuftY),
                    end = Offset(tuftX + size * 0.08f, tuftY - size * 0.15f),
                    strokeWidth = 2.5f
                )
            }
        }
        TileType.FERTILE_SOIL -> {
            drawRoundRect(
                color = Color(0xFF8D6E46),
                topLeft = Offset(px, py),
                size = Size(size, size),
                cornerRadius = CornerRadius(size * 0.15f, size * 0.15f)
            )
            // Soil furrows
            val furrowColor = Color(0xFF735735)
            drawLine(
                color = furrowColor,
                start = Offset(px + size * 0.15f, py + size * 0.35f),
                end = Offset(px + size * 0.85f, py + size * 0.35f),
                strokeWidth = 2f
            )
            drawLine(
                color = furrowColor,
                start = Offset(px + size * 0.15f, py + size * 0.65f),
                end = Offset(px + size * 0.85f, py + size * 0.65f),
                strokeWidth = 2f
            )
        }
        TileType.WATER -> {
            val waveOffset = (sin(wave + x * 0.8f + y * 0.5f) * (size * 0.06f)).toFloat()
            drawRoundRect(
                color = Color(0xFF4FA0C9),
                topLeft = Offset(px, py),
                size = Size(size, size),
                cornerRadius = CornerRadius(size * 0.2f, size * 0.2f)
            )
            // Ripple wave line
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(px + size * 0.2f, py + size * 0.5f + waveOffset),
                end = Offset(px + size * 0.8f, py + size * 0.5f - waveOffset),
                strokeWidth = 2f
            )
        }
        TileType.STONE_OUTCROP -> {
            drawRoundRect(
                color = Color(0xFF7B8488),
                topLeft = Offset(px, py),
                size = Size(size, size),
                cornerRadius = CornerRadius(size * 0.2f, size * 0.2f)
            )
            drawCircle(
                color = Color(0xFF90999D),
                radius = size * 0.25f,
                center = Offset(px + size * 0.45f, py + size * 0.45f)
            )
        }
    }
}

private fun DrawScope.drawNaturalResource(
    res: NaturalResource,
    rx: Float,
    ry: Float,
    size: Float,
    wave: Float
) {
    when (res.type) {
        ResourceType.TREE -> {
            val sway = (sin(wave + res.x) * 2f).toFloat()
            val trunkWidth = size * 0.18f
            val trunkHeight = size * 0.35f
            val baseCenter = Offset(rx + size * 0.5f, ry + size * 0.75f)

            // Shadow
            drawOval(
                color = Color(0x33000000),
                topLeft = Offset(baseCenter.x - size * 0.3f, baseCenter.y - size * 0.1f),
                size = Size(size * 0.6f, size * 0.25f)
            )

            if (res.growthStage == 0) {
                // Sapling / Tunas
                drawLine(
                    color = Color(0xFF5D4037),
                    start = baseCenter,
                    end = Offset(baseCenter.x, baseCenter.y - size * 0.3f),
                    strokeWidth = 3f
                )
                drawCircle(
                    color = Color(0xFF81C784),
                    radius = size * 0.15f,
                    center = Offset(baseCenter.x, baseCenter.y - size * 0.35f)
                )
            } else {
                // Trunk
                drawRoundRect(
                    color = Color(0xFF5D4037),
                    topLeft = Offset(baseCenter.x - trunkWidth / 2, baseCenter.y - trunkHeight),
                    size = Size(trunkWidth, trunkHeight),
                    cornerRadius = CornerRadius(3f, 3f)
                )

                // Foliage canopy (2 tiers)
                val crownRadius = if (res.growthStage == 2) size * 0.38f else size * 0.28f
                val crownCenter = Offset(baseCenter.x + sway, baseCenter.y - trunkHeight - crownRadius * 0.4f)

                // Lower darker canopy
                drawCircle(
                    color = Color(0xFF2E6B38),
                    radius = crownRadius,
                    center = crownCenter
                )
                // Upper brighter canopy
                drawCircle(
                    color = Color(0xFF388E3C),
                    radius = crownRadius * 0.8f,
                    center = Offset(crownCenter.x, crownCenter.y - crownRadius * 0.3f)
                )
            }
        }
        ResourceType.BERRY_BUSH -> {
            val center = Offset(rx + size * 0.5f, ry + size * 0.5f)
            // Leaf shrub
            drawCircle(
                color = Color(0xFF43A047),
                radius = size * 0.32f,
                center = center
            )
            drawCircle(
                color = Color(0xFF66BB6A),
                radius = size * 0.22f,
                center = Offset(center.x - size * 0.08f, center.y - size * 0.08f)
            )
            // Berries
            if (res.amount > 0) {
                val berryColor = Color(0xFF8E24AA)
                drawCircle(color = berryColor, radius = size * 0.07f, center = Offset(center.x - size * 0.12f, center.y))
                drawCircle(color = berryColor, radius = size * 0.07f, center = Offset(center.x + size * 0.12f, center.y - size * 0.1f))
                drawCircle(color = berryColor, radius = size * 0.07f, center = Offset(center.x, center.y + size * 0.12f))
            }
        }
        ResourceType.ROCK -> {
            val center = Offset(rx + size * 0.5f, ry + size * 0.55f)
            drawOval(
                color = Color(0x33000000),
                topLeft = Offset(center.x - size * 0.35f, center.y - size * 0.1f),
                size = Size(size * 0.7f, size * 0.35f)
            )
            drawRoundRect(
                color = Color(0xFF616161),
                topLeft = Offset(center.x - size * 0.3f, center.y - size * 0.25f),
                size = Size(size * 0.6f, size * 0.45f),
                cornerRadius = CornerRadius(size * 0.18f, size * 0.18f)
            )
            drawRoundRect(
                color = Color(0xFF757575),
                topLeft = Offset(center.x - size * 0.22f, center.y - size * 0.22f),
                size = Size(size * 0.35f, size * 0.25f),
                cornerRadius = CornerRadius(size * 0.12f, size * 0.12f)
            )
        }
    }
}

private fun DrawScope.drawBuilding(
    b: Building,
    bx: Float,
    by: Float,
    size: Float,
    wave: Float,
    phase: DayPhase
) {
    val bWidth = b.type.width * size
    val bHeight = b.type.height * size

    // Unconstructed Building (Construction Site Scaffold)
    if (!b.isConstructed) {
        drawRoundRect(
            color = Color(0x44D7CCC8),
            topLeft = Offset(bx + 4f, by + 4f),
            size = Size(bWidth - 8f, bHeight - 8f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        // Construction scaffolding boundary
        drawRoundRect(
            color = Color(0xFF8D6E63),
            topLeft = Offset(bx + 4f, by + 4f),
            size = Size(bWidth - 8f, bHeight - 8f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 3f)
        )

        // Progress Bar
        val barWidth = bWidth * 0.7f
        val barHeight = 8f
        val barX = bx + (bWidth - barWidth) / 2
        val barY = by + bHeight * 0.5f

        drawRoundRect(
            color = Color(0x88000000),
            topLeft = Offset(barX, barY),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFFFFA000),
            topLeft = Offset(barX, barY),
            size = Size(barWidth * (b.constructionProgress / 100f), barHeight),
            cornerRadius = CornerRadius(4f, 4f)
        )
        return
    }

    when (b.type) {
        BuildingType.TOWN_HEARTH -> {
            // Stone plaza base
            drawRoundRect(
                color = Color(0xFF9E9E9E),
                topLeft = Offset(bx + size * 0.2f, by + size * 0.2f),
                size = Size(bWidth - size * 0.4f, bHeight - size * 0.4f),
                cornerRadius = CornerRadius(size * 0.3f, size * 0.3f)
            )
            // Firepit ring
            val center = Offset(bx + bWidth / 2, by + bHeight / 2)
            drawCircle(color = Color(0xFF424242), radius = size * 0.45f, center = center)

            // Warm crackling fire
            val fireFlicker = (sin(wave * 4f) * 3f).toFloat()
            drawCircle(color = Color(0xFFFF5722), radius = size * 0.32f + fireFlicker, center = center)
            drawCircle(color = Color(0xFFFFC107), radius = size * 0.20f + fireFlicker * 0.5f, center = center)

            // Wooden logs around campfire
            drawLine(
                color = Color(0xFF5D4037),
                start = Offset(center.x - size * 0.35f, center.y),
                end = Offset(center.x + size * 0.35f, center.y),
                strokeWidth = 5f
            )
            drawLine(
                color = Color(0xFF5D4037),
                start = Offset(center.x, center.y - size * 0.35f),
                end = Offset(center.x, center.y + size * 0.35f),
                strokeWidth = 5f
            )
        }

        BuildingType.COZY_HUT -> {
            // Cottage Shadow
            drawOval(
                color = Color(0x33000000),
                topLeft = Offset(bx + size * 0.1f, by + bHeight - size * 0.4f),
                size = Size(bWidth * 0.9f, size * 0.5f)
            )
            // Wooden Wall Body
            drawRoundRect(
                color = Color(0xFF8D6E63),
                topLeft = Offset(bx + size * 0.25f, by + size * 0.5f),
                size = Size(bWidth - size * 0.5f, bHeight - size * 0.7f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Wooden door
            drawRoundRect(
                color = Color(0xFF4E342E),
                topLeft = Offset(bx + bWidth * 0.4f, by + bHeight * 0.62f),
                size = Size(size * 0.35f, size * 0.55f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Window (glows yellow at night)
            val windowColor = if (phase == DayPhase.NIGHT) Color(0xFFFFEB3B) else Color(0xFFB0BEC5)
            drawRoundRect(
                color = windowColor,
                topLeft = Offset(bx + bWidth * 0.2f, by + bHeight * 0.65f),
                size = Size(size * 0.22f, size * 0.25f),
                cornerRadius = CornerRadius(2f, 2f)
            )

            // Thatched / Terracotta Pitched Roof
            val roofPath = Path().apply {
                moveTo(bx + bWidth * 0.5f, by + size * 0.15f)
                lineTo(bx + bWidth - size * 0.1f, by + size * 0.55f)
                lineTo(bx + size * 0.1f, by + size * 0.55f)
                close()
            }
            drawPath(roofPath, color = Color(0xFFC06C46))
            drawPath(roofPath, color = Color(0xFF8C3E20), style = Stroke(width = 3f))
        }

        BuildingType.FAMILY_HOMESTEAD -> {
            // Large wooden family estate
            drawOval(
                color = Color(0x33000000),
                topLeft = Offset(bx + size * 0.1f, by + bHeight - size * 0.35f),
                size = Size(bWidth * 0.95f, size * 0.5f)
            )
            // Sturdy log cabin body
            drawRoundRect(
                color = Color(0xFF795548),
                topLeft = Offset(bx + size * 0.18f, by + size * 0.42f),
                size = Size(bWidth - size * 0.36f, bHeight - size * 0.6f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Two warm windows with flowerboxes
            val winColor = if (phase == DayPhase.NIGHT) Color(0xFFFFD54F) else Color(0xFFECEFF1)
            drawRoundRect(
                color = winColor,
                topLeft = Offset(bx + size * 0.32f, by + size * 0.65f),
                size = Size(size * 0.28f, size * 0.32f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = winColor,
                topLeft = Offset(bx + bWidth - size * 0.60f, by + size * 0.65f),
                size = Size(size * 0.28f, size * 0.32f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Flowerbox red & yellow dots
            drawCircle(color = Color(0xFFE91E63), radius = 3.5f, center = Offset(bx + size * 0.38f, by + size * 1.02f))
            drawCircle(color = Color(0xFFFFEB3B), radius = 3.5f, center = Offset(bx + size * 0.50f, by + size * 1.02f))
            drawCircle(color = Color(0xFFE91E63), radius = 3.5f, center = Offset(bx + bWidth - size * 0.52f, by + size * 1.02f))
            // Central oak door
            drawRoundRect(
                color = Color(0xFF3E2723),
                topLeft = Offset(bx + bWidth * 0.42f, by + size * 0.70f),
                size = Size(size * 0.36f, size * 0.62f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Majestic slate blue/teal roof
            val manorRoof = Path().apply {
                moveTo(bx + bWidth * 0.5f, by + size * 0.08f)
                lineTo(bx + bWidth - size * 0.05f, by + size * 0.48f)
                lineTo(bx + size * 0.05f, by + size * 0.48f)
                close()
            }
            drawPath(manorRoof, color = Color(0xFF37474F))
            drawPath(manorRoof, color = Color(0xFF263238), style = Stroke(width = 3f))
        }

        BuildingType.RECREATION_PARK -> {
            // Lush flower garden lawn
            drawRoundRect(
                color = Color(0xFF7CB342),
                topLeft = Offset(bx + 4f, by + 4f),
                size = Size(bWidth - 8f, bHeight - 8f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            // Stepping stone pathway
            drawCircle(color = Color(0xFFCFD8DC), radius = size * 0.16f, center = Offset(bx + size * 0.6f, by + size * 1.4f))
            drawCircle(color = Color(0xFFCFD8DC), radius = size * 0.16f, center = Offset(bx + size * 1.4f, by + size * 0.6f))

            // Central stone fountain / birdbath
            val centerFountain = Offset(bx + bWidth / 2, by + bHeight / 2)
            drawCircle(color = Color(0xFF78909C), radius = size * 0.35f, center = centerFountain)
            drawCircle(color = Color(0xFF29B6F6), radius = size * 0.22f, center = centerFountain)
            // Fountain water splash
            val splash = (sin(wave * 5f) * 2f).toFloat()
            drawCircle(color = Color.White, radius = 4f + splash, center = centerFountain)

            // Flower beds (tulips & daisies)
            val flowerColors = listOf(Color(0xFFE91E63), Color(0xFFFFEB3B), Color(0xFF9C27B0), Color(0xFFFF5722))
            val flowerOffsets = listOf(
                Pair(bx + size * 0.4f, by + size * 0.4f),
                Pair(bx + size * 0.6f, by + size * 0.35f),
                Pair(bx + bWidth - size * 0.4f, by + bHeight - size * 0.4f),
                Pair(bx + bWidth - size * 0.6f, by + bHeight - size * 0.35f)
            )
            for (idx in flowerOffsets.indices) {
                val fPos = flowerOffsets[idx]
                val fCol = flowerColors[idx % flowerColors.size]
                drawCircle(color = fCol, radius = size * 0.10f, center = Offset(fPos.first, fPos.second))
                drawCircle(color = Color.White, radius = size * 0.04f, center = Offset(fPos.first, fPos.second))
            }

            // Wooden park bench
            drawRoundRect(
                color = Color(0xFF8D6E63),
                topLeft = Offset(bx + size * 0.35f, by + bHeight - size * 0.65f),
                size = Size(size * 0.7f, size * 0.25f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }

        BuildingType.COMMUNITY_PLAZA -> {
            // Cobblestone plaza pavement
            drawRoundRect(
                color = Color(0xFFB0BEC5),
                topLeft = Offset(bx + 4f, by + 4f),
                size = Size(bWidth - 8f, bHeight - 8f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            // Wooden pavilion pillars
            val pillarColor = Color(0xFF5D4037)
            drawCircle(color = pillarColor, radius = size * 0.12f, center = Offset(bx + size * 0.4f, by + size * 0.4f))
            drawCircle(color = pillarColor, radius = size * 0.12f, center = Offset(bx + bWidth - size * 0.4f, by + size * 0.4f))
            drawCircle(color = pillarColor, radius = size * 0.12f, center = Offset(bx + size * 0.4f, by + bHeight - size * 0.4f))
            drawCircle(color = pillarColor, radius = size * 0.12f, center = Offset(bx + bWidth - size * 0.4f, by + bHeight - size * 0.4f))

            // Long community banquet table & stools
            drawRoundRect(
                color = Color(0xFF8D6E63),
                topLeft = Offset(bx + size * 0.5f, by + size * 0.8f),
                size = Size(bWidth - size * 1.0f, size * 0.4f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Festive bunting garland
            val garlandColors = listOf(Color(0xFFE91E63), Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF2196F3))
            for (i in 0..3) {
                val gx = bx + size * 0.45f + i * (size * 0.35f)
                val gy = by + size * 0.32f + (sin(wave * 2f + i) * 2f).toFloat()
                drawCircle(color = garlandColors[i % garlandColors.size], radius = 4f, center = Offset(gx, gy))
            }
            // Central lantern / warm light
            val lanternCenter = Offset(bx + bWidth / 2, by + size * 0.4f)
            drawCircle(color = Color(0xFFFFD54F), radius = size * 0.18f, center = lanternCenter)
        }

        BuildingType.WHEAT_FIELD -> {
            // Tilled background
            drawRoundRect(
                color = Color(0xFF7A5835),
                topLeft = Offset(bx + 4f, by + 4f),
                size = Size(bWidth - 8f, bHeight - 8f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Wheat stalks
            val growth = b.cropGrowth / 100f
            val stalkColor = if (growth > 0.8f) Color(0xFFFBC02D) else Color(0xFF8BC34A)
            val rows = 3
            val cols = 3

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val wx = bx + size * 0.4f + c * (size * 0.55f)
                    val wy = by + size * 0.5f + r * (size * 0.55f)
                    val height = (size * 0.35f) * (0.25f + growth * 0.75f)
                    val sway = (sin(wave * 2f + c + r) * 2f).toFloat()

                    drawLine(
                        color = stalkColor,
                        start = Offset(wx, wy),
                        end = Offset(wx + sway, wy - height),
                        strokeWidth = 3f
                    )
                    if (growth > 0.6f) {
                        drawCircle(
                            color = Color(0xFFF57F17),
                            radius = size * 0.08f,
                            center = Offset(wx + sway, wy - height)
                        )
                    }
                }
            }
        }

        BuildingType.PUMPKIN_PATCH -> {
            drawRoundRect(
                color = Color(0xFF6D4C41),
                topLeft = Offset(bx + 4f, by + 4f),
                size = Size(bWidth - 8f, bHeight - 8f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            val growth = b.cropGrowth / 100f
            // Vines
            drawLine(
                color = Color(0xFF558B2F),
                start = Offset(bx + size * 0.3f, by + size * 0.3f),
                end = Offset(bx + bWidth - size * 0.3f, by + bHeight - size * 0.3f),
                strokeWidth = 2.5f
            )
            if (growth > 0.3f) {
                val pumpkinRadius = size * 0.16f * growth
                drawCircle(color = Color(0xFFE65100), radius = pumpkinRadius, center = Offset(bx + size * 0.6f, by + size * 0.7f))
                drawCircle(color = Color(0xFFEF6C00), radius = pumpkinRadius, center = Offset(bx + size * 1.3f, by + size * 1.1f))
            }
        }

        BuildingType.WOODCUTTER_CAMP -> {
            drawRoundRect(
                color = Color(0xFF8D6E63),
                topLeft = Offset(bx + size * 0.2f, by + size * 0.3f),
                size = Size(bWidth - size * 0.4f, bHeight - size * 0.6f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Log piles
            drawRoundRect(
                color = Color(0xFF4E342E),
                topLeft = Offset(bx + size * 0.4f, by + size * 0.6f),
                size = Size(size * 0.6f, size * 0.25f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = Color(0xFF5D4037),
                topLeft = Offset(bx + size * 0.45f, by + size * 0.45f),
                size = Size(size * 0.5f, size * 0.22f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Chopping stump
            drawCircle(color = Color(0xFF3E2723), radius = size * 0.2f, center = Offset(bx + size * 1.3f, by + size * 1.1f))
        }

        BuildingType.STONE_QUARRY -> {
            // Cobblestone masonry yard floor
            drawRoundRect(
                color = Color(0xFF78909C),
                topLeft = Offset(bx + size * 0.12f, by + size * 0.15f),
                size = Size(bWidth - size * 0.24f, bHeight - size * 0.3f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Cut stone blocks stack
            drawRoundRect(
                color = Color(0xFF90A4AE),
                topLeft = Offset(bx + size * 0.3f, by + size * 0.55f),
                size = Size(size * 0.6f, size * 0.35f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = Color(0xFFCFD8DC),
                topLeft = Offset(bx + size * 0.35f, by + size * 0.35f),
                size = Size(size * 0.5f, size * 0.25f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            // Piled raw stone boulders in gathering yard
            drawCircle(color = Color(0xFF546E7A), radius = size * 0.22f, center = Offset(bx + bWidth - size * 0.55f, by + size * 0.85f))
            drawCircle(color = Color(0xFF78909C), radius = size * 0.16f, center = Offset(bx + bWidth - size * 0.72f, by + size * 0.72f))
            drawCircle(color = Color(0xFFB0BEC5), radius = size * 0.14f, center = Offset(bx + bWidth - size * 0.45f, by + size * 0.68f))

            // Mason anvil / stone carving block
            drawRoundRect(
                color = Color(0xFF37474F),
                topLeft = Offset(bx + size * 0.35f, by + size * 1.05f),
                size = Size(size * 0.5f, size * 0.35f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Timber canopy awning
            val awningPath = Path().apply {
                moveTo(bx + size * 0.15f, by + size * 0.2f)
                lineTo(bx + bWidth - size * 0.15f, by + size * 0.2f)
                lineTo(bx + bWidth - size * 0.25f, by + size * 0.48f)
                lineTo(bx + size * 0.25f, by + size * 0.48f)
                close()
            }
            drawPath(awningPath, color = Color(0xFF5D4037))
        }

        BuildingType.FORAGER_HUT -> {
            drawRoundRect(
                color = Color(0xFF795548),
                topLeft = Offset(bx + size * 0.2f, by + size * 0.3f),
                size = Size(bWidth - size * 0.4f, bHeight - size * 0.5f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Baskets of berries
            drawCircle(color = Color(0xFFD7CCC8), radius = size * 0.18f, center = Offset(bx + size * 0.6f, by + size * 1.1f))
            drawCircle(color = Color(0xFF8E24AA), radius = size * 0.1f, center = Offset(bx + size * 0.6f, by + size * 1.1f))
        }

        BuildingType.GRANARY -> {
            // Elevated storage
            drawRoundRect(
                color = Color(0xFF5D4037),
                topLeft = Offset(bx + size * 0.2f, by + size * 0.4f),
                size = Size(bWidth - size * 0.4f, bHeight - size * 0.5f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Roof
            val granaryRoof = Path().apply {
                moveTo(bx + bWidth * 0.5f, by + size * 0.1f)
                lineTo(bx + bWidth - size * 0.1f, by + size * 0.45f)
                lineTo(bx + size * 0.1f, by + size * 0.45f)
                close()
            }
            drawPath(granaryRoof, color = Color(0xFF8D6E63))
        }

        BuildingType.WELL -> {
            val center = Offset(bx + bWidth / 2, by + bHeight / 2)
            drawCircle(color = Color(0xFF757575), radius = size * 0.38f, center = center)
            drawCircle(color = Color(0xFF1E88E5), radius = size * 0.25f, center = center)
            // Wooden roof post
            drawLine(
                color = Color(0xFF5D4037),
                start = Offset(center.x - size * 0.25f, center.y),
                end = Offset(center.x + size * 0.25f, center.y),
                strokeWidth = 3.5f
            )
        }
    }
}

private fun DrawScope.drawVillager(
    v: Villager,
    vx: Float,
    vy: Float,
    size: Float,
    wave: Float,
    isSelected: Boolean
) {
    val charSize = size * 0.65f
    val centerX = vx + size * 0.5f
    val centerY = vy + size * 0.5f

    // Walking animation bob
    val isMoving = v.path.isNotEmpty()
    val bob = if (isMoving) (sin(wave * 6f) * 2.5f).toFloat() else 0f

    // Selection ring
    if (isSelected) {
        drawCircle(
            color = Color(0xFFFFA000),
            radius = charSize * 0.85f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3f)
        )
    }

    // Shadow
    drawOval(
        color = Color(0x44000000),
        topLeft = Offset(centerX - charSize * 0.35f, centerY + charSize * 0.2f),
        size = Size(charSize * 0.7f, charSize * 0.3f)
    )

    // Body / Tunic
    val tunicColor = Color(v.tunicColorHex)
    val bodyRadius = if (v.isChild) charSize * 0.24f else charSize * 0.32f
    val bodyCenter = Offset(centerX, centerY - bob)

    drawCircle(color = tunicColor, radius = bodyRadius, center = bodyCenter)

    // Head
    val headRadius = if (v.isChild) charSize * 0.20f else charSize * 0.25f
    val headCenter = Offset(centerX, bodyCenter.y - bodyRadius * 0.85f)
    drawCircle(color = Color(0xFFFFCC80), radius = headRadius, center = headCenter)

    // Hair / Hat (Elders have grey hair)
    val hairColor = if (v.isElder) Color(0xFFECEFF1) else if (v.isFemale) Color(0xFF5D4037) else Color(0xFF3E2723)
    drawArc(
        color = hairColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(headCenter.x - headRadius, headCenter.y - headRadius),
        size = Size(headRadius * 2, headRadius * 2)
    )

    // Carrying Item Indicator
    if (v.carryingType != CarryType.NONE && v.carryingAmount > 0) {
        val carryOffset = Offset(centerX + charSize * 0.32f, bodyCenter.y - charSize * 0.1f)
        when (v.carryingType) {
            CarryType.WOOD -> {
                drawRoundRect(
                    color = Color(0xFF795548),
                    topLeft = Offset(carryOffset.x - 6f, carryOffset.y - 12f),
                    size = Size(12f, 24f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }
            CarryType.FOOD -> {
                drawCircle(
                    color = Color(0xFFFBC02D),
                    radius = charSize * 0.16f,
                    center = carryOffset
                )
            }
            CarryType.STONE -> {
                drawCircle(
                    color = Color(0xFF9E9E9E),
                    radius = charSize * 0.16f,
                    center = carryOffset
                )
            }
            CarryType.NONE -> {}
        }
    }

    // Status bubble icon above head (Text rendering via native canvas)
    val bubbleIcon = when {
        v.hunger < 25f -> "⚠️"
        v.action == VillagerAction.SLEEPING -> "💤"
        v.action == VillagerAction.EATING -> "🍞"
        v.action == VillagerAction.HARVESTING -> "🌾"
        v.action == VillagerAction.CHOPPING -> "🪓"
        v.action == VillagerAction.MINING -> "⛏️"
        v.action == VillagerAction.BUILDING -> "🔨"
        v.action == VillagerAction.FORAGING -> "🫐"
        v.action == VillagerAction.TILLING -> "🌱"
        v.action == VillagerAction.RECREATING -> "🌸"
        v.action == VillagerAction.SOCIALIZING -> "💬"
        v.action == VillagerAction.PROCREATING -> "❤️"
        v.happiness >= 85f && (sin(wave * 2f + v.posX) > 0.7f) -> "✨"
        v.happiness < 25f -> "🌧️"
        else -> null
    }

    if (bubbleIcon != null) {
        val bubbleY = headCenter.y - headRadius - 16f
        // Draw small bubble backing
        drawCircle(
            color = Color(0xDDFFFFFF),
            radius = 12f,
            center = Offset(centerX, bubbleY)
        )
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textSize = 14f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(bubbleIcon, centerX, bubbleY + 5f, paint)
        }
    }
}

private fun DrawScope.drawAtmosphereOverlay(
    phase: DayPhase,
    progress: Float,
    buildings: List<Building>,
    panOffsetX: Float,
    panOffsetY: Float,
    tileSize: Float,
    viewSize: Size
) {
    when (phase) {
        DayPhase.DUSK -> {
            // Warm sunset tint
            drawRect(
                color = Color(0x33FF9800),
                topLeft = Offset.Zero,
                size = viewSize
            )
        }
        DayPhase.NIGHT -> {
            // Dark night blue filter
            drawRect(
                color = Color(0x990A1128),
                topLeft = Offset.Zero,
                size = viewSize
            )

            // Warm light halos around Town Hearth, Huts, Homesteads, and Plaza at night
            for (b in buildings) {
                if (b.type == BuildingType.TOWN_HEARTH ||
                    (b.type == BuildingType.COZY_HUT && b.isConstructed) ||
                    (b.type == BuildingType.FAMILY_HOMESTEAD && b.isConstructed) ||
                    (b.type == BuildingType.COMMUNITY_PLAZA && b.isConstructed)
                ) {
                    val hx = panOffsetX + (b.x + b.type.width * 0.5f) * tileSize
                    val hy = panOffsetY + (b.y + b.type.height * 0.5f) * tileSize
                    val radius = tileSize * 2.2f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x77FFA000), Color(0x22FFA000), Color(0x00000000)),
                            center = Offset(hx, hy),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(hx, hy)
                    )
                }
            }
        }
        DayPhase.DAWN -> {
            drawRect(
                color = Color(0x22FFB74D),
                topLeft = Offset.Zero,
                size = viewSize
            )
        }
        DayPhase.DAY -> {
            // Crisp natural daylight, no dark overlay
        }
    }
}

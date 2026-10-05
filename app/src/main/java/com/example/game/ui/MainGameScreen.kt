package com.example.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.engine.SimulationEngine
import com.example.game.model.BuildingType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class ActiveSheet {
    NONE,
    BUILD_CATALOG,
    JOB_ASSIGNMENT,
    VILLAGERS_ROSTER,
    COMMUNITY_GOALS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGameScreen(
    context: android.content.Context = LocalContext.current,
    engine: SimulationEngine = remember { SimulationEngine(context.applicationContext) },
    modifier: Modifier = Modifier
) {
    val gameState by engine.gameState.collectAsStateWithLifecycle()
    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var isCleanMode by remember { mutableStateOf(false) }

    // Back button handling: dismiss active sheet, inspector, or clean mode
    BackHandler(enabled = isCleanMode || activeSheet != ActiveSheet.NONE || gameState.selectedVillagerId != null || gameState.selectedBuildingId != null) {
        if (activeSheet != ActiveSheet.NONE) {
            activeSheet = ActiveSheet.NONE
        } else if (gameState.selectedVillagerId != null) {
            engine.selectVillager(null)
        } else if (gameState.selectedBuildingId != null) {
            engine.selectBuilding(null)
        } else if (isCleanMode) {
            isCleanMode = false
        }
    }

    // Game Simulation Tick Loop (Runs smoothly at ~30 FPS)
    LaunchedEffect(Unit) {
        var lastTime = System.currentTimeMillis()
        while (isActive) {
            val now = System.currentTimeMillis()
            val deltaSec = (now - lastTime) / 1000f
            lastTime = now

            // Cap delta to prevent huge jumps
            val safeDelta = deltaSec.coerceIn(0.01f, 0.1f)
            engine.tick(safeDelta)

            delay(33) // ~30 fps
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Interactive World Canvas (Pannable & Zoomable Outlanders style view, with double-tap clean mode toggle)
        WorldCanvas(
            gameState = gameState,
            onTileTap = { x, y ->
                if (!isCleanMode) {
                    engine.selectTile(x, y)
                }
            },
            onVillagerTap = { id ->
                if (!isCleanMode) {
                    engine.selectVillager(id)
                    activeSheet = ActiveSheet.NONE
                }
            },
            onDoubleTap = {
                // Double click toggles clean UI mode (removes/restores all tabs and bars)
                isCleanMode = !isCleanMode
                if (isCleanMode) {
                    activeSheet = ActiveSheet.NONE
                    engine.selectVillager(null)
                    engine.selectBuilding(null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top HUD Bar (Day clock, Speed controls, Resource pills, Events)
        AnimatedVisibility(
            visible = !isCleanMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopHudBar(
                gameState = gameState,
                onSetSpeed = { speed ->
                    engine.setGameSpeed(speed)
                }
            )
        }

        // 3. Pending Building Placement Banner
        val pendingType = gameState.pendingBuildType
        if (!isCleanMode && pendingType != null) {
            val canPlace = if (gameState.selectedTile != null) {
                engine.canPlaceBuilding(pendingType, gameState.selectedTile!!.first, gameState.selectedTile!!.second)
            } else false

            PendingBuildBanner(
                buildingType = pendingType,
                selectedTile = gameState.selectedTile,
                canPlace = canPlace,
                onConfirmPlace = {
                    gameState.selectedTile?.let { (x, y) ->
                        engine.placeBuilding(pendingType, x, y)
                    }
                },
                onCancelPlace = {
                    engine.setPendingBuildType(null)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            // 4. Bottom Action Dock (Bangun, Pekerjaan, Warga, Misi)
            AnimatedVisibility(
                visible = !isCleanMode &&
                    activeSheet == ActiveSheet.NONE &&
                    pendingType == null &&
                    gameState.selectedVillagerId == null &&
                    gameState.selectedBuildingId == null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomActionDock(
                    onOpenBuildCatalog = { activeSheet = ActiveSheet.BUILD_CATALOG },
                    onOpenJobAssignment = { activeSheet = ActiveSheet.JOB_ASSIGNMENT },
                    onOpenVillagersRoster = { activeSheet = ActiveSheet.VILLAGERS_ROSTER },
                    onOpenCommunityGoals = { activeSheet = ActiveSheet.COMMUNITY_GOALS }
                )
            }
        }

        // 5. Inspector Cards (When tapping villager or building)
        val selectedVillager = gameState.villagers.find { it.id == gameState.selectedVillagerId }
        val selectedBuilding = gameState.buildings.find { it.id == gameState.selectedBuildingId }

        AnimatedVisibility(
            visible = !isCleanMode && selectedVillager != null && activeSheet == ActiveSheet.NONE && pendingType == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedVillager?.let { v ->
                VillagerInspectorCard(
                    villager = v,
                    onAssignJob = { newJob ->
                        engine.assignJob(v.id, newJob)
                    },
                    onClose = {
                        engine.selectVillager(null)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = !isCleanMode && selectedBuilding != null && selectedVillager == null && activeSheet == ActiveSheet.NONE && pendingType == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedBuilding?.let { b ->
                BuildingInspectorCard(
                    building = b,
                    onClose = {
                        engine.selectBuilding(null)
                    }
                )
            }
        }

        // 6. Clean Mode Notification Pill
        AnimatedVisibility(
            visible = isCleanMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xD01E293B),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .clickable { isCleanMode = false }
                    .testTag("clean_mode_indicator")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🌿", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tampilan Bersih • Ketuk ganda di layar untuk menampilkan bilah",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 7. ModalBottomSheet with onDismissRequest: auto-dismisses when touching outside
        if (activeSheet != ActiveSheet.NONE && !isCleanMode) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    activeSheet = ActiveSheet.NONE
                },
                sheetState = sheetState,
                containerColor = Color(0xFFFFFDF8),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                scrimColor = Color(0x66000000),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                modifier = Modifier.testTag("game_modal_bottom_sheet")
            ) {
                when (activeSheet) {
                    ActiveSheet.BUILD_CATALOG -> {
                        BuildCatalogSheet(
                            inventory = gameState.inventory,
                            onSelectBuilding = { type ->
                                engine.setPendingBuildType(type)
                                activeSheet = ActiveSheet.NONE
                            },
                            onDismiss = { activeSheet = ActiveSheet.NONE }
                        )
                    }
                    ActiveSheet.JOB_ASSIGNMENT -> {
                        JobAssignmentSheet(
                            villagers = gameState.villagers,
                            buildings = gameState.buildings,
                            isAutoAssign = gameState.isAutoAssignEnabled,
                            jobPriorities = gameState.jobPriorities,
                            onToggleAutoAssign = { engine.toggleAutoAssign() },
                            onSetJobPriority = { job, prio -> engine.setJobPriority(job, prio) },
                            onAdjustJobCount = { job, count ->
                                engine.rebalanceJobs(job, count)
                            },
                            onDismiss = { activeSheet = ActiveSheet.NONE }
                        )
                    }
                    ActiveSheet.VILLAGERS_ROSTER -> {
                        VillagersRosterSheet(
                            villagers = gameState.villagers,
                            onSelectVillager = { id ->
                                engine.selectVillager(id)
                                activeSheet = ActiveSheet.NONE
                            },
                            onDismiss = { activeSheet = ActiveSheet.NONE }
                        )
                    }
                    ActiveSheet.COMMUNITY_GOALS -> {
                        CommunityGoalsSheet(
                            goals = gameState.goals,
                            events = gameState.events,
                            onDismiss = { activeSheet = ActiveSheet.NONE }
                        )
                    }
                    ActiveSheet.NONE -> {}
                }
            }
        }
    }
}

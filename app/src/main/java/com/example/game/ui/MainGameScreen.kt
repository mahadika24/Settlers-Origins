package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun MainGameScreen(
    engine: SimulationEngine = remember { SimulationEngine() },
    modifier: Modifier = Modifier
) {
    val gameState by engine.gameState.collectAsStateWithLifecycle()
    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }

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
        // 1. Interactive World Canvas (Pannable & Zoomable Outlanders style view)
        WorldCanvas(
            gameState = gameState,
            onTileTap = { x, y ->
                engine.selectTile(x, y)
            },
            onVillagerTap = { id ->
                engine.selectVillager(id)
                activeSheet = ActiveSheet.NONE
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top HUD Bar (Day clock, Speed controls, Resource pills, Events)
        TopHudBar(
            gameState = gameState,
            onSetSpeed = { speed ->
                engine.setGameSpeed(speed)
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Pending Building Placement Banner
        val pendingType = gameState.pendingBuildType
        if (pendingType != null) {
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
            if (activeSheet == ActiveSheet.NONE &&
                gameState.selectedVillagerId == null &&
                gameState.selectedBuildingId == null
            ) {
                BottomActionDock(
                    onOpenBuildCatalog = { activeSheet = ActiveSheet.BUILD_CATALOG },
                    onOpenJobAssignment = { activeSheet = ActiveSheet.JOB_ASSIGNMENT },
                    onOpenVillagersRoster = { activeSheet = ActiveSheet.VILLAGERS_ROSTER },
                    onOpenCommunityGoals = { activeSheet = ActiveSheet.COMMUNITY_GOALS },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // 5. Inspector Cards (When tapping villager or building)
        val selectedVillager = gameState.villagers.find { it.id == gameState.selectedVillagerId }
        val selectedBuilding = gameState.buildings.find { it.id == gameState.selectedBuildingId }

        AnimatedVisibility(
            visible = selectedVillager != null && activeSheet == ActiveSheet.NONE && pendingType == null,
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
            visible = selectedBuilding != null && selectedVillager == null && activeSheet == ActiveSheet.NONE && pendingType == null,
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

        // 6. Modal Sheets for Build Catalog, Jobs, Roster, and Goals
        AnimatedVisibility(
            visible = activeSheet == ActiveSheet.BUILD_CATALOG,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BuildCatalogSheet(
                inventory = gameState.inventory,
                onSelectBuilding = { type ->
                    engine.setPendingBuildType(type)
                    activeSheet = ActiveSheet.NONE
                },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }

        AnimatedVisibility(
            visible = activeSheet == ActiveSheet.JOB_ASSIGNMENT,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            JobAssignmentSheet(
                villagers = gameState.villagers,
                onAdjustJobCount = { job, count ->
                    engine.rebalanceJobs(job, count)
                },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }

        AnimatedVisibility(
            visible = activeSheet == ActiveSheet.VILLAGERS_ROSTER,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VillagersRosterSheet(
                villagers = gameState.villagers,
                onSelectVillager = { id ->
                    engine.selectVillager(id)
                    activeSheet = ActiveSheet.NONE
                },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }

        AnimatedVisibility(
            visible = activeSheet == ActiveSheet.COMMUNITY_GOALS,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CommunityGoalsSheet(
                goals = gameState.goals,
                events = gameState.events,
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
    }
}

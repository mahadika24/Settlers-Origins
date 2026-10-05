package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.DayPhase
import com.example.game.model.TimeSystem
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WorldTile
import com.example.game.pathfinding.GridPathfinder

/**
 * Modular Housing System
 * Manages residential assignment, family households, homelessness, and daily indoor/outdoor routines.
 */
object HousingSystem {

    /**
     * Synchronizes households, assigns homes to families & individuals,
     * and updates day/night indoor/outdoor states.
     */
    fun updateHousingRoutines(
        villagers: List<Villager>,
        buildings: List<Building>,
        timeSystem: TimeSystem,
        tiles: Array<Array<WorldTile>>,
        resources: List<com.example.game.model.NaturalResource>,
        dt: Float
    ): Pair<List<Villager>, List<Building>> {
        val constructedHomes = buildings.filter { it.type.housingCapacity > 0 && it.isConstructed }
        val townHearth = buildings.find { it.type == BuildingType.TOWN_HEARTH }

        // 1. Maintain Family Households & Check Valid Homes
        val updatedVillagers = villagers.map { v ->
            var homeId = v.homeBuildingId

            // If current home no longer exists or isn't constructed, clear it
            if (homeId != null && constructedHomes.none { it.id == homeId }) {
                homeId = null
            }

            // If baby or child, match parents' home if available
            if (homeId == null && (v.isBaby || v.isChild) && v.parentIds.isNotEmpty()) {
                val parentWithHome = villagers.find { v.parentIds.contains(it.id) && it.homeBuildingId != null }
                if (parentWithHome != null && constructedHomes.any { it.id == parentWithHome.homeBuildingId }) {
                    homeId = parentWithHome.homeBuildingId
                }
            }

            // If adult with partner, match partner's home if available
            if (homeId == null && v.isAdult && v.partnerId != null) {
                val partner = villagers.find { it.id == v.partnerId && it.homeBuildingId != null }
                if (partner != null && constructedHomes.any { it.id == partner.homeBuildingId }) {
                    homeId = partner.homeBuildingId
                }
            }

            // If still homeless, seek an available home with vacant capacity
            if (homeId == null && constructedHomes.isNotEmpty()) {
                val availableHome = constructedHomes.firstOrNull { home ->
                    val occupantCount = villagers.count { it.homeBuildingId == home.id }
                    occupantCount < home.type.housingCapacity
                }
                if (availableHome != null) {
                    homeId = availableHome.id
                }
            }

            v.copy(homeBuildingId = homeId)
        }.toMutableList()

        // 2. Daily Routine & Physical Entry/Exit Transitions
        for (i in updatedVillagers.indices) {
            val v = updatedVillagers[i]
            val home = constructedHomes.find { it.id == v.homeBuildingId } ?: townHearth

            when (timeSystem.phase) {
                DayPhase.DAWN, DayPhase.DAY -> {
                    // Morning: Wake up and exit home to outdoor world
                    if (v.isInHome) {
                        val exitX = home?.x?.toFloat() ?: v.posX
                        val exitY = ((home?.y ?: 0) + (home?.type?.height ?: 1)).toFloat()
                        updatedVillagers[i] = v.copy(
                            isInHome = false,
                            action = VillagerAction.IDLE,
                            posX = exitX,
                            posY = exitY,
                            statusMessage = "Bangun pagi dan keluar memulai hari"
                        )
                    }
                }
                DayPhase.DUSK -> {
                    // Evening: Wrap up tasks and begin walking home
                    if (!v.isInHome && home != null && v.action != VillagerAction.WALKING_TO && v.action != VillagerAction.DELIVERING) {
                        val distToHome = Math.abs(v.posX - home.x) + Math.abs(v.posY - home.y)
                        if (distToHome > 1.4f && v.path.isEmpty()) {
                            val path = GridPathfinder.findPath(
                                v.posX.toInt(), v.posY.toInt(),
                                home.x, home.y,
                                tiles, buildings, resources
                            )
                            updatedVillagers[i] = v.copy(
                                action = VillagerAction.WALKING_TO,
                                path = path,
                                statusMessage = "Senja tiba, pulang menuju pondok"
                            )
                        }
                    }
                }
                DayPhase.NIGHT -> {
                    // Night: Must actually enter house and sleep
                    if (home != null) {
                        val distToHome = Math.abs(v.posX - home.x) + Math.abs(v.posY - home.y)
                        if (distToHome <= 1.5f) {
                            updatedVillagers[i] = v.copy(
                                isInHome = true,
                                action = VillagerAction.SLEEPING,
                                path = emptyList(),
                                energy = (v.energy + dt * 10f).coerceAtMost(100f),
                                statusMessage = "Tidur nyenyak di dalam peraduan hangat"
                            )
                        } else if (!v.isInHome && v.path.isEmpty()) {
                            // Pathfind to home entrance
                            val path = GridPathfinder.findPath(
                                v.posX.toInt(), v.posY.toInt(),
                                home.x, home.y,
                                tiles, buildings, resources
                            )
                            updatedVillagers[i] = v.copy(
                                action = VillagerAction.WALKING_TO,
                                path = path,
                                statusMessage = "Malam gelap, bergegas masuk rumah"
                            )
                        }
                    } else {
                        // Homeless: cannot enter home, sleeps outdoors
                        updatedVillagers[i] = v.copy(
                            isInHome = false,
                            action = VillagerAction.SLEEPING,
                            energy = (v.energy + dt * 5f).coerceAtMost(100f),
                            statusMessage = "Tidur kedinginan di luar tanpa rumah"
                        )
                    }
                }
            }
        }

        // 3. Update Building residentIds lists
        val updatedBuildings = buildings.map { b ->
            if (b.type.housingCapacity > 0) {
                val currentResidents = updatedVillagers.filter { it.homeBuildingId == b.id }.map { it.id }
                b.copy(residentIds = currentResidents)
            } else b
        }

        return Pair(updatedVillagers, updatedBuildings)
    }
}

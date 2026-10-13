package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingStatus
import com.example.game.model.BuildingType
import com.example.game.model.DayPhase
import com.example.game.model.TimeSystem
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WorldTile
import com.example.game.pathfinding.GridPathfinder

/**
 * Modular Housing System
 * Manages residential assignment, family households, homelessness,
 * and physical entrance-based house entry and exit routines.
 */
object HousingSystem {

    /**
     * Synchronizes households, assigns homes to families & individuals,
     * and updates day/night entrance-based indoor/outdoor states.
     */
    fun updateHousingRoutines(
        villagers: List<Villager>,
        buildings: List<Building>,
        timeSystem: TimeSystem,
        tiles: Array<Array<WorldTile>>,
        resources: List<com.example.game.model.NaturalResource>,
        dt: Float
    ): Pair<List<Villager>, List<Building>> {
        // Only fully constructed and non-demolishing residential buildings can house villagers
        val availableHomes = buildings.filter {
            it.type.housingCapacity > 0 &&
            it.isConstructed &&
            it.status == BuildingStatus.COMPLETED
        }
        val townHearth = buildings.find {
            it.type == BuildingType.TOWN_HEARTH &&
            it.isConstructed &&
            it.status == BuildingStatus.COMPLETED
        }

        // 1. Maintain Family Households & Invalidate Unconstructed/Demolished Homes
        val currentOccupancy = availableHomes.associate { home ->
            home.id to villagers.count { v -> v.homeBuildingId == home.id && availableHomes.any { h -> h.id == v.homeBuildingId } }
        }.toMutableMap()

        val updatedVillagers = villagers.map { v ->
            var homeId = v.homeBuildingId
            var inHome = v.isInHome
            var curX = v.posX
            var curY = v.posY
            var act = v.action

            // If current home no longer exists, is unconstructed, or is demolishing -> evict immediately!
            if (homeId != null && availableHomes.none { it.id == homeId }) {
                if (inHome) {
                    val oldHome = buildings.find { it.id == homeId }
                    val exitPos = oldHome?.getEntranceTile(tiles = tiles) ?: Pair(curX.toInt(), curY.toInt())
                    curX = exitPos.first.toFloat()
                    curY = exitPos.second.toFloat()
                    inHome = false
                    act = VillagerAction.IDLE
                }
                homeId = null
            }

            // If baby or child, seek parents' home if capacity allows
            if (homeId == null && (v.isBaby || v.isChild) && v.parentIds.isNotEmpty()) {
                val parentWithHome = villagers.find { v.parentIds.contains(it.id) && it.homeBuildingId != null }
                if (parentWithHome != null) {
                    val parentHome = availableHomes.find { it.id == parentWithHome.homeBuildingId }
                    if (parentHome != null && (currentOccupancy[parentHome.id] ?: 0) < parentHome.type.housingCapacity) {
                        homeId = parentHome.id
                        currentOccupancy[parentHome.id] = (currentOccupancy[parentHome.id] ?: 0) + 1
                    }
                }
            }

            // If adult with partner, seek partner's home if capacity allows
            if (homeId == null && v.isAdult && v.partnerId != null) {
                val partner = villagers.find { it.id == v.partnerId && it.homeBuildingId != null }
                if (partner != null) {
                    val partnerHome = availableHomes.find { it.id == partner.homeBuildingId }
                    if (partnerHome != null && (currentOccupancy[partnerHome.id] ?: 0) < partnerHome.type.housingCapacity) {
                        homeId = partnerHome.id
                        currentOccupancy[partnerHome.id] = (currentOccupancy[partnerHome.id] ?: 0) + 1
                    }
                }
            }

            // If still homeless, seek an available home respecting strict housing capacity
            if (homeId == null && availableHomes.isNotEmpty()) {
                val availableHome = availableHomes.firstOrNull { home ->
                    (currentOccupancy[home.id] ?: 0) < home.type.housingCapacity
                }
                if (availableHome != null) {
                    homeId = availableHome.id
                    currentOccupancy[availableHome.id] = (currentOccupancy[availableHome.id] ?: 0) + 1
                }
            }

            v.copy(
                homeBuildingId = homeId,
                isInHome = inHome,
                posX = curX,
                posY = curY,
                action = act
            )
        }.toMutableList()

        // 2. Physical Entrance-based Entry / Exit Transitions
        for (i in updatedVillagers.indices) {
            val v = updatedVillagers[i]
            val assignedHome = availableHomes.find { it.id == v.homeBuildingId }

            if (assignedHome != null) {
                val (entranceX, entranceY) = assignedHome.getEntranceTile(tiles = tiles)

                when (timeSystem.phase) {
                    DayPhase.DAWN, DayPhase.DAY -> {
                        // Morning: Exit through the valid house entrance tile
                        if (v.isInHome) {
                            if (v.action == VillagerAction.SLEEPING || v.action == VillagerAction.INSIDE_HOME) {
                                // Step 1: Transition to LEAVING_HOME (still inside)
                                updatedVillagers[i] = v.copy(
                                    action = VillagerAction.LEAVING_HOME,
                                    statusMessage = "Membuka pintu dan melangkah keluar rumah"
                                )
                            } else if (v.action == VillagerAction.LEAVING_HOME) {
                                // Step 2: Step outside at entrance tile
                                updatedVillagers[i] = v.copy(
                                    isInHome = false,
                                    action = VillagerAction.IDLE,
                                    posX = entranceX.toFloat(),
                                    posY = entranceY.toFloat(),
                                    path = emptyList(),
                                    statusMessage = "Bangun pagi dan melangkah keluar memulai hari"
                                )
                            }
                        } else if (v.action == VillagerAction.ENTERING_HOME) {
                            // Caught outside at morning
                            updatedVillagers[i] = v.copy(
                                action = VillagerAction.IDLE,
                                isInHome = false
                            )
                        }
                    }
                    DayPhase.DUSK -> {
                        // Evening: Begin returning home to the entrance
                        if (!v.isInHome && v.action != VillagerAction.DELIVERING) {
                            val distToEntrance = Math.abs(v.posX - entranceX) + Math.abs(v.posY - entranceY)
                            if (distToEntrance > 0.6f) {
                                if (v.action != VillagerAction.RETURNING_HOME || v.path.isEmpty()) {
                                    val path = GridPathfinder.findPath(
                                        v.posX.toInt(), v.posY.toInt(),
                                        entranceX, entranceY,
                                        tiles, buildings, resources
                                    )
                                    updatedVillagers[i] = v.copy(
                                        action = VillagerAction.RETURNING_HOME,
                                        path = path,
                                        statusMessage = "Senja tiba, berjalan pulang menuju pintu rumah"
                                    )
                                }
                            } else {
                                // Reached doorstep at dusk
                                if (v.action != VillagerAction.ENTERING_HOME && v.action != VillagerAction.INSIDE_HOME) {
                                    updatedVillagers[i] = v.copy(
                                        action = VillagerAction.ENTERING_HOME,
                                        posX = entranceX.toFloat(),
                                        posY = entranceY.toFloat(),
                                        path = emptyList(),
                                        statusMessage = "Tiba di depan pintu, melangkah masuk ke rumah"
                                    )
                                } else if (v.action == VillagerAction.ENTERING_HOME) {
                                    updatedVillagers[i] = v.copy(
                                        isInHome = true,
                                        action = VillagerAction.INSIDE_HOME,
                                        posX = entranceX.toFloat(),
                                        posY = entranceY.toFloat(),
                                        path = emptyList(),
                                        statusMessage = "Berada di dalam rumah beristirahat"
                                    )
                                }
                            }
                        } else if (v.isInHome) {
                            updatedVillagers[i] = v.copy(
                                action = VillagerAction.INSIDE_HOME,
                                statusMessage = "Berada di dalam rumah menyambut malam"
                            )
                        }
                    }
                    DayPhase.NIGHT -> {
                        // Night: Physically navigate to entrance before entering
                        if (!v.isInHome) {
                            val distToEntrance = Math.abs(v.posX - entranceX) + Math.abs(v.posY - entranceY)
                            if (distToEntrance <= 0.6f) {
                                if (v.action != VillagerAction.ENTERING_HOME) {
                                    // Step 1: At entrance, transition to ENTERING_HOME
                                    updatedVillagers[i] = v.copy(
                                        action = VillagerAction.ENTERING_HOME,
                                        posX = entranceX.toFloat(),
                                        posY = entranceY.toFloat(),
                                        path = emptyList(),
                                        statusMessage = "Tiba di depan pintu, melangkah masuk ke dalam rumah"
                                    )
                                } else {
                                    // Step 2: Now physically inside house and sleeping
                                    updatedVillagers[i] = v.copy(
                                        isInHome = true,
                                        action = VillagerAction.SLEEPING,
                                        posX = entranceX.toFloat(),
                                        posY = entranceY.toFloat(),
                                        path = emptyList(),
                                        energy = (v.energy + dt * 10f).coerceAtMost(100f),
                                        statusMessage = "Tidur nyenyak di peraduan dalam rumah"
                                    )
                                }
                            } else if (v.action != VillagerAction.DELIVERING) {
                                if (v.action != VillagerAction.RETURNING_HOME || v.path.isEmpty()) {
                                    val path = GridPathfinder.findPath(
                                        v.posX.toInt(), v.posY.toInt(),
                                        entranceX, entranceY,
                                        tiles, buildings, resources
                                    )
                                    updatedVillagers[i] = v.copy(
                                        action = VillagerAction.RETURNING_HOME,
                                        path = path,
                                        statusMessage = "Malam gelap, bergegas pulang menuju pintu rumah"
                                    )
                                }
                            }
                        } else {
                            // Already inside home sleeping: recover energy
                            updatedVillagers[i] = v.copy(
                                action = VillagerAction.SLEEPING,
                                energy = (v.energy + dt * 10f).coerceAtMost(100f),
                                statusMessage = "Tidur nyenyak di peraduan dalam rumah"
                            )
                        }
                    }
                }
            } else {
                // Homeless: Cannot enter any house!
                var curAction = v.action
                if (curAction == VillagerAction.ENTERING_HOME || curAction == VillagerAction.RETURNING_HOME || curAction == VillagerAction.INSIDE_HOME) {
                    curAction = if (timeSystem.isNight) VillagerAction.SLEEPING else VillagerAction.IDLE
                }
                if (v.isInHome) {
                    updatedVillagers[i] = v.copy(
                        isInHome = false,
                        action = curAction,
                        statusMessage = "Meninggalkan bangunan yang tidak berlaku"
                    )
                } else if (timeSystem.isNight) {
                    updatedVillagers[i] = v.copy(
                        isInHome = false,
                        action = VillagerAction.SLEEPING,
                        energy = (v.energy + dt * 5f).coerceAtMost(100f),
                        statusMessage = "Tidur kedinginan di luar karena tidak memiliki rumah"
                    )
                } else if (curAction != v.action) {
                    updatedVillagers[i] = v.copy(action = curAction)
                }
            }
        }

        // 3. Synchronize Building residentIds lists
        val updatedBuildings = buildings.map { b ->
            if (b.type.housingCapacity > 0) {
                val currentResidents = updatedVillagers.filter { it.homeBuildingId == b.id }.map { it.id }
                b.copy(residentIds = currentResidents)
            } else b
        }

        return Pair(updatedVillagers, updatedBuildings)
    }
}

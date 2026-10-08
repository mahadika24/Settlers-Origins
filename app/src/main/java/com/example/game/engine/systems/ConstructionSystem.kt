package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingStatus
import com.example.game.model.BuildingType
import com.example.game.model.JobType
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WorldTile

/**
 * Modular Construction & Demolition System
 * Validates blueprints, placement footprints, manages construction progression,
 * and handles builder demolition work and resource salvage recycling.
 */
object ConstructionSystem {

    fun canPlaceBuilding(
        type: BuildingType,
        startX: Int,
        startY: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>,
        mapSize: Int = 22
    ): Boolean {
        if (startX + type.width > mapSize || startY + type.height > mapSize) return false
        if (startX < 0 || startY < 0) return false

        for (x in startX until (startX + type.width)) {
            for (y in startY until (startY + type.height)) {
                val tile = tiles[x][y]
                if (!tile.type.canBuild) return false
                if (tile.isOccupied) return false
                // Check if already covered by an existing building footprint
                if (buildings.any { b -> x in b.x until (b.x + b.type.width) && y in b.y until (b.y + b.type.height) }) {
                    return false
                }
                // Check if covered by solid rock
                if (resources.any { it.x == x && it.y == y && it.type == ResourceType.ROCK }) {
                    return false
                }
            }
        }
        return true
    }

    data class PlaceBuildingResult(
        val updatedTiles: Array<Array<WorldTile>>,
        val updatedBuildings: List<Building>,
        val updatedResources: List<NaturalResource>,
        val updatedInventory: VillageInventory,
        val newBuilding: Building
    )

    fun placeBuilding(
        type: BuildingType,
        startX: Int,
        startY: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>,
        inventory: VillageInventory,
        mapSize: Int = 22
    ): PlaceBuildingResult? {
        if (!canPlaceBuilding(type, startX, startY, tiles, buildings, resources, mapSize)) {
            return null
        }
        if (inventory.wood < type.woodCost || inventory.stone < type.stoneCost) {
            return null
        }

        val updatedInventory = inventory.copy(
            wood = inventory.wood - type.woodCost,
            stone = inventory.stone - type.stoneCost
        )

        val newTiles = Array(mapSize) { r ->
            Array(mapSize) { c ->
                val old = tiles[r][c]
                if (r in startX until (startX + type.width) && c in startY until (startY + type.height)) {
                    old.copy(isOccupied = true)
                } else old
            }
        }

        val newResources = resources.filterNot {
            it.x in startX until (startX + type.width) && it.y in startY until (startY + type.height)
        }

        val newBuilding = Building(
            type = type,
            x = startX,
            y = startY,
            isConstructed = false,
            constructionProgress = 0f,
            status = BuildingStatus.UNDER_CONSTRUCTION,
            demolitionProgress = 0f,
            deliveredWood = type.woodCost,
            deliveredStone = type.stoneCost
        )

        return PlaceBuildingResult(
            updatedTiles = newTiles,
            updatedBuildings = buildings + newBuilding,
            updatedResources = newResources,
            updatedInventory = updatedInventory,
            newBuilding = newBuilding
        )
    }

    /**
     * Advances construction progress on an unconstructed building.
     */
    fun advanceBuildingProgress(
        buildingId: String,
        progressDelta: Float,
        buildings: List<Building>
    ): Pair<List<Building>, Boolean> {
        var completedNow = false
        val updated = buildings.map { b ->
            if (b.id == buildingId && (!b.isConstructed || b.status == BuildingStatus.UNDER_CONSTRUCTION)) {
                val newProgress = b.constructionProgress + progressDelta
                if (newProgress >= 100f) {
                    completedNow = true
                    b.copy(
                        isConstructed = true,
                        constructionProgress = 100f,
                        status = BuildingStatus.COMPLETED
                    )
                } else {
                    b.copy(constructionProgress = newProgress)
                }
            } else b
        }
        return Pair(updated, completedNow)
    }

    fun canDemolishBuilding(building: Building): Boolean {
        return building.status == BuildingStatus.COMPLETED &&
                building.type != BuildingType.TOWN_HEARTH // Protected settlement core
    }

    /**
     * Marks a completed building for demolition by builders.
     * Evicts residents and releases assigned workers safely.
     */
    fun markForDemolition(
        buildingId: String,
        buildings: List<Building>,
        villagers: List<Villager>
    ): Pair<List<Building>, List<Villager>>? {
        val target = buildings.find { it.id == buildingId } ?: return null
        if (!canDemolishBuilding(target)) return null

        val updatedBuildings = buildings.map { b ->
            if (b.id == buildingId) {
                b.copy(
                    status = BuildingStatus.DEMOLISHING,
                    demolitionProgress = 0f,
                    residentIds = emptyList(),
                    assignedWorkerIds = emptyList()
                )
            } else b
        }

        val updatedVillagers = villagers.map { v ->
            var updatedV = v
            // Evict if resident
            if (updatedV.homeBuildingId == buildingId) {
                updatedV = updatedV.copy(
                    homeBuildingId = null,
                    isInHome = false,
                    housingNeed = (updatedV.housingNeed - 20f).coerceAtLeast(0f),
                    statusMessage = "Kehilangan rumah karena bangunan dibongkar"
                )
            }
            // Release if worker
            if (updatedV.assignedBuildingId == buildingId) {
                updatedV = updatedV.copy(
                    job = JobType.UNASSIGNED,
                    assignedBuildingId = null,
                    action = if (updatedV.carryingAmount > 0) updatedV.action else VillagerAction.IDLE,
                    statusMessage = "Tempat kerja dibongkar, kembali ke warga bebas"
                )
            }
            updatedV
        }

        return Pair(updatedBuildings, updatedVillagers)
    }

    data class DemolitionTickResult(
        val updatedBuildings: List<Building>,
        val updatedInventory: VillageInventory,
        val updatedTiles: Array<Array<WorldTile>>,
        val isDestroyed: Boolean,
        val woodSalvaged: Int = 0,
        val stoneSalvaged: Int = 0,
        val demolishedBuilding: Building? = null
    )

    /**
     * Advances demolition progress. Once 100% is reached, removes building,
     * frees footprint tiles, and deposits salvage into inventory.
     */
    fun advanceDemolitionProgress(
        buildingId: String,
        progressDelta: Float,
        buildings: List<Building>,
        inventory: VillageInventory,
        tiles: Array<Array<WorldTile>>,
        mapSize: Int = 22
    ): DemolitionTickResult {
        val target = buildings.find { it.id == buildingId && it.status == BuildingStatus.DEMOLISHING }
            ?: return DemolitionTickResult(buildings, inventory, tiles, false)

        val newProgress = target.demolitionProgress + progressDelta

        if (newProgress >= 100f) {
            // Demolition Complete!
            val updatedBuildings = buildings.filter { it.id != buildingId }

            // Free occupied footprint tiles
            val newTiles = Array(mapSize) { r ->
                Array(mapSize) { c ->
                    val old = tiles[r][c]
                    if (r in target.x until (target.x + target.type.width) && c in target.y until (target.y + target.type.height)) {
                        old.copy(isOccupied = false, buildingId = null)
                    } else old
                }
            }

            // Calculate deterministic salvage strictly less than original construction cost
            val woodSalvage = target.salvageWood
            val stoneSalvage = target.salvageStone

            val newInventory = inventory.copy(
                wood = (inventory.wood + woodSalvage).coerceAtMost(inventory.maxWood),
                stone = (inventory.stone + stoneSalvage).coerceAtMost(inventory.maxStone)
            )

            return DemolitionTickResult(
                updatedBuildings = updatedBuildings,
                updatedInventory = newInventory,
                updatedTiles = newTiles,
                isDestroyed = true,
                woodSalvaged = woodSalvage,
                stoneSalvaged = stoneSalvage,
                demolishedBuilding = target
            )
        } else {
            val updatedBuildings = buildings.map { b ->
                if (b.id == buildingId) b.copy(demolitionProgress = newProgress) else b
            }
            return DemolitionTickResult(updatedBuildings, inventory, tiles, false)
        }
    }
}

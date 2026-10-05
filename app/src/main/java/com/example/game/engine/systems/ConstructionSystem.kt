package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.VillageInventory
import com.example.game.model.WorldTile

/**
 * Modular Construction System
 * Validates blueprints, placement footprints, builds construction sites,
 * and advances progress to completion without unsafe casts.
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
     * Advances progress on an unconstructed building.
     * Guaranteed pure functional transformation: NO UNSAFE CASTS!
     */
    fun advanceBuildingProgress(
        buildingId: String,
        progressDelta: Float,
        buildings: List<Building>
    ): Pair<List<Building>, Boolean> {
        var completedNow = false
        val updated = buildings.map { b ->
            if (b.id == buildingId && !b.isConstructed) {
                val newProgress = b.constructionProgress + progressDelta
                if (newProgress >= 100f) {
                    completedNow = true
                    b.copy(isConstructed = true, constructionProgress = 100f)
                } else {
                    b.copy(constructionProgress = newProgress)
                }
            } else b
        }
        return Pair(updated, completedNow)
    }
}

package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.TileType
import com.example.game.model.WeatherType
import com.example.game.model.WorldTile
import kotlin.random.Random

/**
 * Modular Nature System
 * Handles growth of vegetation, natural tree reproduction, rock replenish, and berry regrowth.
 */
object NatureSystem {

    /**
     * Updates natural resource lifecycle and regrowth.
     */
    fun updateNature(
        resources: List<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        weather: WeatherType,
        dt: Float,
        mapSize: Int = 22
    ): List<NaturalResource> {
        val result = resources.toMutableList()

        // 1. Advance Growth of Existing Resources
        for (i in result.indices) {
            val res = result[i]
            if (res.type == ResourceType.TREE && res.growthStage < 2) {
                val growthBoost = weather.cropGrowthModifier
                val newProgress = res.growthProgress + (dt * 3.5f * growthBoost)
                if (newProgress >= 100f) {
                    val nextStage = res.growthStage + 1
                    val newAmount = if (nextStage == 1) 6 else 12
                    result[i] = res.copy(
                        growthStage = nextStage,
                        growthProgress = 0f,
                        amount = newAmount,
                        maxAmount = 12
                    )
                } else {
                    result[i] = res.copy(growthProgress = newProgress)
                }
            } else if (res.type == ResourceType.BERRY_BUSH && res.amount < res.maxAmount) {
                // Berry bushes naturally produce berries over time
                val newProgress = res.growthProgress + (dt * 4.0f)
                if (newProgress >= 100f) {
                    result[i] = res.copy(
                        amount = (res.amount + 1).coerceAtMost(res.maxAmount),
                        growthProgress = 0f
                    )
                } else {
                    result[i] = res.copy(growthProgress = newProgress)
                }
            }
        }

        // 2. Natural Tree Regrowth from Mature Trees (Seed dispersal on empty valid grass tiles)
        val treeCount = result.count { it.type == ResourceType.TREE }
        if (treeCount < 35 && Random.nextFloat() < 0.25f) {
            val matureTrees = result.filter { it.type == ResourceType.TREE && it.growthStage == 2 }
            if (matureTrees.isNotEmpty()) {
                val parentTree = matureTrees.random()
                val offset = listOf(
                    Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1),
                    Pair(1, 1), Pair(-1, -1), Pair(-2, 0), Pair(0, 2)
                ).random()
                val nx = (parentTree.x + offset.first).coerceIn(1, mapSize - 2)
                val ny = (parentTree.y + offset.second).coerceIn(1, mapSize - 2)

                val tile = tiles[nx][ny]
                val isOccupiedByBuilding = buildings.any { b ->
                    nx in b.x until (b.x + b.type.width) && ny in b.y until (b.y + b.type.height)
                }
                val hasExistingResource = result.any { it.x == nx && it.y == ny }

                if (tile.type == TileType.GRASS && !tile.isOccupied && !isOccupiedByBuilding && !hasExistingResource) {
                    result.add(
                        NaturalResource(
                            type = ResourceType.TREE,
                            x = nx,
                            y = ny,
                            amount = 3,
                            maxAmount = 12,
                            growthStage = 0,
                            growthProgress = 0f
                        )
                    )
                }
            }
        }

        // 3. Natural Rock Replenishment near Quarries and Rock Outcrops
        val rockCount = result.count { it.type == ResourceType.ROCK }
        if (rockCount < 8 && Random.nextFloat() < 0.15f) {
            val existingRocks = result.filter { it.type == ResourceType.ROCK }
            val nearX = if (existingRocks.isNotEmpty()) existingRocks.random().x else 16
            val nearY = if (existingRocks.isNotEmpty()) existingRocks.random().y else 12
            val offset = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)).random()
            val nx = (nearX + offset.first).coerceIn(1, mapSize - 2)
            val ny = (nearY + offset.second).coerceIn(1, mapSize - 2)

            val tile = tiles[nx][ny]
            val isOccupied = tile.isOccupied || buildings.any { b ->
                nx in b.x until (b.x + b.type.width) && ny in b.y until (b.y + b.type.height)
            } || result.any { it.x == nx && it.y == ny }

            if (tile.type.canBuild && !isOccupied) {
                result.add(
                    NaturalResource(
                        type = ResourceType.ROCK,
                        x = nx,
                        y = ny,
                        amount = 16,
                        maxAmount = 20,
                        growthStage = 2,
                        growthProgress = 100f
                    )
                )
            }
        }

        return result
    }
}

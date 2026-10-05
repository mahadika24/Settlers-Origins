package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.WeatherType

/**
 * Modular Farming System
 * Manages farm preparation, planting, crop growth cycles (Wheat & Pumpkin),
 * weather & well acceleration, and harvesting.
 */
object FarmingSystem {

    /**
     * Advances crop growth for all tilled fields in the village.
     * Guaranteed pure functional transformation: NO UNSAFE CASTS!
     */
    fun updateCropGrowth(
        buildings: List<Building>,
        weather: WeatherType,
        dt: Float
    ): List<Building> {
        val wells = buildings.filter { it.type == BuildingType.WELL && it.isConstructed }

        return buildings.map { building ->
            if (!building.isConstructed) {
                building
            } else if (building.type == BuildingType.WHEAT_FIELD || building.type == BuildingType.PUMPKIN_PATCH) {
                if (building.isTilled && building.cropGrowth < 100f) {
                    val hasWellBonus = wells.any { w ->
                        val dist = Math.abs(w.x - building.x) + Math.abs(w.y - building.y)
                        dist <= 4
                    }
                    val baseGrowthRate = if (hasWellBonus) 4.5f else 3.0f
                    val effectiveGrowthRate = baseGrowthRate * weather.cropGrowthModifier
                    val newGrowth = (building.cropGrowth + dt * effectiveGrowthRate).coerceAtMost(100f)
                    building.copy(cropGrowth = newGrowth)
                } else {
                    building
                }
            } else {
                building
            }
        }
    }

    /**
     * Tills an untilled farm field.
     */
    fun tillField(
        fieldId: String,
        buildings: List<Building>
    ): List<Building> {
        return buildings.map { b ->
            if (b.id == fieldId && (b.type == BuildingType.WHEAT_FIELD || b.type == BuildingType.PUMPKIN_PATCH)) {
                b.copy(isTilled = true, cropGrowth = 10f)
            } else b
        }
    }

    /**
     * Harvests mature crops from a field.
     * Returns Pair of updated buildings list and harvested food quantity.
     */
    fun harvestField(
        fieldId: String,
        buildings: List<Building>
    ): Pair<List<Building>, Int> {
        var harvestedAmount = 0
        val updated = buildings.map { b ->
            if (b.id == fieldId && (b.type == BuildingType.WHEAT_FIELD || b.type == BuildingType.PUMPKIN_PATCH)) {
                harvestedAmount = if (b.type == BuildingType.PUMPKIN_PATCH) 10 else 8
                b.copy(cropGrowth = 0f, isTilled = true)
            } else b
        }
        return Pair(updated, harvestedAmount)
    }
}

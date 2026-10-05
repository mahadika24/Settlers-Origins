package com.example.game.engine.systems

import com.example.game.model.Villager
import com.example.game.model.VillagerAction

/**
 * Modular Needs System
 * Updates continuous needs (Hunger, Housing, Recreation, Socialization, Energy),
 * and computes dynamic happiness & productivity multipliers.
 */
object NeedsSystem {

    /**
     * Updates needs metrics for a villager for a given simulation delta time [dt].
     */
    fun updateVillagerNeeds(
        villager: Villager,
        hasNearbyVillager: Boolean,
        dt: Float
    ): Villager {
        var v = villager

        // 1. Hunger Need (continuous float decay)
        val hungerRate = when {
            v.isBaby -> 0.18f
            v.isChild -> 0.24f
            v.isElder -> 0.30f
            else -> 0.38f
        }
        val newHunger = (v.hunger - dt * hungerRate).coerceIn(0f, 100f)

        // 2. Housing Need (rises when housed, decays when homeless)
        val newHousingNeed = if (v.homeBuildingId != null) {
            (v.housingNeed + dt * 2.5f).coerceAtMost(100f)
        } else {
            (v.housingNeed - dt * 1.8f).coerceAtLeast(0f)
        }

        // 3. Recreation Need (restored by recreating in park, otherwise decays)
        val newRecNeed = if (v.action == VillagerAction.RECREATING) {
            (v.recreationNeed + dt * 22f).coerceAtMost(100f)
        } else {
            (v.recreationNeed - dt * 0.16f).coerceAtLeast(0f)
        }

        // 4. Social Need (restored by socializing or proximity to others)
        var newSocialNeed = if (v.action == VillagerAction.SOCIALIZING) {
            (v.socialNeed + dt * 22f).coerceAtMost(100f)
        } else {
            (v.socialNeed - dt * 0.18f).coerceAtLeast(0f)
        }

        if (hasNearbyVillager) {
            newSocialNeed = (newSocialNeed + dt * 1.2f).coerceAtMost(100f)
        }

        // 5. Energy (recovers when sleeping, otherwise slowly drains during daytime activity)
        val newEnergy = if (v.action == VillagerAction.SLEEPING) {
            (v.energy + dt * 10f).coerceAtMost(100f)
        } else {
            (v.energy - dt * 0.15f).coerceAtLeast(0f)
        }

        return v.copy(
            hunger = newHunger,
            housingNeed = newHousingNeed,
            recreationNeed = newRecNeed,
            socialNeed = newSocialNeed,
            energy = newEnergy
        )
    }
}

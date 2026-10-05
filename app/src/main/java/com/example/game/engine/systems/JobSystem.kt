package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.CarryType
import com.example.game.model.JobPriority
import com.example.game.model.JobType
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction

/**
 * Modular Job & Workforce Balancing System
 * Calculates job capacities, manages manual worker allocation (+/-),
 * prioritizes assignments, and executes safe workforce transitions.
 */
object JobSystem {

    /**
     * Calculates current job capacities across the settlement based on constructed buildings.
     */
    fun calculateJobCapacities(buildings: List<Building>): Map<JobType, Int> {
        val farmCap = buildings.count {
            (it.type == BuildingType.WHEAT_FIELD || it.type == BuildingType.PUMPKIN_PATCH) && it.isConstructed
        } * 2
        val woodcutterCap = buildings.count {
            it.type == BuildingType.WOODCUTTER_CAMP && it.isConstructed
        } * 2
        val minerCap = buildings.count {
            it.type == BuildingType.STONE_QUARRY && it.isConstructed
        } * 2
        val foragerCap = buildings.count {
            it.type == BuildingType.FORAGER_HUT && it.isConstructed
        } * 2
        val unbuiltCount = buildings.count { !it.isConstructed }
        val builderCap = maxOf(unbuiltCount * 2, 2)

        return mapOf(
            JobType.FARMER to farmCap,
            JobType.WOODCUTTER to woodcutterCap,
            JobType.MINER to minerCap,
            JobType.FORAGER to foragerCap,
            JobType.BUILDER to builderCap,
            JobType.UNASSIGNED to 999
        )
    }

    /**
     * Manually adjusts the number of workers assigned to [job].
     * Respects job capacity and ensures safe transitions for workers carrying goods.
     */
    fun rebalanceJobCount(
        job: JobType,
        targetCount: Int,
        villagers: List<Villager>,
        buildings: List<Building>
    ): List<Villager> {
        val capacities = calculateJobCapacities(buildings)
        val cap = capacities[job] ?: 2
        val clampedTarget = targetCount.coerceIn(0, cap)

        val ableWorkers = villagers.filter { it.canWork }
        val currentWithJob = ableWorkers.filter { it.job == job }
        val diff = clampedTarget - currentWithJob.size

        if (diff == 0) return villagers

        val updated = villagers.toMutableList()

        if (diff > 0) {
            // Add workers: prioritize unassigned workers first, then other jobs with lower priority
            var added = 0
            val candidates = updated.filter { it.canWork && it.job == JobType.UNASSIGNED } +
                    updated.filter { it.canWork && it.job != job && it.carryingAmount == 0 }

            for (c in candidates) {
                if (added >= diff) break
                val idx = updated.indexOfFirst { it.id == c.id }
                if (idx != -1) {
                    // Safe transition: keep carrying goods if any, clear wandering path
                    val currentV = updated[idx]
                    val newAction = if (currentV.carryingAmount > 0) currentV.action else VillagerAction.IDLE
                    val newPath = if (currentV.carryingAmount > 0) currentV.path else emptyList()

                    updated[idx] = currentV.copy(
                        job = job,
                        action = newAction,
                        path = newPath,
                        statusMessage = "Beralih tugas menjadi ${job.title}"
                    )
                    added++
                }
            }
        } else {
            // Remove workers: return to UNASSIGNED pool
            var removed = 0
            val toRemove = -diff
            // Prefer workers not currently carrying critical deliveries
            val sortedWorkers = currentWithJob.sortedBy { if (it.carryingAmount > 0) 1 else 0 }

            for (w in sortedWorkers) {
                if (removed >= toRemove) break
                val idx = updated.indexOfFirst { it.id == w.id }
                if (idx != -1) {
                    val currentV = updated[idx]
                    val newAction = if (currentV.carryingAmount > 0) currentV.action else VillagerAction.IDLE
                    val newPath = if (currentV.carryingAmount > 0) currentV.path else emptyList()

                    updated[idx] = currentV.copy(
                        job = JobType.UNASSIGNED,
                        action = newAction,
                        path = newPath,
                        statusMessage = "Menjadi Warga Bebas"
                    )
                    removed++
                }
            }
        }

        return updated
    }

    /**
     * Auto-assigns available workers according to community needs and job priorities.
     */
    fun performAutoAssignment(
        villagers: List<Villager>,
        buildings: List<Building>,
        inventory: VillageInventory,
        jobPriorities: Map<JobType, JobPriority>
    ): List<Villager> {
        val capacities = calculateJobCapacities(buildings)
        val unassignedWorkers = villagers.filter { it.canWork && it.job == JobType.UNASSIGNED }
        if (unassignedWorkers.isEmpty()) return villagers

        val updated = villagers.toMutableList()

        // Prioritize jobs: Sort jobs by player priority and urgent resource shortages
        val orderedJobs = listOf(
            JobType.FARMER,
            JobType.FORAGER,
            JobType.BUILDER,
            JobType.WOODCUTTER,
            JobType.MINER
        ).sortedByDescending { job ->
            val userPrioScore = when (jobPriorities[job] ?: JobPriority.MEDIUM) {
                JobPriority.HIGH -> 300
                JobPriority.MEDIUM -> 200
                JobPriority.LOW -> 100
            }
            val needBonus = when (job) {
                JobType.FARMER -> if (inventory.food < 15) 150 else 0
                JobType.FORAGER -> if (inventory.food < 20) 120 else 0
                JobType.BUILDER -> if (buildings.any { !it.isConstructed }) 80 else 0
                JobType.WOODCUTTER -> if (inventory.wood < 10) 70 else 0
                JobType.MINER -> if (inventory.stone < 8) 60 else 0
                else -> 0
            }
            userPrioScore + needBonus
        }

        for (job in orderedJobs) {
            val cap = capacities[job] ?: 0
            val currentAssigned = updated.count { it.canWork && it.job == job }
            val vacancies = cap - currentAssigned

            if (vacancies > 0) {
                val availableCandidate = updated.find { it.canWork && it.job == JobType.UNASSIGNED }
                if (availableCandidate != null) {
                    val idx = updated.indexOf(availableCandidate)
                    if (idx != -1) {
                        updated[idx] = availableCandidate.copy(
                            job = job,
                            action = VillagerAction.IDLE,
                            path = emptyList(),
                            statusMessage = "Auto-assign: Ditugaskan sebagai ${job.title}"
                        )
                    }
                }
            }
        }

        return updated
    }
}

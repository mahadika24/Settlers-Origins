package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingStatus
import com.example.game.model.BuildingType
import com.example.game.model.JobPriority
import com.example.game.model.JobType
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction

/**
 * Modular Job & Workforce Balancing System
 * Manages persistent workplace assignments, derives capacities dynamically from buildings,
 * supports manual +/- allocation and intelligent auto-rebalancing without oscillation.
 */
object JobSystem {

    /**
     * Calculates current job capacities across the settlement derived directly from constructed buildings.
     */
    fun calculateJobCapacities(buildings: List<Building>): Map<JobType, Int> {
        val activeWorkplaces = buildings.filter {
            it.isConstructed &&
            it.status == BuildingStatus.COMPLETED &&
            it.type.isWorkplace
        }

        val farmCap = activeWorkplaces.filter { it.type.defaultJob == JobType.FARMER }.sumOf { it.type.maxWorkers }
        val woodcutterCap = activeWorkplaces.filter { it.type.defaultJob == JobType.WOODCUTTER }.sumOf { it.type.maxWorkers }
        val minerCap = activeWorkplaces.filter { it.type.defaultJob == JobType.MINER }.sumOf { it.type.maxWorkers }
        val foragerCap = activeWorkplaces.filter { it.type.defaultJob == JobType.FORAGER }.sumOf { it.type.maxWorkers }

        val activeSiteCount = buildings.count {
            (!it.isConstructed && it.status == BuildingStatus.UNDER_CONSTRUCTION) ||
            it.status == BuildingStatus.DEMOLISHING
        }
        val builderCap = maxOf(activeSiteCount * 2, 2)

        return mapOf(
            JobType.FARMER to farmCap,
            JobType.WOODCUTTER to woodcutterCap,
            JobType.MINER to minerCap,
            JobType.FORAGER to foragerCap,
            JobType.BUILDER to builderCap,
            JobType.UNASSIGNED to 999
        )
    }

    data class RebalanceResult(
        val updatedVillagers: List<Villager>,
        val updatedBuildings: List<Building>
    )

    /**
     * Manually adjusts the number of workers assigned to [job].
     * Synchronizes Villager.assignedBuildingId and Building.assignedWorkerIds.
     */
    fun rebalanceJobCount(
        job: JobType,
        targetCount: Int,
        villagers: List<Villager>,
        buildings: List<Building>
    ): RebalanceResult {
        val capacities = calculateJobCapacities(buildings)
        val cap = capacities[job] ?: 2
        val clampedTarget = targetCount.coerceIn(0, cap)

        val ableWorkers = villagers.filter { it.canWork }
        val currentWithJob = ableWorkers.filter { it.job == job }
        val diff = clampedTarget - currentWithJob.size

        if (diff == 0) return RebalanceResult(villagers, buildings)

        val updatedVillagers = villagers.toMutableList()
        val updatedBuildings = buildings.toMutableList()

        if (diff > 0) {
            // Adding workers: prioritize UNASSIGNED workers first, then other jobs
            var added = 0
            val candidates = updatedVillagers.filter { it.canWork && it.job == JobType.UNASSIGNED } +
                    updatedVillagers.filter { it.canWork && it.job != job && it.carryingAmount == 0 }

            for (c in candidates) {
                if (added >= diff) break

                // Find a constructed workplace of this job with available slots
                val workplace = updatedBuildings.find {
                    it.isConstructed &&
                    it.status == BuildingStatus.COMPLETED &&
                    it.type.defaultJob == job &&
                    it.assignedWorkerIds.size < it.type.maxWorkers
                }

                // If job is BUILDER or a valid workplace was found
                val targetBuildingId = workplace?.id

                val vIdx = updatedVillagers.indexOfFirst { it.id == c.id }
                if (vIdx != -1) {
                    val currentV = updatedVillagers[vIdx]

                    // If previously assigned to another building, remove from old workplace
                    if (currentV.assignedBuildingId != null) {
                        val oldBIdx = updatedBuildings.indexOfFirst { it.id == currentV.assignedBuildingId }
                        if (oldBIdx != -1) {
                            val oldB = updatedBuildings[oldBIdx]
                            updatedBuildings[oldBIdx] = oldB.copy(
                                assignedWorkerIds = oldB.assignedWorkerIds.filter { it != currentV.id }
                            )
                        }
                    }

                    // Safe transition: preserve carrying goods if any, clear wandering path
                    val newAction = if (currentV.carryingAmount > 0) currentV.action else VillagerAction.IDLE
                    val newPath = if (currentV.carryingAmount > 0) currentV.path else emptyList()

                    updatedVillagers[vIdx] = currentV.copy(
                        job = job,
                        assignedBuildingId = targetBuildingId,
                        action = newAction,
                        path = newPath,
                        statusMessage = "Ditugaskan sebagai ${job.title}" + (if (workplace != null) " di ${workplace.type.title}" else "")
                    )

                    // Add worker ID to new workplace
                    if (workplace != null) {
                        val bIdx = updatedBuildings.indexOfFirst { it.id == workplace.id }
                        if (bIdx != -1) {
                            val currentB = updatedBuildings[bIdx]
                            updatedBuildings[bIdx] = currentB.copy(
                                assignedWorkerIds = currentB.assignedWorkerIds + currentV.id
                            )
                        }
                    }

                    added++
                }
            }
        } else {
            // Removing workers: release back to UNASSIGNED pool
            var removed = 0
            val toRemove = -diff
            // Prefer workers not currently carrying goods
            val sortedWorkers = currentWithJob.sortedBy { if (it.carryingAmount > 0) 1 else 0 }

            for (w in sortedWorkers) {
                if (removed >= toRemove) break
                val vIdx = updatedVillagers.indexOfFirst { it.id == w.id }
                if (vIdx != -1) {
                    val currentV = updatedVillagers[vIdx]

                    // Remove from assigned building
                    if (currentV.assignedBuildingId != null) {
                        val bIdx = updatedBuildings.indexOfFirst { it.id == currentV.assignedBuildingId }
                        if (bIdx != -1) {
                            val b = updatedBuildings[bIdx]
                            updatedBuildings[bIdx] = b.copy(
                                assignedWorkerIds = b.assignedWorkerIds.filter { it != currentV.id }
                            )
                        }
                    }

                    val newAction = if (currentV.carryingAmount > 0) currentV.action else VillagerAction.IDLE
                    val newPath = if (currentV.carryingAmount > 0) currentV.path else emptyList()

                    updatedVillagers[vIdx] = currentV.copy(
                        job = JobType.UNASSIGNED,
                        assignedBuildingId = null,
                        action = newAction,
                        path = newPath,
                        statusMessage = "Menjadi Warga Bebas"
                    )
                    removed++
                }
            }
        }

        return RebalanceResult(updatedVillagers, updatedBuildings)
    }

    /**
     * Intelligent Auto-Assigner
     * Balances workers across vacant workplaces and priorities without rapid oscillation.
     */
    fun performAutoAssignment(
        villagers: List<Villager>,
        buildings: List<Building>,
        inventory: VillageInventory,
        jobPriorities: Map<JobType, JobPriority>
    ): RebalanceResult {
        val capacities = calculateJobCapacities(buildings)
        val unassignedWorkers = villagers.filter { it.canWork && it.job == JobType.UNASSIGNED }
        if (unassignedWorkers.isEmpty()) return RebalanceResult(villagers, buildings)

        val updatedVillagers = villagers.toMutableList()
        val updatedBuildings = buildings.toMutableList()

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
                JobType.BUILDER -> if (buildings.any { (!it.isConstructed && it.status == BuildingStatus.UNDER_CONSTRUCTION) || it.status == BuildingStatus.DEMOLISHING }) 90 else 0
                JobType.WOODCUTTER -> if (inventory.wood < 12) 70 else 0
                JobType.MINER -> if (inventory.stone < 8) 60 else 0
                else -> 0
            }
            userPrioScore + needBonus
        }

        for (job in orderedJobs) {
            val cap = capacities[job] ?: 0
            val currentAssigned = updatedVillagers.count { it.canWork && it.job == job }
            val vacancies = cap - currentAssigned

            if (vacancies > 0) {
                // Find a workplace building with available worker slot
                val workplace = updatedBuildings.find {
                    it.isConstructed &&
                    it.status == BuildingStatus.COMPLETED &&
                    it.type.defaultJob == job &&
                    it.assignedWorkerIds.size < it.type.maxWorkers
                }

                val targetBuildingId = workplace?.id
                if (workplace != null || job == JobType.BUILDER) {
                    val candidate = updatedVillagers.find { it.canWork && it.job == JobType.UNASSIGNED }
                    if (candidate != null) {
                        val vIdx = updatedVillagers.indexOf(candidate)
                        if (vIdx != -1) {
                            updatedVillagers[vIdx] = candidate.copy(
                                job = job,
                                assignedBuildingId = targetBuildingId,
                                action = VillagerAction.IDLE,
                                path = emptyList(),
                                statusMessage = "Auto-assign: Ditugaskan sebagai ${job.title}" + (if (workplace != null) " di ${workplace.type.title}" else "")
                            )

                            if (workplace != null) {
                                val bIdx = updatedBuildings.indexOfFirst { it.id == workplace.id }
                                if (bIdx != -1) {
                                    val b = updatedBuildings[bIdx]
                                    updatedBuildings[bIdx] = b.copy(
                                        assignedWorkerIds = b.assignedWorkerIds + candidate.id
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        return RebalanceResult(updatedVillagers, updatedBuildings)
    }

    /**
     * Cleans up assignments when a workplace building is demolished or invalid.
     */
    fun releaseWorkersFromBuilding(
        buildingId: String,
        villagers: List<Villager>
    ): List<Villager> {
        return villagers.map { v ->
            if (v.assignedBuildingId == buildingId) {
                v.copy(
                    job = JobType.UNASSIGNED,
                    assignedBuildingId = null,
                    action = if (v.carryingAmount > 0) v.action else VillagerAction.IDLE,
                    statusMessage = "Tempat kerja tidak tersedia, kembali ke warga bebas"
                )
            } else v
        }
    }
}

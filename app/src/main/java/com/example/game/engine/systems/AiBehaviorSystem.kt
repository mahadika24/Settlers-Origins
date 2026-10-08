package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingStatus
import com.example.game.model.BuildingType
import com.example.game.model.CarryType
import com.example.game.model.JobType
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.TimeSystem
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WorldTile
import com.example.game.pathfinding.GridPathfinder
import kotlin.random.Random

/**
 * Modular AI Behavior & Decision System
 * Implements local Utility AI / State Machine for villager needs, routines,
 * persistent workplace job execution, physical logistics, and construction/demolition.
 */
object AiBehaviorSystem {

    data class AiTickResult(
        val updatedVillagers: List<Villager>,
        val updatedBuildings: List<Building>,
        val updatedResources: List<NaturalResource>,
        val updatedInventory: VillageInventory,
        val updatedTiles: Array<Array<WorldTile>>,
        val foodHarvestedDelta: Int,
        val woodHarvestedDelta: Int,
        val stoneHarvestedDelta: Int,
        val newBuildingCompleted: Building?,
        val demolishedBuilding: Building? = null,
        val woodSalvagedDelta: Int = 0,
        val stoneSalvagedDelta: Int = 0
    )

    fun tickAi(
        villagers: List<Villager>,
        buildings: List<Building>,
        resources: List<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        inventory: VillageInventory,
        timeSystem: TimeSystem,
        dt: Float,
        mapSize: Int = 22
    ): AiTickResult {
        var currentInv = inventory
        var currentBuildings = buildings
        var currentResources = resources
        var currentTiles = tiles
        var foodDelta = 0
        var woodDelta = 0
        var stoneDelta = 0
        var completedBuilding: Building? = null
        var demolishedBuildingResult: Building? = null
        var woodSalvagedTotal = 0
        var stoneSalvagedTotal = 0

        val granary = currentBuildings.find { it.type == BuildingType.GRANARY && it.isConstructed && !it.isDemolishing }
        val townHearth = currentBuildings.find { it.type == BuildingType.TOWN_HEARTH }
        val storagePoint = granary ?: townHearth ?: Building(type = BuildingType.TOWN_HEARTH, x = 10, y = 10)

        val recreationPark = currentBuildings.find { it.type == BuildingType.RECREATION_PARK && it.isConstructed && !it.isDemolishing }
        val communityPlaza = currentBuildings.find { it.type == BuildingType.COMMUNITY_PLAZA && it.isConstructed && !it.isDemolishing }

        val updatedVillagers = mutableListOf<Villager>()

        for (villager in villagers) {
            // Skip villagers currently sleeping inside homes
            if (villager.isInHome) {
                updatedVillagers.add(villager)
                continue
            }

            var v = villager

            // 1. Check if carrying goods -> Must physically deliver to storage
            if (v.carryingType != CarryType.NONE && v.carryingAmount > 0) {
                val delRes = ResourceLogisticsSystem.processDelivery(
                    v, currentBuildings, currentResources, currentTiles, currentInv, storagePoint
                )
                v = delRes.updatedVillager
                currentInv = delRes.updatedInventory
                foodDelta += delRes.foodDelivered
                woodDelta += delRes.woodDelivered
                stoneDelta += delRes.stoneDelivered
            }
            // 2. Priority: Critical Hunger (Consume Food from Storage)
            else if (v.hunger < 35f && currentInv.food > 0 && v.action != VillagerAction.EATING) {
                val dist = Math.abs(v.posX - storagePoint.x) + Math.abs(v.posY - storagePoint.y)
                if (dist <= 1.5f) {
                    currentInv = currentInv.copy(food = (currentInv.food - 1).coerceAtLeast(0))
                    v = v.copy(
                        hunger = 100f,
                        action = VillagerAction.EATING,
                        statusMessage = "Menyantap bekal makanan di pusat desa"
                    )
                } else if (v.path.isEmpty()) {
                    val path = GridPathfinder.findPath(
                        v.posX.toInt(), v.posY.toInt(),
                        storagePoint.x, storagePoint.y,
                        currentTiles, currentBuildings, currentResources
                    )
                    v = v.copy(
                        action = VillagerAction.WALKING_TO,
                        path = path,
                        statusMessage = "Mencari makanan karena perut lapar"
                    )
                }
            }
            // 3. Priority: Recreation Need (< 35%) & Park Exists
            else if (v.recreationNeed < 35f && recreationPark != null && v.action != VillagerAction.RECREATING) {
                val dist = Math.abs(v.posX - recreationPark.x) + Math.abs(v.posY - recreationPark.y)
                if (dist <= 1.8f) {
                    v = v.copy(
                        action = VillagerAction.RECREATING,
                        statusMessage = "Berekreasi menikmati bunga & semilir angin 🌸"
                    )
                } else if (v.path.isEmpty()) {
                    val path = GridPathfinder.findPath(
                        v.posX.toInt(), v.posY.toInt(),
                        recreationPark.x, recreationPark.y,
                        currentTiles, currentBuildings, currentResources
                    )
                    v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju taman untuk berekreasi")
                }
            }
            // 4. Priority: Social Need (< 35%) & Plaza or Hearth Exists
            else if (v.socialNeed < 35f && v.action != VillagerAction.SOCIALIZING) {
                val socialVenue = communityPlaza ?: townHearth
                val sx = socialVenue?.x ?: 10
                val sy = socialVenue?.y ?: 10
                val dist = Math.abs(v.posX - sx) + Math.abs(v.posY - sy)

                if (dist <= 1.8f) {
                    v = v.copy(
                        action = VillagerAction.SOCIALIZING,
                        statusMessage = "Bercengkerama akrab dengan tetangga 💬"
                    )
                } else if (v.path.isEmpty()) {
                    val path = GridPathfinder.findPath(
                        v.posX.toInt(), v.posY.toInt(),
                        sx, sy,
                        currentTiles, currentBuildings, currentResources
                    )
                    v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju balai pertemuan warga")
                }
            }
            // 5. Continue Ongoing Recreation/Socializing until satisfied
            else if (v.action == VillagerAction.RECREATING) {
                if (v.recreationNeed >= 95f) {
                    v = v.copy(action = VillagerAction.IDLE, statusMessage = "Pikiran terasa segar bugar!")
                }
            }
            else if (v.action == VillagerAction.SOCIALIZING) {
                if (v.socialNeed >= 95f) {
                    v = v.copy(action = VillagerAction.IDLE, statusMessage = "Hati terasa terhubung dengan warga")
                }
            }
            // 6. Work Duties (Daytime and when canWork is true)
            else if (v.canWork && !timeSystem.isNight) {
                when (v.job) {
                    JobType.FARMER -> {
                        // Farmer must work on their specific assigned farm
                        val farm = currentBuildings.find {
                            it.id == v.assignedBuildingId &&
                            (it.type == BuildingType.WHEAT_FIELD || it.type == BuildingType.PUMPKIN_PATCH) &&
                            it.isConstructed &&
                            !it.isDemolishing
                        }

                        if (farm != null) {
                            val dist = Math.abs(v.posX - farm.x) + Math.abs(v.posY - farm.y)
                            if (dist <= 1.8f) {
                                if (!farm.isTilled) {
                                    currentBuildings = FarmingSystem.tillField(farm.id, currentBuildings)
                                    v = v.copy(action = VillagerAction.TILLING, statusMessage = "Menggemburkan tanah & menabur benih di ladang ${farm.type.title}")
                                } else if (farm.cropGrowth >= 100f) {
                                    val (newBld, harvested) = FarmingSystem.harvestField(farm.id, currentBuildings)
                                    currentBuildings = newBld
                                    v = v.copy(
                                        action = VillagerAction.HARVESTING,
                                        carryingType = CarryType.FOOD,
                                        carryingAmount = harvested,
                                        statusMessage = "Memanen hasil pertanian segar ($harvested pangan)!"
                                    )
                                } else {
                                    v = v.copy(action = VillagerAction.TILLING, statusMessage = "Merawat tanaman di ladang (${farm.cropGrowth.toInt()}%)")
                                }
                            } else if (v.path.isEmpty()) {
                                val path = GridPathfinder.findPath(
                                    v.posX.toInt(), v.posY.toInt(),
                                    farm.x, farm.y,
                                    currentTiles, currentBuildings, currentResources
                                )
                                v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Berangkat ke ladang binaannya")
                            }
                        } else {
                            v = wanderAround(v, currentTiles, currentBuildings, currentResources, mapSize)
                        }
                    }

                    JobType.WOODCUTTER -> {
                        val camp = currentBuildings.find {
                            it.id == v.assignedBuildingId &&
                            it.type == BuildingType.WOODCUTTER_CAMP &&
                            it.isConstructed &&
                            !it.isDemolishing
                        }

                        val refX = camp?.x ?: v.posX.toInt()
                        val refY = camp?.y ?: v.posY.toInt()
                        val tree = currentResources.filter { it.type == ResourceType.TREE && it.growthStage == 2 }
                            .minByOrNull { Math.abs(it.x - refX) + Math.abs(it.y - refY) }

                        if (tree != null) {
                            val hRes = ResourceLogisticsSystem.harvestTree(v, tree, currentResources, currentTiles, currentBuildings)
                            v = hRes.updatedVillager
                            currentResources = hRes.updatedResources
                        } else {
                            v = wanderAround(v, currentTiles, currentBuildings, currentResources, mapSize)
                        }
                    }

                    JobType.MINER -> {
                        val quarry = currentBuildings.find {
                            it.id == v.assignedBuildingId &&
                            it.type == BuildingType.STONE_QUARRY &&
                            it.isConstructed &&
                            !it.isDemolishing
                        }

                        val refX = quarry?.x ?: v.posX.toInt()
                        val refY = quarry?.y ?: v.posY.toInt()
                        val rock = currentResources.filter { it.type == ResourceType.ROCK && it.amount > 0 }
                            .minByOrNull { Math.abs(it.x - refX) + Math.abs(it.y - refY) }

                        if (rock != null) {
                            val hRes = ResourceLogisticsSystem.harvestRock(v, rock, currentResources, currentTiles, currentBuildings)
                            v = hRes.updatedVillager
                            currentResources = hRes.updatedResources
                        } else {
                            v = wanderAround(v, currentTiles, currentBuildings, currentResources, mapSize)
                        }
                    }

                    JobType.FORAGER -> {
                        val hut = currentBuildings.find {
                            it.id == v.assignedBuildingId &&
                            it.type == BuildingType.FORAGER_HUT &&
                            it.isConstructed &&
                            !it.isDemolishing
                        }

                        val refX = hut?.x ?: v.posX.toInt()
                        val refY = hut?.y ?: v.posY.toInt()
                        val bush = currentResources.filter { it.type == ResourceType.BERRY_BUSH && it.amount > 0 }
                            .minByOrNull { Math.abs(it.x - refX) + Math.abs(it.y - refY) }

                        if (bush != null) {
                            val hRes = ResourceLogisticsSystem.harvestBerries(v, bush, currentResources, currentTiles, currentBuildings)
                            v = hRes.updatedVillager
                            currentResources = hRes.updatedResources
                        } else {
                            v = wanderAround(v, currentTiles, currentBuildings, currentResources, mapSize)
                        }
                    }

                    JobType.BUILDER -> {
                        // Builders work on demolition targets first, then construction sites
                        val demolitionTarget = currentBuildings.find { it.status == BuildingStatus.DEMOLISHING }
                        val constructionTarget = currentBuildings.find { !it.isConstructed && it.status == BuildingStatus.UNDER_CONSTRUCTION }

                        if (demolitionTarget != null) {
                            val dist = Math.abs(v.posX - demolitionTarget.x) + Math.abs(v.posY - demolitionTarget.y)
                            if (dist <= 1.8f) {
                                val demoDelta = dt * 15f * v.productivityMultiplier
                                val demoResult = ConstructionSystem.advanceDemolitionProgress(
                                    demolitionTarget.id, demoDelta, currentBuildings, currentInv, currentTiles, mapSize
                                )
                                currentBuildings = demoResult.updatedBuildings
                                currentInv = demoResult.updatedInventory
                                currentTiles = demoResult.updatedTiles

                                if (demoResult.isDestroyed) {
                                    demolishedBuildingResult = demoResult.demolishedBuilding
                                    woodSalvagedTotal += demoResult.woodSalvaged
                                    stoneSalvagedTotal += demoResult.stoneSalvaged
                                    v = v.copy(action = VillagerAction.IDLE, statusMessage = "Pembongkaran bangunan selesai")
                                } else {
                                    val prog = currentBuildings.find { it.id == demolitionTarget.id }?.demolitionProgress ?: 0f
                                    v = v.copy(action = VillagerAction.DEMOLISHING, statusMessage = "Membongkar bangunan (${prog.toInt()}%)")
                                }
                            } else if (v.path.isEmpty()) {
                                val path = GridPathfinder.findPath(
                                    v.posX.toInt(), v.posY.toInt(),
                                    demolitionTarget.x, demolitionTarget.y,
                                    currentTiles, currentBuildings, currentResources
                                )
                                v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju tapak pembongkaran")
                            }
                        } else if (constructionTarget != null) {
                            val dist = Math.abs(v.posX - constructionTarget.x) + Math.abs(v.posY - constructionTarget.y)
                            if (dist <= 1.8f) {
                                val buildDelta = dt * 18f * v.productivityMultiplier
                                val (newBld, completed) = ConstructionSystem.advanceBuildingProgress(
                                    constructionTarget.id, buildDelta, currentBuildings
                                )
                                currentBuildings = newBld
                                if (completed) {
                                    completedBuilding = constructionTarget
                                    v = v.copy(action = VillagerAction.IDLE, statusMessage = "Bangunan berhasil diselesaikan!")
                                } else {
                                    val prog = newBld.find { it.id == constructionTarget.id }?.constructionProgress ?: 0f
                                    v = v.copy(action = VillagerAction.BUILDING, statusMessage = "Mendirikan kerangka bangunan (${prog.toInt()}%)")
                                }
                            } else if (v.path.isEmpty()) {
                                val path = GridPathfinder.findPath(
                                    v.posX.toInt(), v.posY.toInt(),
                                    constructionTarget.x, constructionTarget.y,
                                    currentTiles, currentBuildings, currentResources
                                )
                                v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju tapak konstruksi")
                            }
                        } else {
                            v = wanderAround(v, currentTiles, currentBuildings, currentResources, mapSize)
                        }
                    }

                    JobType.UNASSIGNED -> {
                        v = wanderAround(v, currentTiles, currentBuildings, currentResources, mapSize)
                    }
                }
            } else {
                // Free villager, baby, or child relaxing
                v = wanderAround(v, currentTiles, currentBuildings, currentResources, mapSize)
            }

            // 7. Smooth Movement Along Path
            v = moveVillagerAlongPath(v, dt)
            updatedVillagers.add(v)
        }

        return AiTickResult(
            updatedVillagers = updatedVillagers,
            updatedBuildings = currentBuildings,
            updatedResources = currentResources,
            updatedInventory = currentInv,
            updatedTiles = currentTiles,
            foodHarvestedDelta = foodDelta,
            woodHarvestedDelta = woodDelta,
            stoneHarvestedDelta = stoneDelta,
            newBuildingCompleted = completedBuilding,
            demolishedBuilding = demolishedBuildingResult,
            woodSalvagedDelta = woodSalvagedTotal,
            stoneSalvagedDelta = stoneSalvagedTotal
        )
    }

    private fun wanderAround(
        v: Villager,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>,
        mapSize: Int
    ): Villager {
        if (v.path.isEmpty() && Random.nextFloat() < 0.05f) {
            val rx = (v.posX.toInt() + Random.nextInt(-3, 4)).coerceIn(1, mapSize - 2)
            val ry = (v.posY.toInt() + Random.nextInt(-3, 4)).coerceIn(1, mapSize - 2)
            if (GridPathfinder.isTileWalkable(rx, ry, tiles, buildings, resources)) {
                val path = GridPathfinder.findPath(v.posX.toInt(), v.posY.toInt(), rx, ry, tiles, buildings, resources)
                return v.copy(action = VillagerAction.WANDERING, path = path, statusMessage = "Berjalan santai menikmati desa")
            }
        }
        return v
    }

    private fun moveVillagerAlongPath(villager: Villager, dt: Float): Villager {
        if (villager.path.isEmpty()) return villager

        val nextStep = villager.path.first()
        val targetX = nextStep.first.toFloat()
        val targetY = nextStep.second.toFloat()

        val baseSpeed = 2.4f
        val speed = baseSpeed * (0.85f + villager.productivityMultiplier * 0.15f)

        val dx = targetX - villager.posX
        val dy = targetY - villager.posY
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        val stepDist = speed * dt

        return if (dist <= stepDist) {
            villager.copy(
                posX = targetX,
                posY = targetY,
                path = villager.path.drop(1)
            )
        } else {
            val nx = villager.posX + (dx / dist) * stepDist
            val ny = villager.posY + (dy / dist) * stepDist
            villager.copy(posX = nx, posY = ny)
        }
    }
}

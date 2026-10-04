package com.example.game.engine

import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.CarryType
import com.example.game.model.CommunityGoal
import com.example.game.model.DayPhase
import com.example.game.model.GameSpeed
import com.example.game.model.JobType
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.SimulationEvent
import com.example.game.model.TileType
import com.example.game.model.TimeSystem
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WorldTile
import com.example.game.pathfinding.GridPathfinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class GameState(
    val tiles: Array<Array<WorldTile>>,
    val buildings: List<Building>,
    val resources: List<NaturalResource>,
    val villagers: List<Villager>,
    val inventory: VillageInventory,
    val timeSystem: TimeSystem,
    val events: List<SimulationEvent>,
    val goals: List<CommunityGoal>,
    val selectedTile: Pair<Int, Int>? = null,
    val selectedVillagerId: String? = null,
    val selectedBuildingId: String? = null,
    val pendingBuildType: BuildingType? = null,
    val totalFoodHarvested: Int = 0,
    val totalWoodHarvested: Int = 0,
    val totalStoneHarvested: Int = 0,
    val totalBirths: Int = 0
) {
    val averageHappiness: Float
        get() = if (villagers.isNotEmpty()) {
            villagers.map { it.happiness }.average().toFloat()
        } else 50f

    val averageProductivity: Float
        get() = if (villagers.isNotEmpty()) {
            villagers.map { it.productivityMultiplier }.average().toFloat()
        } else 1.0f
}

class SimulationEngine {

    companion object {
        const val MAP_SIZE = 22
        val MALE_NAMES = listOf("Silas", "Bram", "Jarek", "Lukas", "Finn", "Elian", "Milo", "Kael", "Rowan", "Orin")
        val FEMALE_NAMES = listOf("Martha", "Alara", "Lyra", "Elora", "Sari", "Mira", "Freya", "Anya", "Nesta", "Cora")
        val TUNIC_COLORS = listOf(
            0xFF4A90E2, // Biru Tenang
            0xFFE67E22, // Oranye Hangat
            0xFF27AE60, // Hijau Daun
            0xFF8E44AD, // Ungu Anggun
            0xFFD35400, // Terakota
            0xFF16A085, // Teal Alami
            0xFFC0392B  // Merah Marun
        )
    }

    private val _gameState = MutableStateFlow(createInitialWorld())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private var natureRegrowthTimer = 0f
    private var reproductionCooldownTimer = 0f

    fun setGameSpeed(speed: GameSpeed) {
        _gameState.value = _gameState.value.copy(
            timeSystem = _gameState.value.timeSystem.copy(speed = speed)
        )
    }

    fun selectTile(x: Int, y: Int) {
        val state = _gameState.value
        val building = state.buildings.find {
            x in it.x until (it.x + it.type.width) && y in it.y until (it.y + it.type.height)
        }
        val villager = state.villagers.find {
            Math.abs(it.posX - x) < 0.8f && Math.abs(it.posY - y) < 0.8f
        }

        _gameState.value = state.copy(
            selectedTile = Pair(x, y),
            selectedBuildingId = building?.id,
            selectedVillagerId = villager?.id
        )
    }

    fun selectVillager(id: String?) {
        _gameState.value = _gameState.value.copy(
            selectedVillagerId = id
        )
    }

    fun selectBuilding(id: String?) {
        _gameState.value = _gameState.value.copy(
            selectedBuildingId = id
        )
    }

    fun setPendingBuildType(type: BuildingType?) {
        _gameState.value = _gameState.value.copy(pendingBuildType = type)
    }

    fun assignJob(villagerId: String, newJob: JobType) {
        val state = _gameState.value
        val updatedVillagers = state.villagers.map { v ->
            if (v.id == villagerId) {
                v.copy(
                    job = newJob,
                    action = VillagerAction.IDLE,
                    path = emptyList(),
                    statusMessage = "Ditugaskan sebagai ${newJob.title}"
                )
            } else v
        }
        _gameState.value = state.copy(villagers = updatedVillagers)
        addEvent("Tugas Baru", "${updatedVillagers.find { it.id == villagerId }?.name} sekarang bekerja sebagai ${newJob.title}", newJob.iconEmoji)
    }

    fun rebalanceJobs(job: JobType, targetCount: Int) {
        val state = _gameState.value
        val ableWorkers = state.villagers.filter { it.canWork }
        val currentWithJob = ableWorkers.filter { it.job == job }
        val diff = targetCount - currentWithJob.size

        if (diff == 0) return

        val updated = state.villagers.toMutableList()

        if (diff > 0) {
            var added = 0
            val candidates = updated.filter { it.canWork && it.job == JobType.UNASSIGNED } +
                    updated.filter { it.canWork && it.job != job }

            for (c in candidates) {
                if (added >= diff) break
                val idx = updated.indexOfFirst { it.id == c.id }
                if (idx != -1) {
                    updated[idx] = updated[idx].copy(
                        job = job,
                        action = VillagerAction.IDLE,
                        path = emptyList(),
                        statusMessage = "Beralih pekerjaan ke ${job.title}"
                    )
                    added++
                }
            }
        } else {
            var removed = 0
            val toRemove = -diff
            for (w in currentWithJob) {
                if (removed >= toRemove) break
                val idx = updated.indexOfFirst { it.id == w.id }
                if (idx != -1) {
                    updated[idx] = updated[idx].copy(
                        job = JobType.UNASSIGNED,
                        action = VillagerAction.IDLE,
                        path = emptyList(),
                        statusMessage = "Menjadi Warga Bebas"
                    )
                    removed++
                }
            }
        }

        _gameState.value = state.copy(villagers = updated)
    }

    fun canPlaceBuilding(type: BuildingType, startX: Int, startY: Int): Boolean {
        val state = _gameState.value
        if (startX + type.width > MAP_SIZE || startY + type.height > MAP_SIZE) return false
        if (startX < 0 || startY < 0) return false

        for (x in startX until (startX + type.width)) {
            for (y in startY until (startY + type.height)) {
                val tile = state.tiles[x][y]
                if (!tile.type.canBuild) return false
                if (tile.isOccupied) return false
                if (state.resources.any { it.x == x && it.y == y && it.type == ResourceType.ROCK }) {
                    return false
                }
            }
        }
        return true
    }

    fun placeBuilding(type: BuildingType, x: Int, y: Int): Boolean {
        val state = _gameState.value
        if (!canPlaceBuilding(type, x, y)) return false

        if (state.inventory.wood < type.woodCost || state.inventory.stone < type.stoneCost) {
            addEvent("Bahan Kurang", "Tidak cukup kayu atau batu untuk mendirikan ${type.title}!", "⚠️")
            return false
        }

        val updatedInventory = state.inventory.copy(
            wood = state.inventory.wood - type.woodCost,
            stone = state.inventory.stone - type.stoneCost
        )

        // Mark tiles as OCCUPIED so natural vegetation will never sprout on building footprint
        val newTiles = Array(MAP_SIZE) { r ->
            Array(MAP_SIZE) { c ->
                val old = state.tiles[r][c]
                if (r in x until (x + type.width) && c in y until (y + type.height)) {
                    old.copy(isOccupied = true)
                } else old
            }
        }

        val newResources = state.resources.filterNot {
            it.x in x until (x + type.width) && it.y in y until (y + type.height)
        }

        val newBuilding = Building(
            type = type,
            x = x,
            y = y,
            isConstructed = false,
            constructionProgress = 0f,
            deliveredWood = type.woodCost,
            deliveredStone = type.stoneCost
        )

        _gameState.value = state.copy(
            tiles = newTiles,
            resources = newResources,
            buildings = state.buildings + newBuilding,
            inventory = updatedInventory,
            pendingBuildType = null
        )

        addEvent("Konstruksi Dimulai", "Tapak ${type.title} disiapkan. Tukang bangun akan segera mengerjakannya.", "🔨")
        return true
    }

    /**
     * Main Simulation Update Loop
     */
    fun tick(deltaSeconds: Float) {
        val state = _gameState.value
        val speedMult = state.timeSystem.speed.multiplier
        if (speedMult <= 0f) return

        val dt = deltaSeconds * speedMult

        // 1. Advance Time
        var newTimeSeconds = state.timeSystem.dayTimeSeconds + dt
        var newDayNumber = state.timeSystem.dayNumber
        var dayPassed = false

        if (newTimeSeconds >= TimeSystem.SECONDS_PER_DAY) {
            newTimeSeconds -= TimeSystem.SECONDS_PER_DAY
            newDayNumber++
            dayPassed = true
        }

        val updatedTimeSystem = state.timeSystem.copy(
            dayNumber = newDayNumber,
            dayTimeSeconds = newTimeSeconds
        )

        // 2. Nature Regrowth Tick (Pohon bertunas di tanah kosong alami)
        natureRegrowthTimer += dt
        var updatedResources = state.resources.toMutableList()
        val updatedTiles = state.tiles

        if (natureRegrowthTimer >= 4.0f) {
            natureRegrowthTimer = 0f
            updatedResources = updateNatureRegrowth(updatedResources, updatedTiles, state.buildings)
        }

        // 3. Farming & Construction Tick
        val updatedBuildings = updateFarmingAndConstruction(state.buildings, dt, state)

        // 4. Villagers AI, Needs & Simulation Tick
        var currentInventory = state.inventory
        var foodHarvestedDelta = 0
        var woodHarvestedDelta = 0
        var stoneHarvestedDelta = 0
        var birthsDelta = 0

        val (updatedVillagers, finalInventory) = updateVillagers(
            state.villagers,
            updatedBuildings,
            updatedResources,
            updatedTiles,
            currentInventory,
            updatedTimeSystem,
            dt,
            dayPassed,
            onHarvestFood = { amount -> foodHarvestedDelta += amount },
            onChopWood = { amount -> woodHarvestedDelta += amount },
            onMineStone = { amount -> stoneHarvestedDelta += amount },
            onBabyBorn = { birthsDelta++ }
        )

        // 5. Update Goals / Milestones
        val totalFood = state.totalFoodHarvested + foodHarvestedDelta
        val totalWood = state.totalWoodHarvested + woodHarvestedDelta
        val totalStone = state.totalStoneHarvested + stoneHarvestedDelta
        val totalBirths = state.totalBirths + birthsDelta
        val avgHappiness = if (updatedVillagers.isNotEmpty()) updatedVillagers.map { it.happiness }.average().toFloat() else 50f

        val updatedGoals = state.goals.map { goal ->
            when (goal.id) {
                "goal_food" -> goal.copy(
                    current = totalFood.coerceAtMost(goal.target),
                    isCompleted = totalFood >= goal.target
                )
                "goal_huts" -> {
                    val hutCount = updatedBuildings.count { (it.type == BuildingType.COZY_HUT || it.type == BuildingType.FAMILY_HOMESTEAD) && it.isConstructed }
                    goal.copy(
                        current = hutCount.coerceAtMost(goal.target),
                        isCompleted = hutCount >= goal.target
                    )
                }
                "goal_pop" -> goal.copy(
                    current = updatedVillagers.size.coerceAtMost(goal.target),
                    isCompleted = updatedVillagers.size >= goal.target
                )
                "goal_granary" -> {
                    val granaryBuilt = updatedBuildings.any { it.type == BuildingType.GRANARY && it.isConstructed }
                    goal.copy(
                        current = if (granaryBuilt) 1 else 0,
                        isCompleted = granaryBuilt
                    )
                }
                "goal_stone" -> goal.copy(
                    current = totalStone.coerceAtMost(goal.target),
                    isCompleted = totalStone >= goal.target
                )
                "goal_happiness" -> {
                    val targetScore = goal.target
                    goal.copy(
                        current = avgHappiness.toInt().coerceAtMost(targetScore),
                        isCompleted = avgHappiness >= targetScore
                    )
                }
                "goal_recreation" -> {
                    val hasPark = updatedBuildings.any { it.type == BuildingType.RECREATION_PARK && it.isConstructed }
                    val hasPlaza = updatedBuildings.any { it.type == BuildingType.COMMUNITY_PLAZA && it.isConstructed }
                    val count = (if (hasPark) 1 else 0) + (if (hasPlaza) 1 else 0)
                    goal.copy(
                        current = count.coerceAtMost(goal.target),
                        isCompleted = count >= goal.target
                    )
                }
                else -> goal
            }
        }

        _gameState.value = state.copy(
            tiles = updatedTiles,
            buildings = updatedBuildings,
            resources = updatedResources,
            villagers = updatedVillagers,
            inventory = finalInventory,
            timeSystem = updatedTimeSystem,
            goals = updatedGoals,
            totalFoodHarvested = totalFood,
            totalWoodHarvested = totalWood,
            totalStoneHarvested = totalStone,
            totalBirths = totalBirths
        )
    }

    private fun updateNatureRegrowth(
        resources: MutableList<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>
    ): MutableList<NaturalResource> {
        val result = resources.toMutableList()

        for (i in result.indices) {
            val res = result[i]
            if (res.type == ResourceType.TREE && res.growthStage < 2) {
                val newProgress = res.growthProgress + 15f
                if (newProgress >= 100f) {
                    val nextStage = res.growthStage + 1
                    result[i] = res.copy(
                        growthStage = nextStage,
                        growthProgress = 0f,
                        amount = if (nextStage == 2) 12 else 6
                    )
                } else {
                    result[i] = res.copy(growthProgress = newProgress)
                }
            } else if (res.type == ResourceType.BERRY_BUSH && res.amount < res.maxAmount) {
                result[i] = res.copy(amount = (res.amount + 1).coerceAtMost(res.maxAmount))
            }
        }

        // Chance to sprout on unoccupied natural grass
        if (result.count { it.type == ResourceType.TREE } < 35 && Random.nextFloat() < 0.35f) {
            val matureTrees = result.filter { it.type == ResourceType.TREE && it.growthStage == 2 }
            if (matureTrees.isNotEmpty()) {
                val parentTree = matureTrees.random()
                val offset = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1), Pair(1, 1), Pair(-1, -1)).random()
                val nx = (parentTree.x + offset.first).coerceIn(0, MAP_SIZE - 1)
                val ny = (parentTree.y + offset.second).coerceIn(0, MAP_SIZE - 1)

                val tile = tiles[nx][ny]
                val isOccupiedByBuilding = buildings.any { b ->
                    nx in b.x until (b.x + b.type.width) && ny in b.y until (b.y + b.type.height)
                }
                val hasExistingResource = result.any { it.x == nx && it.y == ny }

                if (tile.type == TileType.GRASS && !tile.isOccupied && !isOccupiedByBuilding && !hasExistingResource) {
                    val newSapling = NaturalResource(
                        type = ResourceType.TREE,
                        x = nx,
                        y = ny,
                        amount = 3,
                        maxAmount = 12,
                        growthStage = 0,
                        growthProgress = 10f
                    )
                    result.add(newSapling)
                }
            }
        }

        // Natural rock replenishment near existing quarries or outcrops so stone does not run out
        if (result.count { it.type == ResourceType.ROCK } < 8 && Random.nextFloat() < 0.25f) {
            val existingRocks = result.filter { it.type == ResourceType.ROCK }
            val nearX = if (existingRocks.isNotEmpty()) existingRocks.random().x else 16
            val nearY = if (existingRocks.isNotEmpty()) existingRocks.random().y else 12
            val offset = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1), Pair(1, 1), Pair(-1, -1)).random()
            val nx = (nearX + offset.first).coerceIn(1, MAP_SIZE - 2)
            val ny = (nearY + offset.second).coerceIn(1, MAP_SIZE - 2)
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

    private fun updateFarmingAndConstruction(
        buildings: List<Building>,
        dt: Float,
        state: GameState
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
                    val growthRate = if (hasWellBonus) 4.5f else 3.0f
                    val newGrowth = (building.cropGrowth + dt * growthRate).coerceAtMost(100f)
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
     * Villagers simulation loop:
     * - Needs System: Hunger, Housing, Recreation, Socialization
     * - Happiness calculation
     * - Productivity scaling on jobs
     * - Birth rate and mortality linkage
     */
    private fun updateVillagers(
        villagers: List<Villager>,
        buildings: List<Building>,
        resources: MutableList<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        inventory: VillageInventory,
        timeSystem: TimeSystem,
        dt: Float,
        dayPassed: Boolean,
        onHarvestFood: (Int) -> Unit,
        onChopWood: (Int) -> Unit,
        onMineStone: (Int) -> Unit,
        onBabyBorn: (Villager) -> Unit
    ): Pair<List<Villager>, VillageInventory> {
        var currentInventory = inventory
        val updatedVillagers = mutableListOf<Villager>()
        val newBabies = mutableListOf<Villager>()

        val townHearth = buildings.find { it.type == BuildingType.TOWN_HEARTH } ?: buildings.firstOrNull()
        val granary = buildings.find { it.type == BuildingType.GRANARY && it.isConstructed }
        val storagePoint = granary ?: townHearth ?: Building(type = BuildingType.TOWN_HEARTH, x = 10, y = 10)

        // Recreation & Social venues
        val recreationPark = buildings.find { it.type == BuildingType.RECREATION_PARK && it.isConstructed }
        val communityPlaza = buildings.find { it.type == BuildingType.COMMUNITY_PLAZA && it.isConstructed }

        // Residential capacity
        val residentialBuildings = buildings.filter { (it.type.housingCapacity > 0) && it.isConstructed }
        val totalCapacity = residentialBuildings.sumOf { it.type.housingCapacity }

        // Average village happiness for birth rate & morale
        val currentAvgHappiness = if (villagers.isNotEmpty()) villagers.map { it.happiness }.average().toFloat() else 50f

        for (villager in villagers) {
            var v = villager

            // 1. Aging & Longevity Check
            if (dayPassed) {
                v = v.copy(ageDays = v.ageDays + 1f)
                // Life expectancy scales with happiness: happy villagers live longer!
                val maxLifeDays = if (v.happiness >= 80f) 85f else if (v.happiness >= 50f) 75f else 65f
                if (v.ageDays > maxLifeDays && Random.nextFloat() < 0.08f) {
                    addEvent("Berpulang Damai", "Warga sepuh ${v.name} (usia ${v.ageDays.toInt()} tahun) telah berpulang dengan damai.", "🕊️")
                    continue
                }
            }

            // 2. Needs System Updates:
            // A. Hunger Need
            val hungerRate = if (v.isChild) 0.22f else 0.38f
            val newHunger = (v.hunger - dt * hungerRate).coerceAtLeast(0f)
            v = v.copy(hunger = newHunger)

            // Starvation / Despair check (higher risk when unhappy & starving)
            if (v.hunger <= 0f) {
                val deathChance = if (v.happiness < 25f) (dt * 0.04f) else (dt * 0.015f)
                if (Random.nextFloat() < deathChance) {
                    addEvent("Duka Kelaparan", "${v.name} meninggal dunia akibat kelaparan yang berkepanjangan.", "💀")
                    continue
                }
            }

            // B. Housing Need
            // Verify if home exists
            val hasValidHome = v.homeBuildingId != null && residentialBuildings.any { it.id == v.homeBuildingId }
            var homeId = v.homeBuildingId
            if (!hasValidHome) {
                // Seek available housing
                val availableHome = residentialBuildings.firstOrNull { b ->
                    val residentsInBuilding = villagers.count { it.homeBuildingId == b.id }
                    residentsInBuilding < b.type.housingCapacity
                }
                if (availableHome != null) {
                    homeId = availableHome.id
                }
            }

            val newHousingNeed = if (homeId != null) {
                (v.housingNeed + dt * 2.5f).coerceAtMost(100f)
            } else {
                (v.housingNeed - dt * 1.8f).coerceAtLeast(0f)
            }
            v = v.copy(homeBuildingId = homeId, housingNeed = newHousingNeed)

            // C. Recreation Need (Slow natural decay, restored by visiting recreation park)
            val newRecNeed = if (v.action == VillagerAction.RECREATING) {
                (v.recreationNeed + dt * 22f).coerceAtMost(100f)
            } else {
                (v.recreationNeed - dt * 0.16f).coerceAtLeast(0f)
            }
            v = v.copy(recreationNeed = newRecNeed)

            // D. Social Need (Decays naturally, restored by visiting plaza/hearth or proximity to others)
            var newSocialNeed = if (v.action == VillagerAction.SOCIALIZING) {
                (v.socialNeed + dt * 22f).coerceAtMost(100f)
            } else {
                (v.socialNeed - dt * 0.18f).coerceAtLeast(0f)
            }

            // Proximity social bonus: chatting with someone nearby
            val hasNearbyVillager = villagers.any { other ->
                other.id != v.id && (Math.abs(other.posX - v.posX) + Math.abs(other.posY - v.posY)) < 2.0f
            }
            if (hasNearbyVillager) {
                newSocialNeed = (newSocialNeed + dt * 1.2f).coerceAtMost(100f)
            }
            v = v.copy(socialNeed = newSocialNeed)

            // 3. AI Behavioral State Machine: Prioritize Needs
            // Priority 1: Starvation / Hunger
            if (v.hunger < 35f && currentInventory.food > 0 && v.action != VillagerAction.EATING) {
                val targetTile = Pair(storagePoint.x, storagePoint.y)
                val dist = Math.abs(v.posX - targetTile.first) + Math.abs(v.posY - targetTile.second)

                if (dist <= 1.5f) {
                    currentInventory = currentInventory.copy(food = (currentInventory.food - 1).coerceAtLeast(0))
                    v = v.copy(
                        hunger = 100f,
                        action = VillagerAction.EATING,
                        statusMessage = "Menyantap roti hangat di pusat desa"
                    )
                } else {
                    val path = GridPathfinder.findPath(
                        v.posX.toInt(), v.posY.toInt(),
                        targetTile.first, targetTile.second,
                        tiles, buildings, resources
                    )
                    v = v.copy(
                        action = VillagerAction.WALKING_TO,
                        path = path,
                        statusMessage = "Mencari makanan karena lapar"
                    )
                }
            }
            // Priority 2: Night Sleep & Rest
            else if (timeSystem.isNight) {
                val home = buildings.find { it.id == v.homeBuildingId } ?: townHearth
                val homeX = home?.x ?: 10
                val homeY = home?.y ?: 10
                val dist = Math.abs(v.posX - homeX) + Math.abs(v.posY - homeY)

                if (dist <= 1.2f) {
                    v = v.copy(
                        action = VillagerAction.SLEEPING,
                        energy = (v.energy + dt * 10f).coerceAtMost(100f),
                        statusMessage = "Tidur nyenyak di peraduan"
                    )
                } else {
                    val path = GridPathfinder.findPath(
                        v.posX.toInt(), v.posY.toInt(),
                        homeX, homeY,
                        tiles, buildings, resources
                    )
                    v = v.copy(
                        action = VillagerAction.WALKING_TO,
                        path = path,
                        statusMessage = "Pulang ke pondok untuk tidur"
                    )
                }
            }
            // Priority 3: Recreation Need (< 35%) & Park Exists
            else if (v.recreationNeed < 35f && recreationPark != null && v.action != VillagerAction.RECREATING) {
                val dist = Math.abs(v.posX - recreationPark.x) + Math.abs(v.posY - recreationPark.y)
                if (dist <= 1.8f) {
                    v = v.copy(
                        action = VillagerAction.RECREATING,
                        statusMessage = "Menghirup wangi bunga di taman rekreasi 🌸"
                    )
                } else if (v.path.isEmpty()) {
                    val path = GridPathfinder.findPath(
                        v.posX.toInt(), v.posY.toInt(),
                        recreationPark.x, recreationPark.y,
                        tiles, buildings, resources
                    )
                    v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju taman untuk berekreasi")
                }
            }
            // Priority 4: Social Need (< 35%) & Plaza or Hearth Exists
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
                        tiles, buildings, resources
                    )
                    v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju balai pertemuan warga")
                }
            }
            // Continue ongoing Recreation/Socializing until satisfied
            else if (v.action == VillagerAction.RECREATING) {
                if (v.recreationNeed >= 95f) {
                    v = v.copy(action = VillagerAction.IDLE, statusMessage = "Merasa segar bugar!")
                }
            }
            else if (v.action == VillagerAction.SOCIALIZING) {
                if (v.socialNeed >= 95f) {
                    v = v.copy(action = VillagerAction.IDLE, statusMessage = "Hati terasa terhubung dengan warga")
                }
            }
            // Priority 5: Daytime Work with Productivity Multiplier
            else {
                v = updateVillagerWork(
                    v, buildings, resources, tiles, storagePoint, dt,
                    onDeliverFood = { amount ->
                        currentInventory = currentInventory.copy(
                            food = (currentInventory.food + amount).coerceAtMost(currentInventory.maxFood)
                        )
                        onHarvestFood(amount)
                    },
                    onDeliverWood = { amount ->
                        currentInventory = currentInventory.copy(
                            wood = (currentInventory.wood + amount).coerceAtMost(currentInventory.maxWood)
                        )
                        onChopWood(amount)
                    },
                    onDeliverStone = { amount ->
                        currentInventory = currentInventory.copy(
                            stone = (currentInventory.stone + amount).coerceAtMost(currentInventory.maxStone)
                        )
                        onMineStone(amount)
                    }
                )
            }

            // Move along path
            v = moveVillagerAlongPath(v, dt)

            updatedVillagers.add(v)
        }

        // 4. Reproduction Linked to Community Happiness
        reproductionCooldownTimer += dt
        if (reproductionCooldownTimer >= 14.0f && timeSystem.isNight) {
            reproductionCooldownTimer = 0f

            // Happiness gate for reproduction:
            // >= 75%: 50% birth chance (flourishing community)
            // 45..74%: 20% birth chance
            // < 45%: 0% birth chance (depressed community does not have babies)
            val birthChance = when {
                currentAvgHappiness >= 75f -> 0.50f
                currentAvgHappiness >= 45f -> 0.20f
                else -> 0.0f
            }

            if (birthChance > 0f && updatedVillagers.size < totalCapacity && currentInventory.food >= 12) {
                val adultMales = updatedVillagers.filter { it.isAdult && !it.isFemale && it.homeBuildingId != null }
                val adultFemales = updatedVillagers.filter { it.isAdult && it.isFemale && it.homeBuildingId != null }

                for (female in adultFemales) {
                    val maleInSameHouse = adultMales.find { it.homeBuildingId == female.homeBuildingId }
                    if (maleInSameHouse != null && Random.nextFloat() < birthChance) {
                        val isBabyGirl = Random.nextBoolean()
                        val babyName = if (isBabyGirl) FEMALE_NAMES.random() else MALE_NAMES.random()
                        val baby = Villager(
                            name = babyName,
                            isFemale = isBabyGirl,
                            ageDays = 0f,
                            hunger = 100f,
                            housingNeed = 100f,
                            recreationNeed = 100f,
                            socialNeed = 100f,
                            energy = 100f,
                            job = JobType.UNASSIGNED,
                            homeBuildingId = female.homeBuildingId,
                            posX = female.posX,
                            posY = female.posY,
                            tunicColorHex = TUNIC_COLORS.random(),
                            statusMessage = "Bayi mungil yang membawa kebahagiaan baru"
                        )
                        newBabies.add(baby)
                        onBabyBorn(baby)
                        addEvent(
                            "Kelahiran Bayi!",
                            "Keluarga ${maleInSameHouse.name} & ${female.name} berbahagia atas kelahiran ${baby.name}! Komunitas makin semarak.",
                            "👶"
                        )
                        break
                    }
                }
            }
        }

        updatedVillagers.addAll(newBabies)

        return Pair(updatedVillagers, currentInventory)
    }

    private fun updateVillagerWork(
        villager: Villager,
        buildings: List<Building>,
        resources: MutableList<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        storagePoint: Building,
        dt: Float,
        onDeliverFood: (Int) -> Unit,
        onDeliverWood: (Int) -> Unit,
        onDeliverStone: (Int) -> Unit
    ): Villager {
        var v = villager
        val prod = v.productivityMultiplier

        // If carrying goods, deliver to storage / collection point first
        if (v.carryingType != CarryType.NONE && v.carryingAmount > 0) {
            val stoneQuarry = buildings.find { it.type == BuildingType.STONE_QUARRY && it.isConstructed }
            val dropPoint = if (v.carryingType == CarryType.STONE && stoneQuarry != null) stoneQuarry else storagePoint

            val dist = Math.abs(v.posX - dropPoint.x) + Math.abs(v.posY - dropPoint.y)
            if (dist <= 1.4f) {
                if (v.carryingType == CarryType.FOOD) {
                    onDeliverFood(v.carryingAmount)
                    v = v.copy(
                        carryingType = CarryType.NONE,
                        carryingAmount = 0,
                        action = VillagerAction.IDLE,
                        statusMessage = "Menyimpan panen di lumbung"
                    )
                } else if (v.carryingType == CarryType.WOOD) {
                    onDeliverWood(v.carryingAmount)
                    v = v.copy(
                        carryingType = CarryType.NONE,
                        carryingAmount = 0,
                        action = VillagerAction.IDLE,
                        statusMessage = "Menumpuk kayu di perbekalan"
                    )
                } else if (v.carryingType == CarryType.STONE) {
                    onDeliverStone(v.carryingAmount)
                    v = v.copy(
                        carryingType = CarryType.NONE,
                        carryingAmount = 0,
                        action = VillagerAction.IDLE,
                        statusMessage = "Menimbun batu di Tempat Pengumpulan Batu 🪨"
                    )
                }
            } else {
                if (v.path.isEmpty()) {
                    val path = GridPathfinder.findPath(
                        v.posX.toInt(), v.posY.toInt(),
                        dropPoint.x, dropPoint.y,
                        tiles, buildings, resources
                    )
                    val deliverMsg = if (v.carryingType == CarryType.STONE) "Mengangkut batu ke Tempat Pengumpulan" else "Mengangkut hasil ke lumbung"
                    v = v.copy(action = VillagerAction.DELIVERING, path = path, statusMessage = deliverMsg)
                }
            }
            return v
        }

        when (v.job) {
            JobType.FARMER -> {
                val farm = buildings.find { (it.type == BuildingType.WHEAT_FIELD || it.type == BuildingType.PUMPKIN_PATCH) && it.isConstructed }
                if (farm != null) {
                    val dist = Math.abs(v.posX - farm.x) + Math.abs(v.posY - farm.y)
                    if (dist <= 1.8f) {
                        if (!farm.isTilled) {
                            val idx = buildings.indexOf(farm)
                            if (idx != -1) {
                                (buildings as MutableList)[idx] = farm.copy(isTilled = true, cropGrowth = 10f)
                            }
                            v = v.copy(action = VillagerAction.TILLING, statusMessage = "Menggemburkan tanah & menabur benih")
                        } else if (farm.cropGrowth >= 100f) {
                            val idx = buildings.indexOf(farm)
                            if (idx != -1) {
                                (buildings as MutableList)[idx] = farm.copy(cropGrowth = 0f, isTilled = true)
                            }
                            v = v.copy(
                                action = VillagerAction.HARVESTING,
                                carryingType = CarryType.FOOD,
                                carryingAmount = if (farm.type == BuildingType.PUMPKIN_PATCH) 10 else 8,
                                statusMessage = "Memanen hasil pertanian melimpah!"
                            )
                        } else {
                            v = v.copy(action = VillagerAction.TILLING, statusMessage = "Merawat tanaman (pertumbuhan ${farm.cropGrowth.toInt()}%)")
                        }
                    } else if (v.path.isEmpty()) {
                        val path = GridPathfinder.findPath(
                            v.posX.toInt(), v.posY.toInt(),
                            farm.x, farm.y,
                            tiles, buildings, resources
                        )
                        v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Berangkat ke ladang")
                    }
                } else {
                    v = wanderAround(v, tiles, buildings, resources)
                }
            }

            JobType.WOODCUTTER -> {
                val tree = resources.filter { it.type == ResourceType.TREE && it.growthStage == 2 }
                    .minByOrNull { Math.abs(it.x - v.posX) + Math.abs(it.y - v.posY) }

                if (tree != null) {
                    val dist = Math.abs(v.posX - tree.x) + Math.abs(v.posY - tree.y)
                    if (dist <= 1.4f) {
                        // Chopping power scaled by productivity!
                        val chopPower = (3.5f * prod).toInt().coerceAtLeast(1)
                        val newAmount = tree.amount - chopPower
                        val idx = resources.indexOf(tree)
                        if (newAmount <= 0) {
                            if (idx != -1) resources.removeAt(idx)
                            v = v.copy(
                                action = VillagerAction.CHOPPING,
                                carryingType = CarryType.WOOD,
                                carryingAmount = 6,
                                statusMessage = "Pohon berhasil ditebang menjadi kayu"
                            )
                        } else {
                            if (idx != -1) resources[idx] = tree.copy(amount = newAmount)
                            v = v.copy(action = VillagerAction.CHOPPING, statusMessage = "Mengayun kapak menebang pohon")
                        }
                    } else if (v.path.isEmpty()) {
                        val path = GridPathfinder.findPath(
                            v.posX.toInt(), v.posY.toInt(),
                            tree.x, tree.y,
                            tiles, buildings, resources
                        )
                        v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju hutan untuk menebang")
                    }
                } else {
                    v = wanderAround(v, tiles, buildings, resources)
                }
            }

            JobType.BUILDER -> {
                val unbuilt = buildings.find { !it.isConstructed }
                if (unbuilt != null) {
                    val dist = Math.abs(v.posX - unbuilt.x) + Math.abs(v.posY - unbuilt.y)
                    if (dist <= 1.8f) {
                        // Construction rate scaled by productivity!
                        val buildRate = 18f * prod
                        val newProgress = unbuilt.constructionProgress + (dt * buildRate)
                        val idx = buildings.indexOf(unbuilt)
                        if (newProgress >= 100f) {
                            if (idx != -1) {
                                (buildings as MutableList)[idx] = unbuilt.copy(
                                    isConstructed = true,
                                    constructionProgress = 100f
                                )
                            }
                            addEvent("Bangunan Selesai!", "${unbuilt.type.title} telah selesai didirikan dan siap digunakan!", "🎉")
                            v = v.copy(action = VillagerAction.IDLE, statusMessage = "Menyelesaikan bangunan")
                        } else {
                            if (idx != -1) {
                                (buildings as MutableList)[idx] = unbuilt.copy(constructionProgress = newProgress)
                            }
                            v = v.copy(action = VillagerAction.BUILDING, statusMessage = "Memasang kayu & tiang (${newProgress.toInt()}%)")
                        }
                    } else if (v.path.isEmpty()) {
                        val path = GridPathfinder.findPath(
                            v.posX.toInt(), v.posY.toInt(),
                            unbuilt.x, unbuilt.y,
                            tiles, buildings, resources
                        )
                        v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju tapak konstruksi")
                    }
                } else {
                    v = wanderAround(v, tiles, buildings, resources)
                }
            }

            JobType.MINER -> {
                val rock = resources.filter { it.type == ResourceType.ROCK && it.amount > 0 }
                    .minByOrNull { Math.abs(it.x - v.posX) + Math.abs(it.y - v.posY) }

                if (rock != null) {
                    val dist = Math.abs(v.posX - rock.x) + Math.abs(v.posY - rock.y)
                    if (dist <= 1.45f) {
                        val minePower = (3.5f * prod).toInt().coerceAtLeast(1)
                        val newAmount = rock.amount - minePower
                        val idx = resources.indexOf(rock)
                        if (newAmount <= 0) {
                            if (idx != -1) resources.removeAt(idx)
                            v = v.copy(
                                action = VillagerAction.MINING,
                                carryingType = CarryType.STONE,
                                carryingAmount = 5,
                                statusMessage = "Batu karang selesai dipahat menjadi batu"
                            )
                        } else {
                            if (idx != -1) resources[idx] = rock.copy(amount = newAmount)
                            v = v.copy(
                                action = VillagerAction.MINING,
                                carryingType = CarryType.STONE,
                                carryingAmount = 4,
                                statusMessage = "Memahat bongkahan batu alami ⛏️"
                            )
                        }
                    } else if (v.path.isEmpty()) {
                        val path = GridPathfinder.findPath(
                            v.posX.toInt(), v.posY.toInt(),
                            rock.x, rock.y,
                            tiles, buildings, resources
                        )
                        v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju batuan karang untuk menambang")
                    }
                } else {
                    v = wanderAround(v, tiles, buildings, resources)
                }
            }

            JobType.FORAGER -> {
                val bush = resources.filter { it.type == ResourceType.BERRY_BUSH && it.amount > 0 }
                    .minByOrNull { Math.abs(it.x - v.posX) + Math.abs(it.y - v.posY) }

                if (bush != null) {
                    val dist = Math.abs(v.posX - bush.x) + Math.abs(v.posY - bush.y)
                    if (dist <= 1.4f) {
                        val idx = resources.indexOf(bush)
                        if (idx != -1) resources[idx] = bush.copy(amount = 0)
                        v = v.copy(
                            action = VillagerAction.FORAGING,
                            carryingType = CarryType.FOOD,
                            carryingAmount = 4,
                            statusMessage = "Memetik buah beri manis dari semak"
                        )
                    } else if (v.path.isEmpty()) {
                        val path = GridPathfinder.findPath(
                            v.posX.toInt(), v.posY.toInt(),
                            bush.x, bush.y,
                            tiles, buildings, resources
                        )
                        v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Mencari semak buah liar")
                    }
                } else {
                    v = wanderAround(v, tiles, buildings, resources)
                }
            }

            JobType.UNASSIGNED -> {
                v = wanderAround(v, tiles, buildings, resources)
            }
        }

        return v
    }

    private fun wanderAround(
        v: Villager,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>
    ): Villager {
        if (v.path.isEmpty() && Random.nextFloat() < 0.05f) {
            val rx = (v.posX.toInt() + Random.nextInt(-3, 4)).coerceIn(1, MAP_SIZE - 2)
            val ry = (v.posY.toInt() + Random.nextInt(-3, 4)).coerceIn(1, MAP_SIZE - 2)
            if (GridPathfinder.isTileWalkable(rx, ry, tiles, buildings, resources)) {
                val path = GridPathfinder.findPath(v.posX.toInt(), v.posY.toInt(), rx, ry, tiles, buildings, resources)
                return v.copy(action = VillagerAction.WANDERING, path = path, statusMessage = "Menikmati pemandangan pulau")
            }
        }
        return v
    }

    private fun moveVillagerAlongPath(villager: Villager, dt: Float): Villager {
        if (villager.path.isEmpty()) return villager

        val nextStep = villager.path.first()
        val targetX = nextStep.first.toFloat()
        val targetY = nextStep.second.toFloat()

        // Move speed influenced slightly by happiness
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

    private fun addEvent(title: String, desc: String, emoji: String) {
        val currentEvents = _gameState.value.events.take(20)
        val newEvent = SimulationEvent(
            day = _gameState.value.timeSystem.dayNumber,
            text = "$title: $desc",
            iconEmoji = emoji
        )
        _gameState.value = _gameState.value.copy(
            events = listOf(newEvent) + currentEvents
        )
    }

    private fun createInitialWorld(): GameState {
        val tiles = Array(MAP_SIZE) { x ->
            Array(MAP_SIZE) { y ->
                val isWater = (x == 0 || y == 0 || (x in 18..21 && y in 2..5))
                val isSoil = (x in 11..16 && y in 8..15)
                val type = when {
                    isWater -> TileType.WATER
                    isSoil -> TileType.FERTILE_SOIL
                    else -> TileType.GRASS
                }
                WorldTile(x = x, y = y, type = type)
            }
        }

        val townHearth = Building(
            type = BuildingType.TOWN_HEARTH,
            x = 9,
            y = 9,
            isConstructed = true,
            constructionProgress = 100f
        )
        val starterHut = Building(
            type = BuildingType.COZY_HUT,
            x = 6,
            y = 9,
            isConstructed = true,
            constructionProgress = 100f
        )
        val starterFarm = Building(
            type = BuildingType.WHEAT_FIELD,
            x = 12,
            y = 9,
            isConstructed = true,
            constructionProgress = 100f,
            isTilled = true,
            cropGrowth = 40f
        )
        val woodcutterCamp = Building(
            type = BuildingType.WOODCUTTER_CAMP,
            x = 6,
            y = 12,
            isConstructed = true,
            constructionProgress = 100f
        )
        val stoneQuarry = Building(
            type = BuildingType.STONE_QUARRY,
            x = 12,
            y = 12,
            isConstructed = true,
            constructionProgress = 100f
        )

        val buildings = listOf(townHearth, starterHut, starterFarm, woodcutterCamp, stoneQuarry)

        for (b in buildings) {
            for (x in b.x until (b.x + b.type.width)) {
                for (y in b.y until (b.y + b.type.height)) {
                    if (x in 0 until MAP_SIZE && y in 0 until MAP_SIZE) {
                        tiles[x][y] = tiles[x][y].copy(isOccupied = true, buildingId = b.id)
                    }
                }
            }
        }

        val resources = mutableListOf<NaturalResource>()
        val treeCoords = listOf(
            Pair(3, 4), Pair(4, 4), Pair(4, 5), Pair(3, 5), Pair(5, 3),
            Pair(4, 14), Pair(5, 15), Pair(3, 16), Pair(4, 16), Pair(5, 17),
            Pair(16, 17), Pair(17, 16), Pair(18, 17), Pair(17, 18), Pair(16, 19),
            Pair(14, 3), Pair(15, 4), Pair(16, 3), Pair(17, 4)
        )
        for (c in treeCoords) {
            if (!tiles[c.first][c.second].isOccupied) {
                resources.add(
                    NaturalResource(
                        type = ResourceType.TREE,
                        x = c.first,
                        y = c.second,
                        amount = 12,
                        maxAmount = 12,
                        growthStage = 2,
                        growthProgress = 100f
                    )
                )
            }
        }

        val berryCoords = listOf(Pair(8, 5), Pair(9, 4), Pair(14, 14), Pair(15, 13))
        for (b in berryCoords) {
            if (!tiles[b.first][b.second].isOccupied) {
                resources.add(
                    NaturalResource(
                        type = ResourceType.BERRY_BUSH,
                        x = b.first,
                        y = b.second,
                        amount = 6,
                        maxAmount = 6,
                        growthStage = 2,
                        growthProgress = 100f
                    )
                )
            }
        }

        val rockCoords = listOf(
            Pair(15, 12), Pair(15, 13), Pair(16, 12), // Rock deposits near Tempat Pengumpulan Batu
            Pair(20, 10), Pair(20, 11),
            Pair(2, 18), Pair(2, 19)
        )
        for (r in rockCoords) {
            if (!tiles[r.first][r.second].isOccupied) {
                resources.add(
                    NaturalResource(
                        type = ResourceType.ROCK,
                        x = r.first,
                        y = r.second,
                        amount = 20,
                        maxAmount = 20,
                        growthStage = 2,
                        growthProgress = 100f
                    )
                )
            }
        }

        val initialVillagers = listOf(
            Villager(
                name = "Silas",
                isFemale = false,
                ageDays = 26f,
                job = JobType.FARMER,
                homeBuildingId = starterHut.id,
                posX = 12.5f,
                posY = 9.5f,
                tunicColorHex = 0xFFD4A338,
                statusMessage = "Merawat benih gandum"
            ),
            Villager(
                name = "Martha",
                isFemale = true,
                ageDays = 24f,
                job = JobType.FARMER,
                homeBuildingId = starterHut.id,
                posX = 13.5f,
                posY = 9.5f,
                tunicColorHex = 0xFF5E8B4E,
                statusMessage = "Mengolah petak tanah"
            ),
            Villager(
                name = "Bram",
                isFemale = false,
                ageDays = 58f,
                job = JobType.WOODCUTTER,
                homeBuildingId = starterHut.id,
                posX = 6.5f,
                posY = 12.5f,
                tunicColorHex = 0xFF8D7B68,
                statusMessage = "Menebang dahan pohon untuk perapian"
            ),
            Villager(
                name = "Kael",
                isFemale = false,
                ageDays = 27f,
                job = JobType.MINER,
                homeBuildingId = townHearth.id,
                posX = 12.5f,
                posY = 12.5f,
                tunicColorHex = 0xFF607D8B,
                statusMessage = "Mengumpulkan batu di Tempat Pengumpulan Batu"
            ),
            Villager(
                name = "Alara",
                isFemale = true,
                ageDays = 22f,
                job = JobType.BUILDER,
                homeBuildingId = starterHut.id,
                posX = 9.5f,
                posY = 9.5f,
                tunicColorHex = 0xFFC86D3B,
                statusMessage = "Memeriksa perkakas pembangunan"
            ),
            Villager(
                name = "Jarek",
                isFemale = false,
                ageDays = 20f,
                job = JobType.FORAGER,
                homeBuildingId = townHearth.id,
                posX = 8.5f,
                posY = 5.5f,
                tunicColorHex = 0xFF7E57C2,
                statusMessage = "Memetik buah beri liar"
            ),
            Villager(
                name = "Toby",
                isFemale = false,
                ageDays = 8f,
                job = JobType.UNASSIGNED,
                homeBuildingId = starterHut.id,
                posX = 10.0f,
                posY = 10.5f,
                tunicColorHex = 0xFF4A90E2,
                statusMessage = "Bermain riang di sekitar perapian"
            )
        )

        val initialGoals = listOf(
            CommunityGoal(
                id = "goal_food",
                title = "Swasembada Pangan",
                description = "Panen dan kumpulkan 25 bahan makanan dari ladang gandum atau semak beri.",
                current = 0,
                target = 25,
                rewardText = "+15 Kebahagiaan Warga"
            ),
            CommunityGoal(
                id = "goal_huts",
                title = "Hunian Nyaman",
                description = "Bangun minimal 2 Pondok Kayu atau Rumah Keluarga untuk menampung warga.",
                current = 1,
                target = 2,
                rewardText = "Memicu kelahiran bayi baru"
            ),
            CommunityGoal(
                id = "goal_recreation",
                title = "Fasilitas Kebahagiaan",
                description = "Bangun Taman Bunga Rekreasi dan Balai Temu Komunitas.",
                current = 0,
                target = 2,
                rewardText = "+25% Produktivitas Komunitas"
            ),
            CommunityGoal(
                id = "goal_happiness",
                title = "Masyarakat Harmonis",
                description = "Capai rata-rata kebahagiaan desa sebesar 80%.",
                current = 85,
                target = 80,
                rewardText = "Lonjakan Kelahiran Bayi & Umur Panjang"
            ),
            CommunityGoal(
                id = "goal_pop",
                title = "Komunitas Berkembang",
                description = "Tingkatkan populasi desa hingga mencapai 9 penduduk.",
                current = 6,
                target = 9,
                rewardText = "Gelar Pemimpin Makmur"
            ),
            CommunityGoal(
                id = "goal_stone",
                title = "Penggalian Batu",
                description = "Bangun Tambang Batu dan kumpulkan 15 batu dari batuan alami pulau.",
                current = 0,
                target = 15,
                rewardText = "+30 Kapasitas Bangunan Megah"
            ),
            CommunityGoal(
                id = "goal_granary",
                title = "Lumbung Masa Depan",
                description = "Dirikan Lumbung Pangan (Granary) untuk memperluas penyimpanan desa.",
                current = 0,
                target = 1,
                rewardText = "+60 Kapasitas Makanan & Kayu"
            )
        )

        val initialEvents = listOf(
            SimulationEvent(
                day = 1,
                text = "Selamat datang di Havenfold! Perapian desa telah menyala.",
                iconEmoji = "🔥"
            ),
            SimulationEvent(
                day = 1,
                text = "Silas dan Martha mulai merawat ladang gandum pertama.",
                iconEmoji = "🌾"
            )
        )

        return GameState(
            tiles = tiles,
            buildings = buildings,
            resources = resources,
            villagers = initialVillagers,
            inventory = VillageInventory(food = 32, wood = 28, stone = 14),
            timeSystem = TimeSystem(),
            events = initialEvents,
            goals = initialGoals
        )
    }
}

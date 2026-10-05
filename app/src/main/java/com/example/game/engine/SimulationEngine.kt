package com.example.game.engine

import android.content.Context
import com.example.game.engine.systems.AiBehaviorSystem
import com.example.game.engine.systems.ConstructionSystem
import com.example.game.engine.systems.FarmingSystem
import com.example.game.engine.systems.HousingSystem
import com.example.game.engine.systems.JobSystem
import com.example.game.engine.systems.NatureSystem
import com.example.game.engine.systems.NeedsSystem
import com.example.game.engine.systems.PopulationSystem
import com.example.game.engine.systems.SimulationClockSystem
import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.CarryType
import com.example.game.model.CommunityGoal
import com.example.game.model.GameSpeed
import com.example.game.model.JobPriority
import com.example.game.model.JobType
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.SimulationEvent
import com.example.game.model.TileType
import com.example.game.model.TimeSystem
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WeatherType
import com.example.game.model.WorldTile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class GameState(
    val tiles: Array<Array<WorldTile>>,
    val buildings: List<Building>,
    val resources: List<NaturalResource>,
    val villagers: List<Villager>,
    val inventory: VillageInventory,
    val timeSystem: TimeSystem,
    val weather: WeatherType = WeatherType.CLEAR,
    val weatherTimer: Float = 0f,
    val isAutoAssignEnabled: Boolean = false,
    val jobPriorities: Map<JobType, JobPriority> = mapOf(
        JobType.FARMER to JobPriority.HIGH,
        JobType.FORAGER to JobPriority.HIGH,
        JobType.WOODCUTTER to JobPriority.MEDIUM,
        JobType.MINER to JobPriority.MEDIUM,
        JobType.BUILDER to JobPriority.LOW
    ),
    val events: List<SimulationEvent>,
    val goals: List<CommunityGoal>,
    val selectedTile: Pair<Int, Int>? = null,
    val selectedVillagerId: String? = null,
    val selectedBuildingId: String? = null,
    val pendingBuildType: BuildingType? = null,
    val totalFoodHarvested: Int = 0,
    val totalWoodHarvested: Int = 0,
    val totalStoneHarvested: Int = 0,
    val totalBirths: Int = 0,
    val totalDeaths: Int = 0
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

/**
 * Main Orchestrator Simulation Engine
 * Coordinates modular systems, ensures single source of truth in GameState,
 * and maintains scalable, deterministic state updates.
 */
class SimulationEngine(private val context: Context? = null) {

    companion object {
        const val MAP_SIZE = 22
    }

    private val _gameState = MutableStateFlow(createInitialWorld())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private var natureRegrowthTimer = 0f
    private var reproductionCooldownTimer = 0f
    private var autoSaveTimer = 0f

    init {
        if (context != null && GameSaveManager.hasSave(context)) {
            val loaded = GameSaveManager.loadGame(context, _gameState.value.tiles)
            if (loaded != null) {
                _gameState.value = loaded
            }
        }
    }

    fun toggleAutoAssign() {
        val current = _gameState.value.isAutoAssignEnabled
        _gameState.value = _gameState.value.copy(isAutoAssignEnabled = !current)
        addEvent(
            "Manajemen Tenaga Kerja",
            if (!current) "Penugasan otomatis (Auto-Assign) diaktifkan oleh tetua desa ⚖️"
            else "Mode penugasan beralih ke Prioritas Manual 🖐️",
            "⚖️"
        )
    }

    fun setJobPriority(job: JobType, priority: JobPriority) {
        val updatedMap = _gameState.value.jobPriorities.toMutableMap()
        updatedMap[job] = priority
        _gameState.value = _gameState.value.copy(jobPriorities = updatedMap)
    }

    fun resetGame() {
        context?.let { GameSaveManager.deleteSave(it) }
        _gameState.value = createInitialWorld()
        addEvent("Dunia Baru", "Desa Havenfold telah diatur ulang ke kondisi awal 🌅", "🌱")
    }

    fun saveGameNow() {
        context?.let { GameSaveManager.saveGame(it, _gameState.value) }
    }

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
            if (v.id == villagerId && v.canWork) {
                // Safe transition: preserve carrying goods if any
                val action = if (v.carryingAmount > 0) v.action else VillagerAction.IDLE
                val path = if (v.carryingAmount > 0) v.path else emptyList()
                v.copy(
                    job = newJob,
                    action = action,
                    path = path,
                    statusMessage = "Ditugaskan sebagai ${newJob.title}"
                )
            } else v
        }
        _gameState.value = state.copy(villagers = updatedVillagers)
        val targetName = updatedVillagers.find { it.id == villagerId }?.name ?: "Warga"
        addEvent("Tugas Baru", "$targetName sekarang bekerja sebagai ${newJob.title}", newJob.iconEmoji)
    }

    fun rebalanceJobs(job: JobType, targetCount: Int) {
        val state = _gameState.value
        val updatedVillagers = JobSystem.rebalanceJobCount(
            job = job,
            targetCount = targetCount,
            villagers = state.villagers,
            buildings = state.buildings
        )
        _gameState.value = state.copy(villagers = updatedVillagers)
    }

    fun canPlaceBuilding(type: BuildingType, startX: Int, startY: Int): Boolean {
        val state = _gameState.value
        return ConstructionSystem.canPlaceBuilding(
            type = type,
            startX = startX,
            startY = startY,
            tiles = state.tiles,
            buildings = state.buildings,
            resources = state.resources,
            mapSize = MAP_SIZE
        )
    }

    fun placeBuilding(type: BuildingType, x: Int, y: Int): Boolean {
        val state = _gameState.value
        val result = ConstructionSystem.placeBuilding(
            type = type,
            startX = x,
            startY = y,
            tiles = state.tiles,
            buildings = state.buildings,
            resources = state.resources,
            inventory = state.inventory,
            mapSize = MAP_SIZE
        ) ?: return false

        _gameState.value = state.copy(
            tiles = result.updatedTiles,
            buildings = result.updatedBuildings,
            resources = result.updatedResources,
            inventory = result.updatedInventory,
            pendingBuildType = null
        )

        addEvent("Konstruksi Dimulai", "Tapak ${type.title} disiapkan. Tukang bangun akan segera mengerjakannya.", "🔨")
        return true
    }

    /**
     * Main Simulation Update Loop
     * Invoked on a regular simulation tick. Coordinates all modular subsystems.
     */
    fun tick(deltaSeconds: Float) {
        val state = _gameState.value

        // 1. Advance Simulation Clock & Time System
        val timeResult = SimulationClockSystem.updateTime(state.timeSystem, deltaSeconds)
        val dt = timeResult.effectiveDt
        if (dt <= 0f) return

        val updatedTimeSystem = timeResult.updatedTimeSystem
        val dayPassed = timeResult.dayPassed

        // 2. Weather System Update
        val weatherResult = SimulationClockSystem.updateWeather(state.weather, state.weatherTimer, dt)
        if (weatherResult.weatherChanged) {
            addEvent("Perubahan Cuaca", "Langit kini berubah menjadi ${weatherResult.updatedWeather.label} (${weatherResult.updatedWeather.description})", weatherResult.updatedWeather.iconEmoji)
        }

        // 3. Nature System Update (Vegetation growth & tree regrowth)
        natureRegrowthTimer += dt
        var updatedResources = state.resources
        if (natureRegrowthTimer >= 3.5f) {
            natureRegrowthTimer = 0f
            updatedResources = NatureSystem.updateNature(
                resources = state.resources,
                tiles = state.tiles,
                buildings = state.buildings,
                weather = weatherResult.updatedWeather,
                dt = 3.5f,
                mapSize = MAP_SIZE
            )
        }

        // 4. Farming System Crop Growth Update
        val farmingBuildings = FarmingSystem.updateCropGrowth(
            buildings = state.buildings,
            weather = weatherResult.updatedWeather,
            dt = dt
        )

        // 5. Housing System Routine Update (Night entry, morning leave, homelessness)
        val (housedVillagers, housedBuildings) = HousingSystem.updateHousingRoutines(
            villagers = state.villagers,
            buildings = farmingBuildings,
            timeSystem = updatedTimeSystem,
            tiles = state.tiles,
            resources = updatedResources,
            dt = dt
        )

        // 6. Needs System Update (Continuous decay & recovery)
        val needsUpdatedVillagers = housedVillagers.map { v ->
            val hasNearby = housedVillagers.any { other ->
                other.id != v.id && (Math.abs(other.posX - v.posX) + Math.abs(other.posY - v.posY)) < 2.0f
            }
            NeedsSystem.updateVillagerNeeds(v, hasNearby, dt)
        }

        // 7. Population System Lifecycle (Aging, Mortality, Births)
        val (popResult, newCooldown) = PopulationSystem.updatePopulation(
            villagers = needsUpdatedVillagers,
            buildings = housedBuildings,
            inventory = state.inventory,
            timeSystem = updatedTimeSystem,
            dt = dt,
            dayPassed = dayPassed,
            reproductionCooldown = reproductionCooldownTimer
        )
        reproductionCooldownTimer = newCooldown

        for (birth in popResult.newBirths) {
            addEvent(
                "Kelahiran Bayi!",
                "Selamat datang bayi ${birth.name}! Komunitas Havenfold semakin semarak dan bahagia.",
                "👶"
            )
        }

        for (deceased in popResult.deceasedVillagers) {
            if (deceased.hunger <= 0f) {
                addEvent("Duka Kelaparan", "${deceased.name} meninggal dunia akibat kelaparan yang berkepanjangan.", "💀")
            } else {
                addEvent("Berpulang Damai", "Warga sepuh ${deceased.name} (usia ${deceased.ageDays.toInt()} tahun) telah berpulang dengan tenang.", "🕊️")
            }
        }

        var activeVillagers = popResult.updatedVillagers

        // 8. Auto-Assign Workforce balancing if enabled
        if (state.isAutoAssignEnabled) {
            activeVillagers = JobSystem.performAutoAssignment(
                villagers = activeVillagers,
                buildings = housedBuildings,
                inventory = state.inventory,
                jobPriorities = state.jobPriorities
            )
        }

        // 9. AI Behavior System (Decisions, work routines, resource deliveries, movement)
        val aiResult = AiBehaviorSystem.tickAi(
            villagers = activeVillagers,
            buildings = housedBuildings,
            resources = updatedResources,
            tiles = state.tiles,
            inventory = state.inventory,
            timeSystem = updatedTimeSystem,
            dt = dt,
            mapSize = MAP_SIZE
        )

        if (aiResult.newBuildingCompleted != null) {
            addEvent("Bangunan Selesai!", "${aiResult.newBuildingCompleted.type.title} telah selesai didirikan dan siap digunakan!", "🎉")
        }

        // 10. Goals & Milestones Update
        val totalFood = state.totalFoodHarvested + aiResult.foodHarvestedDelta
        val totalWood = state.totalWoodHarvested + aiResult.woodHarvestedDelta
        val totalStone = state.totalStoneHarvested + aiResult.stoneHarvestedDelta
        val totalBirths = state.totalBirths + popResult.newBirths.size
        val totalDeaths = state.totalDeaths + popResult.deceasedVillagers.size
        val avgHappiness = if (aiResult.updatedVillagers.isNotEmpty()) {
            aiResult.updatedVillagers.map { it.happiness }.average().toFloat()
        } else 50f

        val updatedGoals = state.goals.map { goal ->
            when (goal.id) {
                "goal_food" -> goal.copy(
                    current = totalFood.coerceAtMost(goal.target),
                    isCompleted = totalFood >= goal.target
                )
                "goal_huts" -> {
                    val hutCount = aiResult.updatedBuildings.count {
                        (it.type == BuildingType.COZY_HUT || it.type == BuildingType.FAMILY_HOMESTEAD) && it.isConstructed
                    }
                    goal.copy(
                        current = hutCount.coerceAtMost(goal.target),
                        isCompleted = hutCount >= goal.target
                    )
                }
                "goal_pop" -> goal.copy(
                    current = aiResult.updatedVillagers.size.coerceAtMost(goal.target),
                    isCompleted = aiResult.updatedVillagers.size >= goal.target
                )
                "goal_granary" -> {
                    val granaryBuilt = aiResult.updatedBuildings.any { it.type == BuildingType.GRANARY && it.isConstructed }
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
                    goal.copy(
                        current = avgHappiness.toInt().coerceAtMost(goal.target),
                        isCompleted = avgHappiness >= goal.target
                    )
                }
                else -> goal
            }
        }

        // 11. Periodic Auto-Save
        autoSaveTimer += dt
        if (autoSaveTimer >= 20.0f) {
            autoSaveTimer = 0f
            saveGameNow()
        }

        // 12. Commit Single Source of Truth
        _gameState.value = state.copy(
            timeSystem = updatedTimeSystem,
            weather = weatherResult.updatedWeather,
            weatherTimer = weatherResult.updatedTimer,
            resources = aiResult.updatedResources,
            buildings = aiResult.updatedBuildings,
            villagers = aiResult.updatedVillagers,
            inventory = aiResult.updatedInventory,
            goals = updatedGoals,
            totalFoodHarvested = totalFood,
            totalWoodHarvested = totalWood,
            totalStoneHarvested = totalStone,
            totalBirths = totalBirths,
            totalDeaths = totalDeaths
        )
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
            Pair(15, 12), Pair(15, 13), Pair(16, 12),
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

        val silasId = UUID.randomUUID().toString()
        val marthaId = UUID.randomUUID().toString()
        val tobyId = UUID.randomUUID().toString()

        val initialVillagers = listOf(
            Villager(
                id = silasId,
                name = "Silas",
                isFemale = false,
                ageDays = 26f,
                job = JobType.FARMER,
                homeBuildingId = starterHut.id,
                partnerId = marthaId,
                childrenIds = listOf(tobyId),
                posX = 12.5f,
                posY = 9.5f,
                tunicColorHex = 0xFFD4A338,
                statusMessage = "Merawat benih gandum"
            ),
            Villager(
                id = marthaId,
                name = "Martha",
                isFemale = true,
                ageDays = 24f,
                job = JobType.FARMER,
                homeBuildingId = starterHut.id,
                partnerId = silasId,
                childrenIds = listOf(tobyId),
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
                id = tobyId,
                name = "Toby",
                isFemale = false,
                ageDays = 8f,
                job = JobType.UNASSIGNED,
                homeBuildingId = starterHut.id,
                parentIds = listOf(silasId, marthaId),
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
                current = 7,
                target = 9,
                rewardText = "Gelar Pemimpin Makmur"
            ),
            CommunityGoal(
                id = "goal_stone",
                title = "Penggalian Batu",
                description = "Kumpulkan 15 batu dari batuan alami pulau.",
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

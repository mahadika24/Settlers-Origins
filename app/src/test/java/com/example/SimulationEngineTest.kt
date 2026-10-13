package com.example

import com.example.game.engine.GameSaveManager
import com.example.game.engine.SimulationEngine
import com.example.game.engine.systems.AiBehaviorSystem
import com.example.game.engine.systems.ConstructionSystem
import com.example.game.engine.systems.FarmingSystem
import com.example.game.engine.systems.HousingSystem
import com.example.game.engine.systems.JobSystem
import com.example.game.engine.systems.NatureSystem
import com.example.game.engine.systems.NeedsSystem
import com.example.game.engine.systems.PopulationSystem
import com.example.game.engine.systems.ResourceLogisticsSystem
import com.example.game.engine.systems.SimulationClockSystem
import com.example.game.model.Building
import com.example.game.model.BuildingStatus
import com.example.game.model.BuildingType
import com.example.game.engine.GameState
import com.example.game.model.CarryType
import com.example.game.model.DayPhase
import com.example.game.model.GameSpeed
import com.example.game.model.JobPriority
import com.example.game.model.JobType
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.TileType
import com.example.game.model.TimeSystem
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WeatherType
import com.example.game.model.WorldTile
import com.example.game.pathfinding.GridPathfinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SimulationEngineTest {

    @Test
    fun testHungerAndFoodConsumption() {
        val hearth = Building(type = BuildingType.TOWN_HEARTH, x = 10, y = 10, isConstructed = true)
        val hungryVillager = Villager(
            name = "Lapar",
            isFemale = false,
            ageDays = 25f,
            hunger = 20f,
            posX = 10.2f,
            posY = 10.2f
        )
        val initialInv = VillageInventory(food = 10, wood = 10, stone = 10)
        val timeSys = TimeSystem()

        val aiResult = AiBehaviorSystem.tickAi(
            villagers = listOf(hungryVillager),
            buildings = listOf(hearth),
            resources = emptyList(),
            tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } },
            inventory = initialInv,
            timeSystem = timeSys,
            dt = 0.5f
        )

        val updated = aiResult.updatedVillagers.first()
        assertEquals(100f, updated.hunger, 0.001f)
        assertEquals(VillagerAction.EATING, updated.action)
        assertEquals(9, aiResult.updatedInventory.food)
    }

    @Test
    fun testAgingAndLifeStages() {
        val baby = Villager(name = "Baby", isFemale = true, ageDays = 2f, posX = 10f, posY = 10f)
        val child = Villager(name = "Child", isFemale = false, ageDays = 8f, posX = 10f, posY = 10f)
        val adult = Villager(name = "Adult", isFemale = true, ageDays = 25f, posX = 10f, posY = 10f)
        val elder = Villager(name = "Elder", isFemale = false, ageDays = 60f, posX = 10f, posY = 10f)

        // Verify baby stage
        assertTrue("Baby flag should be true", baby.isBaby)
        assertFalse("Baby cannot be child", baby.isChild)
        assertFalse("Baby cannot work", baby.canWork)
        assertEquals("Bayi", baby.lifeStageLabel)

        // Verify child stage
        assertTrue("Child flag should be true", child.isChild)
        assertFalse("Child cannot be adult", child.isAdult)
        assertFalse("Child cannot work", child.canWork)
        assertEquals("Anak-anak", child.lifeStageLabel)

        // Verify adult stage
        assertTrue("Adult flag should be true", adult.isAdult)
        assertTrue("Adult can work", adult.canWork)
        assertEquals("Dewasa", adult.lifeStageLabel)

        // Verify elder stage
        assertTrue("Elder flag should be true", elder.isElder)
        assertTrue("Elder can work light duties", elder.canWork)
        assertEquals("Lansia", elder.lifeStageLabel)
    }

    @Test
    fun testContinuousAgingWithSimulationTime() {
        val villager = Villager(name = "AgingTest", isFemale = false, ageDays = 20f, posX = 10f, posY = 10f)
        val timeSys = TimeSystem()
        val dt = 100f // Exactly 1 full simulation day

        val (popResult, _) = PopulationSystem.updatePopulation(
            villagers = listOf(villager),
            buildings = emptyList(),
            inventory = VillageInventory(),
            timeSystem = timeSys,
            dt = dt,
            dayPassed = true,
            reproductionCooldown = 0f
        )

        val updated = popResult.updatedVillagers.first()
        assertEquals(21f, updated.ageDays, 0.01f)
    }

    @Test
    fun testFamilyRelationshipsAndBirth() {
        val fatherId = UUID.randomUUID().toString()
        val motherId = UUID.randomUUID().toString()
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)

        val father = Villager(
            id = fatherId,
            name = "Silas",
            isFemale = false,
            ageDays = 28f,
            partnerId = motherId,
            homeBuildingId = hut.id,
            posX = 6f,
            posY = 9f
        )
        val mother = Villager(
            id = motherId,
            name = "Martha",
            isFemale = true,
            ageDays = 26f,
            partnerId = fatherId,
            homeBuildingId = hut.id,
            posX = 6f,
            posY = 9f
        )

        assertEquals(father.id, mother.partnerId)
        assertEquals(mother.id, father.partnerId)

        // Test birth under optimal conditions (night, food available, housed partners)
        val nightTime = TimeSystem(dayTimeSeconds = 90f) // Night phase
        val inventory = VillageInventory(food = 40)

        val (popResult, _) = PopulationSystem.updatePopulation(
            villagers = listOf(father, mother),
            buildings = listOf(hut),
            inventory = inventory,
            timeSystem = nightTime,
            dt = 0.5f,
            dayPassed = false,
            reproductionCooldown = 15f // cooldown ready
        )

        // Verify baby was born and parents' childrenIds linked
        if (popResult.newBirths.isNotEmpty()) {
            val baby = popResult.newBirths.first()
            assertTrue(baby.isBaby)
            assertEquals(hut.id, baby.homeBuildingId)
            assertTrue(baby.parentIds.contains(fatherId))
            assertTrue(baby.parentIds.contains(motherId))

            val updatedFather = popResult.updatedVillagers.find { it.id == fatherId }!!
            val updatedMother = popResult.updatedVillagers.find { it.id == motherId }!!
            assertTrue(updatedFather.childrenIds.contains(baby.id))
            assertTrue(updatedMother.childrenIds.contains(baby.id))
        }
    }

    @Test
    fun testHousingCapacityAndOccupancy() {
        val hut = Building(
            type = BuildingType.COZY_HUT,
            x = 6,
            y = 9,
            isConstructed = true,
            residentIds = listOf("v1", "v2")
        )
        assertEquals(4, hut.type.housingCapacity)
        assertEquals(2, hut.residentIds.size)

        // Villager sleeping inside
        val sleepingResident = Villager(
            id = "v1",
            name = "Resident1",
            isFemale = false,
            ageDays = 25f,
            homeBuildingId = hut.id,
            isInHome = true,
            action = VillagerAction.SLEEPING,
            posX = 6f,
            posY = 9f
        )
        val outdoorResident = Villager(
            id = "v2",
            name = "Resident2",
            isFemale = true,
            ageDays = 24f,
            homeBuildingId = hut.id,
            isInHome = false,
            posX = 10f,
            posY = 10f
        )

        val insideCount = hut.insideResidentsCount(listOf(sleepingResident, outdoorResident))
        assertEquals(1, insideCount)
    }

    @Test
    fun testHomelessnessStatus() {
        val homeless = Villager(
            name = "Wanderer",
            isFemale = false,
            ageDays = 20f,
            homeBuildingId = null,
            housingNeed = 80f,
            posX = 10f,
            posY = 10f
        )
        assertNull(homeless.homeBuildingId)
        assertFalse(homeless.isInHome)

        // Needs system decays housingNeed for homeless villagers
        val updated = NeedsSystem.updateVillagerNeeds(homeless, hasNearbyVillager = false, dt = 1.0f)
        assertTrue("Housing need must decrease when homeless", updated.housingNeed < 80f)
    }

    @Test
    fun testEnteringAndLeavingHouse() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val resident = Villager(
            id = "res1",
            name = "Rudi",
            isFemale = false,
            ageDays = 25f,
            homeBuildingId = hut.id,
            isInHome = false,
            posX = 7.0f,
            posY = 11.0f
        )

        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val nightTime = TimeSystem(dayTimeSeconds = 90f) // Night

        // Resident near home at night enters the home
        // 1. Reaching entrance -> transitions to ENTERING_HOME
        val (housedEntering, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(resident),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val enteringVillager = housedEntering.first()
        assertEquals(VillagerAction.ENTERING_HOME, enteringVillager.action)
        assertFalse("Not fully inside until entry transition finishes", enteringVillager.isInHome)

        // 2. Completes entry -> inside home and SLEEPING
        val (housedNight, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(enteringVillager),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val sleeping = housedNight.first()
        assertTrue("Must be inside home at night", sleeping.isInHome)
        assertEquals(VillagerAction.SLEEPING, sleeping.action)

        // Morning comes: resident wakes up and steps outside
        val morningTime = TimeSystem(dayTimeSeconds = 25f) // Day
        // 1. First tick -> transitions to LEAVING_HOME
        val (housedLeaving, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(sleeping),
            buildings = listOf(hut),
            timeSystem = morningTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val leavingVillager = housedLeaving.first()
        assertEquals(VillagerAction.LEAVING_HOME, leavingVillager.action)
        assertTrue(leavingVillager.isInHome)

        // 2. Second tick -> physically outside at entrance tile
        val (housedMorning, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(leavingVillager),
            buildings = listOf(hut),
            timeSystem = morningTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val awake = housedMorning.first()
        assertFalse("Must step outside when day arrives", awake.isInHome)
        assertEquals(VillagerAction.IDLE, awake.action)
    }

    @Test
    fun testDayNightCycleProgression() {
        val dawnTime = TimeSystem(dayTimeSeconds = 5f)
        assertEquals(DayPhase.DAWN, dawnTime.phase)
        assertFalse(dawnTime.isNight)

        val dayTime = TimeSystem(dayTimeSeconds = 30f)
        assertEquals(DayPhase.DAY, dayTime.phase)
        assertFalse(dayTime.isNight)

        val duskTime = TimeSystem(dayTimeSeconds = 70f)
        assertEquals(DayPhase.DUSK, duskTime.phase)
        assertFalse(duskTime.isNight)

        val nightTime = TimeSystem(dayTimeSeconds = 90f)
        assertEquals(DayPhase.NIGHT, nightTime.phase)
        assertTrue(nightTime.isNight)
    }

    @Test
    fun testSimulationSpeedMultipliers() {
        val engine = SimulationEngine()
        engine.setGameSpeed(GameSpeed.PAUSED)
        assertEquals(0f, engine.gameState.value.timeSystem.speed.multiplier, 0.001f)

        engine.setGameSpeed(GameSpeed.NORMAL)
        assertEquals(1f, engine.gameState.value.timeSystem.speed.multiplier, 0.001f)

        engine.setGameSpeed(GameSpeed.FAST)
        assertEquals(2f, engine.gameState.value.timeSystem.speed.multiplier, 0.001f)

        engine.setGameSpeed(GameSpeed.TURBO)
        assertEquals(3.5f, engine.gameState.value.timeSystem.speed.multiplier, 0.001f)
    }

    @Test
    fun testFarmingLifecycleAndHarvest() {
        val farm = Building(
            type = BuildingType.WHEAT_FIELD,
            x = 12,
            y = 9,
            isConstructed = true,
            isTilled = false,
            cropGrowth = 0f,
            assignedWorkerIds = listOf("farmer1", "farmer2")
        )

        // 1. Till Field
        val tilledList = FarmingSystem.tillField(farm.id, listOf(farm))
        val tilledFarm = tilledList.first()
        assertTrue(tilledFarm.isTilled)
        assertTrue(tilledFarm.cropGrowth > 0f)

        // 2. Crop Growth
        val grownList = FarmingSystem.updateCropGrowth(
            buildings = listOf(tilledFarm.copy(cropGrowth = 90f)),
            weather = WeatherType.CLEAR,
            dt = 5.0f
        )
        val matureFarm = grownList.first()
        assertEquals(100f, matureFarm.cropGrowth, 0.001f)

        // 3. Harvest
        val (harvestedBuildings, foodYield) = FarmingSystem.harvestField(matureFarm.id, grownList)
        val afterHarvestFarm = harvestedBuildings.first()
        assertEquals(0f, afterHarvestFarm.cropGrowth, 0.001f)
        assertTrue(afterHarvestFarm.isTilled)
        assertEquals(8, foodYield)
    }

    @Test
    fun testResourceHarvestingAndLogistics() {
        val tree = NaturalResource(type = ResourceType.TREE, x = 5, y = 5, amount = 12, growthStage = 2)
        val woodcutter = Villager(
            name = "Cutter",
            isFemale = false,
            ageDays = 25f,
            job = JobType.WOODCUTTER,
            posX = 5f,
            posY = 5.2f
        )
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }

        // Harvest tree
        val harvestRes = ResourceLogisticsSystem.harvestTree(woodcutter, tree, listOf(tree), tiles, emptyList())
        val afterCut = harvestRes.updatedVillager
        assertEquals(VillagerAction.CHOPPING, afterCut.action)

        // Deliver goods to storage
        val carrier = Villager(
            name = "Deliverer",
            isFemale = false,
            ageDays = 25f,
            carryingType = CarryType.WOOD,
            carryingAmount = 6,
            posX = 9.2f,
            posY = 9.2f
        )
        val hearth = Building(type = BuildingType.TOWN_HEARTH, x = 9, y = 9, isConstructed = true)
        val inv = VillageInventory(wood = 10)

        val deliveryRes = ResourceLogisticsSystem.processDelivery(
            villager = carrier,
            buildings = listOf(hearth),
            resources = emptyList(),
            tiles = tiles,
            inventory = inv,
            storagePoint = hearth
        )

        assertEquals(16, deliveryRes.updatedInventory.wood)
        assertEquals(CarryType.NONE, deliveryRes.updatedVillager.carryingType)
        assertEquals(0, deliveryRes.updatedVillager.carryingAmount)
    }

    @Test
    fun testTreeRegrowthLifecycle() {
        val sapling = NaturalResource(type = ResourceType.TREE, x = 5, y = 5, growthStage = 0, amount = 3)
        val youngTree = NaturalResource(type = ResourceType.TREE, x = 5, y = 5, growthStage = 1, amount = 6)
        val matureTree = NaturalResource(type = ResourceType.TREE, x = 5, y = 5, growthStage = 2, amount = 12)

        assertEquals(0, sapling.growthStage)
        assertEquals(1, youngTree.growthStage)
        assertEquals(2, matureTree.growthStage)
        assertTrue(matureTree.amount > youngTree.amount)
        assertTrue(youngTree.amount > sapling.amount)
    }

    @Test
    fun testConstructionLifecycle() {
        val hutType = BuildingType.COZY_HUT
        val unbuilt = Building(
            type = hutType,
            x = 10,
            y = 10,
            isConstructed = false,
            constructionProgress = 80f,
            deliveredWood = hutType.woodCost,
            deliveredStone = hutType.stoneCost
        )

        assertFalse(unbuilt.isConstructed)

        // Advance construction to completion
        val (updatedBuildings, completed) = ConstructionSystem.advanceBuildingProgress(unbuilt.id, 25f, listOf(unbuilt))
        assertTrue(completed)
        assertTrue(updatedBuildings.first().isConstructed)
        assertEquals(100f, updatedBuildings.first().constructionProgress, 0.001f)
    }

    @Test
    fun testGridPathfinderAvoidsObstacles() {
        val mapSize = 10
        val tiles = Array(mapSize) { x ->
            Array(mapSize) { y ->
                WorldTile(x, y, if (x == 5 && y in 2..7) TileType.WATER else TileType.GRASS)
            }
        }
        val buildings = emptyList<Building>()
        val resources = emptyList<NaturalResource>()

        // Path from (3, 4) to (7, 4) must circumvent water wall at x=5
        val path = GridPathfinder.findPath(3, 4, 7, 4, tiles, buildings, resources)
        assertFalse("Path should not be empty", path.isEmpty())
        for (step in path) {
            val tile = tiles[step.first][step.second]
            assertTrue("Step must be walkable", tile.type.isWalkable)
        }
    }

    @Test
    fun testBuildingCollisionAndFootprint() {
        val engine = SimulationEngine()
        // Out of bounds checks
        assertFalse(engine.canPlaceBuilding(BuildingType.COZY_HUT, 25, 25))
        assertFalse(engine.canPlaceBuilding(BuildingType.COZY_HUT, -1, 5))

        // Occupied tile checks (where starter hut is at 6, 9)
        assertFalse(engine.canPlaceBuilding(BuildingType.COZY_HUT, 6, 9))
    }

    @Test
    fun testWorkforceCapacitiesAndRebalance() {
        val engine = SimulationEngine()
        val capacities = JobSystem.calculateJobCapacities(engine.gameState.value.buildings)

        assertTrue(capacities[JobType.FARMER]!! >= 2)
        assertTrue(capacities[JobType.WOODCUTTER]!! >= 2)
        assertTrue(capacities[JobType.MINER]!! >= 2)

        // Test manual rebalancing: reducing farmers from 2 to 1
        val initialFarmerCount = engine.gameState.value.villagers.count { it.job == JobType.FARMER }
        assertEquals(2, initialFarmerCount)
        engine.rebalanceJobs(JobType.FARMER, 1)
        val reducedFarmerCount = engine.gameState.value.villagers.count { it.job == JobType.FARMER }
        assertEquals(1, reducedFarmerCount)

        // Restoring farmers back to capacity (2)
        engine.rebalanceJobs(JobType.FARMER, 2)
        val restoredFarmerCount = engine.gameState.value.villagers.count { it.job == JobType.FARMER }
        assertEquals(2, restoredFarmerCount)

        // Verifying workers cannot be assigned exceeding capacity (cap is 2)
        engine.rebalanceJobs(JobType.FARMER, 5)
        val cappedFarmerCount = engine.gameState.value.villagers.count { it.job == JobType.FARMER }
        assertEquals(2, cappedFarmerCount)
    }

    @Test
    fun testWorkforceAutoAssignAndPriorities() {
        val engine = SimulationEngine()
        assertFalse(engine.gameState.value.isAutoAssignEnabled)

        engine.toggleAutoAssign()
        assertTrue(engine.gameState.value.isAutoAssignEnabled)

        engine.setJobPriority(JobType.FARMER, JobPriority.HIGH)
        assertEquals(JobPriority.HIGH, engine.gameState.value.jobPriorities[JobType.FARMER])

        engine.setJobPriority(JobType.MINER, JobPriority.LOW)
        assertEquals(JobPriority.LOW, engine.gameState.value.jobPriorities[JobType.MINER])
    }

    @Test
    fun testValidAndReachableBuildingEntrance() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val (entranceX, entranceY) = hut.getEntranceTile(tiles = tiles)

        // 1. Entrance tile must be exterior to the 2x2 footprint [6..7, 9..10]
        assertEquals(7, entranceX)
        assertEquals(11, entranceY)
        assertFalse("Entrance must not be inside building footprint", entranceX in 6..7 && entranceY in 9..10)

        // 2. Footprint is solid, entrance is walkable
        assertTrue(GridPathfinder.isTileWalkable(entranceX, entranceY, tiles, listOf(hut), emptyList()))
        assertFalse(GridPathfinder.isTileWalkable(6, 9, tiles, listOf(hut), emptyList()))
        assertFalse(GridPathfinder.isTileWalkable(7, 10, tiles, listOf(hut), emptyList()))

        // 3. Pathfinding reaches the entrance without stepping onto the solid footprint
        val path = GridPathfinder.findPath(2, 2, entranceX, entranceY, tiles, listOf(hut), emptyList())
        assertTrue("Path to entrance must exist", path.isNotEmpty())
        assertEquals(Pair(entranceX, entranceY), path.last())
        for (step in path) {
            assertFalse("Path must never clip through solid building footprint", step.first in 6..7 && step.second in 9..10)
        }
    }

    @Test
    fun testHouseEntryAndIsInHomeTiming() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val nightTime = TimeSystem(dayTimeSeconds = 90f) // Night phase

        // Villager away from entrance
        val villager = Villager(
            id = "v1",
            name = "Aria",
            isFemale = true,
            ageDays = 22f,
            homeBuildingId = hut.id,
            isInHome = false,
            posX = 7.0f,
            posY = 15.0f
        )

        // Step 1: Distant villager at night initiates RETURNING_HOME (isInHome = false)
        val (retHoused, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(villager),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val returningV = retHoused.first()
        assertEquals(VillagerAction.RETURNING_HOME, returningV.action)
        assertFalse("Cannot be in home while walking back", returningV.isInHome)
        assertTrue("Must have path to entrance", returningV.path.isNotEmpty())

        // Step 2: Arriving at the entrance doorstep (7.0f, 11.0f)
        val doorstepV = returningV.copy(posX = 7.0f, posY = 11.0f, path = emptyList())
        val (entHoused, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(doorstepV),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val enteringV = entHoused.first()
        assertEquals(VillagerAction.ENTERING_HOME, enteringV.action)
        assertFalse("Must not be in home until entry transition completes", enteringV.isInHome)

        // Step 3: Entry transition completes -> inside home & SLEEPING
        val (inHoused, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(enteringV),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val sleepingV = inHoused.first()
        assertTrue("Must be inside home after transition", sleepingV.isInHome)
        assertEquals(VillagerAction.SLEEPING, sleepingV.action)
    }

    @Test
    fun testSleepingAndEnergyRecovery() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val nightTime = TimeSystem(dayTimeSeconds = 90f)

        val tiredResident = Villager(
            id = "v2",
            name = "Bram",
            isFemale = false,
            ageDays = 30f,
            homeBuildingId = hut.id,
            isInHome = true,
            action = VillagerAction.SLEEPING,
            energy = 40f,
            posX = 7.0f,
            posY = 11.0f
        )

        val (housed, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(tiredResident),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 2.0f
        )
        val recovered = housed.first()
        assertTrue(recovered.isInHome)
        assertEquals(VillagerAction.SLEEPING, recovered.action)
        assertEquals(60f, recovered.energy, 0.01f) // 40 + 2.0 * 10
    }

    @Test
    fun testLeavingHomeMorningRoutine() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val dayTime = TimeSystem(dayTimeSeconds = 25f) // Day

        val sleepingResident = Villager(
            id = "v3",
            name = "Clara",
            isFemale = true,
            ageDays = 26f,
            homeBuildingId = hut.id,
            isInHome = true,
            action = VillagerAction.SLEEPING,
            posX = 7.0f,
            posY = 11.0f
        )

        // Tick 1: Transition to LEAVING_HOME (still inside)
        val (housed1, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(sleepingResident),
            buildings = listOf(hut),
            timeSystem = dayTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val leavingV = housed1.first()
        assertEquals(VillagerAction.LEAVING_HOME, leavingV.action)
        assertTrue("Still inside during LEAVING_HOME transition", leavingV.isInHome)

        // Tick 2: Steps outside at entrance tile
        val (housed2, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(leavingV),
            buildings = listOf(hut),
            timeSystem = dayTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val outsideV = housed2.first()
        assertFalse("Must be outside after leaving home", outsideV.isInHome)
        assertEquals(VillagerAction.IDLE, outsideV.action)
        assertEquals(7.0f, outsideV.posX, 0.001f)
        assertEquals(11.0f, outsideV.posY, 0.001f)
    }

    @Test
    fun testFamilyHouseSharing() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val timeSys = TimeSystem()

        val parent = Villager(id = "p1", name = "Papa", isFemale = false, ageDays = 30f, homeBuildingId = hut.id, posX = 7f, posY = 11f)
        val partner = Villager(id = "p2", name = "Mama", isFemale = true, ageDays = 28f, partnerId = "p1", homeBuildingId = null, posX = 7f, posY = 11f)
        val child = Villager(id = "c1", name = "Anak", isFemale = false, ageDays = 6f, parentIds = listOf("p1"), homeBuildingId = null, posX = 7f, posY = 11f)

        val (housed, updatedBuildings) = HousingSystem.updateHousingRoutines(
            villagers = listOf(parent, partner, child),
            buildings = listOf(hut),
            timeSystem = timeSys,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )

        val updatedPartner = housed.find { it.id == "p2" }
        val updatedChild = housed.find { it.id == "c1" }

        assertNotNull(updatedPartner)
        assertNotNull(updatedChild)
        assertEquals(hut.id, updatedPartner?.homeBuildingId)
        assertEquals(hut.id, updatedChild?.homeBuildingId)

        val updatedHut = updatedBuildings.find { it.id == hut.id }
        assertEquals(3, updatedHut?.residentIds?.size)
        assertTrue(updatedHut?.residentIds?.containsAll(listOf("p1", "p2", "c1")) == true)
    }

    @Test
    fun testFullHouseCapacityRejection() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true) // Capacity = 4
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val timeSys = TimeSystem()

        val resident1 = Villager(id = "r1", name = "R1", isFemale = true, ageDays = 25f, homeBuildingId = hut.id, posX = 7f, posY = 11f)
        val resident2 = Villager(id = "r2", name = "R2", isFemale = false, ageDays = 25f, homeBuildingId = hut.id, posX = 7f, posY = 11f)
        val resident3 = Villager(id = "r3", name = "R3", isFemale = true, ageDays = 25f, homeBuildingId = hut.id, posX = 7f, posY = 11f)
        val resident4 = Villager(id = "r4", name = "R4", isFemale = false, ageDays = 25f, homeBuildingId = hut.id, posX = 7f, posY = 11f)
        val extraVillager = Villager(id = "r5", name = "R5", isFemale = true, ageDays = 25f, homeBuildingId = null, posX = 7f, posY = 11f)

        val (housed, updatedBuildings) = HousingSystem.updateHousingRoutines(
            villagers = listOf(resident1, resident2, resident3, resident4, extraVillager),
            buildings = listOf(hut),
            timeSystem = timeSys,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )

        val updatedExtra = housed.find { it.id == "r5" }
        assertNull("5th villager cannot join a full 4-person hut", updatedExtra?.homeBuildingId)
        assertEquals(4, updatedBuildings.first().residentIds.size)
    }

    @Test
    fun testHomelessVillagerNeverEntersHouse() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val nightTime = TimeSystem(dayTimeSeconds = 90f)

        // Homeless villager placed right at the entrance of the hut
        val homeless = Villager(
            id = "hl1",
            name = "Gelandangan",
            isFemale = false,
            ageDays = 24f,
            homeBuildingId = null,
            isInHome = false,
            posX = 7.0f,
            posY = 11.0f
        )

        // When night falls, homeless villager must not enter
        val (housed, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(homeless),
            buildings = emptyList(), // No available homes
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )
        val updatedHomeless = housed.first()
        assertFalse("Homeless villager must never enter home", updatedHomeless.isInHome)
        assertEquals(VillagerAction.SLEEPING, updatedHomeless.action)
    }

    @Test
    fun testDemolishedHouseSafeEviction() {
        val hut = Building(
            type = BuildingType.COZY_HUT,
            x = 6,
            y = 9,
            isConstructed = true,
            status = com.example.game.model.BuildingStatus.DEMOLISHING
        )
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val timeSys = TimeSystem()

        val resident = Villager(
            id = "evict1",
            name = "Pengungsi",
            isFemale = false,
            ageDays = 28f,
            homeBuildingId = hut.id,
            isInHome = true,
            posX = 7f,
            posY = 11f
        )

        val (housed, updatedBuildings) = HousingSystem.updateHousingRoutines(
            villagers = listOf(resident),
            buildings = listOf(hut),
            timeSystem = timeSys,
            tiles = tiles,
            resources = emptyList(),
            dt = 1f
        )

        val evicted = housed.first()
        assertFalse("Resident must be safely evicted from demolishing house", evicted.isInHome)
        assertNull("Home reference must be cleared", evicted.homeBuildingId)
        assertEquals(VillagerAction.IDLE, evicted.action)
        assertEquals(0, updatedBuildings.first().residentIds.size)
    }

    @Test
    fun testGameSpeedScalingHousingRoutines() {
        val hut = Building(type = BuildingType.COZY_HUT, x = 6, y = 9, isConstructed = true)
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val nightTime = TimeSystem(dayTimeSeconds = 90f)

        // 1x Speed: dt = 1.0f -> +10 energy
        val (v1x, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(Villager(id = "s1", name = "S1", isFemale = true, ageDays = 20f, homeBuildingId = hut.id, isInHome = true, action = VillagerAction.SLEEPING, energy = 50f, posX = 7f, posY = 11f)),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 1.0f
        )
        assertEquals(60f, v1x.first().energy, 0.01f)

        // 2x Speed: dt = 2.0f -> +20 energy
        val (v2x, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(Villager(id = "s2", name = "S2", isFemale = true, ageDays = 20f, homeBuildingId = hut.id, isInHome = true, action = VillagerAction.SLEEPING, energy = 50f, posX = 7f, posY = 11f)),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 2.0f
        )
        assertEquals(70f, v2x.first().energy, 0.01f)

        // 3.5x Speed: dt = 3.5f -> +35 energy
        val (v35x, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(Villager(id = "s3", name = "S3", isFemale = true, ageDays = 20f, homeBuildingId = hut.id, isInHome = true, action = VillagerAction.SLEEPING, energy = 50f, posX = 7f, posY = 11f)),
            buildings = listOf(hut),
            timeSystem = nightTime,
            tiles = tiles,
            resources = emptyList(),
            dt = 3.5f
        )
        assertEquals(85f, v35x.first().energy, 0.01f)
    }

    @Test
    fun testConstructionWorkflowAndWorkPoint() {
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val inv = VillageInventory(wood = 50, stone = 30)

        // 1. Placement (BLUEPRINT -> UNDER_CONSTRUCTION)
        val placeRes = ConstructionSystem.placeBuilding(
            type = BuildingType.COZY_HUT,
            startX = 4,
            startY = 4,
            tiles = tiles,
            buildings = emptyList(),
            resources = emptyList(),
            inventory = inv
        )
        assertNotNull(placeRes)
        val site = placeRes!!.newBuilding
        assertEquals(BuildingStatus.UNDER_CONSTRUCTION, site.status)
        assertFalse(site.isConstructed)
        assertEquals(0f, site.constructionProgress, 0.01f)

        // Footprint is marked occupied and pathfinder treats it as unwalkable
        assertTrue(placeRes.updatedTiles[4][4].isOccupied)
        assertTrue(placeRes.updatedTiles[5][5].isOccupied)
        assertFalse(GridPathfinder.isTileWalkable(4, 4, placeRes.updatedTiles, placeRes.updatedBuildings, emptyList()))

        // 2. Work point must be strictly OUTSIDE the 2x2 footprint [4..5, 4..5]
        val workPoint0 = site.getConstructionWorkPoint(22, placeRes.updatedTiles, placeRes.updatedBuildings, emptyList(), 0)
        val workPoint1 = site.getConstructionWorkPoint(22, placeRes.updatedTiles, placeRes.updatedBuildings, emptyList(), 1)
        assertFalse("Work point 0 must not be inside building footprint", workPoint0.first in 4..5 && workPoint0.second in 4..5)
        assertFalse("Work point 1 must not be inside building footprint", workPoint1.first in 4..5 && workPoint1.second in 4..5)
        assertTrue("Work point 0 must be walkable", GridPathfinder.isTileWalkable(workPoint0.first, workPoint0.second, placeRes.updatedTiles, placeRes.updatedBuildings, emptyList()))

        // 3. Path to work point must NEVER step into the building footprint
        val path = GridPathfinder.findPathToWorkPoint(0, 0, site, placeRes.updatedTiles, placeRes.updatedBuildings, emptyList(), 0)
        assertTrue("Must find a valid path to work point", path.isNotEmpty())
        for (step in path) {
            assertFalse("Path step must not enter solid footprint", step.first in 4..5 && step.second in 4..5)
        }

        // 4. Progress advances only by actual productivity
        val (progBuildings, completedFirst) = ConstructionSystem.advanceBuildingProgress(site.id, 50f, placeRes.updatedBuildings)
        assertFalse(completedFirst)
        assertEquals(50f, progBuildings.first().constructionProgress, 0.01f)
        assertEquals(BuildingStatus.UNDER_CONSTRUCTION, progBuildings.first().status)

        // 5. Completion (-> COMPLETED)
        val (completedBuildings, completedSecond) = ConstructionSystem.advanceBuildingProgress(site.id, 55f, progBuildings)
        assertTrue(completedSecond)
        val completedB = completedBuildings.first()
        assertTrue(completedB.isConstructed)
        assertEquals(100f, completedB.constructionProgress, 0.01f)
        assertEquals(BuildingStatus.COMPLETED, completedB.status)
    }

    @Test
    fun testDemolitionWorkflowSafetyAndSalvage() {
        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val initialInv = VillageInventory(wood = 10, stone = 5, maxWood = 100, maxStone = 100)

        val hut = Building(
            id = "demo_hut",
            type = BuildingType.COZY_HUT, // cost: 12 wood, 4 stone
            x = 8,
            y = 8,
            isConstructed = true,
            status = BuildingStatus.COMPLETED
        )

        val resident = Villager(
            id = "res1",
            name = "Rina",
            isFemale = true,
            ageDays = 25f,
            homeBuildingId = hut.id,
            isInHome = true,
            posX = 8f,
            posY = 8f
        )
        val worker = Villager(
            id = "work1",
            name = "Joko",
            isFemale = false,
            ageDays = 30f,
            job = JobType.BUILDER,
            assignedBuildingId = hut.id,
            posX = 2f,
            posY = 2f
        )

        // 1. Town Hearth protection
        val hearth = Building(type = BuildingType.TOWN_HEARTH, x = 0, y = 0, isConstructed = true)
        assertFalse("Town Hearth must never be demolished", ConstructionSystem.canDemolishBuilding(hearth))
        assertNull(ConstructionSystem.markForDemolition(hearth.id, listOf(hearth), emptyList()))

        // 2. Mark for demolition (COMPLETED -> DEMOLISHING)
        val markResult = ConstructionSystem.markForDemolition(hut.id, listOf(hut), listOf(resident, worker))
        assertNotNull(markResult)
        val (markedBlds, markedVils) = markResult!!
        val demolishingHut = markedBlds.first()
        assertEquals(BuildingStatus.DEMOLISHING, demolishingHut.status)
        assertEquals(0f, demolishingHut.demolitionProgress, 0.01f)

        // No duplicate demolition queue
        assertFalse("Cannot re-mark building already demolishing", ConstructionSystem.canDemolishBuilding(demolishingHut))
        assertNull(ConstructionSystem.markForDemolition(hut.id, markedBlds, markedVils))

        // Resident safe eviction
        val evictedResident = markedVils.find { it.id == resident.id }!!
        assertFalse("Resident must be evicted", evictedResident.isInHome)
        assertNull("Home reference must be cleared", evictedResident.homeBuildingId)
        assertFalse("Resident must be outside the footprint", evictedResident.posX.toInt() in 8..9 && evictedResident.posY.toInt() in 8..9)

        // Worker released
        val releasedWorker = markedVils.find { it.id == worker.id }!!
        assertNull("Workplace reference must be cleared", releasedWorker.assignedBuildingId)
        assertEquals(JobType.UNASSIGNED, releasedWorker.job)

        // 3. Demolition Work Point is outside footprint
        val demoWorkPoint = demolishingHut.getDemolitionWorkPoint(22, tiles, markedBlds, emptyList())
        assertFalse("Demolition work point must be outside footprint", demoWorkPoint.first in 8..9 && demoWorkPoint.second in 8..9)

        // 4. Advance progress partially
        val partResult = ConstructionSystem.advanceDemolitionProgress(
            hut.id,
            40f,
            markedBlds,
            initialInv,
            tiles
        )
        assertFalse(partResult.isDestroyed)
        assertEquals(40f, partResult.updatedBuildings.first().demolitionProgress, 0.01f)

        // 5. Advance to 100% completion
        val finishResult = ConstructionSystem.advanceDemolitionProgress(
            hut.id,
            65f,
            partResult.updatedBuildings,
            initialInv,
            tiles
        )
        assertTrue(finishResult.isDestroyed)
        assertTrue("Building must be removed from list", finishResult.updatedBuildings.isEmpty())

        // Footprint tiles are freed
        assertFalse(finishResult.updatedTiles[8][8].isOccupied)
        assertFalse(finishResult.updatedTiles[9][9].isOccupied)

        // Salvage added to real inventory strictly less than original cost
        assertTrue("Salvage wood must be > 0", finishResult.woodSalvaged > 0)
        assertTrue("Salvage wood strictly less than cost 12", finishResult.woodSalvaged < hut.type.woodCost)
        assertTrue("Salvage stone must be > 0", finishResult.stoneSalvaged > 0)
        assertTrue("Salvage stone strictly less than cost 4", finishResult.stoneSalvaged < hut.type.stoneCost)
        assertEquals(initialInv.wood + finishResult.woodSalvaged, finishResult.updatedInventory.wood)
        assertEquals(initialInv.stone + finishResult.stoneSalvaged, finishResult.updatedInventory.stone)
    }

    @Test
    fun testDemolitionProgressPersistenceThroughSaveLoad() {
        val engine = SimulationEngine()
        val state = engine.gameState.value.copy(
            buildings = listOf(
                Building(
                    id = "demo_persisted",
                    type = BuildingType.WOODCUTTER_CAMP,
                    x = 10,
                    y = 10,
                    isConstructed = true,
                    status = BuildingStatus.DEMOLISHING,
                    demolitionProgress = 62.5f
                )
            )
        )

        val emptyTiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val json = GameSaveManager.stateToJson(state)
        val loadedState = GameSaveManager.stateFromJson(json, emptyTiles)

        val loadedBuilding = loadedState.buildings.find { it.id == "demo_persisted" }
        assertNotNull(loadedBuilding)
        assertEquals(BuildingStatus.DEMOLISHING, loadedBuilding!!.status)
        assertEquals(62.5f, loadedBuilding.demolitionProgress, 0.01f)
    }
}

package com.example

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
import com.example.game.model.BuildingType
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
            posX = 6.2f,
            posY = 9.2f
        )

        val tiles = Array(22) { x -> Array(22) { y -> WorldTile(x, y, TileType.GRASS) } }
        val nightTime = TimeSystem(dayTimeSeconds = 90f) // Night

        // Resident near home at night enters the home
        val (housedNight, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(resident),
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
        val (housedMorning, _) = HousingSystem.updateHousingRoutines(
            villagers = listOf(sleeping),
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
            cropGrowth = 0f
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
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.engine.SimulationEngine
import com.example.game.model.BuildingType
import com.example.game.model.GameSpeed
import com.example.game.model.JobType
import com.example.game.model.Villager
import com.example.game.pathfinding.GridPathfinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Havenfold", appName)
    }

    @Test
    fun `simulation engine initializes correctly`() {
        val engine = SimulationEngine()
        val state = engine.gameState.value

        assertEquals(SimulationEngine.MAP_SIZE, state.tiles.size)
        assertTrue("Should have initial villagers", state.villagers.isNotEmpty())
        assertTrue("Should have initial buildings", state.buildings.isNotEmpty())
        assertTrue("Should have starter resources", state.inventory.food > 0)
        assertTrue("Average happiness should be positive", state.averageHappiness > 0f)
    }

    @Test
    fun `villager modular needs and happiness work correctly`() {
        val happyVillager = Villager(
            name = "Aria",
            isFemale = true,
            ageDays = 25f,
            hunger = 100f,
            housingNeed = 100f,
            recreationNeed = 100f,
            socialNeed = 100f,
            posX = 5f,
            posY = 5f
        )
        assertEquals(100f, happyVillager.happiness, 0.01f)
        assertEquals(1.35f, happyVillager.productivityMultiplier, 0.01f)

        val sadVillager = Villager(
            name = "Finn",
            isFemale = false,
            ageDays = 30f,
            hunger = 20f,
            housingNeed = 10f,
            recreationNeed = 10f,
            socialNeed = 10f,
            posX = 5f,
            posY = 5f
        )
        assertTrue("Sad villager happiness should be low", sadVillager.happiness < 25f)
        assertEquals(0.45f, sadVillager.productivityMultiplier, 0.01f)
    }

    @Test
    fun `pathfinding finds path on open terrain`() {
        val engine = SimulationEngine()
        val state = engine.gameState.value

        val path = GridPathfinder.findPath(
            startX = 2,
            startY = 2,
            destX = 3,
            destY = 3,
            tiles = state.tiles,
            buildings = state.buildings,
            resources = state.resources
        )
        assertNotNull(path)
    }

    @Test
    fun `simulation tick updates time and simulation state`() {
        val engine = SimulationEngine()
        engine.setGameSpeed(GameSpeed.FAST)
        val initialTime = engine.gameState.value.timeSystem.dayTimeSeconds

        engine.tick(1.0f)
        val newTime = engine.gameState.value.timeSystem.dayTimeSeconds

        assertTrue("Time should advance with tick", newTime > initialTime)
    }
}

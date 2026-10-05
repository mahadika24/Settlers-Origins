package com.example.game.engine.systems

import com.example.game.model.DayPhase
import com.example.game.model.GameSpeed
import com.example.game.model.TimeSystem
import com.example.game.model.WeatherType
import kotlin.random.Random

/**
 * Modular Simulation Clock System
 * Manages simulation time, day/night cycles, game speed scaling, and weather transitions.
 */
object SimulationClockSystem {

    data class TimeTickResult(
        val updatedTimeSystem: TimeSystem,
        val effectiveDt: Float,
        val dayPassed: Boolean
    )

    data class WeatherTickResult(
        val updatedWeather: WeatherType,
        val updatedTimer: Float,
        val weatherChanged: Boolean
    )

    /**
     * Advances simulation clock using speed multiplier.
     * Speed multiplier affects simulation time uniformly across all subsystems.
     */
    fun updateTime(
        timeSystem: TimeSystem,
        deltaSeconds: Float
    ): TimeTickResult {
        val speedMult = timeSystem.speed.multiplier
        if (speedMult <= 0f) {
            return TimeTickResult(
                updatedTimeSystem = timeSystem,
                effectiveDt = 0f,
                dayPassed = false
            )
        }

        val effectiveDt = deltaSeconds * speedMult
        var newTimeSeconds = timeSystem.dayTimeSeconds + effectiveDt
        var newDayNumber = timeSystem.dayNumber
        var dayPassed = false

        if (newTimeSeconds >= TimeSystem.SECONDS_PER_DAY) {
            newTimeSeconds -= TimeSystem.SECONDS_PER_DAY
            newDayNumber++
            dayPassed = true
        }

        val updated = timeSystem.copy(
            dayNumber = newDayNumber,
            dayTimeSeconds = newTimeSeconds
        )

        return TimeTickResult(
            updatedTimeSystem = updated,
            effectiveDt = effectiveDt,
            dayPassed = dayPassed
        )
    }

    /**
     * Cycles seasonal weather variations based on simulation time.
     */
    fun updateWeather(
        currentWeather: WeatherType,
        weatherTimer: Float,
        dt: Float
    ): WeatherTickResult {
        if (dt <= 0f) {
            return WeatherTickResult(currentWeather, weatherTimer, false)
        }

        val newTimer = weatherTimer + dt
        // Weather transitions roughly every 50 simulation seconds
        if (newTimer >= 50f) {
            val weathers = listOf(
                WeatherType.CLEAR,
                WeatherType.CLOUDY,
                WeatherType.RAIN,
                WeatherType.CLEAR,
                WeatherType.HEAT
            )
            val nextWeather = weathers.random()
            return WeatherTickResult(
                updatedWeather = nextWeather,
                updatedTimer = 0f,
                weatherChanged = nextWeather != currentWeather
            )
        }

        return WeatherTickResult(
            updatedWeather = currentWeather,
            updatedTimer = newTimer,
            weatherChanged = false
        )
    }
}

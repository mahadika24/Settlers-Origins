package com.example.game.engine

import android.content.Context
import android.util.Log
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
import com.example.game.model.TimeSystem
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WeatherType
import com.example.game.model.WorldTile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object GameSaveManager {
    private const val TAG = "GameSaveManager"
    private const val SAVE_FILE_NAME = "havenfold_save_v1.json"

    fun hasSave(context: Context): Boolean {
        val file = File(context.filesDir, SAVE_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun deleteSave(context: Context): Boolean {
        val file = File(context.filesDir, SAVE_FILE_NAME)
        return if (file.exists()) file.delete() else false
    }

    fun saveGame(context: Context, state: GameState): Boolean {
        return try {
            val root = JSONObject()

            // 1. Time & Weather
            val timeObj = JSONObject().apply {
                put("dayNumber", state.timeSystem.dayNumber)
                put("dayTimeSeconds", state.timeSystem.dayTimeSeconds.toDouble())
                put("speed", state.timeSystem.speed.name)
            }
            root.put("timeSystem", timeObj)
            root.put("weather", state.weather.name)
            root.put("weatherTimer", state.weatherTimer.toDouble())

            // 2. Inventory
            val invObj = JSONObject().apply {
                put("food", state.inventory.food)
                put("wood", state.inventory.wood)
                put("stone", state.inventory.stone)
                put("maxFood", state.inventory.maxFood)
                put("maxWood", state.inventory.maxWood)
                put("maxStone", state.inventory.maxStone)
            }
            root.put("inventory", invObj)

            // 3. Stats & Settings
            root.put("totalFoodHarvested", state.totalFoodHarvested)
            root.put("totalWoodHarvested", state.totalWoodHarvested)
            root.put("totalStoneHarvested", state.totalStoneHarvested)
            root.put("totalBirths", state.totalBirths)
            root.put("totalDeaths", state.totalDeaths)
            root.put("isAutoAssignEnabled", state.isAutoAssignEnabled)

            // Job priorities
            val prioritiesObj = JSONObject()
            for ((job, prio) in state.jobPriorities) {
                prioritiesObj.put(job.name, prio.name)
            }
            root.put("jobPriorities", prioritiesObj)

            // 4. Buildings
            val buildingsArray = JSONArray()
            for (b in state.buildings) {
                val bObj = JSONObject().apply {
                    put("id", b.id)
                    put("type", b.type.name)
                    put("x", b.x)
                    put("y", b.y)
                    put("isConstructed", b.isConstructed)
                    put("constructionProgress", b.constructionProgress.toDouble())
                    put("deliveredWood", b.deliveredWood)
                    put("deliveredStone", b.deliveredStone)
                    put("isTilled", b.isTilled)
                    put("cropGrowth", b.cropGrowth.toDouble())
                    put("foodStock", b.foodStock)
                    put("woodStock", b.woodStock)

                    val residentsArr = JSONArray()
                    b.residentIds.forEach { residentsArr.put(it) }
                    put("residentIds", residentsArr)

                    val workersArr = JSONArray()
                    b.assignedWorkerIds.forEach { workersArr.put(it) }
                    put("assignedWorkerIds", workersArr)
                }
                buildingsArray.put(bObj)
            }
            root.put("buildings", buildingsArray)

            // 5. Natural Resources
            val resourcesArray = JSONArray()
            for (r in state.resources) {
                val rObj = JSONObject().apply {
                    put("id", r.id)
                    put("type", r.type.name)
                    put("x", r.x)
                    put("y", r.y)
                    put("amount", r.amount)
                    put("maxAmount", r.maxAmount)
                    put("growthStage", r.growthStage)
                    put("growthProgress", r.growthProgress.toDouble())
                    put("markedForHarvest", r.markedForHarvest)
                }
                resourcesArray.put(rObj)
            }
            root.put("resources", resourcesArray)

            // 6. Villagers
            val villagersArray = JSONArray()
            for (v in state.villagers) {
                val vObj = JSONObject().apply {
                    put("id", v.id)
                    put("name", v.name)
                    put("isFemale", v.isFemale)
                    put("ageDays", v.ageDays.toDouble())
                    put("hunger", v.hunger.toDouble())
                    put("housingNeed", v.housingNeed.toDouble())
                    put("recreationNeed", v.recreationNeed.toDouble())
                    put("socialNeed", v.socialNeed.toDouble())
                    put("energy", v.energy.toDouble())
                    put("job", v.job.name)
                    put("homeBuildingId", v.homeBuildingId ?: "")
                    put("isInHome", v.isInHome)
                    put("assignedBuildingId", v.assignedBuildingId ?: "")
                    put("action", v.action.name)
                    put("carryingType", v.carryingType.name)
                    put("carryingAmount", v.carryingAmount)
                    put("posX", v.posX.toDouble())
                    put("posY", v.posY.toDouble())
                    put("partnerId", v.partnerId ?: "")
                    put("tunicColorHex", v.tunicColorHex)
                    put("statusMessage", v.statusMessage)

                    val parentsArr = JSONArray()
                    v.parentIds.forEach { parentsArr.put(it) }
                    put("parentIds", parentsArr)

                    val childrenArr = JSONArray()
                    v.childrenIds.forEach { childrenArr.put(it) }
                    put("childrenIds", childrenArr)
                }
                villagersArray.put(vObj)
            }
            root.put("villagers", villagersArray)

            // 7. Goals
            val goalsArray = JSONArray()
            for (g in state.goals) {
                val gObj = JSONObject().apply {
                    put("id", g.id)
                    put("title", g.title)
                    put("description", g.description)
                    put("current", g.current)
                    put("target", g.target)
                    put("isCompleted", g.isCompleted)
                    put("rewardText", g.rewardText)
                }
                goalsArray.put(gObj)
            }
            root.put("goals", goalsArray)

            // Write atomically to file
            val file = File(context.filesDir, SAVE_FILE_NAME)
            file.writeText(root.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan permainan: ${e.message}", e)
            false
        }
    }

    fun loadGame(context: Context, baseTiles: Array<Array<WorldTile>>): GameState? {
        val file = File(context.filesDir, SAVE_FILE_NAME)
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            val root = JSONObject(content)

            // 1. Time & Weather
            val timeObj = root.getJSONObject("timeSystem")
            val dayNumber = timeObj.getInt("dayNumber")
            val dayTimeSeconds = timeObj.getDouble("dayTimeSeconds").toFloat()
            val speedName = timeObj.optString("speed", GameSpeed.NORMAL.name)
            val speed = try { GameSpeed.valueOf(speedName) } catch (_: Exception) { GameSpeed.NORMAL }

            val timeSystem = TimeSystem(dayNumber = dayNumber, dayTimeSeconds = dayTimeSeconds, speed = speed)
            val weatherName = root.optString("weather", WeatherType.CLEAR.name)
            val weather = try { WeatherType.valueOf(weatherName) } catch (_: Exception) { WeatherType.CLEAR }
            val weatherTimer = root.optDouble("weatherTimer", 0.0).toFloat()

            // 2. Inventory
            val invObj = root.getJSONObject("inventory")
            val inventory = VillageInventory(
                food = invObj.getInt("food"),
                wood = invObj.getInt("wood"),
                stone = invObj.getInt("stone"),
                maxFood = invObj.optInt("maxFood", 80),
                maxWood = invObj.optInt("maxWood", 60),
                maxStone = invObj.optInt("maxStone", 40)
            )

            // 3. Stats & Settings
            val totalFood = root.optInt("totalFoodHarvested", 0)
            val totalWood = root.optInt("totalWoodHarvested", 0)
            val totalStone = root.optInt("totalStoneHarvested", 0)
            val totalBirths = root.optInt("totalBirths", 0)
            val totalDeaths = root.optInt("totalDeaths", 0)
            val isAutoAssign = root.optBoolean("isAutoAssignEnabled", false)

            val prioritiesMap = mutableMapOf<JobType, JobPriority>()
            val prioritiesObj = root.optJSONObject("jobPriorities")
            if (prioritiesObj != null) {
                for (key in prioritiesObj.keys()) {
                    try {
                        val job = JobType.valueOf(key)
                        val prio = JobPriority.valueOf(prioritiesObj.getString(key))
                        prioritiesMap[job] = prio
                    } catch (_: Exception) {}
                }
            }
            if (prioritiesMap.isEmpty()) {
                prioritiesMap[JobType.FARMER] = JobPriority.HIGH
                prioritiesMap[JobType.FORAGER] = JobPriority.HIGH
                prioritiesMap[JobType.WOODCUTTER] = JobPriority.MEDIUM
                prioritiesMap[JobType.MINER] = JobPriority.MEDIUM
                prioritiesMap[JobType.BUILDER] = JobPriority.LOW
            }

            // 4. Buildings
            val buildingsList = mutableListOf<Building>()
            val buildingsArray = root.getJSONArray("buildings")
            for (i in 0 until buildingsArray.length()) {
                val bObj = buildingsArray.getJSONObject(i)
                val typeName = bObj.getString("type")
                val type = try { BuildingType.valueOf(typeName) } catch (_: Exception) { continue }

                val residents = mutableListOf<String>()
                val residentsArr = bObj.optJSONArray("residentIds")
                if (residentsArr != null) {
                    for (r in 0 until residentsArr.length()) residents.add(residentsArr.getString(r))
                }

                val workers = mutableListOf<String>()
                val workersArr = bObj.optJSONArray("assignedWorkerIds")
                if (workersArr != null) {
                    for (w in 0 until workersArr.length()) workers.add(workersArr.getString(w))
                }

                buildingsList.add(
                    Building(
                        id = bObj.getString("id"),
                        type = type,
                        x = bObj.getInt("x"),
                        y = bObj.getInt("y"),
                        isConstructed = bObj.optBoolean("isConstructed", true),
                        constructionProgress = bObj.optDouble("constructionProgress", 100.0).toFloat(),
                        deliveredWood = bObj.optInt("deliveredWood", type.woodCost),
                        deliveredStone = bObj.optInt("deliveredStone", type.stoneCost),
                        assignedWorkerIds = workers,
                        residentIds = residents,
                        isTilled = bObj.optBoolean("isTilled", false),
                        cropGrowth = bObj.optDouble("cropGrowth", 0.0).toFloat(),
                        foodStock = bObj.optInt("foodStock", 0),
                        woodStock = bObj.optInt("woodStock", 0)
                    )
                )
            }

            // 5. Natural Resources
            val resourcesList = mutableListOf<NaturalResource>()
            val resourcesArray = root.getJSONArray("resources")
            for (i in 0 until resourcesArray.length()) {
                val rObj = resourcesArray.getJSONObject(i)
                val typeName = rObj.getString("type")
                val type = try { ResourceType.valueOf(typeName) } catch (_: Exception) { continue }

                resourcesList.add(
                    NaturalResource(
                        id = rObj.getString("id"),
                        type = type,
                        x = rObj.getInt("x"),
                        y = rObj.getInt("y"),
                        amount = rObj.optInt("amount", 10),
                        maxAmount = rObj.optInt("maxAmount", 10),
                        growthStage = rObj.optInt("growthStage", 2),
                        growthProgress = rObj.optDouble("growthProgress", 100.0).toFloat(),
                        markedForHarvest = rObj.optBoolean("markedForHarvest", false)
                    )
                )
            }

            // 6. Villagers
            val villagersList = mutableListOf<Villager>()
            val villagersArray = root.getJSONArray("villagers")
            for (i in 0 until villagersArray.length()) {
                val vObj = villagersArray.getJSONObject(i)
                val jobName = vObj.optString("job", JobType.UNASSIGNED.name)
                val job = try { JobType.valueOf(jobName) } catch (_: Exception) { JobType.UNASSIGNED }
                val actionName = vObj.optString("action", VillagerAction.IDLE.name)
                val action = try { VillagerAction.valueOf(actionName) } catch (_: Exception) { VillagerAction.IDLE }
                val carryName = vObj.optString("carryingType", CarryType.NONE.name)
                val carry = try { CarryType.valueOf(carryName) } catch (_: Exception) { CarryType.NONE }

                val homeId = vObj.optString("homeBuildingId").takeIf { it.isNotEmpty() }
                val partnerId = vObj.optString("partnerId").takeIf { it.isNotEmpty() }
                val assignedBId = vObj.optString("assignedBuildingId").takeIf { it.isNotEmpty() }

                val parents = mutableListOf<String>()
                val parentsArr = vObj.optJSONArray("parentIds")
                if (parentsArr != null) {
                    for (p in 0 until parentsArr.length()) parents.add(parentsArr.getString(p))
                }

                val children = mutableListOf<String>()
                val childrenArr = vObj.optJSONArray("childrenIds")
                if (childrenArr != null) {
                    for (c in 0 until childrenArr.length()) children.add(childrenArr.getString(c))
                }

                villagersList.add(
                    Villager(
                        id = vObj.getString("id"),
                        name = vObj.getString("name"),
                        isFemale = vObj.getBoolean("isFemale"),
                        ageDays = vObj.getDouble("ageDays").toFloat(),
                        hunger = vObj.optDouble("hunger", 100.0).toFloat(),
                        housingNeed = vObj.optDouble("housingNeed", 100.0).toFloat(),
                        recreationNeed = vObj.optDouble("recreationNeed", 85.0).toFloat(),
                        socialNeed = vObj.optDouble("socialNeed", 85.0).toFloat(),
                        energy = vObj.optDouble("energy", 100.0).toFloat(),
                        job = job,
                        homeBuildingId = homeId,
                        isInHome = vObj.optBoolean("isInHome", false),
                        assignedBuildingId = assignedBId,
                        action = action,
                        carryingType = carry,
                        carryingAmount = vObj.optInt("carryingAmount", 0),
                        posX = vObj.getDouble("posX").toFloat(),
                        posY = vObj.getDouble("posY").toFloat(),
                        partnerId = partnerId,
                        parentIds = parents,
                        childrenIds = children,
                        tunicColorHex = vObj.optLong("tunicColorHex", 0xFF5D9CEC),
                        statusMessage = vObj.optString("statusMessage", "Melanjutkan kehidupan di Havenfold")
                    )
                )
            }

            // 7. Goals
            val goalsList = mutableListOf<CommunityGoal>()
            val goalsArray = root.optJSONArray("goals")
            if (goalsArray != null) {
                for (i in 0 until goalsArray.length()) {
                    val gObj = goalsArray.getJSONObject(i)
                    goalsList.add(
                        CommunityGoal(
                            id = gObj.getString("id"),
                            title = gObj.getString("title"),
                            description = gObj.getString("description"),
                            current = gObj.getInt("current"),
                            target = gObj.getInt("target"),
                            isCompleted = gObj.getBoolean("isCompleted"),
                            rewardText = gObj.getString("rewardText")
                        )
                    )
                }
            }

            GameState(
                tiles = baseTiles,
                buildings = buildingsList,
                resources = resourcesList,
                villagers = villagersList,
                inventory = inventory,
                timeSystem = timeSystem,
                weather = weather,
                weatherTimer = weatherTimer,
                isAutoAssignEnabled = isAutoAssign,
                jobPriorities = prioritiesMap,
                events = listOf(
                    SimulationEvent(
                        day = timeSystem.dayNumber,
                        text = "Permainan berhasil dimuat dari data simpanan Havenfold 💾",
                        iconEmoji = "💾"
                    )
                ),
                goals = if (goalsList.isNotEmpty()) goalsList else emptyList(),
                totalFoodHarvested = totalFood,
                totalWoodHarvested = totalWood,
                totalStoneHarvested = totalStone,
                totalBirths = totalBirths,
                totalDeaths = totalDeaths
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat save game: ${e.message}", e)
            null
        }
    }
}

package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.JobType
import com.example.game.model.TimeSystem
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import java.util.UUID
import kotlin.random.Random

/**
 * Modular Population System
 * Handles life stages (Baby -> Child -> Adult -> Elder -> Death),
 * aging, reproduction, family tree links, and mortality.
 */
object PopulationSystem {

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

    data class PopulationTickResult(
        val updatedVillagers: List<Villager>,
        val newBirths: List<Villager>,
        val deceasedVillagers: List<Villager>
    )

    /**
     * Advances population aging and evaluates births & deaths.
     */
    fun updatePopulation(
        villagers: List<Villager>,
        buildings: List<Building>,
        inventory: VillageInventory,
        timeSystem: TimeSystem,
        dt: Float,
        dayPassed: Boolean,
        reproductionCooldown: Float
    ): Pair<PopulationTickResult, Float> {
        val survivingVillagers = mutableListOf<Villager>()
        val deceasedVillagers = mutableListOf<Villager>()
        val newBirths = mutableListOf<Villager>()

        val totalHousingCapacity = buildings.filter { it.isConstructed }.sumOf { it.type.housingCapacity }
        val avgHappiness = if (villagers.isNotEmpty()) villagers.map { it.happiness }.average().toFloat() else 50f

        // 1. Aging and Mortality Check
        val ageDelta = dt / TimeSystem.SECONDS_PER_DAY
        for (v in villagers) {
            val updatedAge = v.ageDays + ageDelta

            // Elder Mortality Check (higher chance past life expectancy; happier elders live longer)
            var died = false
            if (updatedAge >= 55f && dayPassed) {
                val maxLifeDays = if (v.happiness >= 80f) 85f else if (v.happiness >= 50f) 75f else 65f
                if (updatedAge > maxLifeDays && Random.nextFloat() < 0.08f) {
                    died = true
                }
            }

            // Starvation Mortality Check
            if (v.hunger <= 0f) {
                val deathChance = if (v.happiness < 25f) (dt * 0.035f) else (dt * 0.015f)
                if (Random.nextFloat() < deathChance) {
                    died = true
                }
            }

            if (died) {
                deceasedVillagers.add(v)
            } else {
                // Check if life stage transitioned to Adult from Child -> can now work
                val canWorkNow = (updatedAge >= 15f) && !v.isBaby && !v.isChild
                val finalJob = if (!canWorkNow && v.job != JobType.UNASSIGNED) JobType.UNASSIGNED else v.job
                survivingVillagers.add(v.copy(ageDays = updatedAge, job = finalJob))
            }
        }

        // Clean up partner references for deceased villagers
        if (deceasedVillagers.isNotEmpty()) {
            val deceasedIds = deceasedVillagers.map { it.id }.toSet()
            for (i in survivingVillagers.indices) {
                val sv = survivingVillagers[i]
                if (sv.partnerId != null && deceasedIds.contains(sv.partnerId)) {
                    survivingVillagers[i] = sv.copy(partnerId = null)
                }
            }
        }

        // 2. Pair Unpartnered Adults in the Same Home
        for (i in survivingVillagers.indices) {
            val v1 = survivingVillagers[i]
            if (v1.isAdult && v1.partnerId == null && v1.homeBuildingId != null) {
                val partnerCandidate = survivingVillagers.find { candidate ->
                    candidate.id != v1.id &&
                    candidate.isAdult &&
                    candidate.partnerId == null &&
                    candidate.homeBuildingId == v1.homeBuildingId &&
                    candidate.isFemale != v1.isFemale
                }
                if (partnerCandidate != null) {
                    val pIdx = survivingVillagers.indexOf(partnerCandidate)
                    if (pIdx != -1) {
                        survivingVillagers[i] = v1.copy(partnerId = partnerCandidate.id)
                        survivingVillagers[pIdx] = partnerCandidate.copy(partnerId = v1.id)
                    }
                }
            }
        }

        // 3. Reproduction Tick
        var newCooldown = reproductionCooldown + dt
        if (newCooldown >= 14.0f && timeSystem.isNight) {
            newCooldown = 0f

            // Happiness gate for reproduction:
            // >= 75%: 50% birth chance
            // 45..74%: 20% birth chance
            // < 45%: 0% birth chance (depressed community does not have babies)
            val birthChance = when {
                avgHappiness >= 75f -> 0.50f
                avgHappiness >= 45f -> 0.20f
                else -> 0.0f
            }

            if (birthChance > 0f && survivingVillagers.size < totalHousingCapacity && inventory.food >= 12) {
                val adultFemales = survivingVillagers.filter { it.isAdult && it.isFemale && it.partnerId != null && it.homeBuildingId != null }
                for (female in adultFemales) {
                    val partner = survivingVillagers.find { it.id == female.partnerId && it.homeBuildingId == female.homeBuildingId }
                    if (partner != null && Random.nextFloat() < birthChance) {
                        val isBabyGirl = Random.nextBoolean()
                        val babyName = if (isBabyGirl) FEMALE_NAMES.random() else MALE_NAMES.random()
                        val babyId = UUID.randomUUID().toString()

                        val baby = Villager(
                            id = babyId,
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
                            parentIds = listOf(partner.id, female.id),
                            posX = female.posX,
                            posY = female.posY,
                            isInHome = true,
                            tunicColorHex = TUNIC_COLORS.random(),
                            statusMessage = "Bayi mungil penerus kehangatan desa"
                        )
                        newBirths.add(baby)

                        // Update parents' childrenIds list
                        val fIdx = survivingVillagers.indexOf(female)
                        val pIdx = survivingVillagers.indexOf(partner)
                        if (fIdx != -1) survivingVillagers[fIdx] = female.copy(childrenIds = female.childrenIds + babyId)
                        if (pIdx != -1) survivingVillagers[pIdx] = partner.copy(childrenIds = partner.childrenIds + babyId)

                        break // One birth per cycle to prevent sudden baby boom
                    }
                }
            }
        }

        survivingVillagers.addAll(newBirths)

        return Pair(
            PopulationTickResult(
                updatedVillagers = survivingVillagers,
                newBirths = newBirths,
                deceasedVillagers = deceasedVillagers
            ),
            newCooldown
        )
    }
}

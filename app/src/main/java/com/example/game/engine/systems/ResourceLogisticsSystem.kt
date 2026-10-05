package com.example.game.engine.systems

import com.example.game.model.Building
import com.example.game.model.BuildingType
import com.example.game.model.CarryType
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.VillageInventory
import com.example.game.model.Villager
import com.example.game.model.VillagerAction
import com.example.game.model.WorldTile
import com.example.game.pathfinding.GridPathfinder

/**
 * Modular Resource & Logistics System
 * Governs physical resource harvesting, carrying mechanics, pathfinding to storages,
 * and inventory deposits.
 */
object ResourceLogisticsSystem {

    data class DeliveryResult(
        val updatedVillager: Villager,
        val updatedInventory: VillageInventory,
        val foodDelivered: Int = 0,
        val woodDelivered: Int = 0,
        val stoneDelivered: Int = 0
    )

    /**
     * Handles delivering carried items to storage points (Granary, Stone Quarry, or Town Hearth).
     */
    fun processDelivery(
        villager: Villager,
        buildings: List<Building>,
        resources: List<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        inventory: VillageInventory,
        storagePoint: Building
    ): DeliveryResult {
        var v = villager
        var inv = inventory
        var fDelivered = 0
        var wDelivered = 0
        var sDelivered = 0

        val stoneQuarry = buildings.find { it.type == BuildingType.STONE_QUARRY && it.isConstructed }
        val dropPoint = if (v.carryingType == CarryType.STONE && stoneQuarry != null) stoneQuarry else storagePoint

        val dist = Math.abs(v.posX - dropPoint.x) + Math.abs(v.posY - dropPoint.y)
        if (dist <= 1.4f) {
            when (v.carryingType) {
                CarryType.FOOD -> {
                    fDelivered = v.carryingAmount
                    inv = inv.copy(food = (inv.food + fDelivered).coerceAtMost(inv.maxFood))
                    v = v.copy(
                        carryingType = CarryType.NONE,
                        carryingAmount = 0,
                        action = VillagerAction.IDLE,
                        statusMessage = "Menyimpan $fDelivered bahan makanan di lumbung desa"
                    )
                }
                CarryType.WOOD -> {
                    wDelivered = v.carryingAmount
                    inv = inv.copy(wood = (inv.wood + wDelivered).coerceAtMost(inv.maxWood))
                    v = v.copy(
                        carryingType = CarryType.NONE,
                        carryingAmount = 0,
                        action = VillagerAction.IDLE,
                        statusMessage = "Menumpuk $wDelivered batang kayu di persediaan desa"
                    )
                }
                CarryType.STONE -> {
                    sDelivered = v.carryingAmount
                    inv = inv.copy(stone = (inv.stone + sDelivered).coerceAtMost(inv.maxStone))
                    v = v.copy(
                        carryingType = CarryType.NONE,
                        carryingAmount = 0,
                        action = VillagerAction.IDLE,
                        statusMessage = "Menimbun $sDelivered batu di Tempat Pengumpulan Batu"
                    )
                }
                CarryType.NONE -> {
                    v = v.copy(action = VillagerAction.IDLE)
                }
            }
        } else {
            if (v.path.isEmpty()) {
                val path = GridPathfinder.findPath(
                    v.posX.toInt(), v.posY.toInt(),
                    dropPoint.x, dropPoint.y,
                    tiles, buildings, resources
                )
                val deliverLabel = when (v.carryingType) {
                    CarryType.STONE -> "Mengangkut batu ke Tempat Pengumpulan"
                    CarryType.WOOD -> "Mengangkut kayu ke perbekalan"
                    else -> "Mengangkut hasil panen ke lumbung"
                }
                v = v.copy(action = VillagerAction.DELIVERING, path = path, statusMessage = deliverLabel)
            }
        }

        return DeliveryResult(
            updatedVillager = v,
            updatedInventory = inv,
            foodDelivered = fDelivered,
            woodDelivered = wDelivered,
            stoneDelivered = sDelivered
        )
    }

    data class HarvestResult(
        val updatedVillager: Villager,
        val updatedResources: List<NaturalResource>
    )

    /**
     * Executes woodcutting on mature trees.
     */
    fun harvestTree(
        villager: Villager,
        tree: NaturalResource,
        resources: List<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>
    ): HarvestResult {
        var v = villager
        val resList = resources.toMutableList()
        val dist = Math.abs(v.posX - tree.x) + Math.abs(v.posY - tree.y)

        if (dist <= 1.4f) {
            val chopPower = (3.5f * v.productivityMultiplier).toInt().coerceAtLeast(1)
            val newAmount = tree.amount - chopPower
            val idx = resList.indexOf(tree)

            if (newAmount <= 0) {
                if (idx != -1) resList.removeAt(idx)
                v = v.copy(
                    action = VillagerAction.CHOPPING,
                    carryingType = CarryType.WOOD,
                    carryingAmount = 6,
                    statusMessage = "Pohon berhasil ditebang menjadi kayu"
                )
            } else {
                if (idx != -1) resList[idx] = tree.copy(amount = newAmount)
                v = v.copy(action = VillagerAction.CHOPPING, statusMessage = "Mengayun kapak menebang pohon")
            }
        } else if (v.path.isEmpty()) {
            val path = GridPathfinder.findPath(
                v.posX.toInt(), v.posY.toInt(),
                tree.x, tree.y,
                tiles, buildings, resources
            )
            v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Menuju pohon untuk menebang")
        }

        return HarvestResult(v, resList)
    }

    /**
     * Executes mining on rock outcrops / quarry nodes.
     */
    fun harvestRock(
        villager: Villager,
        rock: NaturalResource,
        resources: List<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>
    ): HarvestResult {
        var v = villager
        val resList = resources.toMutableList()
        val dist = Math.abs(v.posX - rock.x) + Math.abs(v.posY - rock.y)

        if (dist <= 1.45f) {
            val minePower = (3.5f * v.productivityMultiplier).toInt().coerceAtLeast(1)
            val newAmount = rock.amount - minePower
            val idx = resList.indexOf(rock)

            if (newAmount <= 0) {
                if (idx != -1) resList.removeAt(idx)
                v = v.copy(
                    action = VillagerAction.MINING,
                    carryingType = CarryType.STONE,
                    carryingAmount = 5,
                    statusMessage = "Batu karang selesai dipahat menjadi batu potong"
                )
            } else {
                if (idx != -1) resList[idx] = rock.copy(amount = newAmount)
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

        return HarvestResult(v, resList)
    }

    /**
     * Executes berry gathering from wild bushes.
     */
    fun harvestBerries(
        villager: Villager,
        bush: NaturalResource,
        resources: List<NaturalResource>,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>
    ): HarvestResult {
        var v = villager
        val resList = resources.toMutableList()
        val dist = Math.abs(v.posX - bush.x) + Math.abs(v.posY - bush.y)

        if (dist <= 1.4f) {
            val idx = resList.indexOf(bush)
            if (idx != -1) resList[idx] = bush.copy(amount = 0)
            v = v.copy(
                action = VillagerAction.FORAGING,
                carryingType = CarryType.FOOD,
                carryingAmount = 4,
                statusMessage = "Memetik buah beri manis dari semak rimba"
            )
        } else if (v.path.isEmpty()) {
            val path = GridPathfinder.findPath(
                v.posX.toInt(), v.posY.toInt(),
                bush.x, bush.y,
                tiles, buildings, resources
            )
            v = v.copy(action = VillagerAction.WALKING_TO, path = path, statusMessage = "Mencari semak buah beri")
        }

        return HarvestResult(v, resList)
    }
}

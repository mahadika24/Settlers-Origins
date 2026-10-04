package com.example.game.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class TileType(val label: String, val color: Color, val isWalkable: Boolean, val canBuild: Boolean) {
    GRASS("Padang Rumput", Color(0xFF6B9E46), true, true),
    FERTILE_SOIL("Tanah Subur", Color(0xFF8F6E4A), true, true),
    WATER("Sungai", Color(0xFF4FA0C9), false, false),
    STONE_OUTCROP("Batu Karang", Color(0xFF808588), false, false)
}

data class WorldTile(
    val x: Int,
    val y: Int,
    val type: TileType,
    val isOccupied: Boolean = false,
    val buildingId: String? = null,
    val resourceId: String? = null,
    val hasRoad: Boolean = false
)

enum class BuildingType(
    val title: String,
    val description: String,
    val width: Int,
    val height: Int,
    val woodCost: Int,
    val stoneCost: Int,
    val maxWorkers: Int,
    val housingCapacity: Int,
    val defaultJob: JobType = JobType.UNASSIGNED,
    val iconEmoji: String
) {
    TOWN_HEARTH(
        title = "Pusat Desa (Hearth)",
        description = "Pusat kehangatan desa. Menyediakan pembagian makanan dan area temu warga.",
        width = 2,
        height = 2,
        woodCost = 0,
        stoneCost = 0,
        maxWorkers = 0,
        housingCapacity = 4,
        iconEmoji = "🔥"
    ),
    COZY_HUT(
        title = "Pondok Kayu (Hut)",
        description = "Hunian nyaman untuk 4 penduduk agar dapat beristirahat dan berkeluarga.",
        width = 2,
        height = 2,
        woodCost = 12,
        stoneCost = 4,
        maxWorkers = 0,
        housingCapacity = 4,
        iconEmoji = "🛖"
    ),
    FAMILY_HOMESTEAD(
        title = "Rumah Keluarga Nyaman",
        description = "Hunian lapang berkualitas tinggi untuk 6 penduduk, memberikan kenyamanan hunian prima.",
        width = 2,
        height = 2,
        woodCost = 18,
        stoneCost = 6,
        maxWorkers = 0,
        housingCapacity = 6,
        iconEmoji = "🏡"
    ),
    RECREATION_PARK(
        title = "Taman Bunga Rekreasi",
        description = "Taman dengan air mancur dan bunga harum. Memenuhi kebutuhan rekreasi warga.",
        width = 2,
        height = 2,
        woodCost = 10,
        stoneCost = 4,
        maxWorkers = 0,
        housingCapacity = 0,
        iconEmoji = "🌸"
    ),
    COMMUNITY_PLAZA(
        title = "Balai Temu Komunitas",
        description = "Tempat berkumpul, bercerita, dan bermusik. Memenuhi kebutuhan sosialisasi warga.",
        width = 2,
        height = 2,
        woodCost = 14,
        stoneCost = 6,
        maxWorkers = 0,
        housingCapacity = 0,
        iconEmoji = "🎪"
    ),
    WHEAT_FIELD(
        title = "Ladang Gandum",
        description = "Lahan pertanian pangan utama. Dikelola oleh petani secara mandiri.",
        width = 2,
        height = 2,
        woodCost = 4,
        stoneCost = 0,
        maxWorkers = 2,
        housingCapacity = 0,
        defaultJob = JobType.FARMER,
        iconEmoji = "🌾"
    ),
    PUMPKIN_PATCH(
        title = "Kebun Labu",
        description = "Tanaman bergizi tinggi dengan panen berlimpah untuk persediaan desa.",
        width = 2,
        height = 2,
        woodCost = 6,
        stoneCost = 2,
        maxWorkers = 2,
        housingCapacity = 0,
        defaultJob = JobType.FARMER,
        iconEmoji = "🎃"
    ),
    FORAGER_HUT(
        title = "Gubuk Pengumpul Beri",
        description = "Tempat pengumpul mencari buah beri dan rempah alami di semak-semak hutan sekitar.",
        width = 2,
        height = 2,
        woodCost = 8,
        stoneCost = 2,
        maxWorkers = 2,
        housingCapacity = 0,
        defaultJob = JobType.FORAGER,
        iconEmoji = "🫐"
    ),
    WOODCUTTER_CAMP(
        title = "Kemah Penebang Kayu",
        description = "Tempat penebang kayu memanen pohon rimba dan mengumpulkan suplai kayu desa.",
        width = 2,
        height = 2,
        woodCost = 6,
        stoneCost = 2,
        maxWorkers = 2,
        housingCapacity = 0,
        defaultJob = JobType.WOODCUTTER,
        iconEmoji = "🪓"
    ),
    STONE_QUARRY(
        title = "Tempat Pengumpulan Batu",
        description = "Pos pemahat dan pengumpul batu untuk memecah batuan alami dan menimbun cadangan batu desa.",
        width = 2,
        height = 2,
        woodCost = 8,
        stoneCost = 0,
        maxWorkers = 2,
        housingCapacity = 0,
        defaultJob = JobType.MINER,
        iconEmoji = "⛏️"
    ),
    GRANARY(
        title = "Lumbung Pangan",
        description = "Menambah kapasitas penyimpanan makanan (+60) dan kayu (+50).",
        width = 2,
        height = 2,
        woodCost = 16,
        stoneCost = 8,
        maxWorkers = 1,
        housingCapacity = 0,
        defaultJob = JobType.BUILDER,
        iconEmoji = "🏛️"
    ),
    WELL(
        title = "Sumur Air Segar",
        description = "Menyediakan air bersih yang mempercepat pertumbuhan ladang sekitar.",
        width = 1,
        height = 1,
        woodCost = 4,
        stoneCost = 6,
        maxWorkers = 0,
        housingCapacity = 0,
        iconEmoji = "💧"
    )
}

data class Building(
    val id: String = UUID.randomUUID().toString(),
    val type: BuildingType,
    val x: Int,
    val y: Int,
    val isConstructed: Boolean = true,
    val constructionProgress: Float = 100f, // 0..100
    val deliveredWood: Int = 0,
    val deliveredStone: Int = 0,
    val assignedWorkerIds: List<String> = emptyList(),
    val residentIds: List<String> = emptyList(),
    // Farming specific state
    val isTilled: Boolean = false,
    val cropGrowth: Float = 0f, // 0..100%
    val foodStock: Int = 0,
    val woodStock: Int = 0
) {
    val isFullyStockedForBuild: Boolean
        get() = deliveredWood >= type.woodCost && deliveredStone >= type.stoneCost
}

enum class ResourceType(val label: String, val iconEmoji: String) {
    TREE("Pohon Rimba", "🌲"),
    BERRY_BUSH("Semak Buah Beri", "🫐"),
    ROCK("Batuan Alami", "🪨")
}

data class NaturalResource(
    val id: String = UUID.randomUUID().toString(),
    val type: ResourceType,
    val x: Int,
    val y: Int,
    val amount: Int = 10,
    val maxAmount: Int = 10,
    val growthStage: Int = 2, // 0: Sapling/Tunas, 1: Muda, 2: Matang
    val growthProgress: Float = 100f,
    val markedForHarvest: Boolean = false
)

enum class JobType(val title: String, val iconEmoji: String, val color: Color) {
    UNASSIGNED("Warga Bebas", "🧑", Color(0xFF8D7B68)),
    FARMER("Petani", "🌾", Color(0xFFD4A338)),
    BUILDER("Tukang Bangun", "🔨", Color(0xFFC86D3B)),
    WOODCUTTER("Penebang Kayu", "🪓", Color(0xFF5E8B4E)),
    MINER("Pengumpul Batu", "⛏️", Color(0xFF607D8B)),
    FORAGER("Pengumpul Beri", "🫐", Color(0xFF7E57C2))
}

enum class VillagerAction(val label: String, val emoji: String) {
    IDLE("Bersantai", "🌿"),
    WANDERING("Berjalan Santai", "🚶"),
    WALKING_TO("Menuju Lokasi", "🏃"),
    TILLING("Mengolah Ladang", "🌱"),
    HARVESTING("Memanen Hasil", "🌾"),
    CHOPPING("Menebang Pohon", "🪓"),
    MINING("Menambang Batu", "⛏️"),
    FORAGING("Memetik Beri", "🫐"),
    BUILDING("Membangun", "🔨"),
    DELIVERING("Membawa Bahan", "📦"),
    EATING("Menyantap Makanan", "🍞"),
    SLEEPING("Tidur & Beristirahat", "💤"),
    PROCREATING("Bercengkerama di Rumah", "❤️"),
    RECREATING("Menikmati Taman", "🌸"),
    SOCIALIZING("Berbincang Warga", "💬")
}

enum class CarryType(val label: String, val emoji: String) {
    NONE("Kosong", ""),
    WOOD("Kayu", "🪵"),
    STONE("Batu", "🪨"),
    FOOD("Makanan", "🍞")
}

data class Villager(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isFemale: Boolean,
    val ageDays: Float, // Age in simulation days (0..14: Anak, 15..54: Dewasa, 55+: Lansia)
    // Modular Needs System (0..100)
    val hunger: Float = 100f,         // 100: kenyang, 0: kelaparan hebat
    val housingNeed: Float = 100f,    // 100: hunian nyaman, 0: terlantar tanpa rumah
    val recreationNeed: Float = 85f,  // 100: segar & terhibur, 0: jenuh/stres
    val socialNeed: Float = 85f,      // 100: terkoneksi komunitas, 0: kesepian
    val energy: Float = 100f,         // 100: berenergi, 0: kelelahan
    val job: JobType = JobType.UNASSIGNED,
    val homeBuildingId: String? = null,
    val assignedBuildingId: String? = null,
    val action: VillagerAction = VillagerAction.IDLE,
    val carryingType: CarryType = CarryType.NONE,
    val carryingAmount: Int = 0,
    val posX: Float,
    val posY: Float,
    val targetTileX: Int? = null,
    val targetTileY: Int? = null,
    val path: List<Pair<Int, Int>> = emptyList(),
    val partnerId: String? = null,
    val tunicColorHex: Long = 0xFF5D9CEC,
    val statusMessage: String = "Merasa damai di Havenfold"
) {
    val isChild: Boolean get() = ageDays < 15f
    val isElder: Boolean get() = ageDays >= 55f
    val isAdult: Boolean get() = ageDays in 15f..54.9f
    val canWork: Boolean get() = isAdult || (isElder && hunger > 20f)

    val lifeStageLabel: String
        get() = when {
            isChild -> "Anak-anak"
            isElder -> "Lansia"
            else -> "Dewasa"
        }

    // Composite Happiness Score (0..100)
    val happiness: Float
        get() = (hunger * 0.35f + housingNeed * 0.25f + recreationNeed * 0.20f + socialNeed * 0.20f).coerceIn(0f, 100f)

    // Productivity Factor based on Happiness:
    // >= 80%: 1.35x (Bonus Semangat)
    // 50..79%: 1.00x (Normal)
    // 25..49%: 0.70x (Lesu)
    // < 25%: 0.45x (Depresi/Sangat Lambat)
    val productivityMultiplier: Float
        get() = when {
            happiness >= 80f -> 1.35f
            happiness >= 50f -> 1.00f
            happiness >= 25f -> 0.70f
            else -> 0.45f
        }

    val happinessLabel: String
        get() = when {
            happiness >= 80f -> "Sangat Bahagia"
            happiness >= 60f -> "Puas"
            happiness >= 40f -> "Cukup"
            happiness >= 25f -> "Gelisah"
            else -> "Sangat Sedih"
        }

    val happinessEmoji: String
        get() = when {
            happiness >= 80f -> "😊"
            happiness >= 60f -> "🙂"
            happiness >= 40f -> "😐"
            happiness >= 25f -> "🙁"
            else -> "😢"
        }
}

enum class GameSpeed(val multiplier: Float, val label: String) {
    PAUSED(0f, "0× (Jeda)"),
    NORMAL(1f, "1× (Normal)"),
    FAST(2f, "2× (Cepat)"),
    TURBO(3.5f, "3.5× (Kilat)")
}

enum class DayPhase(val label: String, val icon: String, val skyColor: Color, val ambientLight: Float) {
    DAWN("Fajar", "🌅", Color(0xFFF9D29B), 0.75f),
    DAY("Siang", "☀️", Color(0xFFEBF6FA), 1.0f),
    DUSK("Senja", "🌇", Color(0xFFF39C6B), 0.70f),
    NIGHT("Malam", "🌙", Color(0xFF1D263B), 0.35f)
}

data class TimeSystem(
    val dayNumber: Int = 1,
    val dayTimeSeconds: Float = 20f,
    val speed: GameSpeed = GameSpeed.NORMAL
) {
    companion object {
        const val SECONDS_PER_DAY = 100f
    }

    val progressOfDay: Float get() = (dayTimeSeconds % SECONDS_PER_DAY) / SECONDS_PER_DAY

    val phase: DayPhase
        get() = when (progressOfDay) {
            in 0.00f..0.15f -> DayPhase.DAWN
            in 0.15f..0.65f -> DayPhase.DAY
            in 0.65f..0.78f -> DayPhase.DUSK
            else -> DayPhase.NIGHT
        }

    val isNight: Boolean get() = phase == DayPhase.NIGHT
    val hourOfDay: Int get() = ((progressOfDay * 24) + 6).toInt() % 24
    val clockString: String get() = String.format("%02d:00", hourOfDay)
}

data class VillageInventory(
    val food: Int = 30,
    val wood: Int = 24,
    val stone: Int = 12,
    val maxFood: Int = 80,
    val maxWood: Int = 60,
    val maxStone: Int = 40
)

data class SimulationEvent(
    val id: String = UUID.randomUUID().toString(),
    val day: Int,
    val text: String,
    val iconEmoji: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class CommunityGoal(
    val id: String,
    val title: String,
    val description: String,
    val current: Int,
    val target: Int,
    val isCompleted: Boolean = false,
    val rewardText: String
)

package vn.guildmaster.core

enum class HeroClass(val label: String, val weapon: String) {
    WARRIOR("Chiến binh", "Kiếm"),
    MAGE("Pháp sư", "Trượng"),
    ARCHER("Cung thủ", "Cung"),
    PRIEST("Tu sĩ", "Chuỳ"),
}

enum class Rarity(
    val label: String,
    val heroMul: Double,
    val itemMul: Double,
    val sellMul: Int,
    val hireMul: Int,
    val traits: Int,
) {
    COMMON("Thường", 1.0, 1.0, 1, 1, 0),
    RARE("Hiếm", 1.15, 1.3, 2, 3, 1),
    EPIC("Sử thi", 1.35, 1.7, 4, 10, 1),
    LEGENDARY("Huyền thoại", 1.6, 2.2, 8, 30, 2),
}

enum class Slot(val label: String, val noun: String) {
    WEAPON("Vũ khí", ""),
    HELMET("Mũ", "Mũ"),
    ARMOR("Giáp", "Giáp"),
    GLOVES("Găng", "Găng tay"),
    BOOTS("Giày", "Giày"),
    ACCESSORY("Phụ kiện", "Bùa"),
}

/** pct = giá trị là phần trăm. */
enum class StatType(val label: String, val pct: Boolean) {
    HP("HP", false),
    ATK("ATK", false),
    DEF("DEF", false),
    SPD("SPD", false),
    CRIT("Crit", true),
    CRIT_DMG("CritDMG", true),
    HP_PCT("HP", true),
    ATK_PCT("ATK", true),
    DEF_PCT("DEF", true),
}

data class Stat(val type: StatType, val value: Double)

enum class Trait(val label: String, val desc: String) {
    BRAVE("Gan dạ", "+10% HP"),
    FIERCE("Hung hãn", "+10% ATK"),
    STURDY("Cứng cỏi", "+10% DEF"),
    SWIFT("Nhanh nhẹn", "+8 SPD"),
    KEEN("Tinh mắt", "+6% Crit"),
    RUTHLESS("Tàn nhẫn", "+25% CritDMG"),
    QUICK("Học nhanh", "+15% EXP"),
    LUCKY("May mắn", "+10% tỉ lệ rơi đồ"),
}

class Item(
    val id: Long,
    val slot: Slot,
    val cls: HeroClass?,
    val rarity: Rarity,
    val itemLevel: Int,
    val main: Stat,
    val subs: List<Stat>,
) {
    val name: String get() = if (slot == Slot.WEAPON) cls?.weapon ?: "Vũ khí" else slot.noun
    val sellPrice: Long get() = 10L * itemLevel * rarity.sellMul
    fun fits(c: HeroClass) = slot != Slot.WEAPON || cls == c
}

class Hero(
    val id: Long,
    val name: String,
    val cls: HeroClass,
    val rarity: Rarity,
    var level: Int,
    var exp: Long,
    val traits: List<Trait>,
    val equipment: MutableMap<Slot, Long> = mutableMapOf(),
    var injuredUntil: Long = 0,
)

class MonsterSpec(val species: Int, val level: Int)

class Quest(
    val id: Long,
    val zone: Int,
    val level: Int,
    val minutes: Int,
    val monsters: List<MonsterSpec>,
    val gold: Long,
    val exp: Long,
    val drops: Int,
)

class Outcome(
    val win: Boolean,
    val gold: Long,
    val exp: Long,
    val items: List<Item>,
    val fallen: List<Long>,
    val logId: Long,
)

class Expedition(
    val quest: Quest,
    val heroIds: List<Long>,
    val startAt: Long,
    val endAt: Long,
    var outcome: Outcome? = null,
)

enum class EventKind { DAMAGE, HEAL, BUFF, DEBUFF, REVIVE, DOWN, STUNNED }

/** heroSide: phe của actor (với DOWN là phe của kẻ ngã). */
class BattleEvent(
    val heroSide: Boolean,
    val actor: String,
    val skill: String,
    val kind: EventKind,
    val target: String,
    val amount: Int,
    val crit: Boolean,
)

class BattleLog(
    val id: Long,
    val at: Long,
    val title: String,
    val win: Boolean,
    val rounds: Int,
    val heroes: List<String>,
    val events: List<BattleEvent>,
)

class GameState(val seed: Long) {
    var gold = 0L
    var guildExp = 0
    var guildLevel = 1
    var lastSeen = 0L
    var nextId = 1L
    val heroes = mutableListOf<Hero>()
    val items = mutableListOf<Item>()
    var boardPeriod = -1L
    val board = mutableListOf<Quest>()
    var tavernPeriod = -1L
    var tavernRoll = 0
    val tavern = mutableListOf<Hero>()
    val expeditions = mutableListOf<Expedition>()
    val logs = mutableListOf<BattleLog>()

    fun newId() = nextId++
    fun hero(id: Long) = heroes.find { it.id == id }
    fun item(id: Long) = items.find { it.id == id }
}

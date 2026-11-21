package vn.guildmaster.core

import kotlin.math.max
import kotlin.math.roundToLong

class GameError(msg: String) : Exception(msg)

class ClaimSummary(
    val win: Boolean,
    val gold: Long,
    val exp: Long,
    val items: List<Item>,
    val autoSoldGold: Long,
    val levelUps: List<String>,
    val injured: List<String>,
    val guildLevelUp: Boolean,
    val logId: Long,
)

/** Mọi thao tác nhận `now` từ ngoài; không tự đọc đồng hồ. */
class Game(val s: GameState) {
    companion object {
        const val BOARD_PERIOD = 4 * 3600_000L
        const val TAVERN_PERIOD = 3 * 3600_000L
        const val MAX_ITEMS = 60
        const val MAX_HEROES = 12
        const val MAX_LOGS = 30
        const val MAX_TEAM = 4

        fun newGame(now: Long, seed: Long): GameState {
            val s = GameState(seed)
            s.gold = 300
            s.lastSeen = now
            val rng = Rng(Gen.mix(seed, 1))
            s.heroes += Gen.hero(rng, s.newId(), HeroClass.WARRIOR, Rarity.COMMON, 1)
            s.heroes += Gen.hero(rng, s.newId(), HeroClass.PRIEST, Rarity.COMMON, 1)
            Game(s).tick(now)
            return s
        }
    }

    /** Giờ máy bị chỉnh lùi thì giữ mốc lastSeen. */
    fun clock(now: Long): Long {
        val t = max(now, s.lastSeen)
        s.lastSeen = t
        return t
    }

    val maxParties get() = when {
        s.guildLevel >= 10 -> 3
        s.guildLevel >= 5 -> 2
        else -> 1
    }
    val guildNeed get() = 5 * s.guildLevel
    val refreshCost get() = 50L * s.guildLevel
    val inventory: List<Item>
        get() {
            val worn = equippedIds()
            return s.items.filter { it.id !in worn }
        }

    fun equippedIds(): Set<Long> = s.heroes.flatMap { it.equipment.values }.toSet()
    fun owner(itemId: Long): Hero? = s.heroes.find { itemId in it.equipment.values }
    fun isAway(heroId: Long) = s.expeditions.any { heroId in it.heroIds }
    fun isInjured(h: Hero, now: Long) = h.injuredUntil > max(now, s.lastSeen)
    fun isFree(h: Hero, now: Long) = !isAway(h.id) && !isInjured(h, now)
    fun stats(h: Hero, equipment: Map<Slot, Long> = h.equipment) = Stats.of(h, equipment) { s.item(it) }
    fun nextBoardAt(now: Long) = (max(now, s.lastSeen) / BOARD_PERIOD + 1) * BOARD_PERIOD
    fun nextTavernAt(now: Long) = (max(now, s.lastSeen) / TAVERN_PERIOD + 1) * TAVERN_PERIOD

    fun enemyPower(q: Quest) = q.monsters.sumOf { Stats.monster(it).power }
    fun teamPower(ids: Collection<Long>) = ids.mapNotNull { s.hero(it) }.sumOf { stats(it).power }

    /** Làm mới bảng/quán theo kỳ, giải quyết đội đã về. Trả true nếu trạng thái đổi. */
    fun tick(now: Long): Boolean {
        val t = clock(now)
        var changed = false
        val bp = t / BOARD_PERIOD
        if (bp != s.boardPeriod) {
            s.boardPeriod = bp
            s.board.clear()
            s.board += Gen.board(s, bp)
            changed = true
        }
        val tp = t / TAVERN_PERIOD
        if (tp != s.tavernPeriod) {
            s.tavernPeriod = tp
            s.tavernRoll = 0
            s.tavern.clear()
            s.tavern += Gen.candidates(s)
            changed = true
        }
        for (e in s.expeditions) if (e.outcome == null && t >= e.endAt) {
            resolve(e)
            changed = true
        }
        return changed
    }

    private fun monsterFighters(q: Quest): List<Fighter> {
        val counts = q.monsters.groupingBy { it.species }.eachCount()
        val seen = mutableMapOf<Int, Int>()
        return q.monsters.map { m ->
            val sp = Data.species(m.species)
            val n = (seen[m.species] ?: 0) + 1
            seen[m.species] = n
            val name = if ((counts[m.species] ?: 1) > 1) "${sp.name} $n" else sp.name
            Fighter(name, false, null, Stats.monster(m), listOfNotNull(sp.skill))
        }
    }

    private fun resolve(e: Expedition) {
        val q = e.quest
        val team = e.heroIds.mapNotNull { s.hero(it) }
        val heroes = team.map { Fighter(it.name, true, it.cls, stats(it), Stats.skills(it)) }
        val r = Battle.run(heroes, monsterFighters(q), Gen.mix(s.seed, q.id))
        val items = mutableListOf<Item>()
        if (r.win) {
            val rng = Rng(Gen.mix(s.seed, q.id xor 0x5EEDL))
            val luck = team.count { Trait.LUCKY in it.traits } * 0.10
            repeat(q.drops) { if (rng.chance(0.6 + luck)) items += Gen.item(rng, s.newId(), q.level) }
        }
        val fallen = if (r.win) emptyList() else team.indices.filter { it !in r.survivors }.map { team[it].id }
        val log = BattleLog(
            s.newId(), e.endAt, "${Data.zones[q.zone].name} · Lv ${q.level}",
            r.win, r.rounds, team.map { it.name }, r.log,
        )
        s.logs.add(0, log)
        while (s.logs.size > MAX_LOGS) s.logs.removeAt(s.logs.lastIndex)
        e.outcome = Outcome(
            r.win,
            if (r.win) q.gold else q.gold / 4,
            if (r.win) q.exp else q.exp / 4,
            items, fallen, log.id,
        )
    }

    fun send(questId: Long, heroIds: List<Long>, now: Long): Expedition {
        val t = clock(now)
        val q = s.board.find { it.id == questId } ?: throw GameError("Nhiệm vụ không còn trên bảng")
        if (heroIds.isEmpty() || heroIds.size > MAX_TEAM) throw GameError("Đội cần 1–$MAX_TEAM người")
        if (heroIds.toSet().size != heroIds.size) throw GameError("Trùng người trong đội")
        if (s.expeditions.size >= maxParties) throw GameError("Đã đủ $maxParties đội đang đi")
        for (id in heroIds) {
            val h = s.hero(id) ?: throw GameError("Không tìm thấy mạo hiểm giả")
            if (!isFree(h, t)) throw GameError("${h.name} không sẵn sàng")
        }
        s.board.remove(q)
        val e = Expedition(q, heroIds, t, t + q.minutes * 60_000L)
        s.expeditions += e
        return e
    }

    fun claim(questId: Long, now: Long): ClaimSummary {
        clock(now)
        val e = s.expeditions.find { it.quest.id == questId } ?: throw GameError("Không tìm thấy đội")
        val o = e.outcome ?: throw GameError("Đội chưa về")
        s.expeditions.remove(e)
        s.gold += o.gold

        val levelUps = mutableListOf<String>()
        for (id in e.heroIds) {
            val h = s.hero(id) ?: continue
            val gain = if (Trait.QUICK in h.traits) (o.exp * 1.15).roundToLong() else o.exp
            val before = h.level
            addExp(h, gain)
            if (h.level > before) levelUps += "${h.name} lên Lv ${h.level}"
        }

        var sold = 0L
        val kept = mutableListOf<Item>()
        var free = MAX_ITEMS - inventory.size
        for (it in o.items) {
            if (free > 0) {
                s.items += it
                kept += it
                free--
            } else sold += it.sellPrice
        }
        s.gold += sold

        var guildUp = false
        if (o.win) {
            s.guildExp++
            while (s.guildExp >= guildNeed) {
                s.guildExp -= guildNeed
                s.guildLevel++
                guildUp = true
            }
        }

        val rest = minOf(e.quest.minutes * 30_000L, 2 * 3600_000L)
        val injured = o.fallen.mapNotNull { s.hero(it) }.onEach { it.injuredUntil = e.endAt + rest }.map { it.name }
        return ClaimSummary(o.win, o.gold, o.exp, kept, sold, levelUps, injured, guildUp, o.logId)
    }

    private fun addExp(h: Hero, amount: Long) {
        if (h.level >= Stats.MAX_LEVEL) return
        h.exp += amount
        while (h.level < Stats.MAX_LEVEL && h.exp >= Stats.expToNext(h.level)) {
            h.exp -= Stats.expToNext(h.level)
            h.level++
        }
        if (h.level >= Stats.MAX_LEVEL) h.exp = 0
    }

    fun hire(index: Int, now: Long): Hero {
        clock(now)
        val c = s.tavern.getOrNull(index) ?: throw GameError("Ứng viên không còn")
        if (s.heroes.size >= MAX_HEROES) throw GameError("Đội hình đã đủ $MAX_HEROES người")
        val price = Gen.hirePrice(c)
        if (s.gold < price) throw GameError("Không đủ vàng")
        s.gold -= price
        val h = Hero(s.newId(), c.name, c.cls, c.rarity, c.level, 0, c.traits)
        s.heroes += h
        s.tavern.removeAt(index)
        return h
    }

    /** Đồ đang mặc tự về kho (kho chứa mọi đồ, đồ mặc chỉ được tham chiếu). */
    fun fire(heroId: Long) {
        val h = s.hero(heroId) ?: throw GameError("Không tìm thấy mạo hiểm giả")
        if (isAway(heroId)) throw GameError("${h.name} đang làm nhiệm vụ")
        s.heroes.remove(h)
    }

    fun equip(heroId: Long, itemId: Long) {
        val h = s.hero(heroId) ?: throw GameError("Không tìm thấy mạo hiểm giả")
        val item = s.item(itemId) ?: throw GameError("Không tìm thấy đồ")
        if (isAway(heroId)) throw GameError("${h.name} đang làm nhiệm vụ")
        if (!item.fits(h.cls)) throw GameError("${h.cls.label} không dùng được ${item.name}")
        val o = owner(itemId)
        if (o != null && o.id != heroId) throw GameError("Đồ đang được ${o.name} mặc")
        h.equipment[item.slot] = itemId
    }

    fun unequip(heroId: Long, slot: Slot) {
        val h = s.hero(heroId) ?: throw GameError("Không tìm thấy mạo hiểm giả")
        if (isAway(heroId)) throw GameError("${h.name} đang làm nhiệm vụ")
        h.equipment.remove(slot)
    }

    fun sell(itemId: Long): Long {
        val item = s.item(itemId) ?: throw GameError("Không tìm thấy đồ")
        if (owner(itemId) != null) throw GameError("Phải tháo đồ trước khi bán")
        s.items.remove(item)
        s.gold += item.sellPrice
        return item.sellPrice
    }

    fun refreshTavern(now: Long) {
        clock(now)
        if (s.gold < refreshCost) throw GameError("Không đủ vàng")
        s.gold -= refreshCost
        s.tavernRoll++
        s.tavern.clear()
        s.tavern += Gen.candidates(s)
    }
}

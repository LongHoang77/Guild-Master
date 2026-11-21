package vn.guildmaster.core

import java.util.SplittableRandom
import kotlin.math.pow
import kotlin.math.roundToLong

class Rng(seed: Long) {
    private val r = SplittableRandom(seed)
    fun int(n: Int) = r.nextInt(n)
    fun range(lo: Int, hi: Int) = if (hi <= lo) lo else lo + r.nextInt(hi - lo + 1)
    fun double() = r.nextDouble()
    fun chance(p: Double) = r.nextDouble() < p
    fun <T> pick(list: List<T>): T = list[r.nextInt(list.size)]

    /** Chọn n phần tử không trùng. */
    fun <T> sample(list: List<T>, n: Int): List<T> {
        val pool = list.toMutableList()
        return List(n.coerceAtMost(pool.size)) { pool.removeAt(r.nextInt(pool.size)) }
    }

    /** Chọn chỉ số theo trọng số. */
    fun weighted(weights: DoubleArray): Int {
        var x = r.nextDouble() * weights.sum()
        for (i in weights.indices) {
            x -= weights[i]
            if (x < 0) return i
        }
        return weights.lastIndex
    }
}

object Gen {
    /** Trộn 2 số thành seed mới (splitmix64). */
    fun mix(a: Long, b: Long): Long {
        var z = a xor (b * -7046029254386353131L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    fun name(rng: Rng): String {
        val n = rng.range(2, 3)
        val s = (1..n).joinToString("") { rng.pick(Data.syllables) }
        return s.replaceFirstChar { it.uppercase() }
    }

    // ---------- Đồ ----------

    private val mainStat = mapOf(
        Slot.WEAPON to Stat(StatType.ATK, 6.0),
        Slot.HELMET to Stat(StatType.HP, 40.0),
        Slot.ARMOR to Stat(StatType.DEF, 5.0),
        Slot.GLOVES to Stat(StatType.CRIT, 2.0),
        Slot.BOOTS to Stat(StatType.SPD, 3.0),
        Slot.ACCESSORY to Stat(StatType.CRIT_DMG, 6.0),
    )

    private val subBase = mapOf(
        StatType.HP_PCT to 3.0, StatType.ATK_PCT to 3.0, StatType.DEF_PCT to 3.0,
        StatType.SPD to 2.0, StatType.CRIT to 1.5, StatType.CRIT_DMG to 4.0,
        StatType.HP to 20.0, StatType.ATK to 3.0,
    )

    private fun round(type: StatType, v: Double) =
        if (type.pct) Math.round(v * 10) / 10.0 else Math.round(v).toDouble().coerceAtLeast(1.0)

    /** Độ hiếm đồ: 70/22/7/1%, cấp càng cao càng dịch sang hiếm. */
    fun itemRarity(rng: Rng, level: Int): Rarity {
        val shift = level.coerceIn(1, 50) * 0.6
        val w = doubleArrayOf(70 - shift, 22 + shift * 0.65, 7 + shift * 0.27, 1 + shift * 0.08)
        return Rarity.entries[rng.weighted(w)]
    }

    fun item(rng: Rng, id: Long, level: Int, rarity: Rarity = itemRarity(rng, level)): Item {
        val slot = rng.pick(Slot.entries)
        val cls = if (slot == Slot.WEAPON) rng.pick(HeroClass.entries) else null
        val base = mainStat.getValue(slot)
        val main = Stat(base.type, round(base.type, base.value * (1 + 0.1 * level) * rarity.itemMul))
        val subCount = when (rarity) {
            Rarity.COMMON -> 0
            Rarity.RARE -> 1
            Rarity.EPIC -> rng.range(2, 3)
            Rarity.LEGENDARY -> 4
        }
        val subs = rng.sample(subBase.keys.filter { it != main.type }, subCount).map {
            Stat(it, round(it, subBase.getValue(it) * (1 + 0.05 * level) * rarity.itemMul))
        }
        return Item(id, slot, cls, rarity, level, main, subs)
    }

    // ---------- Nhân vật ----------

    fun hero(rng: Rng, id: Long, cls: HeroClass, rarity: Rarity, level: Int): Hero =
        Hero(id, name(rng), cls, rarity, level, 0, rng.sample(Trait.entries, rarity.traits))

    /** Tỉ lệ độ hiếm ứng viên theo cấp hội. */
    fun heroRarity(rng: Rng, guildLevel: Int): Rarity {
        var rare = 25.0 + (guildLevel - 1)
        val epic = 4.5 + guildLevel / 5
        val leg = 0.5 + 0.5 * (guildLevel / 10)
        var common = 100 - rare - epic - leg
        if (common < 40) {
            rare -= 40 - common
            common = 40.0
        }
        return Rarity.entries[rng.weighted(doubleArrayOf(common, rare, epic, leg))]
    }

    fun candidates(s: GameState): List<Hero> {
        val rng = Rng(mix(mix(s.seed, s.tavernPeriod), 7777L + s.tavernRoll))
        val maxLv = maxOf(1, (s.heroes.maxOfOrNull { it.level } ?: 1) - 3)
        return List(4) {
            hero(rng, 0, rng.pick(HeroClass.entries), heroRarity(rng, s.guildLevel), rng.range(1, maxLv))
        }
    }

    fun hirePrice(h: Hero): Long = (100.0 * h.rarity.hireMul * (1 + h.level / 10.0)).roundToLong()

    // ---------- Nhiệm vụ ----------

    fun board(s: GameState, period: Long): List<Quest> {
        val rng = Rng(mix(s.seed, period))
        val zones = Data.zones.indices.filter { Data.zones[it].unlockGuild <= s.guildLevel }
        val maxHero = s.heroes.maxOfOrNull { it.level } ?: 1
        return List(5) { i ->
            val zi = rng.pick(zones)
            val z = Data.zones[zi]
            val hi = minOf(z.maxLevel, maxOf(z.minLevel, maxHero + 2))
            val lo = minOf(maxOf(z.minLevel, maxHero - 4), hi)
            val level = rng.range(lo, hi)
            val minutes = Data.durations[rng.int(Data.durations.size)]
            val count = 1 + rng.int(minOf(5, 2 + level / 5))
            val monsters = List(count) { MonsterSpec(zi * 100 + rng.int(z.species.size), level) }
            val t = minutes.toDouble().pow(0.7)
            Quest(
                id = period * 10 + i + 1,
                zone = zi,
                level = level,
                minutes = minutes,
                monsters = monsters,
                gold = (20 * level * t).roundToLong(),
                exp = (15 * level * t).roundToLong(),
                drops = minOf(6, 1 + minutes / 60),
            )
        }
    }
}

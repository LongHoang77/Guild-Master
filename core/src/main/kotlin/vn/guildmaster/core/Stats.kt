package vn.guildmaster.core

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FinalStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spd: Int,
    val crit: Double,
    val critDmg: Double,
) {
    val power: Int
        get() {
            val critFactor = 1 + crit.coerceAtMost(100.0) / 100 * (critDmg / 100 - 1)
            return (hp * 0.25 + atk * critFactor * 4 + def * 2 + spd * 0.3).roundToInt()
        }
}

object Stats {
    const val MAX_LEVEL = 50

    fun levelMul(level: Int) = 1 + 0.08 * (level - 1)

    fun expToNext(level: Int): Long = (50 * level.toDouble().pow(1.6)).roundToLong()

    /** Chỉ số cuối = (gốc × độ hiếm × cấp + phẳng) × (1 + % đồ + % đặc tính). */
    fun of(hero: Hero, equipment: Map<Slot, Long> = hero.equipment, lookup: (Long) -> Item?): FinalStats {
        val b = Data.baseStats.getValue(hero.cls)
        val m = hero.rarity.heroMul * levelMul(hero.level)
        val add = DoubleArray(StatType.entries.size)
        for (id in equipment.values) {
            val item = lookup(id) ?: continue
            add[item.main.type.ordinal] += item.main.value
            for (s in item.subs) add[s.type.ordinal] += s.value
        }
        for (t in hero.traits) when (t) {
            Trait.BRAVE -> add[StatType.HP_PCT.ordinal] += 10.0
            Trait.FIERCE -> add[StatType.ATK_PCT.ordinal] += 10.0
            Trait.STURDY -> add[StatType.DEF_PCT.ordinal] += 10.0
            Trait.SWIFT -> add[StatType.SPD.ordinal] += 8.0
            Trait.KEEN -> add[StatType.CRIT.ordinal] += 6.0
            Trait.RUTHLESS -> add[StatType.CRIT_DMG.ordinal] += 25.0
            Trait.QUICK, Trait.LUCKY -> {}
        }
        fun v(t: StatType) = add[t.ordinal]
        return FinalStats(
            hp = ((b[0] * m + v(StatType.HP)) * (1 + v(StatType.HP_PCT) / 100)).roundToInt(),
            atk = ((b[1] * m + v(StatType.ATK)) * (1 + v(StatType.ATK_PCT) / 100)).roundToInt(),
            def = ((b[2] * m + v(StatType.DEF)) * (1 + v(StatType.DEF_PCT) / 100)).roundToInt(),
            spd = (b[3] + v(StatType.SPD)).roundToInt(),
            crit = b[4] + v(StatType.CRIT),
            critDmg = b[5] + v(StatType.CRIT_DMG),
        )
    }

    fun monster(spec: MonsterSpec): FinalStats {
        val sp = Data.species(spec.species)
        val m = 1 + 0.12 * (spec.level - 1)
        return FinalStats(
            hp = (sp.hp * m).roundToInt(),
            atk = (sp.atk * m).roundToInt(),
            def = (sp.def * m).roundToInt(),
            spd = sp.spd,
            crit = 5.0,
            critDmg = 150.0,
        )
    }

    fun skills(hero: Hero): List<Skill> = Data.skills.getValue(hero.cls).filter { it.unlock <= hero.level }
}

package vn.guildmaster.core

import java.util.SplittableRandom
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Đầu vào trận: chỉ số đã tính, kỹ năng đã lọc theo cấp. */
class Fighter(
    val name: String,
    val isHero: Boolean,
    val cls: HeroClass?,
    val stats: FinalStats,
    val skills: List<Skill>,
)

/** survivors = vị trí các hero còn sống. */
class BattleResult(val win: Boolean, val rounds: Int, val survivors: List<Int>, val log: List<BattleEvent>)

object Battle {
    const val MAX_ACTIONS = 200
    private const val GAUGE = 1000
    val BASIC = Skill("Đánh thường", Target.ENEMY, 1.0)

    private class U(val f: Fighter, val pos: Int) {
        val maxHp = f.stats.hp
        var hp = maxHp
        var gauge = 0
        val cd = IntArray(f.skills.size)
        var reviveUsed = false
        var taunt = 0
        var tauntDef = 0.0
        var shield = 0
        var shieldVal = 0.0
        var bless = 0
        var blessVal = 0.0
        var slow = 0
        var slowVal = 0.0
        var stun = false

        val alive get() = hp > 0
        val spd get() = max(1, (f.stats.spd * (if (slow > 0) 1 - slowVal else 1.0)).roundToInt())

        fun clearEffects() {
            taunt = 0; shield = 0; bless = 0; slow = 0; stun = false; gauge = 0
        }
    }

    fun run(heroes: List<Fighter>, monsters: List<Fighter>, seed: Long): BattleResult {
        val sim = Sim(heroes.mapIndexed { i, f -> U(f, i) }, monsters.mapIndexed { i, f -> U(f, i) }, SplittableRandom(seed))
        return sim.run()
    }

    private class Sim(val hs: List<U>, val ms: List<U>, val rng: SplittableRandom) {
        val all = hs + ms
        val log = mutableListOf<BattleEvent>()

        fun run(): BattleResult {
            var actions = 0
            while (actions < MAX_ACTIONS && hs.any { it.alive } && ms.any { it.alive }) {
                var ready = all.filter { it.alive && it.gauge >= GAUGE }
                if (ready.isEmpty()) {
                    val living = all.filter { it.alive }
                    val t = living.minOf { (GAUGE - it.gauge + it.spd - 1) / it.spd }
                    for (u in living) u.gauge += t * u.spd
                    ready = living.filter { it.gauge >= GAUGE }
                }
                // Hoà: hero trước, rồi theo vị trí
                val u = ready.minWith(compareBy<U>({ if (it.f.isHero) 0 else 1 }, { it.pos }))
                u.gauge -= GAUGE
                act(u)
                actions++
            }
            val win = ms.none { it.alive } && hs.any { it.alive }
            return BattleResult(win, actions, hs.filter { it.alive }.map { it.pos }, log)
        }

        fun act(u: U) {
            for (i in u.cd.indices) if (u.cd[i] > 0) u.cd[i]--
            if (u.taunt > 0) u.taunt--
            if (u.shield > 0) u.shield--
            if (u.bless > 0) u.bless--
            if (u.slow > 0) u.slow--
            if (u.stun) {
                u.stun = false
                event(u, "", EventKind.STUNNED, "", 0)
                return
            }
            val allies = if (u.f.isHero) hs else ms
            val enemies = if (u.f.isHero) ms else hs
            val idx = if (u.f.isHero) chooseHero(u, allies, enemies) else chooseMonster(u)
            val sk = if (idx < 0) BASIC else u.f.skills[idx]
            if (idx >= 0) {
                u.cd[idx] = sk.cooldown
                if (sk.effect == Effect.REVIVE) u.reviveUsed = true
            }
            perform(u, sk, allies, enemies)
        }

        fun ready(u: U, i: Int): Boolean {
            val sk = u.f.skills[i]
            return u.cd[i] == 0 && !(sk.effect == Effect.REVIVE && u.reviveUsed)
        }

        fun find(u: U, effect: Effect) = u.f.skills.indices.firstOrNull { u.f.skills[it].effect == effect && ready(u, it) }

        fun chooseHero(u: U, allies: List<U>, enemies: List<U>): Int {
            when (u.f.cls) {
                HeroClass.PRIEST -> {
                    find(u, Effect.REVIVE)?.let { if (allies.any { !it.alive }) return it }
                    find(u, Effect.HEAL)?.let { if (allies.any { it.alive && it.hp * 2 < it.maxHp }) return it }
                    find(u, Effect.BLESS)?.let { return it }
                }
                HeroClass.WARRIOR -> {
                    find(u, Effect.TAUNT)?.let { if (allies.none { it.alive && it.taunt > 0 }) return it }
                    find(u, Effect.SHIELD)?.let { return it }
                }
                else -> {}
            }
            return bestDamage(u, enemies.count { it.alive })
        }

        /** Kỹ năng sát thương cao nhất đã hồi; đánh lan chỉ khi ≥ 2 địch. */
        fun bestDamage(u: U, enemyCount: Int): Int {
            var best = -1
            var bestVal = BASIC.mult
            for (i in u.f.skills.indices) {
                val sk = u.f.skills[i]
                if (!ready(u, i) || sk.mult <= 0) continue
                val v = when (sk.target) {
                    Target.ENEMY -> sk.mult * sk.hits
                    Target.ENEMIES -> if (enemyCount >= 2) sk.mult * enemyCount else continue
                    else -> continue
                }
                if (v > bestVal) {
                    best = i
                    bestVal = v
                }
            }
            return best
        }

        fun chooseMonster(u: U): Int = if (u.f.skills.isNotEmpty() && ready(u, 0)) 0 else -1

        fun pickEnemy(u: U, enemies: List<U>): U? {
            val living = enemies.filter { it.alive }
            if (living.isEmpty()) return null
            if (u.f.isHero) return living.minBy { it.hp }
            living.firstOrNull { it.taunt > 0 }?.let { return it }
            val w = DoubleArray(living.size) { if (living[it].f.cls == HeroClass.WARRIOR) 3.0 else 1.0 }
            var x = rng.nextDouble() * w.sum()
            for (i in w.indices) {
                x -= w[i]
                if (x < 0) return living[i]
            }
            return living.last()
        }

        fun perform(u: U, sk: Skill, allies: List<U>, enemies: List<U>) {
            when (sk.target) {
                Target.ENEMY -> {
                    val t = pickEnemy(u, enemies) ?: return
                    repeat(sk.hits) { if (t.alive) hit(u, t, sk) }
                    if (!t.alive) return
                    if (sk.effect == Effect.STUN && rng.nextDouble() < sk.chance) {
                        t.stun = true
                        event(u, "choáng", EventKind.DEBUFF, t.f.name, 0)
                    } else if (sk.effect == Effect.SLOW) {
                        t.slow = sk.turns
                        t.slowVal = sk.value
                        event(u, "làm chậm", EventKind.DEBUFF, t.f.name, 0)
                    }
                }
                Target.ENEMIES -> for (t in enemies) if (t.alive) hit(u, t, sk)
                Target.SELF -> {
                    if (sk.effect == Effect.TAUNT) {
                        u.taunt = sk.turns
                        u.tauntDef = sk.value
                    }
                    event(u, sk.name, EventKind.BUFF, "", 0)
                }
                Target.ALLIES -> {
                    for (a in allies) if (a.alive) {
                        if (sk.effect == Effect.SHIELD) {
                            a.shield = sk.turns
                            a.shieldVal = sk.value
                        } else if (sk.effect == Effect.BLESS) {
                            a.bless = sk.turns
                            a.blessVal = sk.value
                        }
                    }
                    event(u, sk.name, EventKind.BUFF, "cả đội", 0)
                }
                Target.ALLY_LOWEST -> {
                    val t = allies.filter { it.alive }.minByOrNull { it.hp.toDouble() / it.maxHp } ?: return
                    val amount = (atkOf(u) * sk.mult + t.maxHp * sk.value).roundToInt()
                    val healed = min(amount, t.maxHp - t.hp)
                    t.hp += healed
                    event(u, sk.name, EventKind.HEAL, t.f.name, healed)
                }
                Target.ALLY_DOWN -> {
                    val t = allies.firstOrNull { !it.alive } ?: return
                    t.clearEffects()
                    t.hp = max(1, (t.maxHp * sk.value).roundToInt())
                    event(u, sk.name, EventKind.REVIVE, t.f.name, t.hp)
                }
            }
        }

        fun atkOf(u: U) = u.f.stats.atk * (1 + if (u.bless > 0) u.blessVal else 0.0)

        fun hit(u: U, t: U, sk: Skill) {
            val def = t.f.stats.def * (1 + if (t.taunt > 0) t.tauntDef else 0.0)
            var dmg = atkOf(u) * sk.mult * 100 / (100 + def)
            val crit = rng.nextDouble() * 100 < u.f.stats.crit + sk.critBonus * 100
            if (crit) dmg *= u.f.stats.critDmg / 100
            dmg *= 0.95 + rng.nextDouble() * 0.1
            if (t.shield > 0) dmg *= 1 - t.shieldVal
            val d = max(1, dmg.roundToInt())
            t.hp = max(0, t.hp - d)
            log += BattleEvent(u.f.isHero, u.f.name, sk.name, EventKind.DAMAGE, t.f.name, d, crit)
            if (!t.alive) {
                t.clearEffects()
                log += BattleEvent(t.f.isHero, t.f.name, "", EventKind.DOWN, t.f.name, 0, false)
            }
        }

        fun event(u: U, skill: String, kind: EventKind, target: String, amount: Int) {
            log += BattleEvent(u.f.isHero, u.f.name, skill, kind, target, amount, false)
        }
    }
}

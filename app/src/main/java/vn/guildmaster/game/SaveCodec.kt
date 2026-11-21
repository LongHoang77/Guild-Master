package vn.guildmaster.game

import org.json.JSONArray
import org.json.JSONObject
import vn.guildmaster.core.BattleEvent
import vn.guildmaster.core.BattleLog
import vn.guildmaster.core.EventKind
import vn.guildmaster.core.Expedition
import vn.guildmaster.core.GameState
import vn.guildmaster.core.Hero
import vn.guildmaster.core.HeroClass
import vn.guildmaster.core.Item
import vn.guildmaster.core.MonsterSpec
import vn.guildmaster.core.Outcome
import vn.guildmaster.core.Quest
import vn.guildmaster.core.Rarity
import vn.guildmaster.core.Slot
import vn.guildmaster.core.Stat
import vn.guildmaster.core.StatType
import vn.guildmaster.core.Trait

/** GameState ↔ JSON. Bản cũ hơn VERSION được nâng cấp trong migrate(). */
object SaveCodec {
    const val VERSION = 1

    fun encode(s: GameState): String {
        val o = JSONObject()
        o.put("schemaVersion", VERSION)
        o.put("seed", s.seed)
        o.put("gold", s.gold)
        o.put("guildExp", s.guildExp)
        o.put("guildLevel", s.guildLevel)
        o.put("lastSeen", s.lastSeen)
        o.put("nextId", s.nextId)
        o.put("heroes", arr(s.heroes.map { hero(it) }))
        o.put("items", arr(s.items.map { item(it) }))
        o.put("board", JSONObject().put("period", s.boardPeriod).put("quests", arr(s.board.map { quest(it) })))
        o.put("tavern", JSONObject().put("period", s.tavernPeriod).put("roll", s.tavernRoll).put("candidates", arr(s.tavern.map { hero(it) })))
        o.put("expeditions", arr(s.expeditions.map { expedition(it) }))
        o.put("logs", arr(s.logs.map { log(it) }))
        return o.toString()
    }

    fun decode(text: String): GameState {
        val o = JSONObject(text)
        val ver = o.optInt("schemaVersion", 1)
        if (ver > VERSION) throw IllegalStateException("Bản lưu mới hơn ứng dụng")
        migrate(o, ver)
        val s = GameState(o.getLong("seed"))
        s.gold = o.getLong("gold")
        s.guildExp = o.getInt("guildExp")
        s.guildLevel = o.getInt("guildLevel")
        s.lastSeen = o.getLong("lastSeen")
        s.nextId = o.getLong("nextId")
        o.getJSONArray("heroes").objs().mapTo(s.heroes) { hero(it) }
        o.getJSONArray("items").objs().mapTo(s.items) { item(it) }
        val b = o.getJSONObject("board")
        s.boardPeriod = b.getLong("period")
        b.getJSONArray("quests").objs().mapTo(s.board) { quest(it) }
        val t = o.getJSONObject("tavern")
        s.tavernPeriod = t.getLong("period")
        s.tavernRoll = t.getInt("roll")
        t.getJSONArray("candidates").objs().mapTo(s.tavern) { hero(it) }
        o.getJSONArray("expeditions").objs().mapTo(s.expeditions) { expedition(it) }
        o.getJSONArray("logs").objs().mapTo(s.logs) { log(it) }
        return s
    }

    @Suppress("UNUSED_PARAMETER")
    private fun migrate(o: JSONObject, from: Int) {
        // Chưa có bản cũ hơn 1.
    }

    private fun arr(list: List<Any>) = JSONArray().apply { list.forEach { put(it) } }
    private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.longs(): List<Long> = (0 until length()).map { getLong(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun hero(h: Hero) = JSONObject()
        .put("id", h.id).put("name", h.name).put("cls", h.cls.name).put("rarity", h.rarity.name)
        .put("level", h.level).put("exp", h.exp)
        .put("traits", arr(h.traits.map { it.name }))
        .put("equipment", JSONObject().apply { h.equipment.forEach { (k, v) -> put(k.name, v) } })
        .put("injuredUntil", h.injuredUntil)

    private fun hero(o: JSONObject): Hero {
        val eq = o.getJSONObject("equipment")
        val equipment = mutableMapOf<Slot, Long>()
        for (k in eq.keys()) equipment[Slot.valueOf(k)] = eq.getLong(k)
        return Hero(
            o.getLong("id"), o.getString("name"), HeroClass.valueOf(o.getString("cls")),
            Rarity.valueOf(o.getString("rarity")), o.getInt("level"), o.getLong("exp"),
            o.getJSONArray("traits").strings().map { Trait.valueOf(it) },
            equipment, o.getLong("injuredUntil"),
        )
    }

    private fun stat(s: Stat) = JSONObject().put("t", s.type.name).put("v", s.value)
    private fun stat(o: JSONObject) = Stat(StatType.valueOf(o.getString("t")), o.getDouble("v"))

    private fun item(i: Item) = JSONObject()
        .put("id", i.id).put("slot", i.slot.name).put("cls", i.cls?.name ?: "")
        .put("rarity", i.rarity.name).put("level", i.itemLevel)
        .put("main", stat(i.main)).put("subs", arr(i.subs.map { stat(it) }))

    private fun item(o: JSONObject): Item {
        val cls = o.optString("cls", "")
        return Item(
            o.getLong("id"), Slot.valueOf(o.getString("slot")),
            if (cls.isEmpty()) null else HeroClass.valueOf(cls),
            Rarity.valueOf(o.getString("rarity")), o.getInt("level"),
            stat(o.getJSONObject("main")), o.getJSONArray("subs").objs().map { stat(it) },
        )
    }

    private fun quest(q: Quest) = JSONObject()
        .put("id", q.id).put("zone", q.zone).put("level", q.level).put("minutes", q.minutes)
        .put("monsters", arr(q.monsters.map { JSONObject().put("s", it.species).put("l", it.level) }))
        .put("gold", q.gold).put("exp", q.exp).put("drops", q.drops)

    private fun quest(o: JSONObject) = Quest(
        o.getLong("id"), o.getInt("zone"), o.getInt("level"), o.getInt("minutes"),
        o.getJSONArray("monsters").objs().map { MonsterSpec(it.getInt("s"), it.getInt("l")) },
        o.getLong("gold"), o.getLong("exp"), o.getInt("drops"),
    )

    private fun expedition(e: Expedition): JSONObject {
        val o = JSONObject()
            .put("quest", quest(e.quest)).put("heroIds", arr(e.heroIds))
            .put("startAt", e.startAt).put("endAt", e.endAt)
        e.outcome?.let { r ->
            o.put("result", JSONObject()
                .put("win", r.win).put("gold", r.gold).put("exp", r.exp)
                .put("items", arr(r.items.map { item(it) })).put("fallen", arr(r.fallen)).put("logId", r.logId))
        }
        return o
    }

    private fun expedition(o: JSONObject): Expedition {
        val r = o.optJSONObject("result")
        val outcome = r?.let {
            Outcome(
                it.getBoolean("win"), it.getLong("gold"), it.getLong("exp"),
                it.getJSONArray("items").objs().map { item(it) }, it.getJSONArray("fallen").longs(), it.getLong("logId"),
            )
        }
        return Expedition(
            quest(o.getJSONObject("quest")), o.getJSONArray("heroIds").longs(),
            o.getLong("startAt"), o.getLong("endAt"), outcome,
        )
    }

    private fun log(l: BattleLog) = JSONObject()
        .put("id", l.id).put("at", l.at).put("title", l.title).put("win", l.win).put("rounds", l.rounds)
        .put("heroes", arr(l.heroes))
        .put("events", arr(l.events.map {
            JSONObject().put("h", it.heroSide).put("a", it.actor).put("s", it.skill).put("k", it.kind.name)
                .put("t", it.target).put("n", it.amount).put("c", it.crit)
        }))

    private fun log(o: JSONObject) = BattleLog(
        o.getLong("id"), o.getLong("at"), o.getString("title"), o.getBoolean("win"), o.getInt("rounds"),
        o.getJSONArray("heroes").strings(),
        o.getJSONArray("events").objs().map {
            BattleEvent(
                it.getBoolean("h"), it.getString("a"), it.getString("s"), EventKind.valueOf(it.getString("k")),
                it.getString("t"), it.getInt("n"), it.getBoolean("c"),
            )
        },
    )
}

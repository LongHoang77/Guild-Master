package vn.guildmaster.game

import android.widget.LinearLayout
import vn.guildmaster.core.Data
import vn.guildmaster.core.Game
import vn.guildmaster.core.Hero
import vn.guildmaster.core.Quest

class QuestScreen(a: MainActivity) : Screen(a) {
    private var picking: Long? = null
    private val selected = mutableListOf<Long>()

    override fun back(): Boolean {
        if (picking == null) return false
        picking = null
        a.render(true)
        return true
    }

    override fun build(root: LinearLayout) {
        val q = picking?.let { id -> s.board.find { it.id == id } }
        if (q == null) {
            picking = null
            buildList(root)
        } else buildPick(root, q)
    }

    private fun buildList(root: LinearLayout) {
        val info = ui.card()
        val timer = ui.text("", 13f, ui.sub)
        info.addView(timer)
        live { timer.text = "Bảng làm mới sau ${Fmt.dur(g.nextBoardAt(a.now()) - a.now())}" }
        if (s.expeditions.size >= g.maxParties) {
            info.addView(ui.text("Đã đủ ${g.maxParties} đội đang đi. Nhận thưởng ở tab Hội để gửi tiếp.", 13f, ui.bad))
        }
        root.addView(info)

        if (s.board.isEmpty()) root.addView(ui.text("Bảng trống — chờ làm mới.", 14f, ui.sub).apply { setPadding(ui.dp(4), ui.dp(12), 0, 0) })
        for (q in s.board) {
            root.addView(questCard(q) {
                picking = q.id
                selected.clear()
                a.render(true)
            })
        }
    }

    private fun questCard(q: Quest, onClick: (() -> Unit)?): LinearLayout {
        val c = ui.card(onClick = onClick)
        val head = ui.row()
        head.addView(ui.lpWeight(ui.text(Data.zones[q.zone].name, 16f, bold = true)))
        head.addView(ui.chip("Lv ${q.level}", ui.accent))
        head.addView(ui.space(6))
        head.addView(ui.chip(Fmt.minutes(q.minutes), ui.ally))
        c.addView(head)
        val mons = q.monsters.groupingBy { it.species }.eachCount().entries
            .joinToString(", ") { (sp, n) -> Data.species(sp).name + if (n > 1) " ×$n" else "" }
        c.addView(ui.gap(ui.text(mons, 13f), 6))
        c.addView(ui.text("Thưởng: ${Fmt.num(q.gold)} vàng · ${Fmt.num(q.exp)} EXP/người · ${q.drops} lượt rơi đồ", 12f, ui.sub))
        c.addView(ui.text("Sức mạnh địch: ${g.enemyPower(q)}", 12f, ui.sub))
        return c
    }

    private fun buildPick(root: LinearLayout, q: Quest) {
        root.addView(questCard(q, null))
        root.addView(ui.section("Chọn đội (1–${Game.MAX_TEAM})"))

        val power = g.teamPower(selected)
        val enemy = g.enemyPower(q)
        val sum = ui.card()
        sum.addView(ui.text("Sức mạnh đội: $power  /  Địch: $enemy", 14f, if (power >= enemy) ui.good else ui.bad, true))
        sum.addView(ui.bar(if (enemy > 0) power.toFloat() / enemy else 1f, if (power >= enemy) ui.good else ui.bad))
        val btns = ui.row().apply { setPadding(0, ui.dp(10), 0, 0) }
        val canSend = selected.isNotEmpty() && s.expeditions.size < g.maxParties
        btns.addView(ui.lpWeight(ui.button("Gửi đi", enabled = canSend) { send(q) }))
        btns.addView(ui.space(8))
        btns.addView(ui.lpWeight(ui.button("Huỷ", Ui.Btn.SECONDARY) { back() }))
        sum.addView(btns)
        root.addView(sum)

        val now = a.now()
        val heroes = s.heroes.sortedWith(compareBy<Hero>({ if (g.isFree(it, now)) 0 else 1 }, { -it.level }))
        for (h in heroes) {
            val free = g.isFree(h, now)
            val sel = h.id in selected
            val c = ui.card(highlight = sel) {
                when {
                    sel -> selected.remove(h.id)
                    !free -> a.toast("${h.name} không sẵn sàng")
                    selected.size >= Game.MAX_TEAM -> a.toast("Tối đa ${Game.MAX_TEAM} người")
                    else -> selected.add(h.id)
                }
                a.render()
            }
            if (!free) c.alpha = 0.5f
            val r = ui.row()
            r.addView(ui.portrait(h.name, h.cls, h.rarity))
            val info = ui.col()
            info.addView(ui.text(h.name, 15f, bold = true))
            info.addView(ui.text("${h.cls.label} · Lv ${h.level} · Sức mạnh ${g.stats(h).power}", 12f, ui.sub))
            info.addView(heroStatus(a, h))
            r.addView(ui.lpWeight(info))
            if (sel) r.addView(ui.text("✓", 20f, ui.accent, true))
            c.addView(r)
            root.addView(c)
        }
    }

    private fun send(q: Quest) {
        a.act(reset = true) {
            val e = g.send(q.id, selected.toList(), a.now())
            Alarms.schedule(a, q.id, e.endAt, a.returnTitle(e))
            picking = null
            selected.clear()
            a.toast("Đã gửi đội")
        }
    }
}

/** Dòng trạng thái hero: đang đi / bị thương (đếm ngược) / rảnh. */
fun Screen.heroStatus(a: MainActivity, h: Hero): android.widget.TextView {
    val t = ui.text("", 12f)
    live {
        val now = a.now()
        when {
            g.isAway(h.id) -> {
                t.text = "Đang làm nhiệm vụ"
                t.setTextColor(ui.ally)
            }
            g.isInjured(h, now) -> {
                t.text = "Bị thương — ${Fmt.dur(h.injuredUntil - now)}"
                t.setTextColor(ui.bad)
            }
            else -> {
                t.text = "Rảnh"
                t.setTextColor(ui.good)
            }
        }
    }
    return t
}

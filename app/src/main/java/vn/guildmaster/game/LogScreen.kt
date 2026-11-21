package vn.guildmaster.game

import android.widget.LinearLayout
import vn.guildmaster.core.BattleLog
import vn.guildmaster.core.EventKind

class LogScreen(a: MainActivity) : Screen(a) {
    private var detail: Long? = null

    fun open(id: Long) {
        detail = id
    }

    override fun back(): Boolean {
        if (detail == null) return false
        detail = null
        a.render(true)
        return true
    }

    override fun build(root: LinearLayout) {
        val log = detail?.let { id -> s.logs.find { it.id == id } }
        if (log == null) {
            detail = null
            buildList(root)
        } else buildDetail(root, log)
    }

    private fun buildList(root: LinearLayout) {
        if (s.logs.isEmpty()) {
            root.addView(ui.text("Chưa có trận nào.", 14f, ui.sub).apply { setPadding(ui.dp(4), ui.dp(12), 0, 0) })
            return
        }
        val now = a.now()
        for (l in s.logs) {
            val c = ui.card {
                detail = l.id
                a.render(true)
            }
            val head = ui.row()
            head.addView(ui.lpWeight(ui.text(l.title, 15f, bold = true)))
            head.addView(resultChip(l))
            c.addView(head)
            c.addView(ui.text("${l.heroes.joinToString(", ")} · ${l.rounds} lượt · ${Fmt.ago(l.at, now)}", 12f, ui.sub))
            root.addView(c)
        }
    }

    private fun resultChip(l: BattleLog) = ui.chip(if (l.win) "Thắng" else "Thua", if (l.win) ui.good else ui.bad, true)

    private fun buildDetail(root: LinearLayout, l: BattleLog) {
        val head = ui.card()
        val r = ui.row()
        r.addView(ui.lpWeight(ui.text(l.title, 17f, bold = true)))
        r.addView(resultChip(l))
        head.addView(r)
        head.addView(ui.text("Đội: ${l.heroes.joinToString(", ")} · ${l.rounds} lượt hành động", 12f, ui.sub))
        root.addView(head)

        val body = ui.card()
        for (e in l.events) {
            val color = if (e.heroSide) ui.ally else ui.bad
            val bold = e.kind == EventKind.DOWN || e.crit
            body.addView(ui.text(Fmt.event(e), 13f, color, bold).apply { setPadding(0, ui.dp(2), 0, ui.dp(2)) })
        }
        root.addView(body)
    }
}

package vn.guildmaster.game

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import vn.guildmaster.core.ClaimSummary
import vn.guildmaster.core.Data
import vn.guildmaster.core.Expedition
import vn.guildmaster.core.Game
import vn.guildmaster.core.GameError

class GuildScreen(a: MainActivity) : Screen(a) {

    override fun build(root: LinearLayout) {
        updateCard(root)
        val c = ui.card()
        c.addView(ui.text("Hội cấp ${s.guildLevel}", 20f, bold = true))
        c.addView(ui.text("Điểm hội ${s.guildExp}/${g.guildNeed} — thắng 1 nhiệm vụ được 1 điểm", 12f, ui.sub))
        c.addView(ui.bar(s.guildExp.toFloat() / g.guildNeed, ui.accent))
        c.addView(ui.gap(ui.text("Vàng: ${Fmt.num(s.gold)}"), 10))
        c.addView(ui.text("Đội đang đi: ${s.expeditions.size}/${g.maxParties}"))
        c.addView(ui.text("Mạo hiểm giả: ${s.heroes.size}/${Game.MAX_HEROES}  ·  Kho đồ: ${g.inventory.size}/${Game.MAX_ITEMS}"))
        root.addView(c)

        root.addView(ui.section("Đội viễn chinh"))
        if (s.expeditions.isEmpty()) {
            val e = ui.card()
            e.addView(ui.text("Chưa có đội nào ra ngoài.", 14f, ui.sub))
            e.addView(ui.gap(ui.button("Xem bảng nhiệm vụ") { a.switchTab(1) }, 10))
            root.addView(e)
        }
        for (e in s.expeditions.sortedBy { it.endAt }) root.addView(expeditionCard(e))

        val foot = ui.row().apply { setPadding(ui.dp(4), ui.dp(20), 0, 0) }
        foot.addView(ui.lpWeight(ui.text("Phiên bản ${Updater.currentName(a)}", 12f, ui.sub)))
        foot.addView(ui.button("Kiểm tra cập nhật", Ui.Btn.SECONDARY) { a.checkUpdate(true) })
        root.addView(foot)
    }

    private fun updateCard(root: LinearLayout) {
        val u = a.update
        if (u == null && a.updateMsg.isEmpty()) return
        val c = ui.card(highlight = true)
        if (u != null) {
            c.addView(ui.text("Có bản cập nhật mới (${u.tag})", 15f, ui.accent, true))
            if (u.notes.isNotBlank()) c.addView(ui.text(u.notes.trim().take(300), 12f, ui.sub))
        }
        val status = ui.text("", 13f)
        c.addView(status)
        live {
            status.text = a.updateMsg
            status.visibility = if (a.updateMsg.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
        if (u != null) c.addView(ui.gap(ui.button("Cập nhật ngay", enabled = !a.updating) { a.startUpdate() }, 8))
        root.addView(c)
    }

    private fun expeditionCard(e: Expedition): LinearLayout {
        val q = e.quest
        val c = ui.card()
        c.addView(ui.text("${Data.zones[q.zone].name} · Lv ${q.level} · ${Fmt.minutes(q.minutes)}", 15f, bold = true))
        val team = ui.row().apply { setPadding(0, ui.dp(8), 0, ui.dp(4)) }
        for (id in e.heroIds) {
            val h = s.hero(id) ?: continue
            team.addView(ui.portrait(h.name, h.cls, h.rarity, 32))
        }
        team.addView(ui.singleLine(ui.text(e.heroIds.mapNotNull { s.hero(it)?.name }.joinToString(", "), 13f, ui.sub)))
        c.addView(team)

        val o = e.outcome
        if (o == null) {
            val status = ui.text("", 13f)
            val bar = ui.bar(0f, ui.ally)
            c.addView(status)
            c.addView(bar)
            live {
                val now = a.now()
                status.text = "Về sau ${Fmt.dur(e.endAt - now)}"
                bar.progress = (now - e.startAt).toFloat() / (e.endAt - e.startAt).coerceAtLeast(1)
            }
        } else {
            val r = ui.row()
            r.addView(ui.lpWeight(ui.text(if (o.win) "Đã về — Thắng" else "Đã về — Thua", 14f, if (o.win) ui.good else ui.bad, true)))
            r.addView(ui.button("Nhận") { claim(e) })
            c.addView(ui.gap(r, 6))
        }
        return c
    }

    private fun claim(e: Expedition) {
        val sum = try {
            g.claim(e.quest.id, a.now())
        } catch (err: GameError) {
            a.toast(err.message ?: "Lỗi")
            return
        }
        Alarms.cancel(a, e.quest.id)
        a.persist()
        a.render()
        showResult(sum)
    }

    private fun showResult(r: ClaimSummary) {
        val v = ui.col().apply { setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(8)) }
        v.addView(ui.text(if (r.win) "THẮNG" else "THUA", 26f, if (r.win) ui.good else ui.bad, true))
        v.addView(ui.gap(ui.text("+${Fmt.num(r.gold)} vàng"), 8))
        v.addView(ui.text("+${Fmt.num(r.exp)} EXP mỗi người"))
        if (r.guildLevelUp) v.addView(ui.text("Hội lên cấp ${s.guildLevel}!", 14f, ui.accent, true))
        for (l in r.levelUps) v.addView(ui.text(l, 14f, ui.good))
        if (r.items.isNotEmpty()) {
            v.addView(ui.gap(ui.text("Chiến lợi phẩm", 13f, ui.sub, true), 10))
            for (it in r.items) v.addView(ui.text("• ${it.name} · ${it.rarity.label} · Lv ${it.itemLevel}", 14f, ui.rarityColor(it.rarity)))
        }
        if (r.autoSoldGold > 0) v.addView(ui.text("Kho đầy — đồ dư bán được ${Fmt.num(r.autoSoldGold)} vàng", 12f, ui.sub))
        if (r.injured.isNotEmpty()) v.addView(ui.gap(ui.text("Bị thương: ${r.injured.joinToString(", ")}", 13f, ui.bad), 8))

        AlertDialog.Builder(a)
            .setView(ScrollView(a).apply { addView(v) })
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Xem trận") { _, _ -> a.openLog(r.logId) }
            .show()
    }
}

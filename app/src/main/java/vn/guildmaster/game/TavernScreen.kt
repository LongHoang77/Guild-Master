package vn.guildmaster.game

import android.widget.LinearLayout
import vn.guildmaster.core.Game
import vn.guildmaster.core.Gen
import vn.guildmaster.core.Stats

class TavernScreen(a: MainActivity) : Screen(a) {

    override fun build(root: LinearLayout) {
        val head = ui.card()
        val timer = ui.text("", 13f, ui.sub)
        head.addView(timer)
        live { timer.text = "Ứng viên mới sau ${Fmt.dur(g.nextTavernAt(a.now()) - a.now())}" }
        head.addView(ui.text("Đội hình ${s.heroes.size}/${Game.MAX_HEROES}", 13f, ui.sub))
        head.addView(ui.gap(ui.button("Làm mới ngay (${Fmt.num(g.refreshCost)} vàng)", Ui.Btn.SECONDARY, s.gold >= g.refreshCost) {
            a.act { g.refreshTavern(a.now()) }
        }, 8))
        root.addView(head)

        if (s.tavern.isEmpty()) root.addView(ui.text("Hết ứng viên — chờ lượt mới.", 14f, ui.sub).apply { setPadding(ui.dp(4), ui.dp(12), 0, 0) })
        s.tavern.forEachIndexed { i, h ->
            val c = ui.card()
            val r = ui.row()
            r.addView(ui.portrait(h.name, h.cls, h.rarity, 48))
            val info = ui.col()
            info.addView(ui.text(h.name, 16f, bold = true))
            info.addView(ui.text("${h.rarity.label} · ${h.cls.label} · Lv ${h.level}", 13f, ui.rarityColor(h.rarity), true))
            r.addView(ui.lpWeight(info))
            c.addView(r)

            val st = Stats.of(h) { null }
            c.addView(ui.gap(ui.text("HP ${st.hp} · ATK ${st.atk} · DEF ${st.def} · SPD ${st.spd}", 13f), 8))
            c.addView(ui.text(
                if (h.traits.isEmpty()) "Không có đặc tính" else h.traits.joinToString(" · ") { "${it.label} (${it.desc})" },
                12f, ui.sub,
            ))
            val price = Gen.hirePrice(h)
            val ok = s.gold >= price && s.heroes.size < Game.MAX_HEROES
            c.addView(ui.gap(ui.button("Thuê — ${Fmt.num(price)} vàng", enabled = ok) {
                a.act {
                    val hired = g.hire(i, a.now())
                    a.toast("${hired.name} đã gia nhập hội")
                }
            }, 10))
            root.addView(c)
        }
    }
}

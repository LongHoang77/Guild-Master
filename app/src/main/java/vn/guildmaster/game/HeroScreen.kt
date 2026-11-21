package vn.guildmaster.game

import android.app.AlertDialog
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import vn.guildmaster.core.Battle
import vn.guildmaster.core.Data
import vn.guildmaster.core.Game
import vn.guildmaster.core.Hero
import vn.guildmaster.core.HeroClass
import vn.guildmaster.core.Item
import vn.guildmaster.core.Slot
import vn.guildmaster.core.Stats
import java.util.Locale

class HeroScreen(a: MainActivity) : Screen(a) {
    private sealed class Page {
        object Roster : Page()
        object Inventory : Page()
        class Detail(val id: Long) : Page()
        class Pick(val id: Long, val slot: Slot) : Page()
    }

    private var page: Page = Page.Roster
    private var filter: HeroClass? = null
    private var sortByRarity = false

    private fun go(p: Page) {
        page = p
        a.render(true)
    }

    override fun back(): Boolean {
        when (val p = page) {
            Page.Roster -> return false
            Page.Inventory, is Page.Detail -> go(Page.Roster)
            is Page.Pick -> go(Page.Detail(p.id))
        }
        return true
    }

    override fun build(root: LinearLayout) {
        when (val p = page) {
            Page.Roster -> buildRoster(root)
            Page.Inventory -> buildInventory(root)
            is Page.Detail -> s.hero(p.id)?.let { buildDetail(root, it) } ?: run { page = Page.Roster; buildRoster(root) }
            is Page.Pick -> s.hero(p.id)?.let { buildPick(root, it, p.slot) } ?: run { page = Page.Roster; buildRoster(root) }
        }
    }

    // ---------- Đội hình ----------

    private fun buildRoster(root: LinearLayout) {
        val chips = ui.row().apply { setPadding(0, ui.dp(4), 0, ui.dp(4)) }
        chips.addView(ui.chip("Tất cả", ui.accent, filter == null) { filter = null; a.render(true) })
        for (c in HeroClass.entries) {
            chips.addView(ui.space(6))
            chips.addView(ui.chip(c.label, ui.clsColor(c), filter == c) { filter = c; a.render(true) })
        }
        root.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })

        val tools = ui.row().apply { setPadding(0, ui.dp(6), 0, ui.dp(6)) }
        tools.addView(ui.lpWeight(ui.button(if (sortByRarity) "Sắp: Độ hiếm" else "Sắp: Cấp", Ui.Btn.SECONDARY) {
            sortByRarity = !sortByRarity
            a.render()
        }))
        tools.addView(ui.space(8))
        tools.addView(ui.lpWeight(ui.button("Kho đồ (${g.inventory.size}/${Game.MAX_ITEMS})", Ui.Btn.SECONDARY) { go(Page.Inventory) }))
        root.addView(tools)

        val list = s.heroes.filter { filter == null || it.cls == filter }.sortedWith(
            if (sortByRarity) compareByDescending<Hero> { it.rarity.ordinal }.thenByDescending { it.level }
            else compareByDescending<Hero> { it.level }.thenByDescending { it.rarity.ordinal },
        )
        root.addView(ui.text("${s.heroes.size}/${Game.MAX_HEROES} mạo hiểm giả", 12f, ui.sub).apply { setPadding(ui.dp(4), 0, 0, ui.dp(4)) })
        for (pair in list.chunked(2)) {
            val r = ui.row()
            pair.forEachIndexed { i, h ->
                if (i > 0) r.addView(ui.space(8))
                r.addView(ui.lpWeight(heroCard(h)))
            }
            if (pair.size == 1) {
                r.addView(ui.space(8))
                r.addView(ui.lpWeight(android.view.View(a)))
            }
            root.addView(r)
        }
    }

    private fun heroCard(h: Hero): LinearLayout {
        val c = ui.card { go(Page.Detail(h.id)) }
        val r = ui.row()
        r.addView(ui.portrait(h.name, h.cls, h.rarity, 40))
        val info = ui.col()
        info.addView(ui.singleLine(ui.text(h.name, 14f, bold = true)))
        info.addView(ui.singleLine(ui.text("${h.cls.label} · Lv ${h.level}", 12f, ui.sub)))
        info.addView(ui.singleLine(heroStatus(a, h)))
        r.addView(ui.lpWeight(info))
        c.addView(r)
        return c
    }

    // ---------- Chi tiết ----------

    private fun buildDetail(root: LinearLayout, h: Hero) {
        val head = ui.card()
        val r = ui.row()
        r.addView(ui.portrait(h.name, h.cls, h.rarity, 64))
        val info = ui.col()
        info.addView(ui.text(h.name, 20f, bold = true))
        info.addView(ui.text("${h.rarity.label} · ${h.cls.label} · Lv ${h.level}", 13f, ui.rarityColor(h.rarity), true))
        info.addView(heroStatus(a, h))
        r.addView(ui.lpWeight(info))
        head.addView(r)
        if (h.level < Stats.MAX_LEVEL) {
            val need = Stats.expToNext(h.level)
            head.addView(ui.gap(ui.text("EXP ${Fmt.num(h.exp)}/${Fmt.num(need)}", 12f, ui.sub), 8))
            head.addView(ui.bar(h.exp.toFloat() / need, ui.accent))
        } else head.addView(ui.gap(ui.text("Cấp tối đa", 12f, ui.sub), 8))
        root.addView(head)

        val st = g.stats(h)
        root.addView(ui.section("Chỉ số"))
        val sc = ui.card()
        val rows = listOf(
            "HP" to "${st.hp}", "ATK" to "${st.atk}",
            "DEF" to "${st.def}", "SPD" to "${st.spd}",
            "Crit" to String.format(Locale.US, "%.1f%%", st.crit), "CritDMG" to String.format(Locale.US, "%.0f%%", st.critDmg),
        )
        for (pair in rows.chunked(2)) {
            val line = ui.row().apply { setPadding(0, ui.dp(2), 0, ui.dp(2)) }
            for ((k, v) in pair) {
                line.addView(ui.lpWeight(ui.text(k, 13f, ui.sub)))
                line.addView(ui.lpWeight(ui.text(v, 14f, bold = true)))
            }
            sc.addView(line)
        }
        sc.addView(ui.gap(ui.text("Sức mạnh: ${st.power}", 14f, ui.accent, true), 6))
        root.addView(sc)

        root.addView(ui.section("Kỹ năng"))
        val kc = ui.card()
        kc.addView(ui.text("${Battle.BASIC.name} — 1× ATK 1 mục tiêu", 13f, ui.sub))
        for (sk in Data.skills.getValue(h.cls)) {
            val open = sk.unlock <= h.level
            kc.addView(ui.gap(ui.text(sk.name + if (open) "" else "  (mở ở Lv ${sk.unlock})", 14f, if (open) ui.text else ui.sub, true), 8))
            kc.addView(ui.text(sk.desc, 12f, ui.sub))
        }
        root.addView(kc)

        root.addView(ui.section("Đặc tính"))
        val tc = ui.card()
        if (h.traits.isEmpty()) tc.addView(ui.text("Không có", 13f, ui.sub))
        for (t in h.traits) tc.addView(ui.text("${t.label}: ${t.desc}", 14f))
        root.addView(tc)

        root.addView(ui.section("Trang bị"))
        for (slot in Slot.entries) {
            val item = h.equipment[slot]?.let { s.item(it) }
            val c = ui.card {
                if (g.isAway(h.id)) a.toast("${h.name} đang làm nhiệm vụ") else go(Page.Pick(h.id, slot))
            }
            val line = ui.row()
            line.addView(ui.text(slot.label, 13f, ui.sub).apply { minWidth = ui.dp(72) })
            if (item == null) line.addView(ui.lpWeight(ui.text("Trống", 14f, ui.sub)))
            else line.addView(ui.lpWeight(ui.itemView(item)))
            line.addView(ui.text("›", 20f, ui.sub))
            c.addView(line)
            root.addView(c)
        }

        root.addView(ui.gap(ui.button("Sa thải", Ui.Btn.DANGER) {
            AlertDialog.Builder(a)
                .setMessage("Sa thải ${h.name}? Đồ đang mặc sẽ về kho.")
                .setPositiveButton("Sa thải") { _, _ ->
                    a.act(reset = true) {
                        g.fire(h.id)
                        page = Page.Roster
                    }
                }
                .setNegativeButton("Huỷ", null)
                .show()
        }, 16))
    }

    // ---------- Chọn đồ ----------

    private fun buildPick(root: LinearLayout, h: Hero, slot: Slot) {
        root.addView(ui.section("${slot.label} — ${h.name}"))
        val curId = h.equipment[slot]
        val cur = curId?.let { s.item(it) }
        val cc = ui.card()
        if (cur == null) cc.addView(ui.text("Đang trống", 14f, ui.sub))
        else {
            cc.addView(ui.text("Đang mặc", 12f, ui.sub))
            cc.addView(ui.itemView(cur))
            cc.addView(ui.gap(ui.button("Tháo", Ui.Btn.SECONDARY) {
                a.act(reset = true) {
                    g.unequip(h.id, slot)
                    page = Page.Detail(h.id)
                }
            }, 8))
        }
        root.addView(cc)

        root.addView(ui.section("Trong kho"))
        val base = g.stats(h).power
        val options = g.inventory.filter { it.slot == slot && it.fits(h.cls) }
            .sortedWith(compareByDescending<Item> { it.rarity.ordinal }.thenByDescending { it.itemLevel })
        if (options.isEmpty()) root.addView(ui.text("Không có đồ phù hợp trong kho.", 14f, ui.sub).apply { setPadding(ui.dp(4), 0, 0, 0) })
        for (it in options) {
            val eq = h.equipment.toMutableMap().apply { put(slot, it.id) }
            val delta = g.stats(h, eq).power - base
            val c = ui.card {
                a.act(reset = true) {
                    g.equip(h.id, it.id)
                    page = Page.Detail(h.id)
                }
            }
            val line = ui.row()
            line.addView(ui.lpWeight(ui.itemView(it)))
            val sign = if (delta >= 0) "+" else ""
            line.addView(ui.text("$sign$delta", 15f, if (delta >= 0) ui.good else ui.bad, true))
            c.addView(line)
            root.addView(c)
        }
    }

    // ---------- Kho đồ ----------

    private fun buildInventory(root: LinearLayout) {
        val inv = g.inventory.sortedWith(compareByDescending<Item> { it.rarity.ordinal }.thenByDescending { it.itemLevel })
        val head = ui.card()
        head.addView(ui.text("Kho đồ ${inv.size}/${Game.MAX_ITEMS}", 16f, bold = true))
        head.addView(ui.text("Kho đầy thì đồ rơi được bán tự động.", 12f, ui.sub))
        val commons = inv.filter { it.rarity.ordinal == 0 }
        if (commons.isNotEmpty()) {
            val total = commons.sumOf { it.sellPrice }
            head.addView(ui.gap(ui.button("Bán hết đồ Thường (+${Fmt.num(total)} vàng)", Ui.Btn.SECONDARY) {
                AlertDialog.Builder(a)
                    .setMessage("Bán ${commons.size} món đồ Thường?")
                    .setPositiveButton("Bán") { _, _ -> a.act { commons.forEach { g.sell(it.id) } } }
                    .setNegativeButton("Huỷ", null)
                    .show()
            }, 8))
        }
        root.addView(head)

        for (it in inv) {
            val c = ui.card()
            val line = ui.row()
            line.addView(ui.lpWeight(ui.itemView(it)))
            line.addView(ui.button("Bán ${Fmt.num(it.sellPrice)}", Ui.Btn.SECONDARY) { a.act { g.sell(it.id) } })
            c.addView(line)
            root.addView(c)
        }
    }
}

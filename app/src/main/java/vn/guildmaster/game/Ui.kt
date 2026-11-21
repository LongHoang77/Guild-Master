package vn.guildmaster.game

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import vn.guildmaster.core.BattleEvent
import vn.guildmaster.core.EventKind
import vn.guildmaster.core.HeroClass
import vn.guildmaster.core.Item
import vn.guildmaster.core.Rarity
import vn.guildmaster.core.Stat
import java.util.Locale
import kotlin.math.min

class Ui(private val ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val density = ctx.resources.displayMetrics.density

    val bg = if (dark) 0xFF16181D.toInt() else 0xFFF4F1EA.toInt()
    val surface = if (dark) 0xFF22252C.toInt() else 0xFFFFFFFF.toInt()
    val surface2 = if (dark) 0xFF2E323B.toInt() else 0xFFE9E3D6.toInt()
    val text = if (dark) 0xFFECEAE4.toInt() else 0xFF1E1F24.toInt()
    val sub = if (dark) 0xFF9EA3AD.toInt() else 0xFF6B6E76.toInt()
    val accent = if (dark) 0xFFE9C46A.toInt() else 0xFF9C6B1C.toInt()
    val onAccent = if (dark) 0xFF1E1F24.toInt() else 0xFFFFFFFF.toInt()
    val good = if (dark) 0xFF81C784.toInt() else 0xFF2E7D32.toInt()
    val bad = if (dark) 0xFFE57373.toInt() else 0xFFC62828.toInt()
    val ally = if (dark) 0xFF64B5F6.toInt() else 0xFF1565C0.toInt()
    val divider = if (dark) 0xFF33363E.toInt() else 0xFFDDD6C8.toInt()

    fun dp(v: Number): Int = (v.toFloat() * density + 0.5f).toInt()

    fun rarityColor(r: Rarity) = when (r) {
        Rarity.COMMON -> if (dark) 0xFFB0B0B0.toInt() else 0xFF8A8A8A.toInt()
        Rarity.RARE -> 0xFF42A5F5.toInt()
        Rarity.EPIC -> 0xFFAB47BC.toInt()
        Rarity.LEGENDARY -> 0xFFFFA726.toInt()
    }

    fun clsColor(c: HeroClass) = when (c) {
        HeroClass.WARRIOR -> 0xFFC0392B.toInt()
        HeroClass.MAGE -> 0xFF5E35B1.toInt()
        HeroClass.ARCHER -> 0xFF2E7D32.toInt()
        HeroClass.PRIEST -> 0xFFB8860B.toInt()
    }

    // ---------- View cơ bản ----------

    fun text(s: CharSequence, size: Float = 14f, color: Int = text, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun section(s: String): TextView = text(s.uppercase(Locale.getDefault()), 12f, sub, true).apply {
        setPadding(dp(4), dp(16), dp(4), dp(6))
        letterSpacing = 0.05f
    }

    fun col(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun row(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

    private fun ripple(base: GradientDrawable) =
        RippleDrawable(ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x22000000), base, null)

    fun card(highlight: Boolean = false, onClick: (() -> Unit)? = null): LinearLayout = col().apply {
        val base = rounded(surface, 12f, if (highlight) dp(2) else 0, accent)
        background = if (onClick != null) ripple(base) else base
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(4), 0, dp(4)) }
        if (onClick != null) {
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    enum class Btn { PRIMARY, SECONDARY, DANGER }

    fun button(label: String, style: Btn = Btn.PRIMARY, enabled: Boolean = true, onClick: () -> Unit): TextView =
        text(label, 14f, bold = true).apply {
            val (bgc, fg) = when (style) {
                Btn.PRIMARY -> accent to onAccent
                Btn.SECONDARY -> surface2 to this@Ui.text
                Btn.DANGER -> bad to 0xFFFFFFFF.toInt()
            }
            setTextColor(fg)
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = ripple(rounded(bgc, 10f))
            alpha = if (enabled) 1f else 0.4f
            isClickable = true
            setOnClickListener { if (enabled) onClick() }
        }

    fun chip(label: String, color: Int, filled: Boolean = false, onClick: (() -> Unit)? = null): TextView =
        text(label, 12f, if (filled) 0xFFFFFFFF.toInt() else color, true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            val base = rounded(if (filled) color else Color.TRANSPARENT, 12f, dp(1), color)
            background = if (onClick != null) ripple(base) else base
            if (onClick != null) setOnClickListener { onClick() }
        }

    fun space(h: Int = 8): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(h), dp(h))
    }

    fun portrait(name: String, cls: HeroClass, rarity: Rarity, size: Int = 44): View =
        Portrait(ctx, name.take(1).uppercase(Locale.getDefault()), clsColor(cls), rarityColor(rarity), dp(3).toFloat()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { marginEnd = dp(12) }
        }

    fun bar(progress: Float, color: Int, height: Int = 6): Bar = Bar(ctx, surface2, color).apply {
        this.progress = progress
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height)).apply { topMargin = dp(6) }
    }

    fun lpWeight(v: View, w: Float = 1f): View = v.apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
    }

    fun gap(v: View, top: Int): View = v.apply {
        val lp = (layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top)
        layoutParams = lp
    }

    fun singleLine(t: TextView): TextView = t.apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    /** Khối mô tả 1 món đồ. */
    fun itemView(item: Item): LinearLayout = col().apply {
        addView(text("${item.name} · ${item.rarity.label} · Lv ${item.itemLevel}", 14f, rarityColor(item.rarity), true))
        addView(text(Fmt.stat(item.main), 13f))
        if (item.subs.isNotEmpty()) addView(text(item.subs.joinToString("  ·  ") { Fmt.stat(it) }, 12f, sub))
    }
}

class Portrait(ctx: Context, private val letter: String, private val fill: Int, private val ring: Int, private val ringW: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val r = min(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        p.color = ring
        c.drawCircle(cx, cy, r, p)
        p.color = fill
        c.drawCircle(cx, cy, r - ringW, p)
        p.color = Color.WHITE
        p.textSize = r * 0.9f
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        c.drawText(letter, cx, cy - (p.descent() + p.ascent()) / 2, p)
    }
}

class Bar(ctx: Context, private val track: Int, private val fill: Int) : View(ctx) {
    var progress = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        p.color = track
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(rect, r, r, p)
        if (progress > 0f) {
            p.color = fill
            rect.right = width * progress
            c.drawRoundRect(rect, r, r, p)
        }
    }
}

object Fmt {
    fun num(v: Long): String = String.format(Locale.US, "%,d", v).replace(',', '.')

    fun dur(ms: Long): String {
        val sec = (ms.coerceAtLeast(0) + 999) / 1000
        val h = sec / 3600
        val m = sec % 3600 / 60
        val s = sec % 60
        return when {
            h > 0 -> String.format(Locale.US, "%dg %02dp", h, m)
            m > 0 -> String.format(Locale.US, "%dp %02ds", m, s)
            else -> "${s}s"
        }
    }

    fun minutes(m: Int) = if (m >= 60) "${m / 60}g" else "${m}p"

    fun stat(s: Stat): String {
        val v = if (s.type.pct) String.format(Locale.US, "%.1f%%", s.value) else s.value.toLong().toString()
        return "${s.type.label} +$v"
    }

    fun ago(at: Long, now: Long): String {
        val d = (now - at).coerceAtLeast(0) / 60_000
        return when {
            d < 1 -> "vừa xong"
            d < 60 -> "$d phút trước"
            d < 1440 -> "${d / 60} giờ trước"
            else -> "${d / 1440} ngày trước"
        }
    }

    fun event(e: BattleEvent): String = when (e.kind) {
        EventKind.DAMAGE -> "${e.actor} [${e.skill}] → ${e.target}: −${e.amount}" + if (e.crit) " (chí mạng)" else ""
        EventKind.HEAL -> "${e.actor} [${e.skill}] → ${e.target}: +${e.amount} HP"
        EventKind.BUFF -> "${e.actor} dùng ${e.skill}" + if (e.target.isNotEmpty()) " cho ${e.target}" else ""
        EventKind.DEBUFF -> "${e.target} bị ${e.skill}"
        EventKind.REVIVE -> "${e.actor} [${e.skill}] → ${e.target} sống lại với ${e.amount} HP"
        EventKind.DOWN -> "${e.target} gục ngã"
        EventKind.STUNNED -> "${e.actor} bị choáng, mất lượt"
    }
}

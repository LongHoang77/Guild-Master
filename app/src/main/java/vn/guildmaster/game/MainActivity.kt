package vn.guildmaster.game

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import vn.guildmaster.core.Expedition
import vn.guildmaster.core.Game
import vn.guildmaster.core.GameError
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.random.Random

abstract class Screen(val a: MainActivity) {
    val ui get() = a.ui
    val g get() = a.game
    val s get() = a.game.s

    abstract fun build(root: LinearLayout)

    /** Trả true nếu đã xử lý nút Back (lùi trang con). */
    open fun back(): Boolean = false

    /** Đăng ký hàm cập nhật mỗi giây (đếm ngược...). */
    fun live(f: () -> Unit) {
        a.live += f
        f()
    }
}

class MainActivity : Activity() {
    lateinit var ui: Ui
    lateinit var game: Game
    private lateinit var store: SaveStore

    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var title: TextView
    private lateinit var goldText: TextView
    private val tabViews = mutableListOf<Pair<ImageView, TextView>>()

    val live = mutableListOf<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var tab = 0

    private val tabs = listOf(
        Triple("Hội", R.drawable.ic_guild, GuildScreen(this)),
        Triple("Nhiệm vụ", R.drawable.ic_quest, QuestScreen(this)),
        Triple("Mạo hiểm giả", R.drawable.ic_heroes, HeroScreen(this)),
        Triple("Quán rượu", R.drawable.ic_tavern, TavernScreen(this)),
        Triple("Nhật ký", R.drawable.ic_log, LogScreen(this)),
    )
    private val logScreen get() = tabs[4].third as LogScreen

    private val ticker = object : Runnable {
        override fun run() {
            if (game.tick(now())) {
                persist()
                render()
            } else {
                live.forEach { it() }
                updateHeader()
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun now() = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        store = SaveStore(this)
        val state = store.load() ?: Game.newGame(now(), Random.nextLong())
        game = Game(state)
        game.tick(now())
        persist()

        setContentView(buildFrame())
        Notify.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // Báo thức mất sau khi khởi động lại máy → đặt lại khi mở app
        for (e in game.s.expeditions) if (e.outcome == null) Alarms.schedule(this, e.quest.id, e.endAt, returnTitle(e))
        render(true)
        checkUpdate(false)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        persist()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (tabs[tab].third.back()) return
        if (tab != 0) {
            switchTab(0)
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun buildFrame(): View {
        val root = ui.col().apply { setBackgroundColor(ui.bg) }

        val header = ui.row().apply { setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(8)) }
        title = ui.text("", 20f, bold = true)
        goldText = ui.text("", 14f, ui.accent, true)
        header.addView(ui.lpWeight(title))
        header.addView(goldText)
        root.addView(header)

        body = ui.col().apply { setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(16)) }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(View(this).apply { setBackgroundColor(ui.divider) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        val bar = ui.row().apply { setPadding(0, ui.dp(6), 0, ui.dp(6)) }
        tabs.forEachIndexed { i, (label, icon, _) ->
            val cell = ui.col().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                setOnClickListener { switchTab(i) }
            }
            val iv = ImageView(this).apply { setImageResource(icon) }
            cell.addView(iv, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
            val tv = ui.singleLine(ui.text(label, 11f)).apply { gravity = Gravity.CENTER }
            cell.addView(tv)
            tabViews += iv to tv
            bar.addView(ui.lpWeight(cell))
        }
        root.addView(bar)
        return root
    }

    fun switchTab(i: Int) {
        tab = i
        render(true)
    }

    fun openLog(id: Long) {
        logScreen.open(id)
        switchTab(4)
    }

    /** Dựng lại màn hiện tại; giữ vị trí cuộn trừ khi reset. */
    fun render(reset: Boolean = false) {
        val y = if (reset) 0 else scroll.scrollY
        live.clear()
        body.removeAllViews()
        tabs[tab].third.build(body)
        updateHeader()
        tabViews.forEachIndexed { i, (iv, tv) ->
            val c = if (i == tab) ui.accent else ui.sub
            iv.setColorFilter(c)
            tv.setTextColor(c)
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun updateHeader() {
        title.text = tabs[tab].first
        goldText.text = "${Fmt.num(game.s.gold)} vàng · Hội Lv ${game.s.guildLevel}"
    }

    /** Chạy thao tác game: lỗi → toast; xong → lưu + dựng lại. */
    fun act(reset: Boolean = false, block: () -> Unit): Boolean {
        return try {
            block()
            persist()
            render(reset)
            true
        } catch (e: GameError) {
            toast(e.message ?: "Lỗi")
            false
        }
    }

    fun persist() {
        try {
            store.save(game.s)
        } catch (e: IOException) {
            toast("Không lưu được: ${e.message}")
        }
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- Tự cập nhật ----------

    var update: Updater.Release? = null
    var updateMsg = ""
    var updating = false

    /** manual = người dùng bấm kiểm tra → báo cả khi không có bản mới. */
    fun checkUpdate(manual: Boolean) {
        if (manual) toast("Đang kiểm tra…")
        thread {
            try {
                val r = Updater.fetchLatest()
                runOnUiThread {
                    update = r?.takeIf { it.code > Updater.currentCode(this) }
                    if (manual && update == null) toast("Đang dùng bản mới nhất")
                    render()
                }
            } catch (e: Exception) {
                if (manual) runOnUiThread { toast("Không kiểm tra được: ${e.message}") }
            }
        }
    }

    fun startUpdate() {
        val r = update ?: return
        if (updating) return
        if (!packageManager.canRequestPackageInstalls()) {
            toast("Hãy cho phép cài ứng dụng từ nguồn này rồi bấm Cập nhật lần nữa")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        persist()
        updating = true
        updateMsg = "Đang tải…"
        render()
        thread {
            try {
                Updater.install(this, r) { p ->
                    runOnUiThread { updateMsg = if (p >= 0) "Đang tải $p%" else "Đang tải…" }
                }
                runOnUiThread { updateMsg = "Đang cài đặt — xác nhận trên hộp thoại hệ thống" }
            } catch (e: Exception) {
                runOnUiThread {
                    updating = false
                    updateMsg = "Lỗi tải bản cập nhật: ${e.message}"
                    render()
                }
            }
        }
    }

    fun returnTitle(e: Expedition): String {
        val leader = e.heroIds.firstNotNullOfOrNull { game.s.hero(it) }?.name ?: "?"
        return "Đội $leader đã về"
    }
}

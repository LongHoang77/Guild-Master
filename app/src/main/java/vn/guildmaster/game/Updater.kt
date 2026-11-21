package vn.guildmaster.game

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tự cập nhật từ GitHub Releases: tag `v<versionCode>`, kèm 1 file .apk. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/LongHoang77/Guild-Master/releases/latest"

    class Release(val code: Long, val tag: String, val apkUrl: String, val notes: String)

    fun currentCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    fun currentName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "GuildMaster-app")
        }

    /** Gọi ở luồng nền. Null nếu release không đúng định dạng. */
    fun fetchLatest(): Release? {
        val c = open(LATEST)
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name")
            val code = tag.trimStart('v', 'V').toLongOrNull() ?: return null
            val assets = o.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Release(code, tag, a.getString("browser_download_url"), o.optString("body", ""))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    /** Gọi ở luồng nền: tải APK thẳng vào phiên cài đặt rồi commit. */
    fun install(ctx: Context, r: Release, progress: (Int) -> Unit) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            val c = open(r.apkUrl)
            try {
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    session.openWrite("update.apk", 0, total).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val p = if (total > 0) (done * 100 / total).toInt() else -1
                            if (p != last) {
                                last = p
                                progress(p)
                            }
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                c.disconnect()
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, 42, Intent(ctx, Receiver::class.java), flags)
            session.commit(pi.intentSender)
            session.close()
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    /** Nhận trạng thái cài: cần người dùng xác nhận → mở hộp thoại hệ thống. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    confirm?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(it)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {}
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "không rõ lỗi"
                    Toast.makeText(ctx, "Cập nhật thất bại: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

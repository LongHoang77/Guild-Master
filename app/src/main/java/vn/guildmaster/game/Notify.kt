package vn.guildmaster.game

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object Notify {
    private const val CHANNEL = "returns"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Đội trở về", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun show(ctx: Context, id: Long, title: String) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText("Mở hội quán để nhận thưởng")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id.toInt(), n)
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }
}

/** Báo đội về bằng setAndAllowWhileIdle (không cần quyền báo thức chính xác). */
object Alarms {
    private const val EXTRA_ID = "id"
    private const val EXTRA_TITLE = "title"

    private fun pending(ctx: Context, id: Long, title: String): PendingIntent {
        val i = Intent(ctx, Receiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(ctx, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context, id: Long, at: Long, title: String) {
        ctx.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, id, title))
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id, ""))
        Notify.cancel(ctx, id)
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(EXTRA_ID, 0)
            Notify.show(ctx, id, intent.getStringExtra(EXTRA_TITLE) ?: "Đội đã về")
        }
    }
}


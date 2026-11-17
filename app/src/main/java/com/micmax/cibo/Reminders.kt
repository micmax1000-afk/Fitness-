package com.micmax.cibo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

/** Un promemoria: [repeat] = "day" per ogni giorno alla stessa ora, null per una volta sola. */
data class Rem(val id: Int, val text: String, val at: Long, val repeat: String? = null, val open: String = "")

/** Promemoria con la sveglia di Android (precisi, anche a telefono fermo); si rimettono da soli dopo il riavvio. */
object Reminders {
    private fun prefs(c: Context) = c.getSharedPreferences("promemoria", Context.MODE_PRIVATE)
    fun load(c: Context): List<Rem> = runCatching {
        Gson().fromJson<List<Rem>>(prefs(c).getString("list", null), object : TypeToken<List<Rem>>() {}.type)
    }.getOrNull().orEmpty().filterNotNull()
    private fun save(c: Context, l: List<Rem>) = prefs(c).edit().putString("list", Gson().toJson(l)).apply()

    /** Il prossimo momento dopo [now]: per i ripetuti la stessa ora del giorno dopo, per gli altri null se è già passato. */
    fun nextOccurrence(at: Long, repeat: String?, now: Long): Long? {
        if (at > now) return at
        if (repeat != "day") return null
        val c = Calendar.getInstance().apply { timeInMillis = at }
        while (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun add(c: Context, text: String, at: Long, repeat: String? = null, open: String = ""): Rem {
        val id = prefs(c).getInt("next", 1000)
        prefs(c).edit().putInt("next", id + 1).apply()
        val r = Rem(id, text, at, repeat, open)
        save(c, load(c) + r)
        schedule(c, r)
        return r
    }

    fun remove(c: Context, id: Int) {
        cancel(c, id)
        save(c, load(c).filter { it.id != id })
    }

    private fun pending(c: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(c, id, Intent(c, ReminderReceiver::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(c: Context, r: Rem) {
        val at = nextOccurrence(r.at, r.repeat, System.currentTimeMillis()) ?: return
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c, r.id)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.onFailure { runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } }
    }

    fun cancel(c: Context, id: Int) { runCatching { c.getSystemService(AlarmManager::class.java).cancel(pending(c, id)) } }

    /** Dopo un riavvio: rimette tutte le sveglie, toglie i promemoria singoli ormai passati. */
    fun rescheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        val keep = load(c).filter { it.repeat == "day" || it.at > now }
        save(c, keep)
        keep.forEach { schedule(c, it) }
    }

    const val CHANNEL = "promemoria"

    fun notify(c: Context, r: Rem) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, r.id, Intent(c, MainActivity::class.java).putExtra("open", r.open).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notif).setContentTitle(r.text).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(open).build()
        runCatching { nm.notify(r.id, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val r = Reminders.load(c).firstOrNull { it.id == id } ?: return
        Reminders.notify(c, r)
        if (r.repeat == "day") Reminders.schedule(c, r) else Reminders.remove(c, r.id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminders.rescheduleAll(c)
        Emergency.sync(c)
    }
}

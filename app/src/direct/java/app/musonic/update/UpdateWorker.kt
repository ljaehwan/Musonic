package app.musonic.update

import app.musonic.R
import app.musonic.settings.*

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.musonic.MainActivity
import java.util.concurrent.TimeUnit

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        UpdateRepository(applicationContext).checkForUpdate()?.let { notifyUpdate(applicationContext, it) }
        Result.success()
    } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.success() }
    companion object {
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("musonic-update-check", ExistingPeriodicWorkPolicy.KEEP, work)
        }
        internal fun notifyUpdate(context: Context, info: ReleaseInfo) {
            val strings = AppPreferences.localizedContext(context)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            val prefs = context.getSharedPreferences("musonic_updates", Context.MODE_PRIVATE)
            if (prefs.getInt("notified", 0) >= info.code) return
            manager.createNotificationChannel(NotificationChannel("updates", strings.getString(R.string.app_updates), NotificationManager.IMPORTANCE_DEFAULT))
            val intent = PendingIntent.getActivity(context, 42, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(42, NotificationCompat.Builder(context, "updates").setSmallIcon(R.drawable.ic_update_notification)
                .setContentTitle(strings.getString(R.string.update_notification_title, info.name))
                .setContentText(strings.getString(R.string.update_notification_detail)).setContentIntent(intent).setAutoCancel(true).build())
            prefs.edit().putInt("notified", info.code).apply()
        }
    }
}

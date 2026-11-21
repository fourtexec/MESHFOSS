package ru.mesh.foss.app

import ru.mesh.foss.R
import ru.mesh.foss.core.*
import ru.mesh.foss.data.*
import ru.mesh.foss.ui.*

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Application: канал уведомлений и фоновая задача продления сессии МЭШ.
 */
class MeshApp : Application() {

    override fun onCreate() {
        super.onCreate()
        UiLanguage.select(getSharedPreferences("mesh_settings", MODE_PRIVATE).getString("language", "ru"))
        MeshLogStore.init(this)
        MeshLog.log("APP", "запуск MESHFOSS")
        createNotificationChannel()
        scheduleTokenRefresh()
    }

    internal fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val localized = UiLanguage.context(this)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localized.getString(R.string.notif_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Раз в 12 часов при наличии сети приложение без запроса обновляет токен МЭШ.
     * Так сессия не «остывает» и вход требуется только при явном отзыве токена.
     */
    private fun scheduleTokenRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TokenRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val CHANNEL_ID = "mesh_token"
    }
}

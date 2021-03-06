package eu.kanade.tachiyomi.data.updater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.DeviceUtil
import eu.kanade.tachiyomi.util.alarmManager
import eu.kanade.tachiyomi.util.notification
import eu.kanade.tachiyomi.util.notificationManager
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UpdateDownloaderAlarm : BroadcastReceiver() {

    companion object {
        const val CHECK_UPDATE_ACTION = "eu.kanade.CHECK_UPDATE"

        /**
         * Sets the alarm to run the intent that checks for update
         * @param context the application context.
         * @param intervalInHours the time in hours when it will be executed.
         */
        fun startAlarm(context: Context, intervalInHours: Int = 12,
                       isEnabled: Boolean = Injekt.get<PreferencesHelper>().automaticUpdateStatus()) {
            // Stop previous running alarms if needed, and do not restart it if the interval is 0.
            UpdateDownloaderAlarm.stopAlarm(context)
            if (intervalInHours == 0 || !isEnabled)
                return

            // Get the time the alarm should fire the event to update.
            val intervalInMillis = intervalInHours * 60 * 60 * 1000
            val nextRun = SystemClock.elapsedRealtime() + intervalInMillis

            // Start the alarm.
            val pendingIntent = getPendingIntent(context)
            context.alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    nextRun, intervalInMillis.toLong(), pendingIntent)
        }

        /**
         * Stops the alarm if it's running.
         * @param context the application context.
         */
        fun stopAlarm(context: Context) {
            val pendingIntent = getPendingIntent(context)
            context.alarmManager.cancel(pendingIntent)
        }

        /**
         * Returns broadcast intent
         * @param context the application context.
         * @return broadcast intent
         */
        fun getPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(context, 0,
                    Intent(context, UpdateDownloaderAlarm::class.java).apply {
                        this.action = CHECK_UPDATE_ACTION
                    }, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }


    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
        // Start the alarm when the system is booted.
            Intent.ACTION_BOOT_COMPLETED -> startAlarm(context)
        // Update the library when the alarm fires an event.
            CHECK_UPDATE_ACTION -> checkVersion(context)
        }
    }

    fun checkVersion(context: Context) {
        if (DeviceUtil.isNetworkConnected(context)) {
            val updateChecker = GithubUpdateChecker(context)
            updateChecker.checkForApplicationUpdate()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ release ->
                        //Get version of latest release
                        var newVersion = release.version
                        newVersion = newVersion.replace("[^\\d.]".toRegex(), "")

                        //Check if latest version is different from current version
                        if (newVersion != BuildConfig.VERSION_NAME) {
                            val downloadLink = release.downloadLink

                            val n = context.notification() {
                                setContentTitle(context.getString(R.string.update_check_notification_update_available))
                                addAction(android.R.drawable.stat_sys_download_done, context.getString(eu.kanade.tachiyomi.R.string.action_download),
                                        UpdateDownloader(context).getInstallOnReceivedIntent(UpdateDownloader.InstallOnReceived.RETRY_DOWNLOAD, downloadLink))
                                setSmallIcon(android.R.drawable.stat_sys_download_done)
                            }
                            // Displays the progress bar on notification
                            context.notificationManager.notify(0, n);
                        }
                    }, {
                        it.printStackTrace()
                    })
        }
    }

}
package org.rm3l.router_companion.job.firmware_update

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.evernote.android.job.DailyJob
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import needle.UiRelatedTask
import org.rm3l.ddwrt.BuildConfig
import org.rm3l.ddwrt.R
import org.rm3l.router_companion.RouterCompanionAppConstants
import org.rm3l.router_companion.RouterCompanionAppConstants.CLOUD_MESSAGING_TOPIC_DDWRT_BUILD_UPDATES
import org.rm3l.router_companion.RouterCompanionAppConstants.GOOGLE_API_KEY
import org.rm3l.router_companion.RouterCompanionAppConstants.NOTIFICATIONS_CHOICE_PREF
import org.rm3l.router_companion.RouterCompanionAppConstants.NOTIFICATIONS_ENABLE
import org.rm3l.router_companion.api.urlshortener.goo_gl.GooGlService
import org.rm3l.router_companion.api.urlshortener.goo_gl.resources.GooGlData
import org.rm3l.router_companion.common.utils.ExceptionUtils
import org.rm3l.router_companion.firmwares.FirmwareRelease
import org.rm3l.router_companion.firmwares.NoNewFirmwareUpdate
import org.rm3l.router_companion.firmwares.RouterFirmwareConnectorManager
import org.rm3l.router_companion.job.RouterCompanionJob
import org.rm3l.router_companion.mgmt.RouterManagementActivity
import org.rm3l.router_companion.multithreading.MultiThreadingManager
import org.rm3l.router_companion.resources.conn.NVRAMInfo
import org.rm3l.router_companion.resources.conn.Router
import org.rm3l.router_companion.tiles.status.router.StatusRouterStateTile
import org.rm3l.router_companion.utils.NetworkUtils
import org.rm3l.router_companion.utils.Utils
import org.rm3l.router_companion.utils.notifications.NOTIFICATION_GROUP_GENERAL_UPDATES
import org.rm3l.router_companion.utils.snackbar.SnackbarCallback
import org.rm3l.router_companion.utils.snackbar.SnackbarUtils
import java.util.concurrent.TimeUnit

const val LAST_RELEASE_CHECKED = "lastReleaseChecked"
const val MANUAL_REQUEST = "MANUAL_REQUEST"

class FirmwareUpdateCheckerJob : DailyJob(), RouterCompanionJob {

    companion object {
        @JvmField
        val TAG = FirmwareUpdateCheckerJob::class.java.simpleName!!

        @JvmStatic
        fun schedule() {
            //This is a premium feature
            if (BuildConfig.DONATIONS || BuildConfig.WITH_ADS) {
                Crashlytics.log(Log.DEBUG, TAG, "Firmware Build Updates feature is *premium*!")
                return
            }

            if (!JobManager.instance().getAllJobRequestsForTag(TAG).isEmpty()) {
                // job already scheduled, nothing to do => cancel
                Crashlytics.log(Log.DEBUG, TAG, "job $TAG already scheduled => nothing to do!")
//                JobManager.instance().cancelAllForTag(TAG)
//                return
            }
            val builder = JobRequest.Builder(TAG)
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                    .setRequiresCharging(true)
            // run job between 9am and 9pm
            DailyJob.schedule(builder,
                    TimeUnit.HOURS.toMillis(9),
                    TimeUnit.HOURS.toMillis(21))
        }

        @JvmStatic
        fun manualCheckForFirmwareUpdate(activity: Activity, gooGlService: GooGlService?, router: Router) {
            val alertDialog = ProgressDialog.show(activity, "Checking for firmware updates",
                    "Please wait...",
                    true)
            MultiThreadingManager.getWebTasksExecutor().execute(
                    object : UiRelatedTask<Pair<String?, Exception?>>() {
                        private var mNewerRelease: FirmwareRelease? = null
                        override fun doWork(): Pair<String?, Exception?> {
                            //First determine current version
                            try {
                                @Suppress("USELESS_ELVIS")
                                val nvramInfo = RouterFirmwareConnectorManager.getConnector(router)
                                        .getDataFor(activity, router,
                                                StatusRouterStateTile::class.java, null) ?:
                                        throw IllegalStateException("Could not retrieve local data")

                                val currentFwVer = nvramInfo.getProperty(NVRAMInfo.OS_VERSION,
                                        "")
                                if (currentFwVer.isNullOrBlank()) {
                                    throw IllegalStateException("Could not retrieve current firmware version")
                                }
                                mNewerRelease = RouterFirmwareConnectorManager.getConnector(router)
                                        .manuallyCheckForFirmwareUpdateAndReturnDownloadLink(currentFwVer)
                                if (mNewerRelease == null) {
                                    //No new update
                                    throw IllegalStateException("Could not retrieve current firmware version")
                                }
                                var newReleaseDLLink: String? = mNewerRelease!!.getDirectLink()
                                if (gooGlService != null) {
                                    try {
                                        val gooGlData = GooGlData()
                                        gooGlData.longUrl = newReleaseDLLink
                                        val response = gooGlService.shortenLongUrl(GOOGLE_API_KEY,
                                                gooGlData).execute()
                                        NetworkUtils.checkResponseSuccessful(response)
                                        newReleaseDLLink = response.body()!!.id
                                    } catch (e: Exception) {
                                        //Do not worry about that => fallback to the original DL link
                                    }
                                }
                                return newReleaseDLLink to null
                            } catch (e: Exception) {
                                Crashlytics.logException(e)
                                return null to e
                            }
                        }

                        override fun thenDoUiRelatedWork(result: Pair<String?, Exception?>?) {
                            Crashlytics.log(Log.DEBUG, TAG, "result: $result")
                            alertDialog.cancel()
                            if (result == null) {
                                Utils.displayMessage(activity,
                                        "Internal Error. Please try again later.",
                                        SnackbarUtils.Style.ALERT)
                                return
                            }
                            val exception = result.second
                            if (exception != null) {
                                when (exception) {
                                    is NoNewFirmwareUpdate -> Utils.displayMessage(activity,
                                            "Your router (${router.canonicalHumanReadableName}) is up to date.",
                                            SnackbarUtils.Style.CONFIRM)
                                    else -> Utils.displayMessage(activity,
                                            "Could not check for update: ${ExceptionUtils.getRootCause(exception).message}",
                                            SnackbarUtils.Style.ALERT)
                                }
                            } else if (mNewerRelease != null && result.first != null) {
                                val routerFirmware = router.routerFirmware
                                SnackbarUtils.buildSnackbar(activity,
                                        activity.findViewById(android.R.id.content),
                                        ContextCompat.getColor(activity, R.color.win8_blue),
                                        "A new ${routerFirmware?.officialName ?: ""} Build (${mNewerRelease!!.version}) is available for '${router.canonicalHumanReadableName}'",
                                        Color.WHITE,
                                        "View", //TODO Reconsider once we have an auto-upgrade firmware feature. Add link to perform the upgrade right away
                                        Color.YELLOW,
                                        Snackbar.LENGTH_LONG,
                                        object : SnackbarCallback {
                                            @Throws(Exception::class)
                                            override fun onShowEvent(bundle: Bundle?) {
                                            }

                                            @Throws(Exception::class)
                                            override fun onDismissEventSwipe(event: Int, bundle: Bundle?) {
                                            }

                                            @Throws(Exception::class)
                                            override fun onDismissEventActionClick(event: Int, bundle: Bundle?) {
                                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.first)))
                                            }

                                            @Throws(Exception::class)
                                            override fun onDismissEventTimeout(event: Int, token: Bundle?) {
                                            }

                                            @Throws(Exception::class)
                                            override fun onDismissEventManual(event: Int, bundle: Bundle?) {
                                            }

                                            @Throws(Exception::class)
                                            override fun onDismissEventConsecutive(event: Int, bundle: Bundle?) {
                                            }
                                        }, null, true)
                            } else {
                                Utils.displayMessage(activity,
                                        "Internal Error. Please try again later.",
                                        SnackbarUtils.Style.ALERT)
                            }
                        }
                    })
        }

        @JvmStatic
        fun handleJob(context: Context, params: Params?): Boolean {
            val routerDao = RouterManagementActivity.getDao(context)
            val globalPreferences = context.getSharedPreferences(
                    RouterCompanionAppConstants.DEFAULT_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            val gooGlService = NetworkUtils.createApiService(context,
                    RouterCompanionAppConstants.URL_SHORTENER_API_BASE_URL, GooGlService::class.java)

            //First check if user is interested in getting updates
            val notificationChoices = globalPreferences?.getStringSet(NOTIFICATIONS_CHOICE_PREF, emptySet())
            if (notificationChoices?.contains(CLOUD_MESSAGING_TOPIC_DDWRT_BUILD_UPDATES) != true) {
                Crashlytics.log(Log.DEBUG, TAG, "Not interested at this time in Firmware Build Updates!")
                //Check next day
                return true
            }

            //Now keep only routers for which the user has accepted notifications
            val forceCheck = params?.extras?.getBoolean(MANUAL_REQUEST, false)
            val releaseAndGooGlLinksMap: MutableMap<String, String?> = mutableMapOf()
            routerDao.allRouters
                    .filter { it.getPreferences(context)?.getBoolean(NOTIFICATIONS_ENABLE, true) == true }
                    .map { router ->
                        val mapped = router to try {
                            val nvramInfo = RouterFirmwareConnectorManager.getConnector(router)
                                    .getDataFor(context, router, StatusRouterStateTile::class.java, null)
                            //noinspection ConstantConditions
                            val currentFwVer = nvramInfo.getProperty(NVRAMInfo.OS_VERSION, "")!!.trim()
                            if (currentFwVer.isBlank()) null
                            else RouterFirmwareConnectorManager.getConnector(router)
                                    .manuallyCheckForFirmwareUpdateAndReturnDownloadLink(currentFwVer)
                        } catch (e: Exception) {
                            Crashlytics.logException(e)
                            null
                        }
                        mapped
                    }
                    .filter { routerAndNewestReleaseUpdatePair -> routerAndNewestReleaseUpdatePair.second != null }
                    .filter { routerAndNewestReleaseUpdatePair ->
                        val router = routerAndNewestReleaseUpdatePair.first
                        val release = routerAndNewestReleaseUpdatePair.second
                        val routerPreferences = router.getPreferences(context)
                        val newReleaseVersion = release!!.version
                        if (forceCheck == true) {
                            true
                        } else {
                            val toProcess = routerPreferences?.getString(LAST_RELEASE_CHECKED,
                                    "")!! != newReleaseVersion
                            if (toProcess) {
                                routerPreferences.edit().putString(LAST_RELEASE_CHECKED, newReleaseVersion).apply()
                                Utils.requestBackup(context)
                            }
                            toProcess
                        }
                    }
                    .forEach { routerAndNewestReleaseUpdatePair ->
                        try {
                            val router = routerAndNewestReleaseUpdatePair.first
                            val release = routerAndNewestReleaseUpdatePair.second
                            val newReleaseVersion = release!!.version
                            val newReleaseDownloadLink = release.getDirectLink()
                            val downloadLink = releaseAndGooGlLinksMap.getOrPut(
                                    "${router.routerFirmware!!.name}.$newReleaseVersion",
                                    {
                                        var newReleaseDLLink: String? = newReleaseDownloadLink
                                        try {
                                            val gooGlData = GooGlData()
                                            gooGlData.longUrl = newReleaseDLLink
                                            val response = gooGlService.shortenLongUrl(GOOGLE_API_KEY,
                                                    gooGlData).execute()
                                            NetworkUtils.checkResponseSuccessful(response)
                                            newReleaseDLLink = response.body()!!.id
                                        } catch (e: Exception) {
                                            //Do not worry about that => fallback to the original DL link
                                        }
                                        newReleaseDLLink
                                    }
                            ) ?: newReleaseDownloadLink

                            // pending implicit intent to view url
                            val resultIntent = Intent(Intent.ACTION_VIEW)
                            resultIntent.data = Uri.parse(downloadLink)

                            val pendingIntent = PendingIntent.getActivity(context,
                                    router.id /* Request code */, resultIntent,
                                    PendingIntent.FLAG_ONE_SHOT)

                            //        Intent intent = new Intent(this, RouterManagementActivity.class);
                            //        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            //        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                            //                FCM_NOTIFICATION_ID /* Request code */, intent,
                            //                PendingIntent.FLAG_ONE_SHOT);

                            val largeIcon = BitmapFactory.decodeResource(context.resources,
                                    R.mipmap.ic_launcher_ddwrt_companion)

                            //        Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                            val notificationBuilder = NotificationCompat.Builder(context,
                                    "$NOTIFICATION_GROUP_GENERAL_UPDATES-${Router.RouterFirmware.DDWRT.name}")
                                    .setGroup(NOTIFICATION_GROUP_GENERAL_UPDATES)
                                    .setLargeIcon(largeIcon)
                                    .setSmallIcon(R.mipmap.ic_launcher_ddwrt_companion)
                                    .setContentTitle(
                                            "A new ${router.routerFirmware!!.displayName} is available for ${router.canonicalHumanReadableName}")
                                    .setContentText(newReleaseVersion)
                                    .setAutoCancel(true)

                            //Notification sound, if required
                            val ringtoneUri = globalPreferences?.getString(
                                    RouterCompanionAppConstants.NOTIFICATIONS_SOUND, null)
                            if (ringtoneUri != null) {
                                notificationBuilder.setSound(Uri.parse(ringtoneUri),
                                        AudioManager.STREAM_NOTIFICATION)
                            }

                            if (!globalPreferences.getBoolean(RouterCompanionAppConstants.NOTIFICATIONS_VIBRATE,
                                    true)) {
                                notificationBuilder.setDefaults(Notification.DEFAULT_LIGHTS)
                                        .setVibrate(RouterCompanionAppConstants.NO_VIBRATION_PATTERN)
                                //                    if (ringtoneUri != null) {
                                //                        mBuilder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND);
                                //                    } else {
                                //                        mBuilder.setDefaults(Notification.DEFAULT_LIGHTS);
                                //                    }
                            }
                            notificationBuilder.setContentIntent(pendingIntent)

                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                                    as NotificationManager

                            notificationManager.notify(router.id + 99999 /* ID of notification */,
                                    notificationBuilder.build())
                        } catch (e: Exception) {
                            //No worries - go on with the next
                            Crashlytics.logException(e)
                        }
                    }
            return true
        }
    }

    override fun isOneShotJob() = false

    override fun onRunDailyJob(params: Params?): DailyJobResult {
        try {
            if (!handleJob(context, params)) {
                return DailyJobResult.CANCEL
            }
        } catch (e: Exception) {
            Crashlytics.logException(e)
        }
        return DailyJobResult.SUCCESS
    }

}

class FirmwareUpdateCheckerOneShotJob : Job(), RouterCompanionJob {

    companion object {
        val TAG = FirmwareUpdateCheckerOneShotJob::class.java.simpleName!!
    }

    override fun onRunJob(params: Params?): Result {
        return try {
            params?.extras?.putBoolean(MANUAL_REQUEST, true)
            if (FirmwareUpdateCheckerJob.handleJob(context, params)) Result.SUCCESS else Result.FAILURE
        } catch (e: Exception) {
            Crashlytics.logException(e)
            Result.FAILURE
        }
    }

}
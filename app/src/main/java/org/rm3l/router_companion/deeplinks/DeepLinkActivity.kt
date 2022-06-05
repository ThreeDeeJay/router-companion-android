package org.rm3l.router_companion.deeplinks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.TaskStackBuilder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.airbnb.deeplinkdispatch.DeepLink
import com.airbnb.deeplinkdispatch.DeepLinkEntry
import com.airbnb.deeplinkdispatch.DeepLinkEntry.Type.CLASS
import com.airbnb.deeplinkdispatch.DeepLinkHandler
import com.airbnb.deeplinkdispatch.DeepLinkResult
import com.airbnb.deeplinkdispatch.DeepLinkUri
import com.airbnb.deeplinkdispatch.Parser
import org.rm3l.router_companion.main.DDWRTMainActivity
import org.rm3l.router_companion.mgmt.RouterManagementActivity
import org.rm3l.router_companion.settings.RouterManagementSettingsActivity
import org.rm3l.router_companion.settings.RouterSettingsActivity
import org.rm3l.router_companion.settings.RouterSpeedTestSettingsActivity
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Created by rm3l on 7/22/17.
 */
@DeepLinkHandler(AppDeepLinkModule::class)
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DeepLinkDelegate, LibraryDeepLinkModuleLoader and AppDeepLinkModuleLoader
        // are generated at compile-time.
        val deepLinkDelegate = DeepLinkDelegate(AppDeepLinkModuleLoader())
        // Delegate the deep link handling to DeepLinkDispatch.
        // It will start the correct Activity based on the incoming Intent URI
        deepLinkDelegate.dispatchFrom(this)
        // Finish this Activity since the correct one has been just started
        finish()
    }
}

// TODO DeepLinkDelegate and AppDeepLinkModuleLoader are auto-generated by DeepLinkDispatch (via kapt)
// Make sure to replace these 2 classes when deep links are changed!!!
class AppDeepLinkModuleLoader : Parser {

    val REGISTRY = listOf(
        DeepLinkEntry(
            "dd-wrt://routers/{routerUuid}/speedtest/settings",
            CLASS,
            RouterSpeedTestSettingsActivity::class.java,
            null
        ),
        DeepLinkEntry(
            "ddwrt://routers/{routerUuid}/speedtest/settings",
            CLASS,
            RouterSpeedTestSettingsActivity::class.java,
            null
        ),
        DeepLinkEntry(
            "dd-wrt://routers/{routerUuidOrRouterName}/actions/{action}",
            CLASS,
            RouterActionsDeepLinkActivity::class.java,
            null
        ),
        DeepLinkEntry(
            "ddwrt://routers/{routerUuidOrRouterName}/actions/{action}",
            CLASS,
            RouterActionsDeepLinkActivity::class.java,
            null
        ),
        DeepLinkEntry(
            "dd-wrt://routers/{routerUuid}/settings",
            CLASS,
            RouterSettingsActivity::class.java,
            null
        ),
        DeepLinkEntry(
            "ddwrt://routers/{routerUuid}/settings",
            CLASS,
            RouterSettingsActivity::class.java,
            null
        ),
        DeepLinkEntry("dd-wrt://management", CLASS, RouterManagementActivity::class.java, null),
        DeepLinkEntry("dd-wrt://settings", CLASS, RouterManagementSettingsActivity::class.java, null),
        DeepLinkEntry("ddwrt://management", CLASS, RouterManagementActivity::class.java, null),
        DeepLinkEntry("ddwrt://settings", CLASS, RouterManagementSettingsActivity::class.java, null),
        DeepLinkEntry("dd-wrt://routers/{routerUuid}", CLASS, DDWRTMainActivity::class.java, null),
        DeepLinkEntry("ddwrt://routers/{routerUuid}", CLASS, DDWRTMainActivity::class.java, null)
    )

    override fun parseUri(uri: String?) =
        REGISTRY.firstOrNull { it.matches(uri) }
}

class DeepLinkDelegate(appDeepLinkModuleLoader: AppDeepLinkModuleLoader) {

    private val loaders: List<Parser>

    init {
        this.loaders = listOf(appDeepLinkModuleLoader)
    }

    private fun findEntry(uriString: String): DeepLinkEntry? {
        for (loader in loaders) {
            val entry = loader.parseUri(uriString)
            if (entry != null) {
                return entry
            }
        }
        return null
    }

    fun dispatchFrom(activity: Activity?): DeepLinkResult {
        if (activity == null) {
            throw NullPointerException("activity == null")
        }
        return dispatchFrom(activity, activity.intent)
    }

    fun dispatchFrom(activity: Activity?, sourceIntent: Intent?): DeepLinkResult {
        if (activity == null) {
            throw NullPointerException("activity == null")
        }
        if (sourceIntent == null) {
            throw NullPointerException("sourceIntent == null")
        }
        val uri = sourceIntent.data ?: return createResultAndNotify(
            activity,
            false,
            null,
            "No Uri in given activity's intent."
        )
        val uriString = uri.toString()
        val entry = findEntry(uriString)
        if (entry != null) {
            val deepLinkUri = DeepLinkUri.parse(uriString)
            val parameterMap = entry.getParameters(uriString)
            for (queryParameter in deepLinkUri.queryParameterNames()) {
                for (queryParameterValue in deepLinkUri.queryParameterValues(queryParameter)) {
                    if (parameterMap.containsKey(queryParameter)) {
                        Log.w(TAG, "Duplicate parameter name in path and query param: $queryParameter")
                    }
                    parameterMap[queryParameter] = queryParameterValue
                }
            }
            parameterMap[DeepLink.URI] = uri.toString()
            val parameters: Bundle
            if (sourceIntent.extras != null) {
                parameters = Bundle(sourceIntent.extras)
            } else {
                parameters = Bundle()
            }
            for ((key, value) in parameterMap) {
                parameters.putString(key, value)
            }
            try {
                val c = entry.activityClass
                var newIntent: Intent?
                var taskStackBuilder: TaskStackBuilder? = null
                if (entry.type == DeepLinkEntry.Type.CLASS) {
                    newIntent = Intent(activity, c)
                } else {
                    var method: Method
                    try {
                        method = c.getMethod(entry.method, Context::class.java)
                        if (method.returnType == TaskStackBuilder::class.java) {
                            taskStackBuilder = method.invoke(c, activity) as TaskStackBuilder
                            if (taskStackBuilder.intentCount == 0) {
                                return createResultAndNotify(
                                    activity,
                                    false,
                                    uri,
                                    "Could not deep link to method: " + entry.method + " intents length == 0"
                                )
                            }
                            newIntent = taskStackBuilder.editIntentAt(taskStackBuilder.intentCount - 1)
                        } else {
                            newIntent = method.invoke(c, activity) as Intent
                        }
                    } catch (exception: NoSuchMethodException) {
                        method = c.getMethod(entry.method, Context::class.java, Bundle::class.java)
                        if (method.returnType == TaskStackBuilder::class.java) {
                            taskStackBuilder = method.invoke(c, activity, parameters) as TaskStackBuilder
                            if (taskStackBuilder.intentCount == 0) {
                                return createResultAndNotify(
                                    activity,
                                    false,
                                    uri,
                                    "Could not deep link to method: " + entry.method + " intents length == 0"
                                )
                            }
                            newIntent = taskStackBuilder.editIntentAt(taskStackBuilder.intentCount - 1)
                        } else {
                            newIntent = method.invoke(c, activity, parameters) as Intent
                        }
                    }
                }
                if (newIntent!!.action == null) {
                    newIntent.action = sourceIntent.action
                }
                if (newIntent.data == null) {
                    newIntent.data = sourceIntent.data
                }
                newIntent.putExtras(parameters)
                newIntent.putExtra(DeepLink.IS_DEEP_LINK, true)
                newIntent.putExtra(DeepLink.REFERRER_URI, uri)
                if (activity.callingActivity != null) {
                    newIntent.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
                }
                if (taskStackBuilder != null) {
                    taskStackBuilder.startActivities()
                } else {
                    activity.startActivity(newIntent)
                }
                return createResultAndNotify(activity, true, uri, null)
            } catch (exception: NoSuchMethodException) {
                return createResultAndNotify(
                    activity,
                    false,
                    uri,
                    "Deep link to non-existent method: " + entry.method
                )
            } catch (exception: IllegalAccessException) {
                return createResultAndNotify(activity, false, uri, "Could not deep link to method: " + entry.method)
            } catch (exception: InvocationTargetException) {
                return createResultAndNotify(activity, false, uri, "Could not deep link to method: " + entry.method)
            }
        } else {
            return createResultAndNotify(
                activity,
                false,
                uri,
                "No registered entity to handle deep link: $uri"
            )
        }
    }

    fun supportsUri(uriString: String): Boolean {
        return findEntry(uriString) != null
    }

    companion object {
        private val TAG = DeepLinkDelegate::class.java.simpleName

        private fun createResultAndNotify(
            context: Context,
            successful: Boolean,
            uri: Uri?,
            error: String?
        ): DeepLinkResult {
            notifyListener(context, !successful, uri, error)
            return DeepLinkResult(successful, uri?.toString(), error ?: "")
        }

        private fun notifyListener(
            context: Context,
            isError: Boolean,
            uri: Uri?,
            errorMessage: String?
        ) {
            val intent = Intent()
            intent.action = DeepLinkHandler.ACTION
            intent.putExtra(DeepLinkHandler.EXTRA_URI, uri?.toString() ?: "")
            intent.putExtra(DeepLinkHandler.EXTRA_SUCCESSFUL, !isError)
            if (isError) {
                intent.putExtra(DeepLinkHandler.EXTRA_ERROR_MESSAGE, errorMessage)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }
}

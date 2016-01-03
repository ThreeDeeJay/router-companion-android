package org.rm3l.ddwrt.tiles.dashboard.system;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.github.curioustechizen.ago.RelativeTimeTextView;
import com.github.lzyzsd.circleprogress.ArcProgress;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;

import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.exceptions.DDWRTNoDataException;
import org.rm3l.ddwrt.exceptions.DDWRTTileAutoRefreshNotAllowedException;
import org.rm3l.ddwrt.main.DDWRTMainActivity;
import org.rm3l.ddwrt.resources.conn.NVRAMInfo;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.tiles.DDWRTTile;
import org.rm3l.ddwrt.tiles.status.router.StatusRouterMemoryTile;
import org.rm3l.ddwrt.utils.ColorUtils;
import org.rm3l.ddwrt.utils.SSHUtils;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.rm3l.ddwrt.mgmt.RouterManagementActivity.ROUTER_SELECTED;
import static org.rm3l.ddwrt.tiles.status.router.StatusRouterMemoryTile.getGrepProcMemInfo;
import static org.rm3l.ddwrt.utils.Utils.isDemoRouter;

/**
 * Created by rm3l on 03/01/16.
 */
public class MemoryTile extends DDWRTTile<NVRAMInfo>  {

    private static final String LOG_TAG = MemoryTile.class.getSimpleName();
    public static final String MEMORY_USED_PERCENT = (NVRAMInfo.MEMORY_USED + "_percent");

    private boolean isThemeLight;

    private long mLastSync;

    public MemoryTile(@NonNull Fragment parentFragment, @NonNull Bundle arguments, @Nullable Router router) {
        super(parentFragment, arguments, router, R.layout.tile_dashboard_mem, null);
        isThemeLight = ColorUtils.isThemeLight(mParentFragmentActivity);
        layout.findViewById(R.id.tile_dashboard_mem_arcprogress)
                .setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        //Open Router State tab
                        if (mParentFragmentActivity instanceof DDWRTMainActivity) {
                            ((DDWRTMainActivity) mParentFragmentActivity)
                                    .selectItemInDrawer(2);
                        } else {
                            //TODO Set proper flags ???
                            final Intent intent = new Intent(mParentFragmentActivity, DDWRTMainActivity.class);
                            intent.putExtra(ROUTER_SELECTED, mRouter.getUuid());
                            intent.putExtra(DDWRTMainActivity.SAVE_ITEM_SELECTED, 2);
                            mParentFragmentActivity.startActivity(intent);
                        }
                    }
                });
    }

    @Override
    public int getTileHeaderViewId() {
        return -1;
    }

    @Override
    public int getTileTitleViewId() {
        return R.id.tile_dashboard_mem_title;
    }

    @Nullable
    @Override
    protected Loader<NVRAMInfo> getLoader(int id, Bundle args) {
        return new AsyncTaskLoader<NVRAMInfo>(this.mParentFragmentActivity) {

            @Nullable
            @Override
            public NVRAMInfo loadInBackground() {

                try {
                    Crashlytics.log(Log.DEBUG, LOG_TAG, "Init background loader for " + StatusRouterMemoryTile.class + ": routerInfo=" +
                            mRouter + " / nbRunsLoader=" + nbRunsLoader);

                    isThemeLight = ColorUtils.isThemeLight(mParentFragmentActivity);

                    if (mRefreshing.getAndSet(true)) {
                        return new NVRAMInfo().setException(new DDWRTTileAutoRefreshNotAllowedException());
                    }
                    nbRunsLoader++;

                    updateProgressBarViewSeparator(0);

                    mLastSync = System.currentTimeMillis();

                    final NVRAMInfo nvramInfo = new NVRAMInfo();

                    updateProgressBarViewSeparator(10);
                    final String[] otherCmds;
                    if (isDemoRouter(mRouter)) {
                        otherCmds = new String[2];
                        otherCmds[0] = "13004 kB";
                        otherCmds[1] = "844 kB";
                    } else {
                        otherCmds = SSHUtils.getManualProperty(mParentFragmentActivity, mRouter,
                                mGlobalPreferences,
                                getGrepProcMemInfo("MemTotal"),
                                getGrepProcMemInfo("MemFree"));
                    }
                    updateProgressBarViewSeparator(30);

                    if (otherCmds != null && otherCmds.length >= 2) {
                        //Total
                        String memTotal = null;
                        List<String> strings = Splitter.on("MemTotal:").omitEmptyStrings()
                                .trimResults().splitToList(otherCmds[0].trim());
                        if (strings != null && strings.size() >= 1) {
                            memTotal = strings.get(0);
                            nvramInfo.setProperty(NVRAMInfo.MEMORY_TOTAL, memTotal);

                        }

                        //Free
                        String memFree = null;
                        strings = Splitter.on("MemFree:").omitEmptyStrings().trimResults()
                                .splitToList(otherCmds[1].trim());
                        if (strings != null && strings.size() >= 1) {
                            memFree = strings.get(0);
                            nvramInfo.setProperty(NVRAMInfo.MEMORY_FREE, strings.get(0));
                        }

                        //Mem used
                        String memUsed = null;
                        if (!(isNullOrEmpty(memTotal)
                                || isNullOrEmpty(memFree))) {
                            //noinspection ConstantConditions
                            final long memTotalLong = Long.parseLong(memTotal.replaceAll(" kB", "").trim());
                            final long memFreeLong = Long.parseLong(memFree.replaceAll(" kB", "").trim());
                            final long memUsedLong = memTotalLong - memFreeLong;
                            memUsed = (Long.toString(memUsedLong) + " kB");

                            nvramInfo.setProperty(NVRAMInfo.MEMORY_USED, memUsed);
                            if (memTotalLong > 0L) {
                                nvramInfo.setProperty(MEMORY_USED_PERCENT,
                                        Long.toString(
                                                Math.min(100, 100 * memUsedLong / memTotalLong)));
                            }
                        }

                        updateProgressBarViewSeparator(90);
                    }

                    if (nvramInfo.isEmpty()) {
                        throw new DDWRTNoDataException("No Data!");
                    }

                    return nvramInfo;

                } catch (@NonNull final Exception e) {
                    e.printStackTrace();
                    return new NVRAMInfo().setException(e);
                }
            }
        };
    }

    @Nullable
    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Nullable
    @Override
    protected OnClickIntent getOnclickIntent() {
        return null;
    }

    @Override
    public void onLoadFinished(Loader<NVRAMInfo> loader, NVRAMInfo data) {
        try {
            //Set tiles
            Crashlytics.log(Log.DEBUG, LOG_TAG, "onLoadFinished: loader=" + loader + " / data=" + data);

            layout.findViewById(R.id.tile_dashboard_mem_loading_view)
                    .setVisibility(View.GONE);
            layout.findViewById(R.id.tile_dashboard_mem_arcprogress)
                    .setVisibility(View.VISIBLE);

            if (data == null) {
                data = new NVRAMInfo().setException(new DDWRTNoDataException("No Data!"));
            }

            Exception exception = data.getException();

            Long memUsagePercent = null;
            if (exception == null) {
                try {
                    final String memUsagePercentStr = data.getProperty(MEMORY_USED_PERCENT);
                    if (!isNullOrEmpty(memUsagePercentStr)) {
                        memUsagePercent = Long.parseLong(memUsagePercentStr);
                    }
                } catch (final NumberFormatException nfe) {
                    Crashlytics.logException(nfe);
                    nfe.printStackTrace();
                    //No worries
                }
                if (memUsagePercent == null) {
                    Crashlytics.logException(new IllegalStateException("memUsagePercent == null"));
                    data = new NVRAMInfo().setException(new
                            DDWRTNoDataException("Invalid memory usage - please try again later!"));
                }
                exception = data.getException();
            }

            final TextView errorPlaceHolderView = (TextView) this.layout
                    .findViewById(R.id.tile_dashboard_mem_error);

            if (!(exception instanceof DDWRTTileAutoRefreshNotAllowedException)) {

                if (exception == null) {
                    errorPlaceHolderView.setVisibility(View.GONE);
                }

                final ArcProgress arcProgress = (ArcProgress)
                        layout.findViewById(R.id.tile_dashboard_mem_arcprogress);
                if (memUsagePercent != null) {
                    arcProgress.setProgress(memUsagePercent.intValue());
                }

                if (isThemeLight) {
                    arcProgress.setBackgroundColor(
                            ContextCompat.getColor(mParentFragmentActivity, R.color.white));
                } else {
                    arcProgress.setBackgroundColor(
                            ContextCompat.getColor(mParentFragmentActivity, R.color.black));
                }

                //Update last sync
                final RelativeTimeTextView lastSyncView = (RelativeTimeTextView)
                        layout.findViewById(R.id.tile_last_sync);
                lastSyncView.setReferenceTime(mLastSync);
                lastSyncView.setPrefix("Last sync: ");
            }

            if (exception != null && !(exception instanceof DDWRTTileAutoRefreshNotAllowedException)) {
                //noinspection ThrowableResultOfMethodCallIgnored
                final Throwable rootCause = Throwables.getRootCause(exception);
                errorPlaceHolderView.setText("Error: " + (rootCause != null ? rootCause.getMessage() : "null"));
                final Context parentContext = this.mParentFragmentActivity;
                errorPlaceHolderView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        //noinspection ThrowableResultOfMethodCallIgnored
                        if (rootCause != null) {
                            Toast.makeText(parentContext,
                                    rootCause.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
                errorPlaceHolderView.setVisibility(View.VISIBLE);
                updateProgressBarWithError();
            } else if (exception == null) {
                updateProgressBarWithSuccess();
            }

        } finally {
            Crashlytics.log(Log.DEBUG, LOG_TAG, "onLoadFinished(): done loading!");
            mRefreshing.set(false);
            doneWithLoaderInstance(this, loader);
        }
    }
}

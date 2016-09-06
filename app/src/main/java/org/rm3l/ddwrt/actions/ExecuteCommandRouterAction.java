package org.rm3l.ddwrt.actions;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Joiner;

import org.apache.commons.io.FileUtils;
import org.rm3l.ddwrt.common.resources.audit.ActionLog;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.utils.ReportingUtils;
import org.rm3l.ddwrt.utils.SSHUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by rm3l on 15/02/16.
 */
public class ExecuteCommandRouterAction extends AbstractRouterAction<String[]> {

    @NonNull
    private final Context mContext;
    @NonNull
    private final String[] mCmd;

    private Map<String, String[]> mResultMap = new ConcurrentHashMap<>();

    //Seems there is a limit on the number of characters we can pass to the SSH server console
    //This flag instructs to copy all the commands to a temporary file, upload the file to the router and exec it
    private boolean potentiallyLongCommand;

    public ExecuteCommandRouterAction(Router router, @NonNull Context context,
                                      @Nullable RouterActionListener listener,
                                      @NonNull final SharedPreferences globalSharedPreferences,
                                      @NonNull final String... cmd) {
        super(router, listener, RouterAction.EXEC_CMD, globalSharedPreferences);
        this.mContext = context;
        this.mCmd = cmd;
        this.potentiallyLongCommand = false;
    }

    public ExecuteCommandRouterAction(Router router, @NonNull Context context,
                                      @Nullable RouterActionListener listener,
                                      @NonNull final SharedPreferences globalSharedPreferences,
                                      boolean potentiallyLongCommand,
                                      @NonNull final String... cmd) {
        this(router, context, listener, globalSharedPreferences, cmd);
        this.potentiallyLongCommand = potentiallyLongCommand;
    }

    @Override
    protected ActionLog getActionLog() {
        return super.getActionLog()
                .setActionData(String.format("- Command: %s", Arrays.toString(mCmd)));
    }

    @Nullable
    @Override
    protected Context getContext() {
        return mContext;
    }

    @NonNull
    @Override
    protected RouterActionResult<String[]> doActionInBackground() {
        Exception exception = null;
        String[] resultForRouter = null;
        try {
            if (potentiallyLongCommand) {
                //Upload to a temp file, then execute that file
                File outputFile = null;
                final String remotePath = "/tmp/." + ExecuteCommandRouterAction.class.getSimpleName() + "_" + UUID.randomUUID() + ".sh";
                try {
                    outputFile = File.createTempFile(ExecuteCommandRouterAction.class.getSimpleName(), ".sh",
                            mContext.getCacheDir());
                    FileUtils.writeStringToFile(outputFile,
                            Joiner.on(" && ").skipNulls().join(mCmd));
                    //Now upload this file onto the remote router
                    if (!SSHUtils.scpTo(mContext, router, globalSharedPreferences,
                            outputFile.getAbsolutePath(), remotePath)) {
                        throw new IllegalStateException("Failed to copy set of remote commands to the router");
                    }
                    resultForRouter = SSHUtils.getManualProperty(mContext, router, globalSharedPreferences,
                            "chmod 700 " + remotePath + " > /dev/null 2>&1 ",
                            remotePath);
                } finally {
                    try {
                        if (outputFile != null) {
                            //noinspection ResultOfMethodCallIgnored
                            outputFile.delete();
                        }
                    } catch (final Exception e) {
                        ReportingUtils.reportException(mContext, e);
                        //No worries
                    } finally {
                        try {
                            SSHUtils.runCommands(mContext, globalSharedPreferences, router,
                                    "rm -rf " + remotePath);
                        } catch (final Exception e) {
                            ReportingUtils.reportException(mContext, e);
                            //No worries
                        }
                    }
                }

            } else {
                resultForRouter = SSHUtils.getManualProperty(
                        mContext,
                        router,
                        globalSharedPreferences,
                        mCmd);
            }

        } catch (Exception e) {
            e.printStackTrace();
            exception = e;
        }

        mResultMap.put(router.getUuid(), resultForRouter);

        return new RouterActionResult<>(resultForRouter, exception);
    }

    @Nullable
    @Override
    protected Object getDataToReturnOnSuccess() {
        return Collections.unmodifiableMap(mResultMap);
    }
}

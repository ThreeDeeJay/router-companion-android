package org.rm3l.ddwrt.actions.activity;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.exceptions.DDWRTCompanionException;
import org.rm3l.ddwrt.mgmt.RouterManagementActivity;
import org.rm3l.ddwrt.mgmt.dao.DDWRTCompanionDAO;
import org.rm3l.ddwrt.multithreading.MultiThreadingManager;
import org.rm3l.ddwrt.resources.conn.NVRAMInfo;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.resources.conn.Router.SSHAuthenticationMethod;
import org.rm3l.ddwrt.utils.SSHUtils;
import org.rm3l.ddwrt.utils.Utils;
import org.rm3l.ddwrt.web.WebActivity;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import needle.UiRelatedProgressTask;

import static org.rm3l.ddwrt.main.DDWRTMainActivity.SAVE_ROUTER_SELECTED;
import static org.rm3l.ddwrt.mgmt.RouterManagementActivity.ROUTER_SELECTED;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.HTTPS_ENABLE;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.HTTP_LANPORT;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.HTTP_PASSWD;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.HTTP_USERNAME;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.HTTP_WANPORT;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.LAN_IPADDR;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.REMOTE_MGT_HTTPS;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.WAN_IPADDR;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.DEFAULT_SHARED_PREFERENCES_KEY;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.EMPTY_STRING;
import static org.rm3l.ddwrt.web.WebUtils.DO_NOT_VERIFY;
import static org.rm3l.ddwrt.web.WebUtils.trustAllHosts;

/**
 * Created by rm3l on 08/03/16.
 */
public class OpenWebManagementPageActivity extends WebActivity {

    private static final String LOG_TAG = OpenWebManagementPageActivity.class.getSimpleName();

    public static final String URL_TO_OPEN = "URL_TO_OPEN";

    private WebManagementLoaderTask mWebManagementLoaderTask;

    private View mLoadingView;

    private TextView mErrorTextView;

    private String mUrl;

    private String mRealm;

    private DDWRTCompanionDAO mDao;

    private Router mRouter;

    @Override
    protected CharSequence getTitleStr() {
        return "Web Management Interface";
    }

    @Override
    protected int getTitleResId() {
        return 0;
    }

    @NonNull
    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    protected boolean autoOpenUrl() {
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        if (mRouter != null) {
            savedInstanceState.putString(SAVE_ROUTER_SELECTED, mRouter.getUuid());
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDao = RouterManagementActivity.getDao(this);

        final Intent intent = getIntent();
        String uuid = intent.getStringExtra(ROUTER_SELECTED);
        if (uuid == null) {
            if (savedInstanceState != null) {
                uuid = savedInstanceState.getString(SAVE_ROUTER_SELECTED);
            }
        }
        this.mRouter = this.mDao.getRouter(uuid);
        if (this.mRouter == null) {
            Toast.makeText(this,
                    "No router set or router no longer exists", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (intent.hasExtra(URL_TO_OPEN)) {
            mUrl = intent.getStringExtra(URL_TO_OPEN);
        }

        mToolbar.setSubtitle(Router
                .getCanonicalHumanReadableNameWithEffectiveInfo(this, this.mRouter, false));

        mWebview.setVisibility(View.GONE);

        mErrorTextView = (TextView)
                findViewById(R.id.error_placeholder);

        mLoadingView = findViewById(R.id.loading_view);
        if (mLoadingView != null) {
            mLoadingView.setVisibility(View.VISIBLE);
        }

        this.mWebManagementLoaderTask = new WebManagementLoaderTask();
        MultiThreadingManager.getWebTasksExecutor().execute(this.mWebManagementLoaderTask);
    }

    @Override
    protected WebViewClient getWebClient() {
        return new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mSwipeRefreshLayout.setRefreshing(true);
                OpenWebManagementPageActivity.this.mSavedUrl = url;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                mSwipeRefreshLayout.setRefreshing(false);
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                final int errorCode = error.getErrorCode();
                final CharSequence description = error.getDescription();
                Crashlytics.log(Log.DEBUG, LOG_TAG,
                        "GOT Page error : code : " + errorCode + ", Desc : " + description);
                showError(OpenWebManagementPageActivity.this, errorCode);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Utils.reportException(null, new WebException(("Error: " + failingUrl + " - errorCode=" + errorCode +
                        ": " + description)));
                Toast.makeText(OpenWebManagementPageActivity.this,
                        "Oh no! " + description, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, final SslError error) {
                //Case of self-signed certificates
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(
                                OpenWebManagementPageActivity.this);
                        builder.setMessage(getString(
                                R.string.notification_error_ssl_cert_invalid,
                                error.getUrl()));
                        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                handler.proceed();
                            }
                        });
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                handler.cancel();
                            }
                        });
                        final AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, String host, String realm) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final LayoutInflater layoutInflater = getLayoutInflater();

                        //If the current SSH authentication method is 'Password' , use that,
                        // otherwise, ask user to supply such info
                        String password = null;
                        if (SSHAuthenticationMethod.PASSWORD
                                .equals(mRouter.getSshAuthenticationMethod())) {
                            password = mRouter.getPasswordPlain();
                        }

                        final View mAuthPromptDialogview = layoutInflater
                                .inflate(R.layout.activity_open_web_mgmt_interface_http_auth_prompt, null);

                        final ScrollView contentScrollView = (ScrollView) mAuthPromptDialogview.findViewById(R.id.http_auth_prompt_scrollview);
                        final EditText usernamePrompt = (EditText) mAuthPromptDialogview.findViewById(R.id.http_auth_prompt_username);
                        final EditText passwordPrompt = (EditText) mAuthPromptDialogview.findViewById(R.id.http_auth_prompt_password);

                        passwordPrompt.setText(password, TextView.BufferType.EDITABLE);

                        final CheckBox showPasswordPrompt = (CheckBox) mAuthPromptDialogview.findViewById(R.id.http_auth_prompt_password_show_checkbox);
                        showPasswordPrompt
                                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                        if (!isChecked) {
                                            passwordPrompt.setInputType(
                                                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                                            Utils.scrollToView(contentScrollView, passwordPrompt);
                                            passwordPrompt.requestFocus();
                                        } else {
                                            passwordPrompt.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                                            Utils.scrollToView(contentScrollView, passwordPrompt);
                                            passwordPrompt.requestFocus();
                                        }
                                        passwordPrompt.setSelection(passwordPrompt.length());
                                    }
                                });

                        final AlertDialog.Builder httpBasicAuthCredsDialogPrompt =
                                new AlertDialog.Builder(OpenWebManagementPageActivity.this)
                                .setTitle(mUrl)
                                .setView(mAuthPromptDialogview)
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        handler.cancel();
                                    }
                                })
                                .setPositiveButton("Go", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String httpUser = usernamePrompt.getText().toString();
                                        final String httpPassword = passwordPrompt.getText().toString();

//                                        mWebview.setHttpAuthUsernamePassword(mUrl,
//                                                Strings.nullToEmpty(mRealm), httpUser, httpPassword);
                                        handler.proceed(httpUser, httpPassword);

//                                        mWebview.loadUrl(mUrl);
//                                        mLoadingView.setVisibility(View.GONE);
                                    }
                                });
                        httpBasicAuthCredsDialogPrompt.create().show();
                    }
                });
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                //Fixes issue https://fabric.io/lemra-inc2/android/apps/org.rm3l.ddwrt/issues/568283d6f5d3a7f76bb8f2dc
                if (url == null) {
                    return false;
                }
                return false;
//                        if (Uri.parse(url).getHost().endsWith("rm3l.org")) {
//                            // This is my web site, so do not override; let my WebView load the page
//                            return false;
//                        } else if (url.startsWith("mailto:")){
//                            startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url)));
//                            return true;
//                        }
//                        // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
//                        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//                        startActivity(intent);
//                        return true;
            }

            private void showError(Context mContext, int errorCode) {
                //Prepare message
                String message = null;
                String title = null;
                if (errorCode == WebViewClient.ERROR_AUTHENTICATION) {
                    message = "User authentication failed on server";
                    title = "Auth Error";
                } else if (errorCode == WebViewClient.ERROR_TIMEOUT) {
                    message = "The server is taking too much time to communicate. Try again later.";
                    title = "Connection Timeout";
                } else if (errorCode == WebViewClient.ERROR_TOO_MANY_REQUESTS) {
                    message = "Too many requests during this load";
                    title = "Too Many Requests";
                } else if (errorCode == WebViewClient.ERROR_UNKNOWN) {
                    message = "Generic error";
                    title = "Unknown Error";
                } else if (errorCode == WebViewClient.ERROR_BAD_URL) {
                    message = "Check entered URL..";
                    title = "Malformed URL";
                } else if (errorCode == WebViewClient.ERROR_CONNECT) {
                    message = "Failed to connect to the server";
                    title = "Connection";
                } else if (errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE) {
                    message = "Failed to perform SSL handshake";
                    title = "SSL Handshake Failed";
                } else if (errorCode == WebViewClient.ERROR_HOST_LOOKUP) {
                    message = "Server or proxy hostname lookup failed";
                    title = "Host Lookup Error";
                } else if (errorCode == WebViewClient.ERROR_PROXY_AUTHENTICATION) {
                    message = "User authentication failed on proxy";
                    title = "Proxy Auth Error";
                } else if (errorCode == WebViewClient.ERROR_REDIRECT_LOOP) {
                    message = "Too many redirects";
                    title = "Redirect Loop Error";
                } else if (errorCode == WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME) {
                    message = "Unsupported authentication scheme (not basic or digest)";
                    title = "Auth Scheme Error";
                } else if (errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME) {
                    message = "Unsupported URI scheme";
                    title = "URI Scheme Error";
                } else if (errorCode == WebViewClient.ERROR_FILE) {
                    message = "Generic file error";
                    title = "File";
                } else if (errorCode == WebViewClient.ERROR_FILE_NOT_FOUND) {
                    message = "File not found";
                    title = "File";
                } else if (errorCode == WebViewClient.ERROR_IO) {
                    message = "The server failed to communicate. Try again later.";
                    title = "IO Error";
                }

                if (message != null) {
                    new AlertDialog.Builder(mContext)
                            .setMessage(message)
                            .setTitle(title)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setCancelable(false)
                            .setPositiveButton("OK",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            setResult(RESULT_CANCELED);
                                        }
                                    }).show();
                }
            }
        };
    }

    @Override
    protected void onPause() {
        if (mWebManagementLoaderTask != null) {
            mWebManagementLoaderTask.cancel();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mWebManagementLoaderTask != null) {
            mWebManagementLoaderTask.cancel();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mWebManagementLoaderTask != null) {
            mWebManagementLoaderTask.cancel();
        }
        super.onDestroy();
    }

    class WebManagementLoaderTask
            extends UiRelatedProgressTask<WebManagementLoaderTask.Result, Integer> {

        private boolean canConnect(@NonNull final String urlStr)  {
            Crashlytics.log(Log.DEBUG, LOG_TAG, "--> Trying GET '" + urlStr + "'");
            HttpURLConnection urlConnection = null;
            try {
                final URL url = new URL(urlStr + "/Management.asp");
                if (url.getProtocol().toLowerCase().equals("https")) {
                    trustAllHosts();
                    final HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
                    https.setHostnameVerifier(DO_NOT_VERIFY);
                    urlConnection = https;
                } else {
                    urlConnection = (HttpURLConnection) url.openConnection();
                }
                //FIXME Add a user-preference
                urlConnection.setConnectTimeout(5000);
                final int statusCode = urlConnection.getResponseCode();
                String wwwAuthenticateHeaderField = urlConnection
                        .getHeaderField("WWW-Authenticate");
                if (wwwAuthenticateHeaderField != null) {
                    final List<String> stringList =
                            Splitter.on("=").omitEmptyStrings().splitToList(wwwAuthenticateHeaderField);
                    if (stringList.size() >= 2) {
                        final String realm = stringList.get(0);
                        if (realm != null) {
                            mRealm = realm.replaceAll("\"", "")
                                    .replaceAll("'", "");
                        }
                    }
                }
                Crashlytics.log(Log.DEBUG, LOG_TAG, "GET " + urlStr + " : " + statusCode);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                Crashlytics.log(Log.DEBUG, LOG_TAG, "Didn't succeed in GET'ing " + urlStr);
                return false;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }

        @Override
        protected WebManagementLoaderTask.Result doWork() {
            try {
                if (!TextUtils.isEmpty(mUrl)) {
                    //Skip, as it has already been pre-fetched
                    if (mUrl.startsWith("https://")) {
                        trustAllHosts();
                    }
                    return new Result(null);
                }
                final NVRAMInfo nvRamInfoFromRouter = SSHUtils.getNVRamInfoFromRouter(
                        OpenWebManagementPageActivity.this,
                        mRouter,
                        getSharedPreferences(DEFAULT_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE),
                        LAN_IPADDR,
                        WAN_IPADDR,
                        HTTP_LANPORT,
                        HTTP_WANPORT,
                        HTTP_USERNAME,
                        HTTP_PASSWD,
                        HTTPS_ENABLE,
                        REMOTE_MGT_HTTPS);

                if (nvRamInfoFromRouter == null || nvRamInfoFromRouter.isEmpty()) {
                    throw new DDWRTCompanionException(
                            "Unable to retrieve info about HTTPd service");
                }

                String lanUrl = "http";
                String wanUrl = "http";
                final String lanIpAddr = nvRamInfoFromRouter
                        .getProperty(LAN_IPADDR, EMPTY_STRING);
                final String lanPort = nvRamInfoFromRouter
                        .getProperty(HTTP_LANPORT, EMPTY_STRING);

                final String wanIpAddr = nvRamInfoFromRouter
                        .getProperty(WAN_IPADDR, EMPTY_STRING);
                final String wanPort = nvRamInfoFromRouter
                        .getProperty(HTTP_WANPORT, EMPTY_STRING);

                if ("1".equals(nvRamInfoFromRouter.getProperty(HTTPS_ENABLE))) {
                    lanUrl += "s";
                }
                if ("1".equals(nvRamInfoFromRouter.getProperty(REMOTE_MGT_HTTPS))) {
                    wanUrl += "s";
                }
                lanUrl += ("://" + lanIpAddr + (TextUtils.isEmpty(lanPort) ? "" : (":" + lanPort)));
                wanUrl += ("://" + wanIpAddr + (TextUtils.isEmpty(wanPort) ? "" : (":" + wanPort)));

                if (canConnect(lanUrl)) {
                    mUrl = lanUrl;
                } else if (canConnect(wanUrl)) {
                    mUrl = wanUrl;
                } else {
                    //Try with router IP / DNS
                    String urlFromRouterRemoteIpOrDns = "http";
                    final String remoteIpAddress = mRouter.getRemoteIpAddress();
                    if ("1".equals(nvRamInfoFromRouter.getProperty(HTTPS_ENABLE))) {
                        urlFromRouterRemoteIpOrDns += "s";
                    }
                    urlFromRouterRemoteIpOrDns +=
                            ("://" + remoteIpAddress + (TextUtils.isEmpty(lanPort) ? "" : (":" + lanPort)));
                    if (canConnect(urlFromRouterRemoteIpOrDns)) {
                        mUrl = urlFromRouterRemoteIpOrDns;
                    } else {
                        //WAN
                        urlFromRouterRemoteIpOrDns = "http";
                        if ("1".equals(nvRamInfoFromRouter.getProperty(REMOTE_MGT_HTTPS))) {
                            urlFromRouterRemoteIpOrDns += "s";
                        }
                        urlFromRouterRemoteIpOrDns +=
                                ("://" + remoteIpAddress + (TextUtils.isEmpty(wanPort) ? "" : (":" + wanPort)));
                        if (canConnect(urlFromRouterRemoteIpOrDns)) {
                            mUrl = urlFromRouterRemoteIpOrDns;
                        } else {
                            //TODO Maybe display dialog where user can explicitly provide the information
                            throw new DDWRTCompanionException("Could not connect to router");
                        }
                    }
                }
                return new Result(null);

            } catch (final Exception e) {
                e.printStackTrace();
                return new Result(e);
            }
        }

        @Override
        protected void onProgressUpdate(Integer integer) {

        }

        @Override
        protected void thenDoUiRelatedWork(WebManagementLoaderTask.Result result) {
            mLoadingView.setVisibility(View.GONE);
            final Exception exception = result.getException();
            if (exception != null) {
                @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                final Throwable rootCause = Throwables.getRootCause(exception);
                mErrorTextView.setVisibility(View.VISIBLE);
                mErrorTextView.setText("Error: " +
                        ExceptionUtils.getRootCauseMessage(exception));
                mErrorTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //noinspection ThrowableResultOfMethodCallIgnored
                        if (rootCause != null) {
                            Toast.makeText(OpenWebManagementPageActivity.this,
                                    rootCause.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
                mWebview.setVisibility(View.GONE);
            } else {
                mWebview.setVisibility(View.VISIBLE);
                mErrorTextView.setVisibility(View.GONE);
                mWebview.loadUrl(mUrl);
            }
//            mSwipeRefreshLayout.setEnabled(true);
        }

        class Result {
            private final Exception exception;

            public Result(Exception exception) {
                this.exception = exception;
            }

            public Exception getException() {
                return exception;
            }
        }
    }
}

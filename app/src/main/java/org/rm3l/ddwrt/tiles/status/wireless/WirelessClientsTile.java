/*
 * DD-WRT Companion is a mobile app that lets you connect to,
 * monitor and manage your DD-WRT routers on the go.
 *
 * Copyright (C) 2014  Armel Soro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact Info: Armel Soro <apps+ddwrt@rm3l.org>
 */

package org.rm3l.ddwrt.tiles.status.wireless;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.util.LruCache;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.cocosw.undobar.UndoBarController;
import com.github.curioustechizen.ago.RelativeTimeTextView;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.actions.DisableWANAccessRouterAction;
import org.rm3l.ddwrt.actions.EnableWANAccessRouterAction;
import org.rm3l.ddwrt.actions.ResetBandwidthMonitoringCountersRouterAction;
import org.rm3l.ddwrt.actions.RouterAction;
import org.rm3l.ddwrt.actions.RouterActionListener;
import org.rm3l.ddwrt.actions.WakeOnLANRouterAction;
import org.rm3l.ddwrt.exceptions.DDWRTNoDataException;
import org.rm3l.ddwrt.exceptions.DDWRTTileAutoRefreshNotAllowedException;
import org.rm3l.ddwrt.resources.ClientDevices;
import org.rm3l.ddwrt.resources.Device;
import org.rm3l.ddwrt.resources.MACOUIVendor;
import org.rm3l.ddwrt.resources.conn.NVRAMInfo;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.tiles.DDWRTTile;
import org.rm3l.ddwrt.tiles.status.bandwidth.BandwidthMonitoringTile;
import org.rm3l.ddwrt.tiles.status.wireless.filter.impl.HideInactiveClientsFilterVisitorImpl;
import org.rm3l.ddwrt.tiles.status.wireless.filter.impl.ShowOnlyHostsWithWANAccessDisabledFilterVisitorImpl;
import org.rm3l.ddwrt.tiles.status.wireless.sort.ClientsSortingVisitor;
import org.rm3l.ddwrt.tiles.status.wireless.sort.impl.ClientsAlphabeticalSortingVisitorImpl;
import org.rm3l.ddwrt.tiles.status.wireless.sort.impl.LastSeenClientsSortingVisitorImpl;
import org.rm3l.ddwrt.tiles.status.wireless.sort.impl.TopTalkersClientsSortingVisitorImpl;
import org.rm3l.ddwrt.utils.ColorUtils;
import org.rm3l.ddwrt.utils.DDWRTCompanionConstants;
import org.rm3l.ddwrt.utils.SSHUtils;
import org.rm3l.ddwrt.utils.Utils;
import org.rm3l.ddwrt.widgets.NetworkTrafficView;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.keyboardsurfer.android.widget.crouton.Style;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.rm3l.ddwrt.DDWRTMainActivity.ROUTER_ACTION;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.WAN_GATEWAY;
import static org.rm3l.ddwrt.tiles.status.bandwidth.BandwidthMonitoringTile.BandwidthMonitoringIfaceData;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.DDWRTCOMPANION_WANACCESS_IPTABLES_CHAIN;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.EMPTY_STRING;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.EMPTY_VALUE_TO_DISPLAY;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_NAME;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.getClientsUsageDataFile;

/**
 *
 */
public class WirelessClientsTile extends DDWRTTile<ClientDevices> implements PopupMenu.OnMenuItemClickListener {

    public static final String HIDE_INACTIVE_HOSTS = "hideInactiveHosts";
    public static final String SORT_LAST_SEEN = "sort_last_seen";
    public static final String SORT = "sort";
    public static final String SORT_TOP_TALKERS = SORT + "_top_talkers";
    public static final String SORT_APHABETICAL = SORT + "_aphabetical";
    public static final String SORTING_STRATEGY = "sorting_strategy";
    public static final String SHOW_ONLY_WAN_ACCESS_DISABLED_HOSTS = "show_only_wan_access_disabled_hosts";
    public static final String IN = "IN";
    public static final String OUT = "OUT";
    public static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance();
    public static final String TEMP_ROUTER_UUID = UUID.randomUUID().toString();
    public static final String RT_GRAPHS = "rt_graphs";
    private static final String LOG_TAG = WirelessClientsTile.class.getSimpleName();
    private static final int MAX_CLIENTS_TO_SHOW_IN_TILE = 199;
    private static final LruCache<String, MACOUIVendor> mMacOuiVendorLookupCache = new LruCache<String, MACOUIVendor>(MAX_CLIENTS_TO_SHOW_IN_TILE) {

        @Override
        protected void entryRemoved(boolean evicted, String key, MACOUIVendor oldValue, MACOUIVendor newValue) {
            super.entryRemoved(evicted, key, oldValue, newValue);
            Log.d(LOG_TAG, "entryRemoved(" + evicted + ", " + key + ")");
        }

        @Override
        protected MACOUIVendor create(final String macAddr) {
            if (isNullOrEmpty(macAddr)) {
                return null;
            }
            //Get to MAC OUI Vendor Lookup API
            try {
                final String url = String.format("%s/%s",
                        MACOUIVendor.MAC_VENDOR_LOOKUP_API_PREFIX, macAddr.toUpperCase());
                Log.d(LOG_TAG, "--> GET " + url);
                final HttpGet httpGet = new HttpGet(url);
                final HttpResponse httpResponse = Utils.getThreadSafeClient().execute(httpGet);
                final StatusLine statusLine = httpResponse.getStatusLine();
                final int statusCode = statusLine.getStatusCode();

                if (statusCode == 200) {
                    final HttpEntity entity = httpResponse.getEntity();
                    final InputStream content = entity.getContent();
                    try {
                        //Read the server response and attempt to parse it as JSON
                        final Reader reader = new InputStreamReader(content);
                        final GsonBuilder gsonBuilder = new GsonBuilder();
                        final Gson gson = gsonBuilder.create();
                        final MACOUIVendor[] macouiVendors = gson.fromJson(reader, MACOUIVendor[].class);
                        Log.d(LOG_TAG, "--> Result of GET " + url + ": " + Arrays.toString(macouiVendors));
                        if (macouiVendors == null || macouiVendors.length == 0) {
                            //Returning null so we can try again later
                            return null;
                        }
                        return macouiVendors[0];

                    } finally {
                        Closeables.closeQuietly(content);
                        entity.consumeContent();
                    }
                } else {
                    Log.e(LOG_TAG, "<--- Server responded with status code: " + statusCode);
                    if (statusCode == 204) {
                        //No Content found on the remote server - no need to retry later
                        return new MACOUIVendor();
                    }
                }

            } catch (final Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    };
    private static final String PER_IP_MONITORING_IP_TABLES_CHAIN = "DDWRTCompanion";
    public static final String USAGE_DB = "/tmp/." + PER_IP_MONITORING_IP_TABLES_CHAIN + "_usage.db";
    public static final String USAGE_DB_OUT = USAGE_DB + ".out";

    //Generate a random string, to use as discriminator for determining dhcp clients
    private static final String MAP_KEYWORD = WirelessClientsTile.class.getSimpleName() + UUID.randomUUID().toString();
    private static final BiMap<Integer, Integer> sortIds = HashBiMap.create(6);

    static {
        sortIds.put(R.id.tile_status_wireless_clients_sort_a_z, 72);
        sortIds.put(R.id.tile_status_wireless_clients_sort_z_a, 73);

        sortIds.put(R.id.tile_status_wireless_clients_sort_top_senders, 82);
        sortIds.put(R.id.tile_status_wireless_clients_sort_top_receivers, 83);
        sortIds.put(R.id.tile_status_wireless_clients_sort_top_senders_current_rate, 84);
        sortIds.put(R.id.tile_status_wireless_clients_sort_top_receivers_current_rate, 85);

        sortIds.put(R.id.tile_status_wireless_clients_sort_seen_recently, 92);
        sortIds.put(R.id.tile_status_wireless_clients_sort_not_seen_recently, 93);
    }

    final Router mRouterCopy;
    private final Object usageDataLock = new Object();
    @NonNull
    private final Map<String, BandwidthMonitoringIfaceData> bandwidthMonitoringIfaceDataPerDevice =
            Maps.newConcurrentMap();
    private String mCurrentIpAddress;
    private String mCurrentMacAddress;
    private String[] activeClients;
    private String[] activeDhcpLeases;
    @Nullable
    private List<String> broadcastAddresses;
    private File wrtbwmonScriptPath;
    private Map<Device, View> currentDevicesViewsMap = Maps.newConcurrentMap();
    private String mUsageDbBackupPath = null;

    private Loader<ClientDevices> mCurrentLoader;

    public WirelessClientsTile(@NonNull Fragment parentFragment, @NonNull Bundle arguments, Router router) {
        super(parentFragment, arguments,
                router,
                R.layout.tile_status_wireless_clients, R.id.tile_status_wireless_clients_togglebutton);

        //We are cloning the Router, with a new UUID, so as to have a different key into the SSH Sessions Cache
        //This is because we are fetching in a quite real-time manner, and we don't want to block other async tasks.
        mRouterCopy = new Router(mRouter).setUuid(TEMP_ROUTER_UUID);

        if (!this.mAutoRefreshToggle) {
            if (mParentFragmentPreferences != null) {
                mParentFragmentPreferences.edit()
                        .remove(getFormattedPrefKey(RT_GRAPHS))
                        .apply();
            }
        } else {
            if (mParentFragmentPreferences != null && !mParentFragmentPreferences.contains(getFormattedPrefKey(RT_GRAPHS))) {
                mParentFragmentPreferences.edit()
                        .putBoolean(getFormattedPrefKey(RT_GRAPHS), true)
                        .apply();
            }
        }

//        Create Options Menu
        final ImageButton tileMenu = (ImageButton) layout.findViewById(R.id.tile_status_wireless_clients_menu);
        if (!ColorUtils.isThemeLight(mParentFragmentActivity)) {
            //Set menu background to white
            tileMenu.setImageResource(R.drawable.abs__ic_menu_moreoverflow_normal_holo_dark);
        }
        tileMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final PopupMenu popup = new PopupMenu(mParentFragmentActivity, v);
                popup.setOnMenuItemClickListener(WirelessClientsTile.this);
                final MenuInflater inflater = popup.getMenuInflater();
                final Menu menu = popup.getMenu();
                inflater.inflate(R.menu.tile_status_wireless_clients_options, menu);

                //Disable menu item from preference
                if (mParentFragmentPreferences != null &&
                        mParentFragmentPreferences.getBoolean(getFormattedPrefKey(HIDE_INACTIVE_HOSTS), false)) {
                    //Mark as checked
                    menu.findItem(R.id.tile_status_wireless_clients_hide_inactive_hosts).setChecked(true);
                }

                final MenuItem rtMenuItem = menu.findItem(R.id.tile_status_wireless_clients_realtime_graphs);
                if (mParentFragmentPreferences != null) {
                    rtMenuItem.setVisible(true);
                    rtMenuItem
                            .setEnabled(mParentFragmentPreferences.contains(getFormattedPrefKey(RT_GRAPHS)));
                    rtMenuItem
                            .setChecked(mParentFragmentPreferences
                                    .getBoolean(getFormattedPrefKey(RT_GRAPHS), false));
                } else {
                    rtMenuItem.setVisible(false);
                }

                final MenuItem showOnlyHostsWithWANAccessDisabledMenuItem = menu
                        .findItem(R.id.tile_status_wireless_clients_show_only_hosts_with_wan_access_disabled);
                //If no devices with WAN Access Disabled, disable the corresponding menu item
                final boolean atLeastOneDeviceWithNoWANAccess = Sets.filter(currentDevicesViewsMap.keySet(), new Predicate<Device>() {
                    @Override
                    public boolean apply(Device input) {
                        return (input.getWanAccessState() == Device.WANAccessState.WAN_ACCESS_DISABLED);
                    }
                }).size() > 0;
                final boolean wanAccessTogglePref = mParentFragmentPreferences != null &&
                        mParentFragmentPreferences.getBoolean(getFormattedPrefKey(SHOW_ONLY_WAN_ACCESS_DISABLED_HOSTS), false);
                if (!atLeastOneDeviceWithNoWANAccess) {
                    showOnlyHostsWithWANAccessDisabledMenuItem.setChecked(false);
                    if (wanAccessTogglePref) {
                        mParentFragmentPreferences.edit()
                                .putBoolean(getFormattedPrefKey(SHOW_ONLY_WAN_ACCESS_DISABLED_HOSTS), false)
                                .apply();
                    }
                } else {
                    //Mark as checked
                    showOnlyHostsWithWANAccessDisabledMenuItem.setChecked(wanAccessTogglePref);
                }

                showOnlyHostsWithWANAccessDisabledMenuItem.setEnabled(atLeastOneDeviceWithNoWANAccess);

                if (mParentFragmentPreferences != null) {
                    final Integer currentSortStrategy = sortIds.inverse()
                            .get(mParentFragmentPreferences.getInt(getFormattedPrefKey(SORTING_STRATEGY), -1));
                    if (currentSortStrategy != null && currentSortStrategy > 0) {
                        final MenuItem currentSortMenuItem = menu.findItem(currentSortStrategy);
                        if (currentSortMenuItem != null) {
                            currentSortMenuItem.setEnabled(false);
                            currentSortMenuItem.setChecked(true);
                        }
                    }
                }

                popup.show();
            }
        });
    }

    @Override
    public int getTileTitleViewId() {
        return R.id.tile_status_wireless_clients_title;
    }

    @Override
    protected void onAutoRefreshToggleCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (mParentFragmentPreferences != null) {
            final SharedPreferences.Editor editor = mParentFragmentPreferences.edit();
            final String rtPrefKey = \"fake-key\";
            if (isChecked) {
                editor.putBoolean(rtPrefKey, true);
                //Destroy existing loader, and reload it
            } else {
                //This will cause menu item to be disabled
                editor.remove(rtPrefKey);
            }
            editor.apply();
            if (this.mCurrentLoader != null) {
                doneLoading(this.mCurrentLoader);
            }
        }
    }

    @Override
    public int getTileHeaderViewId() {
        return R.id.tile_status_wireless_clients_hdr;
    }

    @Nullable
    @Override
    protected Loader<ClientDevices> getLoader(int id, Bundle args) {
        this.mCurrentLoader = new AsyncTaskLoader<ClientDevices>(this.mParentFragmentActivity) {

            @Nullable
            @Override
            public ClientDevices loadInBackground() {

                Log.d(LOG_TAG, "Init background loader for " + WirelessClientsTile.class + ": routerInfo=" +
                        mRouter + " / this.mAutoRefreshToggle= " + mAutoRefreshToggle + " / nbRunsLoader=" + nbRunsLoader);

                //Determine broadcast address at each run (because that might change if connected to another network)
                try {
                    final WifiManager wifiManager = (WifiManager) mParentFragmentActivity.getSystemService(Context.WIFI_SERVICE);
                    final WifiInfo connectionInfo = wifiManager.getConnectionInfo();

                    mCurrentIpAddress = Utils.intToIp(connectionInfo.getIpAddress());
                    mCurrentMacAddress = connectionInfo.getMacAddress();
                } catch (@NonNull final Exception e) {
                    e.printStackTrace();
                    //No worries
                }

                if (nbRunsLoader > 0 && !mAutoRefreshToggle) {
                    //Skip run
                    Log.d(LOG_TAG, "Skip loader run");
                    return new ClientDevices().setException(new DDWRTTileAutoRefreshNotAllowedException());
                }
                nbRunsLoader++;

                final ClientDevices devices = new ClientDevices();

                broadcastAddresses = Lists.newArrayList();

                try {

                    //Get Broadcast Addresses (for WOL)
                    try {
                        final String[] wanAndLanBroadcast = SSHUtils.getManualProperty(mRouterCopy, mGlobalPreferences,
                                "ifconfig `nvram get wan_iface` | grep Bcast | awk -F'Bcast:' '{print $2}' | awk -F'Mask:' '{print $1}'",
                                "ifconfig `nvram get lan_ifname` | grep Bcast | awk -F'Bcast:' '{print $2}' | awk -F'Mask:' '{print $1}'");
                        if (wanAndLanBroadcast != null && wanAndLanBroadcast.length > 0) {
                            for (final String wanAndLanBcast : wanAndLanBroadcast) {
                                if (wanAndLanBcast == null) {
                                    continue;
                                }
                                broadcastAddresses.add(wanAndLanBcast.trim());
                            }
                        }
                    } catch (final Exception e) {
                        //No worries
                        e.printStackTrace();
                    }

                    //Active clients
                    activeClients = SSHUtils.getManualProperty(mRouterCopy, mGlobalPreferences, "arp -a");
                    if (activeClients != null) {
                        devices.setActiveClientsNum(activeClients.length);
                    }

                    //Active DHCP Leases
                    activeDhcpLeases = SSHUtils.getManualProperty(mRouterCopy, mGlobalPreferences, "cat /tmp/dnsmasq.leases");
                    if (activeDhcpLeases != null) {
                        devices.setActiveDhcpLeasesNum(activeDhcpLeases.length);
                    }

                    //Get WAN Gateway Address (we skip it!)
                    String gatewayAddress = EMPTY_STRING;
                    try {
                        final NVRAMInfo nvRamInfoFromRouter = SSHUtils
                                .getNVRamInfoFromRouter(mRouterCopy, mGlobalPreferences, WAN_GATEWAY);
                        if (nvRamInfoFromRouter != null) {
                            //noinspection ConstantConditions
                            gatewayAddress = nvRamInfoFromRouter.getProperty(WAN_GATEWAY, EMPTY_STRING).trim();
                        }
                    } catch (final Exception e) {
                        e.printStackTrace();
                        //No worries
                    }

                    final String[] output = SSHUtils.getManualProperty(mRouterCopy,
                            mGlobalPreferences, "grep dhcp-host /tmp/dnsmasq.conf | sed 's/.*=//' | awk -F , '{print \"" +
                                    MAP_KEYWORD +
                                    "\",$1,$3 ,$2}'",
                            "awk '{print \"" +
                                    MAP_KEYWORD +
                                    "\",$2,$3,$4}' /tmp/dnsmasq.leases",
                            "awk 'NR>1{print \"" +
                                    MAP_KEYWORD +
                                    "\",$4,$1,\"*\"}' /proc/net/arp",
                            "echo done");

                    Log.d(LOG_TAG, "output: " + Arrays.toString(output));

                    if (output == null) {
                        return devices;
                    }

                    final Map<String, Device> macToDevice = Maps.newHashMap();
                    final Multimap<String, Device> macToDeviceOutput = HashMultimap.create();

                    for (final String stdoutLine : output) {
                        if ("done".equals(stdoutLine)) {
                            break;
                        }
                        final List<String> as = Splitter.on(" ").splitToList(stdoutLine);
                        if (as != null && as.size() >= 4 && MAP_KEYWORD.equals(as.get(0))) {
                            final String macAddress = as.get(1);
                            if (isNullOrEmpty(macAddress) || "00:00:00:00:00:00".equals(macAddress)) {
                                //Skip clients with incomplete ARP set-up
                                continue;
                            }

                            final String ipAddress = as.get(2);
                            if (StringUtils.equalsIgnoreCase(ipAddress, gatewayAddress)) {
                                //Skip Gateway
                                continue;
                            }

                            final Device device = new Device(macAddress);
                            device.setIpAddress(ipAddress);

                            if (activeClients != null) {
                                for (final String activeClient : activeClients) {
                                    if (StringUtils.containsIgnoreCase(activeClient, macAddress)) {
                                        device.setActive(true);
                                        break;
                                    }
                                }
                            }

                            final String systemName = as.get(3);
                            if (!"*".equals(systemName)) {
                                device.setSystemName(systemName);
                            }

                            //Alias from SharedPreferences
                            if (mParentFragmentPreferences != null) {
                                final String deviceAlias = mParentFragmentPreferences.getString(macAddress, null);
                                if (!isNullOrEmpty(deviceAlias)) {
                                    device.setAlias(deviceAlias);
                                }
                            }

                            device.setMacouiVendorDetails(mMacOuiVendorLookupCache.get(macAddress));

                            macToDeviceOutput.put(macAddress, device);
                        }
                    }

                    for (final Map.Entry<String, Collection<Device>> deviceEntry : macToDeviceOutput.asMap().entrySet()) {
                        final String macAddr = deviceEntry.getKey();
                        final Collection<Device> deviceCollection = deviceEntry.getValue();
                        for (final Device device : deviceCollection) {
                            //Consider the one that has a Name, if any
                            if (!isNullOrEmpty(device.getSystemName())) {
                                macToDevice.put(macAddr, device);
                                break;
                            }
                        }
                        if (deviceCollection.isEmpty() || macToDevice.containsKey(macAddr)) {
                            continue;
                        }

                        macToDevice.put(macAddr,
                                deviceCollection.iterator().next());
                    }

                    String remoteChecksum = DDWRTCompanionConstants.EMPTY_STRING;

                    synchronized (usageDataLock) {

                        try {
                            final File file = getClientsUsageDataFile(mParentFragmentActivity, mRouter.getUuid());
                            mUsageDbBackupPath = file.getAbsolutePath();
                            Log.d(LOG_TAG, "mUsageDbBackupPath: " + mUsageDbBackupPath);

                            //Compute checksum of remote script, and see if usage DB exists remotely
                            final String[] remoteMd5ChecksumAndUsageDBCheckOutput = SSHUtils
                                    .getManualProperty(mRouterCopy, mGlobalPreferences,
                                            "[ -f " + WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE + " ] && " +
                                                    "md5sum " + WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE + " | awk '{print $1}'",
                                            "[ -f " + USAGE_DB + " ]; echo $?");
                            if (remoteMd5ChecksumAndUsageDBCheckOutput != null && remoteMd5ChecksumAndUsageDBCheckOutput.length > 1) {
                                remoteChecksum = nullToEmpty(remoteMd5ChecksumAndUsageDBCheckOutput[0]).trim();
                                final String doesUsageDataExistRemotely = remoteMd5ChecksumAndUsageDBCheckOutput[1];
                                Log.d(LOG_TAG, "doesUsageDataExistRemotely: " + doesUsageDataExistRemotely);
                                if (doesUsageDataExistRemotely != null &&
                                        file.exists() &&
                                        !"0".equals(doesUsageDataExistRemotely.trim())) {
                                    //Usage Data File does not exist - restore what we have on file (if any)
                                    SSHUtils.scpTo(mRouterCopy, mGlobalPreferences, mUsageDbBackupPath, USAGE_DB);
                                }
                            }


                        } catch (final Exception e) {
                            e.printStackTrace();
                            mUsageDbBackupPath = null;
                        }

                        /** http://www.dd-wrt.com/phpBB2/viewtopic.php?t=75275 */

                        //Copy wrtbwmon file to remote host (/tmp/), if needed
                        Log.d(LOG_TAG, "[COPY] Copying monitoring script to remote router, if needed...");
                        wrtbwmonScriptPath = new File(mParentFragmentActivity.getCacheDir(), WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_NAME);

                        FileUtils.copyInputStreamToFile(mParentFragmentActivity.getResources().openRawResource(R.raw.wrtbwmon_ddwrtcompanion),
                                wrtbwmonScriptPath);

                        //Compare MD5 checksum locally on remotely. If any differences, overwrite the remote one
                        final String localChecksum = Files.hash(wrtbwmonScriptPath, Hashing.md5()).toString();
                        Log.d(LOG_TAG, String.format("<localChecksum=%s , remoteChecksum=%s>", localChecksum, remoteChecksum));
                        if (!remoteChecksum.equalsIgnoreCase(localChecksum)) {
                            Log.i(LOG_TAG, "Local and remote Checksums for the per-client monitoring script are different " +
                                    "=> uploading the local one...");
                            SSHUtils.scpTo(mRouterCopy, mGlobalPreferences,
                                    wrtbwmonScriptPath.getAbsolutePath(),
                                    WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE);
                        }

                        //Run Setup (does not matter if already done)
                        Log.d(LOG_TAG, "[EXEC] Running per-IP bandwidth monitoring...");

                        final String[] usageDbOutLines = SSHUtils.getManualProperty(mRouterCopy, mGlobalPreferences,
                                "chmod 700 " + WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE,
                                WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE + " setup",
                                WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE + " read",
                                "sleep 1",
                                WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE + " update " + USAGE_DB,
                                WRTBWMON_DDWRTCOMPANION_SCRIPT_FILE_PATH_REMOTE + " publish-raw " + USAGE_DB + " " + USAGE_DB_OUT,
                                "cat " + USAGE_DB_OUT,
                                "rm -f " + USAGE_DB_OUT);

                        if (usageDbOutLines != null) {

                            for (final String usageDbOutLine : usageDbOutLines) {
                                if (isNullOrEmpty(usageDbOutLine)) {
                                    continue;
                                }
                                final List<String> splitToList = Splitter.on(",").omitEmptyStrings().splitToList(usageDbOutLine);
                                if (splitToList == null || splitToList.size() < 6) {
                                    Log.w(LOG_TAG, "Line split should have more than 6 elements: " + splitToList);
                                    continue;
                                }
                                final String macAddress = splitToList.get(0);
                                if (isNullOrEmpty(macAddress)) {
                                    continue;
                                }
                                final Device device = macToDevice.get(macAddress.trim());
                                if (device == null) {
                                    continue;
                                }

                                try {
                                    device.setRxTotal(Double.parseDouble(splitToList.get(2)));
                                } catch (final NumberFormatException nfe) {
                                    nfe.printStackTrace();
                                    //no worries
                                }
                                try {
                                    device.setTxTotal(Double.parseDouble(splitToList.get(3)));
                                } catch (final NumberFormatException nfe) {
                                    nfe.printStackTrace();
                                    //no worries
                                }

                                long lastSeen = -1l;
                                try {
                                    lastSeen = Long.parseLong(splitToList.get(1)) * 1000l;
                                    device.setLastSeen(lastSeen);
                                } catch (final NumberFormatException nfe) {
                                    nfe.printStackTrace();
                                    //no worries
                                }

                                if (!bandwidthMonitoringIfaceDataPerDevice.containsKey(macAddress)) {
                                    bandwidthMonitoringIfaceDataPerDevice.put(macAddress, new BandwidthMonitoringIfaceData());
                                }
                                final BandwidthMonitoringIfaceData bandwidthMonitoringIfaceData =
                                        bandwidthMonitoringIfaceDataPerDevice.get(macAddress);

                                try {
                                    final double rxRate = Double.parseDouble(splitToList.get(4));
                                    device.setRxRate(rxRate);
                                    if (lastSeen > 0l) {
                                        bandwidthMonitoringIfaceData.addData(IN,
                                                new BandwidthMonitoringTile.DataPoint(lastSeen, rxRate));
                                    }
                                } catch (final NumberFormatException nfe) {
                                    nfe.printStackTrace();
                                    //no worries
                                }
                                try {
                                    final double txRate = Double.parseDouble(splitToList.get(5));
                                    device.setTxRate(txRate);
                                    if (lastSeen > 0l) {
                                        bandwidthMonitoringIfaceData.addData(OUT,
                                                new BandwidthMonitoringTile.DataPoint(lastSeen, txRate));
                                    }
                                } catch (final NumberFormatException nfe) {
                                    nfe.printStackTrace();
                                    //no worries
                                }

                            }
                        }
                    }

                    //WAN Access
                    try {
                        final String[] wanAccessIptablesChainDump = SSHUtils.getManualProperty(mRouterCopy, mGlobalPreferences,
                                "iptables -L " + DDWRTCOMPANION_WANACCESS_IPTABLES_CHAIN + " --line-numbers -n 2>/dev/null; echo $?");
                        if (wanAccessIptablesChainDump != null) {
                            int exitStatus = -1;
                            if (wanAccessIptablesChainDump.length >= 1) {
                                //Get Command execution status
                                try {
                                    exitStatus = Integer.parseInt(wanAccessIptablesChainDump[wanAccessIptablesChainDump.length - 1]);
                                } catch (final NumberFormatException nfe) {
                                    nfe.printStackTrace();
                                    //No Worries
                                }
                            }
                            if (exitStatus == 0) {
                                for (final Device device : macToDevice.values()) {
                                    final String macAddr = nullToEmpty(device.getMacAddress());
                                    boolean wanAccessDisabled = false;
                                    for (final String wanAccessIptablesChainLine : wanAccessIptablesChainDump) {
                                        if (StringUtils.containsIgnoreCase(wanAccessIptablesChainLine, macAddr)
                                                && StringUtils.containsIgnoreCase(wanAccessIptablesChainLine, "DROP")) {
                                            device.setWanAccessState(Device.WANAccessState.WAN_ACCESS_DISABLED);
                                            wanAccessDisabled = true;
                                            break;
                                        }
                                    }
                                    if (!wanAccessDisabled) {
                                        device.setWanAccessState(Device.WANAccessState.WAN_ACCESS_ENABLED);
                                    }
                                }
                            }
                            //else WAN Access States will remain to 'UNKNOWN' for all devices
                        }
                    } catch (final Exception e) {
                        e.printStackTrace();
                        //No Worries - WAN Access States will remain to 'UNKNOWN'
                    }

                    //Save usage data file
                    final boolean disableBackup = (mParentFragmentPreferences != null &&
                            mParentFragmentPreferences.getBoolean("disableUsageDataAutoBackup", false));
                    Log.d(LOG_TAG, "disableBackup= " + disableBackup + " - mUsageDbBackupPath: " + mUsageDbBackupPath);
                    if (!disableBackup) {
                        try {
                            if (!isNullOrEmpty(mUsageDbBackupPath)) {
                                //Backup to new data file
                                synchronized (usageDataLock) {
                                    SSHUtils.scpFrom(mRouterCopy, mGlobalPreferences, USAGE_DB, mUsageDbBackupPath);
                                }
                            }
                        } catch (final Exception e) {
                            e.printStackTrace();
                            //No worries
                        }
                    }

                    //Final operation
                    for (final Device device : macToDevice.values()) {
                        devices.addDevice(device);
                    }

                    return devices;

                } catch (@NonNull final Exception e) {
                    Log.e(LOG_TAG, e.getMessage() + ": " + Throwables.getStackTraceAsString(e));
                    return new ClientDevices().setException(e);
                }
            }
        };

        return this.mCurrentLoader;
    }

    @Nullable
    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    /**
     * Called when a previously created loader has finished its load.  Note
     * that normally an application is <em>not</em> allowed to commit fragment
     * transactions while in this call, since it can happen after an
     * activity's state is saved.  See {@link android.support.v4.app.FragmentManager#beginTransaction()
     * FragmentManager.openTransaction()} for further discussion on this.
     * <p/>
     * <p>This function is guaranteed to be called prior to the release of
     * the last data that was supplied for this Loader.  At this point
     * you should remove all use of the old data (since it will be released
     * soon), but should not do your own release of the data since its Loader
     * owns it and will take care of that.  The Loader will take care of
     * management of its data so you don't have to.  In particular:
     * <p/>
     * <ul>
     * <li> <p>The Loader will monitor for changes to the data, and report
     * them to you through new calls here.  You should not monitor the
     * data yourself.  For example, if the data is a {@link android.database.Cursor}
     * and you place it in a {@link android.widget.CursorAdapter}, use
     * the {@link android.widget.CursorAdapter#CursorAdapter(android.content.Context,
     * android.database.Cursor, int)} constructor <em>without</em> passing
     * in either {@link android.widget.CursorAdapter#FLAG_AUTO_REQUERY}
     * or {@link android.widget.CursorAdapter#FLAG_REGISTER_CONTENT_OBSERVER}
     * (that is, use 0 for the flags argument).  This prevents the CursorAdapter
     * from doing its own observing of the Cursor, which is not needed since
     * when a change happens you will get a new Cursor throw another call
     * here.
     * <li> The Loader will release the data once it knows the application
     * is no longer using it.  For example, if the data is
     * a {@link android.database.Cursor} from a {@link android.content.CursorLoader},
     * you should not call close() on it yourself.  If the Cursor is being placed in a
     * {@link android.widget.CursorAdapter}, you should use the
     * {@link android.widget.CursorAdapter#swapCursor(android.database.Cursor)}
     * method so that the old Cursor is not closed.
     * </ul>
     *
     * @param loader The Loader that has finished.
     * @param data   The data generated by the Loader.
     */
    @Override
    public void onLoadFinished(Loader<ClientDevices> loader, ClientDevices data) {
        Log.d(LOG_TAG, "onLoadFinished: loader=" + loader + " / data=" + data);

        layout.findViewById(R.id.tile_status_wireless_clients_loading_view)
                .setVisibility(View.GONE);
        layout.findViewById(R.id.tile_status_wireless_clients_layout_list_container)
                .setVisibility(View.VISIBLE);
        layout.findViewById(R.id.tile_status_wireless_clients_togglebutton_container)
                .setVisibility(View.VISIBLE);

        //noinspection ThrowableResultOfMethodCallIgnored
        if (data == null ||
                (data.getDevices().isEmpty() &&
                        !(data.getException() instanceof DDWRTTileAutoRefreshNotAllowedException))) {
            if (data == null) {
                data = new ClientDevices().setException(new DDWRTNoDataException("No Data!"));
            }
        }

        final TextView errorPlaceHolderView = (TextView) this.layout.findViewById(R.id.tile_status_wireless_clients_error);

        final Exception exception = data.getException();

        if (!(exception instanceof DDWRTTileAutoRefreshNotAllowedException)) {

            if (exception == null) {
                errorPlaceHolderView.setVisibility(View.GONE);
            }

            final GridLayout clientsContainer = (GridLayout) this.layout.findViewById(R.id.tile_status_wireless_clients_layout_list_container);
            clientsContainer.removeAllViews();

            final Resources resources = mParentFragmentActivity.getResources();
            clientsContainer.setBackgroundColor(resources.getColor(android.R.color.transparent));

            //Number of Active Clients
            final int numActiveClients = data.getActiveClientsNum();
            ((TextView) layout.findViewById(R.id.tile_status_wireless_clients_active_clients_num))
                    .setText(numActiveClients >= 0 ? String.valueOf(numActiveClients) : EMPTY_VALUE_TO_DISPLAY);

            //Number of Active DHCP Leases
            final int numActiveDhcpLeases = data.getActiveDhcpLeasesNum();
            ((TextView) layout.findViewById(R.id.tile_status_wireless_clients_active_dhcp_leases_num))
                    .setText(numActiveDhcpLeases >= 0 ? String.valueOf(numActiveDhcpLeases) : EMPTY_VALUE_TO_DISPLAY);

            final Set<Device> devices = data.getDevices(MAX_CLIENTS_TO_SHOW_IN_TILE);

//            final int themeBackgroundColor = getThemeBackgroundColor(mParentFragmentActivity, mRouter.getUuid());
            final boolean isThemeLight = ColorUtils.isThemeLight(mParentFragmentActivity);

            final String expandedClientsPrefKey = \"fake-key\";

            Set<String> expandedClients;

            currentDevicesViewsMap.clear();

            final CardView.LayoutParams cardViewLayoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            cardViewLayoutParams.rightMargin = R.dimen.marginRight;
            cardViewLayoutParams.leftMargin = R.dimen.marginLeft;
            cardViewLayoutParams.bottomMargin = R.dimen.activity_vertical_margin;

            for (final Device device : devices) {

                expandedClients = mParentFragmentPreferences.getStringSet(expandedClientsPrefKey, null);
                if (expandedClients == null) {
                    //Add first item right away
                    mParentFragmentPreferences.edit()
                            .putStringSet(expandedClientsPrefKey, Sets.newHashSet(device.getMacAddress()))
                            .apply();
                }

                final CardView cardView = (CardView) mParentFragmentActivity.getLayoutInflater()
                        .inflate(R.layout.tile_status_wireless_client, null);

                //Create Options Menu
                final ImageButton tileMenu = (ImageButton) cardView.findViewById(R.id.tile_status_wireless_client_device_menu);

                if (!isThemeLight) {
                    //Set menu background to white
                    tileMenu.setImageResource(R.drawable.abs__ic_menu_moreoverflow_normal_holo_dark);
                }

                //Add padding to CardView on v20 and before to prevent intersections between the Card content and rounded corners.
                cardView.setPreventCornerOverlap(true);
                //Add padding in API v21+ as well to have the same measurements with previous versions.
                cardView.setUseCompatPadding(true);

                if (isThemeLight) {
                    //Light
                    cardView.setCardBackgroundColor(resources.getColor(R.color.cardview_light_background));
                } else {
                    //Default is Dark
                    cardView.setCardBackgroundColor(resources.getColor(R.color.cardview_dark_background));
                }

                //Highlight CardView
                cardView.setCardElevation(20f);

                final String macAddress = device.getMacAddress();

                final TextView deviceNameView = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_name);
                final String name = device.getName();
                deviceNameView.setText(name);

                final Device.WANAccessState wanAccessState = device.getWanAccessState();
                final boolean isDeviceWanAccessEnabled = (wanAccessState == Device.WANAccessState.WAN_ACCESS_ENABLED);
                if (isDeviceWanAccessEnabled) {
                    deviceNameView.setTextColor(resources.getColor(R.color.ddwrt_green));
                }
                final TextView deviceWanAccessStateView = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_details_wan_access);
                if (wanAccessState == null || isNullOrEmpty(wanAccessState.toString())) {
                    deviceWanAccessStateView.setText(EMPTY_VALUE_TO_DISPLAY);
                } else {
                    deviceWanAccessStateView.setText(wanAccessState.toString());
                }

                final TextView deviceMac = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_mac);
                deviceMac.setText(macAddress);

                final TextView deviceIp = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_ip);
                final String ipAddress = device.getIpAddress();
                deviceIp.setText(ipAddress);

                final boolean isThisDevice = (nullToEmpty(macAddress).equalsIgnoreCase(mCurrentMacAddress) &&
                        nullToEmpty(ipAddress).equals(mCurrentIpAddress));
                if (isThisDevice) {
                    final View thisDevice = cardView.findViewById(R.id.tile_status_wireless_client_device_this);
                    if (isThemeLight) {
                        //Set text color to blue
                        ((TextView) thisDevice)
                                .setTextColor(resources.getColor(R.color.blue));
                    }
                    thisDevice.setVisibility(View.VISIBLE);
                }

                final LinearLayout deviceDetailsPlaceHolder = (LinearLayout) cardView
                        .findViewById(R.id.tile_status_wireless_client_device_details_graph_placeholder);
                final View noDataView = cardView.findViewById(R.id.tile_status_wireless_client_device_details_no_data);

                deviceDetailsPlaceHolder.removeAllViews();

                final BandwidthMonitoringIfaceData bandwidthMonitoringIfaceData;
                synchronized (usageDataLock) {
                    bandwidthMonitoringIfaceData = bandwidthMonitoringIfaceDataPerDevice.get(macAddress);
                }

                final boolean hideGraphPlaceHolder = bandwidthMonitoringIfaceData == null || bandwidthMonitoringIfaceData.getData().isEmpty();
                if (hideGraphPlaceHolder) {
                    //Show no data
                    deviceDetailsPlaceHolder.setVisibility(View.GONE);
                    noDataView.setVisibility(View.VISIBLE);

                } else {

                    final Map<String, EvictingQueue<BandwidthMonitoringTile.DataPoint>> dataCircularBuffer =
                            bandwidthMonitoringIfaceData.getData();

                    long maxX = System.currentTimeMillis() + 5000;
                    long minX = System.currentTimeMillis() - 5000;
                    double maxY = 10;
                    double minY = 1.;

                    final XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
                    final XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();

                    //noinspection ConstantConditions
                    for (final Map.Entry<String, EvictingQueue<BandwidthMonitoringTile.DataPoint>> entry : dataCircularBuffer.entrySet()) {
                        final String inOrOut = entry.getKey();
                        final EvictingQueue<BandwidthMonitoringTile.DataPoint> dataPoints = entry.getValue();
                        final XYSeries series = new XYSeries(inOrOut);
                        for (final BandwidthMonitoringTile.DataPoint point : dataPoints) {
                            final long x = point.getTimestamp();
                            final double y = point.getValue();
                            series.add(x, y);
                            maxX = Math.max(maxX, x);
                            minX = Math.min(minX, x);
                            maxY = Math.max(maxY, y);
                            minY = Math.min(minY, y);
                        }

                        // Now we add our series
                        dataset.addSeries(series);

                        // Now we create the renderer
                        final XYSeriesRenderer renderer = new XYSeriesRenderer();
                        renderer.setLineWidth(2);

                        renderer.setColor(ColorUtils.getColor(inOrOut));
                        // Include low and max value
                        renderer.setDisplayBoundingPoints(true);
                        // we add point markers
                        renderer.setPointStyle(PointStyle.POINT);
                        renderer.setPointStrokeWidth(1);

                        mRenderer.addSeriesRenderer(renderer);
                    }

                    // We want to avoid black border
                    mRenderer.setMarginsColor(Color.argb(0x00, 0xff, 0x00, 0x00)); // transparent margins
                    // Disable Pan on two axis
                    mRenderer.setPanEnabled(false, false);
                    mRenderer.setYAxisMax(maxY + 10);
                    mRenderer.setYAxisMin(minY);
                    mRenderer.setXAxisMin(minX);
                    mRenderer.setXAxisMax(maxX + 10);
                    mRenderer.setShowGrid(false);
                    mRenderer.setClickEnabled(false);
                    mRenderer.setZoomEnabled(true);
                    mRenderer.setPanEnabled(false);
                    mRenderer.setZoomRate(6.0f);
                    mRenderer.setShowLabels(true);
                    mRenderer.setFitLegend(true);
                    mRenderer.setInScroll(true);

                    final GraphicalView chartView = ChartFactory.getTimeChartView(mParentFragmentActivity, dataset, mRenderer, null);
                    chartView.repaint();

                    deviceDetailsPlaceHolder.addView(chartView, 0);

                    deviceDetailsPlaceHolder.setVisibility(View.VISIBLE);
                    noDataView.setVisibility(View.GONE);
                }

                final NetworkTrafficView networkTrafficView =
                        new NetworkTrafficView(mParentFragmentActivity, isThemeLight, mRouter.getUuid(), device);
                networkTrafficView.setRxAndTxBytes(Double.valueOf(device.getRxRate()).longValue(),
                        Double.valueOf(device.getTxRate()).longValue());

                final LinearLayout trafficViewPlaceHolder = (LinearLayout) cardView
                        .findViewById(R.id.tile_status_wireless_client_network_traffic_placeholder);
                trafficViewPlaceHolder.removeAllViews();
                trafficViewPlaceHolder.addView(networkTrafficView);

                final TextView deviceSystemNameView = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_details_system_name);
                final String systemName = device.getSystemName();
                if (isNullOrEmpty(systemName)) {
                    deviceSystemNameView.setText(EMPTY_VALUE_TO_DISPLAY);
                } else {
                    deviceSystemNameView.setText(systemName);
                }

                //OUI Addr
                final TextView ouiVendorRowView = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_details_oui_addr);
                final MACOUIVendor macouiVendorDetails = device.getMacouiVendorDetails();
                if (macouiVendorDetails == null || isNullOrEmpty(macouiVendorDetails.getCompany())) {
                    ouiVendorRowView.setText(EMPTY_VALUE_TO_DISPLAY);
                } else {
                    ouiVendorRowView.setText(macouiVendorDetails.getCompany());
                }

                final RelativeTimeTextView lastSeenRowView = (RelativeTimeTextView) cardView.findViewById(R.id.tile_status_wireless_client_device_details_lastseen);
                final long lastSeen = device.getLastSeen();
                if (lastSeen <= 0l) {
                    lastSeenRowView.setText(EMPTY_VALUE_TO_DISPLAY);
                    lastSeenRowView.setReferenceTime(-1l);
                } else {
                    lastSeenRowView.setReferenceTime(lastSeen);
                    lastSeenRowView.setPrefix(DATE_FORMAT.format(new Date(lastSeen)) + "\n(");
                    lastSeenRowView.setSuffix(")");
                }

                final TextView totalDownloadRowView = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_details_total_download);
                final double rxTotal = device.getRxTotal();
                if (rxTotal < 0.) {
                    totalDownloadRowView.setText(EMPTY_VALUE_TO_DISPLAY);
                } else {
                    final long value = Double.valueOf(rxTotal).longValue();
                    totalDownloadRowView.setText(value + " B (" + byteCountToDisplaySize(value) + ")");
                }

                final TextView totalUploadRowView = (TextView) cardView.findViewById(R.id.tile_status_wireless_client_device_details_total_upload);
                final double txTotal = device.getTxTotal();
                if (txTotal < 0.) {
                    totalUploadRowView.setText(EMPTY_VALUE_TO_DISPLAY);
                } else {
                    final long value = Double.valueOf(txTotal).longValue();
                    totalUploadRowView.setText(value + " B (" + byteCountToDisplaySize(value) + ")");
                }

                final View ouiAndLastSeenView = cardView.findViewById(R.id.tile_status_wireless_client_device_details_oui_lastseen_table);
                final View trafficGraphPlaceHolderView = cardView.findViewById(R.id.tile_status_wireless_client_device_details_graph_placeholder);

                cardView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Set<String> clientsExpanded = new HashSet<>(mParentFragmentPreferences
                                .getStringSet(expandedClientsPrefKey, new HashSet<String>()));

                        if (ouiAndLastSeenView.getVisibility() == View.VISIBLE) {
                            ouiAndLastSeenView.setVisibility(View.GONE);
                            clientsExpanded.remove(macAddress);
                            cardView.setCardElevation(40f);
                        } else {
                            ouiAndLastSeenView.setVisibility(View.VISIBLE);
                            clientsExpanded.add(macAddress);
                            cardView.setCardElevation(2f);
                        }
                        if (hideGraphPlaceHolder) {
                            trafficGraphPlaceHolderView.setVisibility(View.GONE);
                            if (noDataView.getVisibility() == View.VISIBLE) {
                                noDataView.setVisibility(View.GONE);
                            } else {
                                noDataView.setVisibility(View.VISIBLE);
                            }
                        } else {
                            noDataView.setVisibility(View.GONE);
                            if (trafficGraphPlaceHolderView.getVisibility() == View.VISIBLE) {
                                trafficGraphPlaceHolderView.setVisibility(View.GONE);
                            } else {
                                trafficGraphPlaceHolderView.setVisibility(View.VISIBLE);
                            }
                        }
                        mParentFragmentPreferences.edit()
                                .putStringSet(expandedClientsPrefKey, clientsExpanded)
                                .apply();
                    }
                });

                expandedClients = mParentFragmentPreferences.getStringSet(expandedClientsPrefKey,
                        new HashSet<String>());
                if (expandedClients.contains(macAddress)) {
                    cardView.setCardElevation(40f);
                    //Expand detailed view
                    ouiAndLastSeenView.setVisibility(View.VISIBLE);
                    if (hideGraphPlaceHolder) {
                        noDataView.setVisibility(View.VISIBLE);
                        trafficGraphPlaceHolderView.setVisibility(View.GONE);
                    } else {
                        trafficGraphPlaceHolderView.setVisibility(View.VISIBLE);
                        noDataView.setVisibility(View.GONE);
                    }
                } else {
                    //Collapse detailed view
                    ouiAndLastSeenView.setVisibility(View.GONE);
                    trafficGraphPlaceHolderView.setVisibility(View.GONE);
                    noDataView.setVisibility(View.GONE);
                }

                tileMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final PopupMenu popup = new PopupMenu(mParentFragmentActivity, v);
                        popup.setOnMenuItemClickListener(new DeviceOnMenuItemClickListener(deviceNameView, device));
                        final MenuInflater inflater = popup.getMenuInflater();

                        final Menu menu = popup.getMenu();

                        inflater.inflate(R.menu.tile_status_wireless_client_options, menu);

                        if (isThisDevice) {
                            //WOL not needed as this is the current device
                            menu.findItem(R.id.tile_status_wireless_client_wol).setEnabled(false);
                        }

                        final MenuItem wanAccessStateMenuItem = menu.findItem(R.id.tile_status_wireless_client_wan_access_state);
                        if (wanAccessState == null || wanAccessState == Device.WANAccessState.WAN_ACCESS_UNKNOWN) {
                            wanAccessStateMenuItem.setEnabled(false);
                        } else {
                            wanAccessStateMenuItem.setEnabled(true);
                            wanAccessStateMenuItem.setChecked(isDeviceWanAccessEnabled);
                        }

                        popup.show();
                    }
                });

                currentDevicesViewsMap.put(device, cardView);
            }

            //Filters
            Set<Device> newDevices =
                    new HideInactiveClientsFilterVisitorImpl(mParentFragmentPreferences != null &&
                            mParentFragmentPreferences.getBoolean(getFormattedPrefKey(HIDE_INACTIVE_HOSTS), false))
                            .visit(currentDevicesViewsMap.keySet());
            newDevices =
                    new ShowOnlyHostsWithWANAccessDisabledFilterVisitorImpl(mParentFragmentPreferences != null &&
                            mParentFragmentPreferences.getBoolean(getFormattedPrefKey(SHOW_ONLY_WAN_ACCESS_DISABLED_HOSTS), false))
                            .visit(newDevices);

            newDevices = applyCurrentSortingStrategy(newDevices);

            for (final Device dev : newDevices) {
                final View view = currentDevicesViewsMap.get(dev);
                if (view != null) {
                    clientsContainer.addView(view);
                }
            }

            final Button showMore = (Button) this.layout.findViewById(R.id.tile_status_wireless_clients_show_more);
            //Whether to display 'Show more' button
            if (data.getDevicesCount() > MAX_CLIENTS_TO_SHOW_IN_TILE) {
                showMore.setVisibility(View.VISIBLE);
                showMore.setOnClickListener(this);
            } else {
                showMore.setVisibility(View.GONE);
            }

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
        }

        final View tileMenu = layout.findViewById(R.id.tile_status_wireless_clients_menu);
        if (currentDevicesViewsMap.isEmpty()) {
            tileMenu.setVisibility(View.GONE);
        } else {
            tileMenu.setVisibility(View.VISIBLE);
        }

        doneLoading(loader);

        Log.d(LOG_TAG, "onLoadFinished(): done loading!");
    }

    private void doneLoading(Loader<ClientDevices> loader) {
        if (mParentFragmentPreferences != null &&
                mParentFragmentPreferences.getBoolean(getFormattedPrefKey(RT_GRAPHS), false)) {
            //Reschedule next run right away (delay of 500ms), to have a pseudo realtime effect, regardless of the actual sync pref!
            //TODO Check how much extra load that represents on the router
            doneWithLoaderInstance(this, loader, 500l,
                    R.id.tile_status_wireless_clients_togglebutton_title, R.id.tile_status_wireless_clients_togglebutton_separator);
        } else {
            //Use classical sync
            doneWithLoaderInstance(this, loader,
                    R.id.tile_status_wireless_clients_togglebutton_title, R.id.tile_status_wireless_clients_togglebutton_separator);
        }
    }

    @NonNull
    private Set<Device> applyCurrentSortingStrategy(@NonNull final Set<Device> devicesToSort) {
        Integer currentSortingStrategy = null;
        if (mParentFragmentPreferences != null) {
            currentSortingStrategy = sortIds.inverse()
                    .get(mParentFragmentPreferences.getInt(getFormattedPrefKey(SORTING_STRATEGY),
                            -1));
        }
        if (currentSortingStrategy == null || currentSortingStrategy <= 0) {
            return devicesToSort;
        }

        ClientsSortingVisitor clientsSortingVisitor = null;
        switch (currentSortingStrategy) {
            case R.id.tile_status_wireless_clients_sort_a_z:
            case R.id.tile_status_wireless_clients_sort_z_a:
                clientsSortingVisitor = new ClientsAlphabeticalSortingVisitorImpl(currentSortingStrategy);
                break;
            case R.id.tile_status_wireless_clients_sort_seen_recently:
            case R.id.tile_status_wireless_clients_sort_not_seen_recently:
                clientsSortingVisitor = new LastSeenClientsSortingVisitorImpl(currentSortingStrategy);
                break;
            case R.id.tile_status_wireless_clients_sort_top_senders:
            case R.id.tile_status_wireless_clients_sort_top_receivers:
            case R.id.tile_status_wireless_clients_sort_top_senders_current_rate:
            case R.id.tile_status_wireless_clients_sort_top_receivers_current_rate:
                clientsSortingVisitor = new TopTalkersClientsSortingVisitorImpl(currentSortingStrategy);
                break;
            default:
                break;
        }

        if (clientsSortingVisitor == null) {
            return devicesToSort;
        }

        return clientsSortingVisitor.visit(devicesToSort);
    }

    @Nullable
    @Override
    protected OnClickIntent getOnclickIntent() {
        //TODO
        return null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final GridLayout clientsContainer = (GridLayout) this.layout.findViewById(R.id.tile_status_wireless_clients_layout_list_container);
        final int itemId = item.getItemId();
        switch (itemId) {
            case R.id.tile_status_wireless_clients_realtime_graphs: {
                if (mParentFragmentPreferences != null) {
                    mParentFragmentPreferences.edit()
                            .putBoolean(getFormattedPrefKey(RT_GRAPHS), !item.isChecked())
                            .apply();
                }
                return true;
            }
            case R.id.tile_status_wireless_clients_reset_counters: {
                //Reset Counters
                final Bundle token = new Bundle();
                token.putString(ROUTER_ACTION, RouterAction.RESET_COUNTERS.name());

                new UndoBarController.UndoBar(mParentFragmentActivity)
                        .message("Bandwidth Monitoring counters will be reset.")
                        .listener(new MenuActionItemClickListener())
                        .token(token)
                        .show();
                return true;
            }
            case R.id.tile_status_wireless_clients_show_only_hosts_with_wan_access_disabled: {
                //First filter (based on WAN Access State)
                final boolean showOnlyWanAccessDisabledHosts = !item.isChecked();
                Set<Device> newDevices =
                        new ShowOnlyHostsWithWANAccessDisabledFilterVisitorImpl(showOnlyWanAccessDisabledHosts)
                                .visit(currentDevicesViewsMap.keySet());

                //Apply all other visitors
                newDevices =
                        new HideInactiveClientsFilterVisitorImpl(mParentFragmentPreferences != null &&
                                mParentFragmentPreferences.getBoolean(getFormattedPrefKey(HIDE_INACTIVE_HOSTS), false))
                                .visit(newDevices);

                newDevices = applyCurrentSortingStrategy(newDevices);

                clientsContainer.removeAllViews();
                for (final Device device : newDevices) {
                    final View view;
                    if ((view = currentDevicesViewsMap.get(device)) != null) {
                        clientsContainer.addView(view);
                    }
                }

                //Save preference
                if (mParentFragmentPreferences != null) {
                    mParentFragmentPreferences.edit()
                            .putBoolean(getFormattedPrefKey(SHOW_ONLY_WAN_ACCESS_DISABLED_HOSTS), showOnlyWanAccessDisabledHosts)
                            .apply();
                }

                return true;
            }
            case R.id.tile_status_wireless_clients_hide_inactive_hosts: {
                final boolean hideInactive = !item.isChecked();

                //Filter
                Set<Device> newDevices =
                        new HideInactiveClientsFilterVisitorImpl(hideInactive).visit(currentDevicesViewsMap.keySet());

                newDevices =
                        new ShowOnlyHostsWithWANAccessDisabledFilterVisitorImpl(mParentFragmentPreferences != null &&
                                mParentFragmentPreferences.getBoolean(getFormattedPrefKey(SHOW_ONLY_WAN_ACCESS_DISABLED_HOSTS), false))
                                .visit(newDevices);

                newDevices = applyCurrentSortingStrategy(newDevices);

                clientsContainer.removeAllViews();
                for (final Device device : newDevices) {
                    final View view;
                    if ((view = currentDevicesViewsMap.get(device)) != null) {
                        clientsContainer.addView(view);
                    }
                }

                //Save preference
                if (mParentFragmentPreferences != null) {
                    mParentFragmentPreferences.edit()
                            .putBoolean(getFormattedPrefKey(HIDE_INACTIVE_HOSTS), hideInactive)
                            .apply();
                }
                return true;
            }
            case R.id.tile_status_wireless_clients_sort_a_z:
            case R.id.tile_status_wireless_clients_sort_z_a:
            case R.id.tile_status_wireless_clients_sort_top_senders:
            case R.id.tile_status_wireless_clients_sort_top_receivers:
            case R.id.tile_status_wireless_clients_sort_top_senders_current_rate:
            case R.id.tile_status_wireless_clients_sort_top_receivers_current_rate:
            case R.id.tile_status_wireless_clients_sort_seen_recently:
            case R.id.tile_status_wireless_clients_sort_not_seen_recently: {
                final boolean hideInactive = (mParentFragmentPreferences != null &&
                        mParentFragmentPreferences.getBoolean(getFormattedPrefKey(HIDE_INACTIVE_HOSTS), false));

                //Filters
                Set<Device> newDevices =
                        new HideInactiveClientsFilterVisitorImpl(hideInactive).visit(currentDevicesViewsMap.keySet());
                newDevices =
                        new ShowOnlyHostsWithWANAccessDisabledFilterVisitorImpl(mParentFragmentPreferences != null &&
                                mParentFragmentPreferences.getBoolean(getFormattedPrefKey(SHOW_ONLY_WAN_ACCESS_DISABLED_HOSTS), false))
                                .visit(newDevices);

                ClientsSortingVisitor clientsSortingVisitor = null;
                switch (itemId) {
                    case R.id.tile_status_wireless_clients_sort_a_z:
                    case R.id.tile_status_wireless_clients_sort_z_a:
                        clientsSortingVisitor = new ClientsAlphabeticalSortingVisitorImpl(itemId);
                        break;
                    case R.id.tile_status_wireless_clients_sort_top_senders:
                    case R.id.tile_status_wireless_clients_sort_top_receivers:
                    case R.id.tile_status_wireless_clients_sort_top_senders_current_rate:
                    case R.id.tile_status_wireless_clients_sort_top_receivers_current_rate:
                        clientsSortingVisitor = new TopTalkersClientsSortingVisitorImpl(itemId);
                        break;
                    case R.id.tile_status_wireless_clients_sort_seen_recently:
                    case R.id.tile_status_wireless_clients_sort_not_seen_recently:
                        clientsSortingVisitor = new LastSeenClientsSortingVisitorImpl(itemId);
                        break;
                    default:
                        break;
                }

                newDevices = clientsSortingVisitor.visit(newDevices);

                clientsContainer.removeAllViews();
                for (final Device device : newDevices) {
                    final View view;
                    if ((view = currentDevicesViewsMap.get(device)) != null) {
                        clientsContainer.addView(view);
                    }
                }

                //Save preference
                if (mParentFragmentPreferences != null) {
                    mParentFragmentPreferences.edit()
                            .putInt(getFormattedPrefKey(SORTING_STRATEGY), sortIds.get(itemId))
                            .apply();
                }

                return true;
            }
            case R.id.tile_status_wireless_clients_reset_sort_prefs: {
                if (mParentFragmentPreferences != null) {
                    mParentFragmentPreferences.edit()
                            .remove(getFormattedPrefKey(SORT_TOP_TALKERS))
                            .remove(getFormattedPrefKey(SORT_APHABETICAL))
                            .remove(getFormattedPrefKey(SORT_LAST_SEEN))
                            .remove(getFormattedPrefKey(SORTING_STRATEGY))
                            .apply();
                }
                Utils.displayMessage(mParentFragmentActivity, "Changes will appear upon next sync.", Style.CONFIRM);
                return true;
            }
            default:
                break;
        }

        return false;
    }


    private class MenuActionItemClickListener implements UndoBarController.AdvancedUndoListener, RouterActionListener {

        @Override
        public void onHide(@android.support.annotation.Nullable Parcelable parcelable) {
            if (parcelable instanceof Bundle) {
                final Bundle token = (Bundle) parcelable;
                final String routerAction = token.getString(ROUTER_ACTION);
                Log.d(LOG_TAG, "routerAction: [" + routerAction + "]");
                if (isNullOrEmpty(routerAction)) {
                    return;
                }
                try {
                    switch (RouterAction.valueOf(routerAction)) {
                        case RESET_COUNTERS:
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (usageDataLock) {
                                        new ResetBandwidthMonitoringCountersRouterAction(MenuActionItemClickListener.this,
                                                mGlobalPreferences)
                                                .execute(mRouter);
                                    }
                                }
                            }).start();
                            break;
                        default:
                            //Ignored
                            break;
                    }
                } catch (IllegalArgumentException | NullPointerException e) {
                    e.printStackTrace();
                    Utils.reportException(e);
                }
            }
        }

        @Override
        public void onClear(@NonNull Parcelable[] parcelables) {
            //Nothing to do
        }

        @Override
        public void onUndo(@android.support.annotation.Nullable Parcelable parcelable) {
            //Nothing to do
        }

        @Override
        public void onRouterActionSuccess(@NonNull RouterAction routerAction, @NonNull Router router, Object returnData) {
            switch (routerAction) {
                case RESET_COUNTERS:
                    //Also delete local backup (so it does not get restored on the router)
                    synchronized (usageDataLock) {
                        bandwidthMonitoringIfaceDataPerDevice.clear();
                        //noinspection ResultOfMethodCallIgnored
                        DDWRTCompanionConstants.getClientsUsageDataFile(mParentFragmentActivity, router.getUuid())
                                .delete();
                    }
                    break;
                default:
                    //Ignored
                    break;
            }
            Utils.displayMessage(mParentFragmentActivity,
                    String.format("Action '%s' executed successfully on host '%s'", routerAction.toString(), router.getRemoteIpAddress()),
                    Style.CONFIRM);
        }

        @Override
        public void onRouterActionFailure(@NonNull RouterAction routerAction, @NonNull Router router, @Nullable Exception exception) {
            Utils.displayMessage(mParentFragmentActivity,
                    String.format("Error on action '%s': %s", routerAction.toString(), ExceptionUtils.getRootCauseMessage(exception)),
                    Style.ALERT);
        }
    }

    private class DeviceOnMenuItemClickListener implements
            PopupMenu.OnMenuItemClickListener, UndoBarController.AdvancedUndoListener, RouterActionListener {

        @NonNull
        private final TextView deviceNameView;
        @NonNull
        private final Device device;

        private DeviceOnMenuItemClickListener(@NonNull TextView deviceNameView, @NonNull final Device device) {
            this.deviceNameView = deviceNameView;
            this.device = device;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final String macAddress = device.getMacAddress();
            final String deviceName = nullToEmpty(device.getName());

            switch (item.getItemId()) {
                case R.id.tile_status_wireless_client_wan_access_state:
                    final boolean disableWanAccess = item.isChecked();
                    new AlertDialog.Builder(mParentFragmentActivity)
                            .setIcon(R.drawable.ic_action_alert_warning)
                            .setTitle(String.format("%s WAN Access for '%s' (%s)",
                                    disableWanAccess ? "Disable" : "Enable", deviceName, macAddress))
                            .setMessage(String.format("This allows you to %s WAN (Internet) Access for a particular device.\n" +
                                            "%s\n\n" +
                                            "Note that:\n" +
                                            "- This leverages MAC Addresses, which may be relatively easy to spoof.\n" +
                                            "- This setting will get reverted the next time the router reboots. We are working on making this persistent.",
                                    disableWanAccess ? "disable" : "enable",
                                    disableWanAccess ? String.format("'%s' (%s) will still be able to connect to the router local networks, " +
                                            "but will not be allowed to connect to the outside.", deviceName, macAddress) :
                                            String.format("'%s' (%s) will now be able to get access to the outside.", deviceName, macAddress)))
                            .setCancelable(true)
                            .setPositiveButton(String.format("%s WAN Access!",
                                    disableWanAccess ? "Disable" : "Enable"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialogInterface, final int i) {
                                    final Bundle token = new Bundle();
                                    token.putString(ROUTER_ACTION,
                                            disableWanAccess ? RouterAction.DISABLE_WAN_ACCESS.name() :
                                                    RouterAction.ENABLE_WAN_ACCESS.name());

                                    new UndoBarController.UndoBar(mParentFragmentActivity)
                                            .message(String.format("WAN Access will be %s for '%s' (%s)",
                                                    disableWanAccess ? "disabled" : "enabled",
                                                    deviceName, macAddress))
                                            .listener(DeviceOnMenuItemClickListener.this)
                                            .token(token)
                                            .show();
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    //Cancelled - nothing more to do!
                                }
                            }).create().show();
                    return true;
                case R.id.tile_status_wireless_client_wol:
                    //TODO Support SecureOn Password????
                    new AlertDialog.Builder(mParentFragmentActivity)
                            .setIcon(R.drawable.ic_action_alert_warning)
                            .setTitle(String.format("Wake up '%s' (%s)", deviceName, macAddress))
                            .setMessage(String.format("This lets you turn on a computer via the network.\n" +
                                            "For this to work properly:\n" +
                                            "- '%s' (%s) hardware must support Wake-on-LAN (WOL). You can enable it in the BIOS or in the Operating System Settings.\n" +
                                            "- WOL magic packet will be sent from the router to '%s' (%s). To wake over the Internet, " +
                                            "you must forward packets from any port you want to the device you wish to wake.\n" +
                                            "Note that some computers support WOL only when they are in Sleep mode or Hibernated, " +
                                            "not powered off. Some may also require a SecureOn password, which is not supported (yet)!",
                                    deviceName, macAddress, deviceName, macAddress))
                            .setCancelable(true)
                            .setPositiveButton("Send Magic Packet!", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialogInterface, final int i) {

                                    final Bundle token = new Bundle();
                                    token.putString(ROUTER_ACTION, RouterAction.WAKE_ON_LAN.name());

                                    new UndoBarController.UndoBar(mParentFragmentActivity)
                                            .message(String.format("WOL Request will be sent from router to '%s' (%s)",
                                                    deviceName, macAddress))
                                            .listener(DeviceOnMenuItemClickListener.this)
                                            .token(token)
                                            .show();

//                                    new WoLUtils.SendWoLMagicPacketAsyncTask(mParentFragmentActivity, device).execute(macAddress, mBroadcastAddress);
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    //Cancelled - nothing more to do!
                                }
                            }).create().show();
                    return true;
                case R.id.tile_status_wireless_client_rename:
                    if (mParentFragmentPreferences == null) {
                        Toast.makeText(mParentFragmentActivity, "Internal Error: ", Toast.LENGTH_SHORT).show();
                        Utils.reportException(new
                                IllegalStateException("Click on R.id.tile_status_wireless_client_rename - mParentFragmentPreferences == null"));
                    } else {
                        final String currentAlias = mParentFragmentPreferences.getString(macAddress, null);
                        final boolean isNewAlias = isNullOrEmpty(currentAlias);
                        final EditText aliasInputText = new EditText(mParentFragmentActivity);
                        aliasInputText.setText(currentAlias, TextView.BufferType.EDITABLE);
                        aliasInputText.setHint("e.g., \"Mom's PC\"");
                        new AlertDialog.Builder(mParentFragmentActivity)
                                .setTitle((isNewAlias ? "Set device alias" : "Update device alias") + ": " + macAddress)
                                .setMessage("Note that the Alias you define here is stored locally only, not on the router.")
                                .setView(aliasInputText)
                                .setCancelable(true)
                                .setPositiveButton(isNewAlias ? "Set Alias" : "Update Alias", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(final DialogInterface dialogInterface, final int i) {
                                        try {
                                            final String newAlias = nullToEmpty(aliasInputText.getText().toString());
                                            if (newAlias.equals(currentAlias)) {
                                                return;
                                            }
                                            mParentFragmentPreferences.edit()
                                                    .putString(macAddress, newAlias)
                                                    .apply();
                                            //Update device name immediately
                                            device.setAlias(newAlias);
                                            deviceNameView.setText(device.getName());
                                            Utils.displayMessage(mParentFragmentActivity, "Alias set! Changes will appear upon next sync.", Style.CONFIRM);
                                        } catch (final Exception e) {
                                            Utils.reportException(new
                                                    IllegalStateException("Error: Click on R.id.tile_status_wireless_client_rename", e));
                                            Utils.displayMessage(mParentFragmentActivity, "Internal Error - please try again later", Style.ALERT);
                                        }

                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        //Cancelled - nothing more to do!
                                    }
                                }).create().show();
                    }
                    return true;
                default:
                    break;
            }
            return false;
        }

        @Override
        public void onHide(@android.support.annotation.Nullable Parcelable parcelable) {
            if (parcelable instanceof Bundle) {
                final Bundle token = (Bundle) parcelable;
                final String routerAction = token.getString(ROUTER_ACTION);
                Log.d(LOG_TAG, "routerAction: [" + routerAction + "]");
                if (isNullOrEmpty(routerAction)) {
                    return;
                }
                try {
                    switch (RouterAction.valueOf(routerAction)) {
                        case WAKE_ON_LAN:
                            if (broadcastAddresses == null) {
                                Utils.displayMessage(mParentFragmentActivity,
                                        "WOL Internal Error: unable to fetch broadcast addresses. Try again later.",
                                        Style.ALERT);
                                Utils.reportException(new IllegalStateException("WOL Internal Error: unable to fetch broadcast addresses. Try again later."));
                                return;
                            }
                            new WakeOnLANRouterAction(this, mGlobalPreferences, device,
                                    broadcastAddresses.toArray(new String[broadcastAddresses.size()]))
                                    .execute(mRouter);
                            break;
                        case DISABLE_WAN_ACCESS:
                            new DisableWANAccessRouterAction(this, mGlobalPreferences, device).execute(mRouter);
                            break;
                        case ENABLE_WAN_ACCESS:
                            new EnableWANAccessRouterAction(this, mGlobalPreferences, device).execute(mRouter);
                            break;
                        default:
                            //Ignored
                            break;
                    }
                } catch (IllegalArgumentException | NullPointerException e) {
                    e.printStackTrace();
                    Utils.reportException(e);
                }
            }
        }

        @Override
        public void onClear(@NonNull Parcelable[] parcelables) {
            //Nothing to do
        }

        @Override
        public void onRouterActionSuccess(@NonNull RouterAction routerAction, @NonNull Router router, Object returnData) {
            Utils.displayMessage(mParentFragmentActivity,
                    String.format("Action '%s' executed successfully on host '%s'", routerAction.toString(), router.getRemoteIpAddress()),
                    Style.CONFIRM);
        }

        @Override
        public void onRouterActionFailure(@NonNull RouterAction routerAction, @NonNull Router router, @Nullable Exception exception) {
            Utils.displayMessage(mParentFragmentActivity,
                    String.format("Error on action '%s': %s", routerAction.toString(), ExceptionUtils.getRootCauseMessage(exception)),
                    Style.ALERT);
        }

        @Override
        public void onUndo(@android.support.annotation.Nullable Parcelable parcelable) {
            //Nothing to do
        }
    }

}

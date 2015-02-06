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
package org.rm3l.ddwrt.actions;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Joiner;

import org.rm3l.ddwrt.resources.Device;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.utils.SSHUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.rm3l.ddwrt.actions.RouterAction.WAKE_ON_LAN;

public class WakeOnLANRouterAction extends AbstractRouterAction<Void> {

    @NonNull
    private final List<String> mBroadcastAddressCandidates;

    @NonNull
    private final Device mDevice;

    private final int port;

    public WakeOnLANRouterAction(@Nullable RouterActionListener listener,
                                 @NonNull SharedPreferences globalSharedPreferences,
                                 @NonNull Device device, @Nullable String... broadcastAddressCandidates) {
        this(listener, globalSharedPreferences, device, -1, broadcastAddressCandidates);
    }

    public WakeOnLANRouterAction(@Nullable RouterActionListener listener,
                                 @NonNull SharedPreferences globalSharedPreferences,
                                 @NonNull Device device, int port, @Nullable String... broadcastAddressCandidates) {
        super(listener, WAKE_ON_LAN, globalSharedPreferences);
        if (broadcastAddressCandidates != null) {
            this.mBroadcastAddressCandidates = Arrays.asList(broadcastAddressCandidates);
        } else {
            this.mBroadcastAddressCandidates = new ArrayList<>();
        }
        this.mDevice = device;
        this.port = port;
    }

    @NonNull
    @Override
    protected RouterActionResult doActionInBackground(@NonNull Router router) {
        Exception exception = null;
        try {
            if (mBroadcastAddressCandidates.isEmpty()) {
                throw new IllegalArgumentException("No Broadcast Address for WOL Feature");
            }

            ///usr/sbin/wol -i 192.168.1.255 -p PP AA:BB:CC:DD:EE:FF
            final String[] wolCmd = new String[mBroadcastAddressCandidates.size()];
            int i = 0;
            for (final String mBroadcastAddressCandidate : mBroadcastAddressCandidates) {
                wolCmd[i++] = String.format("/usr/sbin/wol -i %s %s %s",
                        mBroadcastAddressCandidate,
                        port > 0 ? String.format("-p %d", port) : "",
                        mDevice.getMacAddress());
            }
            final int exitStatus = SSHUtils
                    .runCommands(globalSharedPreferences, router, Joiner.on(" ; ").skipNulls(), wolCmd);
            if (exitStatus != 0) {
                throw new IllegalStateException();
            }

        } catch (Exception e) {
            e.printStackTrace();
            exception = e;
        }

        return new RouterActionResult(null, exception);
    }
}

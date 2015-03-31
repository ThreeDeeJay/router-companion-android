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
package org.rm3l.ddwrt.resources.conn.openwrt;

import org.rm3l.ddwrt.resources.conn.NVRAMInfo;

public class UCIInfo extends NVRAMInfo {

    public static final String NETWORK_WAN_IPADDR = "network.wan.ipaddr";

    public static final String NETWORK_WAN_NETMASK = "network.wan.netmask";
    public static final String NETWORK_WAN_GATEWAY = "network.wan.gateway";
    public static final String NETWORK_WAN_DNS = "network.wan.dns";
    public static final String NETWORK_WAN_CONNECT_TIME = "network.wan.connect_time";
    public static final String NETWORK_WAN_DEVICE = "network.wan.device";
}

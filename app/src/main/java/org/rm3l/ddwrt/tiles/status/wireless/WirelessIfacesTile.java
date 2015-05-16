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

import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;

import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.fragments.status.StatusWirelessFragment;
import org.rm3l.ddwrt.resources.conn.NVRAMInfo;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.tiles.status.bandwidth.IfacesTile;
import org.rm3l.ddwrt.utils.ColorUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WirelessIfacesTile extends IfacesTile {

    private static final String TAG = WirelessIfacesTile.class.getSimpleName();

    @NonNull
    private List<WirelessIfaceTile> mWirelessIfaceTiles = new CopyOnWriteArrayList<>();

    public WirelessIfacesTile(@NonNull Fragment parentFragment, @NonNull Bundle arguments, Router router) {
        super(parentFragment, arguments, router);
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected Loader<NVRAMInfo> getLoader(final int id, final Bundle args) {

        return new AsyncTaskLoader<NVRAMInfo>(this.mParentFragmentActivity) {

            @Nullable
            @Override
            public NVRAMInfo loadInBackground() {

                final NVRAMInfo nvramInfo = WirelessIfacesTile.super.doLoadInBackground();

                //Also set details
                mWirelessIfaceTiles.clear();

                final Collection<WirelessIfaceTile> wirelessIfaceTiles = StatusWirelessFragment.getWirelessIfaceTiles(args,
                        mParentFragmentActivity, mParentFragment, mRouter);
                if (wirelessIfaceTiles != null) {
                    mWirelessIfaceTiles = new CopyOnWriteArrayList<>(wirelessIfaceTiles);
                }

                for (final WirelessIfaceTile mWirelessIfaceTile : mWirelessIfaceTiles) {
                    if (mWirelessIfaceTile == null) {
                        continue;
                    }
                    final AsyncTaskLoader<NVRAMInfo> mWirelessIfaceTileLoader = (AsyncTaskLoader<NVRAMInfo>)
                            mWirelessIfaceTile.getLoader(id, args);
                    if (mWirelessIfaceTileLoader == null) {
                        continue;
                    }
                    Log.d(TAG, "Building view for iface " + mWirelessIfaceTile.getIface());
                    mWirelessIfaceTile.buildView(
                            mWirelessIfaceTileLoader.loadInBackground());
                }

                return nvramInfo;
            }
        };
    }

    @Override
    public void onLoadFinished(@NonNull Loader<NVRAMInfo> loader, @Nullable NVRAMInfo data) {
        //Hide Non-wireless lines
        final int[] viewsToHide = new int[]{
                R.id.tile_status_bandwidth_ifaces_lan_title,
                R.id.tile_status_bandwidth_ifaces_lan_separator,
                R.id.tile_status_bandwidth_ifaces_lan,
                R.id.tile_status_bandwidth_ifaces_wan,
                R.id.tile_status_bandwidth_ifaces_wan_separator,
                R.id.tile_status_bandwidth_ifaces_wan_title
        };
        for (final int viewToHide : viewsToHide) {
            this.layout.findViewById(viewToHide).setVisibility(View.GONE);
        }

        //Now add each wireless iface tile
        final GridLayout container = (GridLayout) this.layout
                .findViewById(R.id.tile_status_bandwidth_ifaces_list_container);
        container.removeAllViews();

        final Resources resources = mParentFragmentActivity.getResources();
        container.setBackgroundColor(resources.getColor(android.R.color.transparent));

        final boolean isThemeLight = ColorUtils.isThemeLight(mParentFragmentActivity);

        for (final WirelessIfaceTile tile : mWirelessIfaceTiles) {
            if (tile == null) {
                continue;
            }
            final ViewGroup tileViewGroupLayout = tile.getViewGroupLayout();
            if (tileViewGroupLayout instanceof CardView) {

                final CardView cardView = (CardView) tileViewGroupLayout;


                //Create Options Menu
                final ImageButton tileMenu = (ImageButton) cardView.findViewById(R.id.tile_status_wireless_iface_menu);

                if (!isThemeLight) {
                    //Set menu background to white
                    tileMenu.setImageResource(R.drawable.abs__ic_menu_moreoverflow_normal_holo_dark);
                }

                //Add padding to CardView on v20 and before to prevent intersections between the Card content and rounded corners.
                cardView.setPreventCornerOverlap(true);
                //Add padding in API v21+ as well to have the same measurements with previous versions.
                cardView.setUseCompatPadding(true);

                //Highlight CardView
                cardView.setCardElevation(10f);

                if (isThemeLight) {
                    //Light
                    cardView.setCardBackgroundColor(resources.getColor(R.color.cardview_light_background));
                } else {
                    //Default is Dark
                    cardView.setCardBackgroundColor(resources.getColor(R.color.cardview_dark_background));
                }
            }
            if (tileViewGroupLayout != null) {
                container.addView(tileViewGroupLayout);
            }
        }

        container.setVisibility(View.VISIBLE);

        super.onLoadFinished(loader, data);
    }

    @Nullable
    @Override
    protected OnClickIntent getOnclickIntent() {
        return super.getOnclickIntent();
    }
}

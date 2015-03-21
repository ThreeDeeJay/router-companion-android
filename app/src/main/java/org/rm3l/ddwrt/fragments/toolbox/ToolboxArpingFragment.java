package org.rm3l.ddwrt.fragments.toolbox;

import android.os.Bundle;
import android.support.annotation.Nullable;

import org.rm3l.ddwrt.fragments.BaseFragment;
import org.rm3l.ddwrt.tiles.DDWRTTile;
import org.rm3l.ddwrt.tiles.toolbox.ToolboxArpingTile;

import java.util.Arrays;
import java.util.List;

public class ToolboxArpingFragment extends BaseFragment {
    @Nullable
    @Override
    protected List<DDWRTTile> getTiles(@Nullable Bundle savedInstanceState) {
        return Arrays.<DDWRTTile>asList(new ToolboxArpingTile(this, savedInstanceState, this.router));
    }
}

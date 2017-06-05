package org.rm3l.router_companion.actions.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import org.rm3l.ddwrt.R;
import org.rm3l.router_companion.mgmt.RouterManagementActivity;
import org.rm3l.router_companion.mgmt.dao.DDWRTCompanionDAO;
import org.rm3l.router_companion.resources.SpeedTestResult;
import org.rm3l.router_companion.resources.conn.Router;
import org.rm3l.router_companion.utils.ColorUtils;
import org.rm3l.router_companion.utils.Utils;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.rm3l.router_companion.actions.activity.SpeedTestActivity.PER_SEC;
import static org.rm3l.router_companion.actions.activity.SpeedTestActivity.getServerLocationDisplayFromCountryCode;
import static org.rm3l.router_companion.actions.activity.SpeedTestActivity.refreshServerLocationFlag;

/**
 * Created by rm3l on 18/01/16.
 */
public class SpeedTestResultRecyclerViewAdapter
    extends RecyclerView.Adapter<SpeedTestResultRecyclerViewAdapter.ViewHolder> {

  private final SpeedTestActivity activity;
  private final Router mRouter;
  private final DDWRTCompanionDAO mDao;
  private List<SpeedTestResult> speedTestResults;

  public SpeedTestResultRecyclerViewAdapter(final SpeedTestActivity activity,
      final Router mRouter) {
    this.activity = activity;
    this.mRouter = mRouter;
    this.mDao = RouterManagementActivity.getDao(activity);
  }

  public List<SpeedTestResult> getSpeedTestResults() {
    return speedTestResults;
  }

  public SpeedTestResultRecyclerViewAdapter setSpeedTestResults(
      List<SpeedTestResult> speedTestResults) {
    this.speedTestResults = speedTestResults;
    return this;
  }

  @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    // create a new view
    View v = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.speed_test_result_list_layout, parent, false);
    // set the view's size, margins, paddings and layout parameters
    // ...
    final CardView cardView = (CardView) v.findViewById(R.id.speed_test_result_item_cardview);
    final ImageButton deleteImageButton =
        (ImageButton) cardView.findViewById(R.id.speedtest_result_delete);
    if (ColorUtils.Companion.isThemeLight(activity)) {
      //Light
      cardView.setCardBackgroundColor(
          ContextCompat.getColor(activity, R.color.cardview_light_background));

      deleteImageButton.setImageDrawable(
          ContextCompat.getDrawable(activity, R.drawable.ic_delete_black_24dp));
    } else {
      //Default is Dark
      cardView.setCardBackgroundColor(
          ContextCompat.getColor(activity, R.color.cardview_dark_background));
      deleteImageButton.setImageDrawable(
          ContextCompat.getDrawable(activity, R.drawable.ic_delete_white_24dp));
    }

    //        return new ViewHolder(this.context,
    //                RippleViewCreator.addRippleToView(v));
    return new ViewHolder(v);
  }

  @Override public void onBindViewHolder(ViewHolder holder, final int position) {
    if (position < 0 || position >= speedTestResults.size()) {
      Utils.reportException(null, new IllegalStateException());
      Toast.makeText(activity, "Internal Error. Please try again later", Toast.LENGTH_SHORT).show();
      return;
    }
    final SpeedTestResult speedTestResult = speedTestResults.get(position);
    if (speedTestResult == null) {
      return;
    }

    final boolean isThemeLight = ColorUtils.Companion.isThemeLight(activity);

    final View containerView = holder.containerView;

    containerView.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        final View placeholderView =
            containerView.findViewById(R.id.speed_test_result_details_placeholder);
        if (placeholderView.getVisibility() == View.VISIBLE) {
          placeholderView.setVisibility(View.GONE);
        } else {
          placeholderView.setVisibility(View.VISIBLE);
        }
      }
    });

    final TextView testDateView =
        (TextView) containerView.findViewById(R.id.speed_test_result_test_date);
    String speedTestResultDate = speedTestResult.getDate();
    if (!isNullOrEmpty(speedTestResultDate)) {
      speedTestResultDate = speedTestResultDate.replaceAll(" ", "\n");
    }
    testDateView.setText("\n" + speedTestResultDate);

    ((TextView) containerView.findViewById(R.id.speed_test_result_detail_test_date)).setText(
        speedTestResult.getDate());

    final String serverCountryCode = speedTestResult.getServerCountryCode();
    final ImageView imageView =
        (ImageView) containerView.findViewById(R.id.speed_test_result_server_country_flag);
    if (serverCountryCode == null || serverCountryCode.isEmpty()) {
      imageView.setVisibility(View.GONE);
    } else {
      refreshServerLocationFlag(activity, serverCountryCode, imageView);
    }

    final TextView wanPingView =
        (TextView) containerView.findViewById(R.id.speed_test_result_wanPing);
    wanPingView.setCompoundDrawablesWithIntrinsicBounds(0,
        isThemeLight ? R.drawable.ic_settings_ethernet_black_24dp
            : R.drawable.ic_settings_ethernet_white_24dp, 0, 0);
    final Number ping = speedTestResult.getWanPing();
    wanPingView.setText(String.format("%.2f\nms", ping.floatValue()));

    final TextView wanDlView = (TextView) containerView.findViewById(R.id.speed_test_result_wanDl);
    wanDlView.setCompoundDrawablesWithIntrinsicBounds(0,
        isThemeLight ? R.drawable.ic_file_download_black_24dp
            : R.drawable.ic_file_download_white_24dp, 0, 0);
    final String wanDlByteCountDisplaySize =
        (org.rm3l.router_companion.utils.FileUtils.byteCountToDisplaySize(
            speedTestResult.getWanDl().longValue()) + PER_SEC);
    final String wanDl = wanDlByteCountDisplaySize.replaceAll(" ", "\n");
    wanDlView.setText(wanDl);

    final TextView wanUlView = (TextView) containerView.findViewById(R.id.speed_test_result_wanUl);
    wanUlView.setCompoundDrawablesWithIntrinsicBounds(0,
        isThemeLight ? R.drawable.ic_file_upload_black_24dp : R.drawable.ic_file_upload_white_24dp,
        0, 0);
    final String wanUlByteCountToDisplaySize =
        (org.rm3l.router_companion.utils.FileUtils.byteCountToDisplaySize(
            speedTestResult.getWanUl().longValue()) + PER_SEC);
    final String wanUl = wanUlByteCountToDisplaySize.replaceAll(" ", "\n");
    wanUlView.setText(wanUl);

    ((TextView) containerView.findViewById(R.id.speed_test_result_details_server)).setText(
        speedTestResult.getServer());

    final TextView serverLocationView =
        (TextView) containerView.findViewById(R.id.speed_test_result_details_server_location);
    if (isNullOrEmpty(serverCountryCode)) {
      serverLocationView.setText("-");
    } else {
      serverLocationView.setText(getServerLocationDisplayFromCountryCode(serverCountryCode));
    }

    ((TextView) containerView.findViewById(R.id.speed_test_result_details_wanPing)).setText(
        String.format("%.2f ms", ping.floatValue()));
    ((TextView) containerView.findViewById(R.id.speed_test_result_details_wanDownload)).setText(
        wanDlByteCountDisplaySize);
    ((TextView) containerView.findViewById(R.id.speed_test_result_details_wanUpload)).setText(
        wanUlByteCountToDisplaySize);

    final PingRTT wanPingRTT = speedTestResult.getWanPingRTT();
    if (wanPingRTT != null) {
      //Set WAN Ping Max, Min, and Stddev, and Packet Loss
      ((TextView) containerView.findViewById(R.id.speed_test_result_details_wanPing_min)).setText(
          String.format("%.2f ms", wanPingRTT.getMin()));
      ((TextView) containerView.findViewById(R.id.speed_test_result_details_wanPing_max)).setText(
          String.format("%.2f ms", wanPingRTT.getMax()));
      ((TextView) containerView.findViewById(
          R.id.speed_test_result_details_wanPing_packet_loss)).setText(
          String.format("%d%%", Float.valueOf(wanPingRTT.getPacketLoss()).intValue()));
    }

    if (speedTestResult.getWanDLFileSize() != null) {
      ((TextView) containerView.findViewById(
          R.id.speed_test_result_details_wanDownload_size)).setText(
          String.format("%s MB", speedTestResult.getWanDLFileSize().longValue()));
    }
    if (speedTestResult.getWanDLDuration() != null) {
      final long longValue = speedTestResult.getWanDLDuration().longValue();
      ((TextView) containerView.findViewById(
          R.id.speed_test_result_details_wanDownload_duration)).setText(
          longValue + " second" + (longValue > 1 ? "s" : ""));
    }

    containerView.findViewById(R.id.speedtest_result_delete)
        .setOnClickListener(new View.OnClickListener() {
          @Override public void onClick(View v) {
            new AlertDialog.Builder(activity).setIcon(R.drawable.ic_action_alert_warning)
                .setTitle("Delete Speed Test Result?")
                .setMessage("You'll lose this record!")
                .setCancelable(true)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(final DialogInterface dialogInterface, final int i) {
                    final String mRouterUuid = mRouter.getUuid();

                    mDao.deleteSpeedTestResultByRouterById(mRouterUuid, speedTestResult.getId());

                    final List<SpeedTestResult> speedTestResultsByRouter =
                        mDao.getSpeedTestResultsByRouter(mRouterUuid);
                    activity.setSpeedTestResults(speedTestResultsByRouter);
                    activity.notifyDataSetChanged();

                    activity.updateNbSpeedTestResults(speedTestResultsByRouter);

                    //Request Backup
                    Utils.requestBackup(activity);
                  }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                  @Override public void onClick(DialogInterface dialogInterface, int i) {
                    //Cancelled - nothing more to do!
                  }
                })
                .create()
                .show();
          }
        });
  }

  @Override public int getItemCount() {
    return speedTestResults != null ? speedTestResults.size() : 0;
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    final View containerView;
    private final View itemView;

    public ViewHolder(View itemView) {
      super(itemView);
      this.itemView = itemView;
      containerView = itemView.findViewById(R.id.speed_test_result_container);
    }
  }
}

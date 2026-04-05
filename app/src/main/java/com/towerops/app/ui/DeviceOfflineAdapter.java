package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.towerops.app.R;
import com.towerops.app.api.DeviceOfflineApi;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备离线列表适配器 - 表格行样式
 */
public class DeviceOfflineAdapter extends RecyclerView.Adapter<DeviceOfflineAdapter.VH> {

    private List<DeviceOfflineApi.DeviceOfflineItem> items = new ArrayList<>();

    public void setData(List<DeviceOfflineApi.DeviceOfflineItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void clear() {
        this.items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device_offline, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DeviceOfflineApi.DeviceOfflineItem item = items.get(position);
        holder.bind(item);
        // 隔行变色
        if (position % 2 == 1) {
            holder.itemView.setBackgroundColor(holder.itemView.getContext().getColor(R.color.bg_base));
        } else {
            holder.itemView.setBackgroundColor(holder.itemView.getContext().getColor(R.color.bg_card));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final TextView tvSiteName, tvCounty, tvOfflineDevice, tvIsFullOffline, tvStatTime;

        VH(@NonNull View itemView) {
            super(itemView);
            tvSiteName = itemView.findViewById(R.id.tvSiteName);
            tvCounty = itemView.findViewById(R.id.tvCounty);
            tvOfflineDevice = itemView.findViewById(R.id.tvOfflineDevice);
            tvIsFullOffline = itemView.findViewById(R.id.tvIsFullOffline);
            tvStatTime = itemView.findViewById(R.id.tvStatTime);
        }

        void bind(DeviceOfflineApi.DeviceOfflineItem item) {
            tvSiteName.setText(item.siteName);
            tvCounty.setText(item.county != null ? item.county : "");
            tvOfflineDevice.setText(item.offlineDevice != null ? item.offlineDevice : "0");

            // 是否全设备离线
            if ("是".equals(item.isFullOffline)) {
                tvIsFullOffline.setText("⚠ 全离线");
                tvIsFullOffline.setTextColor(itemView.getContext().getColor(R.color.error));
            } else {
                tvIsFullOffline.setText("部分");
                tvIsFullOffline.setTextColor(itemView.getContext().getColor(R.color.warning));
            }

            tvStatTime.setText(item.statTime != null ? item.statTime : "");
        }
    }
}

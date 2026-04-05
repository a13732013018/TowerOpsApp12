package com.towerops.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.model.AccessControlItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 门禁监控列表 Adapter
 * 列：序号 | 站名 | 告警时间 | 蓝牙出站 | 蓝牙间隔 | 状态 | 远程时间 | 远程间隔
 */
public class AccessControlAdapter extends RecyclerView.Adapter<AccessControlAdapter.VH> {

    private final List<AccessControlItem> items = new ArrayList<>();

    /** 长按条目回调 */
    public interface OnItemLongClickListener {
        void onItemLongClick(int position, AccessControlItem item);
    }

    private OnItemLongClickListener longClickListener;

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        this.longClickListener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_access_control, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AccessControlItem item = items.get(position);
        Context ctx = h.itemView.getContext();

        h.tvIndex.setText(String.valueOf(item.getIndex()));
        h.tvStName.setText(item.getStName());
        // 告警时间：显示完整 yyyy-MM-dd HH:mm:ss
        h.tvAlarmTime.setText(item.getAlarmTime());
        // 蓝牙出站时间：显示完整 yyyy-MM-dd HH:mm:ss
        h.tvBtTime.setText(item.getBluetoothOutTime());
        // 蓝牙间隔
        String btInt = item.getBluetoothInterval();
        h.tvBtInterval.setText("-9999".equals(btInt) ? "-" : btInt + "分");
        // 状态
        String status = item.getStatus();
        h.tvStatus.setText(status);
        // 状态颜色
        switch (status) {
            case "合格":
            case "已开门":
                h.tvStatus.setTextColor(Color.parseColor("#10B981")); // 绿
                break;
            case "不合格":
                h.tvStatus.setTextColor(Color.parseColor("#EF4444")); // 红
                break;
            case "待开门":
                h.tvStatus.setTextColor(Color.parseColor("#F59E0B")); // 橙
                break;
            default:
                h.tvStatus.setTextColor(Color.parseColor("#6B7280")); // 灰
                break;
        }
        // 远程开门时间：显示完整 yyyy-MM-dd HH:mm:ss
        h.tvRemoteTime.setText(item.getRemoteOpenTime());
        // 远程间隔
        String ri = item.getRemoteInterval();
        h.tvRemoteInterval.setText("-9999".equals(ri) ? "-" : ri + "分");

        // 交替行背景
        h.itemView.setBackgroundColor(position % 2 == 0
                ? Color.parseColor("#FFFFFF")
                : Color.parseColor("#F9FAFB"));

        // 长按 → 手动触发远程开门确认框
        final int pos = position;
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(pos, items.get(pos));
            }
            return true; // 消费事件，不触发单击
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 设置全量数据 */
    public void setData(List<AccessControlItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    /** 清空列表 */
    public void clearData() {
        items.clear();
        notifyDataSetChanged();
    }

    /** 更新单行状态（线程安全：需在主线程调用） */
    public void updateItem(int index, AccessControlItem updated) {
        if (index >= 0 && index < items.size()) {
            items.set(index, updated);
            notifyItemChanged(index);
        }
    }

    /** 获取指定位置的数据项 */
    public AccessControlItem getItem(int index) {
        if (index >= 0 && index < items.size()) return items.get(index);
        return null;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIndex, tvStName, tvAlarmTime, tvBtTime, tvBtInterval;
        TextView tvStatus, tvRemoteTime, tvRemoteInterval;

        VH(@NonNull View v) {
            super(v);
            tvIndex          = v.findViewById(R.id.tvAcIndex);
            tvStName         = v.findViewById(R.id.tvAcStName);
            tvAlarmTime      = v.findViewById(R.id.tvAcAlarmTime);
            tvBtTime         = v.findViewById(R.id.tvAcBtTime);
            tvBtInterval     = v.findViewById(R.id.tvAcBtInterval);
            tvStatus         = v.findViewById(R.id.tvAcStatus);
            tvRemoteTime     = v.findViewById(R.id.tvAcRemoteTime);
            tvRemoteInterval = v.findViewById(R.id.tvAcRemoteInterval);
        }
    }
}

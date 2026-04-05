package com.towerops.app.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;

import java.util.List;

/**
 * 门禁数据列表 Adapter
 * 数据模型：DoorDataFragment.DoorAlarmRow（每站一条，含合格/不合格标记）
 */
public class DoorDataAdapter extends RecyclerView.Adapter<DoorDataAdapter.VH> {

    private final List<DoorDataFragment.DoorAlarmRow> data;

    public DoorDataAdapter(List<DoorDataFragment.DoorAlarmRow> data) {
        this.data = data;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_door_data, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DoorDataFragment.DoorAlarmRow row = data.get(position);

        h.tvIdx.setText(String.valueOf(row.index));
        h.tvStName.setText(row.stName != null ? row.stName : "");
        h.tvStCode.setText(row.stCode != null ? row.stCode : "");
        h.tvAlarmName.setText(row.alarmName != null ? row.alarmName : "");
        // 告警时间显示完整格式 yyyy-MM-dd HH:mm:ss
        h.tvAlarmTime.setText(row.alarmTime != null && !row.alarmTime.isEmpty() ? row.alarmTime : "");
        h.tvBtTime.setText(row.btOpenTime != null && !row.btOpenTime.isEmpty() ? row.btOpenTime : "无");
        h.tvDiff.setText(row.diffMinutes >= 0 ? row.diffMinutes + "′" : "-");

        // 合格/不合格颜色标记
        if (row.qualified) {
            h.tvQualified.setText("✓ 合格");
            h.tvQualified.setTextColor(Color.parseColor("#16A34A"));  // 绿
            h.itemView.setBackgroundColor(Color.parseColor("#F0FFF4"));
        } else {
            h.tvQualified.setText("✗ 不合格");
            h.tvQualified.setTextColor(Color.parseColor("#DC2626")); // 红
            h.itemView.setBackgroundColor(Color.parseColor("#FFF5F5"));
        }
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIdx, tvStName, tvStCode, tvAlarmName, tvAlarmTime, tvBtTime, tvDiff, tvQualified;

        VH(@NonNull View v) {
            super(v);
            tvIdx       = v.findViewById(R.id.tvDoorIdx);
            tvStName    = v.findViewById(R.id.tvDoorStName);
            tvStCode    = v.findViewById(R.id.tvDoorStCode);
            tvAlarmName = v.findViewById(R.id.tvDoorAlarmName);
            tvAlarmTime = v.findViewById(R.id.tvDoorAlarmTime);
            tvBtTime    = v.findViewById(R.id.tvDoorBtTime);
            tvDiff      = v.findViewById(R.id.tvDoorDiff);
            tvQualified = v.findViewById(R.id.tvDoorQualified);
        }
    }
}

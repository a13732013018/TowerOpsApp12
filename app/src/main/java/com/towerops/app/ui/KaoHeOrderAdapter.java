package com.towerops.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 考核工单列表适配器
 */
public class KaoHeOrderAdapter extends RecyclerView.Adapter<KaoHeOrderAdapter.VH> {

    private List<ShuyunApi.KaoHeOrderInfo> data = new ArrayList<>();

    public void setData(List<ShuyunApi.KaoHeOrderInfo> list) {
        this.data = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public ShuyunApi.KaoHeOrderInfo getItem(int position) {
        if (position >= 0 && position < data.size()) return data.get(position);
        return null;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_kaohe_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ShuyunApi.KaoHeOrderInfo item = data.get(position);

        h.tvIndex.setText(item.index);
        h.tvCityName.setText(item.orderCityName);
        h.tvOrderNum.setText(item.orderNum);
        h.tvJobName.setText(item.jobName);
        h.tvStationName.setText(item.stationName);
        h.tvGroupName.setText(item.groupName);
        h.tvGroupName.setVisibility(item.groupName != null && !item.groupName.isEmpty()
                ? View.VISIBLE : View.GONE);
        h.tvFlowName.setText(item.flowName);
        h.tvDataName.setText(item.dataName);
        h.tvAccestaff.setText(item.accestaff);

        // ── 截止时间 & 高亮判断 ───────────────────────────────────────
        long millisLeft = parseDeadlineMillisLeft(item.reqCompTime);
        final long THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000;
        boolean urgent = millisLeft >= 0 && millisLeft < THREE_DAYS_MS;
        boolean overdue = millisLeft < 0;   // 已超期

        // 创建→要求完成时间文本
        String timeStr = formatShort(item.createTime) + " → " + formatShort(item.reqCompTime);
        if (overdue) {
            // 已超期：追加提示
            long overMs = -millisLeft;
            timeStr += "  ⚠ 已超期" + formatDuration(overMs);
        } else if (urgent) {
            // 即将截止：追加剩余时间
            timeStr += "  ⚠ 还剩" + formatDuration(millisLeft) + "截止";
        }
        h.tvCreateTime.setText(timeStr);

        // 背景 & 文字样式
        if (overdue) {
            // 超期：深红背景
            h.itemView.setBackgroundColor(0xFFFFE5E5);
            h.tvCreateTime.setTextColor(Color.parseColor("#CC0000"));
            h.tvCreateTime.setTypeface(null, Typeface.BOLD);
            h.tvStationName.setTextColor(Color.parseColor("#CC0000"));
        } else if (urgent) {
            // 紧急：橙黄背景
            h.itemView.setBackgroundColor(0xFFFFF3CD);
            h.tvCreateTime.setTextColor(Color.parseColor("#E65C00"));
            h.tvCreateTime.setTypeface(null, Typeface.BOLD);
            h.tvStationName.setTextColor(Color.parseColor("#E65C00"));
        } else {
            // 正常：交替背景
            h.itemView.setBackgroundColor(position % 2 == 0 ? 0xFFFFFFFF : 0xFFF0F4FF);
            h.tvCreateTime.setTextColor(Color.parseColor("#888888"));
            h.tvCreateTime.setTypeface(null, Typeface.NORMAL);
            h.tvStationName.setTextColor(Color.parseColor("#1a1a2e"));
        }
    }

    /**
     * 解析要求完成时间，返回距当前的毫秒数（正=未到期，负=已超期，Long.MAX_VALUE=解析失败）
     */
    private long parseDeadlineMillisLeft(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return Long.MAX_VALUE;
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"};
        for (String pattern : patterns) {
            try {
                Date d = new SimpleDateFormat(pattern, Locale.getDefault()).parse(timeStr);
                if (d != null) return d.getTime() - System.currentTimeMillis();
            } catch (ParseException ignored) {}
        }
        return Long.MAX_VALUE;
    }

    /**
     * 将毫秒格式化为 "X天X小时" 或 "X小时X分" 的可读文本
     */
    private String formatDuration(long ms) {
        long totalMinutes = ms / 60000;
        long days    = totalMinutes / (24 * 60);
        long hours   = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        if (days > 0) return days + "天" + hours + "小时";
        if (hours > 0) return hours + "小时" + minutes + "分";
        return minutes + "分";
    }

    /** 简化时间格式：取 yyyy-MM-dd HH:mm 部分 */
    private String formatShort(String t) {
        if (t == null || t.isEmpty()) return "";
        if (t.length() > 16) return t.substring(0, 16);
        return t;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIndex, tvCityName, tvOrderNum, tvJobName;
        TextView tvStationName, tvGroupName;
        TextView tvFlowName, tvDataName;
        TextView tvCreateTime, tvAccestaff;

        VH(@NonNull View v) {
            super(v);
            tvIndex       = v.findViewById(R.id.tvKHIndex);
            tvCityName    = v.findViewById(R.id.tvKHCityName);
            tvOrderNum    = v.findViewById(R.id.tvKHOrderNum);
            tvJobName     = v.findViewById(R.id.tvKHJobName);
            tvStationName = v.findViewById(R.id.tvKHStationName);
            tvGroupName   = v.findViewById(R.id.tvKHGroupName);
            tvFlowName    = v.findViewById(R.id.tvKHFlowName);
            tvDataName    = v.findViewById(R.id.tvKHDataName);
            tvCreateTime  = v.findViewById(R.id.tvKHCreateTime);
            tvAccestaff   = v.findViewById(R.id.tvKHAccestaff);
        }
    }
}

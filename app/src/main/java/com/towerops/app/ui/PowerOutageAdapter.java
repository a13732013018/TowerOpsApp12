package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.model.PowerOutage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 停电监控列表适配器
 * 支持按站点名称、电压、负载电流、告警时间排序
 */
public class PowerOutageAdapter extends RecyclerView.Adapter<PowerOutageAdapter.ViewHolder> {

    // 排序模式
    public enum SortMode {
        // 站点名称
        ST_NAME_ASC,
        ST_NAME_DESC,
        // 电压
        VOLTAGE_ASC,
        VOLTAGE_DESC,
        // 负载电流
        LOAD_CURRENT_ASC,
        LOAD_CURRENT_DESC,
        // 告警时间
        ALARM_TIME_ASC,
        ALARM_TIME_DESC
    }

    private List<PowerOutage> dataList = new ArrayList<>();
    private SortMode sortMode = SortMode.VOLTAGE_ASC; // 默认按电压从小到大排序

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_power_outage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PowerOutage item = dataList.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public void setData(List<PowerOutage> list) {
        this.dataList = list != null ? new ArrayList<>(list) : new ArrayList<>();
        // 每次新数据到来，重置为默认排序（电压升序）
        this.sortMode = SortMode.VOLTAGE_ASC;
        applySort();
        notifyDataSetChanged();
        android.util.Log.d("PowerOutageAdapter", "setData called, size=" + dataList.size() + ", sortMode=VOLTAGE_ASC");
    }

    /**
     * 更新单条状态（工单监控使用，停电监控保留兼容性）
     * @param rowIndex 行索引提示
     * @param billsn 工单号（停电监控中此参数可为null）
     * @param content 状态内容
     */
    public void updateStatus(int rowIndex, String billsn, String content) {
        // 停电监控不需要此功能，但为了兼容接口保留空实现
        android.util.Log.d("PowerOutageAdapter", "updateStatus called, not supported in PowerOutage");
    }

    /**
     * 按站点编码更新某行的状态列（排序后行位置可能变化，始终全表搜索）
     */
    public void updateByStCode(String stCode, String content) {
        for (int i = 0; i < dataList.size(); i++) {
            if (stCode != null && stCode.equals(dataList.get(i).getStCode())) {
                // 可以根据需要更新对应的字段，这里先只刷新显示
                notifyItemChanged(i);
                return;
            }
        }
    }

    public List<PowerOutage> getData() {
        return dataList;
    }

    /** 清空列表（停止监控时调用） */
    public void clearData() {
        this.dataList = new ArrayList<>();
        this.sortMode = SortMode.VOLTAGE_ASC;
        notifyDataSetChanged();
    }



    public SortMode getSortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode mode) {
        this.sortMode = mode;
        applySort();
        notifyDataSetChanged();
    }

    // 切换排序模式
    public void toggleSortMode(SortMode targetAsc, SortMode targetDesc) {
        if (sortMode == targetDesc) {
            sortMode = targetAsc;
        } else {
            sortMode = targetDesc;
        }
        applySort();
        notifyDataSetChanged();
    }

    // 应用排序
    private void applySort() {
        switch (sortMode) {
            case ST_NAME_ASC:
                Collections.sort(dataList, (a, b) -> 
                    (a.getStName() != null ? a.getStName() : "").compareTo(b.getStName() != null ? b.getStName() : ""));
                break;
            case ST_NAME_DESC:
                Collections.sort(dataList, (a, b) -> 
                    (b.getStName() != null ? b.getStName() : "").compareTo(a.getStName() != null ? a.getStName() : ""));
                break;
            case VOLTAGE_ASC:
                Collections.sort(dataList, (a, b) -> Double.compare(a.getVoltageValue(), b.getVoltageValue()));
                break;
            case VOLTAGE_DESC:
                Collections.sort(dataList, (a, b) -> Double.compare(b.getVoltageValue(), a.getVoltageValue()));
                break;
            case LOAD_CURRENT_ASC:
                Collections.sort(dataList, (a, b) -> Double.compare(a.getLoadCurrentValue(), b.getLoadCurrentValue()));
                break;
            case LOAD_CURRENT_DESC:
                Collections.sort(dataList, (a, b) -> Double.compare(b.getLoadCurrentValue(), a.getLoadCurrentValue()));
                break;
            case ALARM_TIME_ASC:
                Collections.sort(dataList, (a, b) -> 
                    (a.getAlarmTime() != null ? a.getAlarmTime() : "").compareTo(b.getAlarmTime() != null ? b.getAlarmTime() : ""));
                break;
            case ALARM_TIME_DESC:
            default:
                Collections.sort(dataList, (a, b) -> 
                    (b.getAlarmTime() != null ? b.getAlarmTime() : "").compareTo(a.getAlarmTime() != null ? a.getAlarmTime() : ""));
                break;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIndex;
        private final TextView tvGroup;
        private final TextView tvStCode;
        private final TextView tvStName;
        private final TextView tvAlarmTime;
        private final TextView tvDcVoltage;
        private final TextView tvDcLoadCurrent;
        private final TextView tvCause;

        ViewHolder(View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            tvGroup = itemView.findViewById(R.id.tvGroup);
            tvStCode = itemView.findViewById(R.id.tvStCode);
            tvStName = itemView.findViewById(R.id.tvStName);
            tvAlarmTime = itemView.findViewById(R.id.tvAlarmTime);
            tvDcVoltage = itemView.findViewById(R.id.tvDcVoltage);
            tvDcLoadCurrent = itemView.findViewById(R.id.tvDcLoadCurrent);
            tvCause = itemView.findViewById(R.id.tvCause);
        }

        void bind(PowerOutage item) {
            tvIndex.setText(String.valueOf(item.getIndex()));
            tvGroup.setText(item.getGroupName() != null ? item.getGroupName() : "");
            tvStCode.setText(item.getStCode() != null ? item.getStCode() : "");
            tvStName.setText(item.getStName() != null ? item.getStName() : "");
            tvAlarmTime.setText(item.getAlarmTime() != null ? item.getAlarmTime() : "");
            // 电压显示为2位小数
            tvDcVoltage.setText(formatVoltage(item.getDcVoltage()));
            // 负载电流显示为2位小数
            tvDcLoadCurrent.setText(formatLoadCurrent(item.getDcLoadCurrent()));
            // 告警原因统一显示为"停电"
            tvCause.setText("停电");
        }

        // 格式化电压为2位小数
        private String formatVoltage(String voltage) {
            if (voltage == null || voltage.isEmpty()) {
                return "0.00V";
            }
            try {
                double value = Double.parseDouble(voltage.replaceAll("[^0-9.]", ""));
                return String.format("%.2fV", value);
            } catch (NumberFormatException e) {
                return voltage.endsWith("V") ? voltage : voltage + "V";
            }
        }

        // 格式化负载电流为2位小数
        private String formatLoadCurrent(String loadCurrent) {
            if (loadCurrent == null || loadCurrent.isEmpty()) {
                return "0.00A";
            }
            try {
                double value = Double.parseDouble(loadCurrent.replaceAll("[^0-9.]", ""));
                return String.format("%.2fA", value);
            } catch (NumberFormatException e) {
                return loadCurrent.endsWith("A") ? loadCurrent : loadCurrent + "A";
            }
        }
    }
}

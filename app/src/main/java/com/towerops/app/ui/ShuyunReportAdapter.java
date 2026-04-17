package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunReportApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 综合上站报表适配器
 * 支持按列排序
 */
public class ShuyunReportAdapter extends RecyclerView.Adapter<ShuyunReportAdapter.ViewHolder> {

    private List<ShuyunReportApi.ReportItem> dataList = new ArrayList<>();
    private OnSortListener sortListener;

    public interface OnSortListener {
        void onSort(int column, boolean ascending);
    }

    public void setOnSortListener(OnSortListener listener) {
        this.sortListener = listener;
    }

    public void setData(List<ShuyunReportApi.ReportItem> data) {
        this.dataList = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void clear() {
        this.dataList.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shuyun_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShuyunReportApi.ReportItem item = dataList.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public void sortByColumn(int column, boolean ascending) {
        if (dataList.isEmpty()) return;

        switch (column) {
            case 1: // 地市
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.areaName.compareTo(b.areaName) : b.areaName.compareTo(a.areaName));
                break;
            case 2: // 区县
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.cityName.compareTo(b.cityName) : b.cityName.compareTo(a.cityName));
                break;
            case 3: // 起止日期
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.dateType.compareTo(b.dateType) : b.dateType.compareTo(a.dateType));
                break;
            case 4: // 中台派单
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.easyOrder - b.easyOrder : b.easyOrder - a.easyOrder);
                break;
            case 5: // 系统派单
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.sysOrder - b.sysOrder : b.sysOrder - a.sysOrder);
                break;
            case 6: // 综合上站APP
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.appOrder - b.appOrder : b.appOrder - a.appOrder);
                break;
            case 7: // 非综合上站
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.otherOrder - b.otherOrder : b.otherOrder - a.otherOrder);
                break;
            case 8: // 单日单站回单
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.dailyReply.compareTo(b.dailyReply) : b.dailyReply.compareTo(a.dailyReply));
                break;
            case 9: // 综合上站效能
                Collections.sort(dataList, (a, b) -> {
                    try {
                        double av = Double.parseDouble(a.efficiency);
                        double bv = Double.parseDouble(b.efficiency);
                        return ascending ? Double.compare(av, bv) : Double.compare(bv, av);
                    } catch (Exception e) {
                        return 0;
                    }
                });
                break;
            case 10: // 代维
                Collections.sort(dataList, (a, b) -> ascending ? 
                    a.dwShort.compareTo(b.dwShort) : b.dwShort.compareTo(a.dwShort));
                break;
            default:
                return;
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIndex, tvAreaName, tvCityName, tvPeriod;
        private final TextView tvEasyOrder, tvSysOrder, tvAppOrder, tvOtherOrder;
        private final TextView tvDailyReply, tvEfficiency, tvDw;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            tvAreaName = itemView.findViewById(R.id.tvAreaName);
            tvCityName = itemView.findViewById(R.id.tvCityName);
            tvPeriod = itemView.findViewById(R.id.tvPeriod);
            tvEasyOrder = itemView.findViewById(R.id.tvEasyOrder);
            tvSysOrder = itemView.findViewById(R.id.tvSysOrder);
            tvAppOrder = itemView.findViewById(R.id.tvAppOrder);
            tvOtherOrder = itemView.findViewById(R.id.tvOtherOrder);
            tvDailyReply = itemView.findViewById(R.id.tvDailyReply);
            tvEfficiency = itemView.findViewById(R.id.tvEfficiency);
            tvDw = itemView.findViewById(R.id.tvDw);
        }

        public void bind(ShuyunReportApi.ReportItem item, int position) {
            tvIndex.setText(String.valueOf(position + 1));
            tvAreaName.setText(item.areaName);
            tvCityName.setText(item.cityName);
            tvPeriod.setText(item.dateType);
            tvEasyOrder.setText(String.valueOf(item.easyOrder));
            tvSysOrder.setText(String.valueOf(item.sysOrder));
            tvAppOrder.setText(String.valueOf(item.appOrder));
            tvOtherOrder.setText(String.valueOf(item.otherOrder));
            tvDailyReply.setText(item.dailyReply);
            tvEfficiency.setText(item.efficiency);
            tvDw.setText(item.dwShort);

            // 平阳区域高亮显示
            if (item.cityName != null && item.cityName.contains("平阳")) {
                itemView.setBackgroundColor(0xFFFFF3CD); // 浅黄色背景
            } else {
                itemView.setBackgroundColor(0xFFFFFFFF); // 白色背景
            }
        }
    }
}

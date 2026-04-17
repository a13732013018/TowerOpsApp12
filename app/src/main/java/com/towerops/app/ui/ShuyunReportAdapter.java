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
import java.util.List;

/**
 * 综合上站报表适配器
 */
public class ShuyunReportAdapter extends RecyclerView.Adapter<ShuyunReportAdapter.ViewHolder> {

    private List<ShuyunReportApi.ReportItem> dataList = new ArrayList<>();

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

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIndex;
        private final TextView tvCityName;
        private final TextView tvDateType;
        private final TextView tvFaultNum;
        private final TextView tvAvgDowntime;
        private final TextView tvTimelyRate;
        private final TextView tvZhNum;
        private final TextView tvJyNum;
        private final TextView tvTotalNum;
        private final TextView tvDwShort;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            tvCityName = itemView.findViewById(R.id.tvCityName);
            tvDateType = itemView.findViewById(R.id.tvDateType);
            tvFaultNum = itemView.findViewById(R.id.tvFaultNum);
            tvAvgDowntime = itemView.findViewById(R.id.tvAvgDowntime);
            tvTimelyRate = itemView.findViewById(R.id.tvTimelyRate);
            tvZhNum = itemView.findViewById(R.id.tvZhNum);
            tvJyNum = itemView.findViewById(R.id.tvJyNum);
            tvTotalNum = itemView.findViewById(R.id.tvTotalNum);
            tvDwShort = itemView.findViewById(R.id.tvDwShort);
        }

        public void bind(ShuyunReportApi.ReportItem item, int position) {
            tvIndex.setText(String.valueOf(position + 1));
            tvCityName.setText(item.cityName);
            tvDateType.setText(item.dateType);
            tvFaultNum.setText(String.valueOf(item.n1Num3));
            tvAvgDowntime.setText(item.n3Num3 + "h");
            tvTimelyRate.setText(item.n4Num3);
            tvZhNum.setText(String.valueOf(item.weekZhNum));
            tvJyNum.setText(String.valueOf(item.weekJyNum));
            tvTotalNum.setText(String.valueOf(item.num3));
            tvDwShort.setText(item.dwShort);
        }
    }
}

package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.towerops.app.R;
import com.towerops.app.api.ProvinceOrderRateApi;
import java.util.ArrayList;
import java.util.List;

/**
 * 省内工单处理及时率列表适配器
 */
public class ProvinceOrderRateAdapter extends RecyclerView.Adapter<ProvinceOrderRateAdapter.VH> {

    private List<ProvinceOrderRateApi.ProvinceOrderRateItem> items = new ArrayList<>();

    public void setData(List<ProvinceOrderRateApi.ProvinceOrderRateItem> newItems) {
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
                .inflate(R.layout.item_province_order_rate, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ProvinceOrderRateApi.ProvinceOrderRateItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final TextView tvCityName, tvClRate, tvStatTime;
        private final TextView tvCreateSheet, tvNoStatusSheet, tvStatusSheet;
        private final TextView tvCsStatusSheet, tvCsNoStatusSheet;

        VH(@NonNull View itemView) {
            super(itemView);
            tvCityName = itemView.findViewById(R.id.tvCityName);
            tvClRate = itemView.findViewById(R.id.tvClRate);
            tvStatTime = itemView.findViewById(R.id.tvStatTime);
            tvCreateSheet = itemView.findViewById(R.id.tvCreateSheet);
            tvNoStatusSheet = itemView.findViewById(R.id.tvNoStatusSheet);
            tvStatusSheet = itemView.findViewById(R.id.tvStatusSheet);
            tvCsStatusSheet = itemView.findViewById(R.id.tvCsStatusSheet);
            tvCsNoStatusSheet = itemView.findViewById(R.id.tvCsNoStatusSheet);
        }

        void bind(ProvinceOrderRateApi.ProvinceOrderRateItem item) {
            tvCityName.setText(item.cityName);
            tvStatTime.setText("统计日期: " + item.statTime);
            tvCreateSheet.setText(item.createSheet);
            tvNoStatusSheet.setText(item.noStatusSheet);
            tvStatusSheet.setText(item.statusSheet);
            tvCsStatusSheet.setText(item.csStatusSheet);
            tvCsNoStatusSheet.setText(item.csNoStatusSheet);

            // 处理及时率 - 根据数值显示不同颜色
            String rateStr = item.clRate.replace("%", "");
            try {
                double rate = Double.parseDouble(rateStr);
                if (rate >= 90) {
                    tvClRate.setTextColor(itemView.getContext().getColor(R.color.success));
                } else if (rate >= 80) {
                    tvClRate.setTextColor(itemView.getContext().getColor(R.color.warning));
                } else {
                    tvClRate.setTextColor(itemView.getContext().getColor(R.color.error));
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            tvClRate.setText(item.clRate);
        }
    }
}

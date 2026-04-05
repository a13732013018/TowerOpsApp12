package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 省内待办工单列表适配器
 */
public class ProvinceInnerOrderAdapter extends RecyclerView.Adapter<ProvinceInnerOrderAdapter.ViewHolder> {

    private List<ShuyunApi.ProvinceInnerTaskInfo> items = new ArrayList<>();
    private Map<String, Integer> stationCountMap = new HashMap<>();
    private int highlightPosition = -1;
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;

    public interface OnItemClickListener {
        void onItemClick(ShuyunApi.ProvinceInnerTaskInfo item, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ShuyunApi.ProvinceInnerTaskInfo item, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.itemLongClickListener = listener;
    }

    public void setData(List<ShuyunApi.ProvinceInnerTaskInfo> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }
    
    public void setDataWithCount(List<ShuyunApi.ProvinceInnerTaskInfo> newItems, Map<String, Integer> countMap) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        stationCountMap.clear();
        if (countMap != null) {
            stationCountMap.putAll(countMap);
        }
        notifyDataSetChanged();
    }
    
    public void setHighlightPosition(int position) {
        highlightPosition = position;
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public ShuyunApi.ProvinceInnerTaskInfo getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    public List<ShuyunApi.ProvinceInnerTaskInfo> getData() {
        return new ArrayList<>(items);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_province_inner, parent, false);
        return new ViewHolder(view);
    }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ShuyunApi.ProvinceInnerTaskInfo item = items.get(position);
            holder.bind(item, stationCountMap, position == highlightPosition);
            
            // 点击事件
            holder.itemView.setOnClickListener(v -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(item, position);
                }
            });
            
            // 长按事件（双击效果用长按代替，或可以用连续两次点击检测）
            holder.itemView.setOnLongClickListener(v -> {
                if (itemLongClickListener != null) {
                    itemLongClickListener.onItemLongClick(item, position);
                    return true;
                }
                return false;
            });
        }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIndex;
        private final TextView tvHandler;
        private final TextView tvGroup;
        private final TextView tvCreateTime;
        private final TextView tvStationName;
        private final TextView tvOrderCount;
        private final TextView tvOrderDesc;
        private final TextView tvOrderType;
        private final TextView tvDataName;
        private final TextView tvFlowName;
        private final TextView tvReqTime;
        private final View itemView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            tvIndex       = itemView.findViewById(R.id.tvPIIndex);
            tvHandler     = itemView.findViewById(R.id.tvPIHandler);
            tvGroup       = itemView.findViewById(R.id.tvPIGroup);
            tvCreateTime  = itemView.findViewById(R.id.tvPICreateTime);
            tvStationName = itemView.findViewById(R.id.tvPIStationName);
            tvOrderCount  = itemView.findViewById(R.id.tvPIOrderCount);
            tvOrderDesc   = itemView.findViewById(R.id.tvPIOrderDesc);
            tvOrderType   = itemView.findViewById(R.id.tvPIOrderType);
            tvDataName    = itemView.findViewById(R.id.tvPIDataName);
            tvFlowName    = itemView.findViewById(R.id.tvPIFlowName);
            tvReqTime     = itemView.findViewById(R.id.tvPIReqTime);
        }

        public void bind(ShuyunApi.ProvinceInnerTaskInfo item, Map<String, Integer> countMap, boolean isHighlighted) {
            // 序号
            String idx = item.index != null && !item.index.isEmpty() ? item.index : "-";
            tvIndex.setText(idx);

            // 处理人
            tvHandler.setText(item.handler != null ? item.handler : "");

            // 分组
            if (item.groupName != null && !item.groupName.isEmpty()) {
                tvGroup.setText("【" + item.groupName + "】");
                tvGroup.setVisibility(View.VISIBLE);
            } else {
                tvGroup.setVisibility(View.GONE);
            }

            // 创建时间（只显示日期+时间，截断秒后部分）
            String ct = item.createTime != null ? item.createTime : "";
            if (ct.length() > 16) ct = ct.substring(0, 16);
            tvCreateTime.setText(ct);

            // 站点名称
            tvStationName.setText(item.station_name != null ? item.station_name : "");

            // 工单数量（始终显示，包括1张时）
            String stationName = item.station_name != null ? item.station_name : "";
            int count = countMap.getOrDefault(stationName, 1);
            tvOrderCount.setText("(" + count + "张)");
            tvOrderCount.setVisibility(View.VISIBLE);

            // 工单描述
            if (item.order_desc != null && !item.order_desc.isEmpty()) {
                tvOrderDesc.setText(item.order_desc);
                tvOrderDesc.setVisibility(View.VISIBLE);
            } else {
                tvOrderDesc.setVisibility(View.GONE);
            }

            // 工单类型标签
            tvOrderType.setText(resolveOrderTypeName(item.order_type));

            // 任务类型(data_name)
            if (item.data_name != null && !item.data_name.isEmpty()) {
                tvDataName.setText(item.data_name);
                tvDataName.setVisibility(View.VISIBLE);
            } else {
                tvDataName.setVisibility(View.GONE);
            }

            // 流程名
            if (item.flowName != null && !item.flowName.isEmpty()) {
                tvFlowName.setText("流程：" + item.flowName);
                tvFlowName.setVisibility(View.VISIBLE);
            } else {
                tvFlowName.setVisibility(View.GONE);
            }

            // 要求完成时间
            String reqTime = item.req_comp_time != null ? item.req_comp_time : "";
            if (reqTime.length() > 16) reqTime = reqTime.substring(0, 16);
            if (!reqTime.isEmpty()) {
                tvReqTime.setText("要求完成: " + reqTime);
                tvReqTime.setVisibility(View.VISIBLE);
            } else {
                tvReqTime.setVisibility(View.GONE);
            }

            // 高亮显示
            if (isHighlighted) {
                itemView.setBackgroundColor(0xFFFFF3CD); // 淡黄色背景
            } else {
                itemView.setBackgroundColor(0xFFFFFFFF); // 白色背景
            }
        }

        /** 将 order_type 代码转成可读名称 */
        private String resolveOrderTypeName(String code) {
            if (code == null) return "未知";
            switch (code) {
                case "1028": return "一般省内任务派单流程";
                case "1063": return "省内简易派单流程";
                case "1124":
                case "1220": return "智联业务综合派单流程";
                case "1118": return "退服隐患整治工单";
                default:     return code.isEmpty() ? "智联业务综合派单流程" : code;
            }
        }
    }
}

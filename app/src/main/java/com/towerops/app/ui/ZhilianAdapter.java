package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ZhilianApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 智联工单列表适配器
 */
public class ZhilianAdapter extends RecyclerView.Adapter<ZhilianAdapter.ViewHolder> {

    private List<ZhilianApi.ZhilianTaskInfo> dataList = new ArrayList<>();
    private OnItemActionListener actionListener;
    private boolean showAcceptButton = false;
    private boolean showRevertButton = false;

    public interface OnItemActionListener {
        void onAcceptClick(ZhilianApi.ZhilianTaskInfo task);
        void onRevertClick(ZhilianApi.ZhilianTaskInfo task);
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.actionListener = listener;
    }

    public void setShowAcceptButton(boolean show) {
        this.showAcceptButton = show;
    }

    public void setShowRevertButton(boolean show) {
        this.showRevertButton = show;
    }

    public void setData(List<ZhilianApi.ZhilianTaskInfo> list) {
        this.dataList = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void clearData() {
        this.dataList.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zhilian_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ZhilianApi.ZhilianTaskInfo item = dataList.get(position);

        // 序号
        holder.tvIndex.setText(String.valueOf(position + 1));

        // 站点名称
        holder.tvSiteName.setText(item.siteName);

        // 状态
        holder.tvStatus.setText(item.status);
        if ("未领取".equals(item.status)) {
            holder.tvStatus.setTextColor(0xFF2563EB);
            holder.tvStatus.setBackgroundColor(0xFFDBEAFE);
        } else if ("已领取".equals(item.status)) {
            holder.tvStatus.setTextColor(0xFF10B981);
            holder.tvStatus.setBackgroundColor(0xFFD1FAE5);
        } else {
            holder.tvStatus.setTextColor(0xFF6B7280);
            holder.tvStatus.setBackgroundColor(0xFFF3F4F6);
        }

        // 任务类型
        holder.tvTaskType.setText(item.taskTypeName);

        // 创建时间
        holder.tvCreateTime.setText(item.createTime);

        // 处理人（已领取显示）
        if (item.doDealName != null && !item.doDealName.isEmpty()) {
            holder.layoutDealInfo.setVisibility(View.VISIBLE);
            holder.tvDoDealName.setText(item.doDealName);
        } else {
            holder.layoutDealInfo.setVisibility(View.GONE);
        }

        // 按钮显示控制
        holder.btnManualAccept.setVisibility(showAcceptButton ? View.VISIBLE : View.GONE);
        holder.btnManualRevert.setVisibility(showRevertButton ? View.VISIBLE : View.GONE);

        // 按钮点击事件
        holder.btnManualAccept.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onAcceptClick(item);
            }
        });

        holder.btnManualRevert.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onRevertClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIndex, tvSiteName, tvStatus, tvTaskType, tvCreateTime, tvDoDealName;
        LinearLayout layoutDealInfo;
        Button btnManualAccept, btnManualRevert;

        ViewHolder(View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            tvSiteName = itemView.findViewById(R.id.tvSiteName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTaskType = itemView.findViewById(R.id.tvTaskType);
            tvCreateTime = itemView.findViewById(R.id.tvCreateTime);
            tvDoDealName = itemView.findViewById(R.id.tvDoDealName);
            layoutDealInfo = itemView.findViewById(R.id.layoutDealInfo);
            btnManualAccept = itemView.findViewById(R.id.btnManualAccept);
            btnManualRevert = itemView.findViewById(R.id.btnManualRevert);
        }
    }
}

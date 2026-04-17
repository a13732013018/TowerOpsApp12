package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.DoorApprovalApi;

import java.util.List;

/**
 * 门禁审批列表 Adapter
 */
public class DoorApprovalAdapter extends RecyclerView.Adapter<DoorApprovalAdapter.VH> {

    public interface OnApproveListener {
        void onApprove(int position, DoorApprovalApi.ApprovalItem item);
    }

    private final List<DoorApprovalApi.ApprovalItem> data;
    private OnApproveListener listener;

    public DoorApprovalAdapter(List<DoorApprovalApi.ApprovalItem> data) {
        this.data = data;
    }

    public void setOnApproveListener(OnApproveListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_door_approval, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DoorApprovalApi.ApprovalItem item = data.get(position);
        h.tvIdx.setText(String.valueOf(position + 1));
        h.tvSiteName.setText(item.siteName != null && !item.siteName.isEmpty()
                ? item.siteName : item.getShortName());
        h.tvType.setText(item.taskType != null ? item.taskType : "");
        h.tvApplicant.setText(item.loginName != null ? item.loginName : "");
        // 只取时间部分（去掉日期）
        String timeStr = item.createTime;
        if (timeStr != null && timeStr.length() > 10) {
            timeStr = timeStr.substring(5); // 去掉年份 "2026-"
        }
        h.tvTime.setText(timeStr != null ? timeStr : "");
        h.tvStatus.setText(item.status != null ? item.status : "");

        // 审批按钮
        h.btnApprove.setEnabled(true);
        h.btnApprove.setText("审批");
        h.btnApprove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApprove(h.getAdapterPosition(), item);
            }
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    /** 标记某条已审批（变灰按钮） */
    public void markApproved(int position) {
        if (position >= 0 && position < data.size()) {
            data.get(position).status = "已审批";
            notifyItemChanged(position);
        }
    }

    /** 移除某条已审批项 */
    public void remove(int position) {
        if (position >= 0 && position < data.size()) {
            data.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, data.size() - position);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIdx, tvSiteName, tvType, tvApplicant, tvTime, tvStatus;
        Button btnApprove;

        VH(@NonNull View v) {
            super(v);
            tvIdx       = v.findViewById(R.id.tvApprovalIdx);
            tvSiteName  = v.findViewById(R.id.tvApprovalSiteName);
            tvType      = v.findViewById(R.id.tvApprovalType);
            tvApplicant = v.findViewById(R.id.tvApprovalApplicant);
            tvTime      = v.findViewById(R.id.tvApprovalTime);
            tvStatus    = v.findViewById(R.id.tvApprovalStatus);
            btnApprove  = v.findViewById(R.id.btnApprove);
        }
    }
}

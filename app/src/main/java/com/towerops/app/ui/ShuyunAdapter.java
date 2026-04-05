package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 数运工单列表适配器
 */
public class ShuyunAdapter extends RecyclerView.Adapter<ShuyunAdapter.ViewHolder> {

    private List<ShuyunApi.ShuyunTaskInfo> items = new ArrayList<>();
    private OnItemClickListener listener;
    private boolean isPendingList = true; // true=待处理, false=处理中

    public interface OnItemClickListener {
        void onAcceptClick(int position, ShuyunApi.ShuyunTaskInfo item);
        void onRevertClick(int position, ShuyunApi.ShuyunTaskInfo item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setPendingList(boolean pending) {
        this.isPendingList = pending;
    }

    public void setData(List<ShuyunApi.ShuyunTaskInfo> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void addData(List<ShuyunApi.ShuyunTaskInfo> newItems) {
        if (newItems != null) {
            items.addAll(newItems);
            notifyDataSetChanged();
        }
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public ShuyunApi.ShuyunTaskInfo getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shuyun_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShuyunApi.ShuyunTaskInfo item = items.get(position);
        holder.bind(item, isPendingList, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvStatus;
        private final TextView tvSiteName;
        private final TextView tvTaskName;
        private final TextView tvCreateTime;
        private final Button btnAccept;
        private final Button btnRevert;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvSiteName = itemView.findViewById(R.id.tvSiteName);
            tvTaskName = itemView.findViewById(R.id.tvTaskName);
            tvCreateTime = itemView.findViewById(R.id.tvCreateTime);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnRevert = itemView.findViewById(R.id.btnRevert);
        }

        public void bind(ShuyunApi.ShuyunTaskInfo item, boolean isPending, OnItemClickListener listener) {
            tvSiteName.setText(item.siteName);
            tvTaskName.setText(item.taskName);
            tvCreateTime.setText(item.createTime);

            if (isPending) {
                tvStatus.setText("待处理");
                tvStatus.setBackgroundResource(R.drawable.bg_tag_primary);
                btnAccept.setVisibility(View.VISIBLE);
                btnRevert.setVisibility(View.GONE);
            } else {
                tvStatus.setText("处理中");
                tvStatus.setBackgroundResource(R.drawable.bg_tag_warning);
                btnAccept.setVisibility(View.GONE);
                btnRevert.setVisibility(View.VISIBLE);
            }

            btnAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAcceptClick(getAdapterPosition(), item);
                }
            });

            btnRevert.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRevertClick(getAdapterPosition(), item);
                }
            });
        }
    }
}

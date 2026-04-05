package com.towerops.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.HiddenApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 隐患工单统计 Fragment
 *
 * 统计维度：
 *   1. 隐患等级（一般 / 较大 / 重大）
 *   2. 隐患状态（待审核 / 已审核通过 / 待验收 等）
 *   3. 隐患归类（机房机柜 / 开关电源 / 蓄电池 等）
 *   4. 老化分析（录入时间距今 >7 / >15 / >30 / >90 天）
 *
 * 列表头单击可按 站址名称 / 等级 / 状态 / 天数 正反排序
 */
public class HiddenFragment extends Fragment {

    private static final String TAG = "HiddenFragment";

    // ── 排序状态 ─────────────────────────────────────────────────────────
    enum SortCol { STATION, LEVEL, STATUS, DAYS }
    private SortCol sortCol = SortCol.DAYS;  // 默认按天数
    private boolean sortAsc = false;         // 默认降序（天数大→小）

    // ── 控件 ──────────────────────────────────────────────────────────────
    private Button   btnQuery;
    private TextView tvStatus;
    private LinearLayout layoutSummary, layoutStats, layoutHeader;
    private TextView tvTotal, tvOver7, tvOver15, tvOver30, tvOver90;
    private TextView tvLevelStats, tvStatusStats, tvCategoryStats;
    private TextView tvSortStation, tvSortLevel, tvSortStatus, tvSortDays;
    private RecyclerView rvHidden;

    // ── 数据 ──────────────────────────────────────────────────────────────
    private HiddenAdapter adapter;
    private List<HiddenApi.HiddenRecord> allRecords = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean querying = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_hidden, container, false);
        bindViews(v);
        setupListeners();
        setupRecycler();
        return v;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 初始化
    // ─────────────────────────────────────────────────────────────────────

    private void bindViews(View v) {
        btnQuery       = v.findViewById(R.id.btnHiddenQuery);
        tvStatus       = v.findViewById(R.id.tvHiddenStatus);
        layoutSummary  = v.findViewById(R.id.layoutHiddenSummary);
        layoutStats    = v.findViewById(R.id.layoutHiddenStats);
        layoutHeader   = v.findViewById(R.id.layoutHiddenHeader);
        tvTotal        = v.findViewById(R.id.tvHiddenTotal);
        tvOver7        = v.findViewById(R.id.tvHiddenOver7);
        tvOver15       = v.findViewById(R.id.tvHiddenOver15);
        tvOver30       = v.findViewById(R.id.tvHiddenOver30);
        tvOver90       = v.findViewById(R.id.tvHiddenOver90);
        tvLevelStats   = v.findViewById(R.id.tvHiddenLevelStats);
        tvStatusStats  = v.findViewById(R.id.tvHiddenStatusStats);
        tvCategoryStats= v.findViewById(R.id.tvHiddenCategoryStats);
        tvSortStation  = v.findViewById(R.id.tvHiddenSortStation);
        tvSortLevel    = v.findViewById(R.id.tvHiddenSortLevel);
        tvSortStatus   = v.findViewById(R.id.tvHiddenSortStatus);
        tvSortDays     = v.findViewById(R.id.tvHiddenSortDays);
        rvHidden       = v.findViewById(R.id.rvHidden);
    }

    private void setupListeners() {
        btnQuery.setOnClickListener(v -> doQuery());

        // 表头排序点击
        tvSortStation.setOnClickListener(v -> onSortClick(SortCol.STATION));
        tvSortLevel  .setOnClickListener(v -> onSortClick(SortCol.LEVEL));
        tvSortStatus .setOnClickListener(v -> onSortClick(SortCol.STATUS));
        tvSortDays   .setOnClickListener(v -> onSortClick(SortCol.DAYS));
    }

    private void onSortClick(SortCol col) {
        if (sortCol == col) {
            sortAsc = !sortAsc; // 同列再点 → 反转
        } else {
            sortCol = col;
            sortAsc = (col == SortCol.STATION || col == SortCol.STATUS); // 文字列默认升序
        }
        applySortAndRefresh();
        updateSortHeaders();
    }

    private void setupRecycler() {
        adapter = new HiddenAdapter();
        rvHidden.setLayoutManager(new LinearLayoutManager(getContext()));
        rvHidden.setAdapter(adapter);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 排序逻辑
    // ─────────────────────────────────────────────────────────────────────

    private void applySortAndRefresh() {
        List<HiddenApi.HiddenRecord> sorted = new ArrayList<>(allRecords);
        Collections.sort(sorted, (a, b) -> {
            int cmp;
            switch (sortCol) {
                case STATION:
                    cmp = a.stationName.compareTo(b.stationName);
                    break;
                case LEVEL:
                    cmp = levelOrder(a.level) - levelOrder(b.level);
                    break;
                case STATUS:
                    cmp = a.status.compareTo(b.status);
                    break;
                case DAYS:
                default:
                    cmp = Integer.compare(a.ageDays, b.ageDays);
                    break;
            }
            return sortAsc ? cmp : -cmp;
        });
        adapter.setData(sorted);
    }

    /** 等级排序权重：重大=0，较大=1，一般=2，其他=3 */
    private static int levelOrder(String level) {
        if (level == null) return 3;
        if (level.contains("重大")) return 0;
        if (level.contains("较大")) return 1;
        if (level.contains("一般")) return 2;
        return 3;
    }

    private void updateSortHeaders() {
        // 重置全部
        tvSortStation.setText("站址名称 ↕");
        tvSortLevel  .setText("等级 ↕");
        tvSortStatus .setText("状态 ↕");
        tvSortDays   .setText("天数 ↕");
        tvSortStation.setTextColor(Color.parseColor("#374151"));
        tvSortLevel  .setTextColor(Color.parseColor("#374151"));
        tvSortStatus .setTextColor(Color.parseColor("#374151"));
        tvSortDays   .setTextColor(Color.parseColor("#374151"));

        String arrow = sortAsc ? " ↑" : " ↓";
        int activeColor = Color.parseColor("#4F46E5");
        switch (sortCol) {
            case STATION: tvSortStation.setText("站址名称" + arrow); tvSortStation.setTextColor(activeColor); break;
            case LEVEL:   tvSortLevel  .setText("等级" + arrow);     tvSortLevel  .setTextColor(activeColor); break;
            case STATUS:  tvSortStatus .setText("状态" + arrow);     tvSortStatus .setTextColor(activeColor); break;
            case DAYS:    tvSortDays   .setText("天数" + arrow);     tvSortDays   .setTextColor(activeColor); break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 查询逻辑
    // ─────────────────────────────────────────────────────────────────────

    private void doQuery() {
        if (querying) return;

        querying = true;
        btnQuery.setEnabled(false);
        tvStatus.setText("查询中...");
        layoutSummary.setVisibility(View.GONE);
        layoutStats  .setVisibility(View.GONE);
        layoutHeader .setVisibility(View.GONE);
        allRecords.clear();
        adapter.setData(new ArrayList<>());

        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            HiddenApi.QueryResult result = HiddenApi.query(200);
            mainHandler.post(() -> {
                querying = false;
                btnQuery.setEnabled(true);
                if (!result.success) {
                    tvStatus.setText("失败：" + result.errorMsg);
                    return;
                }
                if (result.records.isEmpty()) {
                    String msg = (result.errorMsg != null && !result.errorMsg.isEmpty())
                            ? result.errorMsg : "暂无数据";
                    tvStatus.setText(msg);
                    return;
                }

                allRecords.addAll(result.records);

                // 汇总卡片
                tvTotal .setText(String.valueOf(result.totalCount));
                tvOver7 .setText(String.valueOf(result.over7Days));
                tvOver15.setText(String.valueOf(result.over15Days));
                tvOver30.setText(String.valueOf(result.over30Days));
                tvOver90.setText(String.valueOf(result.over90Days));
                layoutSummary.setVisibility(View.VISIBLE);

                // 等级/状态/归类 统计
                tvLevelStats   .setText(buildStatText(result.levelMap));
                tvStatusStats  .setText(buildStatText(result.statusMap));
                tvCategoryStats.setText(buildStatText(result.categoryMap));
                layoutStats.setVisibility(View.VISIBLE);

                // 列表
                layoutHeader.setVisibility(View.VISIBLE);
                updateSortHeaders();
                applySortAndRefresh();
                tvStatus.setText("共 " + result.totalCount + " 条");
            });
        });
    }

    /** 生成统计文本，每行 "名称: 数量" */
    private String buildStatText(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────────────

    private static class HiddenAdapter extends RecyclerView.Adapter<HiddenAdapter.VH> {
        private List<HiddenApi.HiddenRecord> data = new ArrayList<>();

        void setData(List<HiddenApi.HiddenRecord> list) {
            data = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hidden_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH vh, int position) {
            HiddenApi.HiddenRecord r = data.get(position);
            vh.tvStation .setText(r.stationName);
            vh.tvCategory.setText(r.category.isEmpty() ? "其他" : r.category);
            vh.tvEnterDate.setText(r.enterDate.isEmpty() ? r.findDate : r.enterDate);

            // 隐患等级颜色
            String lv = r.level.isEmpty() ? "-" : r.level;
            int lvColor;
            if (lv.contains("重大"))       lvColor = Color.parseColor("#7C3AED");
            else if (lv.contains("较大"))  lvColor = Color.parseColor("#DC2626");
            else                           lvColor = Color.parseColor("#D97706");
            vh.tvLevel.setText(lv);
            vh.tvLevel.setTextColor(lvColor);

            // 状态
            vh.tvStatus.setText(r.status.isEmpty() ? "-" : r.status);

            // 天数
            if (r.ageDays < 0) {
                vh.tvDays.setText("-");
                vh.tvDays.setTextColor(Color.parseColor("#9CA3AF"));
            } else {
                vh.tvDays.setText(r.ageDays + "天");
                int daysColor;
                if (r.ageDays > 90)       daysColor = Color.parseColor("#7C3AED");
                else if (r.ageDays > 30)  daysColor = Color.parseColor("#DC2626");
                else if (r.ageDays > 15)  daysColor = Color.parseColor("#EA580C");
                else if (r.ageDays > 7)   daysColor = Color.parseColor("#D97706");
                else                      daysColor = Color.parseColor("#16A34A");
                vh.tvDays.setTextColor(daysColor);
            }

            // 背景（超30天浅红，超90天浅紫）
            if (r.ageDays > 90) {
                vh.itemView.setBackgroundColor(Color.parseColor("#F5F3FF"));
            } else if (r.ageDays > 30) {
                vh.itemView.setBackgroundColor(Color.parseColor("#FEF2F2"));
            } else {
                vh.itemView.setBackgroundColor(Color.WHITE);
            }
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvStation, tvLevel, tvStatus, tvDays, tvCategory, tvEnterDate;
            VH(View v) {
                super(v);
                tvStation   = v.findViewById(R.id.tvHiddenStation);
                tvLevel     = v.findViewById(R.id.tvHiddenLevel);
                tvStatus    = v.findViewById(R.id.tvHiddenStatus);
                tvDays      = v.findViewById(R.id.tvHiddenDays);
                tvCategory  = v.findViewById(R.id.tvHiddenCategory);
                tvEnterDate = v.findViewById(R.id.tvHiddenEnterDate);
            }
        }
    }
}

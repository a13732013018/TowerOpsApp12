package com.towerops.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.RepairApi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日常维修统计 Fragment
 *
 * 按维修人统计：
 *   - 总费用 / 有效费用 / 无效费用
 *   - 各隐患归类的金额分布（展开显示）
 */
public class RepairFragment extends Fragment {

    private static final String TAG = "RepairFragment";
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // ── 控件 ──────────────────────────────────────────────────────────────
    private EditText etStartDate, etEndDate;
    private Button   btnStartMinus, btnStartPlus, btnEndMinus, btnEndPlus, btnQuery;
    private TextView tvStatus;
    private LinearLayout layoutSummary, layoutHeader, layoutTotal;
    private TextView tvTotalCost, tvValidCost, tvInvalidCost, tvTotalCount, tvInvalidCount;
    private TextView tvTotalRowCount, tvTotalRowCost, tvTotalRowValid, tvTotalRowInvalid;
    private RecyclerView rvRepair;

    // ── 状态 ──────────────────────────────────────────────────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private RepairAdapter adapter;
    private boolean querying = false;

    // ─────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_repair, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        initDefaultDates();
        setupListeners();

        rvRepair.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RepairAdapter(new ArrayList<>());
        rvRepair.setAdapter(adapter);
    }

    private void bindViews(View v) {
        etStartDate    = v.findViewById(R.id.etRepairStartDate);
        etEndDate      = v.findViewById(R.id.etRepairEndDate);
        btnStartMinus  = v.findViewById(R.id.btnRepairStartMinus);
        btnStartPlus   = v.findViewById(R.id.btnRepairStartPlus);
        btnEndMinus    = v.findViewById(R.id.btnRepairEndMinus);
        btnEndPlus     = v.findViewById(R.id.btnRepairEndPlus);
        btnQuery       = v.findViewById(R.id.btnRepairQuery);
        tvStatus       = v.findViewById(R.id.tvRepairStatus);
        layoutSummary  = v.findViewById(R.id.layoutRepairSummary);
        layoutHeader   = v.findViewById(R.id.layoutRepairHeader);
        layoutTotal    = v.findViewById(R.id.layoutRepairTotal);
        tvTotalCost    = v.findViewById(R.id.tvRepairTotalCost);
        tvValidCost    = v.findViewById(R.id.tvRepairValidCost);
        tvInvalidCost  = v.findViewById(R.id.tvRepairInvalidCost);
        tvTotalCount   = v.findViewById(R.id.tvRepairTotalCount);
        tvInvalidCount = v.findViewById(R.id.tvRepairInvalidCount);
        tvTotalRowCount   = v.findViewById(R.id.tvTotalRowCount);
        tvTotalRowCost    = v.findViewById(R.id.tvTotalRowCost);
        tvTotalRowValid   = v.findViewById(R.id.tvTotalRowValid);
        tvTotalRowInvalid = v.findViewById(R.id.tvTotalRowInvalid);
        rvRepair       = v.findViewById(R.id.rvRepair);
    }

    private void initDefaultDates() {
        Calendar cal = Calendar.getInstance();
        etEndDate.setText(DATE_FMT.format(cal.getTime()));
        cal.set(Calendar.DAY_OF_MONTH, 1);
        etStartDate.setText(DATE_FMT.format(cal.getTime()));
    }

    private void setupListeners() {
        btnStartMinus.setOnClickListener(v -> shiftDate(etStartDate, -1));
        btnStartPlus .setOnClickListener(v -> shiftDate(etStartDate, +1));
        btnEndMinus  .setOnClickListener(v -> shiftDate(etEndDate, -1));
        btnEndPlus   .setOnClickListener(v -> shiftDate(etEndDate, +1));
        btnQuery     .setOnClickListener(v -> doQuery());
    }

    private void shiftDate(EditText et, int delta) {
        try {
            Date d = DATE_FMT.parse(et.getText().toString().trim());
            Calendar cal = Calendar.getInstance();
            assert d != null;
            cal.setTime(d);
            cal.add(Calendar.DAY_OF_MONTH, delta);
            et.setText(DATE_FMT.format(cal.getTime()));
        } catch (Exception ignored) {}
    }

    private void doQuery() {
        if (querying) return;
        String start = etStartDate.getText().toString().trim();
        String end   = etEndDate  .getText().toString().trim();
        if (!start.matches("\\d{4}-\\d{2}-\\d{2}") || !end.matches("\\d{4}-\\d{2}-\\d{2}")) {
            tvStatus.setText("日期格式错误，请使用 yyyy-MM-dd");
            return;
        }

        querying = true;
        btnQuery.setEnabled(false);
        tvStatus.setText("查询中...");
        layoutSummary.setVisibility(View.GONE);
        layoutHeader .setVisibility(View.GONE);
        layoutTotal  .setVisibility(View.GONE);
        adapter.setData(new ArrayList<>());

        executor.execute(() -> {
            RepairApi.QueryResult result = RepairApi.query(start, end, 200);
            mainHandler.post(() -> {
                querying = false;
                btnQuery.setEnabled(true);
                if (!result.success) {
                    tvStatus.setText(result.errorMsg != null ? result.errorMsg : "查询失败");
                    return;
                }
                if (result.records.isEmpty()) {
                    String msg = (result.errorMsg != null && !result.errorMsg.isEmpty())
                            ? result.errorMsg
                            : "暂无数据（" + start + " ~ " + end + "）";
                    tvStatus.setText(msg);
                    layoutTotal.setVisibility(View.GONE);
                    return;
                }
                // 汇总卡片
                int totalInvalidCount = 0;
                for (RepairApi.WorkerStats ws : result.workerStats) {
                    totalInvalidCount += ws.invalidCount;
                }
                tvTotalCost  .setText(fmt(result.totalCostAll));
                tvValidCost  .setText(fmt(result.validCostAll));
                tvInvalidCost.setText(fmt(result.invalidCostAll));
                tvTotalCount .setText(String.valueOf(result.totalCount));
                tvInvalidCount.setText(String.valueOf(totalInvalidCount));
                layoutSummary.setVisibility(View.VISIBLE);
                layoutHeader .setVisibility(View.VISIBLE);

                // 合计行
                tvTotalRowCount  .setText(result.totalCount + "/" + totalInvalidCount);
                tvTotalRowCost   .setText(fmt(result.totalCostAll));
                tvTotalRowValid  .setText(fmt(result.validCostAll));
                tvTotalRowInvalid.setText(fmt(result.invalidCostAll));
                layoutTotal.setVisibility(View.VISIBLE);

                // 在状态栏显示归类汇总
                StringBuilder catSb = new StringBuilder("归类汇总：");
                for (Map.Entry<String, Double> e : result.globalCategoryMap.entrySet()) {
                    catSb.append(e.getKey()).append(" ¥").append(fmt(e.getValue())).append("  ");
                }
                tvStatus.setText(catSb.toString().trim());

                adapter.setData(result.workerStats);
            });
        });
    }

    private static String fmt(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format(Locale.getDefault(), "%.2f", v);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ─── Adapter ─────────────────────────────────────────────────────────

    static class RepairAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_WORKER   = 0;
        private static final int TYPE_CATEGORY = 1;

        /** 展开的行项 */
        static class Row {
            boolean isWorker;
            RepairApi.WorkerStats ws;   // isWorker=true
            String catName;             // isWorker=false
            double catAmt;
        }

        private List<Row> rows = new ArrayList<>();
        /** 记录哪些 worker 已展开 */
        private final java.util.Set<String> expanded = new java.util.HashSet<>();

        RepairAdapter(List<RepairApi.WorkerStats> data) {
            setData(data);
        }

        void setData(List<RepairApi.WorkerStats> data) {
            rows.clear();
            expanded.clear();
            for (RepairApi.WorkerStats ws : data) {
                Row r = new Row();
                r.isWorker = true;
                r.ws = ws;
                rows.add(r);
            }
            notifyDataSetChanged();
        }

        private void toggleExpand(int position) {
            Row clicked = rows.get(position);
            if (!clicked.isWorker) return;
            String name = clicked.ws.name;
            if (expanded.contains(name)) {
                // 收起：移除该 worker 后面的分类行
                expanded.remove(name);
                int pos = position + 1;
                int count = 0;
                while (pos < rows.size() && !rows.get(pos).isWorker) {
                    rows.remove(pos);
                    count++;
                }
                notifyItemRangeRemoved(position + 1, count);
            } else {
                // 展开：插入分类行
                expanded.add(name);
                int ins = position + 1;
                List<Row> cats = new ArrayList<>();
                for (Map.Entry<String, Double> e : clicked.ws.categoryMap.entrySet()) {
                    Row cr = new Row();
                    cr.isWorker = false;
                    cr.catName  = e.getKey();
                    cr.catAmt   = e.getValue();
                    cats.add(cr);
                }
                rows.addAll(ins, cats);
                notifyItemRangeInserted(ins, cats.size());
            }
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).isWorker ? TYPE_WORKER : TYPE_CATEGORY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_WORKER) {
                View v = inf.inflate(R.layout.item_repair_worker, parent, false);
                return new WorkerVH(v);
            } else {
                View v = inf.inflate(R.layout.item_repair_category, parent, false);
                return new CatVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof WorkerVH) {
                WorkerVH vh = (WorkerVH) holder;
                RepairApi.WorkerStats ws = row.ws;
                vh.tvName      .setText(ws.name);
                vh.tvCount     .setText(String.valueOf(ws.count));
                vh.tvInvalidCount.setText(ws.invalidCount > 0 ? "无效" + ws.invalidCount : "");
                vh.tvTotal     .setText(fmt(ws.totalCost));
                vh.tvValid     .setText(fmt(ws.validCost));
                vh.tvInvalid   .setText(fmt(ws.invalidCost));
                // 无效费用 > 0 则红色标注
                vh.tvInvalid.setTextColor(ws.invalidCost > 0 ? Color.parseColor("#DC2626") : Color.parseColor("#374151"));
                boolean isExp = expanded.contains(ws.name);
                vh.tvArrow.setText(isExp ? "▼" : "▶");
                vh.itemView.setOnClickListener(v -> toggleExpand(holder.getAdapterPosition()));
            } else if (holder instanceof CatVH) {
                CatVH vh = (CatVH) holder;
                vh.tvCatName.setText("  · " + row.catName);
                vh.tvCatAmt .setText("¥" + fmt(row.catAmt));
            }
        }

        @Override
        public int getItemCount() { return rows.size(); }

        static class WorkerVH extends RecyclerView.ViewHolder {
            TextView tvName, tvCount, tvInvalidCount, tvTotal, tvValid, tvInvalid, tvArrow;
            WorkerVH(View v) {
                super(v);
                tvName         = v.findViewById(R.id.tvWorkerName);
                tvCount        = v.findViewById(R.id.tvWorkerCount);
                tvInvalidCount = v.findViewById(R.id.tvWorkerInvalidCount);
                tvTotal        = v.findViewById(R.id.tvWorkerTotal);
                tvValid        = v.findViewById(R.id.tvWorkerValid);
                tvInvalid      = v.findViewById(R.id.tvWorkerInvalid);
                tvArrow        = v.findViewById(R.id.tvWorkerArrow);
            }
        }

        static class CatVH extends RecyclerView.ViewHolder {
            TextView tvCatName, tvCatAmt;
            CatVH(View v) {
                super(v);
                tvCatName = v.findViewById(R.id.tvCatName);
                tvCatAmt  = v.findViewById(R.id.tvCatAmt);
            }
        }
    }
}

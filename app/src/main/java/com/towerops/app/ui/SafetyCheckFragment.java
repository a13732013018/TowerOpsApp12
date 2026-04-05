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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.SafetyCheckApi;
import com.towerops.app.api.TowerLoginApi;
import com.towerops.app.model.Session;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 安全打卡统计 Fragment
 *
 * 统计每个人：
 *   - 打卡天数（自然日去重）
 *   - 打卡次数（总计）
 *   - 合格次数 / 不合格次数
 *   - 平均打卡时长（分钟）
 *
 * 合格标准（来自 SafetyCheckApi）：
 *   - 巡检打卡：>= 20 分钟
 *   - 维修打卡：>= 10 分钟
 */
public class SafetyCheckFragment extends Fragment {

    private static final String TAG = "SafetyCheckFragment";
    // ── 控件 ──────────────────────────────────────────────────────────────
    private EditText   etStartDate, etEndDate;
    private Button     btnQuery, btnStartMinus, btnStartPlus, btnEndMinus, btnEndPlus;
    private TextView   tvStatus;
    private LinearLayout layoutSummaryCards, layoutTableHeader;
    private TextView   tvTotalCount, tvQualifiedCount, tvUnqualifiedCount, tvPersonCount;
    private RecyclerView rvSafetyCheck;

    // ── 数据 ──────────────────────────────────────────────────────────────
    private final List<SafetyCheckApi.PersonStats> statsList = new ArrayList<>();
    private StatsAdapter adapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ─────────────────────────────────────────────────────────────────────
    // 生命周期
    // ─────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_safety_check, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etStartDate    = view.findViewById(R.id.etStartDate);
        etEndDate      = view.findViewById(R.id.etEndDate);
        btnQuery       = view.findViewById(R.id.btnQuerySafetyCheck);
        btnStartMinus  = view.findViewById(R.id.btnStartMinus);
        btnStartPlus   = view.findViewById(R.id.btnStartPlus);
        btnEndMinus    = view.findViewById(R.id.btnEndMinus);
        btnEndPlus     = view.findViewById(R.id.btnEndPlus);
        tvStatus       = view.findViewById(R.id.tvSafetyCheckStatus);

        layoutSummaryCards  = view.findViewById(R.id.layoutSummaryCards);
        layoutTableHeader   = view.findViewById(R.id.layoutTableHeader);
        tvTotalCount        = view.findViewById(R.id.tvTotalCount);
        tvQualifiedCount    = view.findViewById(R.id.tvQualifiedCount);
        tvUnqualifiedCount  = view.findViewById(R.id.tvUnqualifiedCount);
        tvPersonCount       = view.findViewById(R.id.tvPersonCount);

        rvSafetyCheck = view.findViewById(R.id.rvSafetyCheck);
        adapter = new StatsAdapter(statsList);
        rvSafetyCheck.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSafetyCheck.setAdapter(adapter);

        // 默认日期：本月第1天 ~ 今天
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        String today = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String firstDay = sdf.format(cal.getTime());
        etStartDate.setText(firstDay);
        etEndDate.setText(today);

        // ± 1天按钮
        btnStartMinus.setOnClickListener(v -> shiftDate(etStartDate, -1));
        btnStartPlus .setOnClickListener(v -> shiftDate(etStartDate, +1));
        btnEndMinus  .setOnClickListener(v -> shiftDate(etEndDate,   -1));
        btnEndPlus   .setOnClickListener(v -> shiftDate(etEndDate,   +1));

        btnQuery.setOnClickListener(v -> doQuery());

        // 状态栏初始提示（从 Session 自动读取 OMMS 认证状态）
        refreshStatus();
    }

    /** 刷新状态栏提示 */
    private void refreshStatus() {
        Session s = Session.get();
        boolean hasOmms = !s.ommsCookie.isEmpty();

        if (hasOmms) {
            tvStatus.setText("就绪（维修打卡 ✓），选日期后点查询");
        } else {
            tvStatus.setText("请先完成 OMMS 登录后再查询打卡统计");
        }
    }

    /** 将日期输入框的日期偏移 delta 天 */
    private void shiftDate(EditText et, int delta) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date d = sdf.parse(et.getText().toString().trim());
            if (d == null) return;
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.add(Calendar.DAY_OF_MONTH, delta);
            et.setText(sdf.format(cal.getTime()));
        } catch (Exception ignored) { }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 查询逻辑
    // ─────────────────────────────────────────────────────────────────────

    private void doQuery() {
        String start  = etStartDate.getText().toString().trim();
        String end    = etEndDate.getText().toString().trim();

        // ── 直接从 Session 读认证（无需手动输入）
        Session s = Session.get();

        // ── 只需要 OMMS 认证（巡检打卡暂不统计）
        boolean hasOmms = !s.ommsCookie.isEmpty();
        if (!hasOmms) {
            Toast.makeText(requireContext(),
                    "请先完成 OMMS 登录后再查询维修打卡统计",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (start.isEmpty() || end.isEmpty()) {
            Toast.makeText(requireContext(),
                    "请填写查询日期范围", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean doOmms = hasOmms;
        final boolean doToms = false;  // 巡检打卡暂不统计，只统计维修打卡（OMMS）

        btnQuery.setEnabled(false);
        tvStatus.setText("查询中，请稍候...");
        layoutSummaryCards.setVisibility(View.GONE);
        layoutTableHeader.setVisibility(View.GONE);
        statsList.clear();
        adapter.notifyDataSetChanged();

        final String queryStart = start;
        final String queryEnd   = end;

        // 重置诊断字段，避免上次残留信息干扰
        if (doOmms) SafetyCheckApi.ommsDiag = "";
        if (doToms)  SafetyCheckApi.tomsDiag  = "";

        executor.execute(() -> {
            List<SafetyCheckApi.CheckRecord> allRecords = new ArrayList<>();
            int ommsArchivedCount = 0, ommsUnarchivedCount = 0;
            int tomsArchivedCount = 0, tomsUnarchivedCount = 0;

            // ── 路线A：OMMS 维修打卡 ─────────────────────────────────
            if (doOmms) {
                // 先试一次，检测401 → 自动刷新Cookie → 重试
                boolean ommsRetryDone = false;
                // 已归档（带日期范围）
                int page = 1;
                while (true) {
                    String resp = SafetyCheckApi.fetchOmmsArchived(queryStart, queryEnd, page, 100);
                    // 401检测：首次遇到401时自动刷新Cookie并重试一次
                    if (!ommsRetryDone && (resp.contains("S-SYS-00027")
                            || resp.contains("session has timed out"))) {
                        ommsRetryDone = true;
                        Session sess = Session.get();
                        if (sess.tower4aSessionCookie != null && !sess.tower4aSessionCookie.isEmpty()) {
                            android.util.Log.d(TAG, "OMMS 401 detected, auto refresh Cookie");
                            TowerLoginApi.Result lr = TowerLoginApi.autoGetOmmsCookie(
                                    sess.tower4aSessionCookie, sess.username, getContext());
                            if (lr.success) {
                                // Cookie已刷新，重试当前页
                                resp = SafetyCheckApi.fetchOmmsArchived(queryStart, queryEnd, page, 100);
                                android.util.Log.d(TAG, "OMMS Cookie refreshed, retry archived page " + page);
                            }
                        }
                    }
                    List<SafetyCheckApi.CheckRecord> batch = SafetyCheckApi.parseRecords(resp);
                    if (batch.isEmpty()) break;
                    allRecords.addAll(batch);
                    ommsArchivedCount += batch.size();
                    if (batch.size() < 100) break;
                    if (++page > 20) break;
                }
                // 未归档（不带日期范围）
                page = 1;
                while (true) {
                    String resp = SafetyCheckApi.fetchOmmsUnarchived(page, 100);
                    List<SafetyCheckApi.CheckRecord> batch = SafetyCheckApi.parseRecords(resp);
                    if (batch.isEmpty()) break;
                    allRecords.addAll(batch);
                    ommsUnarchivedCount += batch.size();
                    if (batch.size() < 100) break;
                    if (++page > 20) break;
                }
            }

            // ── 路线B：TOMS 巡检打卡 ─────────────────────────────────
            if (doToms) {
                // 未归档（无日期，拉全量当前待处理）
                int page = 1;
                while (true) {
                    String resp = SafetyCheckApi.fetchTomsUnarchived(page, 100);
                    List<SafetyCheckApi.CheckRecord> batch = SafetyCheckApi.parseRecords(resp);
                    if (batch.isEmpty()) break;
                    allRecords.addAll(batch);
                    tomsUnarchivedCount += batch.size();
                    if (batch.size() < 100) break;
                    if (++page > 20) break;
                }
                // 已归档（带 UI 日期范围，抓包确认必须传 startCreateTime/endCreateTime）
                page = 1;
                while (true) {
                    String resp = SafetyCheckApi.fetchTomsArchived(queryStart, queryEnd, page, 100);
                    List<SafetyCheckApi.CheckRecord> batch = SafetyCheckApi.parseRecords(resp);
                    if (batch.isEmpty()) break;
                    allRecords.addAll(batch);
                    tomsArchivedCount += batch.size();
                    if (batch.size() < 100) break;
                    if (++page > 20) break;
                }
            }

            // ── 汇总统计 ─────────────────────────────────────────────
            List<SafetyCheckApi.PersonStats> result =
                    SafetyCheckApi.aggregateByPerson(allRecords);

            int totalAll = allRecords.size();
            int qualAll = 0, unqualAll = 0;
            for (SafetyCheckApi.CheckRecord r : allRecords) {
                if (r.isQualified()) qualAll++;
                else                 unqualAll++;
            }
            final int fTotal = totalAll, fQual = qualAll;
            final int fOmmsArch = ommsArchivedCount, fOmmsUnarch = ommsUnarchivedCount;
            final int fTomsArch = tomsArchivedCount, fTomsUnarch = tomsUnarchivedCount;

            mainHandler.post(() -> {
                if (!isAdded()) return;
                statsList.clear();
                statsList.addAll(result);
                adapter.notifyDataSetChanged();

                tvTotalCount.setText(String.valueOf(fTotal));
                tvQualifiedCount.setText(String.valueOf(fQual));
                tvUnqualifiedCount.setText(String.valueOf(totalAll - fQual));
                tvPersonCount.setText(String.valueOf(result.size()));

                if (fTotal > 0) {
                    int rate = (int) Math.round(fQual * 100.0 / fTotal);
                    StringBuilder sb = new StringBuilder();
                    if (doOmms) {
                        sb.append("维修：已归档 ").append(fOmmsArch)
                          .append(" + 未归档 ").append(fOmmsUnarch).append(" 条");
                    }
                    if (doToms) {
                        if (sb.length() > 0) sb.append("；");
                        sb.append("巡检：未归档 ").append(fTomsUnarch)
                          .append(" + 已归档 ").append(fTomsArch).append(" 条");
                    }
                    sb.append("  合计 ").append(fTotal).append(" 条，合格率 ").append(rate).append("%");
                    sb.append("（巡检≥").append(SafetyCheckApi.XUNJIAN_MIN_MINUTES)
                      .append("分钟，维修≥").append(SafetyCheckApi.WEIXIU_MIN_MINUTES).append("分钟）");
                    tvStatus.setText(sb.toString());
                    layoutSummaryCards.setVisibility(View.VISIBLE);
                    layoutTableHeader.setVisibility(View.VISIBLE);
                } else {
                    StringBuilder diagSb = new StringBuilder("暂无数据\n");
                    if (doOmms) {
                        String od = SafetyCheckApi.ommsDiag;
                        if (od.isEmpty()) {
                            diagSb.append("OMMS：未请求");
                        } else if (od.contains("HTTP 401") || od.contains("session has timed out")
                                || od.contains("S-SYS-00027")) {
                            diagSb.append("⚠️ OMMS Cookie 已过期！请去【门禁系统】Tab 重新点「OMMS登录」");
                        } else {
                            diagSb.append(od);
                        }
                    }
                    if (doToms) {
                        if (doOmms) diagSb.append("\n");
                        String td = SafetyCheckApi.tomsDiag;
                        if (td.isEmpty()) {
                            diagSb.append("TOMS：未请求");
                        } else if (td.contains("HTTP 0")) {
                            diagSb.append("⚠️ TOMS 连接失败（HTTP 0）：请检查网络是否在公司内网");
                        } else if (td.contains("HTTP 401") || td.contains("unauthorized")
                                || td.contains("Unauthorized")) {
                            diagSb.append("⚠️ TOMS Token 已过期！请去【门禁系统】Tab 重新点「OMMS登录」刷新 Cookie");
                        } else {
                            diagSb.append(td);
                        }
                    }
                    if (!doOmms && !doToms) {
                        diagSb.append("请先完成 OMMS 登录（维修打卡）；巡检打卡需设置 TOMS Token");
                    }
                    tvStatus.setText(diagSb.toString());
                }
                btnQuery.setEnabled(true);
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // RecyclerView Adapter
    // ─────────────────────────────────────────────────────────────────────

    private static class StatsAdapter
            extends RecyclerView.Adapter<StatsAdapter.VH> {

        private final List<SafetyCheckApi.PersonStats> data;

        StatsAdapter(List<SafetyCheckApi.PersonStats> data) {
            this.data = data;
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvDays, tvCount, tvQualified, tvUnqualified, tvPassRate, tvAvgDuration;
            VH(@NonNull View v) {
                super(v);
                tvName        = v.findViewById(R.id.tvScName);
                tvDays        = v.findViewById(R.id.tvScDays);
                tvCount       = v.findViewById(R.id.tvScCount);
                tvQualified   = v.findViewById(R.id.tvScQualified);
                tvUnqualified = v.findViewById(R.id.tvScUnqualified);
                tvPassRate    = v.findViewById(R.id.tvScPassRate);
                tvAvgDuration = v.findViewById(R.id.tvScAvgDuration);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_safety_check, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SafetyCheckApi.PersonStats ps = data.get(position);

            h.tvName.setText(ps.name);
            h.tvDays.setText(String.valueOf(ps.dayCount));
            h.tvCount.setText(String.valueOf(ps.totalCount));
            h.tvQualified.setText(String.valueOf(ps.qualifiedCount));
            h.tvUnqualified.setText(String.valueOf(ps.unqualifiedCount));

            // 合格率 = 合格数 / 总次数 * 100%
            if (ps.totalCount > 0) {
                int rate = (int) Math.round(ps.qualifiedCount * 100.0 / ps.totalCount);
                h.tvPassRate.setText(rate + "%");
                // 100% 绿色，<60% 红色，其余蓝色
                if (rate == 100) {
                    h.tvPassRate.setTextColor(Color.parseColor("#16A34A"));
                } else if (rate < 60) {
                    h.tvPassRate.setTextColor(Color.parseColor("#DC2626"));
                } else {
                    h.tvPassRate.setTextColor(Color.parseColor("#2563EB"));
                }
            } else {
                h.tvPassRate.setText("-");
                h.tvPassRate.setTextColor(Color.parseColor("#9CA3AF"));
            }

            if (ps.avgDurationMinutes >= 0) {
                h.tvAvgDuration.setText(String.format(Locale.getDefault(),
                        "%.1f", ps.avgDurationMinutes));
            } else {
                h.tvAvgDuration.setText("--");
            }

            // 交替行背景
            h.itemView.setBackgroundColor(
                    position % 2 == 0 ? Color.WHITE : Color.parseColor("#F9FAFB"));

            // 不合格数 > 0 时字体变红加粗
            if (ps.unqualifiedCount > 0) {
                h.tvUnqualified.setTextColor(Color.parseColor("#DC2626"));
            } else {
                h.tvUnqualified.setTextColor(Color.parseColor("#6B7280"));
            }
        }

        @Override
        public int getItemCount() { return data.size(); }
    }
}

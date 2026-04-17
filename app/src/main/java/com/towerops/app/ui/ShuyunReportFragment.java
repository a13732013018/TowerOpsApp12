package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunReportApi;
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
 * 综合上站报表 Fragment
 * 功能：展示数运综合上站报表数据，支持表头排序
 */
public class ShuyunReportFragment extends Fragment {

    private static final String TAG = "ShuyunReportFragment";

    private TextView tvStatus, tvCurrentTime, tvTotalCount, tvEmpty;
    private Button btnQuery;
    private Button btnStartMinus, btnStartPlus, btnEndMinus, btnEndPlus;
    private TextView tvStartDate, tvEndDate;
    private RecyclerView rvReport;

    // 表头排序按钮
    private TextView tvHeadIndex, tvHeadArea, tvHeadCity, tvHeadPeriod;
    private TextView tvHeadEasyOrder, tvHeadSysOrder, tvHeadAppOrder, tvHeadOtherOrder;
    private TextView tvHeadDailyReply, tvHeadEfficiency, tvHeadDw;

    private ShuyunReportAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private String startDate = "";
    private String endDate = "";

    // 排序相关
    private int currentSortColumn = 0;  // 0=默认(序号), 1=地市, 2=区县, 3=起止日期, 4=中台派单, 5=系统派单, 6=APP, 7=非综合上站, 8=单日单站回单, 9=效能, 10=代维
    private boolean sortAscending = false;

    // 排序箭头
    private static final String ARROW_NONE = "";
    private static final String ARROW_DOWN = "▼";
    private static final String ARROW_UP = "▲";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shuyun_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 确保 Session 数据已恢复
        Session.get().loadConfig(requireContext());

        initViews(view);
        setupRecyclerView();
        setupListeners();

        // 更新时间
        updateTime();
        handler.postDelayed(timeRunnable, 1000);
    }

    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tvReportStatus);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalCount = view.findViewById(R.id.tvTotalCount);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnQuery = view.findViewById(R.id.btnQuery);
        btnStartMinus = view.findViewById(R.id.btnStartMinus);
        btnStartPlus = view.findViewById(R.id.btnStartPlus);
        btnEndMinus = view.findViewById(R.id.btnEndMinus);
        btnEndPlus = view.findViewById(R.id.btnEndPlus);
        tvStartDate = view.findViewById(R.id.tvStartDate);
        tvEndDate = view.findViewById(R.id.tvEndDate);
        rvReport = view.findViewById(R.id.rvReport);

        // 初始化表头
        tvHeadIndex = view.findViewById(R.id.tvHeadIndex);
        tvHeadArea = view.findViewById(R.id.tvHeadArea);
        tvHeadCity = view.findViewById(R.id.tvHeadCity);
        tvHeadPeriod = view.findViewById(R.id.tvHeadPeriod);
        tvHeadEasyOrder = view.findViewById(R.id.tvHeadEasyOrder);
        tvHeadSysOrder = view.findViewById(R.id.tvHeadSysOrder);
        tvHeadAppOrder = view.findViewById(R.id.tvHeadAppOrder);
        tvHeadOtherOrder = view.findViewById(R.id.tvHeadOtherOrder);
        tvHeadDailyReply = view.findViewById(R.id.tvHeadDailyReply);
        tvHeadEfficiency = view.findViewById(R.id.tvHeadEfficiency);
        tvHeadDw = view.findViewById(R.id.tvHeadDw);

        // 初始化日期（默认当月1号到当天）
        Calendar cal = Calendar.getInstance();
        endDate = dateFormat.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        startDate = dateFormat.format(cal.getTime());
        tvStartDate.setText(startDate);
        tvEndDate.setText(endDate);
    }

    private void setupRecyclerView() {
        adapter = new ShuyunReportAdapter();
        // 使用固定宽度布局
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rvReport.setLayoutManager(layoutManager);
        rvReport.setAdapter(adapter);
    }

    private void setupListeners() {
        btnQuery.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                queryData();
            }
        });

        // 开始日期调整
        btnStartMinus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(true, -1);
            }
        });
        btnStartPlus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(true, 1);
            }
        });

        // 结束日期调整
        btnEndMinus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(false, -1);
            }
        });
        btnEndPlus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(false, 1);
            }
        });

        // 表头排序点击
        tvHeadArea.setOnClickListener(v -> onHeaderClick(1));
        tvHeadCity.setOnClickListener(v -> onHeaderClick(2));
        tvHeadPeriod.setOnClickListener(v -> onHeaderClick(3));
        tvHeadEasyOrder.setOnClickListener(v -> onHeaderClick(4));
        tvHeadSysOrder.setOnClickListener(v -> onHeaderClick(5));
        tvHeadAppOrder.setOnClickListener(v -> onHeaderClick(6));
        tvHeadOtherOrder.setOnClickListener(v -> onHeaderClick(7));
        tvHeadDailyReply.setOnClickListener(v -> onHeaderClick(8));
        tvHeadEfficiency.setOnClickListener(v -> onHeaderClick(9));
        tvHeadDw.setOnClickListener(v -> onHeaderClick(10));
    }

    private void onHeaderClick(int column) {
        if (column == currentSortColumn) {
            // 同列点击，切换升序/降序
            sortAscending = !sortAscending;
        } else {
            // 新列点击，设为降序
            currentSortColumn = column;
            sortAscending = false;
        }
        updateHeaderArrows();
        adapter.sortByColumn(column, sortAscending);
    }

    private void updateHeaderArrows() {
        // 重置所有表头
        tvHeadIndex.setText("#");
        tvHeadIndex.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadArea.setText("地市");
        tvHeadArea.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadCity.setText("区县");
        tvHeadCity.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadPeriod.setText("起止日期");
        tvHeadPeriod.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadEasyOrder.setText("中台派单");
        tvHeadEasyOrder.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadSysOrder.setText("系统派单");
        tvHeadSysOrder.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadAppOrder.setText("综合上站APP");
        tvHeadAppOrder.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadOtherOrder.setText("非综合上站");
        tvHeadOtherOrder.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadDailyReply.setText("单日单站回单");
        tvHeadDailyReply.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadEfficiency.setText("综合上站效能");
        tvHeadEfficiency.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeadDw.setText("代维");
        tvHeadDw.setTextColor(getResources().getColor(R.color.text_secondary, null));

        // 高亮当前排序列
        String arrow = sortAscending ? ARROW_UP : ARROW_DOWN;
        int highlightColor = getResources().getColor(R.color.colorPrimary, null);
        switch (currentSortColumn) {
            case 1:
                tvHeadArea.setText("地市" + arrow);
                tvHeadArea.setTextColor(highlightColor);
                break;
            case 2:
                tvHeadCity.setText("区县" + arrow);
                tvHeadCity.setTextColor(highlightColor);
                break;
            case 3:
                tvHeadPeriod.setText("起止日期" + arrow);
                tvHeadPeriod.setTextColor(highlightColor);
                break;
            case 4:
                tvHeadEasyOrder.setText("中台派单" + arrow);
                tvHeadEasyOrder.setTextColor(highlightColor);
                break;
            case 5:
                tvHeadSysOrder.setText("系统派单" + arrow);
                tvHeadSysOrder.setTextColor(highlightColor);
                break;
            case 6:
                tvHeadAppOrder.setText("综合上站APP" + arrow);
                tvHeadAppOrder.setTextColor(highlightColor);
                break;
            case 7:
                tvHeadOtherOrder.setText("非综合上站" + arrow);
                tvHeadOtherOrder.setTextColor(highlightColor);
                break;
            case 8:
                tvHeadDailyReply.setText("单日单站回单" + arrow);
                tvHeadDailyReply.setTextColor(highlightColor);
                break;
            case 9:
                tvHeadEfficiency.setText("综合上站效能" + arrow);
                tvHeadEfficiency.setTextColor(highlightColor);
                break;
            case 10:
                tvHeadDw.setText("代维" + arrow);
                tvHeadDw.setTextColor(highlightColor);
                break;
        }
    }

    private void adjustDate(boolean isStart, int delta) {
        try {
            Calendar cal = Calendar.getInstance();
            if (isStart) {
                cal.setTime(dateFormat.parse(startDate));
            } else {
                cal.setTime(dateFormat.parse(endDate));
            }
            cal.add(Calendar.DAY_OF_MONTH, delta);
            String newDate = dateFormat.format(cal.getTime());
            if (isStart) {
                startDate = newDate;
                tvStartDate.setText(newDate);
            } else {
                endDate = newDate;
                tvEndDate.setText(newDate);
            }
        } catch (Exception e) {
            Log.e(TAG, "日期调整异常: " + e.getMessage());
        }
    }

    private void queryData() {
        Session session = Session.get();
        if (session.shuyunPcToken == null || session.shuyunPcToken.isEmpty()) {
            tvStatus.setText("请先在数运监控中登录PC版");
            return;
        }

        // 固定为温州市
        String areaName = "温州市";

        final String finalStartTime = startDate;
        final String finalEndTime = endDate;

        btnQuery.setEnabled(false);
        tvStatus.setText("查询中...");

        executor.execute(() -> {
            List<ShuyunReportApi.ReportItem> result = ShuyunReportApi.getReportList(areaName, finalStartTime, finalEndTime, 1, 100);

            handler.post(() -> {
                btnQuery.setEnabled(true);

                if (result.isEmpty()) {
                    tvStatus.setText("查询完成，暂无数据");
                    tvEmpty.setVisibility(View.VISIBLE);
                    adapter.setData(new ArrayList<>());
                } else {
                    tvStatus.setText("查询完成");
                    tvEmpty.setVisibility(View.GONE);
                    adapter.setData(result);
                }

                tvTotalCount.setText("共 " + result.size() + " 条");
            });
        });
    }

    private final Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTime();
            handler.postDelayed(this, 1000);
        }
    };

    private void updateTime() {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        if (tvCurrentTime != null) {
            tvCurrentTime.setText(time);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(timeRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

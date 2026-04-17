package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
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
 * 功能：展示数运综合上站报表数据
 */
public class ShuyunReportFragment extends Fragment {

    private static final String TAG = "ShuyunReportFragment";

    private TextView tvStatus, tvCurrentTime, tvTotalCount, tvEmpty, tvDebug;
    private Spinner spinnerArea;
    private Button btnQuery;
    private Button btnStartMinus, btnStartPlus, btnEndMinus, btnEndPlus;
    private TextView tvStartDate, tvEndDate;
    private RecyclerView rvReport;

    private ShuyunReportAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private String startDate = "";
    private String endDate = "";

    // 区域列表
    private String[] areaNames = {"温州市"};
    private String[] areaCodes = {"330300"};

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
        tvDebug = view.findViewById(R.id.tvDebug);
        spinnerArea = view.findViewById(R.id.spinnerArea);
        btnQuery = view.findViewById(R.id.btnQuery);
        btnStartMinus = view.findViewById(R.id.btnStartMinus);
        btnStartPlus = view.findViewById(R.id.btnStartPlus);
        btnEndMinus = view.findViewById(R.id.btnEndMinus);
        btnEndPlus = view.findViewById(R.id.btnEndPlus);
        tvStartDate = view.findViewById(R.id.tvStartDate);
        tvEndDate = view.findViewById(R.id.tvEndDate);
        rvReport = view.findViewById(R.id.rvReport);

        // 初始化区域选择器
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                areaNames
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArea.setAdapter(spinnerAdapter);
        spinnerArea.setSelection(0);

        // 初始化日期（默认当月1号到当天）
        Calendar cal = Calendar.getInstance();
        endDate = dateFormat.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1); // 当月1号
        startDate = dateFormat.format(cal.getTime());
        tvStartDate.setText(startDate);
        tvEndDate.setText(endDate);
    }

    private void setupRecyclerView() {
        adapter = new ShuyunReportAdapter();
        rvReport.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReport.setAdapter(adapter);
    }

    private void setupListeners() {
        btnQuery.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                queryData();
            }
        });

        // 开始日期减
        btnStartMinus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(true, -1);
            }
        });

        // 开始日期加
        btnStartPlus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(true, 1);
            }
        });

        // 结束日期减
        btnEndMinus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(false, -1);
            }
        });

        // 结束日期加
        btnEndPlus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustDate(false, 1);
            }
        });
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
        // 检查登录状态
        Session session = Session.get();
        if (session.shuyunPcToken == null || session.shuyunPcToken.isEmpty()) {
            tvStatus.setText("请先在数运监控中登录PC版");
            appendDebug("❌ PC Token为空");
            return;
        }

        int position = spinnerArea.getSelectedItemPosition();
        String areaName = areaNames[position];

        final String finalStartTime = startDate;
        final String finalEndTime = endDate;

        // 调试日志
        String tokenPreview = session.shuyunPcToken.length() > 20 
            ? session.shuyunPcToken.substring(0, 20) + "..." 
            : session.shuyunPcToken;
        appendDebug("📤 请求: " + areaName + " | " + finalStartTime + " ~ " + finalEndTime);
        appendDebug("🔑 Token: " + tokenPreview);

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
                    appendDebug("❌ 返回0条数据");
                } else {
                    tvStatus.setText("查询完成");
                    tvEmpty.setVisibility(View.GONE);
                    adapter.setData(result);
                    appendDebug("✅ 成功 " + result.size() + " 条");
                }

                tvTotalCount.setText("共 " + result.size() + " 条");
            });
        });
    }

    private void appendDebug(String msg) {
        Log.d(TAG, msg);
        if (tvDebug != null) {
            String current = tvDebug.getText().toString();
            String[] lines = current.split("\n");
            if (lines.length > 10) {
                // 保留最后10行
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 10; i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                current = sb.toString();
            }
            tvDebug.setText(current + msg + "\n");
        }
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

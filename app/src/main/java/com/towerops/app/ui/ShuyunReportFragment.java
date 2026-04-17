package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    private TextView tvStatus, tvCurrentTime, tvTotalCount, tvEmpty;
    private Spinner spinnerArea;
    private Button btnQuery;
    private RecyclerView rvReport;

    private ShuyunReportAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        spinnerArea = view.findViewById(R.id.spinnerArea);
        btnQuery = view.findViewById(R.id.btnQuery);
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
    }

    private void queryData() {
        // 检查登录状态
        Session session = Session.get();
        if (session.shuyunPcToken == null || session.shuyunPcToken.isEmpty()) {
            tvStatus.setText("请先在数运监控中登录PC版");
            return;
        }

        int position = spinnerArea.getSelectedItemPosition();
        String areaName = areaNames[position];

        // 设置默认时间范围：当天
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String endTime = sdf.format(new Date());
        String startTime = "2026-04-01"; // 默认从4月开始

        btnQuery.setEnabled(false);
        tvStatus.setText("查询中...");

        executor.execute(() -> {
            List<ShuyunReportApi.ReportItem> result = ShuyunReportApi.getReportList(areaName, startTime, endTime, 1, 100);

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

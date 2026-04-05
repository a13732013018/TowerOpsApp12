package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.towerops.app.R;
import com.towerops.app.api.ProvinceOrderRateApi;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 省内工单处理及时率 Fragment
 * 功能：省内工单处理及时率数据查询与展示
 */
public class ProvinceOrderRateFragment extends Fragment {

    private TextView tvStatus, tvCurrentTime, tvAvgRate, tvEmpty, tvLog;
    private EditText etDataDate;
    private Button btnQuery;
    private RecyclerView rvOrderRate;
    private ScrollView svLog;

    private ProvinceOrderRateAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static ProvinceOrderRateFragment newInstance() {
        return new ProvinceOrderRateFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_province_order_rate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupRecyclerView();
        setupListeners();

        // 设置默认日期为今天
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etDataDate.setText(today);

        // 更新时间
        updateTime();
        handler.postDelayed(timeRunnable, 1000);
    }

    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tvOrderRateStatus);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvAvgRate = view.findViewById(R.id.tvAvgRate);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvLog = view.findViewById(R.id.tvLog);
        etDataDate = view.findViewById(R.id.etDataDate);
        btnQuery = view.findViewById(R.id.btnQuery);
        rvOrderRate = view.findViewById(R.id.rvOrderRate);
        svLog = view.findViewById(R.id.svLog);
    }

    private void setupRecyclerView() {
        adapter = new ProvinceOrderRateAdapter();
        rvOrderRate.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrderRate.setAdapter(adapter);
    }

    private void setupListeners() {
        btnQuery.setOnClickListener(v -> queryData());
    }

    private void queryData() {
        String dataDate = etDataDate.getText().toString().trim();

        if (TextUtils.isEmpty(dataDate)) {
            tvStatus.setText("请输入统计日期");
            return;
        }

        btnQuery.setEnabled(false);
        tvStatus.setText("查询中...");
        appendLog("开始查询省内工单处理及时率...");

        executor.execute(() -> {
            List<ProvinceOrderRateApi.ProvinceOrderRateItem> result =
                    ProvinceOrderRateApi.getProvinceOrderRateList(dataDate);

            handler.post(() -> {
                btnQuery.setEnabled(true);

                if (result.isEmpty()) {
                    tvStatus.setText("查询完成，暂无数据");
                    appendLog("查询完成，暂无数据");
                    tvEmpty.setVisibility(View.VISIBLE);
                    adapter.clear();
                    tvAvgRate.setText("--%");
                } else {
                    tvStatus.setText("查询完成");
                    appendLog("查询到 " + result.size() + " 条工单及时率记录");
                    tvEmpty.setVisibility(View.GONE);
                    adapter.setData(result);

                    // 计算平均及时率
                    double totalRate = 0;
                    int validCount = 0;
                    for (ProvinceOrderRateApi.ProvinceOrderRateItem item : result) {
                        try {
                            String rateStr = item.clRate.replace("%", "");
                            totalRate += Double.parseDouble(rateStr);
                            validCount++;
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    if (validCount > 0) {
                        double avgRate = totalRate / validCount;
                        tvAvgRate.setText(String.format(Locale.getDefault(), "%.1f%%", avgRate));
                    }
                }
            });
        });
    }

    private void appendLog(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logText = "[" + time + "] " + msg + "\n" + (tvLog.getText() != null ? tvLog.getText().toString() : "");
        if (logText.length() > 2000) {
            logText = logText.substring(0, 2000);
        }
        tvLog.setText(logText);
        svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
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

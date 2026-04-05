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
import com.towerops.app.api.DeviceOfflineApi;
import com.towerops.app.model.Session;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备离线 Fragment
 * 功能：综合设备离线数据查询与展示
 */
public class DeviceOfflineFragment extends Fragment {
    private static final String TAG = "DeviceOfflineFragment";

    // 排序字段枚举
    public enum SortField {
        SITE_NAME,
        COUNTY,
        OFFLINE_DEVICE,
        IS_FULL_OFFLINE,
        STAT_TIME
    }

    private TextView tvStatus, tvCurrentTime, tvTotalCount, tvEmpty;
    private Button tvHeaderSiteName, tvHeaderCounty, tvHeaderOfflineDevice, tvHeaderIsFullOffline, tvHeaderStatTime;
    private Spinner spinnerCounty;
    private Button btnQuery;
    private RecyclerView rvDeviceOffline;

    private DeviceOfflineAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 排序状态
    private SortField currentSortField = SortField.SITE_NAME;
    private boolean isSortAscending = false; // 默认降序
    private List<DeviceOfflineApi.DeviceOfflineItem> currentData = new ArrayList<>();

    // 区县列表（平阳县放第一位）
    private String[] countyNames = {"平阳县", "全部", "鹿城区", "龙湾区", "瓯海区", "洞头区", "瑞安市", "乐清市", "永嘉县", "苍南县", "文成县", "泰顺县"};
    private String[] countyCodes = {"330326", "", "330302", "330303", "330304", "330305", "330381", "330382", "330324", "330327", "330328", "330329"};

    public static DeviceOfflineFragment newInstance() {
        return new DeviceOfflineFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_offline, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ★ 确保 Session 数据已恢复（应用重启后 volatile 变量会丢失）
        Session.get().loadConfig(requireContext());

        initViews(view);
        setupSpinner();
        setupRecyclerView();
        setupListeners();

        // 更新时间
        updateTime();
        handler.postDelayed(timeRunnable, 1000);
    }

    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tvDeviceOfflineStatus);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalCount = view.findViewById(R.id.tvTotalCount);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvHeaderSiteName = view.findViewById(R.id.tvHeaderSiteName);
        tvHeaderCounty = view.findViewById(R.id.tvHeaderCounty);
        tvHeaderOfflineDevice = view.findViewById(R.id.tvHeaderOfflineDevice);
        tvHeaderIsFullOffline = view.findViewById(R.id.tvHeaderIsFullOffline);
        tvHeaderStatTime = view.findViewById(R.id.tvHeaderStatTime);
        spinnerCounty = view.findViewById(R.id.spinnerCounty);
        btnQuery = view.findViewById(R.id.btnQuery);
        rvDeviceOffline = view.findViewById(R.id.rvDeviceOffline);
    }

    private void setupSpinner() {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                countyNames
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCounty.setAdapter(spinnerAdapter);
        spinnerCounty.setSelection(0);
    }

    private void setupRecyclerView() {
        adapter = new DeviceOfflineAdapter();
        rvDeviceOffline.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDeviceOffline.setAdapter(adapter);
    }

    private void setupListeners() {
        btnQuery.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                queryData();
            }
        });

        // 表头点击排序
        tvHeaderSiteName.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onSortClick(SortField.SITE_NAME);
            }
        });
        tvHeaderCounty.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onSortClick(SortField.COUNTY);
            }
        });
        tvHeaderOfflineDevice.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onSortClick(SortField.OFFLINE_DEVICE);
            }
        });
        tvHeaderIsFullOffline.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onSortClick(SortField.IS_FULL_OFFLINE);
            }
        });
        tvHeaderStatTime.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onSortClick(SortField.STAT_TIME);
            }
        });
    }

    /**
     * 处理表头点击排序
     */
    private void onSortClick(SortField field) {
        if (currentSortField == field) {
            // 点击同一字段，切换升序/降序
            isSortAscending = !isSortAscending;
        } else {
            // 点击新字段，默认降序
            currentSortField = field;
            isSortAscending = false;
        }
        applySortAndRefresh();
    }

    /**
     * 应用排序并刷新列表
     */
    private void applySortAndRefresh() {
        // 更新表头样式（无论是否有数据）
        updateHeaderStyles();

        if (currentData.isEmpty()) {
            Log.d(TAG, "applySortAndRefresh: 数据为空，跳过排序");
            return;
        }

        Log.d(TAG, "applySortAndRefresh: 数据条数=" + currentData.size() + ", 字段=" + currentSortField + ", 升序=" + isSortAscending);

        // 执行排序
        final boolean ascending = isSortAscending;
        Comparator<DeviceOfflineApi.DeviceOfflineItem> comparator = null;
        switch (currentSortField) {
            case SITE_NAME:
                comparator = new Comparator<DeviceOfflineApi.DeviceOfflineItem>() {
                    @Override
                    public int compare(DeviceOfflineApi.DeviceOfflineItem a, DeviceOfflineApi.DeviceOfflineItem b) {
                        return ascending
                            ? a.siteName.compareToIgnoreCase(b.siteName)
                            : b.siteName.compareToIgnoreCase(a.siteName);
                    }
                };
                break;
            case COUNTY:
                comparator = new Comparator<DeviceOfflineApi.DeviceOfflineItem>() {
                    @Override
                    public int compare(DeviceOfflineApi.DeviceOfflineItem a, DeviceOfflineApi.DeviceOfflineItem b) {
                        String ca = a.county != null ? a.county : "";
                        String cb = b.county != null ? b.county : "";
                        return ascending ? ca.compareToIgnoreCase(cb) : cb.compareToIgnoreCase(ca);
                    }
                };
                break;
            case OFFLINE_DEVICE:
                comparator = new Comparator<DeviceOfflineApi.DeviceOfflineItem>() {
                    @Override
                    public int compare(DeviceOfflineApi.DeviceOfflineItem a, DeviceOfflineApi.DeviceOfflineItem b) {
                        int valA = parseIntSafe(a.offlineDevice);
                        int valB = parseIntSafe(b.offlineDevice);
                        // 如果都是有效数字，按数字排序；否则按字符串排序
                        if (valA != 0 || valB != 0 || (a.offlineDevice != null && a.offlineDevice.matches("\\d+"))
                                || (b.offlineDevice != null && b.offlineDevice.matches("\\d+"))) {
                            if (valA == valB) {
                                return a.siteName.compareToIgnoreCase(b.siteName); // 数字相同按站名排序
                            }
                            return ascending ? valA - valB : valB - valA;
                        }
                        // 都是0或非数字，按字符串排序
                        String sa = a.offlineDevice != null ? a.offlineDevice : "";
                        String sb = b.offlineDevice != null ? b.offlineDevice : "";
                        return ascending ? sa.compareToIgnoreCase(sb) : sb.compareToIgnoreCase(sa);
                    }
                };
                break;
            case IS_FULL_OFFLINE:
                comparator = new Comparator<DeviceOfflineApi.DeviceOfflineItem>() {
                    @Override
                    public int compare(DeviceOfflineApi.DeviceOfflineItem a, DeviceOfflineApi.DeviceOfflineItem b) {
                        String va = a.isFullOffline != null ? a.isFullOffline : "";
                        String vb = b.isFullOffline != null ? b.isFullOffline : "";
                        return ascending ? va.compareTo(vb) : vb.compareTo(va);
                    }
                };
                break;
            case STAT_TIME:
                comparator = new Comparator<DeviceOfflineApi.DeviceOfflineItem>() {
                    @Override
                    public int compare(DeviceOfflineApi.DeviceOfflineItem a, DeviceOfflineApi.DeviceOfflineItem b) {
                        String va = a.statTime != null ? a.statTime : "";
                        String vb = b.statTime != null ? b.statTime : "";
                        return ascending ? va.compareTo(vb) : vb.compareTo(va);
                    }
                };
                break;
        }

        if (comparator != null) {
            Collections.sort(currentData, comparator);
            adapter.setData(new ArrayList<>(currentData));
            Log.d(TAG, "applySortAndRefresh: 排序完成，第一条=" + currentData.get(0).siteName);
        }
    }

    private int parseIntSafe(String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 更新表头样式
     */
    private void updateHeaderStyles() {
        String arrow = isSortAscending ? " ▲" : " ▼";

        // 重置所有表头样式
        tvHeaderSiteName.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeaderCounty.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeaderOfflineDevice.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeaderIsFullOffline.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvHeaderStatTime.setTextColor(getResources().getColor(R.color.text_secondary, null));

        // 设置当前排序字段的样式
        switch (currentSortField) {
            case SITE_NAME:
                tvHeaderSiteName.setText("站点名称" + arrow);
                tvHeaderSiteName.setTextColor(getResources().getColor(R.color.colorPrimary, null));
                break;
            case COUNTY:
                tvHeaderCounty.setText("区县" + arrow);
                tvHeaderCounty.setTextColor(getResources().getColor(R.color.colorPrimary, null));
                break;
            case OFFLINE_DEVICE:
                tvHeaderOfflineDevice.setText("离线设备" + arrow);
                tvHeaderOfflineDevice.setTextColor(getResources().getColor(R.color.colorPrimary, null));
                break;
            case IS_FULL_OFFLINE:
                tvHeaderIsFullOffline.setText("离线状态" + arrow);
                tvHeaderIsFullOffline.setTextColor(getResources().getColor(R.color.colorPrimary, null));
                break;
            case STAT_TIME:
                tvHeaderStatTime.setText("统计时间" + arrow);
                tvHeaderStatTime.setTextColor(getResources().getColor(R.color.colorPrimary, null));
                break;
        }
    }

    private void queryData() {
        int position = spinnerCounty.getSelectedItemPosition();
        String countyId = countyCodes[position];

        btnQuery.setEnabled(false);
        tvStatus.setText("查询中...");

        executor.execute(() -> {
            List<DeviceOfflineApi.DeviceOfflineItem> result = DeviceOfflineApi.getDeviceOfflineList(countyId, 50, 1);

            handler.post(() -> {
                btnQuery.setEnabled(true);

                if (result.isEmpty()) {
                    tvStatus.setText("查询完成，暂无数据");
                    tvEmpty.setVisibility(View.VISIBLE);
                    adapter.clear();
                    currentData.clear();
                } else {
                    tvStatus.setText("查询完成");
                    tvEmpty.setVisibility(View.GONE);
                    currentData = new ArrayList<>(result);
                    applySortAndRefresh();
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

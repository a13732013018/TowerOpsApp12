package com.towerops.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;
import com.towerops.app.model.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 任务工单 Fragment
 *
 * 对应易语言 子程序_省内考核工单查询
 * 接口：/api/flowable/flowable/task/listToOrder
 *
 * 8个过滤选项完全对应易语言8个选择框（10/18/13/14/15/16/17/22）
 * 排序：点击列表上方 站名 | 工单类型 | 要求完成 | 接单人 四个表头列切换排序
 */
public class KaoHeOrderFragment extends Fragment {

    // ── 行政区划 ──────────────────────────────────────────────────────
    private static final String[] CITY_AREA_NAMES = {
        "全部", "平阳县", "泰顺县", "瓯海区", "苍南县", "文成县",
        "瑞安市", "乐清市", "鹿城区", "龙湾区", "洞头区", "永嘉县", "龙港市"
    };
    private static final String[] CITY_AREA_CODES = {
        "", "330326", "330329", "330304", "330327", "330328",
        "330381", "330382", "330302", "330303", "330305", "330324", "330383"
    };

    // ── 分组常量 ──────────────────────────────────────────────────────
    private static final String[][] STATION_GROUP_RULES = {
        {"卢智伟",  "卢智伟、杨桂"},
        {"杨桂",    "卢智伟、杨桂"},
        {"高树调",  "高树调、倪传井"},
        {"倪传井",  "高树调、倪传井"},
        {"苏忠前",  "苏忠前、许方喜"},
        {"许方喜",  "苏忠前、许方喜"},
        {"黄经兴",  "黄经兴、蔡亮"},
        {"蔡亮",    "黄经兴、蔡亮"},
        {"陈德岳",  "陈德岳"},
    };

    // ── 排序列常量 ──────────────────────────────────────────────────
    private static final int SORT_STATION   = 0;
    private static final int SORT_FLOW_NAME = 1;
    private static final int SORT_REQ_TIME  = 2;
    private static final int SORT_ACCESTAFF = 3;

    // ── UI ──────────────────────────────────────────────────────────
    private Spinner  spinnerCounty;
    private CheckBox cbFilter10, cbFilter18, cbFilter17;
    private CheckBox cbFilter13, cbFilter14, cbFilter15, cbFilter16, cbFilter22;
    private Button   btnQuery;
    private TextView tvStatus;
    private RecyclerView rvOrders;
    private KaoHeOrderAdapter adapter;

    // 排序表头
    private TextView tvSortStation, tvSortFlowName, tvSortReqTime, tvSortAccestaff;
    private final TextView[] sortHeaders = new TextView[4]; // 方便统一重置

    // ── 排序状态 ─────────────────────────────────────────────────────
    private int     sortFieldIndex = SORT_STATION; // 当前排序列
    private boolean sortAscending  = true;          // true=↑正序 false=↓反序

    // 各列基础文本（不含箭头）
    private static final String[] SORT_LABELS = {"站名", "工单类型", "要求完成", "接单人"};

    // 当前完整列表（排序用，无需重新请求）
    private List<ShuyunApi.KaoHeOrderInfo> currentFullList = new ArrayList<>();

    private int     selectedCountyIndex = 0;
    private volatile boolean isQuerying = false;
    private boolean updatingFilter18 = false;

    private final Handler       mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor   = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kaohe_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ★ 确保 Session 数据已恢复（应用重启后 volatile 变量会丢失）
        Session.get().loadConfig(requireContext());

        initViews(view);
        setupSpinner();
        setupListeners();
        updateSortHeaders(); // 初始化表头样式
    }

    private void initViews(View view) {
        spinnerCounty = view.findViewById(R.id.spinnerKHCounty);
        cbFilter10    = view.findViewById(R.id.cbKHFilter10);
        cbFilter18    = view.findViewById(R.id.cbKHFilter18);
        cbFilter17    = view.findViewById(R.id.cbKHFilter17);
        cbFilter13    = view.findViewById(R.id.cbKHFilter13);
        cbFilter14    = view.findViewById(R.id.cbKHFilter14);
        cbFilter15    = view.findViewById(R.id.cbKHFilter15);
        cbFilter16    = view.findViewById(R.id.cbKHFilter16);
        cbFilter22    = view.findViewById(R.id.cbKHFilter22);
        btnQuery      = view.findViewById(R.id.btnKHQuery);
        tvStatus      = view.findViewById(R.id.tvKHStatus);
        rvOrders      = view.findViewById(R.id.rvKaoHeOrders);

        tvSortStation   = view.findViewById(R.id.tvKHSortStation);
        tvSortFlowName  = view.findViewById(R.id.tvKHSortFlowName);
        tvSortReqTime   = view.findViewById(R.id.tvKHSortReqTime);
        tvSortAccestaff = view.findViewById(R.id.tvKHSortAccestaff);

        sortHeaders[SORT_STATION]   = tvSortStation;
        sortHeaders[SORT_FLOW_NAME] = tvSortFlowName;
        sortHeaders[SORT_REQ_TIME]  = tvSortReqTime;
        sortHeaders[SORT_ACCESTAFF] = tvSortAccestaff;

        adapter = new KaoHeOrderAdapter();
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(adapter);
    }

    private void setupSpinner() {
        ArrayAdapter<String> countyAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, CITY_AREA_NAMES);
        countyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCounty.setAdapter(countyAdapter);
        // 默认选中平阳县（index=1）
        spinnerCounty.setSelection(1);
        selectedCountyIndex = 1;
    }

    private void setupListeners() {
        spinnerCounty.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                selectedCountyIndex = pos;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // ── 排序表头点击 ─────────────────────────────────────────────
        // 点同一列：切换正/反序；点不同列：切换列并重置为正序
        View.OnClickListener sortClick = v -> {
            int newField;
            if (v.getId() == R.id.tvKHSortStation)    newField = SORT_STATION;
            else if (v.getId() == R.id.tvKHSortFlowName) newField = SORT_FLOW_NAME;
            else if (v.getId() == R.id.tvKHSortReqTime)  newField = SORT_REQ_TIME;
            else                                          newField = SORT_ACCESTAFF;

            if (newField == sortFieldIndex) {
                sortAscending = !sortAscending; // 同列切换正/反
            } else {
                sortFieldIndex = newField;
                sortAscending  = true;          // 换列默认正序
            }
            updateSortHeaders();
            applySortAndRefresh();
        };
        tvSortStation.setOnClickListener(sortClick);
        tvSortFlowName.setOnClickListener(sortClick);
        tvSortReqTime.setOnClickListener(sortClick);
        tvSortAccestaff.setOnClickListener(sortClick);

        // ── cb18：全不选/全选特殊类型 ───────────────────────────────
        cbFilter18.setOnCheckedChangeListener((btn, checked) -> {
            if (updatingFilter18) return;
            updatingFilter18 = true;
            cbFilter13.setChecked(!checked);
            cbFilter14.setChecked(!checked);
            cbFilter15.setChecked(!checked);
            cbFilter16.setChecked(!checked);
            cbFilter22.setChecked(!checked);
            updatingFilter18 = false;
        });

        android.widget.CompoundButton.OnCheckedChangeListener subListener = (btn, checked) -> {
            if (updatingFilter18) return;
            boolean allHidden = !cbFilter13.isChecked() && !cbFilter14.isChecked()
                    && !cbFilter15.isChecked() && !cbFilter16.isChecked() && !cbFilter22.isChecked();
            updatingFilter18 = true;
            cbFilter18.setChecked(allHidden);
            updatingFilter18 = false;
        };
        cbFilter13.setOnCheckedChangeListener(subListener);
        cbFilter14.setOnCheckedChangeListener(subListener);
        cbFilter15.setOnCheckedChangeListener(subListener);
        cbFilter16.setOnCheckedChangeListener(subListener);
        cbFilter22.setOnCheckedChangeListener(subListener);

        btnQuery.setOnClickListener(v -> startQuery());
    }

    // ── 更新排序表头样式（当前列高亮+箭头，其他列灰色） ──────────────
    private void updateSortHeaders() {
        for (int i = 0; i < sortHeaders.length; i++) {
            TextView tv = sortHeaders[i];
            if (tv == null) continue;
            if (i == sortFieldIndex) {
                // 当前排序列：深蓝加粗 + 箭头
                String arrow = sortAscending ? " ↑" : " ↓";
                tv.setText(SORT_LABELS[i] + arrow);
                tv.setTextColor(Color.parseColor("#1D4ED8"));
                tv.setTypeface(null, Typeface.BOLD);
                tv.setBackgroundColor(Color.parseColor("#D6E4FF"));
            } else {
                // 其他列：灰色普通
                tv.setText(SORT_LABELS[i] + " ↕");
                tv.setTextColor(Color.parseColor("#2563EB"));
                tv.setTypeface(null, Typeface.NORMAL);
                tv.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    // ── 对当前列表排序并刷新 ──────────────────────────────────────────
    private void applySortAndRefresh() {
        if (currentFullList == null || currentFullList.isEmpty()) return;
        List<ShuyunApi.KaoHeOrderInfo> sorted = new ArrayList<>(currentFullList);
        Collections.sort(sorted, (a, b) -> {
            String va, vb;
            switch (sortFieldIndex) {
                case SORT_STATION:
                    va = a.stationName   != null ? a.stationName   : "";
                    vb = b.stationName   != null ? b.stationName   : "";
                    break;
                case SORT_FLOW_NAME:
                    va = a.flowName      != null ? a.flowName      : "";
                    vb = b.flowName      != null ? b.flowName      : "";
                    break;
                case SORT_REQ_TIME:
                    va = a.reqCompTime   != null ? a.reqCompTime   : "";
                    vb = b.reqCompTime   != null ? b.reqCompTime   : "";
                    break;
                case SORT_ACCESTAFF:
                    va = a.accestaff     != null ? a.accestaff     : "";
                    vb = b.accestaff     != null ? b.accestaff     : "";
                    break;
                default:
                    va = ""; vb = "";
            }
            int cmp = va.compareTo(vb);
            return sortAscending ? cmp : -cmp;
        });
        // 重新编号
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).index = String.valueOf(i + 1);
        }
        adapter.setData(sorted);
    }

    // ── 查询 ─────────────────────────────────────────────────────────
    private void startQuery() {
        if (isQuerying) {
            Toast.makeText(getContext(), "查询中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        Session s = Session.get();
        String pcToken = s.shuyunPcToken;
        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先登录数运PC端", Toast.LENGTH_SHORT).show();
            return;
        }
        String cookieToken = (s.shuyunPcTokenCookie != null && !s.shuyunPcTokenCookie.isEmpty())
                ? s.shuyunPcTokenCookie : pcToken;

        String cityArea = CITY_AREA_CODES[selectedCountyIndex];

        boolean f10 = cbFilter10.isChecked();
        boolean f18 = cbFilter18.isChecked();
        boolean f17 = cbFilter17.isChecked();
        boolean f13 = cbFilter13.isChecked();
        boolean f14 = cbFilter14.isChecked();
        boolean f15 = cbFilter15.isChecked();
        boolean f16 = cbFilter16.isChecked();
        boolean f22 = cbFilter22.isChecked();

        isQuerying = true;
        btnQuery.setEnabled(false);
        btnQuery.setText("查询中...");
        currentFullList = new ArrayList<>();
        adapter.setData(new ArrayList<>());
        tvStatus.setText("查询中...");

        executor.execute(() -> {
            try {
                String json = ShuyunApi.getKaoHeOrderList(pcToken, cookieToken, cityArea);
                android.util.Log.d("KaoHeOrder", "raw: " + (json.length() > 300 ? json.substring(0, 300) : json));
                List<ShuyunApi.KaoHeOrderInfo> list = ShuyunApi.parseKaoHeOrderList(
                        json, f10, f18, f13, f14, f15, f16, f17, f22,
                        STATION_GROUP_RULES);

                mainHandler.post(() -> {
                    currentFullList = new ArrayList<>(list);
                    applySortAndRefresh();
                    tvStatus.setText("查询完成，共 " + list.size() + " 条任务工单"
                            + (selectedCountyIndex > 0 ? " · " + CITY_AREA_NAMES[selectedCountyIndex] : ""));
                    btnQuery.setEnabled(true);
                    btnQuery.setText("查询");
                    isQuerying = false;
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText("查询失败：" + e.getMessage());
                    btnQuery.setEnabled(true);
                    btnQuery.setText("查询");
                    isQuerying = false;
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }
}

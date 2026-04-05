package com.towerops.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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

import com.google.android.material.tabs.TabLayout;
import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;
import com.towerops.app.api.ProvinceOrderRateApi;
import com.towerops.app.model.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 指标查询 Tab
 * Tab[0] = 指标排名（综合排名视图）
 * Tab[1]~[7] = 电子化覆盖率/资产一致性/PUE有效率/FSU离线率/故障工单合格率/疑似退服/超频告警整治
 */
public class MetricsFragment extends Fragment {

    // ── Tab名称（第0个为排名，1~8为具体指标） ────────────────────────
    private static final String[] TAB_NAMES = {
        "指标排名",
        "电子化覆盖率", "资产一致性", "PUE有效率",
        "FSU离线率", "故障工单合格率", "疑似退服", "超频告警整治", "省内工单处理及时率"
    };

    // 具体指标Tab数量（不含排名Tab）
    private static final int METRIC_COUNT = 8;

    private EditText etDate;
    private TextView tvStatus;
    private TabLayout tabMetrics;
    private LinearLayout llHeader;
    private LinearLayout llRows;

    private int currentTab = 0;

    // tabCache[0] 不用（排名Tab从其他缓存实时计算）
    // tabCache[1..8] 缓存8个指标的原始JSON
    private final String[] tabCache = new String[9];

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    // ── 各指标表头 ────────────────────────────────────────────────────
    private static final String[][] HEADERS = {
        // [0] 排名Tab——表头在代码里动态生成
        {},
        // [1] 电子化覆盖率
        {"序号", "区县", "站址总数", "覆盖数", "覆盖率(%)", "日期"},
        // [2] 资产一致性
        {"地市", "开关-分子", "开关-分母", "开关-一致性",
         "空调-分子", "空调-分母", "空调-一致性",
         "铅酸-分子", "铅酸-分母", "铅酸-一致性",
         "普通锂-分子", "普通锂-分母", "普通锂-一致性",
         "智能锂-分子", "智能锂-分母", "智能锂-一致性",
         "蓄电池一致性", "总资产一致性", "日期"},
        // [3] PUE有效率
        {"序号", "区县", "纳管站址", "有效站址", "有效率(%)", "达标站址", "达标率(%)",
         "I级", "II级", "III级", "IIII级", "日期"},
        // [4] FSU离线率
        {"序号", "区县", "FSU离线率", "交维站址数", "派单总数", "当天超10次", "统计时间"},
        // [5] 故障工单合格率
        {"序号", "区县", "总工单数", "故障工单数", "接单超时", "回单超时", "接单及时率", "回单及时率", "质检合格率", "工单合格率", "统计时间"},
        // [6] 疑似退服
        {"序号", "区县", "统计时间", "疑似退服次数", "一级低压脱离次数", "疑似退服率"},
        // [7] 超频告警整治
        {"序号", "区县", "统计时间", "总工单数", "本月新派发", "遗留工单", "处理及时率", "无效工单", "超时工单", "处理有效率"},
        // [8] 省内工单处理及时率
        {"序号", "区县", "统计时间", "当月派发工单", "未归档工单", "当月归档工单", "当月归档超时工单", "未归档超时工单", "处理及时率"},
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_metrics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etDate     = view.findViewById(R.id.etMetricsDate);
        tvStatus   = view.findViewById(R.id.tvMetricsStatus);
        tabMetrics = view.findViewById(R.id.tabMetrics);
        llHeader   = view.findViewById(R.id.llMetricsHeader);
        llRows     = view.findViewById(R.id.llMetricsRows);

        Button btnQuery     = view.findViewById(R.id.btnMetricsQuery);
        Button btnQueryAll  = view.findViewById(R.id.btnMetricsQueryAll);
        Button btnDateMinus = view.findViewById(R.id.btnDateMinus);
        Button btnDatePlus  = view.findViewById(R.id.btnDatePlus);

        // 默认填入今天
        String defaultDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etDate.setText(defaultDate);

        btnDateMinus.setOnClickListener(v -> shiftDate(-1));
        btnDatePlus.setOnClickListener(v  -> shiftDate(+1));

        // 初始化所有Tab
        for (String name : TAB_NAMES) {
            tabMetrics.addTab(tabMetrics.newTab().setText(name));
        }

        tabMetrics.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                clearTable();
                if (currentTab == 0) {
                    // 排名Tab：如果7个指标都有缓存则直接渲染
                    if (allMetricsCached()) {
                        renderRankingTab();
                    } else {
                        tvStatus.setText("请先点击「查全部」以获取排名数据");
                    }
                } else {
                    if (tabCache[currentTab] != null && !tabCache[currentTab].isEmpty()) {
                        renderTable(currentTab, tabCache[currentTab]);
                    } else {
                        tvStatus.setText("已切换到【" + TAB_NAMES[currentTab] + "】，点击查询");
                    }
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnQuery.setOnClickListener(v -> queryCurrentTab());
        btnQueryAll.setOnClickListener(v -> queryAllTabs());
    }

    // ─── 检查7个指标是否都有缓存 ────────────────────────────────────
    private boolean allMetricsCached() {
        for (int i = 1; i <= METRIC_COUNT; i++) {
            if (tabCache[i] == null || tabCache[i].isEmpty()) return false;
        }
        return true;
    }

    // ─── 查询当前Tab ────────────────────────────────────────────────
    private void queryCurrentTab() {
        if (currentTab == 0) {
            // 排名Tab 直接触发查全部
            queryAllTabs();
            return;
        }
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            Toast.makeText(requireContext(), "请输入查询日期", Toast.LENGTH_SHORT).show();
            return;
        }
        Session s = Session.get();
        String pcToken = s.shuyunPcToken;
        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(requireContext(), "请先登录数运账号", Toast.LENGTH_SHORT).show();
            return;
        }
        String cookieToken = (s.shuyunPcTokenCookie != null && !s.shuyunPcTokenCookie.isEmpty())
                ? s.shuyunPcTokenCookie : pcToken;
        setStatus("查询中...");
        final int tabIdx = currentTab;
        final String fToken = pcToken, fCookie = cookieToken;
        executor.execute(() -> {
            String json = fetchByTab(tabIdx, fToken, fCookie, date);
            mainHandler.post(() -> {
                tabCache[tabIdx] = json;
                renderTable(tabIdx, json);
            });
        });
    }

    // ─── 查询全部（7个指标并发，完成后刷新排名Tab） ─────────────────
    private void queryAllTabs() {
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            Toast.makeText(requireContext(), "请输入查询日期", Toast.LENGTH_SHORT).show();
            return;
        }
        Session s = Session.get();
        String pcToken = s.shuyunPcToken;
        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(requireContext(), "请先登录数运账号", Toast.LENGTH_SHORT).show();
            return;
        }
        String cookieToken = (s.shuyunPcTokenCookie != null && !s.shuyunPcTokenCookie.isEmpty())
                ? s.shuyunPcTokenCookie : pcToken;

        // 清空旧缓存（只清指标，不清排名）
        for (int i = 1; i <= METRIC_COUNT; i++) tabCache[i] = null;
        final int[] doneCount = {0};
        setStatus("正在查询全部指标（0/" + METRIC_COUNT + "）...");

        for (int i = 1; i <= METRIC_COUNT; i++) {
            final int tabIdx = i;
            final String fToken = pcToken, fCookie = cookieToken;
            executor.execute(() -> {
                String json = fetchByTab(tabIdx, fToken, fCookie, date);
                mainHandler.post(() -> {
                    tabCache[tabIdx] = json;
                    doneCount[0]++;
                    setStatus("正在查询全部指标（" + doneCount[0] + "/" + METRIC_COUNT + "）...");
                    // 当前展示的Tab完成时立刻刷新显示
                    if (tabIdx == currentTab) {
                        renderTable(tabIdx, json);
                    }
                    // 全部完成后刷新排名Tab
                    if (doneCount[0] == METRIC_COUNT) {
                        setStatus("全部查询完成，切换Tab可查看各指标数据");
                        // 如果当前停在排名Tab，立刻渲染
                        if (currentTab == 0) {
                            renderRankingTab();
                        }
                    }
                });
            });
        }
    }

    // ─── 根据Tab索引调用对应API（tabIdx 1~8） ───────────────────────
    private String fetchByTab(int tabIdx, String pcToken, String cookieToken, String date) {
        try {
            switch (tabIdx) {
                case 1: return ShuyunApi.queryEleCoverRate(pcToken, cookieToken, date);
                case 2: return ShuyunApi.queryAssetConsistency(pcToken, cookieToken, date);
                case 3: return ShuyunApi.queryPueRate(pcToken, cookieToken, date);
                case 4: return ShuyunApi.queryFsuOfflineRate(pcToken, cookieToken, date);
                case 5: return ShuyunApi.queryFaultOrderQuality(pcToken, cookieToken, date);
                case 6: return ShuyunApi.querySuspectedOutOfService(pcToken, cookieToken, date);
                case 7: return ShuyunApi.queryAlarmEffectiveness(pcToken, cookieToken, date);
                case 8: return ProvinceOrderRateApi.queryProvinceOrderRate(pcToken, cookieToken, date);  // 省内工单处理及时率
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  指标排名 Tab 渲染
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 单条排名记录
     */
    private static class RankEntry {
        String area;        // 区县名
        String metricType;  // 指标类型
        String metricValue; // 指标情况（原始值）
        double numValue;    // 用于排序的数值
        int rank;           // 最终排名
    }

    /**
     * 渲染排名Tab：从8个缓存JSON里提取各区县指标，统一排名后展示
     * 格式：区县 | 指标类型 | 指标情况 | 排名
     */
    private void renderRankingTab() {
        clearTable();
        List<RankEntry> allEntries = new ArrayList<>();

        // ① 电子化覆盖率（Tab1）越大越靠前 → 升序倒排
        allEntries.addAll(extractRanking(
            tabCache[1], "电子化覆盖率",
            new String[]{"AREA_NAME"}, "ELE_COVER_RATE",
            true  // true = 越大排名越靠前（升序后取rank = 从大到小）
        ));

        // ② 资产一致性（Tab2）越大越靠前 → PROPERTY_RATE
        allEntries.addAll(extractRanking(
            tabCache[2], "资产一致性",
            new String[]{"AREA_NAME"}, "PROPERTY_RATE",
            true
        ));

        // ③ PUE有效率（Tab3）越大越靠前 → YX_RATE
        allEntries.addAll(extractRanking(
            tabCache[3], "PUE有效率",
            new String[]{"LAT_NAME", "AREA_NAME"}, "YX_RATE",
            true
        ));

        // ④ FSU离线率（Tab4）越大排名越靠后 → AVG_OFFLINE_RATE（越小越好）
        allEntries.addAll(extractRanking(
            tabCache[4], "FSU离线率",
            new String[]{"CITY_NAME", "AREA_NAME"}, "AVG_OFFLINE_RATE",
            false // false = 越小排名越靠前
        ));

        // ⑤ 故障工单合格率（Tab5）越大越靠前 → SHEET_MAKE_RATE
        allEntries.addAll(extractRanking(
            tabCache[5], "故障工单合格率",
            new String[]{"AREA_NAME", "CITY_NAME"}, "SHEET_MAKE_RATE",
            true
        ));

        // ⑥ 疑似退服（Tab6）越大排名越靠后 → YSTF_RATE（越小越好）
        allEntries.addAll(extractRanking(
            tabCache[6], "疑似退服",
            new String[]{"CITY_NAME", "AREA_NAME"}, "YSTF_RATE",
            false
        ));

        // ⑦ 超频告警整治（Tab7）越大越靠前 → SHEET_CL_RATE
        allEntries.addAll(extractRanking(
            tabCache[7], "超频告警整治",
            new String[]{"AREA_NAME", "CITY_NAME"}, "SHEET_CL_RATE",
            true
        ));

        // ⑧ 省内工单处理及时率（Tab8）越大越靠前 → CL_RATE
        allEntries.addAll(extractRanking(
            tabCache[8], "省内工单处理及时率",
            new String[]{"CITY_NAME", "AREA_NAME"}, "CL_RATE",
            true
        ));

        if (allEntries.isEmpty()) {
            setStatus("排名数据为空，请确认各指标已查询成功");
            return;
        }

        // 渲染表头
        String[] header = {"区县", "指标类型", "指标情况", "排名"};
        addRankRow(llHeader, header, true);

        // 按指标类型分组展示，同一指标内按排名顺序
        // 先收集所有出现过的指标类型（保序）
        List<String> metricOrder = new ArrayList<>();
        for (RankEntry e : allEntries) {
            if (!metricOrder.contains(e.metricType)) metricOrder.add(e.metricType);
        }

        int rowIdx = 0;
        for (String metric : metricOrder) {
            // 同指标内按rank排序
            List<RankEntry> group = new ArrayList<>();
            for (RankEntry e : allEntries) {
                if (e.metricType.equals(metric)) group.add(e);
            }
            // 已经在 extractRanking 里排好了，此处保持原顺序

            // 分组标题行（浅蓝背景）
            LinearLayout titleRow = new LinearLayout(requireContext());
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setBackgroundColor(Color.parseColor("#D6E4FF"));
            TextView tvTitle = new TextView(requireContext());
            LinearLayout.LayoutParams lpFull = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tvTitle.setLayoutParams(lpFull);
            tvTitle.setText("── " + metric + " ──");
            tvTitle.setTextSize(9.5f);
            tvTitle.setTextColor(Color.parseColor("#2563EB"));
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setGravity(Gravity.CENTER);
            tvTitle.setPadding(4, 6, 4, 6);
            titleRow.addView(tvTitle);
            llRows.addView(titleRow);

            for (RankEntry e : group) {
                String rankStr = "第 " + e.rank + " 名";
                String[] cells = {e.area, e.metricType, e.metricValue, rankStr};
                LinearLayout rowView = new LinearLayout(requireContext());
                rowView.setOrientation(LinearLayout.HORIZONTAL);

                boolean isPingyang = e.area != null && e.area.contains("平阳");

                // 平阳县高亮（浅橙/暖黄）优先，否则按名次/交替色
                int bgColor;
                if (isPingyang)           bgColor = Color.parseColor("#FFF3CD"); // 平阳专属高亮
                else if (e.rank == 1)     bgColor = Color.parseColor("#FFF7E0"); // 金
                else if (e.rank == 2)     bgColor = Color.parseColor("#F5F5F5"); // 银
                else if (e.rank == 3)     bgColor = Color.parseColor("#FFF0E0"); // 铜
                else if (rowIdx % 2 == 0) bgColor = Color.parseColor("#FFFFFF");
                else                      bgColor = Color.parseColor("#F0F4FF");
                rowView.setBackgroundColor(bgColor);
                addRankRow(rowView, cells, false, isPingyang);
                llRows.addView(rowView);
                rowIdx++;
            }
        }

        int total = allEntries.size();
        setStatus("指标排名 · 共 " + total + " 条（" + metricOrder.size() + " 项指标）");
    }

    /**
     * 从单个指标的JSON缓存中提取数据，计算排名
     *
     * @param jsonStr     该指标的原始JSON
     * @param metricName  指标名称
     * @param areaKeys    区县字段名备选列表（按优先级）
     * @param valueKey    排名依据字段名
     * @param biggerIsBetter true=越大排名越靠前；false=越小排名越靠前
     */
    private List<RankEntry> extractRanking(String jsonStr, String metricName,
            String[] areaKeys, String valueKey, boolean biggerIsBetter) {
        List<RankEntry> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                JSONObject dataObj = root.optJSONObject("data");
                if (dataObj != null) data = dataObj.optJSONArray("rows");
            }
            if (data == null) data = root.optJSONArray("rows");
            if (data == null || data.length() == 0) return list;

            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.getJSONObject(i);

                // 找区县名
                String area = "";
                for (String k : areaKeys) {
                    String v = row.optString(k, "");
                    if (!v.isEmpty()) { area = v; break; }
                }
                if (area.isEmpty()) area = "未知";

                // 指标值
                String rawVal = row.optString(valueKey, "");
                double num = parseDouble(rawVal);

                RankEntry e = new RankEntry();
                e.area = area;
                e.metricType = metricName;
                e.metricValue = rawVal.isEmpty() ? "-" : rawVal;
                e.numValue = num;
                list.add(e);
            }

            // 按数值排序（越大靠前 or 越小靠前）
            if (biggerIsBetter) {
                // 降序：大→小
                Collections.sort(list, (a, b) -> Double.compare(b.numValue, a.numValue));
            } else {
                // 升序：小→大
                Collections.sort(list, (a, b) -> Double.compare(a.numValue, b.numValue));
            }

            // 赋排名（相同值同名次）
            int rank = 1;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0 && list.get(i).numValue != list.get(i - 1).numValue) {
                    rank = i + 1;
                }
                list.get(i).rank = rank;
            }

        } catch (Exception e) {
            android.util.Log.e("MetricsFragment", "[排名] " + metricName + " 解析失败: " + e.getMessage());
        }
        return list;
    }

    /** 安全解析数字，去掉 % 符号后转 double */
    private double parseDouble(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ─── 排名Tab专用的行添加方法（4列固定宽度） ────────────────────
    // isPingyang=true 时区县列加粗+橙色，其余列也加粗以突出
    private void addRankRow(LinearLayout parent, String[] cells, boolean isHeader) {
        addRankRow(parent, cells, isHeader, false);
    }

    private void addRankRow(LinearLayout parent, String[] cells, boolean isHeader, boolean isPingyang) {
        float density = getResources().getDisplayMetrics().density;
        // 列宽：区县80 | 指标类型90 | 指标情况80 | 排名60
        int[] colWidthDp = {80, 90, 80, 60};

        for (int c = 0; c < cells.length; c++) {
            int w = (int)(colWidthDp[c] * density);
            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            tv.setText(cells[c]);
            tv.setTextSize(isHeader ? 9.5f : (isPingyang ? 9.5f : 9f));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(2, 5, 2, 5);
            tv.setSingleLine(false);
            tv.setMaxLines(2);
            tv.setIncludeFontPadding(false);
            if (isHeader) {
                tv.setTextColor(Color.parseColor("#2563EB"));
                tv.setTypeface(null, Typeface.BOLD);
                tv.setBackgroundColor(Color.parseColor("#EEF2FF"));
            } else if (isPingyang) {
                // 平阳县：全行加粗，区县列橙色，其他列深橙
                if (c == 0) {
                    tv.setTextColor(Color.parseColor("#E65100")); // 深橙色区县名
                    tv.setTypeface(null, Typeface.BOLD);
                } else {
                    tv.setTextColor(Color.parseColor("#5D3200")); // 深棕色其他列
                    tv.setTypeface(null, Typeface.BOLD);
                }
            } else {
                tv.setTextColor(Color.parseColor("#1a1a2e"));
                tv.setTypeface(null, Typeface.NORMAL);
            }

            // 排名列：第1/2/3名特殊颜色（平阳县时也生效，用更鲜明颜色）
            if (!isHeader && c == 3) {
                String rankText = cells[c];
                if (rankText.contains("第 1 名"))
                    tv.setTextColor(isPingyang ? Color.parseColor("#FF8F00") : Color.parseColor("#D4A017"));
                else if (rankText.contains("第 2 名"))
                    tv.setTextColor(isPingyang ? Color.parseColor("#616161") : Color.parseColor("#808080"));
                else if (rankText.contains("第 3 名"))
                    tv.setTextColor(isPingyang ? Color.parseColor("#A0522D") : Color.parseColor("#B87333"));
            }

            if (c < cells.length - 1) {
                LinearLayout cell = new LinearLayout(requireContext());
                cell.setLayoutParams(new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
                cell.setOrientation(LinearLayout.HORIZONTAL);
                cell.addView(tv);
                View divider = new View(requireContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(Color.parseColor("#DDEAFF"));
                cell.addView(divider);
                parent.addView(cell);
            } else {
                parent.addView(tv);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  普通指标Tab渲染（Tab 1~7，原逻辑不变）
    // ═══════════════════════════════════════════════════════════════════

    private void renderTable(int tabIdx, String jsonStr) {
        clearTable();
        if (jsonStr == null || jsonStr.isEmpty()) {
            setStatus("查询失败：网络异常或无数据");
            return;
        }
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                JSONObject dataObj = root.optJSONObject("data");
                if (dataObj != null) data = dataObj.optJSONArray("rows");
            }
            if (data == null) data = root.optJSONArray("rows");

            if (data == null || data.length() == 0) {
                String raw = jsonStr.length() > 300 ? jsonStr.substring(0, 300) + "..." : jsonStr;
                android.util.Log.w("MetricsFragment", "[" + TAB_NAMES[tabIdx] + "] raw: " + raw);
                setStatus("无数据（" + TAB_NAMES[tabIdx] + "）\n返回：" + raw);
                return;
            }

            JSONObject firstRow = data.getJSONObject(0);
            android.util.Log.d("MetricsFragment", "[" + TAB_NAMES[tabIdx] + "] keys: " + firstRow.toString());

            String[] cells0 = buildCells(tabIdx, firstRow, 1);
            boolean allEmpty = true;
            for (int c = 1; c < cells0.length; c++) {
                if (!cells0[c].isEmpty()) { allEmpty = false; break; }
            }

            if (allEmpty) {
                android.util.Log.w("MetricsFragment", "[" + TAB_NAMES[tabIdx] + "] fixed fields all empty, fallback to dynamic");
                renderDynamic(tabIdx, data);
                setStatus(TAB_NAMES[tabIdx] + " · " + data.length() + " 条（动态渲染，请确认字段）");
            } else {
                String[] hdrs = HEADERS[tabIdx];
                addRow(llHeader, hdrs, true, tabIdx);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject row = data.getJSONObject(i);
                    String[] cells = buildCells(tabIdx, row, i + 1);
                    LinearLayout rowView = new LinearLayout(requireContext());
                    rowView.setOrientation(LinearLayout.HORIZONTAL);
                    rowView.setBackgroundColor(i % 2 == 0 ? Color.parseColor("#FFFFFF") : Color.parseColor("#F0F4FF"));
                    addRow(rowView, cells, false, tabIdx);
                    llRows.addView(rowView);
                }
                setStatus(TAB_NAMES[tabIdx] + " · 共 " + data.length() + " 条");
            }

        } catch (Exception e) {
            setStatus("解析失败：" + e.getMessage());
            android.util.Log.e("MetricsFragment", "parse error", e);
        }
    }

    /** 常见英文字段名 → 中文映射 */
    private static Map<String, String> buildKeyMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("AREA_NAME",        "区县");
        m.put("LAT_NAME",         "区县");
        m.put("DISTRICT_NAME",    "区县");
        m.put("REGION_NAME",      "区县");
        m.put("DATA_DATE",        "日期");
        m.put("MONTH",            "月份");
        m.put("FSU_TOTAL",        "FSU总数");
        m.put("FSU_OFFLINE",      "离线数");
        m.put("FSU_OFFLINE_RATE", "离线率(%)");
        m.put("ONLINE_RATE",      "在线率(%)");
        m.put("OFFLINE_COUNT",    "离线数量");
        m.put("TOTAL_COUNT",      "总数量");
        m.put("RATE",             "比率(%)");
        m.put("ONLINE_COUNT",     "在线数量");
        m.put("ORDER_TOTAL",      "工单总数");
        m.put("PASS_COUNT",       "合格数");
        m.put("PASS_RATE",        "合格率(%)");
        m.put("FAIL_COUNT",       "不合格数");
        m.put("FAIL_RATE",        "不合格率(%)");
        m.put("ORDER_COUNT",      "工单数");
        m.put("QUALIFIED_COUNT",  "合格数");
        m.put("QUALIFIED_RATE",   "合格率(%)");
        m.put("UNQUALIFIED_COUNT","不合格数");
        m.put("FINISH_COUNT",     "完成数");
        m.put("FINISH_RATE",      "完成率(%)");
        m.put("SITE_COUNT",       "站址总数");
        m.put("TUIFU_COUNT",      "疑似退服数");
        m.put("TUIFU_RATE",       "疑似退服率(%)");
        m.put("SITE_NAME",        "站点名称");
        m.put("ALARM_COUNT",      "告警次数");
        m.put("PROCESS_COUNT",    "处理数");
        m.put("ALARM_TOTAL",      "告警总数");
        m.put("EFFECTIVE_COUNT",  "有效整治数");
        m.put("EFFECTIVE_RATE",   "整治有效率(%)");
        m.put("INVALID_COUNT",    "无效数");
        m.put("HANDLE_COUNT",     "处理数");
        m.put("HANDLE_RATE",      "处理率(%)");
        m.put("CNT",   "数量");
        m.put("NUM",   "数量");
        m.put("TOTAL", "总数");
        m.put("COUNT", "数量");
        return m;
    }
    private static final Map<String, String> KEY_ZH = buildKeyMap();

    private static String keyZh(String key) {
        String zh = KEY_ZH.get(key.toUpperCase(Locale.US));
        return zh != null ? zh : key;
    }

    private void renderDynamic(int tabIdx, JSONArray data) throws Exception {
        JSONObject firstRow = data.getJSONObject(0);
        List<String> keys = new ArrayList<>();
        for (String areaKey : new String[]{"AREA_NAME", "LAT_NAME", "DISTRICT_NAME", "REGION_NAME"}) {
            if (firstRow.has(areaKey) && !keys.contains(areaKey)) {
                keys.add(areaKey);
                break;
            }
        }
        java.util.Iterator<String> it = firstRow.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!keys.contains(k)) keys.add(k);
        }

        String[] headerArr = new String[keys.size() + 1];
        headerArr[0] = "序号";
        for (int i = 0; i < keys.size(); i++) headerArr[i + 1] = keyZh(keys.get(i));
        addRow(llHeader, headerArr, true, tabIdx);

        for (int r = 0; r < data.length(); r++) {
            JSONObject row = data.getJSONObject(r);
            String[] cells = new String[keys.size() + 1];
            cells[0] = String.valueOf(r + 1);
            for (int c = 0; c < keys.size(); c++) {
                cells[c + 1] = row.optString(keys.get(c), "");
            }
            LinearLayout rowView = new LinearLayout(requireContext());
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setBackgroundColor(r % 2 == 0 ? Color.parseColor("#FFFFFF") : Color.parseColor("#F0F4FF"));
            addRow(rowView, cells, false, tabIdx);
            llRows.addView(rowView);
        }
    }

    // ─── 按Tab取JSON字段组成cell数组（tabIdx 1~8） ──────────────────
    private String[] buildCells(int tabIdx, JSONObject row, int seq) {
        switch (tabIdx) {
            case 1: // 电子化覆盖率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", ""),
                    row.optString("SITE_ALL_COUNT", ""),
                    row.optString("SITE_COVER_COUNT", ""),
                    row.optString("ELE_COVER_RATE", ""),
                    row.optString("DATA_DATE", "")
                };
            case 2: // 资产一致性
                return new String[]{
                    row.optString("AREA_NAME", ""),
                    row.optString("SWITCH_SON", ""), row.optString("SWITCH_FATHER", ""), row.optString("SWITCH_RATE", ""),
                    row.optString("AIR_SON", ""), row.optString("AIR_FATHER", ""), row.optString("AIR_RATE", ""),
                    row.optString("QS_BATTERY_SON", ""), row.optString("QS_BATTERY_FATHER", ""), row.optString("QS_BATTERY_RATE", ""),
                    row.optString("PT_BATTERY_SON", ""), row.optString("PT_BATTERY_FATHER", ""), row.optString("PT_BATTERY_RATE", ""),
                    row.optString("ZN_BATTERY_SON", ""), row.optString("ZN_BATTERY_FATHER", ""), row.optString("ZN_BATTERY_RATE", ""),
                    row.optString("BATTERY_RATE", ""), row.optString("PROPERTY_RATE", ""), row.optString("DATA_DATE", "")
                };
            case 3: // PUE有效率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("LAT_NAME", ""),
                    row.optString("SITE_ALL", ""),
                    row.optString("YX_SITE", ""),
                    row.optString("YX_RATE", ""),
                    row.optString("DB_SITE", ""),
                    row.optString("DB_RATE", ""),
                    row.optString("NH_RATE_1", ""),
                    row.optString("NH_RATE_2", ""),
                    row.optString("NH_RATE_3", ""),
                    row.optString("NH_RATE_4", ""),
                    row.optString("DATA_DATE", "")
                };
            case 4: // FSU离线率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("CITY_NAME", row.optString("AREA_NAME", "")),
                    row.optString("AVG_OFFLINE_RATE", ""),
                    row.optString("JW_SITE", ""),
                    row.optString("PD_COUNT", ""),
                    row.optString("CP_SITE", ""),
                    row.optString("DATA_DATE", "")
                };
            case 5: // 故障工单合格率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", row.optString("CITY_NAME", "")),
                    row.optString("ORDER_COUNT", ""),
                    row.optString("GZORDER_NUM", ""),
                    row.optString("OUT_TIME_ORDER", ""),
                    row.optString("GZORDER_OUTTIME_NUM", ""),
                    row.optString("RECEIVE_RATE", ""),
                    row.optString("HANDLE_INTIME_RATE", ""),
                    row.optString("SHEET_CHECK_RATE", ""),
                    row.optString("SHEET_MAKE_RATE", ""),
                    row.optString("DATA_DATE", "")
                };
            case 6: // 疑似退服
                return new String[]{
                    String.valueOf(seq),
                    row.optString("CITY_NAME", row.optString("AREA_NAME", "")),
                    row.optString("DATA_DATE", ""),
                    row.optString("YSTF_SITE", ""),
                    row.optString("YT_SITE", ""),
                    row.optString("YSTF_RATE", "")
                };
            case 7: // 超频告警整治
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", row.optString("CITY_NAME", "")),
                    row.optString("DATA_DATE", ""),
                    row.optString("SHEET_NUM", ""),
                    row.optString("SHEET_NEW", ""),
                    row.optString("SHEET_YL", ""),
                    row.optString("SHEET_CS_RATE", ""),
                    row.optString("SHEET_WX", ""),
                    row.optString("SHEET_CS", ""),
                    row.optString("SHEET_CL_RATE", "")
                };
            case 8: // 省内工单处理及时率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("CITY_NAME", row.optString("AREA_NAME", "")),
                    row.optString("DATA_DATE", ""),
                    row.optString("THIS_CREATE_SHEET", ""),
                    row.optString("THIS_NOSTATUS_SHEET", ""),
                    row.optString("THIS_STATUS_SHEET", ""),
                    row.optString("CS_STATUS_SHEET", ""),
                    row.optString("CS_NOSTATUS_SHEET", ""),
                    row.optString("CL_RATE", "")
                };
            default:
                try {
                    String area = row.optString("AREA_NAME", row.optString("CITY_NAME", String.valueOf(seq)));
                    StringBuilder sb = new StringBuilder();
                    java.util.Iterator<String> keys = row.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (!k.equals("AREA_NAME") && !k.equals("CITY_NAME")) {
                            sb.append(k).append(":").append(row.optString(k)).append("  ");
                        }
                    }
                    return new String[]{String.valueOf(seq), area, sb.toString().trim()};
                } catch (Exception e) {
                    return new String[]{String.valueOf(seq), "", ""};
                }
        }
    }

    // ─── 添加一行（表头或数据行）────────────────────────────────────
    private void addRow(LinearLayout parent, String[] cells, boolean isHeader, int tabIdx) {
        int colWidthDp = (tabIdx == 2) ? 68 : 76;
        float density = getResources().getDisplayMetrics().density;
        int colWidthPx = (int)(colWidthDp * density);

        for (int c = 0; c < cells.length; c++) {
            int w = (c == 0 && cells.length > 2) ? (int)(50 * density) : colWidthPx;
            if (c == cells.length - 1 && cells.length > 4) w = (int)(90 * density);

            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            tv.setText(cells[c]);
            tv.setTextSize(isHeader ? 9.5f : 9f);
            tv.setTextColor(isHeader ? Color.parseColor("#2563EB") : Color.parseColor("#1a1a2e"));
            tv.setTypeface(null, isHeader ? Typeface.BOLD : Typeface.NORMAL);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(2, 4, 2, 4);
            tv.setSingleLine(false);
            tv.setMaxLines(2);
            tv.setIncludeFontPadding(false);
            if (isHeader) tv.setBackgroundColor(Color.parseColor("#EEF2FF"));

            if (c < cells.length - 1) {
                LinearLayout cell = new LinearLayout(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT);
                cell.setLayoutParams(lp);
                cell.setOrientation(LinearLayout.HORIZONTAL);
                cell.addView(tv);
                View divider = new View(requireContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(Color.parseColor("#DDEAFF"));
                cell.addView(divider);
                parent.addView(cell);
            } else {
                parent.addView(tv);
            }
        }
    }

    // ─── 清空表格 ──────────────────────────────────────────────────
    private void clearTable() {
        if (llHeader != null) llHeader.removeAllViews();
        if (llRows != null) llRows.removeAllViews();
    }

    // ─── 日期 ±1 天 ────────────────────────────────────────────────
    private void shiftDate(int days) {
        String cur = etDate.getText().toString().trim();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date d = sdf.parse(cur);
            if (d == null) d = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.add(Calendar.DAY_OF_MONTH, days);
            etDate.setText(sdf.format(cal.getTime()));
        } catch (ParseException e) {
            etDate.setText(sdf.format(new Date()));
        }
    }

    private void setStatus(String msg) {
        mainHandler.post(() -> {
            if (tvStatus != null) tvStatus.setText(msg);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }
}

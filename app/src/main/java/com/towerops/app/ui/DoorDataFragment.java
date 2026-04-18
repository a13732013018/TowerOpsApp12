package com.towerops.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.AccessControlApi;
import com.towerops.app.api.WorkOrderApi;
import com.towerops.app.model.Session;
import com.towerops.app.util.Logger;
import com.towerops.app.util.ThreadManager;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 门禁数据 子Tab（运维日常 → 门禁数据）
 *
 * 核心逻辑：
 *   1. 翻页拉取 OMMS listAlarm.xhtml（alarmname=门）全量历史门禁告警
 *   2. 同步拉取数运蓝牙开门记录（AccessControlApi.getAllLanyaRecords(startDate, endDate)）
 *   3. 按站名分组，每站遍历所有告警：
 *      - 蓝牙和远程开门时间都算，取离告警时间最近的
 *      - 蓝牙 OR 远程 |时间差| ≤ 30分钟 → 合格
 *      - 蓝牙 AND 远程 都 > 30分钟或无记录 → 不合格
 *      - 合格原因 = 时间差更小的那个（蓝牙 or 远程）
 *   4. 每站只取1条展示：
 *      - 有合格 → 取离当前最近的合格告警
 *      - 全不合格 → 取离当前最近的告警
 *   5. 时间差列显示蓝牙/远程中离告警时间最近的那个的差值
 *   6. 按合格/不合格排序后展示，顶部汇总合格站数/不合格站数
 *   7. 表头可点击排序（正序/倒序切换）
 *   8. 支持导出 CSV 到 Downloads 目录
 */
public class DoorDataFragment extends Fragment {

    private static final String TAG = "DoorDataFragment";

    /** ±30 分钟阈值 */
    private static final int MATCH_THRESHOLD_MIN = 30;

    // ── 排序枚举 ─────────────────────────────────────────────────────────
    private enum SortCol { NONE, ST_NAME, ALARM_TIME, BT_TIME, DIFF, REMOTE_TIME, FOUR_A_TIME, QUALIFIED }
    private SortCol  sortCol = SortCol.QUALIFIED;
    private boolean  sortAsc = false; // 默认：合格在前（desc）

    // ── 控件 ──────────────────────────────────────────────────────────────
    private Spinner      spnRegion;
    private Button       btnQuery, btnExport, btn4AGet, btn4ASave;
    private TextView     tvStatus, tvStartDate, tvEndDate;
    private EditText     et4AToken;
    private Spinner      spinnerCounty;
    private LinearLayout layoutSummary, layoutHeader;
    private TextView     tvTotal, tvOk, tvFail;
    private TextView     thStName, thAlarmTime, thBtTime, thDiff, thRemoteTime, thFourATime, thQualified;
    private RecyclerView rvDoorData;

    /** 当前选中日期（yyyy-MM-dd，空串=不限） */
    private String queryStartDate = "";
    private String queryEndDate   = "";

    // ── 数据 ──────────────────────────────────────────────────────────────
    /** 原始结果（保留完整副本，排序只改展示顺序） */
    private final List<DoorAlarmRow> rawResult  = new ArrayList<>();
    /** 当前展示列表（排序后） */
    private final List<DoorAlarmRow> itemList   = new ArrayList<>();
    private DoorDataAdapter adapter;

    private ExecutorService executor;
    private volatile boolean querying = false;

    // ── 4A Token 区县选择器 ──────────────────────────────────────────────
    private static final String[] DOOR_COUNTY_CODES = {"330326", "330329", "330302", "330327", "330328", "330381", "330382", "330303", "330305", "330324", "330383"};
    private static final String[] DOOR_COUNTY_NAMES = {"平阳", "泰顺", "鹿城", "苍南", "文成", "瑞安", "乐清", "龙湾", "洞头", "永嘉", "龙港"};
    private int selectedDoorCountyIndex = 0;

    // ─────────────────────────────────────────────────────────────────────
    // 数据模型
    // ─────────────────────────────────────────────────────────────────────

    /** 最终展示行（每站一条） */
    public static class DoorAlarmRow {
        public int     index;
        public String  stCode;           // 站址运维ID（OMMS col[11]，12位，非站址编码，仅展示用）
        public String  stName;           // 站名（用于匹配蓝牙记录）
        public String  alarmName;        // 告警名称
        public String  alarmTime;        // 告警发生时间
        public String  btOpenTime;       // 蓝牙开门时间（"无" = 无记录）
        public int     diffMinutes;      // |告警时间 - 蓝牙时间| 分钟，-1 = 无法计算
        public String  remoteOpenTime;   // 远程开门时间（"无" = 无记录）
        public int     remoteDiffMinutes; // |告警时间 - 远程开门时间| 分钟，-1 = 无法计算
        public String  fourAOpenTime;    // 4A开门时间（"无" = 无记录）
        public int     fourADiffMinutes; // |告警时间 - 4A开门时间| 分钟，-1 = 无法计算
        public boolean qualified;        // true=合格（蓝牙/远程/4A任一≤30min），false=不合格
        public String  qualifyReason;    // 合格来源："蓝牙" / "远程" / "4A"（取时间差更小的），不合格时为空
        public String  fsuid;            // FSU ID（col[16] 设备ID，14位，第7-9位=438，用于查远程开门时间）
    }

    // ─────────────────────────────────────────────────────────────────────
    // 生命周期
    // ─────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // ★ 确保 Session 数据已恢复（应用重启后 volatile 变量会丢失）
        Session.get().loadConfig(requireContext());

        View v = inflater.inflate(R.layout.fragment_door_data, container, false);
        bindViews(v);
        setupRecycler();
        setupHeaderSort();
        initDefaultDates();
        setupRegionSpinner();
        tvStartDate.setOnClickListener(v2 -> pickDate(true));
        tvEndDate.setOnClickListener(v2 -> pickDate(false));
        btnQuery.setOnClickListener(v2 -> doQuery());
        btnExport.setOnClickListener(v2 -> doExport());
        btn4AGet.setOnClickListener(v2 -> doGet4AToken());
        // 初始化4A Token和区县选择器
        init4ATokenConfig();
        btn4ASave.setOnClickListener(v2 -> doSave4AToken());
        refreshStatus();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
    }

    private void bindViews(View v) {
        spnRegion     = v.findViewById(R.id.spnDoorRegion);
        btnQuery      = v.findViewById(R.id.btnDoorDataQuery);
        btnExport     = v.findViewById(R.id.btnDoorDataExport);
        btn4AGet     = v.findViewById(R.id.btnDoorData4AGet);
        btn4ASave    = v.findViewById(R.id.btnDoorData4ASave);
        tvStatus      = v.findViewById(R.id.tvDoorDataStatus);
        tvStartDate   = v.findViewById(R.id.tvDoorStartDate);
        tvEndDate     = v.findViewById(R.id.tvDoorEndDate);
        layoutSummary = v.findViewById(R.id.layoutDoorDataSummary);
        layoutHeader  = v.findViewById(R.id.layoutDoorDataHeader);
        tvTotal       = v.findViewById(R.id.tvDoorDataTotal);
        tvOk          = v.findViewById(R.id.tvDoorDataOk);
        tvFail        = v.findViewById(R.id.tvDoorDataFail);
        rvDoorData    = v.findViewById(R.id.rvDoorData);
        // 4A Token配置
        et4AToken     = v.findViewById(R.id.etDoorData4AToken);
        spinnerCounty = v.findViewById(R.id.spinnerDoorDataCounty);
        // 表头
        thStName    = v.findViewById(R.id.thDoorStName);
        thAlarmTime = v.findViewById(R.id.thDoorAlarmTime);
        thBtTime    = v.findViewById(R.id.thDoorBtTime);
        thDiff      = v.findViewById(R.id.thDoorDiff);
        thRemoteTime = v.findViewById(R.id.thDoorRemoteTime);
        thFourATime = v.findViewById(R.id.thDoor4ATime);
        thQualified = v.findViewById(R.id.thDoorQualified);
    }

    /** 初始化默认日期：本月1日 ~ 明日 */
    private void initDefaultDates() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(cal.getTime());
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
        String tomorrow = sdf.format(cal.getTime());
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        String firstDay = sdf.format(cal.getTime());
        queryStartDate = firstDay;
        queryEndDate   = tomorrow;
        tvStartDate.setText(firstDay);
        tvEndDate.setText(tomorrow);
    }

    /** 初始化区域选择Spinner（泰顺/平阳） */
    private void setupRegionSpinner() {
        String[] regions = {"平阳", "泰顺"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_spinner_item, regions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView) v).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                ((TextView) v).setTextColor(0xFF1E293B);
                ((TextView) v).setTypeface(null, android.graphics.Typeface.BOLD);
                return v;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                ((TextView) v).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                ((TextView) v).setTextColor(0xFF1E293B);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnRegion.setAdapter(adapter);

        // 恢复上次选择
        Session s = Session.get();
        spnRegion.setSelection(s.doorAlarmRegion);

        spnRegion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Session.get().doorAlarmRegion = position;
                Logger.d("DoorData", "切换区域: " + regions[position] + " (index=" + position + ")");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /** 弹出 DatePickerDialog 让用户选日期 */
    private void pickDate(boolean isStart) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        java.util.Calendar cal = java.util.Calendar.getInstance();
        try {
            String cur = isStart ? queryStartDate : queryEndDate;
            if (cur != null && !cur.isEmpty()) cal.setTime(sdf.parse(cur));
        } catch (Exception ignored) {}

        new android.app.DatePickerDialog(requireContext(),
                (picker, year, month, day) -> {
                    String date = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                            year, month + 1, day);
                    if (isStart) {
                        queryStartDate = date;
                        tvStartDate.setText(date);
                    } else {
                        queryEndDate = date;
                        tvEndDate.setText(date);
                    }
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void setupRecycler() {
        adapter = new DoorDataAdapter(itemList);
        rvDoorData.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDoorData.setAdapter(adapter);
        Logger.d("DoorData", "setupRecycler: adapter.data.size=" + adapter.getItemCount());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 表头排序
    // ─────────────────────────────────────────────────────────────────────

    private void setupHeaderSort() {
        thStName.setOnClickListener(v -> onHeaderClick(SortCol.ST_NAME,    thStName));
        thAlarmTime.setOnClickListener(v -> onHeaderClick(SortCol.ALARM_TIME, thAlarmTime));
        thBtTime.setOnClickListener(v -> onHeaderClick(SortCol.BT_TIME,    thBtTime));
        thDiff.setOnClickListener(v -> onHeaderClick(SortCol.DIFF,        thDiff));
        if (thRemoteTime != null) thRemoteTime.setOnClickListener(v -> onHeaderClick(SortCol.REMOTE_TIME, thRemoteTime));
        if (thFourATime != null) thFourATime.setOnClickListener(v -> onHeaderClick(SortCol.FOUR_A_TIME, thFourATime));
        thQualified.setOnClickListener(v -> onHeaderClick(SortCol.QUALIFIED,  thQualified));
    }

    private void onHeaderClick(SortCol col, TextView header) {
        if (rawResult.isEmpty()) return;
        if (sortCol == col) {
            sortAsc = !sortAsc; // 同列再点 → 翻转
        } else {
            sortCol = col;
            sortAsc = true;     // 新列 → 升序
        }
        applySortAndRefresh();
        updateHeaderArrows();
    }

    /** 刷新所有表头箭头指示 */
    private void updateHeaderArrows() {
        resetHeader(thStName,    "站名",    SortCol.ST_NAME);
        resetHeader(thAlarmTime, "告警时间", SortCol.ALARM_TIME);
        resetHeader(thBtTime,    "蓝牙时间", SortCol.BT_TIME);
        resetHeader(thDiff,      "时差",     SortCol.DIFF);
        if (thRemoteTime != null) resetHeader(thRemoteTime, "远程时间", SortCol.REMOTE_TIME);
        if (thFourATime != null) resetHeader(thFourATime, "4A时间", SortCol.FOUR_A_TIME);
        resetHeader(thQualified, "结果",    SortCol.QUALIFIED);
    }

    /** 取蓝牙/远程/4A中离告警时间更近的差值（分钟） */
    private static int bestDiff(DoorAlarmRow r) {
        int bt = r.diffMinutes >= 0 ? r.diffMinutes : Integer.MAX_VALUE;
        int rm = r.remoteDiffMinutes >= 0 ? r.remoteDiffMinutes : Integer.MAX_VALUE;
        int fa = r.fourADiffMinutes >= 0 ? r.fourADiffMinutes : Integer.MAX_VALUE;
        int min = Math.min(Math.min(bt, rm), fa);
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private void resetHeader(TextView tv, String base, SortCol col) {
        if (tv == null) return;
        if (sortCol == col) {
            tv.setText(base + (sortAsc ? " ▲" : " ▼"));
            tv.setTextColor(Color.parseColor("#4F46E5"));
        } else {
            tv.setText(base);
            tv.setTextColor(Color.parseColor("#374151"));
        }
    }

    /** 对 rawResult 排序，结果写入 itemList，刷新 RecyclerView */
    private void applySortAndRefresh() {
        List<DoorAlarmRow> sorted = new ArrayList<>(rawResult);
        final boolean asc = sortAsc;
        switch (sortCol) {
            case ST_NAME:
                sorted.sort((a, b) -> {
                    int c = safeStr(a.stName).compareTo(safeStr(b.stName));
                    return asc ? c : -c;
                });
                break;
            case ALARM_TIME:
                sorted.sort((a, b) -> {
                    int c = safeStr(a.alarmTime).compareTo(safeStr(b.alarmTime));
                    return asc ? c : -c;
                });
                break;
            case BT_TIME:
                sorted.sort((a, b) -> {
                    int c = safeStr(a.btOpenTime).compareTo(safeStr(b.btOpenTime));
                    return asc ? c : -c;
                });
                break;
            case DIFF:
                sorted.sort((a, b) -> {
                    // 取蓝牙/远程中更小的差值
                    int da = bestDiff(a);
                    int db = bestDiff(b);
                    if (da < 0) da = Integer.MAX_VALUE;
                    if (db < 0) db = Integer.MAX_VALUE;
                    return asc ? Integer.compare(da, db) : Integer.compare(db, da);
                });
                break;
            case REMOTE_TIME:
                sorted.sort((a, b) -> {
                    int c = safeStr(a.remoteOpenTime).compareTo(safeStr(b.remoteOpenTime));
                    return asc ? c : -c;
                });
                break;
            case FOUR_A_TIME:
                sorted.sort((a, b) -> {
                    int c = safeStr(a.fourAOpenTime).compareTo(safeStr(b.fourAOpenTime));
                    return asc ? c : -c;
                });
                break;
            case QUALIFIED:
            default:
                sorted.sort((a, b) -> {
                    // 合格在前（sortAsc=false 时是默认状态）
                    if (a.qualified != b.qualified)
                        return asc
                                ? (a.qualified ? 1 : -1)   // asc: 不合格在前
                                : (a.qualified ? -1 : 1);  // desc: 合格在前
                    return b.alarmTime.compareTo(a.alarmTime); // 时间降序
                });
                break;
        }
        // 重新编号
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).index = i + 1;
        itemList.clear();
        itemList.addAll(sorted);
        Logger.d("DoorData", "applySortAndRefresh: 准备刷新，itemList.size=" + itemList.size());
        if (adapter != null) {
            // ★ 强制在主线程刷新，并确保 adapter 可见
            ThreadManager.runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                rvDoorData.invalidate();
                Logger.d("DoorData", "applySortAndRefresh: 已在主线程刷新，adapter.getItemCount=" + adapter.getItemCount());
            });
        } else {
            Logger.e("DoorData", "applySortAndRefresh: adapter 为 null！");
        }
    }

    private static String safeStr(String s) { return s == null ? "" : s; }

    // ─────────────────────────────────────────────────────────────────────
    // 查询入口
    // ─────────────────────────────────────────────────────────────────────

    private void refreshStatus() {
        Session s = Session.get();
        if (s.ommsCookie == null || s.ommsCookie.isEmpty()) {
            tvStatus.setText("请先在「门禁系统」Tab完成OMMS登录");
        } else {
            tvStatus.setText("就绪，点「查询」开始匹配门禁告警");
        }
    }

    private void doQuery() {
        if (querying) return;
        Session s = Session.get();
        if (s.ommsCookie == null || s.ommsCookie.isEmpty()) {
            tvStatus.setText("请先在「门禁系统」Tab完成OMMS登录");
            return;
        }
        // 保存区域选择到 SharedPreferences
        s.saveShuyunLogin(requireContext());
        querying = true;
        btnQuery.setEnabled(false);
        btnExport.setVisibility(View.GONE);
        layoutSummary.setVisibility(View.GONE);
        layoutHeader.setVisibility(View.GONE);
        rawResult.clear();
        itemList.clear();
        adapter.notifyDataSetChanged();
        setStatus("正在查询...");

        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<DoorAlarmRow> result = fetchAndMatch();
            Logger.d("DoorData", "fetchAndMatch返回: " + result.size() + " 条");
            ThreadManager.runOnUiThread(() -> {
                if (!isAdded()) return;
                rawResult.clear();
                rawResult.addAll(result);
                Logger.d("DoorData", "rawResult.size=" + rawResult.size() + " itemList.size=" + itemList.size());
                // 默认：合格在前（结果列 降序）
                sortCol = SortCol.QUALIFIED;
                sortAsc = false;
                applySortAndRefresh();
                Logger.d("DoorData", "applySortAndRefresh后: rawResult=" + rawResult.size() + " itemList=" + itemList.size());
                updateHeaderArrows();
                updateSummary(result);
                querying = false;
                btnQuery.setEnabled(true);
                if (!result.isEmpty()) {
                    btnExport.setVisibility(View.VISIBLE);
                    // ★ 强制确保 RecyclerView 可见
                    rvDoorData.setVisibility(View.VISIBLE);
                    Logger.d("DoorData", "doQuery完成: RecyclerView.VISIBLE, adapter.getItemCount=" + adapter.getItemCount());
                    // ★ 终极保险：如果 adapter.getItemCount() 为 0 但 itemList 不为空，强制重建 adapter
                    if (adapter.getItemCount() == 0 && !itemList.isEmpty()) {
                        Logger.e("DoorData", "❌ 严重错误：adapter.getItemCount=0 但 itemList.size=" + itemList.size() + "，强制重建 adapter");
                        setupRecycler();
                        adapter.notifyDataSetChanged();
                        Logger.d("DoorData", "重建后: adapter.getItemCount=" + adapter.getItemCount());
                    }
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 获取4A Bearer Token（通过TymjLoginActivity）
    // ─────────────────────────────────────────────────────────────────────

    private static final int REQ_4A_TOKEN = 1001;

    /**
     * 获取4A Bearer Token
     * 直接启动TymjLoginActivity，让用户在WebView中完成4A登录并获取Bearer Token
     */
    private void doGet4AToken() {
        btn4AGet.setEnabled(false);
        btn4AGet.setText("获取中...");
        setStatus("🔄 正在启动4A登录页面...");

        // ★ 注册回调：登录完成后自动触发查询
        Session.addOn4aTokenReady(() -> {
            android.util.Log.d("DoorData", "★★ 4A Token就绪，回调触发，自动查询");
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(() -> {
                Session.get().loadConfig(requireContext());
                doQuery();
            }, 500);
        });

        try {
            Intent intent = new Intent(requireContext(), TymjLoginActivity.class);
            startActivityForResult(intent, REQ_4A_TOKEN);
        } catch (Exception e) {
            Session.clearOn4aTokenReadyCallbacks(); // 清理回调
            btn4AGet.setEnabled(true);
            btn4AGet.setText("获取4A");
            Toast.makeText(requireContext(), "❌ 启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            android.util.Log.e("DoorData", "启动TymjLoginActivity失败", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        android.util.Log.d("DoorData", "★★ onActivityResult req=" + requestCode + " result=" + resultCode);
        
        if (requestCode == REQ_4A_TOKEN) {
            btn4AGet.setEnabled(true);
            btn4AGet.setText("获取4A");
            
            if (resultCode == Activity.RESULT_OK && data != null) {
                String token = data.getStringExtra(TymjLoginActivity.EXTRA_TOKEN);
                if (token != null && !token.isEmpty()) {
                    // Bearer Token获取成功
                    Session.get().tower4aToken = token;
                    Session.get().tower4aCountyCode = "330326"; // 默认平阳县
                    Session.get().saveTower4aToken(requireContext());
                    // ★ 自动填入输入框
                    if (et4AToken != null) et4AToken.setText(token);

                    Toast.makeText(requireContext(),
                            "✅ Bearer Token 获取成功（len=" + token.length() + "）", Toast.LENGTH_SHORT).show();
                    setStatus("✅ 4A Token 已获取，点击查询可获取4A开门记录");
                } else {
                    setStatus("❌ Token为空，请重试");
                    Toast.makeText(requireContext(), "❌ Token为空", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 用户取消或失败
                Session.clearOn4aTokenReadyCallbacks();
                setStatus("已取消");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 导出 CSV
    // ─────────────────────────────────────────────────────────────────────

    private void doExport() {
        if (rawResult.isEmpty()) {
            Toast.makeText(requireContext(), "暂无数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 文件名：DoorData_yyyyMMdd_HHmmss.csv
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "DoorData_" + ts + ".csv";
                File dir;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 用公共 Downloads
                    dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                } else {
                    dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                }
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);

                FileWriter fw = new FileWriter(outFile, false);
                // BOM，让 Excel 正确识别 UTF-8
                fw.write('\uFEFF');
                // 表头
                fw.write("序号,站名,站址编码,告警类型,告警时间,蓝牙进站时间,蓝牙时差(分钟),远程开门时间,远程时差(分钟),4A开门时间,4A时差(分钟),结果\n");
                // 按当前展示顺序导出
                for (DoorAlarmRow row : itemList) {
                    fw.write(csvCell(String.valueOf(row.index)));
                    fw.write(",");
                    fw.write(csvCell(row.stName));
                    fw.write(",");
                    fw.write(csvCell(row.stCode));
                    fw.write(",");
                    fw.write(csvCell(row.alarmName));
                    fw.write(",");
                    fw.write(csvCell(row.alarmTime));
                    fw.write(",");
                    fw.write(csvCell("无".equals(row.btOpenTime) ? "" : row.btOpenTime));
                    fw.write(",");
                    fw.write(csvCell(row.diffMinutes >= 0 ? String.valueOf(row.diffMinutes) : ""));
                    fw.write(",");
                    fw.write(csvCell("无".equals(row.remoteOpenTime) ? "" : (row.remoteOpenTime == null ? "" : row.remoteOpenTime)));
                    fw.write(",");
                    fw.write(csvCell(row.remoteDiffMinutes >= 0 ? String.valueOf(row.remoteDiffMinutes) : ""));
                    fw.write(",");
                    fw.write(csvCell("无".equals(row.fourAOpenTime) ? "" : (row.fourAOpenTime == null ? "" : row.fourAOpenTime)));
                    fw.write(",");
                    fw.write(csvCell(row.fourADiffMinutes >= 0 ? String.valueOf(row.fourADiffMinutes) : ""));
                    fw.write(",");
                    fw.write(csvCell(row.qualified ? "合格(" + (row.qualifyReason != null ? row.qualifyReason : "") + ")" : "不合格"));
                    fw.write("\n");
                }
                fw.flush();
                fw.close();

                final String path = outFile.getAbsolutePath();
                ThreadManager.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "已导出到：" + path,
                            Toast.LENGTH_LONG).show();
                    setStatus("✅ 已导出 " + itemList.size() + " 条 → " + fileName);
                });
            } catch (Exception e) {
                Logger.e(TAG, "导出失败", e);
                final String msg = e.getMessage();
                ThreadManager.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "导出失败：" + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** CSV 单元格转义：包含逗号/双引号/换行时加双引号包裹，内部双引号转义 */
    private static String csvCell(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 核心：拉取 + 匹配（后台线程）
    // ─────────────────────────────────────────────────────────────────────

    private List<DoorAlarmRow> fetchAndMatch() {

        // 快照日期参数（后台线程不能访问 UI 控件）
        final String startDate = queryStartDate;
        final String endDate   = queryEndDate;

        // 调试日志：确认日期范围
        android.util.Log.d("DoorData", ">>> fetchAndMatch 日期范围: startDate=" + startDate + " endDate=" + endDate);

        // ════ Step 1：拉取所有 OMMS 门禁告警（分页） ════════════════════════
        setStatus("📡 正在拉取OMMS门禁告警（翻页中）...");
        List<RawAlarm> rawAlarms = fetchAllOmmsAlarms(startDate, endDate);
        setStatus("✓ OMMS告警共 " + rawAlarms.size() + " 条，正在拉取蓝牙记录...");

        // ════ Step 2：拉取蓝牙开门记录 ══════════════════════════════════════
        List<AccessControlApi.LanyaRecord> lanyaList = new ArrayList<>();
        Session s = Session.get();
        android.util.Log.d("DoorData", "[蓝牙诊断] shuyunPcToken=" + (s.shuyunPcToken.isEmpty() ? "空" : "有(" + s.shuyunPcToken.length() + "字符)")
                + " shuyunPcTokenCookie=" + (s.shuyunPcTokenCookie.isEmpty() ? "空" : "有(" + s.shuyunPcTokenCookie.length() + "字符)"));
        if (!s.shuyunPcToken.isEmpty() || !s.shuyunPcTokenCookie.isEmpty()) {
            // ★ 按选中区域筛选蓝牙记录（服务端筛选，减少数据量）
            String regionKeyword = (s.doorAlarmRegion == 1) ? "泰顺" : "平阳";
            lanyaList = AccessControlApi.getAllLanyaRecords(startDate, endDate, regionKeyword);
            // ★ 诊断日志：打印API返回情况
            Logger.d("DoorData", "[蓝牙诊断] getAllLanyaRecords返回 " + lanyaList.size() + " 条");
            if (lanyaList.size() > 0) {
                Logger.d("DoorData", "[蓝牙诊断] 首条: stationName=" + lanyaList.get(0).stationName
                    + " openTime=" + lanyaList.get(0).openTime
                    + " bluetoothOpenTime=" + lanyaList.get(0).bluetoothOpenTime);
            }
            if (lanyaList.size() > 1) {
                Logger.d("DoorData", "[蓝牙诊断] 末条: stationName=" + lanyaList.get(lanyaList.size()-1).stationName
                    + " openTime=" + lanyaList.get(lanyaList.size()-1).openTime);
            }
            setStatus("✓ 蓝牙记录共 " + lanyaList.size() + " 条（" + regionKeyword + "），正在拉取4A记录...");
        } else {
            android.util.Log.w("DoorData", "[蓝牙诊断] 数运未登录，跳过蓝牙记录拉取！蓝牙时间将全部显示'无'");
            setStatus("⚠ 数运未登录，跳过蓝牙匹配，正在拉取4A记录...");
        }

        // ════ Step 2b：拉取4A开门记录 ════════════════════════════════════
        List<AccessControlApi.FourARecord> fourAList = new ArrayList<>();
        boolean has4aToken = s.tower4aToken != null && !s.tower4aToken.isEmpty();
        android.util.Log.d("DoorData", "[4A诊断] tower4aToken=" + (has4aToken ? "有(" + s.tower4aToken.length() + "字符)" : "空")
                + " tower4aCountyCode=" + (s.tower4aCountyCode != null ? s.tower4aCountyCode : "空"));
        if (has4aToken) {
            fourAList = AccessControlApi.getAll4aOpenRecords(startDate, endDate, s.tower4aCountyCode);
            Logger.d("DoorData", "[4A诊断] getAll4aOpenRecords返回 " + fourAList.size() + " 条");
            if (!fourAList.isEmpty()) {
                Logger.d("DoorData", "[4A诊断] 首条: stationName=" + fourAList.get(0).stationName
                        + " openTime=" + fourAList.get(0).openTime);
            }
            setStatus("✓ 4A记录共 " + fourAList.size() + " 条，正在匹配...");
        } else {
            android.util.Log.w("DoorData", "[4A诊断] tower4aToken 未配置，跳过4A记录拉取！4A时间将全部显示'无'");
            setStatus("⚠ 4A未配置Token，跳过4A匹配，正在匹配...");
        }

        // ════ Step 3：构建蓝牙查找表 ════
        // ★ 关键发现：OMMS col[11] 是站址运维ID（12位），数运station_code是站址编码（18位），两者不同！
        // ★ 唯一能跨系统关联的字段是站名（station_name），所以必须用站名匹配
        Map<String, List<String>> btByName = new HashMap<>();  // 站名 → 所有开门时间列表
        int lanyaWithName = 0, lanyaNoName = 0;
        for (AccessControlApi.LanyaRecord rec : lanyaList) {
            if (rec.openTime == null || rec.openTime.isEmpty()) continue;
            rec.stationName = AccessControlApi.cleanStationName(rec.stationName);
            if (!rec.stationName.isEmpty()) {
                btByName.computeIfAbsent(rec.stationName, k -> new ArrayList<>()).add(rec.openTime);
                lanyaWithName++;
            } else {
                lanyaNoName++;
            }
        }
        Logger.d("DoorData", "[蓝牙匹配] 蓝牙记录有站名: " + lanyaWithName + "条，无站名: " + lanyaNoName + "条，去重后站数: " + btByName.size());

        // ════ Step 3b：构建4A查找表 ════
        Map<String, List<String>> fourAByName = new HashMap<>();  // 站名 → 所有4A开门时间列表
        int fourAWithName = 0, fourANoName = 0;
        for (AccessControlApi.FourARecord rec : fourAList) {
            if (rec.openTime == null || rec.openTime.isEmpty()) continue;
            if (rec.stationName != null && !rec.stationName.isEmpty()) {
                fourAByName.computeIfAbsent(rec.stationName, k -> new ArrayList<>()).add(rec.openTime);
                fourAWithName++;
            } else {
                fourANoName++;
            }
        }
        Logger.d("DoorData", "[4A匹配] 4A记录有站名: " + fourAWithName + "条，无站名: " + fourANoName + "条，去重后站数: " + fourAByName.size());
        // ★★★ 诊断：打印完整4A站名列表（逐条，打印char codes） ★★★
        StringBuilder fourANamesBuilder = new StringBuilder("[4A完整站名列表] ");
        int faCount = 0;
        for (String name : fourAByName.keySet()) {
            fourANamesBuilder.append("[").append(faCount).append("]'").append(name).append("'");
            // 打印char codes检查隐藏字符
            if (name != null) {
                fourANamesBuilder.append("(len=").append(name.length());
                for (int i = 0; i < Math.min(5, name.length()); i++) {
                    fourANamesBuilder.append(",").append((int)name.charAt(i));
                }
                fourANamesBuilder.append("...)");
            }
            fourANamesBuilder.append("; ");
            faCount++;
        }
        Logger.d("DoorData", fourANamesBuilder.toString());
        // 打印前5条4A站名示例
        int sampleCount = 0;
        for (Map.Entry<String, List<String>> fa : fourAByName.entrySet()) {
            Logger.d("DoorData", "[4A站名示例" + (++sampleCount) + "] 4A站名='" + fa.getKey() + "' norm=" + normName(fa.getKey()) + " 时间数=" + fa.getValue().size()
                    + " 首条=" + (fa.getValue().isEmpty() ? "" : fa.getValue().get(0)));
            if (sampleCount >= 5) break;
        }

        // ════ Step 5：按站分组 + 蓝牙/远程/4A匹配 + 每站取1条 ══════════════════════
        // ★★★ 2026-04-10 更新：增加4A匹配 ★★★
        // 规则1：蓝牙/远程/4A只要有一个≤30分钟 → 合格
        // 规则2：蓝牙/远程/4A都不满足 → 不合格
        // 规则3：每站只取1条展示
        //   - 有合格：取离当前最近的合格告警
        //   - 全不合格：取离当前最近的告警
        // 规则4：时间差取三者中离告警时间最近的那个的差值
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        // ── 5a：按站名分组告警 ──
        Map<String, List<RawAlarm>> alarmsByStation = new LinkedHashMap<>();
        for (RawAlarm alarm : rawAlarms) {
            // ★ 清洗站名，去除不可见字符
            alarm.stName = AccessControlApi.cleanStationName(alarm.stName);
            String key = alarm.stName.isEmpty() ? "【未知站】" : alarm.stName;
            alarmsByStation.computeIfAbsent(key, k -> new ArrayList<>()).add(alarm);
        }
        Logger.d("DoorData", "[分组] 共 " + alarmsByStation.size() + " 站，总告警 " + rawAlarms.size() + " 条");
        // ★★★ 诊断：打印完整OMMS站名列表（检查字符编码） ★★★
        StringBuilder ommsNamesBuilder = new StringBuilder("[OMMS完整站名列表] ");
        int ommsCount = 0;
        for (String name : alarmsByStation.keySet()) {
            ommsNamesBuilder.append("[").append(ommsCount).append("]'").append(name).append("'");
            if (name != null) {
                ommsNamesBuilder.append("(len=").append(name.length());
                for (int i = 0; i < Math.min(5, name.length()); i++) {
                    ommsNamesBuilder.append(",").append((int)name.charAt(i));
                }
                ommsNamesBuilder.append("...)");
            }
            ommsNamesBuilder.append("; ");
            ommsCount++;
        }
        Logger.d("DoorData", ommsNamesBuilder.toString());
        // 打印前5条OMMS站名示例
        sampleCount = 0;
        for (String ommsStation : alarmsByStation.keySet()) {
            Logger.d("DoorData", "[OMMS站名示例" + (++sampleCount) + "] OMMS站名='" + ommsStation + "'");
            if (sampleCount >= 5) break;
        }
        
        // ★★★ 关键诊断：检查4A和OMMS站名是否有交集 ★★★
        Set<String> intersection = new java.util.HashSet<>(fourAByName.keySet());
        intersection.retainAll(alarmsByStation.keySet());
        Logger.d("DoorData", "[★★★ 交叉匹配诊断 ★★★] 4A站数=" + fourAByName.size() 
                + " OMMS站数=" + alarmsByStation.size() 
                + " 交集=" + intersection.size() + " 交集内容=" + intersection);
        
        // 详细检查：每个4A站是否和任何OMMS站名相似
        int matchFound = 0;
        StringBuilder mismatchLog = new StringBuilder("[4A站名相似度检查] ");
        for (String fourAName : fourAByName.keySet()) {
            boolean hasMatch = false;
            String fourANorm = normName(fourAName);
            for (String ommsName : alarmsByStation.keySet()) {
                String ommsNorm = normName(ommsName);
                // 完全相等
                if (fourANorm.equals(ommsNorm)) {
                    hasMatch = true;
                    break;
                }
                // 一个包含另一个（简化匹配）
                if (fourANorm.contains(ommsNorm) || ommsNorm.contains(fourANorm)) {
                    hasMatch = true;
                    break;
                }
            }
            if (hasMatch) {
                matchFound++;
            } else {
                mismatchLog.append(fourANorm).append("✗; ");
            }
        }
        Logger.d("DoorData", "[4A站名相似度检查] 匹配=" + matchFound + "/" + fourAByName.size() + " " + mismatchLog);

        // ── 5b：远程开门记录缓存（同一 FSU ID 只查一次） ──
        Map<String, java.util.List<String>> remoteTimesCache = new HashMap<>();
        boolean hasOmmsCookie = s.ommsCookie != null && !s.ommsCookie.isEmpty();

        int btMatchCount = 0, remoteMatchCount = 0, fourAMatchCount = 0, totalStations = alarmsByStation.size();
        int processedStations = 0;
        List<DoorAlarmRow> allRows = new ArrayList<>();

        for (Map.Entry<String, List<RawAlarm>> entry : alarmsByStation.entrySet()) {
            String stationName = entry.getKey();
            List<RawAlarm> stationAlarms = entry.getValue();
            processedStations++;
            if (processedStations <= 3 || processedStations % 50 == 0) {
                setStatus("🔍 匹配 " + processedStations + "/" + totalStations + "：" + stationName);
            }

            // 取本站的蓝牙时间列表
            List<String> btTimes = btByName.get(stationName);

            // 取本站的远程开门记录（用第一条告警的fsuid查询，同站一般共用FSU）
            java.util.List<String> remoteTimes = null;
            String stationFsuid = stationAlarms.get(0).deviceId;
            if (hasOmmsCookie && stationFsuid != null && !stationFsuid.isEmpty()) {
                remoteTimes = remoteTimesCache.get(stationFsuid);
                if (remoteTimes == null) {
                    remoteTimes = AccessControlApi.getAllRemoteOpenTimesByFsuid(stationFsuid);
                    remoteTimesCache.put(stationFsuid, remoteTimes);
                    Logger.d("DoorData", "[远程匹配] " + stationName + " fsuid=" + stationFsuid + " → " + remoteTimes.size() + "条记录");
                }
            }

            // 取本站的4A开门时间列表（先精确匹配站名，找不到再模糊匹配）
            List<String> fourATimes = fourAByName.get(stationName);
            String fourAMatchInfo = fourATimes != null ? "精确匹配" : "未匹配";
            if (fourATimes == null && !fourAByName.isEmpty()) {
                // 模糊匹配：4A站名可能比OMMS站名短（去掉"基站"等后缀）
                String normStation = normName(stationName);
                for (Map.Entry<String, List<String>> fa : fourAByName.entrySet()) {
                    String normFa = normName(fa.getKey());
                    if (!normStation.isEmpty() && !normFa.isEmpty()
                            && (normFa.contains(normStation) || normStation.contains(normFa))) {
                        fourATimes = fa.getValue();
                        fourAMatchInfo = "模糊匹配→" + fa.getKey();
                        Logger.d("DoorData", "[4A模糊匹配] " + stationName + " → " + fa.getKey() + " (" + fa.getValue().size() + "条)");
                        break;
                    }
                }
            }
            // 前10条记录打印4A匹配详情
            if (processedStations <= 10) {
                Logger.d("DoorData", "[4A匹配详情" + processedStations + "] " + stationName
                        + " fourAByName.get=" + (fourAByName.get(stationName) != null ? "有" : "无")
                        + " fourATimes=" + (fourATimes != null ? fourATimes.size() + "条" : "null")
                        + " 匹配=" + fourAMatchInfo);
            }

            // 遍历本站所有告警，计算每条的蓝牙+远程+4A匹配
            DoorAlarmRow bestQualified = null;  // 最近的合格告警
            DoorAlarmRow bestUnqualified = null; // 最近的不合格告警（全不合格时用）

            for (RawAlarm alarm : stationAlarms) {
                DoorAlarmRow row = new DoorAlarmRow();
                row.stCode            = alarm.stCode;
                row.stName            = alarm.stName;
                row.alarmName         = alarm.alarmName;
                row.alarmTime         = alarm.alarmTime;
                row.btOpenTime        = "无";
                row.diffMinutes       = -1;
                row.remoteOpenTime    = "无";
                row.remoteDiffMinutes = -1;
                row.fourAOpenTime     = "无";
                row.fourADiffMinutes  = -1;
                row.qualified         = false;
                row.qualifyReason     = "";
                row.fsuid             = alarm.deviceId;

                Date alarmDate = parseDate(sdf, alarm.alarmTime);
                boolean btOk = false, remoteOk = false, fourAOk = false;
                int closestBtDiff = Integer.MAX_VALUE;
                int closestRemoteDiff = Integer.MAX_VALUE;
                int closestFourADiff = Integer.MAX_VALUE;

                // ── 蓝牙匹配 ──
                if (alarmDate != null && btTimes != null && !btTimes.isEmpty()) {
                    String closestBtTime = null;
                    for (String btTime : btTimes) {
                        Date btDate = parseDate(sdf, btTime);
                        if (btDate == null) continue;
                        int diffMin = (int)(Math.abs(alarmDate.getTime() - btDate.getTime()) / 60000);
                        if (diffMin < closestBtDiff) {
                            closestBtDiff = diffMin;
                            closestBtTime = btTime;
                        }
                    }
                    if (closestBtTime != null) {
                        row.btOpenTime  = closestBtTime;
                        row.diffMinutes = closestBtDiff;
                        if (closestBtDiff <= MATCH_THRESHOLD_MIN) btOk = true;
                    }
                }

                // ── 远程匹配 ──
                if (alarmDate != null && remoteTimes != null && !remoteTimes.isEmpty()) {
                    String closestRemoteTime = null;
                    for (String rt : remoteTimes) {
                        Date rd = parseDate(sdf, rt);
                        if (rd == null) continue;
                        int diffMin = (int)(Math.abs(alarmDate.getTime() - rd.getTime()) / 60000);
                        if (diffMin < closestRemoteDiff) {
                            closestRemoteDiff = diffMin;
                            closestRemoteTime = rt;
                        }
                    }
                    if (closestRemoteTime != null) {
                        row.remoteOpenTime    = closestRemoteTime;
                        row.remoteDiffMinutes = closestRemoteDiff;
                        if (closestRemoteDiff <= MATCH_THRESHOLD_MIN) remoteOk = true;
                    }
                }

                // ── 4A匹配 ──
                if (alarmDate != null && fourATimes != null && !fourATimes.isEmpty()) {
                    String closestFourATime = null;
                    for (String fat : fourATimes) {
                        Date faDate = parseDate(sdf, fat);
                        if (faDate == null) continue;
                        int diffMin = (int)(Math.abs(alarmDate.getTime() - faDate.getTime()) / 60000);
                        if (diffMin < closestFourADiff) {
                            closestFourADiff = diffMin;
                            closestFourATime = fat;
                        }
                    }
                    if (closestFourATime != null) {
                        row.fourAOpenTime    = closestFourATime;
                        row.fourADiffMinutes = closestFourADiff;
                        if (closestFourADiff <= MATCH_THRESHOLD_MIN) fourAOk = true;
                    }
                    // 前10条记录打印4A赋值结果
                    if (processedStations <= 10) {
                        Logger.d("DoorData", "[4A赋值" + processedStations + "] " + stationName
                                + " fourAOpenTime=" + row.fourAOpenTime + " diff=" + row.fourADiffMinutes + " fourAOk=" + fourAOk);
                    }
                } else {
                    // 调试：打印未进入4A匹配的站
                    if (processedStations <= 5) {
                        Logger.d("DoorData", "[4A跳过" + processedStations + "] " + stationName
                                + " alarmDate=" + (alarmDate != null ? "有" : "空")
                                + " fourATimes=" + (fourATimes != null ? fourATimes.size() : "null"));
                    }
                }

                // ── 判定合格 ──
                if (btOk || remoteOk || fourAOk) {
                    row.qualified = true;
                    // 合格原因：取时间差最小的那个来源
                    int minDiff = Integer.MAX_VALUE;
                    String bestReason = "";
                    if (btOk     && closestBtDiff     < minDiff) { minDiff = closestBtDiff;     bestReason = "蓝牙"; }
                    if (remoteOk && closestRemoteDiff  < minDiff) { minDiff = closestRemoteDiff;  bestReason = "远程"; }
                    if (fourAOk  && closestFourADiff   < minDiff) { minDiff = closestFourADiff;   bestReason = "4A"; }
                    row.qualifyReason = bestReason;

                    // 统计合格来源（只统计主合格原因）
                    if ("蓝牙".equals(bestReason))  btMatchCount++;
                    else if ("远程".equals(bestReason)) remoteMatchCount++;
                    else if ("4A".equals(bestReason))   fourAMatchCount++;

                    // 选最近的合格告警（离当前时间最近的告警时间）
                    if (bestQualified == null || row.alarmTime.compareTo(bestQualified.alarmTime) > 0) {
                        bestQualified = row;
                    }
                } else {
                    // 不合格
                    if (bestUnqualified == null || row.alarmTime.compareTo(bestUnqualified.alarmTime) > 0) {
                        bestUnqualified = row;
                    }
                }
            }

            // ── 每站只取1条 ──
            if (bestQualified != null) {
                allRows.add(bestQualified);
            } else if (bestUnqualified != null) {
                allRows.add(bestUnqualified);
            }
        }

        Logger.d("DoorData", "[最终匹配] " + totalStations + "站，蓝牙合格=" + btMatchCount
                + "，远程合格=" + remoteMatchCount + "，4A合格=" + fourAMatchCount
                + "，展示 " + allRows.size() + " 条");
        
        // ★ 诊断：打印前5条结果的4A时间
        for (int i = 0; i < Math.min(5, allRows.size()); i++) {
            DoorAlarmRow r = allRows.get(i);
            Logger.d("DoorData", "[结果4A诊断" + (i+1) + "] " + r.stName + " fourAOpenTime='" + r.fourAOpenTime + "' fourADiff=" + r.fourADiffMinutes);
        }

        // ════ Step 6：初始排序（合格在前，同类按告警时间降序） ════════════
        allRows.sort((a, b) -> {
            if (a.qualified != b.qualified) return a.qualified ? -1 : 1;
            return b.alarmTime.compareTo(a.alarmTime);
        });
        for (int i = 0; i < allRows.size(); i++) allRows.get(i).index = i + 1;

        int okCount = 0, failCount = 0;
        for (DoorAlarmRow r : allRows) { if (r.qualified) okCount++; else failCount++; }
        setStatus("✅ 完成：" + allRows.size() + " 条告警，合格 " + okCount + " / 不合格 " + failCount);
        return allRows;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 翻页拉取 OMMS 全量门禁告警
    // ─────────────────────────────────────────────────────────────────────

    /** 原始告警行（解析 OMMS HTML 用） */
    private static class RawAlarm {
        String stCode      = "";  // col[11] 站址运维ID（12位）
        String stName      = "";  // col[8]  站名
        String stationCode = "";  // col[44] 站址编码（18位）
        String alarmName   = "";  // col[13] 告警名称
        String alarmTime   = "";  // col[20] 产生时间
        String deviceId    = "";  // col[16] 设备ID（FSU ID，用于查询远程开门时间）
    }

    private static final int MAX_PAGES = 200; // 安全上限，实际由服务器总页数控制

    private List<RawAlarm> fetchAllOmmsAlarms(String startDate, String endDate) {
        List<RawAlarm> all = new ArrayList<>();
        int totalPages = MAX_PAGES; // 默认安全上限，第1页响应后更新为实际总页数

        // ★★★ 第1页：用完整查询模式（带查询条件+trigger） ★★★
        String html = "";
        try {
            html = WorkOrderApi.getDoorAlarmList(1, startDate, endDate);
        } catch (Exception e) {
            Logger.e("DoorData", "第1页查询异常: " + e.getMessage());
            return all;
        }
        if (html == null || html.isEmpty()) return all;
        // AJAX响应中uac/login可能出现在URL配置里，不是真正的登录页，只有很小(<500字节)时才判为登录
        if (html.length() < 500 && (html.contains("doPrevLogin") || html.contains("uac/login"))) return all;

        String workHtml = extractCdata(html);

        // 调试：第1页打印HTML前1000字符
        Logger.d("DoorData", "第1页HTML前1000字符: " +
            (workHtml.length() > 1000 ? workHtml.substring(0, 1000) : workHtml));
        Logger.d("DoorData", "第1页HTML包含<tbody>: " + workHtml.contains("<tbody>"));
        Logger.d("DoorData", "第1页HTML包含<tr>: " + workHtml.contains("<tr>"));

        // 第1页解析总页数
        int parsed = parseTotalPages(workHtml);
        if (parsed > 0) {
            totalPages = Math.min(parsed, MAX_PAGES);
            Logger.d("DoorData", "第1页解析总页数: " + parsed + "，实际翻页上限: " + totalPages);
        } else {
            Logger.w("DoorData", "第1页未解析到总页数，使用默认上限: " + totalPages);
        }

        // ★ 解析服务器返回的总记录数（用于对比App实际拉取数量）
        int serverTotal = parseTotalRecords(workHtml);

        // 解析第1页数据
        List<RawAlarm> pageRows = parseAlarmRows(workHtml);

        // 第1页数据全部收集（去重在匹配阶段按业务规则统一处理）
        all.addAll(pageRows);

        // 记录第1页首条key，翻页检测用
        String lastPageFirstKey = "";
        if (!pageRows.isEmpty()) {
            lastPageFirstKey = pageRows.get(0).stCode + "|" + pageRows.get(0).alarmTime;
        }

        setStatus("📡 已拉取 " + all.size() + " 条门禁告警（第1/" + totalPages + "页）...");

        if (pageRows.size() < 300) {
            Logger.d("DoorData", "第1页不足300条（" + pageRows.size() + "条），已是全部数据");
            return all;
        }

        // ★★★ 第2页起：用 itemScroller=next 翻页模式 ★★★
        int noNewCount = 0; // 连续无新增数据的页数
        for (int page = 2; page <= totalPages; page++) {
            try {
                html = WorkOrderApi.getDoorAlarmNextPage();
            } catch (Exception e) {
                Logger.e("DoorData", "第" + page + "页翻页异常: " + e.getMessage());
                break;
            }
            if (html == null || html.isEmpty()) {
                Logger.w("DoorData", "第" + page + "页返回空，停止翻页");
                break;
            }
            if (html.length() < 500 && (html.contains("doPrevLogin") || html.contains("uac/login"))) break;

            workHtml = extractCdata(html);
            pageRows = parseAlarmRows(workHtml);

            // 翻页诊断
            if (page <= 3) {
                Logger.d("DoorData", "第" + page + "页(Scroller): workHtml.length=" + workHtml.length()
                    + " 包含rich-table-row=" + workHtml.contains("rich-table-row")
                    + " pageRows=" + pageRows.size());
                if (pageRows.size() > 0) {
                    Logger.d("DoorData", "第" + page + "页首条: stCode=" + pageRows.get(0).stCode
                        + " stName=" + pageRows.get(0).stName
                        + " alarmTime=" + pageRows.get(0).alarmTime);
                }
            }

            if (pageRows.isEmpty()) {
                Logger.w("DoorData", "第" + page + "页: 返回0条，停止翻页");
                break;
            }

            // 全部收集（去重在匹配阶段按业务规则统一处理）
            all.addAll(pageRows);
            Logger.d("DoorData", "第" + page + "页: " + pageRows.size() + "条，累计" + all.size() + "条");

            // 检测翻页是否生效：用本页首条 key 与上页首条 key 比对
            String pageFirstKey = pageRows.get(0).stCode + "|" + pageRows.get(0).alarmTime;
            if (pageFirstKey.equals(lastPageFirstKey)) {
                noNewCount++;
                Logger.w("DoorData", "第" + page + "页首条与上页相同（翻页未生效），noNewCount=" + noNewCount);
                if (noNewCount >= 3) {
                    Logger.w("DoorData", "连续" + noNewCount + "页首条重复，停止翻页");
                    break;
                }
            } else {
                noNewCount = 0;
            }
            lastPageFirstKey = pageFirstKey;

            setStatus("📡 已拉取 " + all.size() + " 条门禁告警（第" + page + "/" + totalPages + "页）...");
            if (pageRows.size() < 300) {
                Logger.d("DoorData", "第" + page + "页仅" + pageRows.size() + "条（<300），判定为最后一页，停止翻页");
                break;
            }
        }
        Logger.d("DoorData", "fetchAllOmmsAlarms完成: 总页数=" + totalPages + "，服务器总记录数=" + serverTotal + "，实际翻页到第" + (all.size() > 0 ? (int)Math.ceil(all.size()/300.0) : 0) + "页，原始收集 " + all.size() + " 条（匹配阶段去重）");
        return all;
    }

    /** 从 OMMS 分页 HTML 解析总页数，如 "共51页 755条记录" → 51；解析失败返回 0 */
    private int parseTotalPages(String html) {
        // OMMS 分页信息样式1："共XX页"
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
                "共\\s*(\\d+)\\s*页", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if (m1.find()) {
            try { return Integer.parseInt(m1.group(1)); } catch (Exception ignored) {}
        }
        // 样式2：totalPages / pageCount 隐藏字段
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                "totalPages[^>]*value=[\"'](\\d+)[\"']|pageCount[\"']\\s*:\\s*(\\d+)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if (m2.find()) {
            String v = m2.group(1) != null ? m2.group(1) : m2.group(2);
            try { return Integer.parseInt(v); } catch (Exception ignored) {}
        }
        return 0; // 解析失败，由调用方用 MAX_PAGES 兜底
    }

    /** 从 OMMS 分页 HTML 解析总记录数，如 "共51页 810条记录" → 810；解析失败返回 0 */
    private int parseTotalRecords(String html) {
        // 样式1："共XX页 YYYY条记录"
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
                "共\\s*\\d+\\s*页\\s*(\\d+)\\s*条记录", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if (m1.find()) {
            try {
                int records = Integer.parseInt(m1.group(1));
                Logger.d("DoorData", "[服务器总记录数] 从分页信息解析: " + records + " 条");
                return records;
            } catch (Exception ignored) {}
        }
        // 样式2："YYYY条记录"
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                "(\\d+)\\s*条记录", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if (m2.find()) {
            try {
                int records = Integer.parseInt(m2.group(1));
                Logger.d("DoorData", "[服务器总记录数] 从文本解析: " + records + " 条");
                return records;
            } catch (Exception ignored) {}
        }
        Logger.w("DoorData", "[服务器总记录数] 未解析到总记录数");
        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HTML 解析
    // ─────────────────────────────────────────────────────────────────────

    private String extractCdata(String html) {
        // 门禁告警返回的是完整HTML（<html>开头），不是A4J AJAX响应
        // 直接返回原HTML，不需要提取CDATA
        if (html.contains("<html") || html.contains("<HTML")) {
            return html;
        }
        // A4J AJAX响应：提取CDATA
        if (html.contains("<ajax-response")) {
            StringBuilder sb = new StringBuilder();
            Matcher m = Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL).matcher(html);
            while (m.find()) sb.append(m.group(1));
            if (sb.length() > 0) return sb.toString();
        }
        return html;
    }

    // PAT_TD 改为提取 <td> 内部所有内容（去除HTML标签后提取纯文本）
    private static final Pattern PAT_TD = Pattern.compile(
            "<td[^>]*>(.*?)</td>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PAT_TIME = Pattern.compile(
            "\\d{4}[/-]\\d{2}[/-]\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?");

    /**
     * 从 OMMS 门禁历史告警 HTML 中解析告警行。
     *
     * ★ 2026-04-02 重写：基于固定列索引提取（对齐真实抓包 49 列结构）
     *
     * 真实列映射（49列，0-indexed，包含空列）：
     *   col[0]  = 空（checkbox列）
     *   col[1]  = 序号
     *   col[2]  = 告警ID（UUID）
     *   col[3]  = 告警级别（三级告警/四级告警）
     *   col[4]  = 空
     *   col[5]  = 省公司
     *   col[6]  = 市公司
     *   col[7]  = 县区
     *   col[8]  = ★ 站名（如"平阳朝阳中光"）
     *   col[9]  = 空
     *   col[10] = 维护方式
     *   col[11] = ★ 站址运维ID（如"33032600000717"，12位，注意不是站址编码！）
   *            ※ 站址运维ID ≠ 站址编码（18位），不能用此字段匹配数运蓝牙的station_code
   *            匹配蓝牙必须用站名（col[8]），因为两个系统的站址编码字段不互通
     *   col[12] = 告警源
     *   col[13] = ★ 告警名称（如"门磁开关状态(非智能门禁)"）
     *   col[14] = 设备编码
     *   col[15] = 设备名称（含FSU）
     *   col[16] = 设备ID
     *   col[17] = 厂家
     *   col[18] = 监控点名称（含门禁）
     *   col[19] = 告警标题
     *   col[20] = ★ 产生时间（如"2026/03/01 12:32:11"）
     *   col[21] = 恢复时间
     *   col[22] = 空
     *   col[23] = 采集机
     *   ...
     */
    private List<RawAlarm> parseAlarmRows(String html) {
        List<RawAlarm> list = new ArrayList<>();

        java.util.regex.Pattern TR_PAT = java.util.regex.Pattern.compile(
                "<tr\\s+class=\"rich-table-row[^>]*>(.*?)</tr>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher trM = TR_PAT.matcher(html);

        int trCount = 0, validTrCount = 0;
        while (trM.find()) {
            trCount++;
            String trContent = trM.group(1);
            if (trContent.contains("<thead") || trContent.contains("<tfoot")
                || !trContent.contains("<td")) continue;

            // ★ 提取所有 <td> 原始文本（保留空串，不跳过！确保索引对齐）
            List<String> allTds = new ArrayList<>();
            Matcher tdM = PAT_TD.matcher(trContent);
            while (tdM.find()) {
                String txt = tdM.group(1)
                        .replaceAll("<[^>]+>", "")
                        .replaceAll("&amp;","&").replaceAll("&lt;","<")
                        .replaceAll("&gt;",">").replaceAll("&nbsp;"," ")
                        .trim();
                allTds.add(txt);
            }

            // ★ 安全检查：列数不足则用模糊匹配兜底
            if (allTds.size() < 21) {
                // 列数太少，可能页面结构变了，用旧逻辑兜底
                RawAlarm fallback = parseByFallback(allTds, trContent);
                if (fallback != null) {
                    list.add(fallback);
                    validTrCount++;
                }
                continue;
            }

            RawAlarm row = new RawAlarm();

            // ★ 固定索引提取（对齐 2026-04-02 真实抓包 49 列结构）
            row.stName      = safeGet(allTds, 8);   // col[8]  = 站名
            row.stCode      = safeGet(allTds, 11);  // col[11] = 站址运维ID（12位，非站址编码！）
            row.stationCode = safeGet(allTds, 44);  // col[44] = 站址编码（18位）
            row.alarmName   = safeGet(allTds, 13);  // col[13] = 告警名称
            row.deviceId    = safeGet(allTds, 16);  // col[16] = 设备ID（FSU ID）

            // 时间提取（col[20] = 产生时间，格式 "2026/03/01 12:32:11"）
            String rawTime = safeGet(allTds, 20);
            Matcher tm = PAT_TIME.matcher(rawTime);
            if (tm.find()) {
                row.alarmTime = tm.group().trim().replace("/", "-");  // ★ 统一为连字符格式，匹配时才能解析
                if (row.alarmTime.length() == 16) row.alarmTime += ":00";
            }

            // 调试日志：打印前3行所有关键列，确认列索引是否正确
            if (validTrCount < 3) {
                Logger.d("DoorData", "行[" + validTrCount + "] totalTds=" + allTds.size()
                    + " col[8]站名=" + safeGet(allTds, 8)
                    + " col[11]运维ID=" + safeGet(allTds, 11)
                    + " col[44]站址编码=" + safeGet(allTds, 44)
                    + " col[13]告警名=" + safeGet(allTds, 13)
                    + " col[20]时间=" + row.alarmTime);
            }

            // 只保留有实际数据的行
            if (!row.stName.isEmpty() || !row.stCode.isEmpty() || !row.alarmTime.isEmpty()) {
                list.add(row);
                validTrCount++;
            }
        }
        Logger.d("DoorData", "parseAlarmRows: 共找到" + trCount + "个<tr>，有效行" + validTrCount + "条");
        return list;
    }

    /** 安全获取列表指定索引，越界返回空串 */
    private String safeGet(List<String> list, int index) {
        return (index >= 0 && index < list.size()) ? list.get(index) : "";
    }

    /** 兜底解析：当列数不足时，用旧的模糊匹配逻辑提取 */
    private RawAlarm parseByFallback(List<String> tds, String trContent) {
        RawAlarm row = new RawAlarm();
        // 提取站名
        for (String td : tds) {
            if (td.length() >= 3 && td.length() <= 20
                    && !PAT_TIME.matcher(td).find()
                    && !td.matches("\\d+")
                    && matchesChinese(td)) {
                row.stName = td; break;
            }
        }
        // 提取时间
        for (String td : tds) {
            Matcher tm = PAT_TIME.matcher(td);
            if (tm.find()) {
                row.alarmTime = tm.group().trim().replace("/", "-");  // ★ 统一为连字符格式
                if (row.alarmTime.length() == 16) row.alarmTime += ":00";
                break;
            }
        }
        // 提取告警名称
        for (String td : tds) {
            if (td.contains("门") && td.length() <= 30) {
                row.alarmName = td; break;
            }
        }
        if (row.alarmName == null || row.alarmName.isEmpty()) row.alarmName = "门禁告警";
        // 提取站码
        for (String td : tds) {
            if (td.matches("[A-Za-z0-9]{10,24}")) {
                row.stCode = td; break;
            }
        }
        if (row.stCode.isEmpty() && !row.stName.isEmpty()) {
            Matcher cm = Pattern.compile("\\(([A-Z0-9]{6,16})\\)").matcher(trContent);
            if (cm.find()) row.stCode = cm.group(1);
        }
        if (!row.stName.isEmpty() || !row.stCode.isEmpty() || !row.alarmTime.isEmpty()) {
            return row;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 通过站名在 OMMS FSU 列表页查询 fsuid（objid）
     * 用于远程开门时间匹配：站名 → queryFsuList → 解析 objid
     * 返回第一个匹配的 fsuid，找不到返回空串
     */
    private String queryFsuidByStationName(String stationName) {
        try {
            String resp = AccessControlApi.queryFsuList(stationName);
            if (resp == null || resp.isEmpty()) return "";
            // 解析 AJAX HTML 响应中的 objid（FSU的唯一ID）
            // OMMS 返回表格行，每行含 objid 字段（通常在链接或隐藏域中）
            // 格式：<input type="hidden" name="..." value="fsuObjId"/>
            //   或：javascript:showEntranceGuard('objid_value','...')
            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
                    "showEntranceGuard\\('([^']+)'").matcher(resp);
            if (m1.find()) return m1.group(1);

            // 备选：找 objid 隐藏域
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                    "fsuEntranceId[^=]*=([A-Za-z0-9]+)").matcher(resp);
            if (m2.find()) return m2.group(1);

            // 备选：链接中的 objid 参数
            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile(
                    "objid=([A-Za-z0-9]+)").matcher(resp);
            if (m3.find()) return m3.group(1);

            Logger.d("DoorData", "[queryFsuid] 未解析到fsuid stationName=" + stationName
                    + " respPreview=" + resp.substring(0, Math.min(300, resp.length())));
        } catch (Exception e) {
            Logger.e("DoorData", "[queryFsuid] 异常: " + e.getMessage());
        }
        return "";
    }

    private static Date parseDate(SimpleDateFormat sdf, String s) {
        if (s == null || s.isEmpty()) return null;
        try { return sdf.parse(s); } catch (Exception e) { return null; }
    }

    private static String normName(String name) {
        if (name == null || name.isEmpty()) return "";
        // 去掉基站、铁塔、末尾的站
        return name.trim()
                .replaceAll("基站|铁塔", "")
                .replaceAll("站$", "")
                .trim();
    }

    private static boolean matchesChinese(String s) {
        return Pattern.compile("[\\u4e00-\\u9fa5]").matcher(s).find();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 统计更新（主线程）
    // ─────────────────────────────────────────────────────────────────────

    private void updateSummary(List<DoorAlarmRow> result) {
        if (!isAdded()) return;
        int total = result.size();
        int ok = 0, fail = 0;
        for (DoorAlarmRow r : result) { if (r.qualified) ok++; else fail++; }
        if (total > 0) {
            tvTotal.setText(String.valueOf(total));
            tvOk.setText(String.valueOf(ok));
            tvFail.setText(String.valueOf(fail));
            layoutSummary.setVisibility(View.VISIBLE);
            layoutHeader.setVisibility(View.VISIBLE);
            tvStatus.setText("查询完成");  // ★ 新增：有数据时显示查询完成
        } else {
            Session s = Session.get();
            if (s.ommsCookie == null || s.ommsCookie.isEmpty()) {
                tvStatus.setText("请先在「门禁系统」Tab完成OMMS登录");
            } else {
                tvStatus.setText("暂无门禁告警数据");
            }
        }
    }

    private void setStatus(final String msg) {
        ThreadManager.runOnUiThread(() -> {
            if (!isAdded() || tvStatus == null) return;
            tvStatus.setText(msg);
        });
    }

    // ── 4A Token 配置 ───────────────────────────────────────────────────

    /**
     * 初始化4A Token配置：恢复已保存的Token和区县选择
     */
    private void init4ATokenConfig() {
        Session s = Session.get();
        // ★ 每次初始化时重新加载Session，确保从SharedPreferences读取最新保存的token
        s.loadConfig(requireContext());

        // 恢复Token
        if (et4AToken != null && s.tower4aToken != null && !s.tower4aToken.isEmpty()) {
            et4AToken.setText(s.tower4aToken);
        }

        // 初始化区县选择器
        if (spinnerCounty != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                    android.R.layout.simple_spinner_item, DOOR_COUNTY_NAMES) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    ((TextView) v).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9);
                    return v;
                }
                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    ((TextView) v).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
                    return v;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCounty.setAdapter(adapter);

            // 恢复保存的区县选择
            if (s.tower4aCountyCode != null && !s.tower4aCountyCode.isEmpty()) {
                for (int i = 0; i < DOOR_COUNTY_CODES.length; i++) {
                    if (DOOR_COUNTY_CODES[i].equals(s.tower4aCountyCode)) {
                        spinnerCounty.setSelection(i);
                        selectedDoorCountyIndex = i;
                        break;
                    }
                }
            }

            // 区县选择监听
            spinnerCounty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedDoorCountyIndex = position;
                    Session.get().tower4aCountyCode = DOOR_COUNTY_CODES[position];
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    /**
     * 保存4A Token和区县代码
     */
    private void doSave4AToken() {
        String tokenInput = et4AToken != null ? et4AToken.getText().toString().trim() : "";
        String countyCode = DOOR_COUNTY_CODES[selectedDoorCountyIndex];
        String countyName = DOOR_COUNTY_NAMES[selectedDoorCountyIndex];

        // 自动去掉 "Bearer " 前缀
        if (tokenInput.toLowerCase().startsWith("bearer ")) {
            tokenInput = tokenInput.substring(7).trim();
        }

        Session sv = Session.get();
        sv.tower4aToken = tokenInput;
        sv.tower4aCountyCode = countyCode;
        sv.saveTower4aToken(requireContext());

        Toast.makeText(requireContext(),
                tokenInput.isEmpty() ? "4A Token 已清空" : "✅ 已保存: " + countyName + "(" + tokenInput.length() + "字符)",
                Toast.LENGTH_SHORT).show();
    }

    // ── 门禁审批认证配置 ───────────────────────────────────────────────

}

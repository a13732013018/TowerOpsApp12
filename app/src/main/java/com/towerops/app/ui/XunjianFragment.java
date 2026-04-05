package com.towerops.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.towerops.app.R;
import com.towerops.app.api.XunjianApi;
import com.towerops.app.model.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 巡检工单 Fragment
 *
 * 三个子 Tab：
 *   0 - 未巡检工单（列表 + 分组统计）
 *   1 - 智联巡检（未开始）
 *   2 - APP 巡检质检（任务列表 / 质检详情 / 统计报表）
 */
public class XunjianFragment extends Fragment {

    // ===================== UI 控件 =====================
    // 顶层导航 Tab
    private TextView tabUnstart, tabZhilian, tabQuality;
    private ViewFlipper viewFlipper;

    // Panel 0: 未巡检
    private TextView tvUnstartCount;
    private Button btnQueryUnstart;
    private LinearLayout llUnstartList, llUnstartStat;
    // 未巡检表头
    private TextView thUnstartSeq, thUnstartGroup, thUnstartTasksn, thUnstartStation, thUnstartMajor, thUnstartDate;

    // Panel 1: 智联巡检
    private TextView tvZhilianXunjianCount;
    private Button btnQueryZhilianXunjian;
    private LinearLayout llZhilianXunjianList;
    // 智联巡检表头
    private TextView thZhilianSeq, thZhilianGroup, thZhilianTasksn, thZhilianStation, thZhilianMajor, thZhilianCreatetime;

    // Panel 2: APP 质检
    private Spinner spinnerPlanYear, spinnerQualityYear, spinnerQualityMonth;
    private CheckBox cbQualityByMonth, cbQualityToday, cbShowAll;
    private Button btnQueryQuality;
    private ProgressBar progressQuality;
    private TextView tvQualityProgress;
    private TextView tabQualityTask, tabQualityDetail, tabQualityReport;
    private ViewFlipper viewFlipperQuality;
    private LinearLayout llQualityTaskList, llQualityDetailList, llQualityReport;
    // APP质检表头
    private TextView thTaskSeq, thTaskGroup, thTaskStation, thTaskMajor, thTaskProgress, thTaskStart, thTaskEnd;
    private TextView thDetailSeq, thDetailGroup, thDetailStation, thDetailMajor, thDetailProject, thDetailResult, thDetailPass, thDetailReason;

    // ===================== 数据缓存（用于排序）=====================

    /** 未巡检行缓存 */
    private static class UnstartRow {
        int    seq; String group, tasksn, deviceid, stationname, applymajor, date;
    }
    /** 智联巡检行缓存 */
    private static class ZhilianRow {
        int    seq; String group, tasksn, deviceid, stationname, applymajor, createtime;
    }
    /** APP质检任务行缓存 */
    private static class QualityTaskRow {
        int    seq; String group, tasksn, deviceid, stationcode, stationname, remark,
               applymajor, mainplanname, progress, starttime, endtime, pollingperiod,
               inspecttime, taskuser;
    }

    private final List<UnstartRow>              unstartRows     = new ArrayList<>();
    private final List<ZhilianRow>              zhilianRows     = new ArrayList<>();
    private final List<QualityTaskRow>          qualityTaskRows = new ArrayList<>();
    private final List<XunjianApi.AppQualityDetail> qualityDetailRows = new ArrayList<>();

    // 排序状态（每个面板独立：sortCol=列索引，sortAsc=升序/降序）
    private int  unstartSortCol   = -1; private boolean unstartSortAsc   = true;
    private int  zhilianSortCol   = -1; private boolean zhilianSortAsc   = true;
    private int  taskSortCol      = -1; private boolean taskSortAsc      = true;
    private int  detailSortCol    = -1; private boolean detailSortAsc    = true;

    // ===================== 状态 =====================
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int currentMainTab = 0;
    private int currentQualityTab = 0;

    // UI批量渲染节流：避免高频刷新卡主线程
    private final java.util.concurrent.atomic.AtomicBoolean taskRenderPending   = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean detailRenderPending = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final long RENDER_DELAY_MS = 400; // 每400ms最多全量刷新一次

    // APP 质检并发控制
    private final Object qualityLock = new Object();
    private volatile int qualityRunning = 0;
    private volatile int qualityFinished = 0;
    private volatile int qualityTotal = 0;
    private volatile boolean isQualityRunning = false;
    private volatile int qualityYear = 2026;
    private volatile int qualityMonth = 3;
    // 并发信号量：最多同时30个网络任务，速度快但不会压垮系统
    private Semaphore qualitySemaphore = new Semaphore(30);
    // 瀑布流行号计数（原子递增，线程安全）
    private final AtomicInteger taskRowCounter   = new AtomicInteger(0);
    private final AtomicInteger detailRowCounter = new AtomicInteger(0);
    private volatile boolean qualityByMonth = false;
    private volatile boolean qualityTodayOnly = false;
    private volatile String currentPlanName = "2026";

    // 统计数组（索引0=汇总 1-5=各组）
    private final int[] statRoomTotal = new int[7];
    private final int[] statRoomDone  = new int[7];
    private final int[] statCabTotal  = new int[7];
    private final int[] statCabDone   = new int[7];
    private final int[] statRruTotal  = new int[7];
    private final int[] statRruDone   = new int[7];
    private final int[] statTowerTotal= new int[7];
    private final int[] statTowerDone = new int[7];
    private final int[] statTodayRoom = new int[7];
    private final int[] statTodayTower= new int[7];

    // 任务池
    private final List<XunjianApi.TaskPackage> taskPool = new ArrayList<>();
    private ExecutorService qualityExecutor;

    // ===================== 分组常量 =====================
    // 区域0（平阳）：综合1-5组
    private static final String[][] TASKUSER_AREA0 = {
        {"陶大取", "卢智伟"},
        {"陈龙", "林元龙"},
        {"高树调", "倪传井"},
        {"苏忠前", "许方喜"},
        {"黄经兴", "蔡亮"},
    };
    private static final String[] GROUPNAMES_AREA0 = {
        "综合1组", "综合2组", "综合3组", "综合4组", "综合5组"
    };
    // 区域1（泰顺）：4个片区
    private static final String[][] TASKUSER_AREA1 = {
        {"朱兴达"},
        {"王成"},
        {"夏念悦"},
        {"胡叙渐"},
    };
    private static final String[] GROUPNAMES_AREA1 = {
        "罗阳片区", "雅阳片区", "泗溪片区", "仕阳片区"
    };

    // ===================== 生命周期 =====================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_xunjian, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupTabListeners();
        setupQualitySpinners();
        selectMainTab(0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isQualityRunning = false;
        if (qualityExecutor != null && !qualityExecutor.isShutdown()) {
            qualityExecutor.shutdownNow();
        }
    }

    // ===================== 绑定控件 =====================

    private void bindViews(View v) {
        tabUnstart  = v.findViewById(R.id.tabXunjianUnstart);
        tabZhilian  = v.findViewById(R.id.tabXunjianZhilian);
        tabQuality  = v.findViewById(R.id.tabXunjianQuality);
        viewFlipper = v.findViewById(R.id.viewFlipperXunjian);

        // Panel 0
        tvUnstartCount  = v.findViewById(R.id.tvUnstartCount);
        btnQueryUnstart = v.findViewById(R.id.btnQueryUnstart);
        llUnstartList   = v.findViewById(R.id.llUnstartList);
        llUnstartStat   = v.findViewById(R.id.llUnstartStat);
        thUnstartSeq     = v.findViewById(R.id.thUnstartSeq);
        thUnstartGroup   = v.findViewById(R.id.thUnstartGroup);
        thUnstartTasksn  = v.findViewById(R.id.thUnstartTasksn);
        thUnstartStation = v.findViewById(R.id.thUnstartStation);
        thUnstartMajor   = v.findViewById(R.id.thUnstartMajor);
        thUnstartDate    = v.findViewById(R.id.thUnstartDate);

        // Panel 1
        tvZhilianXunjianCount  = v.findViewById(R.id.tvZhilianXunjianCount);
        btnQueryZhilianXunjian = v.findViewById(R.id.btnQueryZhilianXunjian);
        llZhilianXunjianList   = v.findViewById(R.id.llZhilianXunjianList);
        thZhilianSeq        = v.findViewById(R.id.thZhilianSeq);
        thZhilianGroup      = v.findViewById(R.id.thZhilianGroup);
        thZhilianTasksn     = v.findViewById(R.id.thZhilianTasksn);
        thZhilianStation    = v.findViewById(R.id.thZhilianStation);
        thZhilianMajor      = v.findViewById(R.id.thZhilianMajor);
        thZhilianCreatetime = v.findViewById(R.id.thZhilianCreatetime);

        // Panel 2
        spinnerPlanYear    = v.findViewById(R.id.spinnerPlanYear);
        spinnerQualityYear = v.findViewById(R.id.spinnerQualityYear);
        spinnerQualityMonth= v.findViewById(R.id.spinnerQualityMonth);
        cbQualityByMonth   = v.findViewById(R.id.cbQualityByMonth);
        cbQualityToday     = v.findViewById(R.id.cbQualityToday);
        cbShowAll          = v.findViewById(R.id.cbShowAll);
        btnQueryQuality    = v.findViewById(R.id.btnQueryQuality);
        progressQuality    = v.findViewById(R.id.progressQuality);
        tvQualityProgress  = v.findViewById(R.id.tvQualityProgress);

        tabQualityTask   = v.findViewById(R.id.tabQualityTask);
        tabQualityDetail = v.findViewById(R.id.tabQualityDetail);
        tabQualityReport = v.findViewById(R.id.tabQualityReport);
        viewFlipperQuality = v.findViewById(R.id.viewFlipperQuality);

        llQualityTaskList   = v.findViewById(R.id.llQualityTaskList);
        llQualityDetailList = v.findViewById(R.id.llQualityDetailList);
        llQualityReport     = v.findViewById(R.id.llQualityReport);

        thTaskSeq      = v.findViewById(R.id.thTaskSeq);
        thTaskGroup    = v.findViewById(R.id.thTaskGroup);
        thTaskStation  = v.findViewById(R.id.thTaskStation);
        thTaskMajor    = v.findViewById(R.id.thTaskMajor);
        thTaskProgress = v.findViewById(R.id.thTaskProgress);
        thTaskStart    = v.findViewById(R.id.thTaskStart);
        thTaskEnd      = v.findViewById(R.id.thTaskEnd);

        thDetailSeq     = v.findViewById(R.id.thDetailSeq);
        thDetailGroup   = v.findViewById(R.id.thDetailGroup);
        thDetailStation = v.findViewById(R.id.thDetailStation);
        thDetailMajor   = v.findViewById(R.id.thDetailMajor);
        thDetailProject = v.findViewById(R.id.thDetailProject);
        thDetailResult  = v.findViewById(R.id.thDetailResult);
        thDetailPass    = v.findViewById(R.id.thDetailPass);
        thDetailReason  = v.findViewById(R.id.thDetailReason);
    }

    // ===================== Tab 切换 =====================

    private void setupTabListeners() {
        tabUnstart.setOnClickListener(v -> selectMainTab(0));
        tabZhilian.setOnClickListener(v -> selectMainTab(1));
        tabQuality.setOnClickListener(v -> selectMainTab(2));

        btnQueryUnstart.setOnClickListener(v -> startQueryUnstart());
        btnQueryZhilianXunjian.setOnClickListener(v -> startQueryZhilian());
        btnQueryQuality.setOnClickListener(v -> startQueryQuality());

        tabQualityTask.setOnClickListener(v -> selectQualityTab(0));
        tabQualityDetail.setOnClickListener(v -> selectQualityTab(1));
        tabQualityReport.setOnClickListener(v -> selectQualityTab(2));

        // 未巡检表头排序（列索引 0=序 1=分组 2=工单号 3=站点 4=专业 5=日期）
        View.OnClickListener unstartHeaderClick = v -> {
            int col = 0;
            if (v == thUnstartSeq)     col = 0;
            else if (v == thUnstartGroup)   col = 1;
            else if (v == thUnstartTasksn)  col = 2;
            else if (v == thUnstartStation) col = 3;
            else if (v == thUnstartMajor)   col = 4;
            else if (v == thUnstartDate)    col = 5;
            if (unstartSortCol == col) unstartSortAsc = !unstartSortAsc;
            else { unstartSortCol = col; unstartSortAsc = true; }
            sortAndRenderUnstart();
        };
        thUnstartSeq.setOnClickListener(unstartHeaderClick);
        thUnstartGroup.setOnClickListener(unstartHeaderClick);
        thUnstartTasksn.setOnClickListener(unstartHeaderClick);
        thUnstartStation.setOnClickListener(unstartHeaderClick);
        thUnstartMajor.setOnClickListener(unstartHeaderClick);
        thUnstartDate.setOnClickListener(unstartHeaderClick);

        // 智联巡检表头排序（列索引 0=序 1=分组 2=工单号 3=站点 4=专业 5=创建时间）
        View.OnClickListener zhilianHeaderClick = v -> {
            int col = 0;
            if (v == thZhilianSeq)         col = 0;
            else if (v == thZhilianGroup)       col = 1;
            else if (v == thZhilianTasksn)      col = 2;
            else if (v == thZhilianStation)     col = 3;
            else if (v == thZhilianMajor)       col = 4;
            else if (v == thZhilianCreatetime)  col = 5;
            if (zhilianSortCol == col) zhilianSortAsc = !zhilianSortAsc;
            else { zhilianSortCol = col; zhilianSortAsc = true; }
            sortAndRenderZhilian();
        };
        thZhilianSeq.setOnClickListener(zhilianHeaderClick);
        thZhilianGroup.setOnClickListener(zhilianHeaderClick);
        thZhilianTasksn.setOnClickListener(zhilianHeaderClick);
        thZhilianStation.setOnClickListener(zhilianHeaderClick);
        thZhilianMajor.setOnClickListener(zhilianHeaderClick);
        thZhilianCreatetime.setOnClickListener(zhilianHeaderClick);

        // APP质检任务表头排序（列索引 0=序 1=分组 2=站点 3=专业 4=进度 5=开始 6=结束）
        View.OnClickListener taskHeaderClick = v -> {
            int col = 0;
            if (v == thTaskSeq)       col = 0;
            else if (v == thTaskGroup)    col = 1;
            else if (v == thTaskStation)  col = 2;
            else if (v == thTaskMajor)    col = 3;
            else if (v == thTaskProgress) col = 4;
            else if (v == thTaskStart)    col = 5;
            else if (v == thTaskEnd)      col = 6;
            if (taskSortCol == col) taskSortAsc = !taskSortAsc;
            else { taskSortCol = col; taskSortAsc = true; }
            sortAndRenderQualityTask();
        };
        thTaskSeq.setOnClickListener(taskHeaderClick);
        thTaskGroup.setOnClickListener(taskHeaderClick);
        thTaskStation.setOnClickListener(taskHeaderClick);
        thTaskMajor.setOnClickListener(taskHeaderClick);
        thTaskProgress.setOnClickListener(taskHeaderClick);
        thTaskStart.setOnClickListener(taskHeaderClick);
        thTaskEnd.setOnClickListener(taskHeaderClick);

        // 质检详情表头排序（列索引 0=序 1=分组 2=站点 3=专业 4=巡检项目 5=结果 6=质检 7=质检问题）
        View.OnClickListener detailHeaderClick = v -> {
            int col = 0;
            if (v == thDetailSeq)      col = 0;
            else if (v == thDetailGroup)   col = 1;
            else if (v == thDetailStation) col = 2;
            else if (v == thDetailMajor)   col = 3;
            else if (v == thDetailProject) col = 4;
            else if (v == thDetailResult)  col = 5;
            else if (v == thDetailPass)    col = 6;
            else if (v == thDetailReason)  col = 7;
            if (detailSortCol == col) detailSortAsc = !detailSortAsc;
            else { detailSortCol = col; detailSortAsc = true; }
            sortAndRenderQualityDetail();
        };
        thDetailSeq.setOnClickListener(detailHeaderClick);
        thDetailGroup.setOnClickListener(detailHeaderClick);
        thDetailStation.setOnClickListener(detailHeaderClick);
        thDetailMajor.setOnClickListener(detailHeaderClick);
        thDetailProject.setOnClickListener(detailHeaderClick);
        thDetailResult.setOnClickListener(detailHeaderClick);
        thDetailPass.setOnClickListener(detailHeaderClick);
        thDetailReason.setOnClickListener(detailHeaderClick);
    }

    private void selectMainTab(int idx) {
        currentMainTab = idx;
        viewFlipper.setDisplayedChild(idx);

        // 浅色主题：激活=蓝色底白字，非激活=透明底深灰字
        int activeBg     = Color.parseColor("#2563EB");
        int normalBg     = Color.TRANSPARENT;
        int activeText   = Color.WHITE;
        int normalText   = Color.parseColor("#374151");

        tabUnstart.setBackgroundColor(idx == 0 ? activeBg : normalBg);
        tabZhilian.setBackgroundColor(idx == 1 ? activeBg : normalBg);
        tabQuality.setBackgroundColor(idx == 2 ? activeBg : normalBg);

        tabUnstart.setTextColor(idx == 0 ? activeText : normalText);
        tabZhilian.setTextColor(idx == 1 ? activeText : normalText);
        tabQuality.setTextColor(idx == 2 ? activeText : normalText);
    }

    private void selectQualityTab(int idx) {
        currentQualityTab = idx;
        viewFlipperQuality.setDisplayedChild(idx);

        int white = Color.WHITE;
        int gray  = Color.parseColor("#F3F4F6");
        int textActive  = Color.parseColor("#111827");
        int textInactive= Color.parseColor("#6B7280");

        tabQualityTask.setBackgroundColor(idx == 0 ? white : gray);
        tabQualityDetail.setBackgroundColor(idx == 1 ? white : gray);
        tabQualityReport.setBackgroundColor(idx == 2 ? white : gray);

        tabQualityTask.setTextColor(idx == 0 ? textActive : textInactive);
        tabQualityDetail.setTextColor(idx == 1 ? textActive : textInactive);
        tabQualityReport.setTextColor(idx == 2 ? textActive : textInactive);
    }

    // ===================== 初始化 Spinners =====================

    private void setupQualitySpinners() {
        // 计划年份
        List<String> years = new ArrayList<>();
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = curYear; y >= curYear - 2; y--) years.add(String.valueOf(y));
        years.add("全部");
        spinnerPlanYear.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, years));

        // 质检年份
        List<String> qYears = new ArrayList<>();
        for (int y = curYear; y >= curYear - 1; y--) qYears.add(String.valueOf(y));
        spinnerQualityYear.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, qYears));
        spinnerQualityYear.setSelection(0);

        // 质检月份
        List<String> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) months.add(String.valueOf(m));
        spinnerQualityMonth.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, months));
        int curMonth = Calendar.getInstance().get(Calendar.MONTH); // 0-based
        spinnerQualityMonth.setSelection(curMonth);
    }

    // =========================================================
    // === Panel 0: 未巡检工单 ===
    // =========================================================

    private void startQueryUnstart() {
        if (Session.get().userid.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        btnQueryUnstart.setEnabled(false);
        btnQueryUnstart.setText("查询中...");
        llUnstartList.removeAllViews();
        llUnstartStat.removeAllViews();
        unstartRows.clear();
        unstartSortCol = -1; unstartSortAsc = true;
        updateUnstartHeaders();

        new Thread(() -> {
            String json = XunjianApi.getUnstartList();
            mainHandler.post(() -> {
                btnQueryUnstart.setEnabled(true);
                btnQueryUnstart.setText("查询");
                parseAndShowUnstart(json);
            });
        }).start();
    }

    private void parseAndShowUnstart(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("taskInfoList");
            if (arr == null) {
                tvUnstartCount.setText("未巡检: 0 条（解析失败）");
                return;
            }

            // ③ 纯专业计数：0=机房及配套 1=机柜及配套 2=RRU拉远 3=铁塔 4=室内分布 5=其他
            int[] majorCount = new int[6];

            int total = arr.length();
            tvUnstartCount.setText("未巡检: " + total + " 条");

            for (int i = 0; i < total; i++) {
                JSONObject item = arr.getJSONObject(i);
                String stationname  = XunjianApi.cleanNull(item.optString("stationname"));
                String applymajor   = XunjianApi.cleanNull(item.optString("applymajor"));
                String tasksn       = XunjianApi.cleanNull(item.optString("tasksn"));
                String deviceid     = XunjianApi.cleanNull(item.optString("deviceid"));
                String date         = XunjianApi.cleanNull(item.optString("date"));

                // 专业计数
                int majorIdx;
                switch (applymajor) {
                    case "机房及配套": majorIdx = 0; break;
                    case "机柜及配套": majorIdx = 1; break;
                    case "RRU拉远":   majorIdx = 2; break;
                    case "铁塔":      majorIdx = 3; break;
                    case "室内分布":  majorIdx = 4; break;
                    default:          majorIdx = 5; break;
                }
                majorCount[majorIdx]++;

                // 分组匹配（列表显示用）
                String groupName = matchGroup(stationname);

                // 缓存数据
                UnstartRow r = new UnstartRow();
                r.seq = i + 1; r.group = groupName; r.tasksn = tasksn;
                r.deviceid = deviceid; r.stationname = stationname;
                r.applymajor = applymajor; r.date = date;
                unstartRows.add(r);
            }

            // 渲染列表（默认顺序）
            sortAndRenderUnstart();
            // 渲染统计
            renderUnstartStat(majorCount);

        } catch (Exception e) {
            e.printStackTrace();
            tvUnstartCount.setText("未巡检: 解析异常");
        }
    }


    private void renderUnstartStat(int[] majorCount) {
        llUnstartStat.removeAllViews();
        int grandTotal = 0;
        for (int c : majorCount) grandTotal += c;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 6, 4, 6);
        row.setBackgroundColor(Color.WHITE);

        // 专业颜色（浅色背景适配）：有数量高亮深色，无则浅灰
        int[] colors = {
            Color.parseColor("#2563EB"), // 机房 - 蓝
            Color.parseColor("#2563EB"), // 机柜 - 蓝
            Color.parseColor("#0891B2"), // 拉远 - 青
            Color.parseColor("#DC2626"), // 铁塔 - 红
            Color.parseColor("#7C3AED"), // 室分 - 紫
            Color.parseColor("#9CA3AF"), // 其他 - 灰
        };
        for (int m = 0; m < 6; m++) {
            int val = majorCount[m];
            TextView tv = addCellWeight(row, 1, String.valueOf(val), 12, Gravity.CENTER);
            tv.setTextColor(val > 0 ? colors[m] : Color.parseColor("#D1D5DB"));
            if (val > 0) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        TextView tvTotal = addCell(row, 40, String.valueOf(grandTotal), 12, Gravity.CENTER);
        tvTotal.setTextColor(Color.parseColor("#059669"));
        tvTotal.setTypeface(null, android.graphics.Typeface.BOLD);

        llUnstartStat.addView(row);
    }

    // =========================================================
    // === Panel 1: 智联巡检 ===
    // =========================================================

    private void startQueryZhilian() {
        if (Session.get().userid.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        btnQueryZhilianXunjian.setEnabled(false);
        btnQueryZhilianXunjian.setText("查询中...");
        llZhilianXunjianList.removeAllViews();
        zhilianRows.clear();
        zhilianSortCol = -1; zhilianSortAsc = true;
        updateZhilianHeaders();

        new Thread(() -> {
            String json = XunjianApi.getZhilianXunjianList();
            mainHandler.post(() -> {
                btnQueryZhilianXunjian.setEnabled(true);
                btnQueryZhilianXunjian.setText("查询");
                parseAndShowZhilianXunjian(json);
            });
        }).start();
    }

    private void parseAndShowZhilianXunjian(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                tvZhilianXunjianCount.setText("智联巡检: 0 条（解析失败）");
                return;
            }
            JSONArray records = data.optJSONArray("records");
            if (records == null) records = new JSONArray();

            int total = records.length();
            tvZhilianXunjianCount.setText("智联巡检: " + total + " 条");

            for (int i = 0; i < total; i++) {
                JSONObject item = records.getJSONObject(i);
                String tasksn      = XunjianApi.cleanNull(item.optString("tasksn"));
                String stationName = XunjianApi.cleanNull(item.optString("stationName"));
                String deviceid    = XunjianApi.cleanNull(item.optString("deviceid"));
                String applymajor  = XunjianApi.cleanNull(item.optString("applymajor"));
                String createtime  = XunjianApi.cleanNull(item.optString("createtime"));
                String mainPlanName= XunjianApi.cleanNull(item.optString("mainPlanName"));

                String groupName = matchGroup(stationName);
                ZhilianRow r = new ZhilianRow();
                r.seq = i + 1; r.group = groupName; r.tasksn = tasksn;
                r.deviceid = deviceid; r.stationname = stationName;
                r.applymajor = applymajor; r.createtime = createtime;
                zhilianRows.add(r);
            }

            sortAndRenderZhilian();

        } catch (Exception e) {
            e.printStackTrace();
            tvZhilianXunjianCount.setText("智联巡检: 解析异常");
        }
    }


    // =========================================================
    // === Panel 2: APP 巡检质检 ===
    // =========================================================

    private void startQueryQuality() {
        if (isQualityRunning) {
            Toast.makeText(getContext(), "正在查询中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Session.get().userid.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 读取 UI 配置
        String planYearStr = (String) spinnerPlanYear.getSelectedItem();
        currentPlanName = "全部".equals(planYearStr) ? "2026" : planYearStr;
        qualityByMonth  = cbQualityByMonth.isChecked();
        qualityTodayOnly= cbQualityToday.isChecked();
        final boolean showAll = cbShowAll.isChecked(); // 全显：不过滤今日
        try {
            qualityYear = Integer.parseInt((String) spinnerQualityYear.getSelectedItem());
        } catch (Exception e) { qualityYear = Calendar.getInstance().get(Calendar.YEAR); }
        try {
            qualityMonth = Integer.parseInt((String) spinnerQualityMonth.getSelectedItem());
        } catch (Exception e) { qualityMonth = Calendar.getInstance().get(Calendar.MONTH) + 1; }

        // 清空 UI
        llQualityTaskList.removeAllViews();
        llQualityDetailList.removeAllViews();
        llQualityReport.removeAllViews();
        progressQuality.setProgress(0);
        tvQualityProgress.setText("0%");
        synchronized (qualityTaskRows)   { qualityTaskRows.clear(); }
        synchronized (qualityDetailRows) { qualityDetailRows.clear(); }
        taskRenderPending.set(false);
        detailRenderPending.set(false);
        taskRowCounter.set(0);
        detailRowCounter.set(0);
        taskSortCol = -1; taskSortAsc = true; updateTaskHeaders();
        detailSortCol = -1; detailSortAsc = true; updateDetailHeaders();

        // 重置统计
        resetStats();

        isQualityRunning = true;
        btnQueryQuality.setEnabled(false);

        new Thread(() -> runQualityQuery(showAll)).start();
    }

    private void runQualityQuery(boolean showAll) {
        // 步骤1：获取主计划列表
        String planJson = XunjianApi.getPlanList(currentPlanName);
        if (planJson == null || planJson.isEmpty()) {
            mainHandler.post(() -> {
                isQualityRunning = false;
                btnQueryQuality.setEnabled(true);
                Toast.makeText(getContext(), "获取计划列表失败", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        // 步骤2：解析计划列表，组建任务池
        synchronized (taskPool) {
            taskPool.clear();
        }
        try {
            buildTaskPool(planJson, showAll);
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized (qualityLock) {
            qualityTotal    = taskPool.size();
            qualityFinished = 0;
            qualityRunning  = 0;
        }

        if (qualityTotal == 0) {
            mainHandler.post(() -> {
                isQualityRunning = false;
                btnQueryQuality.setEnabled(true);
                tvQualityProgress.setText("无任务");
                Toast.makeText(getContext(), "未找到巡检任务", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        // 步骤3：并发处理（CachedThreadPool无排队延迟，Semaphore限制真实并发30）
        if (qualityExecutor != null && !qualityExecutor.isShutdown()) {
            qualityExecutor.shutdownNow();
        }
        qualitySemaphore = new Semaphore(30);
        taskRowCounter.set(0);
        detailRowCounter.set(0);
        qualityExecutor = Executors.newCachedThreadPool();

        for (int i = 0; i < qualityTotal; i++) {
            if (!isQualityRunning) break;
            final int idx = i;
            qualityExecutor.submit(() -> {
                try {
                    qualitySemaphore.acquire();
                    processOneTask(idx);
                } catch (InterruptedException ignored) {
                } finally {
                    qualitySemaphore.release();
                }
            });
        }

        // 等待所有任务完成（超时 180 秒，每100ms检查一次，不阻塞主线程）
        long deadline = System.currentTimeMillis() + 180_000L;
        while (System.currentTimeMillis() < deadline) {
            synchronized (qualityLock) {
                if (qualityFinished >= qualityTotal) break;
            }
            try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
        }

        // 完成：更新状态，渲染统计报表（任务列表和详情已由瀑布流实时填充完毕）
        mainHandler.post(() -> {
            progressQuality.setProgress(100);
            tvQualityProgress.setText("100%");
            isQualityRunning = false;
            btnQueryQuality.setEnabled(true);
            renderQualityReport();
        });
    }

    /** 解析计划列表 JSON，为每个任务建立 TaskPackage 并放入 taskPool */
    private void buildTaskPool(String planJson, boolean showAll) throws Exception {
        JSONObject root = new JSONObject(planJson);
        JSONArray planList = root.optJSONArray("planList");
        if (planList == null) return;

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (int i = 0; i < planList.length(); i++) {
            JSONObject plan = planList.getJSONObject(i);
            String mainplanid   = plan.optString("mainplanid");
            String mainplanname = XunjianApi.cleanNull(plan.optString("mainplanname"));
            String applymajor   = XunjianApi.cleanNull(plan.optString("applymajor"));
            String allnum       = XunjianApi.cleanNull(plan.optString("allnum"));
            String finishnum    = XunjianApi.cleanNull(plan.optString("finishnum"));
            String remark       = XunjianApi.cleanNull(plan.optString("remark"));

            // 获取该计划下的具体任务
            String taskJson = XunjianApi.getTaskListByPlanId(mainplanid);
            if (taskJson == null || taskJson.isEmpty()) continue;

            JSONObject taskRoot = new JSONObject(taskJson);
            JSONArray taskArr   = taskRoot.optJSONArray("taskInfoList");
            if (taskArr == null) continue;

            for (int j = 0; j < taskArr.length(); j++) {
                JSONObject t = taskArr.getJSONObject(j);
                XunjianApi.TaskPackage pkg = new XunjianApi.TaskPackage();
                pkg.mainplanname = mainplanname;
                pkg.applymajor   = applymajor;
                pkg.allnum       = allnum;
                pkg.finishnum    = finishnum;
                pkg.remark       = remark;
                pkg.endtime      = XunjianApi.cleanNull(t.optString("endtime"));
                pkg.starttime    = XunjianApi.cleanNull(t.optString("starttime"));
                pkg.stationname  = XunjianApi.cleanNull(t.optString("stationname"));
                pkg.taskuser     = XunjianApi.cleanNull(t.optString("taskuser"));
                pkg.stationcode  = XunjianApi.cleanNull(t.optString("stationcode"));
                pkg.inspecttime  = XunjianApi.cleanNull(t.optString("inspecttime"));
                pkg.taskid       = XunjianApi.cleanNull(t.optString("taskid"));
                pkg.taskuserid   = XunjianApi.cleanNull(t.optString("taskuserid"));

                // ② 全显=true时显示全部；全显=false只显示今日
                if (!showAll) {
                    boolean isTodayTask = pkg.starttime.startsWith(todayDate) || pkg.endtime.startsWith(todayDate);
                    if (!isTodayTask) continue;
                }

                synchronized (taskPool) { taskPool.add(pkg); }
            }
        }
    }

    /** 处理单个任务（并发工作线程） */
    private void processOneTask(int idx) {
        XunjianApi.TaskPackage pkg;
        synchronized (taskPool) {
            if (idx >= taskPool.size()) { finishOne(); return; }
            pkg = taskPool.get(idx);
        }

        // 获取签到距离/精确专业信息
        String signJson = XunjianApi.getTaskSignInfo(pkg.taskid);
        String rangeStr = "";
        String tasksn   = "";
        String deviceid = "";
        try {
            JSONObject root = new JSONObject(signJson);
            JSONArray list  = root.optJSONArray("list");
            if (list != null && list.length() > 0) {
                JSONObject item = list.getJSONObject(0);
                String bottomMajor = XunjianApi.cleanNull(item.optString("applymajor"));
                if (!bottomMajor.isEmpty()) pkg.applymajor = bottomMajor;
                rangeStr = XunjianApi.cleanNull(item.optString("range_site"));
                tasksn   = XunjianApi.cleanNull(item.optString("tasksn"));
                deviceid = XunjianApi.cleanNull(item.optString("deviceid"));
            }
        } catch (Exception ignore) {}

        // 英中翻译
        pkg.applymajor = XunjianApi.translateMajor(pkg.applymajor);

        // 获取分组
        String[] groupResult = getGroupInfo(pkg.taskuser, pkg.stationname);
        String groupName = groupResult[0];
        int groupIdx     = Integer.parseInt(groupResult[1]);

        // 统计
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        boolean isDone  = !pkg.endtime.isEmpty() && !pkg.endtime.contains("null");
        boolean isToday = isDone && pkg.endtime.startsWith(todayStr);

        int cat = XunjianApi.getMajorCategory(pkg.applymajor);
        updateStats(cat, groupIdx, isDone, isToday);

        // 计算轮检周期
        String pollingperiod = "";
        if (!pkg.endtime.isEmpty() && !pkg.starttime.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date dt1 = sdf.parse(pkg.endtime);
                Date dt2 = sdf.parse(pkg.starttime);
                if (dt1 != null && dt2 != null) {
                    long diffMin = Math.abs(dt1.getTime() - dt2.getTime()) / 60000;
                    pollingperiod = String.valueOf(diffMin);
                }
            } catch (Exception ignore) {}
        }

        // 写入任务列表
        final String fGroupName  = groupName;
        final String fTasksn     = tasksn;
        final String fDeviceid   = deviceid;
        final String fStationname= pkg.stationname;
        final String fRemark     = pkg.remark;
        final String fApply      = pkg.applymajor;
        final String fPlanName   = pkg.mainplanname;
        final String fProgress   = pkg.finishnum + "/" + pkg.allnum;
        final String fStart      = pkg.starttime;
        final String fEnd        = pkg.endtime;
        final String fPoll       = pollingperiod;
        final String fTaskuser   = pkg.taskuser;
        final int    fSeq        = idx + 1;

        // 直接在子线程写入缓存+触发节流渲染（不通过mainHandler避免积压）
        addQualityTaskRow(fSeq, fGroupName, fTasksn, fDeviceid,
                pkg.stationcode, fStationname, fRemark, fApply, fPlanName, fProgress,
                fStart, fEnd, fPoll, pkg.inspecttime, fTaskuser);

        // 质检逻辑（按月/今日/默认全部）
        boolean needQuality = false;
        if (!pkg.starttime.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date startDate = sdf.parse(pkg.starttime);
                if (startDate != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(startDate);
                    int taskYear  = cal.get(Calendar.YEAR);
                    int taskMonth = cal.get(Calendar.MONTH) + 1;
                    int taskDay   = cal.get(Calendar.DAY_OF_MONTH);
                    int curDay    = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

                    // 两者都未勾选：不执行质检
                    if (qualityByMonth && taskMonth == qualityMonth && taskYear == qualityYear) {
                        needQuality = true;
                    }
                    if (qualityTodayOnly && taskDay == curDay
                            && taskMonth == qualityMonth && taskYear == qualityYear) {
                        needQuality = true;
                    }
                }
            } catch (Exception ignore) {}
        }

        if (needQuality && !pkg.taskid.isEmpty()) {
            runQualityCheck(pkg.taskid, fGroupName, fPlanName, fStationname,
                    fApply, fTaskuser, fStart, pkg.stationcode, rangeStr, fSeq);
        }

        finishOne();
    }

    /** 质检子流程：获取巡检项目列表 → 逐条获取详情 → 写入质检详情列表 */
    private void runQualityCheck(String taskid, String groupName, String planName,
                                  String stationname, String applymajor, String taskuser,
                                  String starttime, String stationcode,
                                  String rangeStr, int seq) {
        String modListJson = XunjianApi.getModularList(taskid);
        try {
            JSONObject modRoot = new JSONObject(modListJson);
            JSONArray modList  = modRoot.optJSONArray("list");
            if (modList == null) return;

            for (int k = 0; k < modList.length(); k++) {
                JSONObject mod = modList.getJSONObject(k);
                String modularid  = mod.optString("modularid");
                String imagecount = XunjianApi.cleanNull(mod.optString("imagecount"));
                String devname    = XunjianApi.cleanNull(mod.optString("devname"));

                String detailJson = XunjianApi.getModularDetail(taskid, modularid);
                JSONObject detailRoot = new JSONObject(detailJson);
                JSONArray detailList  = detailRoot.optJSONArray("list");
                if (detailList == null) continue;

                for (int p = 0; p < detailList.length(); p++) {
                    JSONObject d = detailList.getJSONObject(p);
                    XunjianApi.AppQualityDetail detail = new XunjianApi.AppQualityDetail();
                    detail.seq          = String.valueOf(p + 1);
                    detail.groupName    = groupName;
                    detail.mainplanname = planName;
                    detail.sitecode     = XunjianApi.cleanNull(d.optString("sitecode"));
                    detail.sitename     = XunjianApi.cleanNull(d.optString("sitename"));
                    detail.applymajor   = applymajor;
                    detail.devname      = devname;
                    detail.projectname  = XunjianApi.cleanNull(d.optString("projectname"));
                    detail.request      = XunjianApi.cleanNull(d.optString("request"))
                            .replace("隐患拍照", "").replace("有隐患拍照", "");
                    detail.actualfill   = XunjianApi.cleanNull(d.optString("actualfill"));
                    detail.ishidden     = XunjianApi.cleanNull(d.optString("ishidden"));
                    detail.remark       = XunjianApi.cleanNull(d.optString("remark"));
                    detail.endtime      = XunjianApi.cleanNull(d.optString("endtime"));
                    detail.imagecount   = imagecount;
                    detail.range_site   = rangeStr;
                    detail.starttime    = starttime;
                    detail.taskuser     = taskuser;

                    // 质检判断
                    checkQualityRules(detail);

                    // 直接加入缓存（线程安全），由节流调度统一刷新UI
                    synchronized (qualityDetailRows) { qualityDetailRows.add(detail); }
                    scheduleRenderDetail();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 质检规则判断（对应易语言的质检逻辑） */
    private void checkQualityRules(XunjianApi.AppQualityDetail d) {
        // 必拍项目无照片
        if (d.request.contains("拍照") && parseInt0(d.imagecount) == 0) {
            d.qualityPass   = "否";
            d.qualityReason = "必拍项目无照片";
            return;
        }
        // 巡检结果与备注逻辑不符
        if ("N".equals(d.actualfill) && "正常".equals(d.remark)) {
            d.qualityPass   = "否";
            d.qualityReason = "巡检结果与备注逻辑不符";
            return;
        }
        // 无烟感隐患未上报
        if ("N".equals(d.ishidden) && d.remark.contains("无") && "烟感检查".equals(d.projectname)) {
            d.qualityPass   = "否";
            d.qualityReason = "无烟感隐患未上报";
            return;
        }
        // 经纬度偏差大于1000米
        if (!d.range_site.isEmpty() && parseDouble0(d.range_site) > 1000) {
            d.qualityPass   = "否";
            d.qualityReason = "经纬度偏差大于1000米";
            return;
        }
        // 负载总电流为0
        if ("负载总电流".equals(d.projectname) && parseDouble0(d.actualfill) == 0) {
            d.qualityPass   = "否";
            d.qualityReason = "负载总电流为0不规范";
            return;
        }
        // 低压线路铺设方式填写错误
        if ("低压线路铺设方式".equals(d.projectname)) {
            if (!d.actualfill.contains("架空") && !d.actualfill.contains("地埋")
                    && !d.actualfill.contains("管道") && !d.actualfill.contains("明敷")) {
                d.qualityPass   = "否";
                d.qualityReason = "巡检结果填写错误";
                return;
            }
        }
        // 交流引入电缆材质
        if ("交流引入电缆材质".equals(d.projectname)) {
            if (!d.actualfill.contains("铜") && !d.actualfill.contains("铝")) {
                d.qualityPass   = "否";
                d.qualityReason = "巡检结果填写错误，填写铜或铝";
                return;
            }
        }
        // 智联设备检查
        if ("是否存在智联设备-附挂物检查".equals(d.projectname)) {
            if ("不符合规范".equals(d.actualfill)) {
                if (!"现场巡检未发现智联设备".equals(d.remark)) {
                    d.qualityPass   = "否";
                    d.qualityReason = "智联设备巡检备注填写有误";
                    return;
                }
            } else if ("符合规范".equals(d.actualfill)) {
                if (!d.remark.contains("现场巡检发现智联设备")) {
                    d.qualityPass   = "否";
                    d.qualityReason = "智联设备巡检备注填写有误";
                    return;
                }
            } else {
                if (!d.actualfill.contains("符合规范") && !d.actualfill.contains("纠纷")
                        && !d.actualfill.contains("拆")) {
                    d.qualityPass   = "否";
                    d.qualityReason = "智联设备巡检结果填写有误";
                    return;
                }
            }
        }
    }

    // ===================== 统计 =====================

    private void resetStats() {
        for (int i = 0; i < 7; i++) {
            statRoomTotal[i] = statRoomDone[i] = 0;
            statCabTotal[i]  = statCabDone[i]  = 0;
            statRruTotal[i]  = statRruDone[i]  = 0;
            statTowerTotal[i]= statTowerDone[i] = 0;
            statTodayRoom[i] = statTodayTower[i]= 0;
        }
    }

    private void updateStats(int cat, int groupIdx, boolean isDone, boolean isToday) {
        synchronized (qualityLock) {
            // cat: 1=拉远/室分  2=机柜  3=铁塔  4=机房(默认)
            int g6 = 6; // 汇总槽（索引6）
            switch (cat) {
                case 1: // 拉远
                    statRruTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statRruTotal[groupIdx]++;
                    if (isDone) {
                        statRruDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statRruDone[groupIdx]++;
                        if (isToday) {
                            statTodayRoom[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayRoom[groupIdx]++;
                        }
                    }
                    break;
                case 2: // 机柜
                    statCabTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statCabTotal[groupIdx]++;
                    if (isDone) {
                        statCabDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statCabDone[groupIdx]++;
                        if (isToday) {
                            statTodayRoom[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayRoom[groupIdx]++;
                        }
                    }
                    break;
                case 3: // 铁塔
                    statTowerTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statTowerTotal[groupIdx]++;
                    if (isDone) {
                        statTowerDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statTowerDone[groupIdx]++;
                        if (isToday) {
                            statTodayTower[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayTower[groupIdx]++;
                        }
                    }
                    break;
                default: // 机房
                    statRoomTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statRoomTotal[groupIdx]++;
                    if (isDone) {
                        statRoomDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statRoomDone[groupIdx]++;
                        if (isToday) {
                            statTodayRoom[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayRoom[groupIdx]++;
                        }
                    }
                    break;
            }
        }
    }

    private void renderQualityReport() {
        llQualityReport.removeAllViews();
        // 区域由taskuser自动匹配，报表展示平阳综合组（与统计数组一致）
        String[] names   = {"综合1组","综合2组","综合3组","综合4组","综合5组","合计"};
        String[] members = {"陶大取、卢智伟","陈龙、林元龙","高树调、倪传井","苏忠前、许方喜","黄经兴、蔡亮",""};
        int groupCount = 6;

        for (int i = 1; i <= groupCount; i++) {
            int gi = (i == groupCount) ? 6 : i; // 最后一行用汇总槽
            String name   = names[i - 1];
            String member = members[i - 1];

            String roomText  = statRoomDone[gi]  + "/" + statRoomTotal[gi];
            String cabText   = statCabDone[gi]   + "/" + statCabTotal[gi];
            String rruText   = statRruDone[gi]   + "/" + statRruTotal[gi];
            String towerText = statTowerDone[gi] + "/" + statTowerTotal[gi];

            int roomDone  = statRoomDone[gi]  + statCabDone[gi]  + statRruDone[gi];
            int roomTotal2= statRoomTotal[gi] + statCabTotal[gi] + statRruTotal[gi];
            int towerDone2= statTowerDone[gi];
            int towerTotal2=statTowerTotal[gi];

            int todayMachine = statTodayRoom[gi];
            int todayTower   = statTodayTower[gi];
            int grandTotal   = roomDone + towerDone2;

            String roomPct = roomTotal2 > 0
                    ? String.format(Locale.getDefault(), "%.1f%%", (float) roomDone / roomTotal2 * 100) : "0%";
            String towerPct = towerTotal2 > 0
                    ? String.format(Locale.getDefault(), "%.1f%%", (float) towerDone2 / towerTotal2 * 100) : "0%";

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(2, 3, 2, 3);
            row.setBackgroundColor(i % 2 == 0 ? Color.parseColor("#EFF6FF") : Color.WHITE);
            if (i == groupCount) row.setBackgroundColor(Color.parseColor("#DBEAFE"));

            addCell(row, 50, name, 9, Gravity.CENTER).setTypeface(null, android.graphics.Typeface.BOLD);
            addCellWeight(row, 1, member, 8, Gravity.START);
            addCell(row, 44, roomText, 8, Gravity.CENTER);
            addCell(row, 44, cabText, 8, Gravity.CENTER);
            addCell(row, 44, rruText, 8, Gravity.CENTER);
            addCell(row, 44, towerText, 8, Gravity.CENTER);
            addCell(row, 36, String.valueOf(todayMachine), 9, Gravity.CENTER).setTextColor(Color.parseColor("#2563EB"));
            addCell(row, 36, String.valueOf(todayTower), 9, Gravity.CENTER).setTextColor(Color.parseColor("#D97706"));
            addCell(row, 36, String.valueOf(grandTotal), 9, Gravity.CENTER).setTypeface(null, android.graphics.Typeface.BOLD);
            addCell(row, 44, roomPct, 8, Gravity.CENTER);
            addCell(row, 44, towerPct, 8, Gravity.CENTER);

            llQualityReport.addView(row);
        }
    }

    // ===================== 列表行渲染 =====================

    private void addQualityTaskRow(int seq, String groupName, String tasksn, String deviceid,
                                    String stationcode, String stationname, String remark,
                                    String applymajor, String mainplanname, String progress,
                                    String starttime, String endtime, String pollingperiod,
                                    String inspecttime, String taskuser) {
        QualityTaskRow r = new QualityTaskRow();
        r.seq = seq; r.group = groupName; r.tasksn = tasksn; r.deviceid = deviceid;
        r.stationcode = stationcode; r.stationname = stationname; r.remark = remark;
        r.applymajor = applymajor; r.mainplanname = mainplanname; r.progress = progress;
        r.starttime = starttime; r.endtime = endtime; r.pollingperiod = pollingperiod;
        r.inspecttime = inspecttime; r.taskuser = taskuser;
        synchronized (qualityTaskRows) { qualityTaskRows.add(r); }
        // 瀑布流：直接追加一行 View，不重建整个列表
        final int rowIdx = taskRowCounter.incrementAndGet();
        final QualityTaskRow finalR = r;
        mainHandler.post(() -> {
            if (llQualityTaskList != null) renderQualityTaskRow(rowIdx, finalR);
        });
    }

    private void addQualityDetailRow(XunjianApi.AppQualityDetail d) {
        synchronized (qualityDetailRows) { qualityDetailRows.add(d); }
        // 瀑布流：直接追加一行 View，不重建整个列表
        final int rowIdx = detailRowCounter.incrementAndGet();
        mainHandler.post(() -> {
            if (llQualityDetailList != null) renderQualityDetailRow(rowIdx, d);
        });
    }

    /**
     * 节流渲染：使用 AtomicBoolean CAS，保证同一时刻只有一个 postDelayed 在等待。
     * 400ms 后一次性刷新，主线程最多每 400ms 做一次全量重绘。
     */
    private void scheduleRenderTask() {
        if (taskRenderPending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                taskRenderPending.set(false);
                sortAndRenderQualityTask();
            }, RENDER_DELAY_MS);
        }
    }

    private void scheduleRenderDetail() {
        if (detailRenderPending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                detailRenderPending.set(false);
                sortAndRenderQualityDetail();
            }, RENDER_DELAY_MS);
        }
    }

    // ===================== 排序渲染 =====================

    /** 排序比较：空串排末尾 */
    private int strCmp(String a, String b, boolean asc) {
        boolean ea = (a == null || a.isEmpty()), eb = (b == null || b.isEmpty());
        if (ea && eb) return 0;
        if (ea) return 1;
        if (eb) return -1;
        int c = a.compareTo(b);
        return asc ? c : -c;
    }

    // --- 未巡检 ---
    private void sortAndRenderUnstart() {
        if (unstartSortCol >= 0) {
            final int col = unstartSortCol; final boolean asc = unstartSortAsc;
            Collections.sort(unstartRows, (a, b) -> {
                switch (col) {
                    case 0: return asc ? Integer.compare(a.seq, b.seq) : Integer.compare(b.seq, a.seq);
                    case 1: return strCmp(a.group, b.group, asc);
                    case 2: return strCmp(a.tasksn, b.tasksn, asc);
                    case 3: return strCmp(a.stationname, b.stationname, asc);
                    case 4: return strCmp(a.applymajor, b.applymajor, asc);
                    case 5: return strCmp(a.date, b.date, asc);
                    default: return 0;
                }
            });
        }
        updateUnstartHeaders();
        llUnstartList.removeAllViews();
        for (int i = 0; i < unstartRows.size(); i++) {
            UnstartRow r = unstartRows.get(i);
            renderUnstartRow(i + 1, r);
        }
    }

    private void renderUnstartRow(int rowIdx, UnstartRow r) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 3, 4, 3);
        row.setBackgroundColor(rowIdx % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE);
        addCell(row, 30,  String.valueOf(r.seq), 8, Gravity.CENTER);
        addCell(row, 50,  r.group, 8, Gravity.CENTER);
        addCell(row, 80,  r.tasksn, 8, Gravity.CENTER);
        addCellWeight(row, 1, r.stationname, 8, Gravity.START);
        addCell(row, 50,  r.applymajor, 8, Gravity.CENTER);
        String d = r.date;
        addCell(row, 60,  d != null && d.length() > 10 ? d.substring(0, 10) : d, 8, Gravity.CENTER);
        llUnstartList.addView(row);
    }

    private void updateUnstartHeaders() {
        String[] labels = {"序","分组","工单号","站点名","专业","日期"};
        TextView[] ths = {thUnstartSeq, thUnstartGroup, thUnstartTasksn, thUnstartStation, thUnstartMajor, thUnstartDate};
        for (int i = 0; i < ths.length; i++) {
            String suffix = (unstartSortCol == i) ? (unstartSortAsc ? "▲" : "▼") : "";
            ths[i].setText(labels[i] + suffix);
            ths[i].setTextColor(unstartSortCol == i ? Color.parseColor("#2563EB") : Color.parseColor("#374151"));
        }
    }

    // --- 智联巡检 ---
    private void sortAndRenderZhilian() {
        if (zhilianSortCol >= 0) {
            final int col = zhilianSortCol; final boolean asc = zhilianSortAsc;
            Collections.sort(zhilianRows, (a, b) -> {
                switch (col) {
                    case 0: return asc ? Integer.compare(a.seq, b.seq) : Integer.compare(b.seq, a.seq);
                    case 1: return strCmp(a.group, b.group, asc);
                    case 2: return strCmp(a.tasksn, b.tasksn, asc);
                    case 3: return strCmp(a.stationname, b.stationname, asc);
                    case 4: return strCmp(a.applymajor, b.applymajor, asc);
                    case 5: return strCmp(a.createtime, b.createtime, asc);
                    default: return 0;
                }
            });
        }
        updateZhilianHeaders();
        llZhilianXunjianList.removeAllViews();
        for (int i = 0; i < zhilianRows.size(); i++) {
            ZhilianRow r = zhilianRows.get(i);
            renderZhilianRow(i + 1, r);
        }
    }

    private void renderZhilianRow(int rowIdx, ZhilianRow r) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 3, 4, 3);
        row.setBackgroundColor(rowIdx % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE);
        addCell(row, 30,  String.valueOf(r.seq), 8, Gravity.CENTER);
        addCell(row, 50,  r.group, 8, Gravity.CENTER);
        addCell(row, 80,  r.tasksn, 8, Gravity.CENTER);
        addCellWeight(row, 1, r.stationname, 8, Gravity.START);
        addCell(row, 50,  r.applymajor, 8, Gravity.CENTER);
        addCell(row, 100, r.createtime, 8, Gravity.CENTER);
        llZhilianXunjianList.addView(row);
    }

    private void updateZhilianHeaders() {
        String[] labels = {"序","分组","工单号","站点名","专业","创建时间"};
        TextView[] ths = {thZhilianSeq, thZhilianGroup, thZhilianTasksn, thZhilianStation, thZhilianMajor, thZhilianCreatetime};
        for (int i = 0; i < ths.length; i++) {
            String suffix = (zhilianSortCol == i) ? (zhilianSortAsc ? "▲" : "▼") : "";
            ths[i].setText(labels[i] + suffix);
            ths[i].setTextColor(zhilianSortCol == i ? Color.parseColor("#2563EB") : Color.parseColor("#374151"));
        }
    }

    // --- APP质检任务 ---
    private void sortAndRenderQualityTask() {
        List<QualityTaskRow> snapshot;
        synchronized (qualityTaskRows) {
            snapshot = new ArrayList<>(qualityTaskRows);
        }
        if (taskSortCol >= 0) {
            final int col = taskSortCol; final boolean asc = taskSortAsc;
            Collections.sort(snapshot, (a, b) -> {
                switch (col) {
                    case 0: return asc ? Integer.compare(a.seq, b.seq) : Integer.compare(b.seq, a.seq);
                    case 1: return strCmp(a.group, b.group, asc);
                    case 2: return strCmp(a.stationname, b.stationname, asc);
                    case 3: return strCmp(a.applymajor, b.applymajor, asc);
                    case 4: return strCmp(a.progress, b.progress, asc);
                    case 5: return strCmp(a.starttime, b.starttime, asc);
                    case 6: return strCmp(a.endtime, b.endtime, asc);
                    default: return 0;
                }
            });
        }
        updateTaskHeaders();
        llQualityTaskList.removeAllViews();
        for (int i = 0; i < snapshot.size(); i++) {
            renderQualityTaskRow(i + 1, snapshot.get(i));
        }
    }

    private void renderQualityTaskRow(int rowIdx, QualityTaskRow r) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 2, 4, 2);
        row.setBackgroundColor(rowIdx % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE);
        addCell(row, 30,  String.valueOf(r.seq), 8, Gravity.CENTER);
        addCell(row, 44,  r.group, 8, Gravity.CENTER);
        addCellWeight(row, 1, r.stationname, 8, Gravity.START);
        addCell(row, 40,  r.applymajor, 8, Gravity.CENTER);
        addCell(row, 56,  r.progress, 8, Gravity.CENTER);
        String st = r.starttime; addCell(row, 90, st != null && st.length() > 16 ? st.substring(0, 16) : st, 8, Gravity.CENTER);
        String et = r.endtime;   addCell(row, 90, et != null && et.length() > 16 ? et.substring(0, 16) : et, 8, Gravity.CENTER);
        llQualityTaskList.addView(row);
    }

    private void updateTaskHeaders() {
        String[] labels = {"序","分组","站点","专业","进度","开始时间","结束时间"};
        TextView[] ths = {thTaskSeq, thTaskGroup, thTaskStation, thTaskMajor, thTaskProgress, thTaskStart, thTaskEnd};
        for (int i = 0; i < ths.length; i++) {
            String suffix = (taskSortCol == i) ? (taskSortAsc ? "▲" : "▼") : "";
            ths[i].setText(labels[i] + suffix);
            ths[i].setTextColor(taskSortCol == i ? Color.parseColor("#2563EB") : Color.parseColor("#374151"));
        }
    }

    // --- 质检详情 ---
    private void sortAndRenderQualityDetail() {
        List<XunjianApi.AppQualityDetail> snapshot;
        synchronized (qualityDetailRows) {
            snapshot = new ArrayList<>(qualityDetailRows);
        }
        if (detailSortCol >= 0) {
            final int col = detailSortCol; final boolean asc = detailSortAsc;
            Collections.sort(snapshot, (a, b) -> {
                switch (col) {
                    case 0: { int ai=0, bi=0; try{ai=Integer.parseInt(a.seq);}catch(Exception e){} try{bi=Integer.parseInt(b.seq);}catch(Exception e){} return asc?Integer.compare(ai,bi):Integer.compare(bi,ai); }
                    case 1: return strCmp(a.groupName, b.groupName, asc);
                    case 2: return strCmp(a.sitename, b.sitename, asc);
                    case 3: return strCmp(a.applymajor, b.applymajor, asc);
                    case 4: return strCmp(a.projectname, b.projectname, asc);
                    case 5: return strCmp(a.actualfill, b.actualfill, asc);
                    case 6: return strCmp(a.qualityPass, b.qualityPass, asc);
                    case 7: return strCmp(a.qualityReason, b.qualityReason, asc);
                    default: return 0;
                }
            });
        }
        updateDetailHeaders();
        llQualityDetailList.removeAllViews();
        for (int i = 0; i < snapshot.size(); i++) {
            renderQualityDetailRow(i + 1, snapshot.get(i));
        }
    }

    private void renderQualityDetailRow(int rowIdx, XunjianApi.AppQualityDetail d) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 2, 4, 2);
        boolean fail = "否".equals(d.qualityPass);
        row.setBackgroundColor(fail ? Color.parseColor("#FEF2F2") : (rowIdx % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE));
        addCell(row, 30,  d.seq, 8, Gravity.CENTER);
        addCell(row, 44,  d.groupName, 8, Gravity.CENTER);
        addCellWeight(row, 1, d.sitename, 8, Gravity.START);
        addCell(row, 44,  d.applymajor, 8, Gravity.CENTER);
        addCellWeight(row, 1, d.projectname, 8, Gravity.START);
        addCell(row, 44,  d.actualfill, 8, Gravity.CENTER);
        TextView tvPass = addCell(row, 32, d.qualityPass, 8, Gravity.CENTER);
        if (fail) tvPass.setTextColor(Color.parseColor("#EF4444"));
        addCellWeight(row, 1, d.qualityReason, 8, Gravity.START)
                .setTextColor(fail ? Color.parseColor("#DC2626") : Color.parseColor("#374151"));
        llQualityDetailList.addView(row);
    }

    private void updateDetailHeaders() {
        String[] labels = {"序","分组","站点","专业","巡检项目","结果","质检","质检问题"};
        TextView[] ths = {thDetailSeq, thDetailGroup, thDetailStation, thDetailMajor, thDetailProject, thDetailResult, thDetailPass, thDetailReason};
        for (int i = 0; i < ths.length; i++) {
            String suffix = (detailSortCol == i) ? (detailSortAsc ? "▲" : "▼") : "";
            ths[i].setText(labels[i] + suffix);
            ths[i].setTextColor(detailSortCol == i ? Color.parseColor("#2563EB") : Color.parseColor("#374151"));
        }
    }

    // ===================== 原子完成计数 =====================

    private void finishOne() {
        int fin, tot;
        synchronized (qualityLock) {
            qualityRunning--;
            qualityFinished++;
            fin = qualityFinished;
            tot = qualityTotal;
        }
        // 每完成一个任务更新进度条（瀑布流模式下列表由addView追加，进度更新是唯一的主线程开销，极轻量）
        if (tot > 0) {
            final int pct = (int)((float) fin / tot * 100);
            mainHandler.post(() -> {
                if (progressQuality != null) progressQuality.setProgress(pct);
                if (tvQualityProgress != null) tvQualityProgress.setText(pct + "%");
            });
        }
    }

    // ===================== 分组匹配 =====================

    private String matchGroup(String stationname) {
        // 站名匹配（基于 taskuser 匹配在 getGroupInfo 里）
        // 此处仅返回"其他分组"作为 fallback
        return "其他分组";
    }

    /**
     * 综合分组匹配（优先匹配平阳，再匹配泰顺）
     * @return [groupName, groupIndex(1-based，0=未知)]
     */
    private String[] getGroupInfo(String taskuser, String stationname) {
        // 先尝试平阳分组
        for (int g = 0; g < TASKUSER_AREA0.length; g++) {
            for (String name : TASKUSER_AREA0[g]) {
                if (taskuser.contains(name)) {
                    return new String[]{GROUPNAMES_AREA0[g], String.valueOf(g + 1)};
                }
            }
        }
        // 再尝试泰顺分组
        for (int g = 0; g < TASKUSER_AREA1.length; g++) {
            for (String name : TASKUSER_AREA1[g]) {
                if (taskuser.contains(name)) {
                    return new String[]{GROUPNAMES_AREA1[g], String.valueOf(g + 1)};
                }
            }
        }
        return new String[]{"其他分组", "0"};
    }

    private int groupIndexOf(String groupName, String[] names) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(groupName)) return i;
        }
        return -1;
    }

    // ===================== View 工具方法 =====================

    private TextView addCell(LinearLayout parent, int widthDp, String text, int textSp, int gravity) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        tv.setText(text != null ? text : "");
        tv.setTextSize(textSp);
        tv.setGravity(gravity);
        tv.setPadding(2, 1, 2, 1);
        tv.setTextColor(Color.parseColor("#374151"));
        tv.setSingleLine(false);
        parent.addView(tv);
        return tv;
    }

    private TextView addCellWeight(LinearLayout parent, float weight, String text, int textSp, int gravity) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        tv.setText(text != null ? text : "");
        tv.setTextSize(textSp);
        tv.setGravity(gravity);
        tv.setPadding(2, 1, 2, 1);
        tv.setTextColor(Color.parseColor("#374151"));
        tv.setSingleLine(false);
        parent.addView(tv);
        return tv;
    }

    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int)(dp * density + 0.5f);
    }

    private int parseInt0(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private double parseDouble0(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }
}

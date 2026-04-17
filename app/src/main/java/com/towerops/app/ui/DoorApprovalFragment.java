package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.DoorApprovalApi;
import com.towerops.app.util.ThreadManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 门禁审批 Fragment
 * 支持手动审批和全自动监控审批模式
 */
public class DoorApprovalFragment extends Fragment {

    private static final String TAG = "DoorApprovalFragment";

    // 自动审批相关
    private static final long AUTO_POLL_INTERVAL_SECONDS = 30;  // 轮询间隔
    private static final long APPROVE_DELAY_MIN_MS = 1500;     // 审批间隔最小值
    private static final long APPROVE_DELAY_MAX_MS = 3500;    // 审批间隔最大值

    // UI 控件
    private SwitchCompat    switchAutoApproval;
    private TextView        tvAutoStatus, tvApprovalStatus, tvApprovalCount, tvEmpty;
    private Button          btnRefresh, btnApprovalAll;
    private LinearLayout    layoutManualButtons;
    private RecyclerView    rvApproval;

    // 列表相关
    private final List<DoorApprovalApi.ApprovalItem> itemList = new ArrayList<>();
    private DoorApprovalAdapter adapter;

    // 线程池
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> autoPollTask;

    // 状态标志
    private volatile boolean loading = false;
    private volatile boolean autoRunning = false;
    private final AtomicBoolean autoApproving = new AtomicBoolean(false);
    private final AtomicInteger totalApproved = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);

    private final Random rnd = new Random();
    private final StringBuilder logBuilder = new StringBuilder();
    private final Object logLock = new Object();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_door_approval, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化控件
        switchAutoApproval  = view.findViewById(R.id.switchAutoApproval);
        tvAutoStatus        = view.findViewById(R.id.tvAutoStatus);
        tvApprovalStatus    = view.findViewById(R.id.tvApprovalStatus);
        tvApprovalCount     = view.findViewById(R.id.tvApprovalCount);
        tvEmpty             = view.findViewById(R.id.tvApprovalEmpty);
        btnRefresh          = view.findViewById(R.id.btnApprovalRefresh);
        btnApprovalAll      = view.findViewById(R.id.btnApprovalAll);
        layoutManualButtons = view.findViewById(R.id.layoutManualButtons);
        rvApproval          = view.findViewById(R.id.rvDoorApproval);

        setupRecycler();
        setupListeners();

        // 初始化时刷新一次列表
        mainHandler.postDelayed(this::doRefresh, 500);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAutoMonitor();
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
    }

    private void setupRecycler() {
        adapter = new DoorApprovalAdapter(itemList);
        adapter.setOnApproveListener((position, item) -> doApproveSingle(position, item));
        rvApproval.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvApproval.setAdapter(adapter);
    }

    private void setupListeners() {
        // 刷新按钮
        btnRefresh.setOnClickListener(v -> {
            if (autoRunning) {
                appendLog("⚠ 自动模式运行中，请先关闭自动审批");
                Toast.makeText(requireContext(), "自动模式运行中，请先关闭自动审批", Toast.LENGTH_SHORT).show();
                return;
            }
            doRefresh();
        });

        // 一键审批按钮
        btnApprovalAll.setOnClickListener(v -> {
            if (autoRunning) {
                Toast.makeText(requireContext(), "自动模式运行中，请先关闭自动审批", Toast.LENGTH_SHORT).show();
                return;
            }
            doApproveAll();
        });

        // 自动审批开关
        switchAutoApproval.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startAutoMonitor();
            } else {
                stopAutoMonitor();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 自动审批监控
    // ─────────────────────────────────────────────────────────────────────

    private void startAutoMonitor() {
        if (autoRunning) return;

        // 检查认证
        String missing = DoorApprovalApi.checkAuth();
        if (!missing.isEmpty()) {
            appendLog("❌ 缺少认证: " + missing);
            Toast.makeText(requireContext(), "缺少认证: " + missing + "，无法开启自动审批", Toast.LENGTH_LONG).show();
            switchAutoApproval.setChecked(false);
            return;
        }

        autoRunning = true;
        totalApproved.set(0);
        totalFailed.set(0);
        logBuilder.setLength(0);

        // 更新UI
        switchAutoApproval.setChecked(true);
        tvAutoStatus.setText("● 监控中 " + AUTO_POLL_INTERVAL_SECONDS + "秒轮询");
        tvAutoStatus.setTextColor(0xFF16A34A);  // 绿色
        layoutManualButtons.setVisibility(View.GONE);
        appendLog("✅ 自动审批已开启");
        appendLog("📡 轮询间隔: " + AUTO_POLL_INTERVAL_SECONDS + "秒");
        appendLog("⏱ 审批延迟: " + APPROVE_DELAY_MIN_MS + "-" + APPROVE_DELAY_MAX_MS + "ms");

        // 立即执行一次
        doAutoPoll();

        // 启动定时轮询
        scheduler = Executors.newSingleThreadScheduledExecutor();
        autoPollTask = scheduler.scheduleAtFixedRate(
            this::doAutoPoll,
            AUTO_POLL_INTERVAL_SECONDS,
            AUTO_POLL_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    private void stopAutoMonitor() {
        if (!autoRunning) return;

        autoRunning = false;

        if (autoPollTask != null) {
            autoPollTask.cancel(false);
            autoPollTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        // 更新UI
        switchAutoApproval.setChecked(false);
        tvAutoStatus.setText("已关闭");
        tvAutoStatus.setTextColor(0xFF64748B);  // 灰色
        layoutManualButtons.setVisibility(View.VISIBLE);
        appendLog("⏹ 自动审批已关闭");
        appendLog("📊 本轮统计: 成功 " + totalApproved.get() + " 条，失败 " + totalFailed.get() + " 条");
    }

    private void doAutoPoll() {
        if (!autoRunning) return;

        appendLog("───────────────");
        appendLog("🔍 [" + getTimeString() + "] 开始轮询...");

        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 1. 获取待审批列表
                List<DoorApprovalApi.ApprovalItem> pending = DoorApprovalApi.getUnfinishedList();

                if (pending == null || pending.isEmpty()) {
                    mainHandler.post(() -> {
                        tvApprovalCount.setText("待审批 0 条");
                        appendLog("📭 暂无待审批工单");
                        updateStatus();
                    });
                    return;
                }

                mainHandler.post(() -> {
                    itemList.clear();
                    itemList.addAll(pending);
                    adapter.notifyDataSetChanged();
                    updateEmpty();
                    tvApprovalCount.setText("待审批 " + itemList.size() + " 条");
                    appendLog("📋 发现 " + pending.size() + " 条待审批，开始自动审批...");
                    updateStatus();
                });

                // 2. 自动审批所有工单
                if (autoApproving.compareAndSet(false, true)) {
                    autoApproveAll(pending);
                }

            } catch (Exception e) {
                appendLog("❌ 轮询异常: " + e.getMessage());
            }
        });
    }

    private void autoApproveAll(List<DoorApprovalApi.ApprovalItem> items) {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int done = 0, fail = 0;

            for (int i = 0; i < items.size(); i++) {
                if (!autoRunning) {
                    appendLog("⚠ 自动审批被中断");
                    break;
                }

                DoorApprovalApi.ApprovalItem item = items.get(i);

                if (item.appUrl == null || item.appUrl.isEmpty()) {
                    fail++;
                    appendLog("⚠ [" + (i+1) + "/" + items.size() + "] 跳过(无URL): " + item.siteName);
                    continue;
                }

                appendLog("🔄 [" + (i+1) + "/" + items.size() + "] 审批: " + item.siteName);

                boolean ok = DoorApprovalApi.approveByAppUrl(item.appUrl);
                if (ok) {
                    done++;
                    totalApproved.incrementAndGet();
                    appendLog("✅ [" + (i+1) + "/" + items.size() + "] 成功: " + item.siteName);
                } else {
                    fail++;
                    totalFailed.incrementAndGet();
                    appendLog("❌ [" + (i+1) + "/" + items.size() + "] 失败: " + item.siteName);
                }

                // 仿生延迟
                if (i < items.size() - 1) {
                    long delay = APPROVE_DELAY_MIN_MS + rnd.nextInt((int)(APPROVE_DELAY_MAX_MS - APPROVE_DELAY_MIN_MS));
                    appendLog("⏳ 等待 " + delay + "ms...");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // 从列表移除已审批的
            final int finalDone = done;
            final int finalFail = fail;
            mainHandler.post(() -> {
                itemList.removeIf(item -> {
                    for (DoorApprovalApi.ApprovalItem approved : items) {
                        if (approved.taskId != null && approved.taskId.equals(item.taskId)) {
                            return true;
                        }
                    }
                    return false;
                });
                adapter.notifyDataSetChanged();
                updateEmpty();
                tvApprovalCount.setText("待审批 " + itemList.size() + " 条");

                appendLog("───────────────");
                appendLog("✅ 本轮完成: 成功 " + finalDone + "，失败 " + finalFail);
                appendLog("📊 累计: 成功 " + totalApproved.get() + "，失败 " + totalFailed.get());
                updateStatus();
            });

            autoApproving.set(false);
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 手动审批
    // ─────────────────────────────────────────────────────────────────────

    private void doRefresh() {
        if (loading || autoRunning) return;

        String missing = DoorApprovalApi.checkAuth();
        if (!missing.isEmpty()) {
            setStatus("❌ 缺少认证: " + missing);
            Toast.makeText(requireContext(), "缺少认证: " + missing, Toast.LENGTH_LONG).show();
            return;
        }

        loading = true;
        btnRefresh.setEnabled(false);
        setStatus("正在获取待审批工单...");

        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<DoorApprovalApi.ApprovalItem> result = DoorApprovalApi.getUnfinishedList();

            mainHandler.post(() -> {
                if (!isAdded()) return;
                itemList.clear();
                itemList.addAll(result);
                adapter.notifyDataSetChanged();
                updateEmpty();
                tvApprovalCount.setText("待审批 " + itemList.size() + " 条");
                if (itemList.isEmpty()) {
                    setStatus("✅ 暂无待审批工单");
                } else {
                    setStatus("共 " + itemList.size() + " 条待审批，点击「审批」逐条，或开启自动审批");
                }
                loading = false;
                btnRefresh.setEnabled(true);
            });
        });
    }

    private void doApproveSingle(int position, DoorApprovalApi.ApprovalItem item) {
        if (autoRunning) {
            Toast.makeText(requireContext(), "自动模式运行中", Toast.LENGTH_SHORT).show();
            return;
        }

        if (item.appUrl == null || item.appUrl.isEmpty()) {
            Toast.makeText(requireContext(), "无法获取审批URL", Toast.LENGTH_LONG).show();
            return;
        }

        setStatus("正在审批：" + item.siteName + "...");
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            boolean ok = DoorApprovalApi.approveByAppUrl(item.appUrl);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                if (ok) {
                    Toast.makeText(requireContext(), "✅ 审批成功：" + item.siteName, Toast.LENGTH_SHORT).show();
                    adapter.remove(position);
                    tvApprovalCount.setText("待审批 " + itemList.size() + " 条");
                    setStatus(itemList.isEmpty() ? "✅ 全部审批完成" : "剩余 " + itemList.size() + " 条");
                    updateEmpty();
                } else {
                    Toast.makeText(requireContext(), "❌ 审批失败：" + item.siteName, Toast.LENGTH_SHORT).show();
                    setStatus("审批失败：" + item.siteName);
                }
            });
        });
    }

    private void doApproveAll() {
        if (itemList.isEmpty()) {
            Toast.makeText(requireContext(), "暂无待审批工单", Toast.LENGTH_SHORT).show();
            return;
        }

        String missing = DoorApprovalApi.checkAuth();
        if (!missing.isEmpty()) {
            Toast.makeText(requireContext(), "缺少认证: " + missing, Toast.LENGTH_LONG).show();
            return;
        }

        btnApprovalAll.setEnabled(false);
        btnRefresh.setEnabled(false);
        int total = itemList.size();
        setStatus("开始一键审批，共 " + total + " 条...");

        final List<DoorApprovalApi.ApprovalItem> snapshot = new ArrayList<>(itemList);

        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int done = 0, fail = 0;

            for (int i = 0; i < snapshot.size(); i++) {
                if (!isAdded()) break;

                DoorApprovalApi.ApprovalItem item = snapshot.get(i);

                if (item.appUrl == null || item.appUrl.isEmpty()) {
                    fail++;
                    setStatus("⚠ 跳过: " + item.siteName);
                    continue;
                }

                setStatus("🔄 [" + (i+1) + "/" + snapshot.size() + "] " + item.siteName);

                boolean ok = DoorApprovalApi.approveByAppUrl(item.appUrl);
                if (ok) {
                    done++;
                    final int finalDone = done;
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        for (int j = itemList.size() - 1; j >= 0; j--) {
                            if (itemList.get(j).taskId != null &&
                                    itemList.get(j).taskId.equals(item.taskId)) {
                                adapter.remove(j);
                                break;
                            }
                        }
                        tvApprovalCount.setText("待审批 " + itemList.size() + " 条");
                    });
                } else {
                    fail++;
                }

                if (i < snapshot.size() - 1) {
                    try {
                        Thread.sleep(1000 + rnd.nextInt(2000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            final int finalDone = done;
            final int finalFail = fail;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                btnApprovalAll.setEnabled(true);
                btnRefresh.setEnabled(true);
                updateEmpty();
                setStatus("✅ 一键审批完成: 成功 " + finalDone + "，失败 " + finalFail);
                tvApprovalCount.setText("待审批 " + itemList.size() + " 条");
                Toast.makeText(requireContext(), "完成: 成功 " + finalDone + "，失败 " + finalFail, Toast.LENGTH_LONG).show();
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────

    private void appendLog(String msg) {
        synchronized (logLock) {
            logBuilder.insert(0, msg + "\n");
            // 保持日志不超过50行
            int idx = logBuilder.indexOf("\n", 50);
            if (idx > 0) {
                logBuilder.delete(idx, logBuilder.length());
            }
        }
        mainHandler.post(this::updateStatus);
    }

    private void updateStatus() {
        if (!isAdded() || tvApprovalStatus == null) return;
        synchronized (logLock) {
            tvApprovalStatus.setText(logBuilder.toString().trim());
        }
    }

    private void setStatus(String msg) {
        synchronized (logLock) {
            logBuilder.setLength(0);
            logBuilder.append(msg);
        }
        updateStatus();
    }

    private void updateEmpty() {
        if (!isAdded()) return;
        if (itemList.isEmpty()) {
            rvApproval.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            rvApproval.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private String getTimeString() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }
}

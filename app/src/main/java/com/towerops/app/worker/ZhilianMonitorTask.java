package com.towerops.app.worker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.towerops.app.api.ZhilianApi;
import com.towerops.app.model.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 智联工单监控任务 —— 对应易语言的智联工单监测逻辑
 *
 * 核心功能：
 * 1. 定时轮询未领取工单列表
 * 2. 定时轮询已领取工单列表
 * 3. 自动接单（带仿生延迟）
 * 4. 自动回单（根据时间差判断）
 *
 * 线程安全：
 * - 使用全局网络锁防止接单和回单并发冲突
 * - 使用分段锁处理不同工单
 * - 操作时序锁保证操作间隔
 */
public class ZhilianMonitorTask implements Runnable {

    private static final String TAG = "ZhilianMonitorTask";

    // =====================================================================
    // 配置参数（对应易语言中的选择框和阈值设置）
    // =====================================================================
    private static final int MIN_INTERVAL_MS = 60000;   // 最小轮询间隔 60秒
    private static final int MAX_INTERVAL_MS = 120000;  // 最大轮询间隔 120秒

    private static final int MIN_ACCEPT_DELAY_MS = 2500;   // 接单最小延迟 2.5秒
    private static final int MAX_ACCEPT_DELAY_MS = 6000;   // 接单最大延迟 6秒

    private static final int MIN_REVERT_DELAY_MS = 5000;   // 回单最小延迟 5秒
    private static final int MAX_REVERT_DELAY_MS = 12000;  // 回单最大延迟 12秒

    // 回单时间阈值（分钟）
    private static final int MIN_REVERT_TIME_DIFF = 300;   // 最小300分钟
    private static final int MAX_REVERT_TIME_DIFF = 720;   // 最大720分钟

    // =====================================================================
    // 线程锁
    // =====================================================================
    /**
     * 全局网络发包许可证 —— 防止接单和回单在同一秒撞车
     */
    private static final ReentrantLock NET_LOCK = new ReentrantLock(true);

    /**
     * 操作时序锁 —— 保证任意两次操作之间的最小间隔
     */
    private static final ReentrantLock OP_SEQUENCE_LOCK = new ReentrantLock(true);
    private static volatile long lastOpTimeMs = 0L;
    private static final long MIN_OP_GAP_MS = 8000L;   // 最少等8秒
    private static final long MAX_OP_GAP_MS = 15000L;  // 最多等15秒

    // =====================================================================
    // 状态控制
    // =====================================================================
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private final Random random = new Random();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 回调接口
    public interface Callback {
        void onStatusUpdate(String message);
        void onUnclaimedOrders(List<ZhilianApi.ZhilianTaskInfo> orders);
        void onClaimedOrders(List<ZhilianApi.ZhilianTaskInfo> orders);
        void onTaskAccepted(String id, boolean success);
        void onTaskReverted(String id, boolean success);
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    // =====================================================================
    // 主运行循环
    // =====================================================================
    @Override
    public void run() {
        isRunning = true;
        postStatus("智联监控启动");

        while (isRunning) {
            if (!isPaused) {
                try {
                    // 执行一轮监控
                    doMonitorRound();
                } catch (Exception e) {
                    Log.e(TAG, "监控轮次异常", e);
                    postStatus("异常: " + e.getMessage());
                }
            }

            // 随机间隔后再执行下一轮
            int interval = randomInt(MIN_INTERVAL_MS, MAX_INTERVAL_MS);
            postStatus("下次轮询: " + (interval / 1000) + "秒后");

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        isRunning = false;
        postStatus("智联监控已停止");
    }

    // =====================================================================
    // 一轮监控流程
    // =====================================================================
    private void doMonitorRound() {
        Session s = Session.get();

        // 解析智联配置
        boolean enableAccept = false;
        boolean enableRevert = false;

        if (!s.zhilianConfig.isEmpty()) {
            String[] cfg = s.zhilianConfig.split("\u0001", -1);
            if (cfg.length >= 2) {
                enableAccept = "true".equalsIgnoreCase(cfg[0]);
                enableRevert = "true".equalsIgnoreCase(cfg[1]);
            }
        }

        // =========================================================
        // 1. 查询未领取工单
        // =========================================================
        postStatus("查询未领取工单...");
        String unclaimedJson = ZhilianApi.getUnclaimedTaskList();
        List<ZhilianApi.ZhilianTaskInfo> unclaimedList = ZhilianApi.parseUnclaimedList(unclaimedJson);

        postStatus("未领取工单: " + unclaimedList.size() + "条");
        postUnclaimedOrders(unclaimedList);

        // 自动接单（如果开启且数量小于10）
        if (enableAccept && unclaimedList.size() < 10) {
            for (ZhilianApi.ZhilianTaskInfo task : unclaimedList) {
                if (!isRunning) break;

                // 获取操作时序锁
                if (!acquireOpLock()) continue;

                NET_LOCK.lock();
                try {
                    postStatus("准备接单: " + task.siteName);

                    // 仿生延迟
                    sleepRandom(MIN_ACCEPT_DELAY_MS, MAX_ACCEPT_DELAY_MS);

                    // 执行接单
                    String result = ZhilianApi.acceptTask(task.id, task.versionId);
                    boolean success = ZhilianApi.isSuccess(result);

                    postStatus("接单" + (success ? "成功" : "失败") + ": " + task.siteName);
                    postTaskAccepted(task.id, success);

                } finally {
                    NET_LOCK.unlock();
                    releaseOpLock();
                }
            }
        }

        // =========================================================
        // 2. 查询已领取工单（延迟1.5-3.5秒，错开网络请求）
        // =========================================================
        sleepRandom(1500, 3500);

        postStatus("查询已领取工单...");
        String claimedJson = ZhilianApi.getClaimedTaskList();
        List<ZhilianApi.ZhilianTaskInfo> claimedList = ZhilianApi.parseClaimedList(claimedJson);

        postStatus("已领取工单: " + claimedList.size() + "条");
        postClaimedOrders(claimedList);

        // 自动回单（如果开启）
        if (enableRevert) {
            List<ZhilianApi.ZhilianTaskInfo> toRevert = new ArrayList<>();

            // 筛选需要回单的工单（前3条，时间差超过阈值）
            for (int i = 0; i < Math.min(3, claimedList.size()); i++) {
                ZhilianApi.ZhilianTaskInfo task = claimedList.get(i);

                // 计算时间差（分钟）
                int timeDiff = minutesDiff(task.createTime);
                int threshold = randomInt(MIN_REVERT_TIME_DIFF, MAX_REVERT_TIME_DIFF);

                if (timeDiff > threshold) {
                    toRevert.add(task);
                }
            }

            // 执行回单
            for (ZhilianApi.ZhilianTaskInfo task : toRevert) {
                if (!isRunning) break;

                // 获取操作时序锁
                if (!acquireOpLock()) continue;

                NET_LOCK.lock();
                try {
                    postStatus("准备回单: " + task.siteName);

                    // 仿生延迟
                    sleepRandom(MIN_REVERT_DELAY_MS, MAX_REVERT_DELAY_MS);

                    // 执行回单
                    String result = ZhilianApi.revertTask(task.id, task.siteId);
                    boolean success = ZhilianApi.isSuccess(result);

                    postStatus("回单" + (success ? "成功" : "失败") + ": " + task.siteName);
                    postTaskReverted(task.id, success);

                } finally {
                    NET_LOCK.unlock();
                    releaseOpLock();
                }
            }
        }
    }

    // =====================================================================
    // 控制方法
    // =====================================================================
    public void stop() {
        isRunning = false;
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    // =====================================================================
    // 回调辅助方法
    // =====================================================================
    private void postStatus(String message) {
        Log.d(TAG, message);
        if (callback != null) {
            mainHandler.post(() -> callback.onStatusUpdate(message));
        }
    }

    private void postUnclaimedOrders(List<ZhilianApi.ZhilianTaskInfo> orders) {
        if (callback != null) {
            mainHandler.post(() -> callback.onUnclaimedOrders(orders));
        }
    }

    private void postClaimedOrders(List<ZhilianApi.ZhilianTaskInfo> orders) {
        if (callback != null) {
            mainHandler.post(() -> callback.onClaimedOrders(orders));
        }
    }

    private void postTaskAccepted(String id, boolean success) {
        if (callback != null) {
            mainHandler.post(() -> callback.onTaskAccepted(id, success));
        }
    }

    private void postTaskReverted(String id, boolean success) {
        if (callback != null) {
            mainHandler.post(() -> callback.onTaskReverted(id, success));
        }
    }

    // =====================================================================
    // 工具方法
    // =====================================================================
    private int randomInt(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }

    private void sleepRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(randomInt(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 计算时间字符串到现在的分钟差
     */
    private int minutesDiff(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return 0;
        try {
            String s = timeStr.trim()
                    .replace("/", "-")
                    .replace("T", " ");
            // 去掉毫秒部分
            int dot = s.indexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            // 补秒
            if (s.length() == 16) s += ":00";
            if (s.length() != 19) return 0;

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            sdf.setLenient(false);
            java.util.Date past = sdf.parse(s);
            if (past == null) return 0;
            long diff = System.currentTimeMillis() - past.getTime();
            return diff < 0 ? 0 : (int) (diff / 60000L);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取操作时序锁
     */
    private boolean acquireOpLock() {
        OP_SEQUENCE_LOCK.lock();
        if (Thread.currentThread().isInterrupted()) {
            OP_SEQUENCE_LOCK.unlock();
            return false;
        }
        long now = System.currentTimeMillis();
        long randGap = MIN_OP_GAP_MS + (long) (Math.random() * (MAX_OP_GAP_MS - MIN_OP_GAP_MS));
        long wait = randGap - (now - lastOpTimeMs);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                OP_SEQUENCE_LOCK.unlock();
                return false;
            }
        }
        return true;
    }

    /**
     * 释放操作时序锁
     */
    private void releaseOpLock() {
        lastOpTimeMs = System.currentTimeMillis();
        OP_SEQUENCE_LOCK.unlock();
    }
}

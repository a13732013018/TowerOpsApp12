package com.towerops.app.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Handler;
import android.os.Looper;

/**
 * 线程管理工具类
 * 统一管理后台线程，避免代码中频繁 new Thread()
 * 
 * 特性：
 * 1. 线程池复用：固定5个核心线程，避免频繁创建销毁
 * 2. 自动命名：线程池线程命名规则：TowerOps-Pool-1/2/3...
 * 3. 主线程切换：提供 runOnUiThread() 方法
 * 4. 任务取消：支持取消未执行的任务
 * 5. 延迟执行：提供延迟任务支持
 * 
 * 使用示例：
 * // 后台执行耗时任务
 * ThreadManager.execute(() -> {
 *     String result = heavyWork();
 *     // 切换回主线程更新UI
 *     ThreadManager.runOnUiThread(() -> {
 *         textView.setText(result);
 *     });
 * });
 * 
 * // 延迟执行
 * ThreadManager.postDelayed(() -> {
 *     // 2秒后执行
 * }, 2000);
 */
public class ThreadManager {

    private static final int CORE_POOL_SIZE = 5;  // 核心线程数
    private static final String THREAD_NAME_PREFIX = "TowerOps-Pool-";

    private static ExecutorService executorService;
    private static Handler mainHandler;

    // 单例初始化
    static {
        // 自定义线程工厂，设置线程名称
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, THREAD_NAME_PREFIX + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };

        executorService = Executors.newFixedThreadPool(CORE_POOL_SIZE, factory);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 在后台线程池执行任务
     * 替换项目中所有 new Thread(() -> { ... }).start();
     * 
     * @param task 要执行的任务
     */
    public static void execute(Runnable task) {
        if (task == null) {
            Logger.e("ThreadManager", "任务不能为null");
            return;
        }
        executorService.execute(task);
    }

    /**
     * 提交任务并返回Future（可用于取消）
     * 
     * @param task 要执行的任务
     * @return Future对象，可调用 cancel() 取消任务
     */
    public static Future<?> submit(Runnable task) {
        if (task == null) {
            Logger.e("ThreadManager", "任务不能为null");
            return null;
        }
        return executorService.submit(task);
    }

    /**
     * 在主线程执行任务（更新UI）
     * 替换项目中所有 new Handler(Looper.getMainLooper()).post(...)
     * 
     * @param task 要执行的任务
     */
    public static void runOnUiThread(Runnable task) {
        if (task == null) {
            Logger.e("ThreadManager", "任务不能为null");
            return;
        }
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 已在主线程，直接执行
            task.run();
        } else {
            // 切换到主线程
            mainHandler.post(task);
        }
    }

    /**
     * 延迟在主线程执行任务
     * 替换项目中所有 new Handler(Looper.getMainLooper()).postDelayed(..., delay)
     * 
     * @param task 要执行的任务
     * @param delayMillis 延迟时间（毫秒）
     */
    public static void postDelayed(Runnable task, long delayMillis) {
        if (task == null) {
            Logger.e("ThreadManager", "任务不能为null");
            return;
        }
        mainHandler.postDelayed(task, delayMillis);
    }

    /**
     * 取消延迟任务
     * 
     * @param task 要取消的任务
     */
    public static void removeCallbacks(Runnable task) {
        if (task != null) {
            mainHandler.removeCallbacks(task);
        }
    }

    /**
     * 在后台线程延迟执行任务
     * 
     * @param task 要执行的任务
     * @param delayMillis 延迟时间（毫秒）
     * @return Future对象，可调用 cancel() 取消任务
     */
    public static Future<?> executeDelayed(Runnable task, long delayMillis) {
        if (task == null) {
            Logger.e("ThreadManager", "任务不能为null");
            return null;
        }
        
        return executorService.submit(() -> {
            try {
                Thread.sleep(delayMillis);
                task.run();
            } catch (InterruptedException e) {
                Logger.w("ThreadManager", "延迟任务被中断", e);
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 获取线程池状态（用于调试）
     * 
     * @return 状态字符串
     */
    public static String getPoolStatus() {
        if (executorService instanceof java.util.concurrent.ThreadPoolExecutor) {
            java.util.concurrent.ThreadPoolExecutor pool = 
                (java.util.concurrent.ThreadPoolExecutor) executorService;
            return String.format(
                "线程池: 活动线程=%d, 队列大小=%d, 已完成=%d",
                pool.getActiveCount(),
                pool.getQueue().size(),
                pool.getCompletedTaskCount()
            );
        }
        return "线程池状态未知";
    }

    /**
     * 关闭线程池（应用退出时调用）
     * 注意：关闭后无法再提交新任务
     */
    public static void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Logger.i("ThreadManager", "线程池已关闭");
        }
    }

    /**
     * 立即关闭线程池（尝试中断正在执行的任务）
     */
    public static void shutdownNow() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            Logger.i("ThreadManager", "线程池已强制关闭");
        }
    }
}

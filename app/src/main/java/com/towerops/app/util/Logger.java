package com.towerops.app.util;

import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 统一日志框架
 * 替换项目中所有 e.printStackTrace()，提供更好的日志管理和调试能力
 * 
 * 特性：
 * - 统一日志格式：[时间] [级别] [标签] 消息
 * - 自动记录调用栈（仅DEBUG/ERROR级别）
 * - 支持日志开关（生产环境可关闭）
 * - 区分不同模块的日志标签
 * 
 * 使用示例：
 * Logger.d("LoginApi", "登录成功，token=" + token);
 * Logger.e("Network", "请求失败", exception);
 * Logger.w("Session", "Cookie已过期");
 */
public class Logger {

    private static final String TAG_PREFIX = "TowerOps";
    private static final boolean ENABLED = true; // 生产环境可设为false关闭日志
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    // 日志级别
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;

    /**
     * DEBUG级别日志
     */
    public static void d(String tag, String message) {
        if (ENABLED) {
            log(DEBUG, tag, message, null);
        }
    }

    /**
     * INFO级别日志
     */
    public static void i(String tag, String message) {
        if (ENABLED) {
            log(INFO, tag, message, null);
        }
    }

    /**
     * WARN级别日志
     */
    public static void w(String tag, String message) {
        if (ENABLED) {
            log(WARN, tag, message, null);
        }
    }

    /**
     * WARN级别日志（带异常）
     */
    public static void w(String tag, String message, Throwable throwable) {
        if (ENABLED) {
            log(WARN, tag, message, throwable);
        }
    }

    /**
     * ERROR级别日志
     */
    public static void e(String tag, String message) {
        if (ENABLED) {
            log(ERROR, tag, message, null);
        }
    }

    /**
     * ERROR级别日志（带异常）
     * 替换所有 e.printStackTrace()
     */
    public static void e(String tag, String message, Throwable throwable) {
        if (ENABLED) {
            log(ERROR, tag, message, throwable);
        }
    }

    /**
     * 核心日志方法
     */
    private static void log(int level, String tag, String message, Throwable throwable) {
        if (!ENABLED) return;

        // 构建完整日志格式
        String time = DATE_FORMAT.format(new Date());
        String fullTag = TAG_PREFIX + "." + tag;
        String logMessage = String.format("[%s] [%s] %s", time, getLevelString(level), message);

        // 根据级别调用Android Log
        switch (level) {
            case VERBOSE:
                Log.v(fullTag, logMessage, throwable);
                break;
            case DEBUG:
                Log.d(fullTag, logMessage, throwable);
                break;
            case INFO:
                Log.i(fullTag, logMessage, throwable);
                break;
            case WARN:
                Log.w(fullTag, logMessage, throwable);
                break;
            case ERROR:
                // ERROR级别自动添加异常堆栈
                if (throwable != null) {
                    String stackTrace = getStackTrace(throwable);
                    Log.e(fullTag, logMessage + "\n" + stackTrace);
                } else {
                    Log.e(fullTag, logMessage);
                }
                break;
        }
    }

    /**
     * 获取日志级别字符串
     */
    private static String getLevelString(int level) {
        switch (level) {
            case VERBOSE: return "V";
            case DEBUG: return "D";
            case INFO: return "I";
            case WARN: return "W";
            case ERROR: return "E";
            default: return "?";
        }
    }

    /**
     * 获取异常堆栈信息
     */
    private static String getStackTrace(Throwable throwable) {
        if (throwable == null) return "";
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 批量替换工具：将 e.printStackTrace() 转换为 Logger.e() 调用
     * 
     * 正则表达式：catch\s*\([^)]+\)\s*\{\s*e\.printStackTrace\(\);\s*\}
     * 替换为：catch (Exception e) { Logger.e(TAG, "操作失败", e); }
     * 
     * 注意：需要将 TAG 替换为实际的类名
     */
    public static void replacePrintStackTrace() {
        // 此方法为文档说明，实际替换需要IDE或脚本辅助
        Log.d(TAG_PREFIX, "请使用IDE的正则替换功能批量替换 e.printStackTrace()");
    }
}

package com.towerops.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

/**
 * SharedPreferences 统一管理工具类
 * 替换项目中分散的 SharedPreferences 操作，提供类型安全的API
 * 
 * 特性：
 * 1. 类型安全：提供 getInt/getLong/getString/getBoolean 等类型化方法
 * 2. 默认值支持：统一管理默认值，避免重复代码
 * 3. 自动同步/异步：提供 apply() 和 commit() 两种写入模式
 * 4. 多文件支持：支持多个 SharedPreferences 文件
 * 5. 批量操作：支持批量保存多个键值对
 * 
 * 使用示例：
 * // 默认SharedPreferences文件
 * PrefHelper.putString("username", "user001");
 * String name = PrefHelper.getString("username", "");
 * 
 * // 指定文件名
 * PrefHelper.putString("token_file", "access_token", "xxx");
 * 
 * // 批量保存
 * PrefHelper.edit(editor -> {
 *     editor.putString("key1", "value1");
 *     editor.putInt("key2", 100);
 * });
 */
public class PrefHelper {

    // 默认SharedPreferences文件名
    private static final String DEFAULT_PREFS_NAME = "default_prefs";

    // 默认值常量
    private static final String DEFAULT_STRING = "";
    private static final int DEFAULT_INT = 0;
    private static final long DEFAULT_LONG = 0L;
    private static final float DEFAULT_FLOAT = 0.0f;
    private static final boolean DEFAULT_BOOLEAN = false;

    /**
     * 获取默认 SharedPreferences
     * 
     * @param context 上下文
     * @return SharedPreferences 实例
     */
    public static SharedPreferences getDefault(Context context) {
        if (context == null) {
            Logger.e("PrefHelper", "context 不能为null");
            return null;
        }
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * 获取指定名称的 SharedPreferences
     * 
     * @param context 上下文
     * @param name 文件名
     * @return SharedPreferences 实例
     */
    public static SharedPreferences get(Context context, String name) {
        if (context == null) {
            Logger.e("PrefHelper", "context 不能为null");
            return null;
        }
        if (TextUtils.isEmpty(name)) {
            return getDefault(context);
        }
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /**
     * 保存字符串（异步）
     * 
     * @param context 上下文
     * @param key 键
     * @param value 值
     */
    public static void putString(Context context, String key, String value) {
        putString(context, DEFAULT_PREFS_NAME, key, value, false);
    }

    /**
     * 保存字符串（可指定文件名和同步模式）
     * 
     * @param context 上下文
     * @param prefsName 文件名
     * @param key 键
     * @param value 值
     * @param sync 是否同步写入（true=commit, false=apply）
     */
    public static void putString(Context context, String prefsName, String key, String value, boolean sync) {
        if (context == null || TextUtils.isEmpty(key)) {
            Logger.e("PrefHelper", "参数不合法: context=" + context + ", key=" + key);
            return;
        }

        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) return;

        if (sync) {
            prefs.edit().putString(key, value).commit();
        } else {
            prefs.edit().putString(key, value).apply();
        }
    }

    /**
     * 保存字符串（同步）- 用于关键数据（如Cookie）
     * 
     * @param context 上下文
     * @param prefsName 文件名
     * @param key 键
     * @param value 值
     */
    public static void putStringSync(Context context, String prefsName, String key, String value) {
        putString(context, prefsName, key, value, true);
    }

    /**
     * 获取字符串
     * 
     * @param context 上下文
     * @param key 键
     * @param defaultValue 默认值
     * @return 值
     */
    public static String getString(Context context, String key, String defaultValue) {
        return getString(context, DEFAULT_PREFS_NAME, key, defaultValue);
    }

    /**
     * 获取字符串（可指定文件名）
     * 
     * @param context 上下文
     * @param prefsName 文件名
     * @param key 键
     * @param defaultValue 默认值
     * @return 值
     */
    public static String getString(Context context, String prefsName, String key, String defaultValue) {
        if (context == null || TextUtils.isEmpty(key)) {
            return defaultValue != null ? defaultValue : DEFAULT_STRING;
        }

        SharedPreferences prefs = get(context, prefsName);
        return prefs != null ? prefs.getString(key, defaultValue) : defaultValue;
    }

    /**
     * 保存整数（异步）
     */
    public static void putInt(Context context, String key, int value) {
        putInt(context, DEFAULT_PREFS_NAME, key, value, false);
    }

    /**
     * 保存整数（可指定文件名和同步模式）
     */
    public static void putInt(Context context, String prefsName, String key, int value, boolean sync) {
        if (context == null || TextUtils.isEmpty(key)) {
            Logger.e("PrefHelper", "参数不合法: context=" + context + ", key=" + key);
            return;
        }

        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) return;

        if (sync) {
            prefs.edit().putInt(key, value).commit();
        } else {
            prefs.edit().putInt(key, value).apply();
        }
    }

    /**
     * 获取整数
     */
    public static int getInt(Context context, String key, int defaultValue) {
        return getInt(context, DEFAULT_PREFS_NAME, key, defaultValue);
    }

    /**
     * 获取整数（可指定文件名）
     */
    public static int getInt(Context context, String prefsName, String key, int defaultValue) {
        if (context == null || TextUtils.isEmpty(key)) {
            return defaultValue;
        }

        SharedPreferences prefs = get(context, prefsName);
        return prefs != null ? prefs.getInt(key, defaultValue) : defaultValue;
    }

    /**
     * 保存长整型（异步）
     */
    public static void putLong(Context context, String key, long value) {
        putLong(context, DEFAULT_PREFS_NAME, key, value, false);
    }

    /**
     * 保存长整型（可指定文件名和同步模式）
     */
    public static void putLong(Context context, String prefsName, String key, long value, boolean sync) {
        if (context == null || TextUtils.isEmpty(key)) {
            Logger.e("PrefHelper", "参数不合法: context=" + context + ", key=" + key);
            return;
        }

        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) return;

        if (sync) {
            prefs.edit().putLong(key, value).commit();
        } else {
            prefs.edit().putLong(key, value).apply();
        }
    }

    /**
     * 获取长整型
     */
    public static long getLong(Context context, String key, long defaultValue) {
        return getLong(context, DEFAULT_PREFS_NAME, key, defaultValue);
    }

    /**
     * 获取长整型（可指定文件名）
     */
    public static long getLong(Context context, String prefsName, String key, long defaultValue) {
        if (context == null || TextUtils.isEmpty(key)) {
            return defaultValue;
        }

        SharedPreferences prefs = get(context, prefsName);
        return prefs != null ? prefs.getLong(key, defaultValue) : defaultValue;
    }

    /**
     * 保存布尔值（异步）
     */
    public static void putBoolean(Context context, String key, boolean value) {
        putBoolean(context, DEFAULT_PREFS_NAME, key, value, false);
    }

    /**
     * 保存布尔值（可指定文件名和同步模式）
     */
    public static void putBoolean(Context context, String prefsName, String key, boolean value, boolean sync) {
        if (context == null || TextUtils.isEmpty(key)) {
            Logger.e("PrefHelper", "参数不合法: context=" + context + ", key=" + key);
            return;
        }

        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) return;

        if (sync) {
            prefs.edit().putBoolean(key, value).commit();
        } else {
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    /**
     * 获取布尔值
     */
    public static boolean getBoolean(Context context, String key, boolean defaultValue) {
        return getBoolean(context, DEFAULT_PREFS_NAME, key, defaultValue);
    }

    /**
     * 获取布尔值（可指定文件名）
     */
    public static boolean getBoolean(Context context, String prefsName, String key, boolean defaultValue) {
        if (context == null || TextUtils.isEmpty(key)) {
            return defaultValue;
        }

        SharedPreferences prefs = get(context, prefsName);
        return prefs != null ? prefs.getBoolean(key, defaultValue) : defaultValue;
    }

    /**
     * 删除指定键（异步）
     */
    public static void remove(Context context, String key) {
        remove(context, DEFAULT_PREFS_NAME, key, false);
    }

    /**
     * 删除指定键（可指定文件名和同步模式）
     */
    public static void remove(Context context, String prefsName, String key, boolean sync) {
        if (context == null || TextUtils.isEmpty(key)) {
            Logger.e("PrefHelper", "参数不合法: context=" + context + ", key=" + key);
            return;
        }

        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) return;

        if (sync) {
            prefs.edit().remove(key).commit();
        } else {
            prefs.edit().remove(key).apply();
        }
    }

    /**
     * 清空所有数据（异步）
     */
    public static void clear(Context context) {
        clear(context, DEFAULT_PREFS_NAME, false);
    }

    /**
     * 清空所有数据（可指定文件名和同步模式）
     */
    public static void clear(Context context, String prefsName, boolean sync) {
        if (context == null) {
            Logger.e("PrefHelper", "context 不能为null");
            return;
        }

        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) return;

        if (sync) {
            prefs.edit().clear().commit();
        } else {
            prefs.edit().clear().apply();
        }
    }

    /**
     * 检查是否包含指定键
     */
    public static boolean contains(Context context, String key) {
        return contains(context, DEFAULT_PREFS_NAME, key);
    }

    /**
     * 检查是否包含指定键（可指定文件名）
     */
    public static boolean contains(Context context, String prefsName, String key) {
        if (context == null || TextUtils.isEmpty(key)) {
            return false;
        }

        SharedPreferences prefs = get(context, prefsName);
        return prefs != null && prefs.contains(key);
    }

    /**
     * 批量操作接口
     * 用于一次性保存多个键值对
     */
    public interface BatchEditAction {
        void edit(SharedPreferences.Editor editor);
    }

    /**
     * 批量编辑（异步）
     */
    public static void edit(Context context, BatchEditAction action) {
        edit(context, DEFAULT_PREFS_NAME, action, false);
    }

    /**
     * 批量编辑（可指定文件名和同步模式）
     */
    public static void edit(Context context, String prefsName, BatchEditAction action, boolean sync) {
        if (context == null || action == null) {
            Logger.e("PrefHelper", "参数不合法: context=" + context + ", action=" + action);
            return;
        }

        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) return;

        SharedPreferences.Editor editor = prefs.edit();
        action.edit(editor);

        if (sync) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    /**
     * 获取所有键值对（用于调试）
     */
    public static String dumpAll(Context context, String prefsName) {
        SharedPreferences prefs = get(context, prefsName);
        if (prefs == null) {
            return "SharedPreferences不存在";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SharedPreferences [").append(prefsName).append("]:\n");
        
        for (String key : prefs.getAll().keySet()) {
            Object value = prefs.getAll().get(key);
            sb.append("  ").append(key).append(" = ").append(value).append("\n");
        }
        
        return sb.toString();
    }
}

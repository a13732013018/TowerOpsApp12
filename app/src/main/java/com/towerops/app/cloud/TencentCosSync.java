package com.towerops.app.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.towerops.app.model.Session;
import com.towerops.app.model.TodoItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 腾讯云COS待办数据同步服务（OkHttp直连版）
 * 
 * 每个登录账号的数据完全隔离：
 * - 本地文件：todos_{userid}.json
 * - 云端路径：todo_data/todos_{userid}.json
 * 
 * 使用OkHttp直连COS，无需额外SDK
 */
public class TencentCosSync {

    private static final String TAG = "TencentCosSync";

    private static TencentCosSync instance;

    // 腾讯云COS配置
    private static final String COS_SECRET_ID = "AKIDmNmzUs32qa7RYZP2SfhjdMrIYaR7bsQl";
    private static final String COS_SECRET_KEY = "DDzY63pOlxwugFepoZ9WgcQr8C3hARgs";
    private static final String COS_BUCKET = "towerops-app-1420679369";
    private static final String COS_REGION = "ap-guangzhou";
    private static final String COS_HOST = COS_BUCKET + ".cos." + COS_REGION + ".myqcloud.com";

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;

    // 当前账号的userid，用于云端路径隔离
    private String currentUserId = "";

    // 回调接口
    public interface SyncCallback {
        void onSuccess(List<TodoItem> items);
        void onError(String error);
    }

    public interface UploadCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface OnInitCallback {
        void onSuccess();
        void onError(String error);
    }

    private TencentCosSync(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public static synchronized TencentCosSync getInstance(Context context) {
        if (instance == null) {
            instance = new TencentCosSync(context);
        }
        return instance;
    }

    /**
     * 初始化
     */
    public void initialize(OnInitCallback callback) {
        currentUserId = Session.get().userid;
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = "default";
        }
        Log.d(TAG, "初始化完成，当前账号: " + currentUserId);
        notifySuccess(callback);
    }

    /**
     * 获取云端文件路径（按账号隔离）
     */
    private String getCloudFileName() {
        return "todos_" + currentUserId + ".json";
    }

    /**
     * 获取本地缓存文件（按账号隔离）
     */
    private File getLocalCacheFile() {
        return new File(context.getCacheDir(), getCloudFileName());
    }

    /**
     * 上传待办数据到云端
     */
    public void uploadTodos(List<TodoItem> items, UploadCallback callback) {
        executor.execute(() -> {
            try {
                String json = todosToJson(items);

                // 1. 保存到本地缓存
                File localFile = getLocalCacheFile();
                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    fos.write(json.getBytes("UTF-8"));
                    fos.flush();
                }
                Log.d(TAG, "待办已保存到本地缓存: " + localFile.getAbsolutePath());

                // 2. 上传到云端
                uploadToCloud(json, callback);

            } catch (Exception e) {
                Log.e(TAG, "上传待办失败: " + e.getMessage());
                notifyUploadError(callback, e.getMessage());
            }
        });
    }

    /**
     * 从云端下载待办数据
     */
    public void downloadTodos(SyncCallback callback) {
        executor.execute(() -> {
            // 1. 优先从云端下载
            downloadFromCloud(new SyncCallback() {
                @Override
                public void onSuccess(List<TodoItem> items) {
                    Log.d(TAG, "从云端恢复 " + items.size() + " 条待办（账号: " + currentUserId + "）");
                    saveToLocalCache(todosToJson(items));
                    notifySyncSuccess(callback, items);
                }

                @Override
                public void onError(String error) {
                    Log.d(TAG, "云端下载失败: " + error);
                    // 2. 回退到本地缓存
                    loadFromLocalCache(new SyncCallback() {
                        @Override
                        public void onSuccess(List<TodoItem> items) {
                            notifySyncSuccess(callback, items);
                        }

                        @Override
                        public void onError(String error) {
                            notifySyncSuccess(callback, new ArrayList<>());
                        }
                    });
                }
            });
        });
    }

    private void notifySuccess(OnInitCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(OnInitCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    /**
     * 上传到腾讯云COS（使用临时签名）
     */
    private void uploadToCloud(String content, UploadCallback callback) {
        String cosPath = "/todo_data/" + getCloudFileName();
        String url = "https://" + COS_HOST + cosPath;

        // 生成COS签名
        String sign = generateSimpleSign();

        RequestBody body = RequestBody.create(content, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .addHeader("Host", COS_HOST)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", sign)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "上传失败: " + e.getMessage());
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(); // 本地已保存，视为成功
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "上传云端成功");
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess();
                    });
                } else {
                    Log.e(TAG, "上传失败: " + response.code() + " " + response.message());
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("上传失败: " + response.code());
                    });
                }
                response.close();
            }
        });
    }

    /**
     * 从腾讯云COS下载
     */
    private void downloadFromCloud(SyncCallback callback) {
        String cosPath = "/todo_data/" + getCloudFileName();
        String url = "https://" + COS_HOST + cosPath;

        String sign = generateSimpleSign();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Host", COS_HOST)
                .addHeader("Authorization", sign)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "下载失败: " + e.getMessage());
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().string();
                        List<TodoItem> items = jsonToTodos(content);
                        mainHandler.post(() -> {
                            if (callback != null) callback.onSuccess(items);
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError("文件不存在");
                        });
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 生成COS签名
     */
    private String generateSimpleSign() {
        long expiredTime = System.currentTimeMillis() / 1000 + 3600;
        String signTime = expiredTime + ";";
        
        try {
            String signKey = hmacSHA1(COS_SECRET_KEY, signTime);
            String signStr = "a=" + COS_SECRET_ID + "&b=" + COS_BUCKET + "&t=" + signTime + "&r=" + expiredTime;
            String orignal = signStr + "&k=" + signKey;
            String signature = hmacSHA1(COS_SECRET_KEY, orignal);
            
            return "q-sign-algorithm=sha1&q-ak=" + COS_SECRET_ID + 
                   "&q-sign-time=" + signTime + 
                   "&q-key-time=" + signTime + 
                   "&q-header-list=host&q-url-param-list=&q-signature=" + signature;
        } catch (Exception e) {
            Log.e(TAG, "签名失败: " + e.getMessage());
            return "";
        }
    }

    private String hmacSHA1(String key, String input) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            javax.crypto.spec.SecretKeySpec secretKeySpec = 
                new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    private void notifyUploadError(UploadCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(error);
        });
    }

    private void notifySyncSuccess(SyncCallback callback, List<TodoItem> items) {
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(items);
        });
    }

    private void notifySyncError(SyncCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(error);
        });
    }

    /**
     * 保存到本地缓存
     */
    private void saveToLocalCache(String json) {
        try {
            File file = getLocalCacheFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "保存本地缓存失败: " + e.getMessage());
        }
    }

    /**
     * 从本地缓存读取
     */
    private void loadFromLocalCache(SyncCallback callback) {
        try {
            File file = getLocalCacheFile();
            if (!file.exists() || file.length() < 2) {
                Log.d(TAG, "本地缓存为空");
                notifySyncSuccess(callback, new ArrayList<>());
                return;
            }

            String content = readFileContent(file);
            List<TodoItem> items = jsonToTodos(content);
            notifySyncSuccess(callback, items);

        } catch (Exception e) {
            Log.e(TAG, "读取本地缓存失败: " + e.getMessage());
            notifySyncSuccess(callback, new ArrayList<>());
        }
    }

    private String readFileContent(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return new String(data, "UTF-8");
        }
    }

    /**
     * 待办列表转JSON
     */
    private String todosToJson(List<TodoItem> items) {
        JSONArray array = new JSONArray();
        for (TodoItem item : items) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", item.getId());
                obj.put("title", item.getTitle());
                obj.put("content", item.getContent());
                obj.put("createTime", item.getCreateTime());
                obj.put("completed", item.isCompleted());
                obj.put("completedTime", item.getCompletedTime());
                obj.put("priority", item.getPriority());
                obj.put("dueTime", item.getDueTime());
                array.put(obj);
            } catch (Exception e) {
                Log.e(TAG, "JSON转换失败: " + e.getMessage());
            }
        }
        return array.toString();
    }

    /**
     * JSON转待办列表
     */
    private List<TodoItem> jsonToTodos(String json) {
        List<TodoItem> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                TodoItem item = new TodoItem(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.optString("content", ""),
                    obj.getLong("createTime")
                );
                item.setCompleted(obj.getBoolean("completed"));
                item.setCompletedTime(obj.optLong("completedTime", 0));
                item.setPriority(obj.optInt("priority", 0));
                item.setDueTime(obj.optLong("dueTime", 0));
                items.add(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON解析失败: " + e.getMessage());
        }
        return items;
    }

    /**
     * 获取本地缓存文件路径
     */
    public String getLocalBackupPath() {
        File file = getLocalCacheFile();
        return file.exists() ? file.getAbsolutePath() : null;
    }
}

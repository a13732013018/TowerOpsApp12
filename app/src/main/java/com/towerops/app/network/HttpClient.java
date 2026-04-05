package com.towerops.app.network;

import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * OkHttp 客户端统一配置
 * 优化网络性能，统一管理连接池、超时、拦截器
 * 
 * 优化点：
 * 1. 连接池复用：最大5个空闲连接，保持5分钟
 * 2. 超时控制：连接10秒，读取30秒，写入30秒
 * 3. 日志拦截：DEBUG模式记录请求/响应
 * 4. 自动重试：启用失败重试机制
 * 
 * 使用方式：
 * OkHttpClient client = HttpClient.getInstance();
 * Request request = new Request.Builder()...build();
 * Response response = client.newCall(request).execute();
 */
public class HttpClient {

    private static OkHttpClient instance;
    private static final int CONNECT_TIMEOUT = 10;  // 连接超时10秒
    private static final int READ_TIMEOUT = 30;     // 读取超时30秒
    private static final int WRITE_TIMEOUT = 30;    // 写入超时30秒
    
    // 连接池配置
    private static final int MAX_IDLE_CONNECTIONS = 5;  // 最大空闲连接数
    private static final long KEEP_ALIVE_DURATION = 5;  // 保持5分钟（分钟）

    /**
     * 获取 OkHttpClient 单例
     */
    public static OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (HttpClient.class) {
                if (instance == null) {
                    instance = createClient();
                }
            }
        }
        return instance;
    }

    /**
     * 创建 OkHttpClient 实例
     */
    private static OkHttpClient createClient() {
        // 日志拦截器（仅DEBUG模式）
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // 连接池
        ConnectionPool connectionPool = new ConnectionPool(
            MAX_IDLE_CONNECTIONS, 
            KEEP_ALIVE_DURATION, 
            TimeUnit.MINUTES
        );

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .retryOnConnectionFailure(true)  // 启用连接失败自动重试
            .followRedirects(true)           // 跟随重定向
            .followSslRedirects(true);       // 跟随HTTPS重定向

        // DEBUG模式添加日志拦截器
        if (isDebugMode()) {
            builder.addInterceptor(logging);
        }

        return builder.build();
    }

    /**
     * 判断是否为DEBUG模式
     * 通过 BuildConfig.DEBUG 判断（自动生成）
     */
    private static boolean isDebugMode() {
        try {
            // 使用反射避免编译时依赖BuildConfig
            Class<?> buildConfig = Class.forName("com.towerops.app.BuildConfig");
            return (boolean) buildConfig.getField("DEBUG").get(null);
        } catch (Exception e) {
            // 默认开启日志（生产环境可设为false）
            return false;
        }
    }

    /**
     * 获取连接池统计信息（用于调试和性能分析）
     */
    public static String getPoolStats() {
        OkHttpClient client = getInstance();
        ConnectionPool pool = client.connectionPool();
        return String.format(
            "连接池: 空闲连接=%d/%d",
            pool.idleConnectionCount(),
            MAX_IDLE_CONNECTIONS
        );
    }

    /**
     * 关闭客户端并释放资源
     * 注意：调用后getInstance()会创建新实例
     */
    public static void shutdown() {
        synchronized (HttpClient.class) {
            if (instance != null) {
                instance.dispatcher().executorService().shutdown();
                instance.connectionPool().evictAll();
                instance = null;
            }
        }
    }
}

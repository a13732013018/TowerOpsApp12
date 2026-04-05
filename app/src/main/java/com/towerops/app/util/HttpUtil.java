package com.towerops.app.util;

import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 网络请求工具类 —— 对应易语言的 网页_访问_对象()
 * 使用 OkHttp 4.x，复用单例客户端（Keep-Alive 自动生效）
 */
public class HttpUtil {

    private static final MediaType FORM_TYPE =
            MediaType.parse("application/x-www-form-urlencoded");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)       // ★ 跟随 302 重定向（OMMS 可能 302 到登录页）
            .followSslRedirects(true)
            .build();

    /**
     * HTTP 响应结果封装（包含响应体和响应头）
     */
    public static class HttpResponse {
        public String body = "";
        public String setCookie = "";
        public int code = 0;
        public boolean success = false;
    }

    /**
     * POST 请求，返回响应体字符串
     *
     * @param url     目标地址
     * @param post    POST 参数字符串（application/x-www-form-urlencoded 或 JSON 格式）
     * @param headers 附加协议头（格式："Key: Value\nKey2: Value2"），可为 null
     * @param cookie  Cookie 字符串，可为 null
     * @return 响应体，失败返回空字符串
     */
    public static String post(String url, String post, String headers, String cookie) {
        HttpResponse response = postWithHeaders(url, post, headers, cookie);
        return response != null ? response.body : "";
    }

    /**
     * POST 请求，返回完整响应（包含响应体和 Set-Cookie 头）
     * 【核心】用于 PC 登录时获取 Set-Cookie 中的 towerNumber-Token
     *
     * @param url     目标地址
     * @param post    POST 参数字符串（如果为 "__EMPTY_BODY__" 则发送空 body）
     * @param headers 附加协议头
     * @param cookie  Cookie 字符串
     * @return HttpResponse 包含 body 和 setCookie（所有 Set-Cookie 合并后的字符串）
     */
    public static HttpResponse postWithHeaders(String url, String post, String headers, String cookie) {
        HttpResponse result = new HttpResponse();
        try {
            // 【核心】支持空 body POST 请求（Content-Length: 0）
            RequestBody body;
            if ("__EMPTY_BODY__".equals(post)) {
                // 发送真正的空 body，Content-Length 会为 0
                body = RequestBody.create(null, new byte[0]);
            } else {
                // 根据Content-Type自动选择MediaType（兼容带charset的情况）
                MediaType mediaType = FORM_TYPE; // 默认
                if (headers != null && headers.toLowerCase().contains("content-type: application/json")) {
                    mediaType = MediaType.parse("application/json;charset=UTF-8");
                }
                body = RequestBody.create(post, mediaType);
            }
            Request.Builder builder = new Request.Builder()
                    .url(url.trim())
                    .post(body);

            // 默认协议头
            builder.header("User-Agent", "okhttp/4.10.0");
            builder.header("Connection", "Keep-Alive");

            // 附加 cookie
            if (cookie != null && !cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }

            // 解析自定义协议头（换行分隔，格式 "Key: Value"）
            if (headers != null && !headers.isEmpty()) {
                String[] lines = headers.split("\n");
                for (String line : lines) {
                    // 【修正】支持 "Key: Value" 和 "Key:Value" 两种格式
                    int idx = line.indexOf(": ");
                    if (idx == -1) {
                        idx = line.indexOf(":");
                    }
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String val = line.substring(idx + 1).trim();
                        // 如果以空格开头，去掉空格
                        if (val.startsWith(" ")) {
                            val = val.substring(1);
                        }
                        builder.header(key, val);
                        System.out.println("[HttpUtil] Header: " + key + " = " + (key.equalsIgnoreCase("Cookie") || key.equalsIgnoreCase("Authorization") ? val.substring(0, Math.min(20, val.length())) + "..." : val));
                    }
                }
            }

            Request request = builder.build();
            
            // 【调试】打印最终请求的所有header
            System.out.println("[HttpUtil] Request URL: " + url);
            System.out.println("[HttpUtil] Request headers:");
            for (String name : request.headers().names()) {
                String val = request.header(name);
                if (name.equalsIgnoreCase("Cookie") || name.equalsIgnoreCase("Authorization")) {
                    System.out.println("  " + name + ": " + (val != null ? val.substring(0, Math.min(20, val.length())) + "..." : "null"));
                } else {
                    System.out.println("  " + name + ": " + val);
                }
            }
            
            try (Response response = CLIENT.newCall(request).execute()) {
                result.code = response.code();
                result.success = result.code >= 200 && result.code < 300;
                
                // 【核心】提取所有 Set-Cookie 头并合并（服务器可能返回多个）
                java.util.List<String> setCookieList = response.headers("Set-Cookie");
                if (!setCookieList.isEmpty()) {
                    StringBuilder sbCookie = new StringBuilder();
                    for (String sc : setCookieList) {
                        String part = sc.split(";")[0].trim(); // 只取 name=value 部分
                        if (sbCookie.length() > 0) sbCookie.append("; ");
                        sbCookie.append(part);
                    }
                    result.setCookie = sbCookie.toString();
                    System.out.println("[HttpUtil] Set-Cookie merged: " + result.setCookie.substring(0, Math.min(80, result.setCookie.length())));
                }
                
                if (response.body() != null) {
                    result.body = response.body().string();
                }
                // 记录详细日志
                System.out.println("[HttpUtil] POST " + url + " -> " + result.code);
                if (!result.success) {
                    System.err.println("[HttpUtil] Error response: " + result.code + " body: " + result.body);
                }
                return result;
            }
        } catch (Exception e) {
            System.err.println("[HttpUtil] POST Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * POST 请求（无 Cookie，无自定义头）
     */
    public static String post(String url, String post) {
        return post(url, post, null, null);
    }

    /**
     * GET 请求，返回响应体字符串
     */
    public static String get(String url, String headers, String cookie) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url.trim())
                    .get();

            // 默认协议头
            builder.header("User-Agent", "okhttp/4.10.0");
            builder.header("Connection", "Keep-Alive");

            // 附加 cookie
            if (cookie != null && !cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }

            // 解析自定义协议头（换行分隔，格式 "Key: Value"）
            if (headers != null && !headers.isEmpty()) {
                String[] lines = headers.split("\n");
                for (String line : lines) {
                    // 与 POST 保持一致：支持 "Key: Value" 和 "Key:Value"
                    int idx = line.indexOf(": ");
                    if (idx == -1) {
                        idx = line.indexOf(":");
                    }
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String val = line.substring(idx + 1).trim();
                        builder.header(key, val);
                    }
                }
            }

            Request request = builder.build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * GET 请求（无自定义头和cookie）
     */
    public static String get(String url) {
        return get(url, null, null);
    }

    /**
     * PUT 请求，返回响应体字符串
     * 对应易语言 网页_访问_对象(url, 3, post, ...)
     *
     * @param url     目标地址
     * @param put     PUT 请求体字符串
     * @param headers 附加协议头
     * @param cookie  Cookie 字符串
     * @return 响应体，失败返回空字符串
     */
    public static String put(String url, String put, String headers, String cookie) {
        try {
            // 根据Content-Type自动选择MediaType（兼容带charset的情况）
            MediaType mediaType = FORM_TYPE; // 默认
            if (headers != null && headers.toLowerCase().contains("content-type: application/json")) {
                mediaType = MediaType.parse("application/json;charset=UTF-8");
            }
            RequestBody body = RequestBody.create(put, mediaType);
            Request.Builder builder = new Request.Builder()
                    .url(url.trim())
                    .put(body);

            // 默认协议头
            builder.header("User-Agent", "okhttp/4.10.0");
            builder.header("Connection", "Keep-Alive");

            // 附加 cookie
            if (cookie != null && !cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }

            // 解析自定义协议头
            if (headers != null && !headers.isEmpty()) {
                String[] lines = headers.split("\n");
                for (String line : lines) {
                    int idx = line.indexOf(": ");
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String val = line.substring(idx + 2).trim();
                        builder.header(key, val);
                    }
                }
            }

            Request request = builder.build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * GET 请求，返回字节数组（用于图片等二进制内容）
     * 同时会自动将响应的 Set-Cookie 存入 CookieStore
     */
    public static byte[] getBytes(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url.trim())
                    .get()
                    .header("User-Agent", "okhttp/4.10.0")
                    .header("Connection", "Keep-Alive")
                    .build();

            try (okhttp3.Response response = CLIENT.newCall(request).execute()) {
                // 提取并保存 Cookie（合并所有 Set-Cookie 头，服务器可能返回多个）
                java.util.List<String> setCookies = response.headers("Set-Cookie");
                if (!setCookies.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String sc : setCookies) {
                        // 取 name=value 部分（分号前），多个 Cookie 用 "; " 连接
                        String part = sc.split(";")[0].trim();
                        if (sb.length() > 0) sb.append("; ");
                        sb.append(part);
                    }
                    String allCookies = sb.toString();
                    System.out.println("[HttpUtil] Saving cookies: " + allCookies);
                    com.towerops.app.util.CookieStore.saveCookie(allCookies);
                }
                if (response.body() != null) {
                    return response.body().bytes();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

package com.towerops.app.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.towerops.app.model.Session;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 静默获取 tymj 门禁 Bearer Token（全自动化，无需手动抓包）
 *
 * 流程（类似 OmmsWebViewHelper，完全自动）：
 *   1. OkHttp 带4A Cookie POST /uac/home/soaprequest(r=360650, s=100033, ssoPwd="")
 *   2. 拿到 SSO URL
 *   3. 把4A Cookie 注入 WebView CookieManager
 *   4. WebView 加载 SSO URL，完整走 SSO 跳转链
 *   5. onPageFinished 后用 JS 从 localStorage/sessionStorage 获取 Authorization Token
 *   6. 同时检查 tymj 域 Cookie 中是否有 Authorization 字段
 *
 * Bearer Token 用途：
 *   POST http://tymj.chinatowercom.cn:8006/api/recordAccess/getPage
 *   Header: Authorization: Bearer {token}
 */
public class TymjWebViewHelper {

    private static final String TAG           = "TymjWebViewHelper";
    private static final String BASE_4A      = "http://4a.chinatowercom.cn:20000";
    private static final String UA4A_HOME     = BASE_4A + "/uac/home";
    private static final String TYMS_HOST     = "http://tymj.chinatowercom.cn:8006";

    // soaprequest 参数：门禁系统 (r=360650)
    private static final String R_TYMJ = "360650";
    private static final String S_TYMJ = "100033";

    public interface Callback {
        /** token 不带 "Bearer " 前缀，原始值 */
        void onSuccess(String bearerToken);
        void onFail(String reason);
    }

    /**
     * 全自动 SSO 流程：soaprequest → WebView 加载 SSO URL → 捕获 Bearer Token
     *
     * @param context       Activity/Fragment Context（必须主线程调用）
     * @param tower4aCookie 4A 登录拿到的 SESSION/route Cookie 字符串
     * @param callback      结果回调（主线程）
     */
    public static void fetchBearerToken(Context context,
                                        String tower4aCookie,
                                        Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                // Step 1：调 soaprequest 获取 SSO URL（空 ssoPwd，跟 OMMS 一样）
                SoapResult result = callSoaprequest(tower4aCookie);
                android.util.Log.d(TAG, "soaprequest status=" + result.status + " url=" + result.url);

                if (result.status == null) {
                    mainHandler.post(() -> callback.onFail("soaprequest 网络错误或4A Session已过期"));
                    return;
                }

                if ("success".equals(result.status)) {
                    // 正常 SSO URL → WebView 加载
                    mainHandler.post(() -> loadSsoInWebView(context, tower4aCookie, result.url,
                            mainHandler, callback));
                } else {
                    // 降级：soaprequest 失败，直接访问 tymj 首页
                    android.util.Log.w(TAG, "soaprequest 失败（FALLBACK），直接访问 tymj 首页");
                    mainHandler.post(() -> loadSsoInWebView(context, tower4aCookie, TYMS_HOST + "/",
                            mainHandler, callback));
                }

            } catch (Exception e) {
                android.util.Log.e(TAG, "fetchBearerToken error: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onFail("网络错误: " + e.getMessage()));
            }
        }).start();
    }

    // ─── soaprequest 调用（空 ssoPwd，跟 OMMS 一样）──────────────────────────────

    private static class SoapResult {
        String status;   // "success" / null
        String url;      // SSO URL（当 status=success 时）
        String message;  // 错误消息
    }

    /**
     * OkHttp 调 4A soaprequest 接口（r=360650），ssoPwd 为空。
     */
    private static SoapResult callSoaprequest(String cookieStr) throws IOException {
        SoapResult result = new SoapResult();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        String url = UA4A_HOME + "/soaprequest?r=" + System.currentTimeMillis();

        FormBody body = new FormBody.Builder()
                .add("r", R_TYMJ)
                .add("s", S_TYMJ)
                .add("ssoPwd", "")          // 空字符串，跟 OMMS 一样
                .add("superUserCode", "")
                .add("superRandom", "")
                .build();

        Request req = new Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Host", "4a.chinatowercom.cn:20000")
                .header("Origin", BASE_4A)
                .header("Referer", UA4A_HOME + "/index")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", cookieStr != null ? cookieStr : "")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";

            // 302 → Session 过期
            if (resp.code() == 302 || resp.code() == 301) {
                android.util.Log.e(TAG, "soaprequest 302 Location=" + resp.header("Location"));
                result.status = null;
                result.message = "4A Session 已过期（302）";
                return result;
            }

            if (!resp.isSuccessful() && resp.code() != 200) {
                android.util.Log.e(TAG, "soaprequest HTTP " + resp.code());
                result.status = null;
                result.message = "HTTP " + resp.code();
                return result;
            }

            // 返回了 HTML 登录页 → Session 过期
            if (respBody.contains("<html") || respBody.contains("doPrevLogin")) {
                android.util.Log.e(TAG, "soaprequest returned HTML (session expired)");
                result.status = null;
                result.message = "4A Session 已过期（HTML重定向）";
                return result;
            }

            try {
                JSONObject j = new JSONObject(respBody);
                result.status = j.optString("status", "");
                result.url = j.optString("url", "");
                result.message = j.optString("message", j.optString("msg", ""));

                android.util.Log.d(TAG, "soaprequest: status=" + result.status
                        + " url=" + (result.url.length() > 100 ? result.url.substring(0, 100) : result.url));

                if (!"success".equals(result.status) || result.url.isEmpty()) {
                    android.util.Log.e(TAG, "soaprequest not success: " + respBody.substring(0, Math.min(300, respBody.length())));
                    result.status = null; // 触发 FALLBACK
                }
                return result;

            } catch (org.json.JSONException e) {
                android.util.Log.e(TAG, "soaprequest JSON parse error: " + e.getMessage()
                        + " body=" + respBody.substring(0, Math.min(200, respBody.length())));
                result.status = null;
                return result;
            }
        }
    }

    // ─── WebView SSO 加载 ─────────────────────────────────────────────────────

    private static void loadSsoInWebView(Context context, String tower4aCookie,
                                         String ssoUrl, Handler mainHandler,
                                         Callback callback) {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        // 清除旧的 tymj Cookie
        cm.setCookie(TYMS_HOST, "Authorization=; Max-Age=0");
        cm.setCookie(TYMS_HOST, "JSESSIONID=; Max-Age=0");
        cm.flush();

        // 注入4A Cookie 到4A域
        if (tower4aCookie != null) {
            for (String pair : tower4aCookie.split(";")) {
                pair = pair.trim();
                if (!pair.isEmpty()) {
                    cm.setCookie(BASE_4A, pair);
                }
            }
            // 4A Cookie 也注入到 tymj 域（SSO 跨域传递）
            for (String pair : tower4aCookie.split(";")) {
                pair = pair.trim();
                if (!pair.isEmpty()) {
                    cm.setCookie(TYMS_HOST, pair);
                }
            }
        }
        // tymj 域基础 Cookie
        cm.setCookie(TYMS_HOST, "userOrgCode=100033");
        cm.flush();

        android.util.Log.d(TAG, "loadSsoInWebView: loading ssoUrl=" + ssoUrl);

        WebView wv = new WebView(context.getApplicationContext());
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36");
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);

        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        final boolean[] done = {false};

        // 超时保护：30秒
        Runnable timeoutRunnable = () -> {
            if (!done[0]) {
                done[0] = true;
                android.util.Log.w(TAG, "WebView SSO 超时30s");
                destroyWebView(wv);
                callback.onFail("等待超时（30秒），请确保4A登录有效");
            }
        };
        mainHandler.postDelayed(timeoutRunnable, 30_000);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                android.util.Log.d(TAG, "redirect -> " + request.getUrl());
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                android.util.Log.d(TAG, "onPageFinished url=" + url);
                if (done[0]) return;

                // 被踢回4A登录页 → Session 过期
                if (url.contains("uac/login") || url.contains("doPrevLogin")) {
                    done[0] = true;
                    mainHandler.removeCallbacks(timeoutRunnable);
                    destroyWebView(view);
                    callback.onFail("4A Session 已过期，请重新登录4A账号");
                    return;
                }

                // tymj 相关页面 → 检查 Cookie 和 localStorage
                if (url.contains("tymj.chinatowercom.cn")) {
                    mainHandler.postDelayed(() -> {
                        if (done[0]) return;
                        checkAndExtractToken(view, cm, mainHandler, timeoutRunnable, done, callback);
                    }, 2000);
                }
            }
        });

        wv.loadUrl(ssoUrl);
    }

    /**
     * 检查 WebView 中的 Authorization Token。
     * 尝试多种方式：
     *   1. Cookie: Authorization 字段（不带 Bearer 前缀）
     *   2. localStorage.getItem("token")
     *   3. localStorage.getItem("access_token")
     *   4. sessionStorage.getItem("token")
     *   5. JS 变量 window.token / window.authorizationToken
     */
    private static void checkAndExtractToken(WebView view, CookieManager cm,
                                             Handler mainHandler,
                                             Runnable timeoutRunnable,
                                             boolean[] done, Callback callback) {
        // 方式1：检查 Cookie 中的 Authorization
        String rawCookie = cm.getCookie(TYMS_HOST);
        android.util.Log.d(TAG, "checkAndExtractToken rawCookie=" + (rawCookie != null ? rawCookie.substring(0, Math.min(100, rawCookie.length())) : "null"));

        String bearerToken = null;

        if (rawCookie != null) {
            for (String part : rawCookie.split(";")) {
                part = part.trim();
                if (part.startsWith("Authorization=") || part.startsWith("authorization=")) {
                    bearerToken = part.substring(part.indexOf('=') + 1).trim();
                    if (bearerToken.startsWith("Bearer ")) {
                        bearerToken = bearerToken.substring("Bearer ".length());
                    }
                    break;
                }
            }
        }

        // 方式2-5：JS 注入读取 localStorage / sessionStorage / JS 变量
        if (bearerToken == null || bearerToken.isEmpty()) {
            String[] keys = {
                    "token", "access_token", "bearerToken", "authorization",
                    "Authorization", "tokenData", "authToken", "userToken"
            };
            for (String key : keys) {
                String js = "(function(){"
                        + "try{"
                        + "var v=localStorage.getItem('" + key + "');"
                        + "if(v)return v;"
                        + "v=sessionStorage.getItem('" + key + "');"
                        + "if(v)return v;"
                        + "}catch(e){}"
                        + "try{"
                        + "if(window." + key + ")return window." + key + ";"
                        + "}catch(e){}"
                        + "return null;"
                        + "})()";
                final String fKey = key;
                view.evaluateJavascript(js, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (done[0]) return;
                        if (value != null && !value.equals("null") && !value.equals("\"null\"")) {
                            String v = value.replaceAll("^\"|\"$", "").trim();
                            if (!v.isEmpty()) {
                                android.util.Log.d(TAG, "Found token via key=" + fKey + " len=" + v.length());
                                if (!done[0]) {
                                    done[0] = true;
                                    mainHandler.removeCallbacks(timeoutRunnable);
                                    destroyWebView(view);
                                    callback.onSuccess(v);
                                }
                            }
                        }
                    }
                });
            }
        }

        // Cookie 方式直接成功
        if (bearerToken != null && !bearerToken.isEmpty()) {
            android.util.Log.d(TAG, "Found Bearer Token in Cookie len=" + bearerToken.length());
            if (!done[0]) {
                done[0] = true;
                mainHandler.removeCallbacks(timeoutRunnable);
                destroyWebView(view);
                callback.onSuccess(bearerToken);
            }
            return;
        }

        // 还没找到，再等一下下
        mainHandler.postDelayed(() -> {
            if (done[0]) return;
            // 再试一次 Cookie
            String retryCookie = cm.getCookie(TYMS_HOST);
            if (retryCookie != null) {
                for (String part : retryCookie.split(";")) {
                    part = part.trim();
                    if (part.startsWith("Authorization=") || part.startsWith("authorization=")) {
                        String t = part.substring(part.indexOf('=') + 1).trim();
                        if (t.startsWith("Bearer ")) t = t.substring("Bearer ".length());
                        if (!t.isEmpty() && !done[0]) {
                            done[0] = true;
                            mainHandler.removeCallbacks(timeoutRunnable);
                            destroyWebView(view);
                            callback.onSuccess(t);
                            return;
                        }
                    }
                }
            }
        }, 3000);
    }

    private static void destroyWebView(WebView wv) {
        try {
            wv.stopLoading();
            wv.clearHistory();
            wv.clearCache(true);
            wv.loadUrl("about:blank");
            wv.removeAllViews();
            wv.destroy();
        } catch (Exception e) {
            android.util.Log.e(TAG, "destroyWebView error: " + e.getMessage());
        }
    }
}

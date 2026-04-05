package com.towerops.app.api;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.towerops.app.model.Session;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 用4A soaprequest → WebView SSO，静默获取含 pwdaToken 的 OMMS Cookie
 *
 * 流程：
 *   1. OkHttp 带4A Cookie POST /uac/home/soaprequest(r=200007, s=100033)
 *   2. 拿到 SSO URL（形如 /uac_oa/ssoForUac?args=...）
 *   3. 把4A Cookie 注入 WebView CookieManager
 *   4. WebView 加载 SSO URL，完整走 SSO 跳转链（→ OMMS From4A.jsp → index.xhtml）
 *   5. onPageFinished 检测 OMMS 有效页面后等 JS 写 pwdaToken，再取出 Cookie
 *
 * 为什么不直接加载 From4A.jsp：
 *   From4A.jsp 是 SSO URL 的最终目的地，直接访问缺少 soaprequest 生成的 token，
 *   OMMS 无法验证身份，会重定向到 login.xhtml（登录页）。
 */
public class OmmsWebViewHelper {

    private static final String TAG          = "OmmsWebViewHelper";
    private static final String OMMS_HOST    = "http://omms.chinatowercom.cn:9000";
    private static final String BASE_4A      = "http://4a.chinatowercom.cn:20000";
    private static final String UA4A_HOME    = BASE_4A + "/uac/home";

    // soaprequest 参数：运维监控
    private static final String R_OMMS = "200007";
    private static final String S_OMMS = "100033";

    public interface Callback {
        void onSuccess(String ommsCookie);
        void onFail(String reason);
    }

    /**
     * 完整 SSO 流程：soaprequest → WebView 加载 SSO URL → 捕获 pwdaToken Cookie
     *
     * @param context       Activity/Fragment Context（必须主线程调用）
     * @param tower4aCookie 4A 登录拿到的 SESSION/route Cookie 字符串
     * @param loginName     4A 登录账号（如 "wx-linjy22"），soaprequest 认证用
     * @param callback      结果回调（主线程）
     */
    public static void fetchOmmsCookie(Context context,
                                       String tower4aCookie,
                                       String loginName,
                                       Callback callback) {
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        // ── Step 1：在后台线程调 soaprequest 获取 SSO URL ─────────────────
        new Thread(() -> {
            try {
                String ssoUrl = callSoaprequest(tower4aCookie);
                android.util.Log.d(TAG, "soaprequest ssoUrl=" + ssoUrl);

                // soaprequest 返回 null → Session 真的过期
                if (ssoUrl == null) {
                    mainHandler.post(() -> callback.onFail(
                            "soaprequest 返回空 URL，4A Session 可能已过期"));
                    return;
                }

                // ★ FALLBACK 降级：soaprequest 接口失败（内部错误/限流等），
                //   直接构造 From4A.jsp URL，WebView 携带4A Cookie 直接访问
                if ("FALLBACK".equals(ssoUrl)) {
                    String fallbackUrl = OMMS_HOST + "/From4A.jsp?moduleurl=/layout/index.xhtml";
                    // 尝试追加 loginName（用 Session 里保存的 username）
                    String fallbackLoginName = Session.get().username;
                    if (fallbackLoginName != null && !fallbackLoginName.isEmpty()) {
                        fallbackUrl = OMMS_HOST + "/From4A.jsp?loginName=" + fallbackLoginName
                                + "&moduleurl=/layout/index.xhtml";
                    }
                    android.util.Log.w(TAG, "soaprequest降级，直接访问From4A.jsp: " + fallbackUrl);
                    final String finalFallback = fallbackUrl;
                    mainHandler.post(() -> loadSsoInWebView(context, tower4aCookie,
                            finalFallback, mainHandler, callback));
                    return;
                }

                // ── Step 2：回主线程创建 WebView 并加载 SSO URL ─────────────
                mainHandler.post(() -> loadSsoInWebView(context, tower4aCookie, ssoUrl,
                        mainHandler, callback));

            } catch (Exception e) {
                android.util.Log.e(TAG, "fetchOmmsCookie soaprequest error: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onFail("soaprequest 网络错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * OkHttp 调 4A soaprequest 接口，返回 SSO URL（失败返回 null）。
     */
    private static String callSoaprequest(String cookieStr) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        String url = UA4A_HOME + "/soaprequest?r=" + System.currentTimeMillis();

        FormBody body = new FormBody.Builder()
                .add("r", R_OMMS)
                .add("s", S_OMMS)
                .add("ssoPwd", "")
                .add("superUserCode", "")
                .add("superRandom", "")
                .add("aliJar", "")
                .build();

        Request req = new Request.Builder()
                .url(url)
                .post(body)
                .header("Cookie", cookieStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", BASE_4A)
                .header("Referer", UA4A_HOME + "/index")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            android.util.Log.d(TAG, "soaprequest HTTP=" + resp.code()
                    + " body=" + respBody.substring(0, Math.min(300, respBody.length())));

            // 302 → Session 过期
            if (resp.code() == 302 || resp.code() == 301) {
                android.util.Log.e(TAG, "soaprequest 302 Location=" + resp.header("Location"));
                return null;
            }

            if (!resp.isSuccessful()) {
                android.util.Log.e(TAG, "soaprequest HTTP " + resp.code());
                return null;
            }

            // 返回了 HTML 登录页
            if (respBody.contains("<html") || respBody.contains("doPrevLogin")) {
                android.util.Log.e(TAG, "soaprequest returned HTML (session expired)");
                return null;
            }

            try {
                JSONObject j = new JSONObject(respBody);
                String status = j.optString("status", "");
                String ssoUrl = j.optString("url", "");

                android.util.Log.d(TAG, "soaprequest status=" + status
                        + " url=" + ssoUrl.substring(0, Math.min(100, ssoUrl.length())));

                if (!"success".equals(status) || ssoUrl.isEmpty()) {
                    String msg = j.optString("message", j.optString("msg", ""));
                    android.util.Log.e(TAG, "soaprequest not success: status=" + status
                            + " msg=" + msg
                            + " body=" + respBody.substring(0, Math.min(300, respBody.length())));
                    // ★ 降级：返回 "FALLBACK" 标记，调用方可尝试直接 From4A.jsp
                    return "FALLBACK";
                }

                // 去掉 "redirect:" 前缀
                if (ssoUrl.startsWith("redirect:")) {
                    ssoUrl = ssoUrl.substring("redirect:".length());
                }

                // 补全相对 URL（如 /uac_oa/ssoForUac?args=...）
                if (ssoUrl.startsWith("/")) {
                    ssoUrl = BASE_4A + ssoUrl;
                }

                return ssoUrl;

            } catch (org.json.JSONException e) {
                android.util.Log.e(TAG, "soaprequest JSON parse error: " + e.getMessage()
                        + " body=" + respBody.substring(0, Math.min(200, respBody.length())));
                return null;
            }
        }
    }

    /**
     * 在主线程里创建 WebView，加载 SSO URL，等 JS 写 pwdaToken 后捕获 Cookie。
     */
    private static void loadSsoInWebView(Context context, String tower4aCookie,
                                          String ssoUrl, android.os.Handler mainHandler,
                                          Callback callback) {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        // 清除旧 OMMS Cookie，避免干扰
        cm.setCookie(OMMS_HOST, "JSESSIONID=; Max-Age=0");
        cm.setCookie(OMMS_HOST, "pwdaToken=; Max-Age=0");
        cm.flush();

        // 注入4A Cookie 到4A域（WebView 访问4A域时自动带上）
        for (String pair : tower4aCookie.split(";")) {
            pair = pair.trim();
            if (!pair.isEmpty()) {
                cm.setCookie(BASE_4A, pair);
            }
        }
        // ★★ 关键修复：soaprequest 有时直接返回 OMMS 域的 From4A.jsp URL
        // 此时 WebView 访问 OMMS 域时需要携带4A Cookie 才能通过认证
        // 同时将4A Cookie 注入到 OMMS 域
        for (String pair : tower4aCookie.split(";")) {
            pair = pair.trim();
            if (!pair.isEmpty()) {
                cm.setCookie(OMMS_HOST, pair);
            }
        }
        cm.flush();

        android.util.Log.d(TAG, "loadSsoInWebView: loading ssoUrl=" + ssoUrl);

        WebView wv = new WebView(context.getApplicationContext());
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36");
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);

        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        final boolean[] done = {false};

        // 超时保护：25秒
        Runnable timeoutRunnable = () -> {
            if (!done[0]) {
                done[0] = true;
                android.util.Log.w(TAG, "WebView SSO 超时25s");
                destroyWebView(wv);
                callback.onFail("WebView 等待超时（25秒），请检查网络或4A Cookie是否过期");
            }
        };
        mainHandler.postDelayed(timeoutRunnable, 25_000);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                android.util.Log.d(TAG, "redirect -> " + url);
                return false; // 放行所有跳转
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
                    callback.onFail("4A Session 已过期，被踢到登录页，请重新登录4A");
                    return;
                }

                // ★★ From4A.jsp：SSO中转页，等待继续跳转，不要在这里报错
                if (url.contains("omms.chinatowercom.cn") && url.contains("From4A.jsp")) {
                    android.util.Log.d(TAG, "SSO中转页 From4A.jsp，等待最终跳转...");
                    // 不做任何操作，等下一次 onPageFinished
                    return;
                }

                // 被踢到 OMMS 自己的 login.xhtml → SSO 失败但可以手动登录
                if (url.contains("omms.chinatowercom.cn") && url.contains("login.xhtml")) {
                    done[0] = true;
                    mainHandler.removeCallbacks(timeoutRunnable);
                    destroyWebView(view);
                    callback.onFail("OMMS SSO失败，落到OMMS登录页（login.xhtml）。"
                            + "请点击「OMMS授权登录」按钮手动完成登录");
                    return;
                }

                // 落到 OMMS 有效页面 → 等 JS 写 pwdaToken（约1.5秒）
                if (url.contains("omms.chinatowercom.cn")
                        && !url.contains("login.xhtml")
                        && !url.contains("logout")) {
                    mainHandler.postDelayed(() -> {
                        if (done[0]) return;
                        checkAndReturn(view, cm, mainHandler, timeoutRunnable, done, callback,
                                context.getApplicationContext());
                    }, 1500);
                }
            }
        });

        wv.loadUrl(ssoUrl);
    }

    private static void checkAndReturn(WebView view, CookieManager cm,
                                       android.os.Handler mainHandler,
                                       Runnable timeoutRunnable,
                                       boolean[] done, Callback callback,
                                       Context appCtx) {
        String rawCookie = cm.getCookie(OMMS_HOST);
        android.util.Log.d(TAG, "checkAndReturn rawCookie="
                + (rawCookie == null ? "null" : rawCookie.substring(0, Math.min(120, rawCookie.length()))));

        if (rawCookie != null && rawCookie.contains("pwdaToken")) {
            // ✅ 成功
            done[0] = true;
            mainHandler.removeCallbacks(timeoutRunnable);
            String ommsCookie = filterOmmsCookie(rawCookie);
            android.util.Log.d(TAG, "SUCCESS ommsCookie len=" + ommsCookie.length());
            Session.get().ommsCookie = ommsCookie;
            // ★ 持久化到 SharedPreferences（避免 APP 重启后丢失）
            Session.get().saveOmmsCookie(appCtx);
            destroyWebView(view);
            callback.onSuccess(ommsCookie);

        } else if (rawCookie != null && rawCookie.contains("JSESSIONID")) {
            // 有 JSESSIONID 但 pwdaToken 还没写，再等1.5秒
            android.util.Log.d(TAG, "JSESSIONID ok, waiting for pwdaToken...");
            mainHandler.postDelayed(() -> {
                if (done[0]) return;
                String raw2 = cm.getCookie(OMMS_HOST);
                if (raw2 != null && raw2.contains("pwdaToken")) {
                    done[0] = true;
                    mainHandler.removeCallbacks(timeoutRunnable);
                    String ommsCookie = filterOmmsCookie(raw2);
                    Session.get().ommsCookie = ommsCookie;
                    // ★ 持久化到 SharedPreferences（避免 APP 重启后丢失）
                    Session.get().saveOmmsCookie(appCtx);
                    destroyWebView(view);
                    callback.onSuccess(ommsCookie);
                } else {
                    // 仍然没有 pwdaToken，以 JSESSIONID 兜底
                    done[0] = true;
                    mainHandler.removeCallbacks(timeoutRunnable);
                    String fallback = raw2 != null ? filterOmmsCookie(raw2) : "";
                    android.util.Log.w(TAG, "no pwdaToken after retry, fallback=" + fallback);
                    destroyWebView(view);
                    if (fallback.isEmpty()) {
                        callback.onFail("OMMS SSO 完成但未获取到任何 Cookie");
                    } else {
                        callback.onFail("OMMS 获取到 JSESSIONID 但无 pwdaToken（OMMS 可能暂时不可用）");
                    }
                }
            }, 1500);

        } else {
            // 还没有任何 OMMS Cookie，等待下一次 onPageFinished
            android.util.Log.d(TAG, "no OMMS cookie yet, waiting for next page");
        }
    }

    private static void destroyWebView(WebView wv) {
        try {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            wv.clearHistory();
            wv.destroy();
        } catch (Exception ignored) {}
    }

    /** 过滤只保留 OMMS 相关字段 */
    private static String filterOmmsCookie(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(
                "JSESSIONID", "pwdaToken", "nodeInformation", "acctId", "uid",
                "loginName", "fp", "userOrgCode", "lang", "route", "moduleUrl"
        ));
        StringBuilder sb = new StringBuilder();
        for (String pair : raw.split(";")) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = pair.substring(0, eq).trim();
                if (keep.contains(k) || k.startsWith("JSESSIONID") || k.equals("pwdaToken")) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(pair);
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : raw;
    }
}

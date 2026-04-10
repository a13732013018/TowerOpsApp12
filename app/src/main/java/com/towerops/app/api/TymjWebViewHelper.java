package com.towerops.app.api;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.towerops.app.model.Session;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 获取 tymj 门禁 Bearer Token
 *
 * 流程：
 *   1. WebView加载4A首页
 *   2. 注入JS查找门禁链接并获取跳转URL
 *   3. 加载门禁系统
 *   4. 拦截门禁API请求，捕获 Authorization Bearer Token
 */
public class TymjWebViewHelper {

    private static final String TAG           = "TymjWebViewHelper";
    private static final String BASE_4A       = "http://4a.chinatowercom.cn:20000";
    private static final String UA4A_INDEX    = BASE_4A + "/uac/index";
    private static final String TYMS_HOST     = "http://tymj.chinatowercom.cn:8006";

    // 门禁系统参数
    private static final String R_TYMJ = "360650";
    private static final String S_TYMJ = "100033";

    public interface Callback {
        void onSuccess(String bearerToken);
        void onFail(String reason);
    }

    public interface LoginCallback {
        void onLoginRequired();
        void onLoginSuccess();
        void onLoginFailed(String reason);
    }

    /**
     * 获取 Bearer Token
     * 如果4A Cookie无效，自动启动TymjLoginActivity让用户重新登录
     */
    public static void fetchBearerToken(Context context, LoginCallback loginCallback, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final LoginCallback lc = loginCallback;
        final Callback cb = callback;
        final Context ctx = context.getApplicationContext();

        String tower4aCookie = Session.get().tower4aSessionCookie;
        
        // 检查Cookie是否有效（包含关键字段）
        boolean cookieValid = tower4aCookie != null && !tower4aCookie.isEmpty() 
                && (tower4aCookie.contains("SESSION=") || tower4aCookie.contains("Tnuocca="));
        
        if (!cookieValid) {
            Log.d(TAG, "4A Cookie无效或为空，启动TymjLoginActivity...");
            lc.onLoginRequired();
            
            // 清空旧Cookie（已无效）
            Session session = Session.get();
            session.tower4aSessionCookie = "";
            
            // 启动TymjLoginActivity让用户在WebView中登录4A
            mainHandler.post(() -> {
                Intent intent = new Intent(ctx, com.towerops.app.ui.TymjLoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            });
            return;
        }

        Log.d(TAG, "开始获取Bearer Token（Cookie有效）");

        mainHandler.post(() -> {
            lc.onLoginSuccess();
            startWebViewFlow(context, tower4aCookie, cb);
        });
    }

    private static void startWebViewFlow(Context context, String tower4aCookie, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final Callback cb = callback;

        Log.d(TAG, "startWebViewFlow: 加载4A首页");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.removeAllCookies(null);
        cm.flush();

        try { Thread.sleep(300); } catch (InterruptedException e) {}

        // 注入4A Cookie
        if (tower4aCookie != null) {
            for (String pair : tower4aCookie.split(";")) {
                pair = pair.trim();
                if (!pair.isEmpty()) {
                    cm.setCookie(BASE_4A, pair);
                    cm.setCookie(TYMS_HOST, pair);
                }
            }
        }
        cm.flush();

        WebView wv = new WebView(context.getApplicationContext());
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        // JS接口
        final boolean[] done = {false};

        wv.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setModuleUrl(String url) {
                Log.d(TAG, "JS回调捕获URL: " + url);
                if (url != null && url.contains("tymj") && !done[0]) {
                    done[0] = true;
                    mainHandler.post(() -> {
                        Log.d(TAG, "跳转到门禁: " + url);
                        wv.loadUrl(url);
                    });
                }
            }
        }, "androidBridge");

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest req) {
                String url = req.getUrl().toString();
                Log.d(TAG, "nav -> " + url);

                // 捕获门禁URL
                if (url.contains("tymj.chinatowercom.cn")) {
                    Log.d(TAG, "捕获到门禁URL");
                }

                // 登录页
                if (url.contains("uac/login") || url.contains("doPrevLogin")) {
                    if (!done[0]) {
                        done[0] = true;
                        destroyWebView(view);
                        mainHandler.post(() -> cb.onFail("4A Session已过期"));
                    }
                    return true;
                }
                return false;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (!done[0] && url.contains("tymj.chinatowercom.cn")) {
                    java.util.Map<String, String> headers = req.getRequestHeaders();
                    String rawAuth = headers.get("Authorization");
                    if (rawAuth != null && !rawAuth.isEmpty()) {
                        Log.d(TAG, "拦截到 Authorization!");
                        final String auth = rawAuth.startsWith("Bearer ") ? rawAuth.substring(7) : rawAuth;
                        if (!done[0]) {
                            done[0] = true;
                            mainHandler.post(() -> {
                                destroyWebView(view);
                                cb.onSuccess(auth);
                            });
                        }
                    }
                }
                return super.shouldInterceptRequest(view, req);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url);
                if (done[0]) return;

                // 4A首页加载完成，注入JS查找门禁链接
                if (url.contains("4a.chinatowercom.cn") && !url.contains("login")) {
                    mainHandler.postDelayed(() -> {
                        if (done[0]) return;
                        injectJsFindDoorLink(view);
                    }, 1000);
                }

                // 门禁页面
                if (url.contains("tymj.chinatowercom.cn")) {
                    mainHandler.postDelayed(() -> triggerApiRequest(view), 2000);
                }
            }
        });

        // 超时保护
        mainHandler.postDelayed(() -> {
            if (!done[0]) {
                done[0] = true;
                destroyWebView(wv);
                mainHandler.post(() -> cb.onFail("获取超时（90秒），请重试"));
            }
        }, 90000);

        // 加载4A首页
        wv.loadUrl(UA4A_INDEX);
    }

    private static void injectJsFindDoorLink(WebView view) {
        String js = "(function(){" +
                // 查找所有链接，找到门禁相关的
                "var foundUrl = null;" +
                "document.querySelectorAll('a').forEach(function(a){" +
                "  var href = a.href || '';" +
                "  var onclick = a.getAttribute('onclick') || '';" +
                "  if(href.indexOf('tymj') > 0 || onclick.indexOf('360650') > 0 || onclick.indexOf('gores') > 0){" +
                "    foundUrl = href;" +
                "  }" +
                "});" +
                "if(foundUrl){" +
                "  window.androidBridge.setModuleUrl(foundUrl);" +
                "} else if(typeof gores === 'function'){" +
                "  try{" +
                "    var result = gores('360650', '100033');" +
                "    if(result && typeof result === 'object' && result.url){" +
                "      window.androidBridge.setModuleUrl(result.url);" +
                "    } else if(typeof result === 'string' && result.indexOf('http') > 0){" +
                "      window.androidBridge.setModuleUrl(result);" +
                "    }" +
                "  } catch(e) { console.log('gores error'); }" +
                "}" +
                "})()";

        view.evaluateJavascript(js, value -> {
            Log.d(TAG, "JS注入结果: " + value);
        });
    }

    private static void triggerApiRequest(WebView view) {
        String js = "(function(){" +
                "var xhr=new XMLHttpRequest();" +
                "xhr.open('POST','http://tymj.chinatowercom.cn:8006/api/recordAccess/getPage',true);" +
                "xhr.setRequestHeader('Content-Type','application/json');" +
                "xhr.setRequestHeader('Accept','application/json');" +
                "xhr.withCredentials=true;" +
                "var body={'areaId':'','startTime':'2026-04-10 00:00:00','endTime':'2026-04-10 23:59:59'," +
                "'pageNum':1,'pageSize':10};" +
                "xhr.send(JSON.stringify(body));" +
                "return 'triggered';" +
                "})()";
        view.evaluateJavascript(js, value -> Log.d(TAG, "API触发: " + value));
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
            Log.e(TAG, "destroyWebView error: " + e.getMessage());
        }
    }
}

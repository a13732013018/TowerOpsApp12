package com.towerops.app.api;

import android.util.Base64;

import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.towerops.app.model.Session;

/**
 * 铁塔4A系统登录API封装
 * 地址: http://4a.chinatowercom.cn:20000/uac/
 *
 * 登录流程：
 * 1. getSalt     → 获取32位盐值
 * 2. getPubKey   → 获取RSA 1024bit公钥
 * 3. checkMixedLogin → 检查是否需要短信验证
 * 4. doPrevLogin → 第一步登录（账号密码加密提交），触发短信
 * 5. refreshMsg  → 发送短信验证码，返回msgId
 * 6. doNextLogin → 第二步登录（提交短信验证码），成功后得到SESSION Cookie
 */
public class TowerLoginApi {

    private static final String BASE_URL = "http://4a.chinatowercom.cn:20000/uac";
    private static final String FP = "6370ceda5e44488e79ff9404a0552ef1";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    private final OkHttpClient client;

    // 登录过程中的临时状态
    private String salt;
    private String publicKey;
    private String loginR;      // 本次登录会话的随机标识
    private String sessionCookie; // 登录成功后的完整 Cookie 字符串（所有字段拼接）

    public TowerLoginApi() {
        // 内存CookieJar：自动保存和携带Cookie，保持登录会话一致性
        CookieJar cookieJar = new CookieJar() {
            private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                String host = url.host();
                List<Cookie> existing = cookieStore.containsKey(host)
                        ? cookieStore.get(host) : new ArrayList<>();
                // 更新/追加Cookie
                for (Cookie newCookie : cookies) {
                    existing.removeIf(c -> c.name().equals(newCookie.name()));
                    existing.add(newCookie);
                }
                cookieStore.put(host, existing);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : new ArrayList<>();
            }
        };

        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(cookieJar)   // 关键：自动管理Cookie
                .build();
    }

    /** 结果包装类 */
    public static class Result {
        public final boolean success;
        public final String  message;
        public final String  data;    // 成功时的额外数据（如msgId）

        public Result(boolean success, String message, String data) {
            this.success = success;
            this.message = message;
            this.data    = data;
        }
    }

    /**
     * Step 1+2+3: 获取Salt、公钥，并检查登录类型
     */
    public Result initLogin() {
        try {
            // 1. getSalt
            String r1 = String.valueOf(Math.random());
            Request req1 = new Request.Builder()
                    .url(BASE_URL + "/getSalt?r=" + r1)
                    .addHeader("User-Agent", USER_AGENT)
                    .build();
            try (Response resp = client.newCall(req1).execute()) {
                if (!resp.isSuccessful()) return new Result(false, "获取Salt失败: " + resp.code(), null);
                salt = resp.body().string().replace("\"", "").trim();
            }

            // 2. getPubKey
            String r2 = String.valueOf(Math.random());
            Request req2 = new Request.Builder()
                    .url(BASE_URL + "/getPubKey?r=" + r2)
                    .addHeader("User-Agent", USER_AGENT)
                    .build();
            try (Response resp = client.newCall(req2).execute()) {
                if (!resp.isSuccessful()) return new Result(false, "获取公钥失败: " + resp.code(), null);
                publicKey = resp.body().string().replace("\"", "").trim();
                // 去掉公钥中可能存在的换行和空格，确保Base64解析正常
                publicKey = publicKey.replaceAll("[\\r\\n\\s]", "");
            }

            // 生成本次登录的随机r（32位hex）
            loginR = UUID.randomUUID().toString().replace("-", "");

            // 3. checkMixedLogin
            Request req3 = new Request.Builder()
                    .url(BASE_URL + "/checkMixedLogin?r=" + loginR)
                    .addHeader("User-Agent", USER_AGENT)
                    .build();
            try (Response resp = client.newCall(req3).execute()) {
                String body = resp.body().string();
                // no_checkmixed = 正常，可继续登录
            }

            return new Result(true, "初始化成功", null);

        } catch (Exception e) {
            return new Result(false, "网络异常: " + e.getMessage(), null);
        }
    }

    /**
     * Step 4: doPrevLogin - 提交账号密码，触发短信验证码
     */
    public Result doPrevLogin(String username, String password) {
        try {
            String encUsername  = rsaEncrypt(username);
            String encPassword  = rsaEncrypt(password);
            String encLoginCode = rsaEncrypt(salt);

            FormBody body = new FormBody.Builder()
                    .add("loginCode",  encLoginCode)
                    .add("csrftoken",  "")
                    .add("username",   encUsername)
                    .add("password",   encPassword)
                    .add("loginFrom",  "oth")
                    .add("fp",         FP)
                    .add("useragent",  USER_AGENT)
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/doPrevLogin?r=" + loginR)
                    .post(body)
                    .addHeader("User-Agent",   USER_AGENT)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer",      "http://4a.chinatowercom.cn:20000/uac/")
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                String responseBody = resp.body().string();
                JSONObject json = new JSONObject(responseBody);
                String message = json.optString("message", "");

                if ("NEXT".equals(message)) {
                    return new Result(true, "密码验证通过，正在发送短信...", null);
                } else if ("OK".equals(message)) {
                    // 直接登录成功（无需短信），走完整 SSO 流程
                    try {
                        visitOmmsViaSso(username);
                    } catch (Exception e) {
                        android.util.Log.w("TowerLoginApi", "visitOmmsViaSso(direct) failed: " + e.getMessage());
                        extractAndSaveCookie(resp);
                    }
                    return new Result(true, "登录成功", "direct");
                } else {
                    return new Result(false, "账号或密码错误", null);
                }
            }
        } catch (Exception e) {
            return new Result(false, "登录异常: " + e.getMessage(), null);
        }
    }

    /**
     * Step 5: refreshMsg - 发送短信验证码
     */
    public Result refreshMsg(String username, String password) {
        try {
            String encUsername = rsaEncrypt(username);
            String encPassword = rsaEncrypt(password);

            FormBody body = new FormBody.Builder()
                    .add("csrftoken", "")
                    .add("username",  encUsername)
                    .add("password",  encPassword)
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/refreshMsg?r=" + loginR)
                    .post(body)
                    .addHeader("User-Agent",   USER_AGENT)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer",      "http://4a.chinatowercom.cn:20000/uac/")
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                String responseBody = resp.body().string();
                JSONObject json = new JSONObject(responseBody);
                String message  = json.optString("message", "");
                String msgId    = json.optString("msgId", "");
                String phoneMsg = json.optString("phoneMsg", "");

                if ("NEXT".equals(message) && !msgId.isEmpty()) {
                    return new Result(true, phoneMsg.isEmpty() ? "短信已发送" : phoneMsg, msgId);
                } else {
                    return new Result(false, "短信发送失败: " + responseBody, null);
                }
            }
        } catch (Exception e) {
            return new Result(false, "发送短信异常: " + e.getMessage(), null);
        }
    }

    /**
     * Step 6: doNextLogin - 提交短信验证码，完成登录
     */
    public Result doNextLogin(String username, String password, String msgId, String msgCode) {
        try {
            String encUsername = rsaEncrypt(username);
            String encPassword = rsaEncrypt(password);
            String r = String.valueOf(Math.random());

            FormBody body = new FormBody.Builder()
                    .add("csrftoken",  "")
                    .add("msgCode",    msgCode)
                    .add("msgId",      msgId)
                    .add("loginFrom",  "oth")
                    .add("username",   encUsername)
                    .add("password",   encPassword)
                    .add("fp",         FP)
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/doNextLogin?r=" + r)
                    .post(body)
                    .addHeader("User-Agent",   USER_AGENT)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer",      "http://4a.chinatowercom.cn:20000/uac/")
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                String responseBody = resp.body().string();
                JSONObject json = new JSONObject(responseBody);
                String message = json.optString("message", "");

                if ("OK".equals(message)) {
                    // SSO 完整流程（与浏览器 js gores/tores/openPostWindow 完全一致）
                    // 步骤一：POST /uac/home/soaprequest，获取 OMMS 跳转 URL
                    try {
                        visitOmmsViaSso(username);
                    } catch (Exception e) {
                        android.util.Log.w("TowerLoginApi", "visitOmmsViaSso failed: " + e.getMessage());
                        extractAndSaveCookie(resp);
                    }
                    android.util.Log.d("TowerLoginApi", "doNextLogin cookie: hasPwdaToken="
                            + (sessionCookie != null && sessionCookie.contains("pwdaToken"))
                            + " len=" + (sessionCookie != null ? sessionCookie.length() : 0));
                    return new Result(true, "登录成功", sessionCookie);
                } else {
                    return new Result(false, "验证码错误或已过期", null);
                }
            }
        } catch (Exception e) {
            return new Result(false, "提交验证码异常: " + e.getMessage(), null);
        }
    }

    /**
     * 完整 SSO 流程：4A 登录成功后，通过 soaprequest 接口获取 OMMS 跳转 URL 并完成 SSO。
     *
     * 对应浏览器 JS 中的 gores('200007','100033','') → tores(r,s) → openPostWindow/window.open
     * 【注意】运维监控 appId=200007，不是统一门禁管理的 360650
     *
     * 流程：
     *   1. POST /uac/home/soaprequest?r=200007&s=100033
     *      返回 JSON: {status:"success", url:"http://omms.../layout/...?token=xxx&...", ssoType:"3"}
     *   2a. ssoType=="3": 把 URL 的 query 参数拆出来 + appAcctId=loginName，POST 到目标地址
     *   2b. 否则: GET url（去掉 "redirect:" 前缀）
     *   3. 上面两步完成后 OkHttp CookieJar 里已有完整 OMMS Cookie，调用 extractAndSaveCookie
     *
     * @param loginName 当前登录账号（appAcctId 字段，如 "wx-linjy22"）
     */
    private void visitOmmsViaSso(String loginName) throws IOException {
        // 步骤一：POST soaprequest（运维监控 appId=200007）
        FormBody soapBody = new FormBody.Builder()
                .add("r", "200007")
                .add("s", "100033")
                .add("ssoPwd", "")
                .add("superUserCode", "")
                .add("superRandom", "")
                .add("aliJar", "")
                .build();
        Request soapReq = new Request.Builder()
                .url("http://4a.chinatowercom.cn:20000/uac/home/soaprequest")
                .post(soapBody)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Referer", "http://4a.chinatowercom.cn:20000/uac/home/index")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .build();

        String soapRespStr;
        try (Response soapResp = client.newCall(soapReq).execute()) {
            soapRespStr = soapResp.body().string();
            android.util.Log.d("TowerLoginApi", "soaprequest resp=" + soapRespStr.substring(0, Math.min(300, soapRespStr.length())));
        }

        // 步骤二：解析 soaprequest 响应，执行 SSO 跳转
        try {
            JSONObject soapJson = new JSONObject(soapRespStr);
            String status  = soapJson.optString("status", "");
            String ssoUrl  = soapJson.optString("url", "");
            String ssoType = soapJson.optString("ssoType", "");

            if (!"success".equals(status) || ssoUrl.isEmpty()) {
                android.util.Log.w("TowerLoginApi", "soaprequest status=" + status + " msg=" + soapJson.optString("msg", ""));
                // 降级：直接 GET From4A.jsp
                fallbackGetOmms(loginName);
                return;
            }

            android.util.Log.d("TowerLoginApi", "ssoType=" + ssoType + " ssoUrl=" + ssoUrl.substring(0, Math.min(200, ssoUrl.length())));

            if ("3".equals(ssoType)) {
                // ssoType==3: POST 目标 URL，参数从 query string 解析，额外加 appAcctId
                int qIdx = ssoUrl.indexOf('?');
                String postUrl = qIdx > 0 ? ssoUrl.substring(0, qIdx) : ssoUrl;
                FormBody.Builder fb = new FormBody.Builder();
                // 加 appAcctId（openPostWindow 里写死的）
                fb.add("appAcctId", loginName);
                // 解析 query 参数
                if (qIdx > 0) {
                    String query = ssoUrl.substring(qIdx + 1);
                    for (String pair : query.split("&")) {
                        int eqIdx = pair.indexOf('=');
                        if (eqIdx > 0) {
                            String k = java.net.URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8");
                            String v = java.net.URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8");
                            fb.add(k, v);
                        }
                    }
                }
                Request ssoReq = new Request.Builder()
                        .url(postUrl)
                        .post(fb.build())
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "http://4a.chinatowercom.cn:20000/uac/home/index")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                        .build();
                try (Response ssoResp = client.newCall(ssoReq).execute()) {
                    android.util.Log.d("TowerLoginApi", "ssoType3 POST code=" + ssoResp.code()
                            + " finalUrl=" + ssoResp.request().url());
                }
            } else {
                // 直接 GET（去掉 "redirect:" 前缀）
                String openUrl = ssoUrl.replace("redirect:", "");
                Request getReq = new Request.Builder()
                        .url(openUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "http://4a.chinatowercom.cn:20000/uac/home/index")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build();
                try (Response getResp = client.newCall(getReq).execute()) {
                    android.util.Log.d("TowerLoginApi", "ssoGet code=" + getResp.code()
                            + " finalUrl=" + getResp.request().url());
                }
            }

            // ★★★ 关键步骤：访问 From4A.jsp 完成 OMMS 登录（建立 nodeInformation、acctId、uid 等 Cookie）
            // 浏览器真实流程：4A登录成功 → soaprequest → From4A.jsp?loginName=xxx&moduleurl=/layout/index.xhtml
            //                → 重定向到 listFsu.xhtml（此时 nodeInformation、acctId、uid、pwdaToken 全部写入）
            // 如果跳过此步，OMMS 的 JSESSIONID 会是未授权状态，导致 listFsu.xhtml 拒绝访问，
            // refreshViewState() 得到的是登录重定向页（不含 ViewState）
            try {
                String from4aUrl = "http://omms.chinatowercom.cn:9000/From4A.jsp"
                        + "?loginName=" + loginName
                        + "&moduleurl=/layout/index.xhtml";
                Request from4aReq = new Request.Builder()
                        .url(from4aUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                        .addHeader("Referer", "http://4a.chinatowercom.cn:20000/uac/home/index")
                        .build();
                try (Response from4aResp = client.newCall(from4aReq).execute()) {
                    String finalUrl = from4aResp.request().url().toString();
                    android.util.Log.d("TowerLoginApi", "From4A.jsp code=" + from4aResp.code()
                            + " finalUrl=" + finalUrl);
                    // 验证：最终 URL 应该是 listFsu.xhtml 或 layout/index.xhtml，而不是登录页
                    boolean redirectedToLogin = finalUrl.contains("uac/login") || finalUrl.contains("doPrevLogin");
                    if (redirectedToLogin) {
                        android.util.Log.w("TowerLoginApi", "From4A.jsp 被重定向到登录页！SSO Token可能无效");
                    } else {
                        android.util.Log.d("TowerLoginApi", "From4A.jsp SSO 完成，OMMS Cookie 已建立");
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("TowerLoginApi", "From4A.jsp 访问失败: " + e.getMessage());
            }
        } catch (Exception e) {
            android.util.Log.w("TowerLoginApi", "visitOmmsViaSso parse/jump failed: " + e.getMessage());
            fallbackGetOmms(loginName);
            return;
        }

        // 步骤三：SSO 完成后提取完整 Cookie
        extractAndSaveCookie(null);
    }

    /**
     * SSO 失败时的降级方案：直接 GET From4A.jsp（带 loginName 参数）
     * ★ 不能直接 GET index.xhtml，因为未建立 OMMS 会话时会被302到登录页
     * ★ From4A.jsp 是 OMMS SSO 的唯一合法入口，访问后服务端会写入 nodeInformation/acctId/uid 等 Cookie
     *
     * @param loginName 4A 登录账号（如 "wx-linjy22"）
     */
    private void fallbackGetOmms(String loginName) {
        try {
            String from4aUrl = "http://omms.chinatowercom.cn:9000/From4A.jsp"
                    + "?loginName=" + loginName
                    + "&moduleurl=/layout/index.xhtml";
            Request ommsReq = new Request.Builder()
                    .url(from4aUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                    .addHeader("Referer", "http://4a.chinatowercom.cn:20000/uac/")
                    .build();
            try (Response r = client.newCall(ommsReq).execute()) {
                android.util.Log.d("TowerLoginApi", "fallbackGetOmms(From4A.jsp) code=" + r.code()
                        + " finalUrl=" + r.request().url());
            }
        } catch (Exception e) {
            android.util.Log.w("TowerLoginApi", "fallbackGetOmms failed: " + e.getMessage());
        }
        extractAndSaveCookie(null);
    }

    /**
     * 从 CookieJar 提取完整 Cookie 字符串，合并所有域的 Cookie。
     *
     * 易语言关键行为：OkHttpClient 使用自定义 CookieJar，对所有域共享 Cookie，
     * 因此发给 OMMS 的请求同时携带 4A 域的 SESSION、OMMS 域的 JSESSIONID/pwdaToken 等。
     * 这与标准浏览器行为不同——浏览器会严格按域隔离 Cookie。
     *
     * 本方法复现易语言行为：合并 4A + OMMS 所有域的 Cookie，全部拼入 tower4aSessionCookie。
     * 其中 OMMS 域 Cookie 优先（防止 SESSION 被 4A 域 SESSION 覆盖，实际上字段名不冲突）。
     *
     * Cookie 优先级（后写覆盖前写）：
     *   4A 域 (4a.chinatowercom.cn) → OMMS 域 (omms.chinatowercom.cn)
     *
     * 调用时机：访问 OMMS 首页之后（CookieJar 已累积两个域的所有 Cookie）。
     */
    private void extractAndSaveCookie(Response ignoredResponse) {
        // 使用 Map 去重：OMMS 域的 Cookie 覆盖 4A 域同名 Cookie
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();

        // 先放 4A 域
        HttpUrl tower4aUrl = HttpUrl.parse("http://4a.chinatowercom.cn:20000/uac/");
        if (tower4aUrl != null) {
            List<Cookie> cookies4a = client.cookieJar().loadForRequest(tower4aUrl);
            for (Cookie c : cookies4a) {
                merged.put(c.name(), c.value());
            }
            android.util.Log.d("TowerLoginApi", "extractCookie: 4A domain cookies count=" + cookies4a.size());
        }

        // 再放 OMMS 域（覆盖同名字段）
        HttpUrl ommsUrl = HttpUrl.parse("http://omms.chinatowercom.cn:9000/layout/index.xhtml");
        if (ommsUrl != null) {
            List<Cookie> ommsCookies = client.cookieJar().loadForRequest(ommsUrl);
            for (Cookie c : ommsCookies) {
                merged.put(c.name(), c.value());
            }
            android.util.Log.d("TowerLoginApi", "extractCookie: OMMS domain cookies count=" + ommsCookies.size());
        }

        if (!merged.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : merged.entrySet()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
            sessionCookie = sb.toString();
            android.util.Log.d("TowerLoginApi", "extractCookie(merged) totalFields=" + merged.size()
                    + " hasPwdaToken=" + merged.containsKey("pwdaToken")
                    + " hasJSESSIONID=" + merged.containsKey("JSESSIONID")
                    + " hasSESSION=" + merged.containsKey("SESSION")
                    + " len=" + sessionCookie.length()
                    + " preview=" + sessionCookie.substring(0, Math.min(150, sessionCookie.length())));
        } else {
            android.util.Log.w("TowerLoginApi", "extractAndSaveCookie: CookieJar is empty for both domains");
        }
    }

    /**
     * 登录成功后的完整 Cookie 字符串（可直接填入 HTTP Cookie 头）
     * 包含 SESSION、pwdaToken、JSESSIONID、acctId、uid 等 OMMS 所需全部字段
     */
    public String getSessionCookie() {
        return sessionCookie;
    }

    // =====================================================================
    // 静态工具：用已有的4A Session Cookie 自动获取 OMMS Cookie（无感知）
    // =====================================================================

    /**
     * 用已有的4A SESSION Cookie 自动完成 OMMS 登录，获取 OMMS 专属 Cookie。
     *
     * 原理（对应浏览器的「点击运维监控」行为）：
     *   1. 用4A的 SESSION Cookie POST soaprequest（r=200007, s=100033）
     *      → 返回 OMMS 的 SSO 跳转 URL 和 ssoType
     *   2. 根据 ssoType 访问 SSO URL（POST or GET）
     *   3. 访问 From4A.jsp?loginName=xxx → 完成 OMMS 会话建立
     *      → OkHttp CookieJar 里此时有 OMMS 的 JSESSIONID/pwdaToken/nodeInformation 等
     *   4. 提取并写入 Session.ommsCookie，持久化
     *
     * 典型使用场景：门禁 Tab 发现 ommsCookie 为空时，后台静默调用此方法，
     *   用户无需任何操作，自动完成 OMMS 登录。
     *
     * @param tower4aSessionCookie 铁塔4A系统的完整 SESSION Cookie 字符串
     *        （格式如 "SESSION=xxx; route=xxx; Tnuocca=xxx"）
     * @param loginName           登录账号（如 "wx-linjy22"），用于 From4A.jsp loginName 参数
     * @param ctx                 Android Context，用于持久化 ommsCookie 到 SharedPreferences
     * @return Result 对象：success=true 表示成功且 Session.ommsCookie 已更新
     */
    public static Result autoGetOmmsCookie(String tower4aSessionCookie,
                                           String loginName,
                                           android.content.Context ctx) {
        try {
            if (tower4aSessionCookie == null || tower4aSessionCookie.isEmpty()) {
                return new Result(false,
                        "未找到4A登录凭证，请先在「工单监控」Tab完成4A账号登录", null);
            }
            // ★ 检查 tower4aSessionCookie 里是否真的含有4A的SESSION字段
            // （如果只有 OMMS 字段说明4A根本没登录成功）
            boolean has4aSession = tower4aSessionCookie.contains("SESSION=")
                    || tower4aSessionCookie.contains("Tnuocca=");
            if (!has4aSession) {
                android.util.Log.w("TowerLoginApi",
                        "autoGetOmmsCookie: tower4aSessionCookie 不含SESSION/Tnuocca，4A未登录");
                return new Result(false,
                        "4A Session已过期，请重新在「工单监控」Tab登录4A账号", null);
            }

            android.util.Log.e("TowerLoginApi", "═══════════════════════════════════════");
            android.util.Log.e("TowerLoginApi", "【OMMS认证调试】autoGetOmmsCookie 开始");
            android.util.Log.e("TowerLoginApi", "cookieLen=" + tower4aSessionCookie.length());
            android.util.Log.e("TowerLoginApi", "loginName=" + loginName);
            android.util.Log.e("TowerLoginApi", "has4aSession=" + has4aSession);
            android.util.Log.e("TowerLoginApi", "cookie预览=" + tower4aSessionCookie.substring(0, Math.min(150, tower4aSessionCookie.length())));
            android.util.Log.e("TowerLoginApi", "═══════════════════════════════════════");

            // ─── 构建专用 OkHttpClient（跨域共享 CookieJar，模拟浏览器行为）────
            final java.util.Map<String, String> globalCookies = new java.util.LinkedHashMap<>();

            // ★ 只预填4A域专属的 Cookie，过滤掉 OMMS 字段（JSESSIONID/pwdaToken/nodeInformation等）
            // 原因：tower4aSessionCookie 是由 extractAndSaveCookie 合并4A+OMMS域生成的，
            //       里面可能含有旧的 OMMS Cookie，带着旧 OMMS Cookie 去做 SSO 会被服务端当作已登录，
            //       导致不触发新的 SSO 流程，pwdaToken 拿不到新值
            // 4A域专属字段白名单：SESSION、Tnuocca、route、ULTRA_U_K
            java.util.Set<String> tower4aCookieNames = new java.util.HashSet<>(java.util.Arrays.asList(
                    "SESSION", "Tnuocca", "route", "ULTRA_U_K", "JSESSIONID_4A"
            ));
            // OMMS域字段黑名单（过滤掉这些）
            java.util.Set<String> ommsCookieBlacklist = new java.util.HashSet<>(java.util.Arrays.asList(
                    "JSESSIONID", "pwdaToken", "nodeInformation", "acctId", "uid",
                    "loginName", "fp", "userOrgCode", "lang", "moduleUrl"
            ));
            for (String pair : tower4aSessionCookie.split(";")) {
                pair = pair.trim();
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = pair.substring(0, eq).trim();
                    String v = pair.substring(eq + 1).trim();
                    // 跳过 OMMS 专属字段，只保留4A域的认证字段
                    if (!ommsCookieBlacklist.contains(k)) {
                        globalCookies.put(k, v);
                    }
                }
            }
            android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie: preloaded "
                    + globalCookies.size() + " 4A-only cookies, keys=" + globalCookies.keySet());

            // 全局共享 CookieJar：所有域的响应 Cookie 都合并到同一个 Map 里，
            // 对任何域发请求时都携带 Map 里的全部 Cookie
            CookieJar globalJar = new CookieJar() {
                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    for (Cookie c : cookies) {
                        globalCookies.put(c.name(), c.value());
                    }
                    android.util.Log.d("TowerLoginApi", "CookieJar.save from " + url.host()
                            + " count=" + cookies.size()
                            + " total=" + globalCookies.size());
                }
                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    // 把 globalCookies 全部转成对应 Host 的 Cookie 对象
                    List<Cookie> list = new ArrayList<>();
                    for (Map.Entry<String, String> e : globalCookies.entrySet()) {
                        try {
                            Cookie c = new Cookie.Builder()
                                    .name(e.getKey()).value(e.getValue())
                                    .domain(url.host()).path("/").build();
                            list.add(c);
                        } catch (Exception ignored) {}
                    }
                    return list;
                }
            };

            // ★ 关闭自动跟随重定向，改为手动跟随——这样能捕获每一跳的 Set-Cookie
            // 原因：OkHttp 自动跟随时，中间跳的 Set-Cookie 有时因 domain 问题存不进 CookieJar
            //       手动跟随可以强制把每一跳的 Set-Cookie 都写进 globalCookies
            OkHttpClient httpClientNoRedir = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .followRedirects(false)       // ★ 不自动跟随
                    .followSslRedirects(false)
                    .cookieJar(globalJar)
                    .build();

            // 手动跟随重定向的工具方法（内联）
            // 从每一跳响应头里手动提取 Set-Cookie 存入 globalCookies
            String currentUrl = "http://omms.chinatowercom.cn:9000/layout/index.xhtml";
            String referer    = "http://4a.chinatowercom.cn:20000/uac/home/index";
            String finalOmmsUrl = currentUrl;
            String ommsBodyStr  = "";
            int redirectCount = 0;
            while (redirectCount < 15) {
                android.util.Log.e("TowerLoginApi", "【OMMS认证调试】跳转[" + redirectCount + "] 请求: " + currentUrl);
                Request req = new Request.Builder()
                        .url(currentUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                        .addHeader("Referer", referer)
                        .build();
                try (Response resp = httpClientNoRedir.newCall(req).execute()) {
                    int code = resp.code();
                    android.util.Log.e("TowerLoginApi", "【OMMS认证调试】跳转[" + redirectCount + "] 响应码: " + code);
                    // 手动提取每一跳的 Set-Cookie
                    for (String sc : resp.headers("Set-Cookie")) {
                        // Set-Cookie: name=value; Path=/; ...
                        String nameVal = sc.split(";")[0].trim();
                        int eq = nameVal.indexOf('=');
                        if (eq > 0) {
                            String cName  = nameVal.substring(0, eq).trim();
                            String cValue = nameVal.substring(eq + 1).trim();
                            globalCookies.put(cName, cValue);
                            android.util.Log.e("TowerLoginApi",
                                    "【OMMS认证调试】跳转[" + redirectCount + "] Set-Cookie: "
                                    + cName + "=" + cValue.substring(0, Math.min(50, cValue.length())));
                        }
                    }
                    android.util.Log.d("TowerLoginApi",
                            "autoGetOmmsCookie redirect[" + redirectCount + "] code=" + code
                            + " url=" + currentUrl
                            + " cookies_total=" + globalCookies.size()
                            + " pwdaToken=" + globalCookies.containsKey("pwdaToken"));
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String location = resp.header("Location");
                        if (location == null || location.isEmpty()) break;
                        // 相对路径转绝对
                        if (location.startsWith("/")) {
                            java.net.URL base = new java.net.URL(currentUrl);
                            location = base.getProtocol() + "://" + base.getHost()
                                    + (base.getPort() > 0 && base.getPort() != 80 ? ":" + base.getPort() : "")
                                    + location;
                        } else if (!location.startsWith("http")) {
                            // 相对路径（不含/）
                            java.net.URL base = new java.net.URL(currentUrl);
                            String basePath = base.getPath();
                            int lastSlash = basePath.lastIndexOf('/');
                            location = base.getProtocol() + "://" + base.getHost()
                                    + (base.getPort() > 0 && base.getPort() != 80 ? ":" + base.getPort() : "")
                                    + basePath.substring(0, lastSlash + 1) + location;
                        }
                        referer    = currentUrl;
                        currentUrl = location;
                        redirectCount++;
                    } else {
                        // 非重定向，读 body
                        finalOmmsUrl = currentUrl;
                        ommsBodyStr  = resp.body() != null ? resp.body().string() : "";
                        break;
                    }
                }
            }
            android.util.Log.e("TowerLoginApi",
                    "═══════════════════════════════════════");
            android.util.Log.e("TowerLoginApi", "【OMMS认证调试】autoGetOmmsCookie SSO完成");
            android.util.Log.e("TowerLoginApi", "finalUrl=" + finalOmmsUrl);
            android.util.Log.e("TowerLoginApi", "redirectCount=" + redirectCount);
            android.util.Log.e("TowerLoginApi", "cookies=" + globalCookies.size());
            android.util.Log.e("TowerLoginApi", "pwdaToken=" + globalCookies.containsKey("pwdaToken"));
            android.util.Log.e("TowerLoginApi", "JSESSIONID=" + globalCookies.containsKey("JSESSIONID"));
            android.util.Log.e("TowerLoginApi", "ommsBodyStr长度=" + ommsBodyStr.length());
            if (!ommsBodyStr.isEmpty()) {
                android.util.Log.e("TowerLoginApi", "ommsBodyStr预览=" + ommsBodyStr.substring(0, Math.min(300, ommsBodyStr.length())));
            }
            android.util.Log.e("TowerLoginApi",
                    "═══════════════════════════════════════");

            // 判断是否成功进入 OMMS（未被踢回登录页或登出页）
            boolean redirectedToLogin = finalOmmsUrl.contains("uac/login")
                    || finalOmmsUrl.contains("doPrevLogin")
                    || finalOmmsUrl.contains("login.xhtml");
            boolean redirectedToLogout = finalOmmsUrl.endsWith("logout.xhtml");
            
            if (redirectedToLogin || redirectedToLogout) {
                android.util.Log.e("TowerLoginApi",
                        "【OMMS认证调试】⚠️ 被踢回登录页/登出页，尝试清空Cookie后重新SSO...");
                
                // ★★★ 关键修复：当检测到logout时，清空所有Cookie，重新发起完整的SSO流程 ★★★
                globalCookies.clear();
                android.util.Log.e("TowerLoginApi", "已清空Cookie，重新开始SSO流程");
                
                // 重新从OMMS入口发起请求，看看能否触发新的SSO
                String ssoStartUrl = "http://omms.chinatowercom.cn:9000/layout/index.xhtml";
                String ssoReferer  = "http://4a.chinatowercom.cn:20000/uac/home/index";
                String ssoFinalUrl = ssoStartUrl;
                int ssoRetryCount = 0;
                
                while (ssoRetryCount < 15) {
                    android.util.Log.e("TowerLoginApi", "【SSO重试】跳转[" + ssoRetryCount + "] 请求: " + ssoStartUrl);
                    Request ssoReq = new Request.Builder()
                            .url(ssoStartUrl)
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                            .addHeader("Referer", ssoReferer)
                            .build();
                    try (Response ssoResp = httpClientNoRedir.newCall(ssoReq).execute()) {
                        int ssoCode = ssoResp.code();
                        android.util.Log.e("TowerLoginApi", "【SSO重试】跳转[" + ssoRetryCount + "] 响应码: " + ssoCode);
                        
                        // 收集Cookie
                        for (String sc : ssoResp.headers("Set-Cookie")) {
                            String nameVal = sc.split(";")[0].trim();
                            int eq = nameVal.indexOf('=');
                            if (eq > 0) {
                                globalCookies.put(nameVal.substring(0, eq).trim(),
                                        nameVal.substring(eq + 1).trim());
                                android.util.Log.e("TowerLoginApi", "【SSO重试】Set-Cookie: " + nameVal);
                            }
                        }
                        
                        if (ssoCode == 301 || ssoCode == 302 || ssoCode == 303 || ssoCode == 307 || ssoCode == 308) {
                            String location = ssoResp.header("Location");
                            if (location == null || location.isEmpty()) break;
                            if (location.startsWith("/")) {
                                java.net.URL base = new java.net.URL(ssoStartUrl);
                                location = base.getProtocol() + "://" + base.getHost()
                                        + (base.getPort() > 0 && base.getPort() != 80 ? ":" + base.getPort() : "")
                                        + location;
                            }
                            ssoReferer  = ssoStartUrl;
                            ssoStartUrl = location;
                            ssoResp.close(); // 关闭旧响应才能发新请求
                            ssoRetryCount++;
                        } else {
                            ssoFinalUrl = ssoStartUrl;
                            android.util.Log.e("TowerLoginApi", "【SSO重试】最终URL: " + ssoFinalUrl);
                            android.util.Log.e("TowerLoginApi", "【SSO重试】pwdaToken: " + globalCookies.containsKey("pwdaToken"));
                            android.util.Log.e("TowerLoginApi", "【SSO重试】JSESSIONID: " + globalCookies.containsKey("JSESSIONID"));
                            break;
                        }
                    }
                }
                
                // 检查重试后的结果
                boolean retrySuccess = ssoFinalUrl.contains("layout/index") 
                        && (globalCookies.containsKey("JSESSIONID") || globalCookies.containsKey("pwdaToken"));
                
                if (!retrySuccess) {
                    android.util.Log.e("TowerLoginApi", "【OMMS认证调试】⚠️ 4A Session已过期，自动刷新失败");
                    android.util.Log.e("TowerLoginApi", "   原因：4A Cookie已失效，OMMS无法建立会话");
                    android.util.Log.e("TowerLoginApi", "   ");
                    android.util.Log.e("TowerLoginApi", "   ★ 重要提示 ★");
                    android.util.Log.e("TowerLoginApi", "   安全打卡功能需要同时完成两个登录：");
                    android.util.Log.e("TowerLoginApi", "   1. 【工单监控】Tab → 4A账号登录");
                    android.util.Log.e("TowerLoginApi", "   2. 【门禁系统】Tab → 点击「OMMS登录」→ 点击「运维监控」");
                    android.util.Log.e("TowerLoginApi", "   ");
                    android.util.Log.e("TowerLoginApi", "   请按以下步骤操作：");
                    android.util.Log.e("TowerLoginApi", "   步骤1：进入【门禁系统】Tab");
                    android.util.Log.e("TowerLoginApi", "   步骤2：点击「OMMS登录」按钮");
                    android.util.Log.e("TowerLoginApi", "   步骤3：WebView打开后，输入4A账号密码登录");
                    android.util.Log.e("TowerLoginApi", "   步骤4：点击「运维监控」菜单");
                    android.util.Log.e("TowerLoginApi", "   步骤5：提示成功后，回到【运维日常】→【安全打卡】查询");
                    
                    return new Result(false,
                            "安全打卡需要完成OMMS授权，请先进入【门禁系统】Tab，点击「OMMS登录」完成授权", null);
                }
                
                // 重试成功，更新最终URL
                finalOmmsUrl = ssoFinalUrl;
                android.util.Log.e("TowerLoginApi", "【OMMS认证调试】✅ SSO重试成功!");
            }

            // ─── Step 2：若 pwdaToken 还没拿到，再单独访问 From4A.jsp ──────────
            // layout/index.xhtml SSO 链路不一定每次都返回 pwdaToken
            // From4A.jsp 是专门绑定 OMMS 用户会话的入口，确保 pwdaToken / nodeInformation 写入
            if (!globalCookies.containsKey("pwdaToken")) {
                android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie: pwdaToken not found, trying From4A.jsp");
                String from4aUrl = "http://omms.chinatowercom.cn:9000/From4A.jsp"
                        + "?loginName=" + java.net.URLEncoder.encode(loginName, "UTF-8")
                        + "&moduleurl=/layout/index.xhtml";
                // From4A.jsp 也可能有302，同样手动跟随
                String f4aUrl = from4aUrl;
                String f4aReferer = finalOmmsUrl.isEmpty() ? "http://4a.chinatowercom.cn:20000/uac/home/index" : finalOmmsUrl;
                for (int i = 0; i < 10; i++) {
                    Request f4aReq = new Request.Builder()
                            .url(f4aUrl)
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                            .addHeader("Referer", f4aReferer)
                            .build();
                    try (Response f4aResp = httpClientNoRedir.newCall(f4aReq).execute()) {
                        for (String sc : f4aResp.headers("Set-Cookie")) {
                            String nameVal = sc.split(";")[0].trim();
                            int eq = nameVal.indexOf('=');
                            if (eq > 0) {
                                globalCookies.put(nameVal.substring(0, eq).trim(),
                                        nameVal.substring(eq + 1).trim());
                            }
                        }
                        android.util.Log.d("TowerLoginApi",
                                "From4A.jsp hop[" + i + "] code=" + f4aResp.code()
                                + " url=" + f4aUrl
                                + " pwdaToken=" + globalCookies.containsKey("pwdaToken"));
                        String loc = f4aResp.header("Location");
                        if ((f4aResp.code() == 302 || f4aResp.code() == 301) && loc != null && !loc.isEmpty()) {
                            if (loc.startsWith("/")) {
                                java.net.URL base = new java.net.URL(f4aUrl);
                                loc = base.getProtocol() + "://" + base.getHost()
                                        + (base.getPort() > 0 && base.getPort() != 80 ? ":" + base.getPort() : "")
                                        + loc;
                            }
                            f4aReferer = f4aUrl;
                            f4aUrl = loc;
                        } else {
                            break;
                        }
                    }
                }
                android.util.Log.d("TowerLoginApi",
                        "autoGetOmmsCookie after From4A.jsp: pwdaToken="
                        + globalCookies.containsKey("pwdaToken")
                        + " total=" + globalCookies.size());
            }

            // ─── Step 3：检查是否拿到了 OMMS 专属 Cookie ──────────────────────
            boolean hasPwdaToken  = globalCookies.containsKey("pwdaToken");
            boolean hasJsessionId = globalCookies.containsKey("JSESSIONID");
            android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie after steps:"
                    + " total=" + globalCookies.size()
                    + " pwdaToken=" + hasPwdaToken
                    + " JSESSIONID=" + hasJsessionId
                    + " nodeInformation=" + globalCookies.containsKey("nodeInformation"));

            if (!hasPwdaToken && !hasJsessionId) {
                // OMMS SSO 未完成，globalCookies 只有4A的字段
                return new Result(false,
                        "OMMS自动登录失败：未获取到 JSESSIONID/pwdaToken"
                        + "（4A账号可能无OMMS权限，或网络不可达）", null);
            }

            // ─── Step 4：把 globalCookies 里 OMMS 专属的 Cookie 写入 Session ──
            // ★ 只保留 OMMS 域的 Cookie，不包含4A的字段（route/SESSION/Tnuocca/JSESSIONID是4A的）
            // 4A Cookie 混入后，OMMS 服务端收到不认识的字段，会导致 ViewState 解析失败
            // OMMS 专属字段白名单：JSESSIONID（OMMS的）、pwdaToken、nodeInformation、acctId、uid、loginName、fp、userOrgCode、lang
            java.util.Set<String> ommsCookieNames = new java.util.HashSet<>(java.util.Arrays.asList(
                    "JSESSIONID", "pwdaToken", "nodeInformation", "acctId", "uid",
                    "loginName", "fp", "userOrgCode", "lang", "route"
            ));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : globalCookies.entrySet()) {
                // 只写入 OMMS 域专属 Cookie（跳过4A的 SESSION、Tnuocca 等）
                if (ommsCookieNames.contains(e.getKey())
                        || e.getKey().startsWith("JSESSIONID")
                        || e.getKey().equals("pwdaToken")
                        || e.getKey().equals("nodeInformation")) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(e.getKey()).append("=").append(e.getValue());
                }
            }
            // 如果过滤后为空（极端情况），降级用全量
            if (sb.length() == 0) {
                for (Map.Entry<String, String> e : globalCookies.entrySet()) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(e.getKey()).append("=").append(e.getValue());
                }
            }
            String ommsCookieStr = sb.toString();
            android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie Step4 filtered ommsCookie: "
                    + ommsCookieStr.substring(0, Math.min(150, ommsCookieStr.length())));

            // ★★★ 关键：只有自动获取的 ommsCookie 含 pwdaToken 才写入 Session
            // 否则保留用户手动粘贴的 ommsCookie（含 pwdaToken 的完整版本）
            // 原因：pwdaToken 是浏览器端 JS 写入的，纯 HTTP SSO 流程拿不到，
            //       自动获取的 ommsCookie 只有 JSESSIONID，不能覆盖用户手动粘贴的完整 Cookie
            String existingOmmsCookie = Session.get().ommsCookie;
            boolean existingHasPwda = existingOmmsCookie != null && existingOmmsCookie.contains("pwdaToken");
            boolean newHasPwda = ommsCookieStr.contains("pwdaToken");
            if (newHasPwda || !existingHasPwda) {
                // 自动获取的有 pwdaToken，或者原来也没有 → 写入
                Session.get().ommsCookie = ommsCookieStr;
                if (ctx != null) Session.get().saveOmmsCookie(ctx);
                android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie: wrote ommsCookie len=" + ommsCookieStr.length()
                        + " newHasPwda=" + newHasPwda);
            } else {
                // 原来有 pwdaToken，自动获取的没有 → 保留原来的，不覆盖
                android.util.Log.d("TowerLoginApi",
                        "autoGetOmmsCookie: existing ommsCookie has pwdaToken, NOT overwriting"
                        + " existingLen=" + existingOmmsCookie.length());
                ommsCookieStr = existingOmmsCookie; // 后续 ViewState 获取用有 pwdaToken 的
            }

            android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie SUCCESS len="
                    + ommsCookieStr.length() + " preview="
                    + ommsCookieStr.substring(0, Math.min(120, ommsCookieStr.length())));

            // ─── Step 5：GET listFsu.xhtml，提取真实 ViewState ────────────────
            // ★ 用最终确定的 ommsCookieStr（含 pwdaToken 的版本）发请求
            try {
                final String finalOmmsCookie = ommsCookieStr;
                OkHttpClient vsClient = new OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
                Request vsReq = new Request.Builder()
                        .url("http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml")
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                        .addHeader("Referer", "http://omms.chinatowercom.cn:9000/layout/index.xhtml")
                        .addHeader("Cache-Control", "no-cache")
                        .addHeader("Cookie", finalOmmsCookie)
                        .build();
                String vsHtml = "";
                try (Response vsResp = vsClient.newCall(vsReq).execute()) {
                    vsHtml = vsResp.body() != null ? vsResp.body().string() : "";
                    android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie ViewState GET code="
                            + vsResp.code() + " htmlLen=" + vsHtml.length()
                            + " preview=" + vsHtml.substring(0, Math.min(100, vsHtml.length())));
                }
                // 用 AccessControlApi 的 extractViewState（包级可见性问题→直接内联正则）
                java.util.regex.Matcher vsM = java.util.regex.Pattern.compile(
                        "javax\\.faces\\.ViewState[^>]*value=\"([^\"]+)\"").matcher(vsHtml);
                if (vsM.find()) {
                    String vs = vsM.group(1);
                    android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie got ViewState=" + vs);
                    // 写入 AccessControlApi 的缓存（反射或直接调用静态方法）
                    com.towerops.app.api.AccessControlApi.setCachedViewState(vs);
                } else {
                    // 格式2：<state>...</state>
                    java.util.regex.Matcher vsM2 = java.util.regex.Pattern.compile(
                            "<state>([^<]+)</state>").matcher(vsHtml);
                    if (vsM2.find() && !vsM2.group(1).trim().isEmpty()) {
                        String vs = vsM2.group(1).trim();
                        android.util.Log.d("TowerLoginApi", "autoGetOmmsCookie got ViewState(state)=" + vs);
                        com.towerops.app.api.AccessControlApi.setCachedViewState(vs);
                    } else {
                        android.util.Log.w("TowerLoginApi",
                                "autoGetOmmsCookie: ViewState not found in listFsu.xhtml, keeping j_id3");
                    }
                }
            } catch (Exception vsEx) {
                android.util.Log.w("TowerLoginApi", "autoGetOmmsCookie ViewState fetch failed: " + vsEx.getMessage());
            }

            boolean finalHasPwda = ommsCookieStr.contains("pwdaToken");
            String msg = finalHasPwda
                    ? "OMMS自动登录成功，Cookie len=" + ommsCookieStr.length()
                    : "OMMS已获取JSESSIONID（无pwdaToken），请在设置中手动粘贴完整OMMS Cookie";
            return new Result(true, msg, ommsCookieStr);

        } catch (Exception e) {
            android.util.Log.e("TowerLoginApi", "autoGetOmmsCookie failed", e);
            return new Result(false, "OMMS自动登录异常: " + e.getMessage(), null);
        }
    }

    /**
     * RSA/ECB/PKCS1Padding 加密，返回 Base64 字符串
     */
    private String rsaEncrypt(String plaintext) throws Exception {
        byte[] keyBytes = Base64.decode(publicKey, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey pubKey = keyFactory.generatePublic(spec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }
}

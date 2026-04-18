package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;
import com.towerops.app.util.SignUtil;
import com.towerops.app.util.TimeUtil;

import org.json.JSONObject;

/**
 * 登录相关 API —— 对应易语言按钮59（获取验证码）和按钮51（PIN码登录）
 *
 * [BUG-FIX] userid/c_account 硬编码问题
 *   原代码：发短信(4A_LOGIN_SMS_SEND)和PIN登录(4A_LOGIN_CHECK_PIN)的 URL 及 POST 里
 *           userid 和 c_account 全部硬编码为 "2662936450"，不管选哪个账号登录
 *           都用这个固定 ID 请求，导致服务器返回的 token/userid 对应的是该固定账号，
 *           而不是当前选中的账号，造成后台接单时身份错乱。
 *   修复：改为动态使用当前 account 字段作为 c_account，
 *         URL 中的 userid 参数使用临时占位值（服务器在未登录时不校验该字段），
 *         登录成功后 Session.userid 由服务器返回的真实 userid 覆盖。
 */
public class LoginApi {

    private static final String BASE = "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service";
    /** ★ 平台版本号，铁塔APP升级后在此处修改 */
    static final String V = "1.0.95";
    /** ★ upvs 版本串，铁塔升级后在此处修改（格式 yyyy-MM-dd-ccssoft） */
    private static final String UPVS = "2026-03-31-ccssoft";

    // =====================================================================
    // 1. 获取短信验证码（两步：先校验账号，再发送短信）
    // =====================================================================
    public static class SmsResult {
        public boolean success;
        public String  message;
    }

    public static SmsResult sendSmsCode(String account, String password,
                                        String verifyCode, String cookie) {
        SmsResult result = new SmsResult();
        String ts   = TimeUtil.getCurrentTimestamp();
        String sign = SignUtil.buildLoginSign(account, ts);

        // --- 第一步：登录校验 ---
        String url1  = BASE + "?porttype=USER_LOGIN&v=" + V + "&c=0";
        String post1 = "loginName=" + account
                + "&password="        + password
                + "&loginVersion=202206"
                + "&verifyCode="      + verifyCode
                + "&clientType=0"
                + "&osversion=android%3A14"
                + "&softversion="     + V
                + "&phoneinfo=M2011K2C"
                + "&pin=&imsi=&phoneNum=&appacctid=&token=&pushtype=1"
                + "&c_timestamp="    + ts
                + "&c_account="      + account
                + "&c_sign="         + sign
                + "&upvs=" + UPVS;

        // 第一步用 postWithHeaders，获取服务器返回的新 Set-Cookie
        HttpUtil.HttpResponse resp1 = HttpUtil.postWithHeaders(url1, post1, null, cookie);
        String str1 = (resp1 != null) ? resp1.body : "";
        // 若服务器在第一步返回了新 Cookie，第二步必须用新 Cookie（Session 绑定）
        String cookieForStep2 = cookie;
        if (resp1 != null && resp1.setCookie != null && !resp1.setCookie.isEmpty()) {
            // 用新 Cookie 替换（铁塔服务器 USER_LOGIN 成功后会下发新 Session Cookie）
            cookieForStep2 = resp1.setCookie;
            System.out.println("[LoginApi] Step1 returned new cookie, using for step2: " + cookieForStep2.substring(0, Math.min(40, cookieForStep2.length())));
        }
        try {
            JSONObject j1 = new JSONObject(str1);
            if (!"OK".equals(j1.optString("status"))) {
                result.success = false;
                // 返回真实错误信息
                String errMsg = j1.optString("errorMsg", "");
                String dataInfo = j1.optString("data_info", "");
                if (!errMsg.isEmpty()) {
                    result.message = "第一步失败: " + errMsg;
                } else if (!dataInfo.isEmpty()) {
                    result.message = "第一步失败: " + dataInfo;
                } else {
                    result.message = "第一步失败(status=" + j1.optString("status") + "): " + str1;
                }
                return result;
            }
        } catch (Exception e) {
            result.success = false;
            result.message = "第一步解析失败: " + str1;
            return result;
        }

        // --- 第二步：发送短信 ---
        ts = TimeUtil.getCurrentTimestamp();
        sign = SignUtil.buildLoginSign(account, ts);  // ts 更新后必须重算 sign
        String url2  = BASE + "?porttype=4A_LOGIN_SMS_SEND&v=" + V + "&userid=" + account + "&c=0";
        String post2 = "loginName="     + account
                + "&password="          + password
                + "&loginVersion=202206"
                + "&clientType=0"
                + "&osversion=android%3A14"
                + "&softversion="       + V
                + "&phoneinfo=M2011K2C"
                + "&c_timestamp="       + ts
                + "&c_account="         + account
                + "&c_sign="            + sign
                + "&upvs=" + UPVS;

        // [BUG-FIX] 第二步必须用第一步返回的新 Cookie（Session 绑定）
        String str2 = HttpUtil.post(url2, post2, null, cookieForStep2);
        try {
            JSONObject j2 = new JSONObject(str2);
            result.success = "OK".equals(j2.optString("status"));
            result.message = result.success ? "验证码已发送" : "短信发送失败: " + str2;
        } catch (Exception e) {
            result.success = false;
            result.message = "第二步解析失败: " + str2;
        }
        return result;
    }

    // =====================================================================
    // 2. PIN 码登录（对应按钮51）
    // =====================================================================
    public static class LoginResult {
        public boolean success;
        public String  message;
        public String  userid;
        public String  token;
        public String  mobilephone;
        public String  username;
        /** 登录账号标识（对应铁塔App的 user.loginname，如 wx-linjy22）*/
        public String  loginname;
    }

    public static LoginResult loginWithPin(String account, String password,
                                           String verifyCode, String pin) {
        return loginWithPin(account, password, verifyCode, pin, "");
    }

    public static LoginResult loginWithPin(String account, String password,
                                           String verifyCode, String pin, String cookie) {
        LoginResult result = new LoginResult();
        String ts   = TimeUtil.getCurrentTimestamp();
        String sign = SignUtil.buildLoginSign(account, ts);

        // [BUG-FIX] userid 和 c_account 由硬编码改为动态使用当前 account
        String url  = BASE + "?porttype=4A_LOGIN_CHECK_PIN&v=" + V + "&userid=" + account + "&c=0";
        String post = "loginName="     + account
                + "&password="         + password
                + "&loginVersion=202206"
                + "&verifyCode="       + verifyCode
                + "&clientType=0"
                + "&osversion=android%3A14"
                + "&softversion="      + V
                + "&phoneinfo=M2011K2C"
                + "&pin="              + pin
                + "&pushtype=1"
                + "&c_timestamp="      + ts
                + "&c_account="        + account
                + "&c_sign="           + sign
                + "&upvs=" + UPVS;

        // [BUG-FIX] 必须携带验证码阶段的 Cookie，保证与发短信是同一 Session
        String str = HttpUtil.post(url, post, null, cookie.isEmpty() ? null : cookie);
        try {
            JSONObject json = new JSONObject(str);
            result.userid      = getNestedStr(json, "user.userid");
            result.token       = json.optString("token", "");
            result.mobilephone = getNestedStr(json, "user.mobilephone");
            result.username    = getNestedStr(json, "user.username");
            result.loginname   = getNestedStr(json, "user.loginname");
            result.success     = str.contains("mobilephone");
            result.message     = result.success ? "登录成功" : "登录失败";
        } catch (Exception e) {
            result.success = false;
            result.message = "JSON解析失败: " + str;
        }
        return result;
    }

    // =====================================================================
    // 4. 获取 appacctid 和 X-Auth-Token
    //    易语言中调用 OBTAIN_TOKEN_NEW 接口，返回 token 和 appacctid
    //    协议头用 APP登录后的 token（Authorization: Bearer token）
    //    POST参数用运维账号的用户名
    // =====================================================================
    public static String obtainAppAcctId(String userid, String username, String appToken, String cookie) {
        android.util.Log.d("LoginApi", "========== obtainAppAcctId ==========");
        String BASE = "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service";

        try {
            // 易语言中的固定值
            String FIXED_C_SIGN = "9ADF4F992537DFC09E6620DE9B3193B0";
            String FIXED_UPVS = "2026-01-04-ccssoft";
            String ts = TimeUtil.getCurrentTimestamp();

            // URL 格式（易语言）
            String url = BASE + "?porttype=OBTAIN_TOKEN_NEW&v=1.0.95&userid=" + userid + "&c=0";

            // POST 参数（易语言格式）
            // ★★★ 修复（2026-04-18）：c_account 应传 loginname（昵称），不是 userid
            //    易语言代码：c_account=昵称编辑框.内容（即 loginname，如 wx-linjy22）
            String post = "username=" + username
                    + "&c_timestamp=" + ts
                    + "&c_account=" + username  // 传 loginname，不是 userid
                    + "&c_sign=" + FIXED_C_SIGN
                    + "&upvs=" + FIXED_UPVS;

            // 计算 Content-Length（易语言中是127）
            int contentLength = post.getBytes("UTF-8").length;

            // 易语言协议头：用 appToken 替换 "token2"
            // Authorization: token2  →  Authorization: <appToken>
            // ★★★ 修复（2026-04-18）：移除 Accept-Encoding: gzip，避免服务器返回压缩数据
            String headers = "Authorization: " + appToken + "\n"
                    + "equiptoken: \n"
                    + "appVer: 202112\n"
                    + "Content-Type: application/x-www-form-urlencoded\n"
                    + "Content-Length: " + contentLength + "\n"
                    + "Host: ywapp.chinatowercom.cn:58090\n"
                    + "Connection: Keep-Alive\n"
                    + "Cookie: ULTRA_U_K=\"\"; ULTRA_U_K=\"\"\n"
                    + "User-Agent: okhttp/4.10.0";

            android.util.Log.d("LoginApi", "OBTAIN_TOKEN_NEW URL: " + url);
            android.util.Log.d("LoginApi", "OBTAIN_TOKEN_NEW POST: " + post);
            android.util.Log.d("LoginApi", "OBTAIN_TOKEN_NEW Auth: " + (appToken.length() > 20 ? appToken.substring(0, 20) + "..." : appToken));
            android.util.Log.d("LoginApi", "OBTAIN_TOKEN_NEW Headers:\n" + headers);

            String resp = HttpUtil.post(url, post, headers, null);
            android.util.Log.d("LoginApi", "OBTAIN_TOKEN_NEW 响应: " + resp);

            if (resp != null && !resp.isEmpty()) {
                JSONObject json = new JSONObject(resp);
                String status = json.optString("status", "");
                String appacctid = json.optString("appacctid", "");
                String returnedToken = json.optString("token", "");

                android.util.Log.d("LoginApi", "status: " + status);
                android.util.Log.d("LoginApi", "appacctid: " + appacctid);
                android.util.Log.d("LoginApi", "token: " + (returnedToken.isEmpty() ? "(empty)" : returnedToken.substring(0, Math.min(50, returnedToken.length())) + "..."));

                if ("OK".equals(status) || "10000".equals(status)) {
                    // 成功：保存 X-Auth-Token 和 appacctid
                    Session s = Session.get();
                    if (!returnedToken.isEmpty()) {
                        s.doorApprovalXAuthToken = returnedToken;
                        android.util.Log.d("LoginApi", "已更新 doorApprovalXAuthToken: " + returnedToken.substring(0, Math.min(50, returnedToken.length())));
                    }
                    if (!appacctid.isEmpty()) {
                        s.doorApprovalAcctId = appacctid;
                        android.util.Log.d("LoginApi", "已更新 doorApprovalAcctId: " + appacctid);
                    }
                    return appacctid;
                } else {
                    android.util.Log.w("LoginApi", "OBTAIN_TOKEN_NEW 返回失败: status=" + status);
                }
            } else {
                android.util.Log.w("LoginApi", "OBTAIN_TOKEN_NEW 响应为空");
            }
        } catch (Exception e) {
            android.util.Log.e("LoginApi", "OBTAIN_TOKEN_NEW 异常: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        // ★★★ 修复（2026-04-18）：失败时不覆盖原有值，保持 Session 中已有的 token
        android.util.Log.w("LoginApi", "OBTAIN_TOKEN_NEW 调用失败，不覆盖原有认证信息");
        return "";  // 返回空，让调用方使用后备值或保留原有值
    }

    /** 兼容旧调用（无 Cookie） */
    public static String obtainAppAcctId(String userid, String username, String token) {
        return obtainAppAcctId(userid, username, token, null);
    }

    /** 解析 "user.userid" 这类带点的路径 */
    private static String getNestedStr(JSONObject root, String path) {
        try {
            String[] parts = path.split("\\.");
            JSONObject cur = root;
            for (int i = 0; i < parts.length - 1; i++) {
                cur = cur.getJSONObject(parts[i]);
            }
            return cur.optString(parts[parts.length - 1], "");
        } catch (Exception e) {
            return "";
        }
    }

    // =====================================================================
    // 3. 图形验证码直接登录（无需手机短信验证码 / PIN 码）
    //    对应易语言新模式：只调用 USER_LOGIN 一步，verifyCode 传图形码
    //    服务器校验通过后直接返回 token/userid，不再需要短信二次验证
    // =====================================================================
    public static LoginResult loginDirect(String account, String password,
                                          String verifyCode, String cookie) {
        LoginResult result = new LoginResult();
        String ts   = TimeUtil.getCurrentTimestamp();
        String sign = SignUtil.buildLoginSign(account, ts);

        String url  = BASE + "?porttype=USER_LOGIN&v=" + V + "&c=0";
        String post = "loginName="    + account
                + "&password="        + password
                + "&loginVersion=202206"
                + "&verifyCode="      + verifyCode
                + "&clientType=0"
                + "&osversion=android%3A12"
                + "&softversion="     + V
                + "&phoneinfo=VYG-AL00"
                + "&pin=&imsi=&phoneNum=&appacctid=&token=&pushtype=1"
                + "&c_timestamp="     + ts
                + "&c_account="       + account
                + "&c_sign="          + sign
                + "&upvs="            + UPVS;

        // 用 postWithHeaders 获取完整响应（含 Set-Cookie，不过直接登录只有一步不需要传递）
        HttpUtil.HttpResponse resp = HttpUtil.postWithHeaders(url, post, null, cookie);
        String str = (resp != null) ? resp.body : "";
        try {
            JSONObject json = new JSONObject(str);
            String status = json.optString("status", "");
            if ("OK".equals(status)) {
                result.userid      = getNestedStr(json, "user.userid");
                result.token       = json.optString("token", "");
                result.mobilephone = getNestedStr(json, "user.mobilephone");
                result.username    = getNestedStr(json, "user.username");
                result.loginname   = getNestedStr(json, "user.loginname");
                result.success     = !result.token.isEmpty();
                result.message     = result.success ? "登录成功" : "登录失败：未返回token";
            } else {
                result.success = false;
                String errMsg   = json.optString("errorMsg", "");
                String dataInfo = json.optString("data_info", "");
                if (!errMsg.isEmpty()) {
                    result.message = errMsg;
                } else if (!dataInfo.isEmpty()) {
                    result.message = dataInfo;
                } else {
                    result.message = "登录失败(status=" + status + "): " + str;
                }
            }
        } catch (Exception e) {
            result.success = false;
            result.message = "JSON解析失败: " + str;
        }
        return result;
    }
}

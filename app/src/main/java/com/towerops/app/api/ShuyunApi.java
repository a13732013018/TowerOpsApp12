package com.towerops.app.api;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 数运工单 API —— 对应数运系统APP版登录和工单接口
 *
 * 接口列表：
 * 1. getImgcode      - 获取验证码图片（PC版）
 * 2. loginByPc       - PC版登录（需要验证码）
 * 3. loginByApp      - APP版登录（用户名+密码+IMEI）
 * 4. getTaskList    - 获取工单列表
 * 5. acceptTask     - 接单
 * 6. revertTask     - 回单
 */
public class ShuyunApi {

    // PC版服务器
    private static final String PC_BASE = "http://zjtowercom.cn:8998";
    // APP版服务器
    private static final String APP_BASE = "http://223.95.77.175:19021";

    // ===================== PC端账号 =====================
    // PC端账号（用于PC版网站登录）
    public static final String PC_USER = "13566295657";
    public static final String PC_PASS = "MLcQylxc4733Iav/cNx+oQ==";

    // ===================== APP端账号 =====================
    // APP端默认账号
    public static final String DEFAULT_USER = "13732013018";
    public static final String DEFAULT_PASS = "F8TVasxWplZNK7AJq4T1cA==";
    public static final String DEFAULT_IMEI = "ba9f03beaacd4c05";

    // APP端备用账号
    public static final String BACKUP_USER = "15858734252";
    public static final String BACKUP_PASS = "0/YBW5U6t/yAZggx3MfHCQ==";
    public static final String BACKUP_IMEI = "a873a215e542edab";

    // =====================================================================
    // 1. 获取验证码图片（PC版）
    // =====================================================================
    /**
     * 获取验证码图片和IP
     * @return 包含image和ip的JSON字符串
     */
    public static String getImgcode() {
        String url = PC_BASE + "/api/auth/jwt/getImgcode";
        try {
            String result = HttpUtil.get(url, null, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析验证码返回的IP
     */
    public static String parseIp(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            return root.optString("ip", "");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析验证码返回的Base64图片
     */
    public static android.graphics.Bitmap parseCaptchaImage(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            String base64Image = root.optString("image", "");
            if (!base64Image.isEmpty()) {
                byte[] imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解析验证码返回的数学运算题目（如果有）
     * 返回格式如：{"num1": 5, "num2": 3, "symbol": "+"}
     */
    public static CaptchaMath parseMathCode(String jsonStr) {
        CaptchaMath math = new CaptchaMath();
        try {
            JSONObject root = new JSONObject(jsonStr);
            
            // 先检查这些字段是否真的存在于JSON中，而不是用默认值
            boolean hasNum1 = root.has("num1");
            boolean hasNum2 = root.has("num2");
            boolean hasSymbol = root.has("symbol");
            
            // 只有当所有字段都存在时，才解析数学题
            if (hasNum1 && hasNum2 && hasSymbol) {
                math.num1 = root.getInt("num1");
                math.num2 = root.getInt("num2");
                math.symbol = root.getString("symbol");
                // 计算结果
                switch (math.symbol) {
                    case "+": math.result = math.num1 + math.num2; break;
                    case "-": math.result = math.num1 - math.num2; break;
                    case "×": math.result = math.num1 * math.num2; break;
                    case "÷": math.result = math.num2 != 0 ? math.num1 / math.num2 : 0; break;
                    default: math.result = math.num1 + math.num2;
                }
                math.hasMath = true;
            } else {
                // 服务器没有返回数学题字段，说明是普通图形验证码
                math.hasMath = false;
            }
        } catch (Exception e) {
            math.hasMath = false;
        }
        return math;
    }

    /**
     * 验证码数学运算封装
     */
    public static class CaptchaMath {
        public int num1 = 0;
        public int num2 = 0;
        public String symbol = "+";
        public int result = 0;
        public boolean hasMath = false;
    }

    /**
     * 验证码结果封装（包含图片和IP）
     */
    public static class CaptchaResult {
        public android.graphics.Bitmap image;
        public String ip = "";
        public CaptchaMath math = new CaptchaMath();
    }

    /**
     * 获取验证码图片和IP（从JSON中解析Base64图片）
     * @return CaptchaResult包含图片和IP
     */
    public static CaptchaResult getCaptcha() {
        CaptchaResult result = new CaptchaResult();
        try {
            // 获取验证码JSON
            String jsonStr = getImgcode();
            if (jsonStr.isEmpty()) {
                return result;
            }

            // 解析IP
            result.ip = parseIp(jsonStr);

            // 解析数学题
            result.math = parseMathCode(jsonStr);

            // 从JSON中解析Base64图片
            result.image = parseCaptchaImage(jsonStr);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 从URL获取图片Bitmap
     */
    private static android.graphics.Bitmap getImageBitmap(String imgUrl) {
        try {
            java.net.URL url = new java.net.URL(imgUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            java.io.InputStream is = conn.getInputStream();
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =====================================================================
    // 2. PC版登录
    // =====================================================================
    /**
     * PC版登录（需要验证码）- 旧版本，只返回JSON字符串
     * @param username 用户名
     * @param password 密码（加密后的）
     * @param imgcode 验证码
     * @param ip IP地址（从getImgcode获取）
     * @return 登录结果JSON
     */
    public static String loginByPc(String username, String password, String imgcode, String ip) {
        String url = PC_BASE + "/api/auth/jwt/token";

        String post = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"imgcode\":\"" + imgcode + "\","
                + "\"ip\":\"" + ip + "\"}";

        String headers = buildPcLoginHeader(post.length());
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * PC版登录（需要验证码）- 【核心】新版本，同时获取 Authorization token 和 Set-Cookie
     * @param username 用户名
     * @param password 密码（加密后的）
     * @param imgcode 验证码
     * @param ip IP地址（从getImgcode获取）
     * @return PcLoginResult 包含 token 和 cookieToken
     */
    public static PcLoginResult loginByPcWithCookie(String username, String password, String imgcode, String ip) {
        String url = PC_BASE + "/api/auth/jwt/token";

        String post = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"imgcode\":\"" + imgcode + "\","
                + "\"ip\":\"" + ip + "\"}";

        String headers = buildPcLoginHeader(post.length());
        
        PcLoginResult result = new PcLoginResult();
        try {
            // 【核心】使用 postWithHeaders 获取 Set-Cookie
            HttpUtil.HttpResponse response = HttpUtil.postWithHeaders(url, post, headers, null);
            
            // 解析 JSON 获取 token
            if (response.body != null && !response.body.isEmpty()) {
                JSONObject root = new JSONObject(response.body);
                result.token = root.optString("data", "");
            }
            
            // 【核心】Authorization 和 towerNumber-Token 使用相同的值（PC登录获取的token）
            result.cookieToken = result.token;
            System.out.println("[ShuyunApi] PC登录成功，token: " + (result.token.length() > 20 ? result.token.substring(0, 20) + "..." : result.token));
            
            result.success = !result.token.isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 解析PC版登录返回的token
     */
    public static String parsePcToken(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            return root.optString("data", "");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * PC版登录结果封装（包含token和cookie）
     */
    public static class PcLoginResult {
        public String token = "";        // Authorization 使用的 token
        public String cookieToken = "";  // Cookie 中 towerNumber-Token 的值
        public boolean success = false;
    }

    /**
     * 解析PC版登录返回的完整结果（包括token和cookie）
     * 【核心】Authorization 和 towerNumber-Token 可能不同
     */
    public static PcLoginResult parsePcLoginResult(String jsonStr, String setCookieHeader) {
        PcLoginResult result = new PcLoginResult();
        try {
            // 1. 从 JSON 中获取 token（用于 Authorization）
            JSONObject root = new JSONObject(jsonStr);
            result.token = root.optString("data", "");
            
            // 2. 从 Set-Cookie 中提取 towerNumber-Token（用于 Cookie）
            if (setCookieHeader != null && !setCookieHeader.isEmpty()) {
                result.cookieToken = extractTowerNumberToken(setCookieHeader);
            }
            
            // 如果 cookieToken 为空，使用 token 作为备选
            if (result.cookieToken.isEmpty()) {
                result.cookieToken = result.token;
            }
            
            result.success = !result.token.isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 从 Set-Cookie 头中提取 towerNumber-Token
     */
    private static String extractTowerNumberToken(String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isEmpty()) {
            return "";
        }
        try {
            // 按分号分割多个 cookie
            String[] cookies = setCookieHeader.split(";");
            for (String cookie : cookies) {
                cookie = cookie.trim();
                if (cookie.startsWith("towerNumber-Token=")) {
                    return cookie.substring("towerNumber-Token=".length());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // =====================================================================
    // 3. 同时登录PC端和APP端
    // =====================================================================
    /**
     * 同时登录PC端和APP端（使用指定账号）
     * @param userIndex 账号索引（0或1）
     * @return 包含PC和APP登录结果的封装对象
     */
    public static ShuyunDualLoginResult loginDual(int userIndex) {
        ShuyunDualLoginResult result = new ShuyunDualLoginResult();

        // 获取账号信息
        String user, pass, imei;
        if (userIndex == 1) {
            user = BACKUP_USER;
            pass = BACKUP_PASS;
            imei = BACKUP_IMEI;
        } else {
            user = DEFAULT_USER;
            pass = DEFAULT_PASS;
            imei = DEFAULT_IMEI;
        }

        // 1. 先获取验证码图片和IP（PC端）
        String imgcodeResult = getImgcode();
        String pcIp = parseIp(imgcodeResult);

        if (pcIp.isEmpty()) {
            result.errorMsg = "获取PC端IP失败";
            return result;
        }
        result.pcIp = pcIp;

        // 2. PC端登录（使用固定验证码 "1234"，实际使用时需要用户输入）
        // 注意：PC端需要图形验证码，这里用默认值，实际应该让用户输入
        String pcLoginResult = loginByPc(user, pass, "1234", pcIp);
        String pcToken = parsePcToken(pcLoginResult);

        if (pcToken.isEmpty()) {
            // PC端登录可能需要验证码，暂时跳过或用备用方式
            result.pcToken = "";
            result.pcLoginSuccess = false;
        } else {
            result.pcToken = pcToken;
            result.pcLoginSuccess = true;
        }

        // 3. APP端登录
        String appLoginResult = loginByApp(user, pass, imei);
        ShuyunLoginResult appLogin = parseAppLogin(appLoginResult);

        if (appLogin.success) {
            result.appToken = appLogin.token;
            result.appUserId = appLogin.userId;
            result.appLoginSuccess = true;
        } else {
            result.appLoginSuccess = false;
            result.errorMsg = "APP登录失败";
        }

        result.success = result.appLoginSuccess; // APP登录成功即认为整体成功
        return result;
    }

    /**
     * 同时登录结果封装
     */
    public static class ShuyunDualLoginResult {
        public boolean success = false;
        public boolean pcLoginSuccess = false;
        public boolean appLoginSuccess = false;
        public String pcToken = "";
        public String pcIp = "";
        public String appToken = "";
        public String appUserId = "";
        public String errorMsg = "";
    }

    // =====================================================================
    // APP版登录（原有方法保留）
    // =====================================================================
    /**
     * APP版登录（用户名+密码+IMEI）
     * @param userIndex 账号索引（0或1）
     * @return 登录结果JSON
     */
    public static String loginByApp(int userIndex) {
        String user, pass, imei;

        if (userIndex == 1) {
            user = BACKUP_USER;
            pass = BACKUP_PASS;
            imei = BACKUP_IMEI;
        } else {
            user = DEFAULT_USER;
            pass = DEFAULT_PASS;
            imei = DEFAULT_IMEI;
        }

        return loginByApp(user, pass, imei);
    }

    /**
     * APP版登录（指定账号）
     */
    public static String loginByApp(String user, String pass, String imei) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"deviceType\":\"ANDROID\","
                + "\"password\":\"" + pass + "\","
                + "\"imei\":\"" + imei + "\","
                + "\"userId\":\"" + user + "\""
                + "},"
                + "\"interfaceName\":\"/api/auth/jwt/tokenAppByAiPu\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppLoginHeader();
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析APP版登录返回的token和userId
     */
    public static ShuyunLoginResult parseAppLogin(String jsonStr) {
        ShuyunLoginResult result = new ShuyunLoginResult();
        try {
            JSONObject root = new JSONObject(jsonStr);
            // 解析错误信息
            result.message = root.optString("message", "");
            // 解析data
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                result.token = data.optString("data", "");
                result.userId = data.optString("userId", "");
                result.success = !result.token.isEmpty();
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.message = e.getMessage();
        }
        return result;
    }

    public static class ShuyunLoginResult {
        public String token = "";
        public String userId = "";
        public boolean success = false;
        public String message = "";
    }

    // =====================================================================
    // 4. 获取工单列表（待实现，根据实际接口补充）
    // =====================================================================
    /**
     * 获取工单列表
     * @param token 登录token
     * @param userId 用户ID
     * @return 工单列表JSON
     */
    public static String getTaskList(String token, String userId) {
        // TODO: 根据实际接口实现
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"userId\":\"" + userId + "\""
                + "},"
                + "\"interfaceName\":\"/api/task/list\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(token);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析工单列表
     */
    public static List<ShuyunTaskInfo> parseTaskList(String jsonStr) {
        List<ShuyunTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray arr = data.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        ShuyunTaskInfo info = new ShuyunTaskInfo();
                        info.id = item.optString("id", "");
                        info.taskName = item.optString("taskName", "");
                        info.siteName = item.optString("siteName", "");
                        info.status = item.optString("status", "");
                        info.createTime = item.optString("createTime", "");
                        list.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static class ShuyunTaskInfo {
        public String id = "";
        public String taskName = "";
        public String siteName = "";
        public String status = "";
        public String createTime = "";
    }

    // =====================================================================
    // 5. 接单
    // =====================================================================
    /**
     * 接单
     */
    public static String acceptTask(String token, String userId, String taskId) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"userId\":\"" + userId + "\","
                + "\"taskId\":\"" + taskId + "\""
                + "},"
                + "\"interfaceName\":\"/api/task/accept\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(token);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // =====================================================================
    // 6. 回单
    // =====================================================================
    /**
     * 回单
     */
    public static String revertTask(String token, String userId, String taskId, String remark) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"userId\":\"" + userId + "\","
                + "\"taskId\":\"" + taskId + "\","
                + "\"remark\":\"" + remark + "\""
                + "},"
                + "\"interfaceName\":\"/api/task/revert\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(token);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // =====================================================================
    // 工具方法
    // =====================================================================

    /**
     * 判断API调用是否成功
     */
    public static boolean isSuccess(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return false;
        // 空对象 {} 也视为成功（审核接口成功时返回 {}）
        if ("{}".equals(jsonStr.trim())) return true;
        try {
            JSONObject root = new JSONObject(jsonStr);
            // 根据不同接口判断success字段
            if (root.has("success")) {
                return root.getBoolean("success");
            }
            // status字段为200表示成功（登录接口）
            if (root.has("status")) {
                return root.getInt("status") == 200;
            }
            if (root.has("code")) {
                return "200".equals(root.getString("code")) || "0".equals(root.getString("code"));
            }
            // flag字段为"1"表示成功（工单处理接口），但flag="0"时不能仅凭此判断失败
            if (root.has("flag")) {
                String flag = root.getString("flag");
                // 如果flag="1"直接返回成功
                if ("1".equals(flag)) {
                    return true;
                }
                // flag="0"时继续检查其他字段
            }
            // data字段存在即认为成功
            return root.has("data");
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildPcHeader() {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/login\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    private static String buildPcLoginHeader(int contentLength) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Length: " + contentLength + "\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/login\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    /**
     * 县级/市级/省级审核工单列表请求头（form-urlencoded）
     * 【核心】Authorization 和 towerNumber-Token 使用相同的值（PC登录token）
     * @param pcToken PC登录获取的token（用于Authorization）
     * @param cookieToken Cookie中的token（与pcToken相同）
     */
    private static String buildCountyApiHeader(String pcToken, String cookieToken) {
        // ★ Cookie 只需要 towerNumber-Token
        //   去掉硬编码的 SECKEY/BMAP/sysName（伊世豪是别人账号，会导致服务端校验失败）
        //   去掉 SameSite=Lax/Secure（这些不是 Cookie 字段，放在 Cookie 头里会报错）
        String cookie = "towerNumber-Token=" + (cookieToken != null && !cookieToken.isEmpty() ? cookieToken : pcToken);
        
        // ★ 不加 Accept-Encoding：OkHttp 自动处理 gzip，手动加会关闭自动解压导致乱码
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + pcToken + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/x-www-form-urlencoded;charset=UTF-8\n"
                + "Cookie: " + cookie + "\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    /**
     * 县级/市级/省级审核工单列表请求头（兼容旧版本，使用同一个token）
     */
    private static String buildCountyApiHeader(String pcToken) {
        return buildCountyApiHeader(pcToken, pcToken);
    }

    /**
     * 省级审核专用请求头（form-urlencoded）
     * 【关键】Referer使用省级审核页面地址
     * @param pcToken PC登录获取的token（用于Authorization）
     * @param cookieToken Cookie中的token（与pcToken相同）
     */
    private static String buildProvinceApiHeader(String pcToken, String cookieToken) {
        // ★ 只保留 towerNumber-Token，去掉 sysName=伊世豪 等硬编码字段
        String cookie = "towerNumber-Token=" + (cookieToken != null && !cookieToken.isEmpty() ? cookieToken : pcToken);
        
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + pcToken + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/x-www-form-urlencoded;charset=UTF-8\n"
                + "Cookie: " + cookie + "\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/myWork/taskTodo\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    /**
     * 获取工单列表 GET 请求头（与浏览器一致）
     * 【核心】Authorization 和 towerNumber-Token 使用相同的值（PC登录token）
     * @param token PC登录获取的token
     */
    private static String buildTaskListHeader(String token) {
        // ★ 只保留 towerNumber-Token，去掉 sysName=伊世豪 等硬编码字段
        String cookie = "towerNumber-Token=" + token;
        
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + token + "\n"
                + "Cache-Control: no-cache\n"
                + "Cookie: " + cookie + "\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Proxy-Connection: keep-alive\n"
                + "Referer: http://zjtowercom.cn:8998/myWork/taskTodo\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";
    }

    /**
     * 县级/市级审核 JSON POST请求头（用于提交审核）
     * 【核心】Authorization 和 Cookie 中的 towerNumber-Token 可能不同
     * @param authToken 用于 Authorization 的 token（当前登录获取的）
     * @param cookieToken 用于 Cookie 中 towerNumber-Token 的值（固定值）
     */
    private static String buildCountyJsonHeader(String authToken, String cookieToken) {
        // ★ 只保留 towerNumber-Token，去掉 sysName=伊世豪 等硬编码字段
        String cookie = "towerNumber-Token=" + (cookieToken != null && !cookieToken.isEmpty() ? cookieToken : authToken);
        
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + authToken + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Cookie: " + cookie + "\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    /**
     * 县级/市级审核 JSON POST请求头（兼容旧版本，使用同一个token）
     */
    private static String buildCountyJsonHeader(String pcToken) {
        return buildCountyJsonHeader(pcToken, pcToken);
    }

    private static String buildAppLoginHeader() {
        return "Content-Type: application/x-www-form-urlencoded\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 14; M2011K2C Build/UKQ1.240624.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36\n"
                + "Host: 223.95.77.175:19021\n"
                + "Connection: Keep-Alive\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept: application/json, text/plain, */*\n"
                + "Origin: http://223.95.77.175:19021\n"
                + "Referer: http://223.95.77.175:19021/\n"
                + "X-Requested-With: com.zjtt.mobile";
    }

    private static String buildAppHeader(String token) {
        return "Content-Type: application/x-www-form-urlencoded\n"
                + "Authorization: " + token + "\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 14; M2011K2C Build/UKQ1.240624.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36\n"
                + "Host: 223.95.77.175:19021\n"
                + "Connection: Keep-Alive\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept: application/json, text/plain, */*\n"
                + "Origin: http://223.95.77.175:19021\n"
                + "Referer: http://223.95.77.175:19021/\n"
                + "X-Requested-With: com.zjtt.mobile";
    }

    // =====================================================================
    // 7. 智联工单接口（PC端API）
    // =====================================================================
    /**
     * 获取智联待签收工单列表
     * @param pcToken PC端登录Token
     * @param userId 用户ID
     * @param cityArea 区县代码（如330326）
     * @return 工单列表JSON
     */
    public static String getZhilianTaskList(String pcToken, String userId, String cityArea) {
        String url = PC_BASE + "/api/flowable/flowable/task/listToSign";

        // POST body参数（与易语言一致）
        String post = "page=1"
                + "&limit=10"
                + "&flowId=1024,1124,1160,1220"
                + "&orderType="
                + "&userId=" + userId
                + "&area=330300"
                + "&cityArea=" + cityArea;

        String headers = buildCountyApiHeader(pcToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析智联工单列表
     * @param jsonStr API返回的JSON
     * @return 工单列表
     */
    public static List<ZhilianTaskInfo> parseZhilianTaskList(String jsonStr) {
        List<ZhilianTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray rows = data.optJSONArray("rows");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.getJSONObject(i);
                        ZhilianTaskInfo info = new ZhilianTaskInfo();
                        info.flowId = item.optString("flowId", "");
                        info.jobId = item.optString("jobId", "");
                        info.orderNum = item.optString("orderNum", "");
                        info.userId = item.optString("userId", "");
                        info.workInstId = item.optString("workInstId", "");
                        info.siteName = item.optString("title", item.optString("siteName", ""));
                        info.createTime = item.optString("createTime", "");
                        info.flowName = item.optString("flowName", "");
                        list.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 智联工单接单
     * @param pcToken PC端登录Token
     * @param workInstId 工单实例ID
     * @param orderNum 工单编号
     * @param flowId 流程ID
     * @param jobId 任务ID
     * @param userId 用户ID
     * @return 接单结果
     */
    public static String acceptZhilianTask(String pcToken, String workInstId, String orderNum,
            String flowId, String jobId, String userId) {
        String url = PC_BASE + "/api/flowable/flowableFlow/updateWorkStatus";

        // JSON格式请求体（与易语言一致）
        String post = "{\"workInstId\":\"" + workInstId + "\","
                + "\"orderNum\":\"" + orderNum + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"userId\":\"" + userId + "\"}";

        // 使用JSON POST请求头
        String headers = buildCountyJsonHeader(pcToken);

        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 智联工单信息封装
     */
    public static class ZhilianTaskInfo {
        public String flowId = "";
        public String jobId = "";
        public String orderNum = "";
        public String userId = "";
        public String workInstId = "";
        public String siteName = "";
        public String createTime = "";
        public String flowName = "";
    }

    // =====================================================================
    // 8. 县级审核接口（PC端API）
    // =====================================================================
    /**
     * 获取县级待审核工单列表
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，固定值）
     * @param userId 区县经理代号（36745平阳/31950泰顺）
     * @return 工单列表JSON
     */
    public static String getCountyTaskList(String pcToken, String cookieToken, String userId) {
        // 与易语言完全一致：URL和body都带参数，使用POST请求（form-urlencoded）
        String url = PC_BASE + "/api/flowable/flowable/task/listTodo"
                + "?page=1"
                + "&limit=10"
                + "&userId=" + userId
                + "&flowId=1025,1054,1055,1056,1131,1027,1028,1033,1038,1040,1048,1072,1118,1122,1127,1137,1143,1063"
                + "&orderType=&xmlx=&area=&cityArea=";

        String post = "page=1&limit=10&userId=" + userId
                + "&flowId=1025,1054,1055,1056,1131,1027,1028,1033,1038,1040,1048,1072,1118,1122,1127,1137,1143,1063"
                + "&orderType=&xmlx=&area=&cityArea=";

        // 【核心】使用固定cookieToken的请求头
        String headers = buildCountyApiHeader(pcToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取县级待审核工单列表（兼容旧版本，使用同一个token）
     */
    public static String getCountyTaskList(String pcToken, String userId) {
        return getCountyTaskList(pcToken, pcToken, userId);
    }

    /**
     * 解析县级待审核工单列表
     * @param jsonStr API返回的JSON
     * @return 工单列表
     */
    public static List<CountyTaskInfo> parseCountyTaskList(String jsonStr) {
        List<CountyTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray rows = data.optJSONArray("rows");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.getJSONObject(i);
                        CountyTaskInfo info = new CountyTaskInfo();
                        info.orderNum = item.optString("orderNum", "");
                        info.req_deal_limit = item.optString("req_deal_limit", "");
                        info.station_code = item.optString("station_code", "");
                        info.req_comp_time = item.optString("req_comp_time", "");
                        info.flowInstId = item.optString("flowInstId", "");
                        info.city_name = item.optString("city_name", "");
                        info.workInstId = item.optString("workInstId", "");
                        info.data_name = item.optString("data_name", "");
                        info.rwkssj_time = item.optString("rwkssj_time", "");
                        info.flowId = item.optString("flowId", "");
                        info.order_type = item.optString("order_type", "");
                        info.jobName = item.optString("jobName", "");
                        info.station_name = item.optString("station_name", "");
                        info.relaType = item.optString("relaType", "");
                        info.flowInstName = item.optString("flowInstName", "");
                        info.flowName = item.optString("flowName", "");
                        info.jobInstId = item.optString("jobInstId", "");
                        info.jobId = item.optString("jobId", "");
                        list.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 县级审核通过
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，固定值）
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param userId 区县经理代号
     * @return 审核结果
     */
    public static String submitCountyAudit(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String userId) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // 计算nextJobAndUser：根据jobId前缀（注意：需要用英文逗号）
        String jobPrefix = jobId.contains("_") ? jobId.substring(0, jobId.indexOf("_")) : jobId;
        String nextJobAndUser = jobPrefix + "_003@12101,12102,12104,12108,12376,22979,30264,37493,12107,37614,37881,12103,12106,12120,12101,12103";

        // JSON格式请求体（与易语言一致）
        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + userId + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + nextJobAndUser + "\","
                + "\"copyUsers\":\"" + "" + "\"}";

        // 【核心】使用双token
        String headers = buildCountyJsonHeader(pcToken, cookieToken);

        try {
            String result = HttpUtil.put(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 县级审核通过（兼容旧版本，使用同一个token）
     */
    public static String submitCountyAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String userId) {
        return submitCountyAudit(pcToken, pcToken, orderNum, jobInstId, flowInstId, jobId, workInstId, flowId, userId);
    }

    /**
     * 县级审核工单信息封装
     */
    public static class CountyTaskInfo {
        public String orderNum = "";
        public String req_deal_limit = "";
        public String station_code = "";
        public String req_comp_time = "";
        public String flowInstId = "";
        public String city_name = "";
        public String workInstId = "";
        public String data_name = "";
        public String rwkssj_time = "";
        public String flowId = "";
        public String order_type = "";
        public String jobName = "";
        public String station_name = "";
        public String relaType = "";
        public String flowInstName = "";
        public String flowName = "";
        public String jobInstId = "";
        public String jobId = "";
    }

    // =====================================================================
    // 9. 省级审核接口（PC端API）- 需要开发者权限
    // =====================================================================
    // 省级审核用户ID
    private static final String PROVINCE_AUDIT_USER_ID = "32269";

    /**
     * 获取省级待审核工单列表
     * 【关键】与市级审核完全一致，仅userId不同
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param cityArea 区县代码（如330326）
     * @return 工单列表JSON
     */
    public static String getProvinceTaskList(String pcToken, String cookieToken, String cityArea) {
        // 与市级审核完全一致：URL和body都带参数，使用POST请求（form-urlencoded）
        String url = PC_BASE + "/api/flowable/flowable/task/listTodo"
                + "?page=1"
                + "&limit=10"
                + "&userId=" + PROVINCE_AUDIT_USER_ID
                + "&flowId=&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        String post = "page=1&limit=10&userId=" + PROVINCE_AUDIT_USER_ID
                + "&flowId=&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        // 【核心】与市级审核使用相同的请求头
        String headers = buildCountyApiHeader(pcToken, cookieToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取省级待审核工单列表（兼容旧版本）
     */
    public static String getProvinceTaskList(String pcToken, String cityArea) {
        return getProvinceTaskList(pcToken, pcToken, cityArea);
    }

    /**
     * 省级普通审核通过
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param jobId_ID 延期判断返回的jobId
     * @return 审核结果
     */
    public static String submitProvinceAudit(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // 省级审核的审核人ID是随机选择的（与易语言一致）
        String[] auditorIds = {"39717", "37257", "35887", "41247", "41541", "11875", "11881", "12178", "12182", "12187",
                "12190", "12191", "12195", "12200", "22990", "24091", "29719", "31254", "40414"};
        int randomIndex = (int) (Math.random() * auditorIds.length);
        String auditorId = auditorIds[randomIndex];

        // JSON格式请求体
        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + auditorId + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + jobId_ID + "@10023\","
                + "\"copyUsers\":\"" + "" + "\"}";

        // 【核心】使用双token
        String headers = buildCountyJsonHeader(pcToken, cookieToken);

        try {
            String result = HttpUtil.put(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 省级普通审核通过（兼容旧版本）
     */
    public static String submitProvinceAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        return submitProvinceAudit(pcToken, pcToken, orderNum, jobInstId, flowInstId, jobId, workInstId, flowId, jobId_ID);
    }

    /**
     * 省级延期审核通过
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param jobId_ID 延期判断返回的jobId
     * @return 审核结果
     */
    public static String submitProvinceDelayAudit(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // 省级审核的审核人ID是随机选择的（与易语言一致）
        String[] auditorIds = {"11875", "11881", "12178", "12182", "12187", "12190", "12191", "12195", "12200",
                "22990", "24091", "29719", "31254", "40414"};
        int randomIndex = (int) (Math.random() * auditorIds.length);
        String auditorId = auditorIds[randomIndex];

        // 延期审核的 nextJobAndUser 更长，包含更多审核人（与易语言一致）
        String nextJobAndUser = jobId_ID + "@11875,11881,12173,12178,12182,12187,12190,12191,12192,12195,12200,12201,12204,12205,22990,24091,29719,29721,29723,30170,30172,31190,31254,31255,31741,31943,32166,32269,32270,32743,33012,33323,33520,34567,34812,34999,35822,35823,35887,35910,36073,36074,36075,36119,37169,37170,37171,37208,37209,37210,37211,37229,37256,37257,37258,37272,37273,37319,37364,37383,37718,37958,37959,38097,38381,38572,38620,39304,39482,39717,40414,40458,40565,40566,40752,40883,40884,40885,40954,40966,40967,40984,41031,41188,41246,41247,41317,41350,41390,41541";

        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + auditorId + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + nextJobAndUser + "\","
                + "\"copyUsers\":\"" + "" + "\"}";

        // 【核心】使用双token
        String headers = buildCountyJsonHeader(pcToken, cookieToken);

        try {
            String result = HttpUtil.put(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 省级延期审核通过（兼容旧版本）
     */
    public static String submitProvinceDelayAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        return submitProvinceDelayAudit(pcToken, pcToken, orderNum, jobInstId, flowInstId, jobId, workInstId, flowId, jobId_ID);
    }

    // =====================================================================
    // 9. 市级审核接口（PC端API）
    // =====================================================================
    // 市级审核用户ID
    private static final String CITY_AUDIT_USER_ID = "12376";

    /**
     * 获取市级待审核工单列表
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param cityArea 区县代码（如330326）
     * @return 工单列表JSON
     */
    public static String getCityTaskList(String pcToken, String cookieToken, String cityArea) {
        // 与易语言完全一致：URL和body都带参数，使用POST请求（form-urlencoded）
        String url = PC_BASE + "/api/flowable/flowable/task/listTodo"
                + "?page=1"
                + "&limit=10"
                + "&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        String post = "page=1&limit=10&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        // 【核心】使用双token
        String headers = buildCountyApiHeader(pcToken, cookieToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取市级待审核工单列表（兼容旧版本）
     */
    public static String getCityTaskList(String pcToken, String cityArea) {
        return getCityTaskList(pcToken, pcToken, cityArea);
    }

    /**
     * 获取市级已办工单列表
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param cityArea 区县代码（如330326）
     * @return 已办工单列表JSON
     */
    public static String getCityFinishedList(String pcToken, String cookieToken, String cityArea) {
        // 与易语言一致：URL和body都带参数，使用POST请求（form-urlencoded）
        // limit=10用于获取更多数据，以便分离显示前3条+省监控审核工单
        String url = PC_BASE + "/api/flowable/flowable/task/listToFinish"
                + "?page=1"
                + "&limit=10"
                + "&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&area=330300&cityArea=" + cityArea;

        String post = "page=1&limit=10&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&area=330300&cityArea=" + cityArea;

        // 【核心】使用双token
        String headers = buildCountyApiHeader(pcToken, cookieToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取市级已办工单列表（兼容旧版本）
     */
    public static String getCityFinishedList(String pcToken, String cityArea) {
        return getCityFinishedList(pcToken, pcToken, cityArea);
    }

    /**
     * 延期判断接口（市级审核用）
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param relaType 关联类型
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @return 延期判断结果JSON
     */
    public static String checkDelay(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String relaType, String flowInstId, String jobId, String workInstId, String flowId) {
        return checkDelayWithUserId(pcToken, cookieToken, orderNum, jobInstId, relaType, flowInstId, jobId, workInstId, flowId, CITY_AUDIT_USER_ID);
    }

    /**
     * 延期判断接口（市级审核用，兼容旧版本）
     */
    public static String checkDelay(String pcToken, String orderNum, String jobInstId,
            String relaType, String flowInstId, String jobId, String workInstId, String flowId) {
        return checkDelay(pcToken, pcToken, orderNum, jobInstId, relaType, flowInstId, jobId, workInstId, flowId);
    }

    /**
     * 延期判断接口（省级审核用）
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param relaType 关联类型
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @return 延期判断结果JSON
     */
    public static String checkDelayForProvince(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String relaType, String flowInstId, String jobId, String workInstId, String flowId) {
        return checkDelayWithUserId(pcToken, cookieToken, orderNum, jobInstId, relaType, flowInstId, jobId, workInstId, flowId, PROVINCE_AUDIT_USER_ID);
    }

    /**
     * 延期判断接口（省级审核用，兼容旧版本）
     */
    public static String checkDelayForProvince(String pcToken, String orderNum, String jobInstId,
            String relaType, String flowInstId, String jobId, String workInstId, String flowId) {
        return checkDelayForProvince(pcToken, pcToken, orderNum, jobInstId, relaType, flowInstId, jobId, workInstId, flowId);
    }

    /**
     * 延期判断接口（通用）
     */
    private static String checkDelayWithUserId(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String relaType, String flowInstId, String jobId, String workInstId, String flowId, String userId) {
        String url = PC_BASE + "/api/flowable/orderInfo/showWorkInfo";

        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"spec\":\"" + relaType + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"workType\":\"D\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"userId\":\"" + userId + "\","
                + "\"requireId\":\"" + orderNum + "\","
                + "\"gotoType\":\"taskTodo\"}";

        // 【核心】使用双token
        String headers = buildCountyJsonHeader(pcToken, cookieToken);
        
        // 【调试日志】
        System.out.println("[ShuyunApi] checkDelay userId: " + userId);
        System.out.println("[ShuyunApi] checkDelay POST: " + post.substring(0, Math.min(200, post.length())) + "...");

        try {
            String result = HttpUtil.post(url, post, headers, null);
            System.out.println("[ShuyunApi] checkDelay result length: " + (result != null ? result.length() : 0));
            if (result != null && result.length() > 0) {
                System.out.println("[ShuyunApi] checkDelay result preview: " + result.substring(0, Math.min(300, result.length())));
            }
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析延期判断结果
     * @param jsonStr API返回的JSON
     * @return 延期判断结果：自动审核/省监控审核
     */
    public static String parseDelayResult(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return "自动审核";
        }
        try {
            // 返回的是纯JSON对象（不是包装在data里）
            JSONObject root = new JSONObject(jsonStr);

            // 从 nextJobsList[0].checktitle 获取延期判断结果
            JSONArray nextJobsList = root.optJSONArray("nextJobsList");
            if (nextJobsList != null && nextJobsList.length() > 0) {
                JSONObject firstJob = nextJobsList.getJSONObject(0);
                String checktitle = firstJob.optString("checktitle", "");

                // 根据 checktitle 判断审核类型
                if (checktitle.contains("省监控") || checktitle.contains("延期")) {
                    return "省监控审核";
                }
                // 默认是自动审核
                return "自动审核";
            }

            return "自动审核";
        } catch (Exception e) {
            e.printStackTrace();
            return "自动审核";
        }
    }

    /**
     * 从延期判断结果中提取 jobId（用于提交审核）
     * 【核心】与易语言一致：从 "jobId":" 提取到 ","seletyp"
     * @param jsonStr API返回的JSON
     * @return jobId 字符串
     */
    public static String extractJobIdFromDelayResult(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return "";
        }
        try {
            // 【核心】与易语言一致：文本提取方式
            // 易语言：jobId_ID ＝ 文本_取出中间文本 (延期判断文本, #常量_jobId1, #常量_jobId2)
            // #常量_jobId1="jobId":" 
            // #常量_jobId2=","seletyp
            String startMarker = "\"jobId\":\"";
            String endMarker = "\",\"seletyp";
            
            int startIndex = jsonStr.indexOf(startMarker);
            if (startIndex == -1) {
                // 尝试不带空格的变体
                startMarker = "\"jobId\":\"";
                startIndex = jsonStr.indexOf(startMarker);
            }
            
            if (startIndex != -1) {
                startIndex += startMarker.length();
                int endIndex = jsonStr.indexOf(endMarker, startIndex);
                if (endIndex != -1) {
                    String jobId = jsonStr.substring(startIndex, endIndex);
                    System.out.println("[ShuyunApi] 提取jobId_ID: " + jobId);
                    return jobId;
                }
            }
            
            // 如果文本提取失败，回退到 JSON 解析
            JSONObject root = new JSONObject(jsonStr);
            JSONArray nextJobsList = root.optJSONArray("nextJobsList");
            if (nextJobsList != null && nextJobsList.length() > 0) {
                JSONObject firstJob = nextJobsList.getJSONObject(0);
                String jobId = firstJob.optString("jobId", "");
                System.out.println("[ShuyunApi] 提取jobId_ID(JSON): " + jobId);
                return jobId;
            }

            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 市级普通审核通过
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param jobId_ID 延期判断返回的jobId
     * @return 审核结果
     */
    public static String submitCityAudit(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // JSON格式请求体
        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + CITY_AUDIT_USER_ID + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + jobId_ID + "@10023\","
                + "\"copyUsers\":\"" + "" + "\"}";

        // 【核心】使用双token
        String headers = buildCountyJsonHeader(pcToken, cookieToken);

        try {
            String result = HttpUtil.put(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 市级普通审核通过（兼容旧版本）
     */
    public static String submitCityAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        return submitCityAudit(pcToken, pcToken, orderNum, jobInstId, flowInstId, jobId, workInstId, flowId, jobId_ID);
    }

    /**
     * 市级延期审核通过
     * @param pcToken PC端登录Token（Authorization）
     * @param cookieToken PC端登录Token（Cookie中的towerNumber-Token，可为空）
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param jobId_ID 延期判断返回的jobId
     * @return 审核结果
     */
    public static String submitCityDelayAudit(String pcToken, String cookieToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // 延期审核的 nextJobAndUser 更长，包含更多审核人
        String nextJobAndUser = jobId_ID + "@11875,11881,12173,12178,12182,12187,12190,12191,12192,12195,12200,12201,12204,12205,22990,24091,29719,29721,29723,30170,30172,31190,31254,31255,31741,31943,32166,32269,32270,32743,33012,33323,33520,34567,34812,34999,35822,35823,35887,35910,36073,36074,36075,36119,37169,37170,37171,37208,37209,37210,37211,37229,37256,37257,37258,37272,37273,37319,37364,37383,37718,37958,37959,38097,38381,38572,38620,39304,39482,39717,40414,40458,40565,40566,40752,40883,40884,40885,40954,40966,40967,40984,41031,41188,41246,41247,41317,41350,41390,41541";

        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + CITY_AUDIT_USER_ID + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + nextJobAndUser + "\","
                + "\"copyUsers\":\"" + "" + "\"}";

        // 【核心】使用双token
        String headers = buildCountyJsonHeader(pcToken, cookieToken);

        try {
            String result = HttpUtil.put(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 市级延期审核通过（兼容旧版本）
     */
    public static String submitCityDelayAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        return submitCityDelayAudit(pcToken, pcToken, orderNum, jobInstId, flowInstId, jobId, workInstId, flowId, jobId_ID);
    }

    /**
     * 市级审核工单信息封装（复用CountyTaskInfo结构）
     */
    public static class CityTaskInfo extends CountyTaskInfo {
        public String shsj_time = ""; // 审核时间
    }

    // =====================================================================
    // PC端API Header
    // =====================================================================
    private static String buildPcApiHeader(String token) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + token + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    private static String buildPcJsonHeader(int contentLength) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Length: " + contentLength + "\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    // =====================================================================
    // 省内待办工单 API（对应易语言"待办简易工单"查询逻辑）
    // =====================================================================

    /**
     * 省内待办工单数据模型
     * 对应易语言超级列表框31的各列
     */
    public static class ProvinceInnerTaskInfo {
        public String index         = "";  // 序号
        public String groupName     = "";  // 分组（分组常量匹配结果）
        public String station_code  = "";  // 站点编码
        public String station_name  = "";  // 站点名称
        public String orderNum      = "";  // 工单号
        public String flowName      = "";  // 流程名称
        public String data_name     = "";  // 数据名称
        public String createTime    = "";  // 创建时间
        public String req_comp_time = "";  // 要求完成时间
        public String req_deal_limit= "";  // 处理限期
        public String jobName       = "";  // 环节名称
        public String handler       = "";  // 处理人姓名
        public String order_desc    = "";  // 工单描述
        public String flowInstId    = "";  // 流程实例ID
        public String jobId         = "";  // 任务ID
        public String workInstId    = "";  // 工作实例ID
        public String flowId        = "";  // 流程ID
        public String jobInstId     = "";  // 任务实例ID
        public String order_type    = "";  // 工单类型
        public String workType      = "";  // 工作类型
    }

    /**
     * 分组1（第一小组）的人员配置
     */
    public static final String[][] GROUP1_MEMBERS = {
        {"12001", "林甲雨"},
        {"22961", "卢智伟"},
        {"12005", "高树调"},
        {"12004", "苏忠前"},
        {"12003", "黄经兴"},
        {"12007", "陶大取"}
    };

    /**
     * 分组2（第二小组）的人员配置
     */
    public static final String[][] GROUP2_MEMBERS = {
        {"11961", "刘娟娟"},
        {"11956", "朱兴达"},
        {"11954", "王成"},
        {"11953", "夏念悦"},
        {"11950", "梅传威"}
    };

    /**
     * 工单类型选项（与易语言组合框8对应）
     * index 0 = 全部(1124,1220,1028,1063)
     * index 1 = 应急(1028)
     * index 2 = 投诉(1063)
     * index 3 = 综合(1124,1220)
     * index 4 = 其他(1118)
     */
    public static final String[] ORDER_TYPE_CODES = {
        "1124,1220,1028,1063",
        "1028",
        "1063",
        "1124,1220",
        "1118"
    };

    /**
     * 站点分组常量（与易语言分组常量数组对应）
     * 用于匹配 station_name 判断所属分组
     */
    public static final String[][] STATION_GROUP_RULES = {
        {"卢智伟、杨桂",  ""},   // 分组1：需从Session或配置读取实际常量
        {"高树调、倪传井", ""},   // 分组2
        {"苏忠前、许方喜", ""},   // 分组3
        {"黄经兴、蔡亮",  ""},   // 分组4
        {"陈德岳",        ""}    // 室分1组
    };

    /**
     * 查询单个处理人的省内待办工单（limit=1000）
     * 对应易语言 子程序_工作线程_拉取单人工单 中的网络请求
     *
     * @param pcToken     PC登录token（Authorization）
     * @param cookieToken Cookie中的towerNumber-Token
     * @param userId      处理人userId（如 "12001"）
     * @param flowId      工单类型代码（如 "1124,1220,1028,1063"）
     * @param cityArea    区号（如 "330326"）
     * @return 工单列表JSON
     */
    public static String getProvinceInnerTaskList(String pcToken, String cookieToken,
            String userId, String flowId, String cityArea) {
        String url = PC_BASE + "/api/flowable/flowable/task/listTodo"
                + "?page=1&limit=1000&userId=" + userId
                + "&flowId=" + flowId
                + "&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        String post = "page=1&limit=1000&userId=" + userId
                + "&flowId=" + flowId
                + "&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        String headers = buildCountyApiHeader(pcToken, cookieToken);
        
        // 【调试日志】
        System.out.println("[ProvinceInner] URL: " + url);
        System.out.println("[ProvinceInner] POST: " + post);
        System.out.println("[ProvinceInner] Token: " + (pcToken != null ? pcToken.substring(0, Math.min(20, pcToken.length())) + "..." : "null"));
        
        try {
            String result = HttpUtil.post(url, post, headers, null);
            // 【调试日志】
            System.out.println("[ProvinceInner] Response: " + (result != null ? result.substring(0, Math.min(200, result.length())) + "..." : "null"));
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[ProvinceInner] Error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 解析省内待办工单列表（含处理人信息）
     * 对应易语言 子程序_工作线程_拉取单人工单 中的JSON解析逻辑
     *
     * @param jsonStr   API返回的JSON
     * @param handler   处理人姓名（由调用方传入）
     * @param groupName 分组名（由调用方匹配后传入，可为空）
     * @return 工单列表
     */
    public static List<ProvinceInnerTaskInfo> parseProvinceInnerTaskList(
            String jsonStr, String handler, String groupName) {
        List<ProvinceInnerTaskInfo> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return list;
            JSONArray rows = data.optJSONArray("rows");
            if (rows == null) return list;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.getJSONObject(i);
                ProvinceInnerTaskInfo info = new ProvinceInnerTaskInfo();
                info.station_code  = item.optString("station_code",  "");
                info.station_name  = item.optString("station_name",  "");
                info.orderNum      = item.optString("orderNum",       "");
                info.flowName      = item.optString("flowName",       "");
                info.data_name     = item.optString("data_name",      "");
                info.createTime    = item.optString("createTime",     "");
                info.req_comp_time = item.optString("req_comp_time",  "");
                info.req_deal_limit= item.optString("req_deal_limit", "");
                info.jobName       = item.optString("jobName",        "");
                info.order_desc    = item.optString("order_desc",     "");
                info.flowInstId    = item.optString("flowInstId",     "");
                info.jobId         = item.optString("jobId",          "");
                info.workInstId    = item.optString("workInstId",     "");
                info.flowId        = item.optString("flowId",         "");
                info.jobInstId     = item.optString("jobInstId",      "");
                info.order_type    = item.optString("order_type",     "");
                info.workType      = item.optString("workType",       "");
                info.handler       = handler;
                info.groupName     = groupName != null ? groupName : "";
                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ============================================================
    // 省内待办 - 计划上站 & 综合上站回单 API
    // ============================================================

    /**
     * 计划上站 - 保存计划信息
     * 对应易语言 子程序_综合上站计划
     * 
     * @param pcToken PC登录token
     * @param cookieToken Cookie token
     * @param cityArea 区县代码（如330326）
     * @param groupId 小组ID（如361, 363等）
     * @param groupName 小组名称
     * @param stationCode 站点代码
     * @param stationName 站点名称
     * @param upSiteTime 上站时间（格式：yyyy-MM-dd）
     * @return API响应JSON
     */
    public static String saveSitePlan(String pcToken, String cookieToken,
            String cityArea, String groupId, String groupName,
            String stationCode, String stationName, String upSiteTime) {
        String url = PC_BASE + "/api/manager/site-plan-manage/save-info";
        
        // 构建JSON body
        String jsonBody = "{"
            + "\"area\":\"330300\","
            + "\"city\":[\"" + cityArea + "\"],"
            + "\"groupId\":" + groupId + ","
            + "\"groupName\":\"" + groupName + "\","
            + "\"siteCode\":\"\","
            + "\"siteName\":\"\","
            + "\"upStiteTime\":\"" + upSiteTime + " 00:00:00\","
            + "\"addStation\":[{"
            + "\"stationCode\":\"" + stationCode + "\","
            + "\"stationName\":\"" + stationName + "\","
            + "\"areaCode\":\"330300\","
            + "\"cityCode\":\"" + cityArea + "\""
            + "}]}"
            + "}";
        
        String headers = buildCountyApiHeader(pcToken, cookieToken);
        headers = headers.replace("Content-Type: application/x-www-form-urlencoded", 
                                  "Content-Type: application/json");
        
        try {
            String result = HttpUtil.post(url, jsonBody, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 综合上站回单 - 步骤一（真正的工单流转）
     * 对应易语言 数运APP回单_步骤一
     *
     * 【关键】使用 APP 服务器代理接口（223.95.77.175:19021），不是PC服务器
     * 通过 requestType="put" 委托服务器转发 PUT 请求
     * nextJobAndUser = jobId纯字母部分 + "_002@" + 区县经理代号
     *
     * @param appToken  数运APP登录token（不是PC token）
     * @param receiptId 处理人ID（如 "12001"）
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param orderNum 工单号
     * @param flowId 流程ID
     * @param jobInstId 任务实例ID
     * @param countyManagerCode 区县经理代号（如 "36745"）
     * @return API响应JSON
     */
    public static String receiptStepOne(String appToken,
            String receiptId, String flowInstId, String jobId, String workInstId,
            String orderNum, String flowId, String jobInstId, String countyManagerCode) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        // jobId 只保留字母部分（对应易语言 文本区分_只取字母）
        String jobIdLetters = jobId.replaceAll("[^a-zA-Z]", "");
        String nextJobAndUser = jobIdLetters + "_002@" + countyManagerCode;

        String post = "data={"
            + "\"head\":{\"Authorization\":\"" + appToken + "\"},"
            + "\"requestType\":\"put\","
            + "\"data\":{"
            + "\"flowInstId\":\"" + flowInstId + "\","
            + "\"jobId\":\"" + jobId + "\","
            + "\"dealContent\":\"ok\","
            + "\"workInstId\":\"" + workInstId + "\","
            + "\"orderNum\":\"" + orderNum + "\","
            + "\"nextJobAndUser\":\"" + nextJobAndUser + "\","
            + "\"operType\":\"011\","
            + "\"flowId\":\"" + flowId + "\","
            + "\"userId\":\"" + receiptId + "\","
            + "\"jobInstId\":\"" + jobInstId + "\""
            + "},"
            + "\"interfaceName\":\"/api/flowable/flowable/task/complete\","
            + "\"apiType\":\"REST\""
            + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(appToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 综合上站回单 - 步骤二（记录操作日志）
     * 对应易语言 数运APP回单_步骤二
     *
     * 【关键】使用 APP 服务器代理接口，接口路径为 /api/flowable/zhszCountClicks/insert
     * 字段包含 jobName("接单")、remark("综合上站-处理成功")、createBy、isZhsz("1")
     *
     * @param appToken  数运APP登录token
     * @param receiptId 处理人ID
     * @param stationCode 站点代码
     * @param orderType 工单类型
     * @param orderNum 工单号
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param workType 工作类型
     * @param stationName 站点名称
     * @param flowId 流程ID
     * @param flowName 流程名称
     * @return API响应JSON
     */
    public static String receiptStepTwo(String appToken,
            String receiptId, String stationCode, String orderType, String orderNum,
            String jobId, String workInstId, String workType, String stationName,
            String flowId, String flowName) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
            + "\"head\":{\"Authorization\":\"" + appToken + "\"},"
            + "\"requestType\":\"post\","
            + "\"data\":{"
            + "\"jobName\":\"接单\","
            + "\"stationCode\":\"" + stationCode + "\","
            + "\"orderType\":\"" + orderType + "\","
            + "\"orderNum\":\"" + orderNum + "\","
            + "\"remark\":\"综合上站-处理成功\","
            + "\"flowName\":\"" + flowName + "\","
            + "\"jobId\":\"" + jobId + "\","
            + "\"createBy\":\"" + receiptId + "\","
            + "\"isZhsz\":\"1\","
            + "\"workInstId\":\"" + workInstId + "\","
            + "\"workType\":\"" + workType + "\","
            + "\"stationName\":\"" + stationName + "\","
            + "\"flowId\":\"" + flowId + "\""
            + "},"
            + "\"interfaceName\":\"/api/flowable/zhszCountClicks/insert\","
            + "\"apiType\":\"REST\""
            + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(appToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析回单结果（穿透式鉴定引擎）
     * 完全对应易语言"穿透式鉴定引擎"逻辑：
     *   1. 先穿透内层：data.msg 有内容 → 绝对失败
     *   2. 内层干净 → 看外层 returnCode（1/200/0 = 成功）
     *   3. 否则取 returnMsg/msg 作为错误描述
     */
    public static ReceiptResult parseReceiptResult(String jsonStr) {
        ReceiptResult result = new ReceiptResult();
        result.success = false;
        result.message = "未知错误";

        if (jsonStr == null || jsonStr.isEmpty()) {
            result.message = "失败:网络超时或被拦截";
            return result;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);

            // 第一步：穿透内层，看 data.msg 是否有报错（有内容 = 绝对失败）
            JSONObject data = root.optJSONObject("data");
            String dataMsg = "";
            if (data != null) {
                dataMsg = data.optString("msg", "").trim();
            }

            if (!dataMsg.isEmpty()) {
                // 内层 data.msg 有内容，判定失败
                result.success = false;
                result.message = "失败:" + dataMsg;
                return result;
            }

            // 第二步：内层干净，看外层状态码
            String headCode = root.optString("returnCode", "").trim();
            if (headCode.isEmpty()) {
                headCode = root.optString("code", "").trim();  // 兼容其他接口
            }

            if ("1".equals(headCode) || "200".equals(headCode) || "0".equals(headCode)) {
                result.success = true;
                result.message = "回单执行成功";
                return result;
            }

            // 第三步：网关层错误，取 returnMsg/msg
            String errMsg = root.optString("returnMsg", "").trim();
            if (errMsg.isEmpty()) {
                errMsg = root.optString("msg", "").trim();
            }
            if (errMsg.isEmpty()) {
                errMsg = "未知业务异常";
            }
            result.success = false;
            result.message = "失败:" + errMsg;

        } catch (Exception e) {
            result.success = false;
            result.message = "失败:服务器系统异常";
        }

        return result;
    }

    /**
     * 回单结果数据类
     */
    public static class ReceiptResult {
        public boolean success;
        public String message;
    }

    // ============================================================
    // 待签工单查询 API
    // ============================================================

    /**
     * 查询待签工单列表
     * 对应易语言 子程序_数运APP签到
     *
     * @param appToken 数运APP登录token
     * @param userId 用户ID
     * @return API响应JSON
     */
    public static String getToSignTaskList(String appToken, String userId) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
            + "\"head\":{\"Authorization\":\"" + appToken + "\"},"
            + "\"requestType\":\"post\","
            + "\"data\":{"
            + "\"siteCode\":\"\","
            + "\"limit\":100,"
            + "\"siteName\":\"\","
            + "\"orderNum\":\"\","
            + "\"page\":1,"
            + "\"flowInstName\":\"\","
            + "\"userId\":\"" + userId + "\""
            + "},"
            + "\"interfaceName\":\"/api/flowable/mtCon/listToSign\","
            + "\"apiType\":\"REST\""
            + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        try {
            String headers = buildAppApiHeader(appToken);
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析待签工单列表
     *
     * @param json API返回的JSON
     * @return 工单列表
     */
    public static List<ProvinceInnerTaskInfo> parseToSignTaskList(String json) {
        List<ProvinceInnerTaskInfo> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;

        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return list;

            JSONObject dataObj = data.optJSONObject("data");
            if (dataObj == null) return list;

            JSONArray rows = dataObj.optJSONArray("rows");
            if (rows == null) return list;

            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) continue;

                ProvinceInnerTaskInfo info = new ProvinceInnerTaskInfo();
                info.station_code  = item.optString("station_code", "");
                info.station_name  = item.optString("station_name", "");
                info.orderNum      = item.optString("orderNum", "");
                info.flowName      = item.optString("flowName", "");
                info.data_name     = item.optString("data_name", "");
                info.createTime    = item.optString("createTime", "");
                info.req_comp_time = item.optString("req_comp_time", "");
                info.req_deal_limit= item.optString("req_deal_limit", "");
                info.jobName       = item.optString("jobName", "");
                info.order_desc    = item.optString("order_desc", "");
                info.flowInstId    = item.optString("flowInstId", "");
                info.jobId         = item.optString("jobId", "");
                info.workInstId    = item.optString("workInstId", "");
                info.flowId        = item.optString("flowId", "");
                info.jobInstId     = item.optString("jobInstId", "");
                info.order_type    = item.optString("order_type", "");
                info.workType      = item.optString("workType", "");
                info.handler       = item.optString("handler", "");
                info.groupName     = "";
                info.index         = String.valueOf(i + 1);
                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 构建数运APP API请求头
     */
    private static String buildAppApiHeader(String appToken) {
        return "User-Agent: Dalvik/2.1.0 (Linux; U; Android 14; M2011K2C Build/UKQ1.240624.001)\r\n"
            + "Authorization: " + appToken;
    }

    /**
     * 待签工单签到
     * 对应易语言 数运APP签到
     *
     * @param appToken APP登录token
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param orderNum 工单号
     * @param flowId 流程ID
     * @return API响应JSON
     */
    public static String signTask(String appToken, String userId,
            String jobId, String workInstId, String orderNum, String flowId) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
            + "\"head\":{\"Authorization\":\"" + appToken + "\"},"
            + "\"requestType\":\"post\","
            + "\"data\":{"
            + "\"jobId\":\"" + jobId + "\","
            + "\"workInstId\":\"" + workInstId + "\","
            + "\"orderNum\":\"" + orderNum + "\","
            + "\"flowId\":\"" + flowId + "\","
            + "\"userId\":\"" + userId + "\""
            + "},"
            + "\"interfaceName\":\"/api/flowable/mtCon/updateWorkStatus\","
            + "\"apiType\":\"REST\""
            + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        try {
            String headers = buildAppApiHeader(appToken);
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析签到结果
     *
     * @param json API返回的JSON
     * @return 解析结果
     */
    public static ReceiptResult parseSignResult(String json) {
        ReceiptResult result = new ReceiptResult();
        result.success = false;
        result.message = "解析失败";

        if (json == null || json.isEmpty()) {
            result.message = "网络超时或被拦截";
            return result;
        }

        try {
            JSONObject root = new JSONObject(json);
            String returnCode = root.optString("returnCode", "");
            String returnMsg = root.optString("returnMsg", "");
            JSONObject data = root.optJSONObject("data");

            // 判断成功：外层返回 1，且内层明确提示"成功"或 flag 为 true
            if ("1".equals(returnCode)) {
                if (data != null) {
                    String dataMsg = data.optString("msg", "");
                    boolean flag = data.optBoolean("flag", false);

                    if (dataMsg.contains("成功") || flag) {
                        result.success = true;
                        result.message = "签到成功";
                    } else if (!dataMsg.isEmpty()) {
                        result.success = false;
                        result.message = "失败:" + dataMsg;
                    } else {
                        result.success = false;
                        result.message = "失败:" + (returnMsg.isEmpty() ? "未知业务异常" : returnMsg);
                    }
                } else {
                    result.success = true;
                    result.message = "签到成功";
                }
            } else {
                result.success = false;
                result.message = "失败:" + (returnMsg.isEmpty() ? "未知业务异常" : returnMsg);
            }
        } catch (Exception e) {
            result.message = "失败:服务器返回数据异常";
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  指标查询 API（对应易语言 6 个子程序）
    //  统一使用 PC_BASE + buildCountyApiHeader，POST JSON
    // ═══════════════════════════════════════════════════════════════

    /**
     * 电子化覆盖率  /api/report/report/dataTable/2022010601
     * 字段: DATA_DATE, AREA_NAME, SITE_ALL_COUNT, SITE_COVER_COUNT, ELE_COVER_RATE
     */
    public static String queryEleCoverRate(String pcToken, String cookieToken, String startTime) {
        String url = PC_BASE + "/api/report/report/dataTable/2022010601";
        String body = "{\"startTime\":\"" + startTime + "\",\"areaCode\":\"330300\",\"region_type\":\"3\"}";
        return postMetrics(url, body, pcToken, cookieToken);
    }

    /**
     * FSU离线率  /api/report/report/dataTable/2022010400
     * 字段: CITY_NAME, AVG_OFFLINE_RATE, JW_SITE, PD_COUNT, CP_SITE, DATA_DATE
     */
    public static String queryFsuOfflineRate(String pcToken, String cookieToken, String startTime) {
        String url = PC_BASE + "/api/report/report/dataTable/2022010400";
        String body = "{\"startTime\":\"" + startTime + "\",\"areaCode\":\"330300\",\"region_type\":\"3\"}";
        return postMetrics(url, body, pcToken, cookieToken);
    }

    /**
     * 故障工单处理合格率  /api/report/report/dataTable/4
     * 字段: AREA_NAME, ORDER_COUNT, GZORDER_NUM, OUT_TIME_ORDER, GZORDER_OUTTIME_NUM,
     *       RECEIVE_RATE, HANDLE_INTIME_RATE, SHEET_CHECK_RATE, SHEET_MAKE_RATE, DATA_DATE
     * 注意：body 需要 cityId="3" 和 siteLevel="全量"
     */
    public static String queryFaultOrderQuality(String pcToken, String cookieToken, String startTime) {
        String url = PC_BASE + "/api/report/report/dataTable/4";
        String body = "{\"cityId\":\"3\",\"areaCode\":\"330300\",\"startTime\":\"" + startTime
                + "\",\"siteLevel\":\"全量\"}";
        return postMetrics(url, body, pcToken, cookieToken);
    }

    /**
     * 疑似退服  /api/report/report/dataTable/2021120920
     * 字段: CITY_NAME, DATA_DATE, YSTF_SITE, YT_SITE, YSTF_RATE
     * 注意：body 需要 siteLevel="全量"
     */
    public static String querySuspectedOutOfService(String pcToken, String cookieToken, String startTime) {
        String url = PC_BASE + "/api/report/report/dataTable/2021120920";
        String body = "{\"region_type\":\"3\",\"areaCode\":\"330300\",\"startTime\":\"" + startTime
                + "\",\"siteLevel\":\"全量\"}";
        return postMetrics(url, body, pcToken, cookieToken);
    }

    /**
     * 超频告警整治有效性（实为"故障工单统计"）  /api/report/report/dataTable/2022010602
     * 字段: AREA_NAME, DATA_DATE, SHEET_NUM, SHEET_NEW, SHEET_YL,
     *       SHEET_CS_RATE, SHEET_WX, SHEET_CS, SHEET_CL_RATE
     */
    public static String queryAlarmEffectiveness(String pcToken, String cookieToken, String startTime) {
        String url = PC_BASE + "/api/report/report/dataTable/2022010602";
        String body = "{\"startTime\":\"" + startTime + "\",\"areaCode\":\"330300\",\"region_type\":\"3\"}";
        return postMetrics(url, body, pcToken, cookieToken);
    }

    /**
     * 资产一致性  /api/report/report/dataTable/14
     * 字段: AREA_NAME, SWITCH_SON/FATHER/RATE, AIR_xxx, QS_BATTERY_xxx,
     *       PT_BATTERY_xxx, ZN_BATTERY_xxx, BATTERY_RATE, PROPERTY_RATE, DATA_DATE
     */
    public static String queryAssetConsistency(String pcToken, String cookieToken, String startTime) {
        String url = PC_BASE + "/api/report/report/dataTable/14";
        String body = "{\"lat_type\":\"3\",\"startTime\":\"" + startTime + "\",\"areaCode\":\"330300\"}";
        return postMetrics(url, body, pcToken, cookieToken);
    }

    /**
     * PUE有效率  /api/report/report/dataTable/2021122401
     * 字段: DATA_DATE, LAT_NAME, SITE_ALL, YX_SITE, DB_SITE, YX_RATE, DB_RATE,
     *       NH_RATE_1~4
     */
    public static String queryPueRate(String pcToken, String cookieToken, String startTime) {
        String url = PC_BASE + "/api/report/report/dataTable/2021122401";
        String body = "{\"startTime\":\"" + startTime + "\",\"areaCode\":\"330300\",\"region_type\":\"3\"}";
        return postMetrics(url, body, pcToken, cookieToken);
    }

    /** 通用指标查询POST（JSON body，双token PC协议头） */
    private static String postMetrics(String url, String jsonBody, String pcToken, String cookieToken) {
        // cookieToken 为空时 fallback 到 pcToken
        String cToken = (cookieToken != null && !cookieToken.isEmpty()) ? cookieToken : pcToken;
        String headers = buildCountyJsonHeader(pcToken, cToken);
        try {
            String result = HttpUtil.post(url, jsonBody, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  考核工单 API
    //  对应易语言 子程序_省内考核工单查询
    //  接口：/api/flowable/flowable/task/listToOrder
    //  flowId=1027,1028,1033,1038,1040,1048,1072,1118,1122,1127,
    //         1131,1137,1143,1124,1160,1024,1220
    // ═══════════════════════════════════════════════════════════════

    /** 考核工单数据模型 */
    public static class KaoHeOrderInfo {
        public String index;          // 序号
        public String orderCityName;  // 地市
        public String orderNum;       // 工单号
        public String stationCode;    // 站点编码
        public String flowName;       // 工单类型（易：临_工单类型）
        public String dataName;       // 任务类型（易：临_任务类型）
        public String groupName;      // 分组（匹配结果）
        public String stationName;    // 站点名称
        public String workTime;       // 工时
        public String createTime;     // 创建时间
        public String reqCompTime;    // 要求完成时间
        public String shsjTime;       // 实核时间
        public String jobName;        // 当前环节
        public String accestaff;      // 接单人
        public String remark;         // 备注
        public String alarmCreateTime;// 告警创建时间
    }

    /**
     * 查询省内考核工单列表
     * @param pcToken     PC登录Token（Authorization头）
     * @param cookieToken Cookie中的 towerNumber-Token（来自 Set-Cookie）
     * @param cityArea    行政区划代码（如 330326）
     */
    public static String getKaoHeOrderList(String pcToken, String cookieToken, String cityArea) {
        String cToken = (cookieToken != null && !cookieToken.isEmpty()) ? cookieToken : pcToken;
        String flowIds = "1027,1028,1033,1038,1040,1048,1072,1118,1122,1127,1131,1137,1143,1124,1160,1024,1220";
        // URL带参（GET参数）
        String url = PC_BASE + "/api/flowable/flowable/task/listToOrder"
                + "?page=1&limit=10000"
                + "&flowId=" + flowIds
                + "&orderType=&jobId=&orderStatusFlag=R,N&user_id=12376"
                + "&stationName=&stationCode=&sortType=&group_id=&xmlx="
                + "&startTime1=2016-02-10+00:00:00&startTime2=2030-03-30+00:00:00"
                + "&endTime1=&endTime2=&area_code=330300&cityArea=" + cityArea;
        // POST body（与易语言 post 变量完全一致）
        String post = "page=1&limit=10000"
                + "&flowId=" + flowIds
                + "&orderType=&jobId=&orderStatusFlag=R,N&user_id=12376"
                + "&stationName=&stationCode=&sortType=&group_id=&xmlx="
                + "&startTime1=2016-02-10+00:00:00&startTime2=2030-03-30+00:00:00"
                + "&endTime1=&endTime2=&area_code=330300&cityArea=" + cityArea;
        String headers = buildCountyApiHeader(pcToken, cToken);
        android.util.Log.d("KaoHeOrder", "请求URL: " + url);
        android.util.Log.d("KaoHeOrder", "pcToken前30: " + (pcToken != null && pcToken.length() > 30 ? pcToken.substring(0, 30) : pcToken));
        try {
            // 改用POST请求（与易语言 网页_访问_对象(url,1,post...) 一致，1=POST）
            String result = HttpUtil.post(url, post, headers, null);
            android.util.Log.d("KaoHeOrder", "返回长度: " + (result != null ? result.length() : 0));
            android.util.Log.d("KaoHeOrder", "返回前200: " + (result != null && result.length() > 200 ? result.substring(0, 200) : result));
            return result != null ? result : "";
        } catch (Exception e) {
            android.util.Log.e("KaoHeOrder", "请求异常: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析考核工单列表，并在本地进行过滤
     *
     * @param jsonStr       接口返回的JSON
     * @param filter10      true=过滤掉特定环节（自动审核/省监控审核/移动EOMS处理等）
     * @param filter18      true=过滤掉特定工单类型（验收遗留/退服稽核/智联业务/高铁监控等）
     * @param filterSelect13 选框13：验收工程遗留派单流程(新)  false=排除
     * @param filterSelect14 选框14：退服基站稽核工单          false=排除
     * @param filterSelect15 选框15：设备信号不准确异常整治     false=排除
     * @param filterSelect16 选框16：储能光伏验收流程          false=排除
     * @param filterSelect17 选框17：省内任务派单/超频等杂单   true=过滤掉
     * @param filterSelect22 选框22：智联业务相关              false=排除
     * @param stationGroupRules 分组常量数组{{关键词, 分组名},...}
     */
    public static java.util.List<KaoHeOrderInfo> parseKaoHeOrderList(
            String jsonStr,
            boolean filter10, boolean filter18,
            boolean filterSelect13, boolean filterSelect14,
            boolean filterSelect15, boolean filterSelect16,
            boolean filterSelect17, boolean filterSelect22,
            String[][] stationGroupRules) {

        java.util.List<KaoHeOrderInfo> result = new java.util.ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return result;

        try {
            org.json.JSONObject root = new org.json.JSONObject(jsonStr);
            // 打印返回码便于排查
            int code = root.optInt("code", root.optInt("status", 0));
            String msg = root.optString("msg", root.optString("message", ""));
            android.util.Log.d("ShuyunApi", "[考核工单] code=" + code + " msg=" + msg);
            org.json.JSONObject data = root.optJSONObject("data");
            if (data == null) {
                android.util.Log.w("ShuyunApi", "[考核工单] data is null, raw=" + (jsonStr.length() > 200 ? jsonStr.substring(0, 200) : jsonStr));
                return result;
            }
            org.json.JSONArray rows = data.optJSONArray("rows");
            if (rows == null) {
                android.util.Log.w("ShuyunApi", "[考核工单] rows is null");
                return result;
            }
            android.util.Log.d("ShuyunApi", "[考核工单] rows.length=" + rows.length());

            int seq = 0;
            for (int i = 0; i < rows.length(); i++) {
                org.json.JSONObject obj = rows.getJSONObject(i);

                String jobName   = obj.optString("job_name", "");
                String flowName  = obj.optString("flow_name", "");
                String dataName  = obj.optString("data_name", "");
                String stationName = obj.optString("station_name", "");

                boolean shouldInsert = true;

                // ── 对应易语言 选择框10 ──
                if (filter10) {
                    if (jobName.equals("自动审核") || jobName.equals("省监控审核")
                            || jobName.equals("移动EOMS处理") || jobName.equals("移动侧审核")
                            || jobName.equals("系统判断是否恢复模块数") || jobName.equals("铁塔省监控")) {
                        shouldInsert = false;
                    }
                }

                // ── 对应易语言 选择框18 ──
                if (filter18) {
                    if (flowName.equals("验收工程遗留派单流程(新)") || flowName.equals("退服基站稽核工单")
                            || dataName.equals("设备信号不准确异常整治") || dataName.equals("储能光伏验收流程")
                            || flowName.equals("智联业务故障处理流程") || flowName.equals("智联业务地市派单")
                            || flowName.equals("智联业务综合派单流程") || flowName.equals("高铁监控故障工单流程")) {
                        shouldInsert = false;
                    }
                }

                // ── 对应选18=假时的分项过滤 ──
                if (!filter18) {
                    if (!filterSelect13 && flowName.equals("验收工程遗留派单流程(新)")) shouldInsert = false;
                    if (!filterSelect14 && flowName.equals("退服基站稽核工单"))         shouldInsert = false;
                    if (!filterSelect15 && dataName.equals("设备信号不准确异常整治"))    shouldInsert = false;
                    if (!filterSelect16 && dataName.equals("储能光伏验收流程"))          shouldInsert = false;
                    if (!filterSelect22 && (flowName.equals("智联业务故障处理流程")
                            || flowName.equals("智联业务地市派单")
                            || flowName.equals("智联业务综合派单流程")
                            || flowName.equals("高铁监控故障工单流程")))                shouldInsert = false;
                    if (filterSelect17) {
                        if (flowName.equals("一般省内任务派单流程") && !dataName.equals("设备信号不准确异常整治"))
                            shouldInsert = false;
                        if (flowName.equals("超频告警整治工单") || flowName.equals("电流波动派单流程")
                                || flowName.equals("通用任务工单（铁塔）") || flowName.equals("退服隐患整治工单")
                                || flowName.equals("蓄电池整治流程") || flowName.equals("整流模块异常减少派单流程")
                                || flowName.equals("直放站故障处理流程"))
                            shouldInsert = false;
                    }
                }

                if (!shouldInsert) continue;

                KaoHeOrderInfo info = new KaoHeOrderInfo();
                seq++;
                info.index          = String.valueOf(seq);
                info.orderCityName  = obj.optString("order_city_name", "");
                info.orderNum       = obj.optString("order_num", "");
                info.stationCode    = obj.optString("station_code", "");
                info.flowName       = flowName;
                info.dataName       = dataName;
                info.stationName    = stationName;
                info.workTime       = obj.optString("work_Time", "");
                info.createTime     = obj.optString("create_time", "");
                info.reqCompTime    = obj.optString("req_comp_time", "");
                info.shsjTime       = obj.optString("shsj_time", "");
                info.jobName        = jobName;
                info.accestaff      = obj.optString("accestaff", "");
                info.remark         = obj.optString("remark", "");
                info.alarmCreateTime = obj.optString("alarm_create_time", "");

                // 分组匹配（对应易语言分组常量数组）
                info.groupName = "";
                if (stationGroupRules != null) {
                    for (String[] rule : stationGroupRules) {
                        if (stationName.contains(rule[0])) {
                            info.groupName = rule[1];
                            break;
                        }
                    }
                }

                result.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}

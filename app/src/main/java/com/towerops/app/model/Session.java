package com.towerops.app.model;

import android.content.Context;

import com.towerops.app.util.Logger;
import com.towerops.app.util.PrefHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局会话信息 —— 登录成功后持久保存，供所有线程使用（等价于易语言全局变量）
 */
public class Session {

    private static volatile Session instance;

    /** 4A Token 就绪回调列表（登录完成后通知所有等待者） */
    private static java.util.ArrayList<Runnable> on4aTokenReadyCallbacks = new java.util.ArrayList<>();

    /** 注册4A Token就绪回调（可注册多个） */
    public static void addOn4aTokenReady(Runnable cb) {
        synchronized (on4aTokenReadyCallbacks) {
            on4aTokenReadyCallbacks.add(cb);
        }
    }

    /** 通知所有4A Token就绪回调（在登录Activity保存token后调用） */
    public static void clearOn4aTokenReadyCallbacks() {
        synchronized (on4aTokenReadyCallbacks) {
            on4aTokenReadyCallbacks.clear();
        }
    }

    public static void notifyOn4aTokenReady() {
        java.util.ArrayList<Runnable> callbacks;
        synchronized (on4aTokenReadyCallbacks) {
            if (on4aTokenReadyCallbacks.isEmpty()) return;
            callbacks = new java.util.ArrayList<>(on4aTokenReadyCallbacks);
            on4aTokenReadyCallbacks.clear();
        }
        android.util.Log.d("Session", "★★ 通知 " + callbacks.size() + " 个4A Token回调");
        for (Runnable cb : callbacks) cb.run();
    }

    private Session() {}

    public static Session get() {
        if (instance == null) {
            synchronized (Session.class) {
                if (instance == null) instance = new Session();
            }
        }
        return instance;
    }

    // ---------- 登录后写入 ----------
    public volatile String userid       = "";
    public volatile String token        = "";   // Authorization 值，发请求时动态组头
    public volatile String mobilephone  = "";
    public volatile String username     = "";
    /**
     * 真实姓名（中文），来自 AccountConfig 第三列。
     * 用于与工单 actionlist 中的 acceptOperator（中文接单人姓名）比对，
     * 以决定是否触发自动接单/回单。
     *
     * [BUG-FIX] 原代码用 username（账号工号）比对中文姓名，永远不等，后台接单/回单无法触发。
     */
    public volatile String realname     = "";

    // ---------- 智联工单相关 ----------
    /**
     * c_sign 签名，用于智联工单接口
     * 从登录响应或配置中获取
     */
    public volatile String cSign        = "E9163ADC4E8E9B20293C8FC11A78E652";

    /**
     * 运维账号信息数组（对应易语言的运维账号信息数组）
     * 用于智联工单接单时的用户名参数
     */
    public volatile String[] accountConfig = new String[0];

    // ---------- 运行时配置（主线程写，工作线程读）----------
    // ★ appConfig 同时保存在内存和 SharedPreferences，服务重建后可从 prefs 恢复 ★
    public volatile String appConfig    = ""; // 选1|选2|选5|阈值反馈|阈值接单 用 \u0001 分隔
    public volatile String[] taskArray  = new String[0];

    // ---------- 智联工单配置 ----------
    /**
     * 智联工单配置：enableAccept|enableRevert|minRevertDelay|maxRevertDelay
     * 用 \u0001 分隔
     */
    public volatile String zhilianConfig = "";

    // ---------- 数运工单相关 ----------
    /**
     * 数运APP端登录token
     */
    public volatile String shuyunAppToken = "";

    /**
     * 数运APP用户ID
     */
    public volatile String shuyunAppUserId = "";

    /**
     * 数运APP登录IMEI（用于省级审核权限验证）
     */
    public volatile String shuyunAppImei = "";

    /**
     * 数运PC端登录token（用于 Authorization）
     */
    public volatile String shuyunPcToken = "";

    /**
     * 数运PC端登录token（用于 Cookie 中的 towerNumber-Token）
     * 【核心】可能与 shuyunPcToken 不同，来自登录响应的 Set-Cookie
     */
    public volatile String shuyunPcTokenCookie = "";

    /**
     * 数运PC端登录IP（用于验证）
     */
    public volatile String shuyunPcIp = "";

    /**
     * 数运区县代码（如330300），用于智联工单接口
     */
    public volatile String shuyunCityArea = "330300";

    /**
     * 区县经理代号（用于县级审核）
     * 市区: 36745, 其他: 31950
     */
    public volatile String countyManagerCode = "36745";

    /**
     * 数运账号信息（用于APP登录）
     * 格式：用户名|密码|imei 用 \u0001 分隔
     */
    public volatile String[] shuyunAccountConfig = new String[0];

    /**
     * 数运工单配置：enableAccept|enableRevert|minRevertDelay|maxRevertDelay
     * 用 \u0001 分隔
     */
    public volatile String shuyunConfig = "";

    // ---------- 铁塔4A SESSION Cookie ----------
    /**
     * 铁塔4A系统的SESSION Cookie（格式："SESSION=xxxxxxxx"）
     * 登录成功后由 WorkOrderFragment 写入，供数运等系统使用
     */
    public volatile String tower4aSessionCookie = "";

    /**
     * 铁塔4A门禁系统 Bearer Token
     * 格式：Authorization: Bearer {tower4aToken}
     * 从浏览器抓包（http://tymj.chinatowercom.cn:8006）的 Authorization 头获取
     * 用于4A开门记录查询接口 /api/recordAccess/getPage
     */
    public volatile String tower4aToken = "";

    /**
     * 4A门禁系统县级代码（如 "330326"）
     * 用于 /api/recordAccess/getPage 接口的 countyCodeList 参数
     */
    public volatile String tower4aCountyCode = "";

    /**
     * 门禁Tab当前选中的账号下标（对应 AccountConfig.ACCOUNTS）
     * 用于 OmmsLoginActivity 自动重登录时知道用哪个账号+密码
     */
    public volatile int selected4AAccountIndex = 0;

    // ---------- TOMS 安全打卡系统（chntoms5.chinatowercom.cn:8081）----------
    /**
     * TOMS 系统 Cookie（格式："loginName=xxx; fp=xxx; userOrgCode=xxx"）
     * 从浏览器抓包粘贴，与 OMMS Cookie 一起使用
     */
    public volatile String tomsCookie = "";

    /**
     * TOMS 系统 JWT Token（请求头 token 字段，从浏览器抓包获取）
     */
    public volatile String tomsToken = "";

    // ---------- 门禁告警区域选择 ----------
    /**
     * 门禁告警查询区域：0=平阳（默认），1=泰顺
     * OMMS省市县区域ID：
     *   泰顺：0001945/0099874/0107440
     *   平阳：0001945/0099874/0107437
     */
    public volatile int doorAlarmRegion = 0;

    // ---------- 门禁审批认证信息（来自铁塔App登录）----------
    /**
     * 门禁审批系统 X-Auth-Token（JWT格式，从铁塔App抓包获取）
     * 用于 http://36.111.4.4:8090 的查询接口
     */
    public volatile String doorApprovalXAuthToken = "";

    /**
     * 门禁审批系统登录账号（从铁塔App抓包获取，如 wx-linjy22）
     */
    public volatile String doorApprovalLoginAcct = "";

    /**
     * 门禁审批系统账号ID（从铁塔App抓包获取，如 203349045）
     */
    public volatile String doorApprovalAcctId = "";

    // ---------- OMMS 门禁系统 Cookie ----------
    /**
     * OMMS门禁系统Cookie
     *
     * 获取方式（优先级由高到低）：
     *   1. 自动获取（推荐）：点击门禁Tab「开启蓝牙进站监控」，程序自动通过4A SSO流程获取
     *      → 调用 TowerLoginApi.autoGetOmmsCookie(tower4aSessionCookie, loginName, ctx)
     *      → 流程：soaprequest(r=360650,s=100033) → SSO跳转 → From4A.jsp → 提取Cookie
     *   2. 手动粘贴（备选）：浏览器打开OMMS → F12 → Network → 复制任意请求的Cookie行粘贴到输入框
     *
     * 典型值（来自浏览器抓包）：
     *   route=xxx; JSESSIONID=xxx; acctId=xxx; uid=wx-linjy22;
     *   moduleUrl=/layout/index.xhtml; nodeInformation=xxx;
     *   pwdaToken=eyJhbGc...（JWT Token，必须有）
     *
     * 注意：与 tower4aSessionCookie 不同——这个是OMMS系统专属Cookie，
     * 包含 pwdaToken/JSESSIONID/nodeInformation 等字段，4A SESSION单独无法访问OMMS。
     */
    public volatile String ommsCookie = "";

    public static final String PREF_SESSION    = "session_prefs";
    private static final String KEY_APP_CONFIG  = "app_config";
    private static final String KEY_USERID      = "userid";
    private static final String KEY_TOKEN       = "token";
    private static final String KEY_MOBILE      = "mobilephone";
    public static final String KEY_USERNAME    = "username";
    public static final String KEY_REALNAME    = "realname";

    // ---------- 铁塔4A持久化Key ----------
    public static final String KEY_TOWER4A_COOKIE = "tower4a_session_cookie";
    private static final String KEY_TOWER4A_TOKEN  = "tower4a_token";
    private static final String KEY_TOWER4A_COUNTY = "tower4a_county_code";

    // ---------- OMMS Cookie 持久化Key ----------
    private static final String KEY_OMMS_COOKIE = "omms_cookie";

    // ---------- 数运登录信息持久化Key ----------
    private static final String KEY_SHUYUN_APP_TOKEN = "shuyun_app_token";
    private static final String KEY_SHUYUN_APP_USERID = "shuyun_app_userid";
    private static final String KEY_SHUYUN_APP_IMEI = "shuyun_app_imei";
    private static final String KEY_SHUYUN_PC_TOKEN = "shuyun_pc_token";
    private static final String KEY_SHUYUN_PC_TOKEN_COOKIE = "shuyun_pc_token_cookie";
    private static final String KEY_SHUYUN_PC_IP = "shuyun_pc_ip";
    private static final String KEY_SHUYUN_CITY_AREA = "shuyun_city_area";
    private static final String KEY_COUNTY_MANAGER_CODE = "county_manager_code";

    // ---------- 门禁告警区域选择持久化Key ----------
    private static final String KEY_DOOR_ALARM_REGION = "door_alarm_region";

    // ---------- 门禁审批认证持久化Key ----------
    private static final String KEY_DOOR_APPROVAL_X_AUTH_TOKEN = "door_approval_x_auth_token";
    private static final String KEY_DOOR_APPROVAL_LOGIN_ACCT  = "door_approval_login_acct";
    private static final String KEY_DOOR_APPROVAL_ACCT_ID     = "door_approval_acct_id";

    /**
     * 将 appConfig 持久化到 SharedPreferences。
     * 在 MainActivity.buildConfig() 写入 appConfig 后立刻调用。
     */
    public void saveConfig(Context ctx) {
        PrefHelper.putString(ctx, PREF_SESSION, KEY_APP_CONFIG, appConfig, false);
    }

    /**
     * 登录成功后调用：把登录凭据（token/userid 等）写入 SharedPreferences。
     * 服务被系统重建（START_STICKY）时进程可能重启，内存变量丢失，
     * 必须持久化才能让后台接单的 Authorization 头带上正确的 token。
     */
    public void saveLogin(Context ctx) {
        PrefHelper.edit(ctx, PREF_SESSION, editor -> {
            editor.putString(KEY_USERID,      userid);
            editor.putString(KEY_TOKEN,       token);
            editor.putString(KEY_MOBILE,      mobilephone);
            editor.putString(KEY_USERNAME,    username);
            editor.putString(KEY_REALNAME,    realname);
        }, false);
    }

    /**
     * 保存数运登录信息（PC端和APP端token）
     * 在数运登录成功后调用
     * ★ 改为同步保存(commit)，确保数据真正写入磁盘后再返回
     */
    public void saveShuyunLogin(Context ctx) {
        PrefHelper.edit(ctx, PREF_SESSION, editor -> {
            editor.putString(KEY_SHUYUN_APP_TOKEN,         shuyunAppToken);
            editor.putString(KEY_SHUYUN_APP_USERID,        shuyunAppUserId);
            editor.putString(KEY_SHUYUN_APP_IMEI,          shuyunAppImei);
            editor.putString(KEY_SHUYUN_PC_TOKEN,          shuyunPcToken);
            editor.putString(KEY_SHUYUN_PC_TOKEN_COOKIE,   shuyunPcTokenCookie);
            editor.putString(KEY_SHUYUN_PC_IP,            shuyunPcIp);
            editor.putString(KEY_SHUYUN_CITY_AREA,         shuyunCityArea);
            editor.putString(KEY_COUNTY_MANAGER_CODE,       countyManagerCode);
            editor.putInt(KEY_DOOR_ALARM_REGION,            doorAlarmRegion);
        }, true);  // ★ 改为true使用commit()同步保存
    }

    /**
     * 从 SharedPreferences 恢复 appConfig 和登录凭据（服务重建/进程恢复时调用）。
     * 若 prefs 里没有，对应字段保持原值不变。
     */
    public void loadConfig(Context ctx) {
        String savedConfig = PrefHelper.getString(ctx, PREF_SESSION, KEY_APP_CONFIG, "");
        if (!savedConfig.isEmpty()) appConfig = savedConfig;

        // ★ 恢复登录凭据：服务重建后 token/userid 等内存变量会清空，
        //   acceptBill() 需要用 s.token 构建 Authorization 头，
        //   若 token 为空则服务器鉴权失败，接单被拒 ★
        String savedToken = PrefHelper.getString(ctx, PREF_SESSION, KEY_TOKEN, "");
        if (!savedToken.isEmpty()) {
            token       = savedToken;
            userid      = PrefHelper.getString(ctx, PREF_SESSION, KEY_USERID,    userid);
            mobilephone = PrefHelper.getString(ctx, PREF_SESSION, KEY_MOBILE,    mobilephone);
            username    = PrefHelper.getString(ctx, PREF_SESSION, KEY_USERNAME,  username);
            realname    = PrefHelper.getString(ctx, PREF_SESSION, KEY_REALNAME,  realname);
        }

        // 恢复数运登录信息
        String savedShuyunAppToken = PrefHelper.getString(ctx, PREF_SESSION, KEY_SHUYUN_APP_TOKEN, "");
        if (!savedShuyunAppToken.isEmpty()) {
            shuyunAppToken = savedShuyunAppToken;
            shuyunAppUserId = PrefHelper.getString(ctx, PREF_SESSION, KEY_SHUYUN_APP_USERID, shuyunAppUserId);
            shuyunAppImei = PrefHelper.getString(ctx, PREF_SESSION, KEY_SHUYUN_APP_IMEI, shuyunAppImei);
        }

        String savedShuyunPcToken = PrefHelper.getString(ctx, PREF_SESSION, KEY_SHUYUN_PC_TOKEN, "");
        if (!savedShuyunPcToken.isEmpty()) {
            shuyunPcToken = savedShuyunPcToken;
            shuyunPcTokenCookie = PrefHelper.getString(ctx, PREF_SESSION, KEY_SHUYUN_PC_TOKEN_COOKIE, shuyunPcToken);
            shuyunPcIp = PrefHelper.getString(ctx, PREF_SESSION, KEY_SHUYUN_PC_IP, shuyunPcIp);
        }

        // 恢复配置信息
        String savedCityArea = PrefHelper.getString(ctx, PREF_SESSION, KEY_SHUYUN_CITY_AREA, "");
        if (!savedCityArea.isEmpty()) {
            shuyunCityArea = savedCityArea;
        }

        String savedCountyCode = PrefHelper.getString(ctx, PREF_SESSION, KEY_COUNTY_MANAGER_CODE, "");
        if (!savedCountyCode.isEmpty()) {
            countyManagerCode = savedCountyCode;
        }

        // 恢复门禁告警区域选择
        doorAlarmRegion = PrefHelper.getInt(ctx, PREF_SESSION, KEY_DOOR_ALARM_REGION, 0);

        // 恢复门禁审批认证信息
        doorApprovalXAuthToken = PrefHelper.getString(ctx, PREF_SESSION, KEY_DOOR_APPROVAL_X_AUTH_TOKEN, "");
        doorApprovalLoginAcct  = PrefHelper.getString(ctx, PREF_SESSION, KEY_DOOR_APPROVAL_LOGIN_ACCT,  "");
        doorApprovalAcctId    = PrefHelper.getString(ctx, PREF_SESSION, KEY_DOOR_APPROVAL_ACCT_ID,    "");
        android.util.Log.d("Session", "loadConfig - doorApprovalXAuthToken: [" + doorApprovalXAuthToken + "]");
        android.util.Log.d("Session", "loadConfig - doorApprovalLoginAcct:  [" + doorApprovalLoginAcct + "]");
        android.util.Log.d("Session", "loadConfig - doorApprovalAcctId:    [" + doorApprovalAcctId + "]");

        // 恢复铁塔4A Cookie
        String saved4aCookie = PrefHelper.getString(ctx, PREF_SESSION, KEY_TOWER4A_COOKIE, "");
        if (!saved4aCookie.isEmpty()) {
            tower4aSessionCookie = saved4aCookie;
        }

        // 恢复铁塔4A Bearer Token 和县级代码
        String saved4aToken = PrefHelper.getString(ctx, PREF_SESSION, KEY_TOWER4A_TOKEN, "");
        if (!saved4aToken.isEmpty()) {
            tower4aToken = saved4aToken;
        }
        String saved4aCounty = PrefHelper.getString(ctx, PREF_SESSION, KEY_TOWER4A_COUNTY, "");
        if (!saved4aCounty.isEmpty()) {
            tower4aCountyCode = saved4aCounty;
        }

        // 恢复OMMS Cookie
        String savedOmmsCookie = PrefHelper.getString(ctx, PREF_SESSION, KEY_OMMS_COOKIE, "");
        if (!savedOmmsCookie.isEmpty()) {
            ommsCookie = savedOmmsCookie;
            android.util.Log.d("Session", "★★ loadConfig: 恢复OMMS Cookie 长度=" + ommsCookie.length()
                    + " hasJSESSIONID=" + ommsCookie.contains("JSESSIONID")
                    + " hasPwdaToken=" + ommsCookie.contains("pwdaToken")
                    + " preview=" + ommsCookie.substring(0, Math.min(100, ommsCookie.length())));
        } else {
            android.util.Log.w("Session", "★★ loadConfig: SharedPreferences中没有OMMS Cookie (ommsCookie仍为空)");
        }
    }

    /**
     * 保存铁塔4A SESSION Cookie 到 SharedPreferences
     * 在4A登录成功后由 WorkOrderFragment 调用
     */
    public void saveTower4aCookie(android.content.Context ctx) {
        PrefHelper.putString(ctx, PREF_SESSION, KEY_TOWER4A_COOKIE, tower4aSessionCookie, false);
    }

    /**
     * 保存铁塔4A Bearer Token 和县级代码
     * 用户在设置界面粘贴 Authorization 头后调用
     */
    public void saveTower4aToken(android.content.Context ctx) {
        PrefHelper.edit(ctx, PREF_SESSION, editor -> {
            editor.putString(KEY_TOWER4A_TOKEN,  tower4aToken);
            editor.putString(KEY_TOWER4A_COUNTY, tower4aCountyCode);
        }, true);
    }

    /**
     * 保存门禁审批认证信息
     * 用户在门禁数据配置行粘贴后调用
     */
    public void saveDoorApprovalAuth(android.content.Context ctx) {
        android.util.Log.d("Session", "saveDoorApprovalAuth - X-Auth-Token: [" + doorApprovalXAuthToken + "]");
        android.util.Log.d("Session", "saveDoorApprovalAuth - loginAcct:  [" + doorApprovalLoginAcct + "]");
        android.util.Log.d("Session", "saveDoorApprovalAuth - acctId:     [" + doorApprovalAcctId + "]");
        PrefHelper.edit(ctx, PREF_SESSION, editor -> {
            editor.putString(KEY_DOOR_APPROVAL_X_AUTH_TOKEN, doorApprovalXAuthToken);
            editor.putString(KEY_DOOR_APPROVAL_LOGIN_ACCT,  doorApprovalLoginAcct);
            editor.putString(KEY_DOOR_APPROVAL_ACCT_ID,    doorApprovalAcctId);
        }, true);
        android.util.Log.d("Session", "saveDoorApprovalAuth - 已调用 commit()");
    }

    /**
     * 保存OMMS Cookie 到 SharedPreferences
     * 在门禁Tab输入框内容变化时调用（对应易语言「编辑框1」内容持久化）
     */
    public void saveOmmsCookie(android.content.Context ctx) {
        // ★ 使用 commit() 同步写入，确保 Activity/Service 销毁前数据已落盘
        //   apply() 是异步的，在 finish() 后可能还没完成写入，导致 APP 重启后 Cookie 丢失
        PrefHelper.putString(ctx, PREF_SESSION, KEY_OMMS_COOKIE, ommsCookie, true);
    }

    // ---------- 我的待办 持久化 ----------
    private static final String PREF_TODO_PREFIX = "todo_prefs_";  // 后缀拼 userid，不同账号完全隔离
    private static final String KEY_TODOS        = "todos_json";

    /** 返回当前登录账号专属的 SharedPreferences 文件名；userid 为空时用 "default" 兜底。 */
    private String todoPrefName() {
        String uid = userid != null && !userid.isEmpty() ? userid : "default";
        return PREF_TODO_PREFIX + uid;
    }

    /**
     * 返回外部私有目录下的待办 JSON 文件（路径固定，卸载重装后保留）。
     * 路径：/sdcard/Android/data/com.towerops.app/files/todos_{userid}.json
     * 不需要 READ/WRITE_EXTERNAL_STORAGE 权限（Android 4.4+ 自身包名目录免权限）。
     * 若 getExternalFilesDir 返回 null（无外置存储）则返回 null。
     */
    private java.io.File getTodoExternalFile(Context ctx) {
        try {
            java.io.File dir = ctx.getApplicationContext().getExternalFilesDir(null);
            if (dir == null) return null;
            if (!dir.exists()) dir.mkdirs();
            String uid = userid != null && !userid.isEmpty() ? userid : "default";
            return new java.io.File(dir, "todos_" + uid + ".json");
        } catch (Exception e) {
            Logger.w("Session", "获取待办外部文件失败", e);
            return null;
        }
    }

    /**
     * 将待办列表序列化为 JSON，双写到：
     *   1. 外部私有目录文件（卸载重装后保留，跨版本不丢失）
     *   2. SharedPreferences（应用内快速读写，兜底）
     */
    public void saveTodos(Context ctx, List<TodoItem> todos) {
        try {
            JSONArray arr = new JSONArray();
            for (TodoItem item : todos) {
                JSONObject obj = new JSONObject();
                obj.put("id",            item.getId());
                obj.put("title",         item.getTitle());
                obj.put("content",       item.getContent() != null ? item.getContent() : "");
                obj.put("createTime",    item.getCreateTime());
                obj.put("completed",     item.isCompleted());
                obj.put("completedTime", item.getCompletedTime());
                obj.put("priority",      item.getPriority());
                obj.put("dueTime",       item.getDueTime());
                arr.put(obj);
            }
            String json = arr.toString();

            // ① 写外部文件（卸载重装不丢失）
            java.io.File extFile = getTodoExternalFile(ctx);
            if (extFile != null) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(extFile);
                     java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, "UTF-8")) {
                    osw.write(json);
                    osw.flush();
                } catch (Exception e) {
                    Logger.w("Session", "saveTodos extFile failed: " + e.getMessage());
                }
            }

            // ② 写 SharedPreferences（兜底）
            PrefHelper.putString(ctx, todoPrefName(), KEY_TODOS, json, false);
        } catch (Exception e) {
            Logger.e("Session", "saveTodos failed", e);
        }
    }

    /**
     * 从当前账号专属存储反序列化待办列表。
     * 优先读外部文件（卸载重装后可恢复），外部文件不存在时回退 SharedPreferences。
     * 在 TodoFragment.onViewCreated 时调用。
     */
    public List<TodoItem> loadTodos(Context ctx) {
        List<TodoItem> list = new ArrayList<>();
        try {
            // ① 优先读外部文件
            String json = null;
            java.io.File extFile = getTodoExternalFile(ctx);
            if (extFile != null && extFile.exists() && extFile.length() > 2) {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(extFile);
                    byte[] buf = new byte[(int) extFile.length()];
                    fis.read(buf);
                    fis.close();
                    json = new String(buf, "UTF-8");
                    Logger.d("Session", "loadTodos from extFile: " + extFile.getAbsolutePath());
                } catch (Exception e) {
                    Logger.w("Session", "loadTodos extFile read failed: " + e.getMessage());
                    json = null;
                }
            }

            // ② 外部文件不可用，回退 SharedPreferences
            if (json == null || json.isEmpty()) {
                json = PrefHelper.getString(ctx, todoPrefName(), KEY_TODOS, "");
                if (json != null && !json.isEmpty()) {
                    Logger.d("Session", "loadTodos from SharedPreferences (extFile unavailable)");
                }
            }

            if (json == null || json.isEmpty()) return list;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                TodoItem item = new TodoItem();
                item.setId(obj.optString("id", ""));
                item.setTitle(obj.optString("title", ""));
                item.setContent(obj.optString("content", ""));
                item.setCreateTime(obj.optLong("createTime", 0));
                item.setCompleted(obj.optBoolean("completed", false));
                item.setCompletedTime(obj.optLong("completedTime", 0));
                item.setPriority(obj.optInt("priority", 0));
                item.setDueTime(obj.optLong("dueTime", 0));
                list.add(item);
            }
        } catch (Exception e) {
            Logger.e("Session", "loadTodos failed", e);
        }
        return list;
    }

    // ---------- 并发计数（用 synchronized 保护）----------
    private int runningThreads = 0;
    private int finishedCount  = 0;
    private int totalCount     = 0;

    public synchronized void resetProgress(int total) {
        this.totalCount     = total;
        this.runningThreads = 0;
        this.finishedCount  = 0;
    }

    public synchronized boolean tryAcquireSlot(int maxSlots) {
        if (runningThreads < maxSlots) { runningThreads++; return true; }
        return false;
    }

    public synchronized void releaseSlot() {
        runningThreads--;
        finishedCount++;
    }

    public synchronized boolean allDone() {
        return finishedCount >= totalCount;
    }

    public synchronized int getFinished() { return finishedCount; }
    public synchronized int getTotal()    { return totalCount; }
}

package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * 门禁系统API —— 对应易语言蓝牙进站实时监控功能
 *
 * 封装了三个核心接口：
 * 1. 获取门禁告警数据（铁塔APP接口）
 * 2. 获取数运门禁蓝牙记录（数运接口）
 * 3. 远程查询/下发开门指令（OMMS接口）
 */
public class AccessControlApi {

    private static final String TAG = "AccessControlApi";

    // =====================================================================
    // 1. 铁塔APP - 门禁告警列表
    // =====================================================================

    /**
     * 获取门禁告警数据
     * 对应易语言：获取门禁告警数据()
     */
    public static String getAlarmList(String userid, String token) {
        try {
            long ts = System.currentTimeMillis() / 1000L;
            String url = "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service"
                    + "?porttype=FSU_ALARM_LIST&v=" + LoginApi.V + "&userid=" + userid + "&c=0";
            String post = "start=1&limit=50&c_timestamp=" + ts
                    + "&c_account=" + userid
                    + "&c_sign=2800ADE8BBBB67247CCFB6FA0E37C7A7"
                    + "&upvs=2025-03-18-ccssoft"
                    + "&provinceId=&cityId=&areaId=&alarmlevel=&begintimetype="
                    + "&stationcode=&alarmname=%E9%97%A8";  // 门

            String headers = "Content-Type: application/x-www-form-urlencoded\n"
                    + "Authorization: " + token + "\n"
                    + "User-Agent: Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36\n"
                    + "Accept: application/json";

            return HttpUtil.post(url, post, headers, null);
        } catch (Exception e) {
            android.util.Log.e(TAG, "getAlarmList failed", e);
            return "";
        }
    }

    // =====================================================================
    // 2. 数运 - 蓝牙进站记录
    // =====================================================================

    /**
     * 获取数运门禁蓝牙记录
     * 对应易语言：获取数运门禁数据()
     *
     * 认证方式：与 ShuyunApi.buildCountyApiHeader() 完全一致：
     *   Authorization: {shuyunPcToken}  （注意：无 Bearer 前缀）
     *   Cookie: towerNumber-Token={shuyunPcTokenCookie}
     *
     * 需要先在「数运工单」Tab完成PC端登录
     *
     * @param stationFilter 站点名过滤（空字符串表示不过滤）
     */
    public static String getLanyaInfo(String stationFilter) {
        try {
            Session s = Session.get();
            // token 空值检查（在调用前就能给出明确提示）
            if (s.shuyunPcToken.isEmpty() && s.shuyunPcTokenCookie.isEmpty()) {
                android.util.Log.w(TAG, "getLanyaInfo: shuyunPcToken 和 shuyunPcTokenCookie 均为空，未登录数运PC端");
                return "";
            }

            String url = "http://zjtowercom.cn:8998/api/flowable/clCon/lanyainfo";
            // 与易语言原版一致：city="温州市"，station_name 传空获取全量，limit=200
            String post = "{\"page\":1,\"limit\":200,"
                    + "\"created_start\":\"2024-05-01 00:00:00\","
                    + "\"created_end\":\"2030-06-01 00:00:00\","
                    + "\"city\":\"温州市\","
                    + "\"station_name\":\"" + stationFilter + "\","
                    + "\"user_name\":\"\"}";

            String token = s.shuyunPcToken.isEmpty() ? s.shuyunPcTokenCookie : s.shuyunPcToken;
            String cookieToken = s.shuyunPcTokenCookie.isEmpty() ? token : s.shuyunPcTokenCookie;
            // ★ Cookie 只需要 towerNumber-Token，去掉硬编码的 sysName/SECKEY 等字段
            //   原代码里有 sysName=%E4%BC%8A%E4%B8%96%E8%B1%AA（伊世豪）是别人的账号，会导致服务端 session 校验失败
            String cookie = "towerNumber-Token=" + cookieToken;

            // ★ 不加 Accept-Encoding：OkHttp 自动处理 gzip，手动加会关闭自动解压导致乱码
            String headers = "Accept: application/json, text/plain, */*\n"
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

            android.util.Log.d(TAG, "getLanyaInfo token=" + (token.length() > 20 ? token.substring(0, 20) + "..." : token)
                    + " cookieToken=" + (cookieToken.length() > 20 ? cookieToken.substring(0, 20) + "..." : cookieToken));
            String resp = HttpUtil.post(url, post, headers, cookie);
            android.util.Log.d(TAG, "getLanyaInfo resp len=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(200, resp.length())));
            return resp;
        } catch (Exception e) {
            android.util.Log.e(TAG, "getLanyaInfo failed", e);
            return "";
        }
    }

    // =====================================================================
    // 2b. 数运蓝牙记录 - 全量查询（供门禁数据子Tab匹配）
    //     返回：List<LanyaRecord>，每条含站名、站码（可能为空）、进出时间
    //     支持翻页，自动合并所有页结果
    // =====================================================================

    /** 单条蓝牙开门记录 */
    public static class LanyaRecord {
        public String stationName;   // 站名
        public String stationCode;   // 站址编码（可能为空）
        public String openTime;      // 匹配用时间（优先 time_of_bluetooth_open_door → time_of_going_in → come_out_time）
        public String comeInTime;    // 进站时间原始值（time_of_going_in 或 come_in_time）
        public String comeOutTime;   // 出站时间原始值（come_out_time）
        public String bluetoothOpenTime; // 蓝牙开门时间（time_of_bluetooth_open_door）
    }

    /**
     * 全量拉取蓝牙开门记录，自动翻页合并。
     * 最多拉 100 页（每页200条），超出则停止（确保不遗漏）。
     *
     * @param startDate 查询起始日期，格式 "yyyy-MM-dd"，为空则取30天前
     * @param endDate   查询截止日期，格式 "yyyy-MM-dd"，为空则取今天
     * @param cityName  区县名称（如"平阳县""泰顺县"），用于按区县筛选蓝牙记录
     */
    public static java.util.List<LanyaRecord> getAllLanyaRecords(String startDate, String endDate, String cityName) {
        java.util.List<LanyaRecord> all = new java.util.ArrayList<>();
        Session s = Session.get();
        if (s.shuyunPcToken.isEmpty() && s.shuyunPcTokenCookie.isEmpty()) return all;

        // 日期范围处理：将 "yyyy-MM-dd" 转为 "yyyy-MM-dd HH:mm:ss"
        String createdStart, createdEnd;
        if (startDate == null || startDate.isEmpty()) {
            createdStart = "2024-05-01 00:00:00";
        } else {
            // 支持 "yyyy-MM-dd" 和 "yyyy-MM-dd HH:mm:ss" 两种格式
            createdStart = startDate.length() == 10 ? startDate + " 00:00:00" : startDate;
        }
        if (endDate == null || endDate.isEmpty()) {
            createdEnd = "2030-06-01 00:00:00";
        } else {
            createdEnd = endDate.length() == 10 ? endDate + " 00:00:00" : endDate;
        }

        String token      = s.shuyunPcToken.isEmpty() ? s.shuyunPcTokenCookie : s.shuyunPcToken;
        String cookieToken = s.shuyunPcTokenCookie.isEmpty() ? token : s.shuyunPcTokenCookie;
        String cookie     = "towerNumber-Token=" + cookieToken;
        String headers    = "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + token + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/accessControl/bluetoothRecords\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";
        String url = "http://zjtowercom.cn:8998/api/flowable/clCon/lanyainfo";

        android.util.Log.d(TAG, "getAllLanyaRecords dateRange=" + createdStart + " ~ " + createdEnd + " cityName=" + cityName);
        android.util.Log.d(TAG, "getAllLanyaRecords token_prefix=" + (token.length() > 30 ? token.substring(0, 30) : token));
        android.util.Log.d(TAG, "getAllLanyaRecords cookie=" + cookie.substring(0, Math.min(60, cookie.length())));

        int page = 1;
        int maxPage = 100;
        while (page <= maxPage) {
            String stationFilter = (cityName != null && !cityName.isEmpty()) ? cityName : "";
            String post = "{\"page\":" + page + ",\"limit\":200,"
                    + "\"created_start\":\"" + createdStart + "\","
                    + "\"created_end\":\"" + createdEnd + "\","
                    + "\"city\":\"温州市\","
                    + "\"station_name\":\"" + stationFilter + "\","
                    + "\"user_name\":\"\","
                    + "\"del_flag\":\"0\","
                    + "\"open_door_status\":\"0\"}";

            android.util.Log.d(TAG, "getAllLanyaRecords p" + page + " POST=" + post);

            try {
                String resp = HttpUtil.post(url, post, headers, cookie);
                android.util.Log.d(TAG, "getAllLanyaRecords p" + page + " respLen=" + (resp == null ? "null" : resp.length())
                        + " preview=" + (resp == null ? "null" : resp.substring(0, Math.min(500, resp.length()))));
                if (resp == null || resp.isEmpty()) break;
                org.json.JSONObject obj = new org.json.JSONObject(resp);
                org.json.JSONArray arr = null;
                org.json.JSONObject data = obj.optJSONObject("data");
                android.util.Log.d(TAG, "getAllLanyaRecords p" + page + " dataObj=" + (data != null ? data.toString().substring(0, Math.min(300, data.toString().length())) : "null"));
                if (data != null) {
                    arr = data.optJSONArray("rows");
                    if (arr == null) arr = data.optJSONArray("list");
                    if (arr == null) arr = data.optJSONArray("records");
                }
                if (arr == null) arr = obj.optJSONArray("data");
                if (arr == null || arr.length() == 0) break;

                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject row = arr.getJSONObject(i);
                    LanyaRecord rec = new LanyaRecord();
                    // 站名
                    String[] nameKeys = {"station_name","stationName","siteName","site_name","name","sname","st_name","stName"};
                    for (String k : nameKeys) {
                        String v = row.optString(k,"").trim();
                        if (!v.isEmpty() && !v.equals("null")) { rec.stationName = v; break; }
                    }
                    if (rec.stationName == null) rec.stationName = "";
                    // 站码
                    String[] codeKeys = {"station_code","stationCode","stCode","st_code","site_code","siteCode","code"};
                    for (String k : codeKeys) {
                        String v = row.optString(k,"").trim();
                        if (!v.isEmpty() && !v.equals("null")) { rec.stationCode = v; break; }
                    }
                    if (rec.stationCode == null) rec.stationCode = "";
                    // 进出时间（JSON实际返回字段名）
                    rec.bluetoothOpenTime = row.optString("time_of_bluetooth_open_door", "").trim();
                    if ("null".equals(rec.bluetoothOpenTime)) rec.bluetoothOpenTime = "";

                    // 进站时间：time_of_going_in（实际字段名）→ come_in_time（旧名）→ in_time
                    rec.comeInTime = row.optString("time_of_going_in", "").trim();
                    if (rec.comeInTime.isEmpty() || "null".equals(rec.comeInTime)) {
                        rec.comeInTime = row.optString("come_in_time",
                                row.optString("comeInTime",
                                row.optString("in_time", ""))).trim();
                        if ("null".equals(rec.comeInTime)) rec.comeInTime = "";
                    }

                    // 出站时间：come_out_time（实际字段名存在）
                    rec.comeOutTime = row.optString("come_out_time",
                            row.optString("comeOutTime",
                            row.optString("out_time", ""))).trim();
                    if ("null".equals(rec.comeOutTime)) rec.comeOutTime = "";

                    // openTime 优先级：蓝牙开门时间 > 进站时间 > 出站时间
                    if (!rec.bluetoothOpenTime.isEmpty()) {
                        rec.openTime = rec.bluetoothOpenTime;
                    } else if (!rec.comeInTime.isEmpty()) {
                        rec.openTime = rec.comeInTime;
                    } else {
                        rec.openTime = rec.comeOutTime;
                    }
                    if (rec.stationName.isEmpty() && rec.openTime.isEmpty()) continue;
                    all.add(rec);
                }
                // 如果本页不足200条，说明已是最后一页
                if (arr.length() < 200) break;
                page++;
            } catch (Exception e) {
                android.util.Log.e(TAG, "getAllLanyaRecords p" + page + " err=" + e.getMessage());
                break;
            }
        }
        android.util.Log.d(TAG, "getAllLanyaRecords total=" + all.size() + " pages=" + (page));
        return all;
    }

    // =====================================================================
    // 3. OMMS - 门禁开门操作（三步流程）
    // =====================================================================

    /**
     * 门禁 ViewState 缓存（多线程只读，主线程写）
     * ★ 初始值 j_id25：来自用户 2026-03-28 真实抓包，OMMS listFsu 页面 AJAX 请求中的 ViewState 实际值
     *   j_id1 是框架内部值，不能用于 AJAX 请求；j_id25 是业务页面真实值（比 j_id3 更新）
     */
    private static volatile String cachedViewState = "j_id25";

    public static String getCachedViewState() { return cachedViewState; }

    /**
     * 由外部（如 TowerLoginApi.autoGetOmmsCookie）在成功获取 OMMS Cookie 后
     * 立即调用，将从 listFsu.xhtml 提取到的真实 ViewState 写入缓存
     */
    public static void setCachedViewState(String viewState) {
        if (viewState != null && !viewState.isEmpty()) {
            cachedViewState = viewState;
            android.util.Log.d("AccessControlApi", "setCachedViewState=" + viewState);
        }
    }

    /**
     * 查询 FSU 门禁列表（OMMS AJAX 接口）
     * 基于用户真实抓包的完整 POST body（2026-03-26 最新抓包）
     *
     * 用途：根据站点名搜索，获取 fsuid（objid）和 doorId（门禁id），
     *       再传给 getRemoteOpenTime / doOpenDoor 使用
     *
     * ★ 触发字段为 queryForm:j_id156（不是 j_id155，来自最新抓包）
     * ★ currPageObjId=1（不是 0）
     * ★ 末尾加 AJAX:EVENTS_COUNT=1
     *
     * @param stationName 站点名关键词（空字符串查全量，建议传入站点名精确匹配）
     * @return OMMS 返回的 HTML 片段（AJAX 响应），包含门禁列表数据
     */
    public static String queryFsuList(String stationName) {
        try {
            String url = "http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml";
            // 完整 POST body，来自用户真实抓包（2026-03-26，Chrome，PC端登录后）
            // queryForm:queryStaStatusSelId=2 → 只查"已投产"站点
            // queryForm:pageSizeText=35       → 每页35条
            // queryForm:j_id156              → 查询按钮触发字段（最新抓包）
            // stationName 支持传空字符串（查全量）或传入站点名
            String encodedStation = stationName.isEmpty() ? "" : java.net.URLEncoder.encode(stationName, "UTF-8");
            String post = buildFsuQueryPost(encodedStation, cachedViewState);

            String headers = buildOmmsPostHeaders();
            // ★ 使用用户粘贴的 OMMS 专属 Cookie
            String cookie = Session.get().ommsCookie;
            String resp = HttpUtil.post(url, post, headers, cookie);
            android.util.Log.d(TAG, "queryFsuList stationName=" + stationName
                    + " respLen=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(300, resp.length())));
            return resp;
        } catch (Exception e) {
            android.util.Log.e(TAG, "queryFsuList failed", e);
            return "";
        }
    }



    /**
     * 刷新 OMMS 门禁页面的 ViewState
     * 对应易语言：子程序_更新门禁页面状态值()
     *
     * ★★★ 易语言原版分析：
     *   - 局_提交cookie 变量声明后未赋值（默认为空字符串！）
     *   - 也就是说：GET listFsu.xhtml 时根本不带 Cookie
     *   - 易语言的 HTTP 对象内部持有已登录的 OMMS Session（JSESSIONID），
     *     请求会自动带上，所以能返回正常页面
     *   - 取不到 ViewState 就默认用 "j_id1"
     *
     * ★★★ Java 对应策略（三步降级）：
     *
     *   Step1: 用 ommsCookie（已有）GET listFsu.xhtml → 解析 ViewState
     *          成功 → 返回 "OK"
     *
     *   Step2: ommsCookie 为空/无效 → 用 tower4aSessionCookie（4A SESSION）
     *          直接 GET listFsu.xhtml → OMMS 服务端 SSO 认出4A SESSION
     *          → 返回完整页面并 Set-Cookie JSESSIONID → 解析 ViewState
     *          → 同时把拿到的 JSESSIONID 写入 ommsCookie（for 后续请求）
     *          成功 → 返回 "OK_4A"
     *
     *   Step3: 两步都失败 → 默认 "j_id1"（与易语言完全一致）
     *          → 返回 "NOT_FOUND"（使用默认值继续）
     *
     * @return 诊断码：
     *   "OK"             - 用 ommsCookie 成功拿到 ViewState
     *   "OK_4A"          - 用 tower4aSessionCookie SSO 成功拿到 ViewState
     *   "NOT_FOUND"      - 未找到 ViewState，使用默认 j_id1
     *   "LOGIN_REDIRECT" - 两步都被踢回登录页（Session 全部无效）
     *   "EMPTY_RESPONSE" - 响应为空
     *   "ERROR:xxx"      - 异常
     */
    public static String refreshViewState() {
        try {
            String url = "http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml";
            Session s = Session.get();

            // ─── Step 1：用 ommsCookie GET listFsu.xhtml ──────────────────────
            String ommsCookie = s.ommsCookie;
            android.util.Log.d(TAG, "refreshViewState ommsCookie len=" + ommsCookie.length()
                    + " hasPwdaToken=" + ommsCookie.contains("pwdaToken")
                    + " hasJSESSIONID=" + ommsCookie.contains("JSESSIONID")
                    + " hasNodeInfo=" + ommsCookie.contains("nodeInformation"));

            if (!ommsCookie.isEmpty()) {
                String html = HttpUtil.get(url, buildOmmsGetHeaders(), ommsCookie);
                android.util.Log.d(TAG, "Step1(ommsCookie) GET len=" + html.length()
                        + " preview100=" + html.substring(0, Math.min(100, html.length())));

                if (!html.isEmpty()) {
                    boolean isLogin = html.contains("doPrevLogin") || html.contains("uac/login")
                            || html.contains("loginpage") || html.contains("请先登录");
                    if (!isLogin) {
                        String vs = extractViewState(html);
                        if (vs != null && !vs.isEmpty()) {
                            cachedViewState = vs;
                            android.util.Log.d(TAG, "Step1 OK ViewState=" + vs.substring(0, Math.min(30, vs.length())));
                            return "OK";
                        }
                        // 页面正常但没有 ViewState（frameset 或 ommsCookie 含4A Cookie导致OMMS没完成SSO）
                        // ★ 不要直接 return NOT_FOUND，继续走 Step2 用4A SESSION重新做SSO
                        android.util.Log.d(TAG, "Step1: no ViewState in ommsCookie response, trying Step2(4A SSO)");
                    } else {
                        android.util.Log.w(TAG, "Step1: ommsCookie 被重定向到登录页，尝试Step2(4A SSO)");
                    }
                }
            }

            // ─── Step 2：用 tower4aSessionCookie 做 SSO ───────────────────────
            // 对应易语言行为：局_提交cookie 为空时，OMMS 服务端通过 SSO Token 完成认证
            // Java 里我们直接带4A的 SESSION Cookie 访问 listFsu.xhtml，
            // OMMS 服务端会识别4A SESSION 并完成 SSO，返回正常页面（同时 Set-Cookie JSESSIONID）
            String tower4aCookie = s.tower4aSessionCookie;
            if (tower4aCookie == null || tower4aCookie.isEmpty()) {
                android.util.Log.w(TAG, "Step2: tower4aSessionCookie 也为空，无法 SSO");
                // 两步都没 Cookie：使用默认值 j_id1（与易语言 集_门禁ViewState = "j_id1" 一致）
                cachedViewState = "j_id1";
                return "NOT_FOUND";
            }

            android.util.Log.d(TAG, "Step2: using tower4aSessionCookie len=" + tower4aCookie.length()
                    + " for OMMS SSO GET");

            // ★ Step2 只用 tower4aSessionCookie，不混入 ommsCookie
            // 原因：ommsCookie 里含有4A域的 Cookie（SESSION/Tnuocca等），混入后 OMMS 无法正确触发 SSO
            String combinedCookie = tower4aCookie;

            // 用 Referer: layout/index.xhtml（与易语言 ADD_协议头.添加("Referer",...) 一致）
            String getHeadersWith4A = "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\n"
                    + "Accept-Language: zh-CN,zh;q=0.9\n"
                    + "Cache-Control: no-cache\n"
                    + "Connection: keep-alive\n"
                    + "Host: omms.chinatowercom.cn:9000\n"
                    + "Pragma: no-cache\n"
                    + "Referer: http://omms.chinatowercom.cn:9000/layout/index.xhtml\n"
                    + "Upgrade-Insecure-Requests: 1\n"
                    + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Mobile Safari/537.36";

            String html2 = HttpUtil.get(url, getHeadersWith4A, combinedCookie);
            android.util.Log.d(TAG, "Step2(4A SSO) GET len=" + html2.length()
                    + " preview100=" + html2.substring(0, Math.min(100, html2.length())));

            if (html2.isEmpty()) return "EMPTY_RESPONSE";

            boolean isLogin2 = html2.contains("doPrevLogin") || html2.contains("uac/login")
                    || html2.contains("loginpage") || html2.contains("请先登录");
            if (isLogin2) {
                android.util.Log.w(TAG, "Step2: 4A SSO 也被踢回登录页");
                return "LOGIN_REDIRECT";
            }

            String vs2 = extractViewState(html2);
            if (vs2 != null && !vs2.isEmpty()) {
                cachedViewState = vs2;
                android.util.Log.d(TAG, "Step2(4A SSO) OK ViewState=" + vs2.substring(0, Math.min(30, vs2.length())));
                return "OK_4A";
            }

            // 页面正常但没有 ViewState → 使用默认 j_id1（与易语言一致）
            android.util.Log.w(TAG, "Step2: page OK but no ViewState, setting default j_id1");
            cachedViewState = "j_id1";
            return "NOT_FOUND";

        } catch (Exception e) {
            android.util.Log.e(TAG, "refreshViewState failed", e);
            return "ERROR:" + e.getMessage();
        }
    }

    /**
     * 构建 FSU 查询的完整 AJAX POST body
     * 来源：用户真实抓包（2026-03-26，Chrome 146，PC端 OMMS 登录后）
     *
     * ★ 关键字段说明：
     *   - AJAXREQUEST=_viewRoot        → JSF AJAX 触发标识
     *   - queryForm:queryStaStatusSelId=2 → 只查"已投产"状态
     *   - currPageObjId=1              → 第1页（抓包值）
     *   - javax.faces.ViewState        → 当前缓存的 ViewState
     *   - queryForm:j_id156            → 查询按钮触发字段（最新抓包，非 j_id155）
     *   - AJAX:EVENTS_COUNT=1          → JSF AJAX 事件计数
     *   - selectProvFlag/lon/lat/fsuNameHid/innerIpName/aid/aname/selStatusHid/registstatusId
     *     → 页面当前已加载数据的状态字段，服务端用于定位 ViewState 对应的数据集
     *
     * @param encodedStationName URL 编码后的站点名（空字符串 = 不过滤）
     * @param viewState          当前缓存的 ViewState 值
     * @return 完整的 application/x-www-form-urlencoded POST body
     */
    private static String buildFsuQueryPost(String encodedStationName, String viewState) {
        // 2026-03-28 true packet alignment: removed hardcoded page state fields, fixed j_id numbers
        return "AJAXREQUEST=_viewRoot"
                + "&queryForm%3AunitHidden="
                + "&queryForm%3AqueryFlag=queryFlag"
                + "&queryForm%3AnameText="
                + "&queryForm%3AfsuidText="
                + "&queryForm%3AqueryFsuClass_hiddenValue="
                + "&queryForm%3AregiststatusText_hiddenValue="
                + "&queryForm%3Aj_id59="
                + "&queryForm%3Aj_id63="
                + "&queryForm%3AqueryFactoryNameSelId_hiddenValue="
                + "&queryForm%3AqueryWireLessSelId_hiddenValue="
                + "&queryForm%3AqueryStaStatusSelId_hiddenValue=2"
                + "&queryForm%3AqueryStaStatusSelId=2"
                + "&queryForm%3AqueryIfEntranceSelId_hiddenValue="
                + "&queryForm%3AqueryStaTypeSelId_hiddenValue="
                + "&queryForm%3AquerySiteSourceSelId_hiddenValue="
                + "&queryForm%3AqueryExistsAir_hiddenValue="
                + "&queryForm%3AqueryDWCompanyName="
                + "&queryForm%3Aj_id91="
                + "&queryForm%3Aj_id95="
                + "&queryForm%3AqueryHardwareFactoryNameSelId_hiddenValue="
                + "&queryForm%3Aj_id102="
                + "&queryForm%3AqueryFsuDeviceid="
                + "&queryForm%3Aj_id109="
                + "&queryForm%3Aj_id113="
                + "&queryForm%3Aj_id117="
                + "&queryForm%3Aj_id121="
                + "&queryForm%3Aquerytext="
                + "&queryForm%3Aquerytext2="
                + "&queryForm%3AqueryCard_hiddenValue="
                + "&queryForm%3AqueryCsta1_hiddenValue="
                + "&queryForm%3AqueryCsta2_hiddenValue="
                + "&queryForm%3AqueryStationName=" + encodedStationName
                + "&queryForm%3AqueryProjectCode="
                + "&queryForm%3AqueryProjectName="
                + "&queryForm%3Aj_id143="
                + "&queryForm%3AqueryCrewAreaId="
                + "&queryForm%3AqueryCrewAreaName="
                + "&queryForm%3Aj_id150="
                + "&queryForm%3AcountSizeText="
                + "&queryForm%3AcurrPageObjId=0"
                + "&queryForm%3ApageSizeText=10000"
                + "&queryForm%3ApanelOpenedState="
                + "&queryForm=queryForm"
                + "&autoScroll="
                + "&javax.faces.ViewState=" + viewState
                + "&queryForm%3Aj_id158=queryForm%3Aj_id158"
                + "&AJAX%3AEVENTS_COUNT=1&";
    }

    /**
     * 从 HTML 或 JSF AJAX XML 中提取 ViewState 值
     * 兼容以下格式：
     *   1. <input type="hidden" name="javax.faces.ViewState" id="javax.faces.ViewState" value="j_id3:xxx"/>
     *   2. <state>j_id3:xxx</state>
     *   3. <![CDATA[j_id3:xxx]]>（ViewState 在 CDATA 里）
     */
    private static String extractViewState(String content) {
        if (content == null || content.isEmpty()) return null;
        // 格式1：标准 HTML hidden input
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
                "javax\\.faces\\.ViewState[^>]*value=\"([^\"]+)\"").matcher(content);
        if (m1.find()) return m1.group(1);

        // 格式2：AJAX XML <state>...</state>
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                "<state>([^<]+)</state>").matcher(content);
        if (m2.find()) {
            String val = m2.group(1).trim();
            if (!val.isEmpty()) return val;
        }

        // 格式3：AJAX XML CDATA，且包含 j_id
        java.util.regex.Matcher m3 = java.util.regex.Pattern.compile(
                "<!\\[CDATA\\[(j_id[^\\]]+)\\]\\]>").matcher(content);
        if (m3.find()) {
            String val = m3.group(1).trim();
            if (!val.isEmpty()) return val;
        }

        // 格式4：简单字符串查找（兼容旧格式）
        String marker = "javax.faces.ViewState\" value=\"";
        int idx = content.indexOf(marker);
        if (idx >= 0) {
            int s = idx + marker.length();
            int e = content.indexOf("\"", s);
            if (e > s) return content.substring(s, e);
        }
        return null;
    }

    /**
     * 用 FSU ID（14位，第7-9位=438）查对应门禁ID（14位，第7-9位=499）
     *
     * 流程：POST listEntrance.xhtml 传 fsuid（运维ID），从返回HTML中找第7-9位=499的运维ID
     *
     * 抓包来源（用户2026-04-05真实抓包）：
     *   接口：listEntrance.xhtml
     *   POST body 中 queryForm:j_id31=站名（URL编码）
     *   返回HTML中 checkbox的id属性 = 运维ID（14位）
     *   门禁ID特征：第7-9位固定为"499"
     *   FSU ID特征：第7-9位固定为"438"
     *
     * ★ 本方法传入 FSU ID（438），通过同站门禁列表页找 checkbox id 中499特征的那个 = 门禁ID
     *
     * @param fsuid  FSU ID（col[16] 设备ID，14位，第7-9位=438）
     * @return       门禁ID（14位，第7-9位=499），找不到返回空串
     */
    public static String queryEntranceDoorId(String fsuid) {
        if (fsuid == null || fsuid.isEmpty()) return "";
        try {
            String url = "http://omms.chinatowercom.cn:9000/business/resMge/devMge/listEntrance.xhtml";
            String cookie = Session.get().ommsCookie;

            // POST body：传入 fsuid 作为查询条件（queryForm:j_id11 = FSU ID）
            // 来自用户抓包：queryForm:j_id31=站名，这里我们用 fsuid 字段（j_id11或j_id15等），
            // 参照抓包结构，用j_id11传fsuid（OMMS门禁管理页FSU运维ID字段）
            String encodedFsuid = java.net.URLEncoder.encode(fsuid, "UTF-8");
            String post = "AJAXREQUEST=_viewRoot"
                    + "&queryForm=queryForm"
                    + "&queryForm%3AunitHidden="
                    + "&queryForm%3AqueryRoomIdHidden="
                    + "&queryForm%3AqueryStationIdHidden="
                    + "&queryForm%3Aj_id11=" + encodedFsuid   // ★ FSU运维ID
                    + "&queryForm%3Aj_id15="
                    + "&queryForm%3Aj_id19="
                    + "&queryForm%3Aj_id23="
                    + "&queryForm%3Aj_id27="
                    + "&queryForm%3Aj_id31="
                    + "&queryForm%3AquerySiteSourceCode="
                    + "&queryForm%3AcurrPageObjId=1"
                    + "&queryForm%3ApageSizeText=35"
                    + "&javax.faces.ViewState=" + cachedViewState
                    + "&queryForm%3Aj_id39=queryForm%3Aj_id39"
                    + "&AJAX%3AEVENTS_COUNT=1&";

            String headers = buildOmmsPostHeaders()
                    .replace("listFsu.xhtml", "listEntrance.xhtml");

            String resp = HttpUtil.post(url, post, headers, cookie);
            android.util.Log.d(TAG, "queryEntranceDoorId fsuid=" + fsuid
                    + " respLen=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(500, resp.length())));

            if (resp.isEmpty() || resp.contains("doPrevLogin") || resp.contains("uac/login")) {
                return "";
            }

            // ★ 从 checkbox 的 id 或 value 中找14位数字、第7-9位=499 的门禁ID
            // 格式：<input type="checkbox" id="33032649900229" ...> 或 value="33032649900229"
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(?:id|value)=\"(\\d{14})\"").matcher(resp);
            while (m.find()) {
                String candidate = m.group(1);
                // 第7-9位（0-indexed 6-8）=499
                if (candidate.length() == 14 && candidate.substring(6, 9).equals("499")) {
                    android.util.Log.d(TAG, "queryEntranceDoorId 找到门禁ID=" + candidate + " fsuid=" + fsuid);
                    return candidate;
                }
            }

            // 备选：直接在HTML文本中搜索499特征的14位数字
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                    "\\b(\\d{6}499\\d{5})\\b").matcher(resp);
            if (m2.find()) {
                String candidate = m2.group(1);
                android.util.Log.d(TAG, "queryEntranceDoorId [备选] 找到门禁ID=" + candidate + " fsuid=" + fsuid);
                return candidate;
            }

            android.util.Log.w(TAG, "queryEntranceDoorId 未找到499特征门禁ID, fsuid=" + fsuid);
        } catch (Exception e) {
            android.util.Log.e(TAG, "queryEntranceDoorId failed fsuid=" + fsuid, e);
        }
        return "";
    }

    /**
     * 查询远程开门时间记录（完整流程：FSU ID → 门禁ID → 远程开门记录）
     *
     * 接口：POST listEntrance.xhtml（远程开门记录查询）
     *
     * ★ 两个参数均为14位数字：
     *   - FSU ID  ：第7-9位=438（col[16] 设备ID，已从告警数据直接获得）
     *   - 门禁ID  ：第7-9位=499（通过 queryEntranceDoorId() 查 listEntrance.xhtml 获得）
     *
     * POST body 参数（来自用户抓包）：
     *   fsuId={FSU ID}      门禁控制器运维ID（438特征）
     *   doorId={门禁ID}     门禁设备运维ID（499特征）
     *
     * @param fsuid  FSU ID（14位，第7-9位=438）
     * @return       最近一条远程开门时间字符串（yyyy-MM-dd HH:mm:ss），失败返回空串
     */
    public static String getRemoteOpenTimeByFsuid(String fsuid) {
        if (fsuid == null || fsuid.isEmpty()) return "";
        try {
            // Step 1：用 FSU ID 查门禁ID（499特征）
            String doorId = queryEntranceDoorId(fsuid);
            android.util.Log.d(TAG, "getRemoteOpenTimeByFsuid fsuid=" + fsuid + " doorId=" + doorId);

            if (doorId.isEmpty()) {
                android.util.Log.w(TAG, "getRemoteOpenTimeByFsuid 未找到门禁ID，直接用fsuid查");
                doorId = fsuid; // 兜底：有些站FSU就是门禁
            }

            // Step 2：POST 查询远程开门时间记录
            // 接口：listEntrance.xhtml AJAX，传 fsuId 和 doorId
            String url = "http://omms.chinatowercom.cn:9000/business/resMge/devMge/listEntrance.xhtml";
            String cookie = Session.get().ommsCookie;
            String encodedFsuid = java.net.URLEncoder.encode(fsuid, "UTF-8");
            String encodedDoorId = java.net.URLEncoder.encode(doorId, "UTF-8");
            String post = "AJAXREQUEST=_viewRoot"
                    + "&queryForm=queryForm"
                    + "&queryForm%3AunitHidden="
                    + "&queryForm%3AqueryRoomIdHidden="
                    + "&queryForm%3AqueryStationIdHidden="
                    + "&queryForm%3Aj_id11="
                    + "&queryForm%3Aj_id15="
                    + "&queryForm%3Aj_id19="
                    + "&queryForm%3Aj_id23="
                    + "&queryForm%3Aj_id27="
                    + "&queryForm%3Aj_id31="
                    + "&queryForm%3AquerySiteSourceCode="
                    + "&queryForm%3AcurrPageObjId=1"
                    + "&queryForm%3ApageSizeText=35"
                    + "&fsuId=" + encodedFsuid
                    + "&doorId=" + encodedDoorId
                    + "&javax.faces.ViewState=" + cachedViewState
                    + "&queryForm%3Aj_id39=queryForm%3Aj_id39"
                    + "&AJAX%3AEVENTS_COUNT=1&";

            String headers = buildOmmsPostHeaders()
                    .replace("listFsu.xhtml", "listEntrance.xhtml");
            String resp = HttpUtil.post(url, post, headers, cookie);
            android.util.Log.d(TAG, "getRemoteOpenTimeByFsuid step2 respLen=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(500, resp.length())));

            if (resp.isEmpty() || resp.contains("doPrevLogin")) return "";

            // 更新 ViewState
            String newVs = extractViewState(resp);
            if (newVs != null && !newVs.isEmpty()) cachedViewState = newVs;

            // 从响应中提取时间
            String t = extractTimeFromHtml(resp);
            if (!t.isEmpty()) {
                android.util.Log.d(TAG, "getRemoteOpenTimeByFsuid 命中: " + t);
                return t;
            }

            android.util.Log.w(TAG, "getRemoteOpenTimeByFsuid 未找到时间, fsuid=" + fsuid + " doorId=" + doorId);
        } catch (Exception e) {
            android.util.Log.e(TAG, "getRemoteOpenTimeByFsuid failed", e);
        }
        return "";
    }


    /**
     * 查询某FSU的最近远程开门时间
     *
     * ★ 2026-04-05 升级：14位FSU ID（438特征）直接走 getRemoteOpenTimeByFsuid 新流程。
     *
     * ★★★ 旧说明（32位hex格式兜底）★★★
     * 远程开门时间 不在 listFsu.xhtml（FSU列表页），而在专门的"远程开门记录"接口。
     * 目前已知 OMMS 开门记录接口候选：
     *   A. GET  /business/resMge/pwMge/fsuMge/fsuDetail.xhtml?objid={fsuid}
     *   B. POST /business/resMge/pwMge/fsuMge/listFsu.xhtml  （点击某行 → AJAX 展开详情）
     *   C. GET  /business/alarm/accesslog/list?fsuId={fsuid}  （告警日志接口）
     *
     * 本方法尝试 A/C 两种，并把原始响应前500字符写进日志，方便用户抓包确认正确接口。
     *
     * @param fsuid FSU的objid（来自告警数据的 objid 字段，或14位FSU运维ID）
     */
    public static String getRemoteOpenTime(String fsuid) {
        if (fsuid == null || fsuid.isEmpty()) {
            android.util.Log.w(TAG, "getRemoteOpenTime: fsuid为空，跳过");
            return "";
        }

        // ★ 14位438特征FSU ID 不再走旧的 getRemoteOpenTimeByFsuid（listEntrance.xhtml 是设备列表页，不是开门记录）
        // 统一走下面的方案C（listFsu.xhtml 展开 FSU 行详情）
        String cookie = Session.get().ommsCookie;
        android.util.Log.d(TAG, "getRemoteOpenTime fsuid=" + fsuid
                + " cookieLen=" + cookie.length()
                + " hasPwdaToken=" + cookie.contains("pwdaToken")
                + " hasJSESSIONID=" + cookie.contains("JSESSIONID"));

        // ─── 方案A：GET FSU 详情页（带 objid 参数）──────────────────────────────
        try {
            String urlA = "http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/fsuDetail.xhtml?objid=" + fsuid;
            String resp = HttpUtil.get(urlA, buildOmmsGetHeaders(), cookie);
            android.util.Log.d(TAG, "getRemoteOpenTime [A] respLen=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(500, resp.length())));

            if (resp.contains("doPrevLogin") || resp.contains("uac/login") || resp.contains("loginpage")) {
                android.util.Log.w(TAG, "getRemoteOpenTime [A]: Cookie已失效");
                return "Cookie已失效";
            }
            String t = extractTimeFromHtml(resp);
            if (!t.isEmpty()) {
                android.util.Log.d(TAG, "getRemoteOpenTime [A] 命中: " + t);
                return t;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "getRemoteOpenTime [A] 异常: " + e.getMessage());
        }

        // ─── 方案B：GET 开门日志接口（/accesslog 系列）────────────────────────────
        try {
            // 尝试 OMMS 操作日志接口（告警详情中的远程开门记录，不同版本路径不同）
            String urlB = "http://omms.chinatowercom.cn:9000/business/alarm/accesslog/list"
                    + "?fsuId=" + fsuid + "&pageSize=1&pageNo=1";
            String resp = HttpUtil.get(urlB, buildOmmsGetHeaders(), cookie);
            android.util.Log.d(TAG, "getRemoteOpenTime [B] respLen=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(500, resp.length())));
            if (!resp.contains("doPrevLogin") && !resp.contains("uac/login")) {
                String t = extractTimeFromHtml(resp);
                if (!t.isEmpty()) {
                    android.util.Log.d(TAG, "getRemoteOpenTime [B] 命中: " + t);
                    return t;
                }
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "getRemoteOpenTime [B] 异常: " + e.getMessage());
        }

        // ─── 方案C：POST listFsu.xhtml AJAX（2026-03-28 真实抓包对齐）───────────
        // 抓包来源：用户点击FSU行展开详情时浏览器发出的请求
        // 容器控件：j_id670（非 j_id657），参数名：fsuEntranceId（非 objIdHid）
        // 触发字段：j_id670:j_id716，ViewState：j_id25（初始默认值）
        try {
            String url = "http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml";
            String encodedViewState = java.net.URLEncoder.encode(cachedViewState, "UTF-8");
            String post = "AJAXREQUEST=_viewRoot"
                    + "&j_id670=j_id670"
                    + "&autoScroll="
                    + "&javax.faces.ViewState=" + encodedViewState
                    + "&fsuEntranceId=" + fsuid                        // ★ 真实参数名
                    + "&j_id670%3Aj_id716=j_id670%3Aj_id716"          // ★ 真实触发字段
                    + "&AJAX%3AEVENTS_COUNT=1&";
            String resp = HttpUtil.post(url, post, buildOmmsPostHeaders(), cookie);
            android.util.Log.d(TAG, "getRemoteOpenTime [C] respLen=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(500, resp.length())));
            if (!resp.contains("doPrevLogin") && !resp.contains("uac/login")) {
                // 先尝试从响应中提取时间
                String t = extractTimeFromHtml(resp);
                if (!t.isEmpty()) {
                    android.util.Log.d(TAG, "getRemoteOpenTime [C] 命中: " + t);
                    return t;
                }
                // 更新 ViewState（JSF AJAX 响应会返回新的 ViewState）
                String newVs = extractViewState(resp);
                if (newVs != null && !newVs.isEmpty()) {
                    cachedViewState = newVs;
                    android.util.Log.d(TAG, "getRemoteOpenTime [C] 更新ViewState=" + newVs);
                }
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "getRemoteOpenTime [C] 异常: " + e.getMessage());
        }

        android.util.Log.w(TAG, "getRemoteOpenTime: A/B/C 三种方案均未取到时间，fsuid=" + fsuid
                + " → 需要抓包确认正确接口");
        return "";
    }

    /**
     * 从 HTML/JSON 响应中提取单个时间字符串（第一个匹配）
     * 支持：YYYY-MM-DD HH:mm:ss / YYYY/MM/DD HH:mm:ss
     */
    private static String extractTimeFromHtml(String content) {
        if (content == null || content.isEmpty()) return "";
        // YYYY-MM-DD HH:mm:ss
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
                "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}").matcher(content);
        if (m1.find()) return m1.group().replace("T", " ");
        // YYYY/MM/DD HH:mm:ss
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                "\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}").matcher(content);
        if (m2.find()) return m2.group().replace("/", "-");
        return "";
    }

    /**
     * 从 HTML/JSON 响应中提取所有时间字符串
     * 支持：YYYY-MM-DD HH:mm:ss / YYYY/MM/DD HH:mm:ss / YYYY-MM-DDTHH:mm:ss
     *
     * @return 所有匹配到的时间列表（已统一为 yyyy-MM-dd HH:mm:ss 格式），空列表表示未找到
     */
    private static java.util.List<String> extractAllTimesFromHtml(String content) {
        java.util.List<String> times = new java.util.ArrayList<>();
        if (content == null || content.isEmpty()) return times;
        // YYYY-MM-DD HH:mm:ss 或 YYYY-MM-DDTHH:mm:ss
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
                "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}").matcher(content);
        while (m1.find()) {
            times.add(m1.group().replace("T", " "));
        }
        // YYYY/MM/DD HH:mm:ss
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                "\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}").matcher(content);
        while (m2.find()) {
            times.add(m2.group().replace("/", "-"));
        }
        return times;
    }

    /**
     * 查询某FSU的所有远程开门时间记录
     *
     * ★★★ 2026-04-05 修正 ★★★
     * 之前用 listEntrance.xhtml（门禁设备列表页），返回的是设备列表不是开门日志，始终0条。
     * 现在改用与门禁系统Tab相同的方案C：
     *   POST listFsu.xhtml（j_id670:j_id716 展开 FSU 行详情），从展开 HTML 中提取所有远程开门时间。
     *   这与 getRemoteOpenTime() 方案C 完全一致，只是返回所有时间供取最近的。
     *
     * @param fsuid  FSU ID（14位，第7-9位=438，col[16] 设备ID）
     * @return       所有远程开门时间列表（yyyy-MM-dd HH:mm:ss），失败返回空列表
     */
    public static java.util.List<String> getAllRemoteOpenTimesByFsuid(String fsuid) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (fsuid == null || fsuid.isEmpty()) return result;
        try {
            // ★ 2026-04-05 修正：用方案C（POST listFsu.xhtml j_id670:j_id716 展开 FSU 行详情）
            // 之前用 listEntrance.xhtml 返回的是设备列表不是开门日志，始终0条
            // 现在与门禁系统Tab getRemoteOpenTime() 方案C 完全一致
            String url = "http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml";
            String cookie = Session.get().ommsCookie;
            String encodedViewState = java.net.URLEncoder.encode(cachedViewState, "UTF-8");
            String post = "AJAXREQUEST=_viewRoot"
                    + "&j_id670=j_id670"
                    + "&autoScroll="
                    + "&javax.faces.ViewState=" + encodedViewState
                    + "&fsuEntranceId=" + fsuid
                    + "&j_id670%3Aj_id716=j_id670%3Aj_id716"
                    + "&AJAX%3AEVENTS_COUNT=1&";
            String resp = HttpUtil.post(url, post, buildOmmsPostHeaders(), cookie);
            android.util.Log.d(TAG, "getAllRemoteOpenTimesByFsuid fsuid=" + fsuid
                    + " respLen=" + resp.length());

            if (resp.isEmpty() || resp.contains("doPrevLogin") || resp.contains("uac/login")) {
                return result;
            }

            // 更新 ViewState
            String newVs = extractViewState(resp);
            if (newVs != null && !newVs.isEmpty()) cachedViewState = newVs;

            // 提取所有时间
            result = extractAllTimesFromHtml(resp);
            android.util.Log.d(TAG, "getAllRemoteOpenTimesByFsuid 找到 " + result.size() + " 条时间记录, fsuid=" + fsuid);
        } catch (Exception e) {
            android.util.Log.e(TAG, "getAllRemoteOpenTimesByFsuid failed", e);
        }
        return result;
    }

    /**
     * 三步开门指令
     * 对应易语言：子程序_运维远程开门()
     *
     * Cookie 使用用户粘贴的 OMMS 专属 Cookie（Session.ommsCookie）
     *
     * @param fsuid  objid（超级列表框5第11列）
     * @param doorId 门禁id（超级列表框5第10列）
     */
    public static String doOpenDoor(String fsuid, String doorId) {
        try {
            String url = "http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml";
            String headers = buildOmmsPostHeaders();
            String cookie = Session.get().ommsCookie;

            // ★★★ 关键：开门前先动态获取 ViewState（JSF 每次登录/刷新后值都会变）
            // refreshViewState() 会 GET listFsu.xhtml，从 HTML 中提取最新 ViewState
            String vsResult = refreshViewState();
            String vs = cachedViewState;   // refreshViewState() 成功后更新了 cachedViewState
            android.util.Log.d(TAG, "doOpenDoor: refreshViewState result=" + vsResult
                    + " ViewState=" + vs + " fsuid=" + fsuid);
            // 刷新后等待 500ms，让服务端稳定
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            // ──────────────────────────────────────────────────────────────
            // 步骤零：触发列表行点击（激活当前行上下文）
            // 容器：j_id763，触发字段：j_id763:j_id764
            // ──────────────────────────────────────────────────────────────
            String post0 = "AJAXREQUEST=_viewRoot"
                    + "&j_id763=j_id763"
                    + "&autoScroll="
                    + "&javax.faces.ViewState=" + vs
                    + "&j_id763%3Aj_id764=j_id763%3Aj_id764"
                    + "&";
            String resp0 = HttpUtil.post(url, post0, headers, cookie);
            android.util.Log.d(TAG, "doOpenDoor step0 respLen=" + resp0.length()
                    + " preview=" + resp0.substring(0, Math.min(300, resp0.length())));
            // 每步响应后更新 ViewState（JSF AJAX 响应携带新 ViewState）
            String vs0 = extractViewState(resp0);
            if (vs0 != null && !vs0.isEmpty()) vs = vs0;
            try { Thread.sleep(1500 + (long)(Math.random() * 1000)); } catch (InterruptedException ignored) {}

            // ──────────────────────────────────────────────────────────────
            // 步骤一：激活开门弹窗
            // 容器：j_id670，触发：j_id670:j_id720，参数：relTableName + id={fsuid}
            // ──────────────────────────────────────────────────────────────
            String post1 = "AJAXREQUEST=_viewRoot"
                    + "&j_id670=j_id670"
                    + "&autoScroll="
                    + "&javax.faces.ViewState=" + vs
                    + "&j_id670%3Aj_id720=j_id670%3Aj_id720"
                    + "&relTableName=TW_PW_ENTRANCE_GUARD"
                    + "&id=" + fsuid
                    + "&AJAX%3AEVENTS_COUNT=1&";
            String resp1 = HttpUtil.post(url, post1, headers, cookie);
            android.util.Log.d(TAG, "doOpenDoor step1 respLen=" + resp1.length()
                    + " fsuid=" + fsuid
                    + " preview=" + resp1.substring(0, Math.min(300, resp1.length())));
            String vs1 = extractViewState(resp1);
            if (vs1 != null && !vs1.isEmpty()) vs = vs1;
            try { Thread.sleep(1500 + (long)(Math.random() * 1000)); } catch (InterruptedException ignored) {}

            // ──────────────────────────────────────────────────────────────
            // 步骤二：提交开门指令
            // selControlDevice={doorId}，openCause=2，j_id1103=提交按钮
            // ──────────────────────────────────────────────────────────────
            String post2 = "AJAXREQUEST=_viewRoot"
                    + "&openDoor_Form%3AselControlDevice=" + doorId
                    + "&openDoor_Form%3AopenCause=2"
                    + "&openDoor_Form%3Aj_id1101="
                    + "&openDoor_Form=openDoor_Form"
                    + "&autoScroll="
                    + "&javax.faces.ViewState=" + vs
                    + "&openDoor_Form%3Aj_id1103=openDoor_Form%3Aj_id1103&";
            String resp2 = HttpUtil.post(url, post2, headers, cookie);
            android.util.Log.d(TAG, "doOpenDoor step2 respLen=" + resp2.length()
                    + " doorId=" + doorId
                    + " preview=" + resp2.substring(0, Math.min(300, resp2.length())));
            String vs2 = extractViewState(resp2);
            if (vs2 != null && !vs2.isEmpty()) vs = vs2;
            try { Thread.sleep(1500 + (long)(Math.random() * 1000)); } catch (InterruptedException ignored) {}

            // ──────────────────────────────────────────────────────────────
            // 步骤三：密码确认（空密码，OMMS不校验）
            // j_id2002=空密码框，j_id2005=确认按钮
            // ──────────────────────────────────────────────────────────────
            String post3 = "AJAXREQUEST=_viewRoot"
                    + "&chickMiMaForm%3Aj_id2002="
                    + "&chickMiMaForm=chickMiMaForm"
                    + "&autoScroll="
                    + "&javax.faces.ViewState=" + vs
                    + "&chickMiMaForm%3Aj_id2005=chickMiMaForm%3Aj_id2005&";
            String resp3 = HttpUtil.post(url, post3, headers, cookie);
            android.util.Log.d(TAG, "doOpenDoor step3 respLen=" + resp3.length()
                    + " preview=" + resp3.substring(0, Math.min(300, resp3.length())));

            return resp3;
        } catch (Exception e) {
            android.util.Log.e(TAG, "doOpenDoor failed", e);
            return "";
        }
    }

    // =====================================================================
    // 工具方法
    // =====================================================================

    /**
     * 构建 GET 请求的 OMMS 协议头（与易语言 子程序_更新门禁页面状态值 完全一致）
     * ★ 不加 Accept-Encoding：OkHttp 会自动处理 gzip，手动指定会关闭自动解压导致乱码
     */
    private static String buildOmmsGetHeaders() {
        return "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Host: omms.chinatowercom.cn:9000\n"
                + "Pragma: no-cache\n"
                + "Referer: http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml\n"
                + "Upgrade-Insecure-Requests: 1\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";
    }

    /**
     * 构建 POST 请求的 OMMS 协议头（与用户抓包一致）
     * ★ 不加 Accept-Encoding：OkHttp 会自动处理 gzip，手动指定会关闭自动解压导致乱码
     */
    private static String buildOmmsPostHeaders() {
        return "Accept: */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Content-Type: application/x-www-form-urlencoded; charset=UTF-8\n"
                + "Host: omms.chinatowercom.cn:9000\n"
                + "Origin: http://omms.chinatowercom.cn:9000\n"
                + "Pragma: no-cache\n"
                + "Proxy-Connection: keep-alive\n"
                + "Referer: http://omms.chinatowercom.cn:9000/business/resMge/pwMge/fsuMge/listFsu.xhtml\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36\n"
                + "X-Requested-With: XMLHttpRequest";
    }

    // =====================================================================
    // 4. 4A系统 - 门禁开门记录（全量翻页）
    //    接口：POST http://tymj.chinatowercom.cn:8006/api/recordAccess/getPage
    //    认证：Authorization: Bearer {tower4aToken}
    //    Cookie：userOrgCode=100033（来自抓包）
    // =====================================================================

    /** 4A开门单条记录 */
    public static class FourARecord {
        public String stationName;   // 站点名称（deviceName字段）
        public String openTime;      // 开门时间（accessTime字段）
        public String openResult;    // 开门结果（openResult字段）
        public String operators;     // 操作人（operators字段）
    }

    /**
     * 全量拉取4A开门记录，自动翻页合并。
     *
     * @param startDate  查询起始日期，格式 "yyyy-MM-dd"
     * @param endDate    查询截止日期，格式 "yyyy-MM-dd"
     * @param countyCode 县级代码（如 "330326"，来自 Session.tower4aCountyCode）
     * @return 所有4A开门记录列表
     */
    public static java.util.List<FourARecord> getAll4aOpenRecords(String startDate, String endDate, String countyCode) {
        java.util.List<FourARecord> all = new java.util.ArrayList<>();
        Session s = Session.get();

        // 4A接口需要 tower4aToken（Bearer Token）
        String token = s.tower4aToken;
        if (token == null || token.isEmpty()) {
            android.util.Log.w(TAG, "getAll4aOpenRecords: tower4aToken 为空，未登录4A系统");
            return all;
        }

        // 日期处理
        String startTime = (startDate == null || startDate.isEmpty()) ? "2026-01-01 00:00:00"
                : (startDate.length() == 10 ? startDate + " 00:00:00" : startDate);
        String endTime = (endDate == null || endDate.isEmpty()) ? "2030-12-31 23:59:59"
                : (endDate.length() == 10 ? endDate + " 23:59:59" : endDate);

        // 县级代码（优先用传入参数，其次用 Session 中保存的）
        String county = (countyCode != null && !countyCode.isEmpty()) ? countyCode
                : (s.tower4aCountyCode != null && !s.tower4aCountyCode.isEmpty() ? s.tower4aCountyCode : "");

        String url = "http://tymj.chinatowercom.cn:8006/api/recordAccess/getPage";
        String headers = "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: Bearer " + token + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json\n"
                + "Host: tymj.chinatowercom.cn:8006\n"
                + "Origin: http://tymj.chinatowercom.cn:8006\n"
                + "Pragma: no-cache\n"
                + "Referer: http://tymj.chinatowercom.cn:8006/\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";
        String cookie = "userOrgCode=100033";

        android.util.Log.d(TAG, "getAll4aOpenRecords dateRange=" + startTime + " ~ " + endTime + " county=" + county);
        android.util.Log.d(TAG, "getAll4aOpenRecords 使用Token=" + token.substring(0, Math.min(20, token.length())) + "...(前20字符)");

        int page = 1;
        int pageSize = 100;
        int maxPage = 200;
        while (page <= maxPage) {
            // 构建请求体（按抓包结构）
            org.json.JSONObject postJson = new org.json.JSONObject();
            try {
                postJson.put("areaId", "");
                postJson.put("areaName", "");
                postJson.put("devCode", "");
                postJson.put("startTime", startTime);
                postJson.put("endTime", endTime);
                postJson.put("roomId", "");
                postJson.put("deviceCode", "");
                postJson.put("operators", "");
                postJson.put("bluetoothName", "");
                postJson.put("deviceName", "");
                postJson.put("userCode", "");
                postJson.put("accountSource", "");
                postJson.put("userType", "");
                postJson.put("belongingDepartment", "");
                postJson.put("undefined", "");
                postJson.put("openResult", "");
                postJson.put("type", "");
                postJson.put("openSource", "");
                postJson.put("item1", "");
                postJson.put("item2", "");
                postJson.put("item3", "");
                // 县级代码列表
                org.json.JSONArray countyList = new org.json.JSONArray();
                if (!county.isEmpty()) countyList.put(county);
                postJson.put("provinceCodeList", new org.json.JSONArray());
                postJson.put("cityCodeList", new org.json.JSONArray());
                postJson.put("countyCodeList", countyList);
                postJson.put("pageNum", page);
                postJson.put("pageSize", pageSize);
                // 调试：打印实际发送的请求体
                android.util.Log.d(TAG, "getAll4aOpenRecords p" + page + " postJson=" + postJson.toString());
            } catch (Exception e) {
                android.util.Log.e(TAG, "getAll4aOpenRecords buildPost failed", e);
                break;
            }

            try {
                String resp = HttpUtil.post(url, postJson.toString(), headers, cookie);
                if (resp == null || resp.isEmpty()) {
                    android.util.Log.w(TAG, "getAll4aOpenRecords p" + page + " 空响应(HTTP可能失败)，停止");
                    break;
                }
                // 打印完整响应（前500字符），方便调试
                String respPreview = resp.length() > 500 ? resp.substring(0, 500) + "...(截断)" : resp;
                android.util.Log.d(TAG, "getAll4aOpenRecords p" + page + " respLen=" + resp.length()
                        + " resp=" + respPreview);

                org.json.JSONObject obj;
                try {
                    obj = new org.json.JSONObject(resp);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "getAll4aOpenRecords JSON解析失败: " + e.getMessage() + " resp=" + respPreview, e);
                    break;
                }
                org.json.JSONArray rows = null;

                // 尝试从 data.records / data.rows / data.list / data 中取数组
                org.json.JSONObject data = obj.optJSONObject("data");
                if (data != null) {
                    rows = data.optJSONArray("records");
                    if (rows == null) rows = data.optJSONArray("rows");
                    if (rows == null) rows = data.optJSONArray("list");
                    // 打印data的key，方便调试
                    android.util.Log.d(TAG, "getAll4aOpenRecords data keys=" + data.keys().toString());
                }
                if (rows == null) rows = obj.optJSONArray("data");
                // 检查是否找到了数据
                if (rows == null || rows.length() == 0) {
                    // 打印完整的obj结构
                    android.util.Log.d(TAG, "getAll4aOpenRecords 无数据: obj=" + obj.toString().substring(0, Math.min(300, obj.toString().length())));
                    android.util.Log.d(TAG, "getAll4aOpenRecords p" + page + " 无更多数据，停止");
                    break;
                }

                for (int i = 0; i < rows.length(); i++) {
                    try {
                        org.json.JSONObject row = rows.getJSONObject(i);
                        FourARecord rec = new FourARecord();
                        // 站点名（优先 deviceName，其次 roomName、stationName）
                        rec.stationName = row.optString("deviceName", "").trim();
                        if (rec.stationName.isEmpty() || "null".equals(rec.stationName)) {
                            rec.stationName = row.optString("roomName", "").trim();
                        }
                        if (rec.stationName.isEmpty() || "null".equals(rec.stationName)) {
                            rec.stationName = row.optString("stationName", "").trim();
                        }
                        if (rec.stationName.isEmpty() || "null".equals(rec.stationName)) {
                            rec.stationName = row.optString("name", "").trim();
                        }
                        if (rec.stationName.isEmpty() || "null".equals(rec.stationName)) {
                            rec.stationName = row.optString("doorName", "").trim();
                        }
                        if ("null".equals(rec.stationName)) rec.stationName = "";
                        // 开门时间（accessTime）- 尝试多个可能字段名
                        rec.openTime = row.optString("accessTime", "").trim();
                        if (rec.openTime.isEmpty()) rec.openTime = row.optString("openTime", "").trim();
                        if (rec.openTime.isEmpty()) rec.openTime = row.optString("access_time", "").trim();
                        if (rec.openTime.isEmpty()) rec.openTime = row.optString("createTime", "").trim();
                        if (rec.openTime.isEmpty()) rec.openTime = row.optString("create_time", "").trim();
                        if ("null".equals(rec.openTime)) rec.openTime = "";
                        // 调试：打印第一条的原始字段
                        if (i == 0) {
                            android.util.Log.d(TAG, "[4A解析] 首行JSON keys=" + row.keys().toString()
                                    + " accessTime=" + row.optString("accessTime", "【空】")
                                    + " openTime=" + row.optString("openTime", "【空】")
                                    + " access_time=" + row.optString("access_time", "【空】")
                                    + " createTime=" + row.optString("createTime", "【空】"));
                        }
                        // 开门结果
                        rec.openResult = row.optString("openResult", "").trim();
                        if ("null".equals(rec.openResult)) rec.openResult = "";
                        // 操作人
                        rec.operators = row.optString("operators", "").trim();
                        if ("null".equals(rec.operators)) rec.operators = "";

                        if (!rec.stationName.isEmpty() || !rec.openTime.isEmpty()) {
                            all.add(rec);
                        }
                    } catch (Exception ignored) {}
                }

                android.util.Log.d(TAG, "getAll4aOpenRecords p" + page + " 本页=" + rows.length() + " 累计=" + all.size());

                // 获取总页数判断是否继续翻页
                int total = 0;
                if (data != null) {
                    total = data.optInt("total", 0);
                    if (total == 0) total = data.optInt("totalCount", 0);
                    if (total == 0) total = data.optInt("count", 0);
                }
                if (total == 0) total = obj.optInt("total", 0);
                if (total > 0 && all.size() >= total) {
                    android.util.Log.d(TAG, "getAll4aOpenRecords 已拉完全部 total=" + total);
                    break;
                }
                if (rows.length() < pageSize) {
                    android.util.Log.d(TAG, "getAll4aOpenRecords 本页不足" + pageSize + "条，停止");
                    break;
                }
                page++;
            } catch (Exception e) {
                android.util.Log.e(TAG, "getAll4aOpenRecords p" + page + " err=" + e.getMessage());
                break;
            }
        }
        android.util.Log.d(TAG, "getAll4aOpenRecords 完成，共 " + all.size() + " 条，pages=" + page);
        return all;
    }

    /**
     * 计算两个时间字符串之间的分钟差
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    public static int minutesBetween(String timeA, String timeB) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            long a = sdf.parse(timeA).getTime();
            long b = sdf.parse(timeB).getTime();
            return (int) Math.abs((b - a) / 60000L);
        } catch (Exception e) {
            return -9999;
        }
    }

    /**
     * 计算某时间与当前时间的分钟差（正数表示过去多少分钟）
     */
    public static int minutesFromNow(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            long t = sdf.parse(timeStr).getTime();
            return (int) ((System.currentTimeMillis() - t) / 60000L);
        } catch (Exception e) {
            return -9999;
        }
    }
}

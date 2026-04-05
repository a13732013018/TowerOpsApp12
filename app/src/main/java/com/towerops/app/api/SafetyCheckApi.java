package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 安全打卡 API（双系统）
 *
 * ┌─ 系统A：OMMS 维修打卡 ──────────────────────────────────────────┐
 * │  Host: omms.chinatowercom.cn:9000                              │
 * │  URL:  /portal/ClockInController/selectClockingOrder2         │
 * │  认证: Cookie 请求头（ommsCookie，含 JSESSIONID/pwdaToken）    │
 * │  queryType "1"=未归档（body 不带日期），"2"=已归档（带日期范围）│
 * └────────────────────────────────────────────────────────────────┘
 *
 * ┌─ 系统B：TOMS 巡检打卡 ──────────────────────────────────────────┐
 * │  Host: chntoms5.chinatowercom.cn:8081                         │
 * │  URL:  /api/maintenance/safetyCheck/getSafetyCheckList        │
 * │  认证: token 请求头（tomsToken，JWT）                          │
 * │  body: {"queryUnitId":"","workStatus":"未归档","businessType":"",│
 * │         "page":1,"limit":35}                                  │
 * │  workStatus: "未归档" / "已归档"                              │
 * └────────────────────────────────────────────────────────────────┘
 *
 * 打卡合格标准（按响应中任务内容字段判断）：
 *   - 含"巡检"/"资产清查" → 巡检合格线（>= 20 分钟）
 *   - 含"修理"/"维修" 或其他 → 维修合格线（>= 10 分钟）
 */
public class SafetyCheckApi {

    private static final String TAG = "SafetyCheckApi";

    /** OMMS 系统：维修打卡接口（Cookie 认证） */
    private static final String OMMS_URL =
            "http://omms.chinatowercom.cn:9000/portal/ClockInController/selectClockingOrder2";

    /** TOMS 系统：巡检打卡接口（token 请求头认证）*/
    private static final String TOMS_URL =
            "http://chntoms5.chinatowercom.cn:8081/api/maintenance/safetyCheck/getSafetyCheckList";

    /** 巡检打卡合格最低分钟数 */
    public static final int XUNJIAN_MIN_MINUTES = 20;
    /** 维修打卡合格最低分钟数 */
    public static final int WEIXIU_MIN_MINUTES = 10;

    // ─────────────────────────────────────────────────────────────────────
    // 单条打卡记录
    // ─────────────────────────────────────────────────────────────────────
    public static class CheckRecord {
        public String id;
        public String workerName;    // 人员姓名（upSiteUsername）
        public String businessType;  // 业务类型："1"=普通巡检, "2"=智联设备巡检
        public String taskContent;   // 任务内容（含"巡检"/"维修"等关键词，用于合格判断）
        public String workStatus;    // 工单状态（"已归档"/"未归档"）
        public String startTime;     // 进场时间（upSiteTime）
        public String endTime;       // 离场时间（offSiteTime）
        public String stationName;   // 站址名称（siteName）
        public long   durationMinutes; // 打卡时长（分钟），-1 表示未结束

        /**
         * 是否合格。
         *
         * 判断逻辑（两套结构统一）：
         *   结构A（巡检接口）：taskContent/businessType 含"巡检"/"资产清查" → 巡检线20分钟
         *   结构B（维修接口）：TASK_NAME/SELF_TASK_NAME 含"巡检" → 巡检线20分钟
         *                      SELF_TASK_NAME="安全打卡_修理" 或 TASK_NAME含"维修"/"故障" → 维修线10分钟
         *   默认维修合格线 10 分钟。
         */
        public boolean isQualified() {
            if (durationMinutes < 0) return false;
            boolean isXunjian = false;
            if (taskContent != null) {
                // 结构B：SELF_TASK_NAME="安全打卡_修理" 明确是维修，不算巡检
                boolean isMaintenance = taskContent.contains("修理") || taskContent.contains("维修");
                if (!isMaintenance) {
                    // 含"巡检"/"资产清查"关键词才算巡检
                    isXunjian = taskContent.contains("巡检") || taskContent.contains("资产清查");
                }
            }
            // businessType 辅助判断（结构A）
            if (!isXunjian && businessType != null) {
                isXunjian = businessType.contains("巡检") || businessType.contains("xunjian")
                        || businessType.contains("inspection");
            }
            int minRequired = isXunjian ? XUNJIAN_MIN_MINUTES : WEIXIU_MIN_MINUTES;
            return durationMinutes >= minRequired;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 人员汇总统计
    // ─────────────────────────────────────────────────────────────────────
    public static class PersonStats {
        public String  name;
        public int     totalCount;     // 打卡总次数
        public int     qualifiedCount; // 合格次数
        public int     unqualifiedCount; // 不合格次数
        /** 打卡天数（按自然日去重） */
        public int     dayCount;
        /** 平均时长（分钟），-1表示无有效数据 */
        public double  avgDurationMinutes;
        /** 总时长（分钟，用于计算平均） */
        public long    totalDurationMinutes;
        /** 有效时长记录条数（durationMinutes >= 0 的条目数） */
        public int     validDurationCount;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 构建请求头 / Cookie
    // TOMS 与 OMMS 共用同一套 WS4A JWT，直接从 Session.ommsCookie 提取即可
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 从 ommsCookie 字符串中提取指定 key 的值。
     * 例：extractCookieValue("loginName=abc; fp=xxx", "loginName") → "abc"
     */
    private static String extractCookieValue(String cookieStr, String key) {
        if (cookieStr == null || cookieStr.isEmpty()) return "";
        for (String part : cookieStr.split(";")) {
            part = part.trim();
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).trim().equals(key)) {
                return part.substring(eq + 1).trim();
            }
        }
        return "";
    }

    /** OMMS 维修打卡请求头（Cookie 认证，X-Requested-With 必须有） */
    private static String buildOmmsHeaders() {
        // ★ HttpUtil 用 \n 分割 headers，必须用 \n，不能用 \r\n
        return "Accept: application/json, text/javascript, */*; q=0.01\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Content-Type: application/json\n"
                + "Origin: http://omms.chinatowercom.cn:9000\n"
                + "Referer: http://omms.chinatowercom.cn:9000/portal/iframe.html?modules/selfTask/views/clockedTaskListIndex\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36\n"
                + "X-Requested-With: XMLHttpRequest\n";
    }

    /**
     * TOMS 巡检打卡请求头（仅 token 请求头，无 Cookie）
     *
     * 最新抓包确认（2026-03-29 两次对比）：
     *   - TOMS token = OMMS Cookie 里的 pwdaToken（两者都是 iss=WS4A 的 JWT，同一套认证体系）
     *   - 优先用 Session.tomsToken（手动设置），否则自动从 ommsCookie 提取 pwdaToken
     *   - ❌ 不需要 Cookie 请求头（TOMS 抓包中无 Cookie）
     */
    private static String buildTomsHeaders() {
        Session s = Session.get();
        // 优先用手动设置的 tomsToken，否则从 ommsCookie 自动提取 pwdaToken（两者是同一套 WS4A JWT）
        String tok = (s.tomsToken != null && !s.tomsToken.isEmpty())
                ? s.tomsToken
                : extractCookieValue(s.ommsCookie, "pwdaToken");
        android.util.Log.d(TAG, "buildTomsHeaders tok=" + tok.substring(0, Math.min(30, tok.length())) + "...");
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Content-Type: application/json\n"
                + "Origin: http://chntoms5.chinatowercom.cn:8081\n"
                + "Referer: http://chntoms5.chinatowercom.cn:8081/\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36\n"
                + "token: " + tok + "\n";
    }

    private static String buildCookie() {
        Session s = Session.get();
        String cookie = s.ommsCookie != null ? s.ommsCookie : "";
        if (cookie.isEmpty()) {
            android.util.Log.w(TAG, "buildCookie: ommsCookie is EMPTY!");
        } else {
            android.util.Log.d(TAG, "buildCookie: len=" + cookie.length()
                    + " hasPwdaToken=" + cookie.contains("pwdaToken")
                    + " hasJSESSIONID=" + cookie.contains("JSESSIONID")
                    + " preview=" + cookie.substring(0, Math.min(100, cookie.length())));
        }
        return cookie;
    }

    /**
     * 从 Cookie 中提取 OMMS orgId（OMMS body 字段 orgId，对应机构代码如 "0107437"）。
     *
     * 注意：Cookie 里的 "orgId" 字段存的是账号数字 ID（如 203349045），
     * 不是机构代码。机构代码对应 Cookie 字段 "unitId"/"deptCode"/"unitCode"。
     * 如果都找不到，传空字符串，OMMS 服务端会用当前 Session 的机构，依然能返回数据。
     */
    private static String extractOrgId() {
        Session s = Session.get();
        // 优先取 unitId（机构单位代码，如 "0107437"）
        String val = extractCookieValue(s.ommsCookie, "unitId");
        if (!val.isEmpty()) return val;
        // 兜底 unitCode / deptCode
        val = extractCookieValue(s.ommsCookie, "unitCode");
        if (!val.isEmpty()) return val;
        val = extractCookieValue(s.ommsCookie, "deptCode");
        if (!val.isEmpty()) return val;
        // 不传 orgId（传空），OMMS 服务端用 Session 机构，避免传错误的账号 ID
        return "";
    }

    /**
     * 提取 TOMS queryUnitId（机构单位代码，如 "0107437"）。
     *
     * 最新抓包确认（2026-03-29）：
     *   queryUnitId = "0107437"，与 OMMS Cookie 的 unitId 字段一致。
     *   之前误用 userOrgCode（纯数字区划码如 "330326"），是错误的。
     *
     * 来源优先级：
     *   1. OMMS Cookie unitId 字段（最准确，与 OMMS orgId 同一个值）
     *   2. 传空字符串（TOMS 服务端可能用 Session 机构兜底）
     */
    private static String extractTomsUnitId() {
        Session s = Session.get();
        // OMMS Cookie unitId = 机构单位代码（如 "0107437"）
        String val = extractCookieValue(s.ommsCookie, "unitId");
        if (!val.isEmpty()) return val;
        // tomsCookie unitId
        val = extractCookieValue(s.tomsCookie, "unitId");
        if (!val.isEmpty()) return val;
        // 找不到传空，TOMS 服务端兜底
        return "";
    }

    /**
     * 诊断信息（查询后可读取，供 Fragment 显示）
     * lastDiag: 最后一次调用的诊断（兼容旧代码）
     * ommsDiag: OMMS 路线最后诊断（含 HTTP 状态码）
     * tomsDiag: TOMS 路线最后诊断（含 HTTP 状态码）
     */
    public static volatile String lastDiag = "";
    public static volatile String ommsDiag = "";
    public static volatile String tomsDiag = "";

    // ─────────────────────────────────────────────────────────────────────
    // 日期格式工具：2026-03-01 → 2026-3-1（抓包确认服务器不补零）
    // ─────────────────────────────────────────────────────────────────────
    private static String toOmmsDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.length() < 10) return yyyyMMdd;
        String[] parts = yyyyMMdd.split("-");
        if (parts.length != 3) return yyyyMMdd;
        try {
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int d = Integer.parseInt(parts[2]);
            return y + "-" + m + "-" + d;
        } catch (NumberFormatException e) {
            return yyyyMMdd;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // OMMS 维修打卡查询（系统A）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * OMMS queryType=1：未归档维修打卡（当前待处理）
     * 抓包确认：body 不带 planTimeStart/planTimeEnd
     * {"queryType":"1","orgId":"0107437","pageName":"taskListIndex","page":1,"rows":15}
     */
    public static String fetchOmmsUnarchived(int page, int rows) {
        try {
            String orgId = extractOrgId();
            JSONObject body = new JSONObject();
            body.put("queryType",  "1");
            body.put("orgId",      orgId);
            body.put("pageName",   "taskListIndex");
            body.put("page",       page);
            body.put("rows",       rows);
            String bodyStr = body.toString();
            android.util.Log.d(TAG, "fetchOmmsUnarchived orgId=" + orgId + " body=" + bodyStr);

            HttpUtil.HttpResponse hr = HttpUtil.postWithHeaders(
                    OMMS_URL, bodyStr, buildOmmsHeaders(), buildCookie());
            String resp = hr != null ? hr.body : "";
            int code = hr != null ? hr.code : 0;
            android.util.Log.d(TAG, "fetchOmmsUnarchived code=" + code + " len=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(400, resp.length())));

            lastDiag = "OMMS未归档 HTTP " + code
                    + " | orgId=" + (orgId.isEmpty() ? "空！" : orgId)
                    + " | " + resp.substring(0, Math.min(150, resp.length()));
            ommsDiag = lastDiag;
            return resp;
        } catch (Exception e) {
            android.util.Log.e(TAG, "fetchOmmsUnarchived failed", e);
            lastDiag = "OMMS fetch 异常: " + e.getMessage();
            ommsDiag = lastDiag;
            return "";
        }
    }

    /**
     * OMMS queryType=2：已归档维修打卡（历史记录，必须带日期范围）
     * 抓包确认：{"queryType":"2","orgId":"0107437","planTimeStart":"2026-3-1",
     *            "planTimeEnd":"2026-3-29","pageName":"taskListIndex","page":1,"rows":15}
     */
    public static String fetchOmmsArchived(String startDate, String endDate, int page, int rows) {
        try {
            String orgId = extractOrgId();
            String start = toOmmsDate(startDate);
            String end   = toOmmsDate(endDate);
            JSONObject body = new JSONObject();
            body.put("queryType",     "2");
            body.put("orgId",         orgId);
            body.put("planTimeStart", start);
            body.put("planTimeEnd",   end);
            body.put("pageName",      "taskListIndex");
            body.put("page",          page);
            body.put("rows",          rows);
            String bodyStr = body.toString();
            android.util.Log.d(TAG, "fetchOmmsArchived orgId=" + orgId
                    + " start=" + start + " end=" + end + " body=" + bodyStr);

            HttpUtil.HttpResponse hr = HttpUtil.postWithHeaders(
                    OMMS_URL, bodyStr, buildOmmsHeaders(), buildCookie());
            String resp = hr != null ? hr.body : "";
            int code = hr != null ? hr.code : 0;
            android.util.Log.d(TAG, "fetchOmmsArchived code=" + code + " len=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(400, resp.length())));

            lastDiag = "OMMS已归档 HTTP " + code
                    + " | orgId=" + (orgId.isEmpty() ? "空！" : orgId)
                    + " | " + resp.substring(0, Math.min(150, resp.length()));
            ommsDiag = lastDiag;
            return resp;
        } catch (Exception e) {
            android.util.Log.e(TAG, "fetchOmmsArchived failed", e);
            lastDiag = "OMMS fetch 异常: " + e.getMessage();
            ommsDiag = lastDiag;
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // TOMS 巡检打卡查询（系统B）
    // Host: chntoms5.chinatowercom.cn:8081
    // 认证: token 请求头（JWT，不用 Cookie）
    // body 字段分页用 limit（不是 rows！）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * TOMS 巡检打卡：workStatus="未归档"（当前待处理）
     * 抓包确认：{"queryUnitId":"","workStatus":"未归档","businessType":"","page":1,"limit":35}
     */
    public static String fetchTomsUnarchived(int page, int limit) {
        return fetchToms("未归档", page, limit);
    }

    /**
     * TOMS 巡检打卡：workStatus="已归档"（历史记录）
     * 抓包确认：body 必须含 startCreateTime / endCreateTime，格式 yyyy-MM-dd
     * 例：{"queryUnitId":"","workStatus":"已归档","businessType":"","startCreateTime":"2026-03-23","endCreateTime":"2026-03-29","page":1,"limit":35}
     */
    public static String fetchTomsArchived(String startDate, String endDate, int page, int limit) {
        return fetchToms("已归档", startDate, endDate, page, limit);
    }

    /** 无日期版本：自动取最近30天（兼容旧调用） */
    public static String fetchTomsArchived(int page, int limit) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        String today = sdf.format(new java.util.Date());
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, -30);
        String monthAgo = sdf.format(cal.getTime());
        return fetchToms("已归档", monthAgo, today, page, limit);
    }

    private static String fetchToms(String workStatus, int page, int limit) {
        return fetchToms(workStatus, null, null, page, limit);
    }

    private static String fetchToms(String workStatus, String startDate, String endDate, int page, int limit) {
        try {
            String unitId = extractTomsUnitId();
            JSONObject body = new JSONObject();
            body.put("queryUnitId",  unitId);
            body.put("workStatus",   workStatus);
            body.put("businessType", "");
            if (startDate != null && !startDate.isEmpty()) {
                body.put("startCreateTime", startDate);
                body.put("endCreateTime",   endDate != null ? endDate : startDate);
            }
            body.put("page",         page);
            body.put("limit",        limit);
            String bodyStr = body.toString();
            android.util.Log.d(TAG, "fetchToms workStatus=" + workStatus
                    + " unitId=" + unitId + " body=" + bodyStr);

            // TOMS Cookie 已内嵌在 buildTomsHeaders() 里（Cookie + token 双认证）
            HttpUtil.HttpResponse hr = HttpUtil.postWithHeaders(
                    TOMS_URL, bodyStr, buildTomsHeaders(), null);
            String resp = hr != null ? hr.body : "";
            int code = hr != null ? hr.code : 0;
            android.util.Log.d(TAG, "fetchToms code=" + code + " len=" + resp.length()
                    + " preview=" + resp.substring(0, Math.min(400, resp.length())));

            lastDiag = "TOMS巡检" + workStatus + " HTTP " + code
                    + " | " + resp.substring(0, Math.min(150, resp.length()));
            tomsDiag = lastDiag;
            return resp;
        } catch (Exception e) {
            android.util.Log.e(TAG, "fetchToms failed", e);
            lastDiag = "TOMS fetch 异常: " + e.getMessage();
            tomsDiag = lastDiag;
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 兼容旧调用名（方法名改了，保留别名）
    // ─────────────────────────────────────────────────────────────────────
    public static String fetchUnarchived(int page, int rows) { return fetchOmmsUnarchived(page, rows); }
    public static String fetchArchived(String s, String e, int page, int rows) { return fetchOmmsArchived(s, e, page, rows); }
    public static String fetchByQueryType(String qt, String s, String e, int page, int rows) {
        return "1".equals(qt) ? fetchOmmsUnarchived(page, rows) : fetchOmmsArchived(s, e, page, rows);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 解析响应 JSON 为 CheckRecord 列表（兼容 OMMS 和 TOMS 两套结构）
    //
    // OMMS 维修打卡响应（顶层 rows 数组）：
    // { "pageIndex":1, "total":14, "rows": [
    //     { "WORK_ORDER_ID":"...", "DO_MAN_NAME":"张三",
    //       "SITE_NAME":"xxx站", "TASK_NAME":"故障修复",
    //       "SELF_TASK_NAME":"安全打卡", "BUSI_TYPE":"1",
    //       "事前打卡时间":"2026-03-01 08:30:00",
    //       "事后打卡时间":"2026-03-01 09:15:00" }
    // ] }
    //
    // TOMS 巡检打卡响应（data.rows 或 data.list）：
    // { "code":0, "msg":"success", "data": {
    //     "total":14, "rows": [
    //       { "id":"...", "upSiteUsername":"张三",
    //         "siteName":"xxx站", "taskContent":"日常巡检",
    //         "businessType":"1", "status":"未归档",
    //         "upSiteTime":"2026-03-29 10:00:00",
    //         "offSiteTime":"2026-03-29 10:35:00" }
    //     ] }
    // }
    // ─────────────────────────────────────────────────────────────────────
    public static List<CheckRecord> parseRecords(String json) {
        List<CheckRecord> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(json);
            android.util.Log.d(TAG, "parseRecords root keys=" + root.keys());

            JSONArray arr = null;

            // ① 优先匹配结构A：dataRows.list（巡检打卡）
            if (root.has("dataRows") && !root.isNull("dataRows")) {
                JSONObject dataRows = root.getJSONObject("dataRows");
                if (dataRows.has("list"))         arr = dataRows.getJSONArray("list");
                else if (dataRows.has("rows"))    arr = dataRows.getJSONArray("rows");
                else if (dataRows.has("records")) arr = dataRows.getJSONArray("records");
            }
            // ② 兼容 data.list / data.rows / data 直接是数组
            if (arr == null && root.has("data")) {
                Object d = root.get("data");
                if (d instanceof JSONArray) {
                    arr = (JSONArray) d;
                } else if (d instanceof JSONObject) {
                    JSONObject dObj = (JSONObject) d;
                    if (dObj.has("list"))         arr = dObj.getJSONArray("list");
                    else if (dObj.has("rows"))    arr = dObj.getJSONArray("rows");
                    else if (dObj.has("records")) arr = dObj.getJSONArray("records");
                }
            }
            // ③ 顶层 rows / list / records（结构B维修打卡用 rows）
            if (arr == null && root.has("rows"))    arr = root.getJSONArray("rows");
            if (arr == null && root.has("list"))    arr = root.getJSONArray("list");
            if (arr == null && root.has("records")) arr = root.getJSONArray("records");

            if (arr == null) {
                android.util.Log.w(TAG, "parseRecords: no array found, json=" + json.substring(0, Math.min(400, json.length())));
                return list;
            }
            android.util.Log.d(TAG, "parseRecords: found " + arr.length() + " records");

            // 支持两种时间格式：
            //   结构A: "2026-03-29 16:12:14"（无毫秒）
            //   结构B: "2026-03-29 16:12:14"（无毫秒，但来源字段是中文"事前打卡时间"）
            SimpleDateFormat sdf    = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",     Locale.getDefault());
            SimpleDateFormat sdfMs  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                CheckRecord r = new CheckRecord();

                // ──── ID
                // 结构A: "id" | 结构B: "ID"
                r.id = optStr(o, "id", "ID", "orderId", "checkId");

                // ──── 人员姓名
                // 结构A: "upSiteUsername" | 结构B: "DO_MAN_NAME"（执行人）
                r.workerName = optStr(o, "upSiteUsername", "DO_MAN_NAME",
                        "offSiteUsername", "workerName", "personName", "userName");

                // ──── 业务类型（用于合格判断辅助）
                // 结构A: "businessType"="1"/"2" | 结构B: "BUSI_TYPE"="1"
                r.businessType = optStr(o, "businessType", "BUSI_TYPE", "workType", "type");

                // ──── 任务内容（含"巡检"/"维修"关键词，决定合格线）
                // 结构A: "taskContent" | 结构B: "TASK_NAME"（任务名）+ "SELF_TASK_NAME"（打卡类型）
                r.taskContent = optStr(o, "taskContent", "TASK_NAME", "SELF_TASK_NAME",
                        "workContent", "content", "remark");

                // ──── 工单状态
                // 结构A: "status"="已归档" | 结构B: "STATUS"="11"（数字，忽略）
                r.workStatus = optStr(o, "status", "STATUS", "workStatus");

                // ──── 进场/离场时间
                // 结构A: "upSiteTime" / "offSiteTime"
                // 结构B: "事前打卡时间" / "事后打卡时间"（中文字段名）
                r.startTime = optStr(o, "upSiteTime", "事前打卡时间",
                        "startTime", "clockInTime", "beginTime", "createTime");
                r.endTime   = optStr(o, "offSiteTime", "事后打卡时间",
                        "endTime", "clockOutTime", "finishTime");

                // ──── 站址名称
                // 结构A: "siteName" | 结构B: "SITE_NAME"
                r.stationName = optStr(o, "siteName", "SITE_NAME", "stationName",
                        "stName", "siteAddress");

                // ──── 计算时长（兼容两种格式）
                r.durationMinutes = calcDurationMultiFmt(r.startTime, r.endTime, sdf, sdfMs);

                list.add(r);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "parseRecords failed", e);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 按人员汇总统计
    // ─────────────────────────────────────────────────────────────────────
    public static List<PersonStats> aggregateByPerson(List<CheckRecord> records) {
        // LinkedHashMap 保持插入顺序
        Map<String, PersonStats> map = new LinkedHashMap<>();
        // 用于统计天数：name -> set of "yyyy-MM-dd"
        Map<String, java.util.Set<String>> dayMap = new LinkedHashMap<>();

        for (CheckRecord r : records) {
            String name = (r.workerName == null || r.workerName.trim().isEmpty())
                    ? "未知" : r.workerName.trim();

            PersonStats ps = map.get(name);
            if (ps == null) {
                ps = new PersonStats();
                ps.name = name;
                map.put(name, ps);
                dayMap.put(name, new java.util.HashSet<>());
            }

            ps.totalCount++;

            // 统计合格
            if (r.isQualified()) ps.qualifiedCount++;
            else                 ps.unqualifiedCount++;

            // 统计天数（从 startTime 取日期部分）
            if (r.startTime != null && r.startTime.length() >= 10) {
                dayMap.get(name).add(r.startTime.substring(0, 10));
            }

            // 累计时长
            if (r.durationMinutes >= 0) {
                ps.totalDurationMinutes += r.durationMinutes;
                ps.validDurationCount++;
            }
        }

        // 汇总天数和平均时长
        for (Map.Entry<String, PersonStats> e : map.entrySet()) {
            PersonStats ps = e.getValue();
            ps.dayCount = dayMap.get(e.getKey()).size();
            ps.avgDurationMinutes = ps.validDurationCount > 0
                    ? (double) ps.totalDurationMinutes / ps.validDurationCount : -1;
        }

        // 按打卡总次数降序排列
        List<PersonStats> result = new ArrayList<>(map.values());
        result.sort((a, b) -> b.totalCount - a.totalCount);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────

    /** 计算两个时间字符串之间的分钟差，失败返回 -1 */
    private static long calcDuration(String start, String end, SimpleDateFormat sdf) {
        if (start == null || start.isEmpty() || end == null || end.isEmpty()) return -1;
        try {
            Date s = sdf.parse(start);
            Date e = sdf.parse(end);
            if (s == null || e == null) return -1;
            long diff = e.getTime() - s.getTime();
            if (diff < 0) return -1;
            return diff / 60000L;
        } catch (ParseException ex) {
            return -1;
        }
    }

    /**
     * 多格式时间解析（兼容有毫秒/无毫秒两种格式）
     * 结构A: "2026-03-29 16:12:14"
     * 结构B: "2026-03-29 16:12:14"（同，但来源是"事前/事后打卡时间"中文字段）
     */
    private static long calcDurationMultiFmt(String start, String end,
            SimpleDateFormat sdf, SimpleDateFormat sdfMs) {
        if (start == null || start.isEmpty() || end == null || end.isEmpty()) return -1;
        // 先去掉尾部多余的 .000 类后缀（如"2026-03-26 16:24:01.000"→标准化）
        String s2 = start.contains(".") ? start.substring(0, start.indexOf('.')) : start;
        String e2 = end.contains(".")   ? end.substring(0, end.indexOf('.'))     : end;
        return calcDuration(s2.trim(), e2.trim(), sdf);
    }

    /** 按优先级取第一个非空字段 */
    private static String optStr(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "").trim();
            if (!v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }
}

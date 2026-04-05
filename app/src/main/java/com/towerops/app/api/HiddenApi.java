package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 隐患工单 API — OMMS monitorList.xhtml
 *
 * URL: http://omms.chinatowercom.cn:9000/business/hiddenFixMge/monitorList.xhtml
 * 认证: Cookie (JSESSIONID + pwdaToken)
 *
 * 统计维度：
 *   1. 隐患等级（一般隐患 / 较大隐患 / 重大隐患）
 *   2. 隐患状态（待审核 / 已审核通过 / 待验收 / 已关闭 等）
 *   3. 隐患归类（机房机柜 / 开关电源 / 蓄电池 等）
 *   4. 老化分析（录入时间距今：超过7/15/30/90天）
 *
 * 表格字段索引：
 *   [1]序号 [4]隐患编码 [11]隐患状态 [13]站址名称
 *   [25]隐患归类 [27]隐患等级 [38]发现时间 [39]隐患录入时间
 *   [40]发现人 [30]隐患来源 [33]专项名称 [34]六类场景
 */
public class HiddenApi {

    private static final String TAG = "HiddenApi";

    public static final String URL =
            "http://omms.chinatowercom.cn:9000/business/hiddenFixMge/monitorList.xhtml";

    // 列索引
    private static final int COL_CODE       = 4;
    private static final int COL_STATUS     = 11;
    private static final int COL_STATION    = 13;
    private static final int COL_CATEGORY   = 25;
    private static final int COL_LEVEL      = 27;
    private static final int COL_FIND_DATE  = 38;
    private static final int COL_ENTER_DATE = 39;
    private static final int COL_FINDER     = 40;

    // ─── 数据模型 ──────────────────────────────────────────────────────────

    /** 单条隐患记录 */
    public static class HiddenRecord {
        public String code;        // 隐患编码
        public String status;      // 隐患状态
        public String stationName; // 站址名称
        public String category;    // 隐患归类
        public String level;       // 隐患等级
        public String findDate;    // 发现时间
        public String enterDate;   // 隐患录入时间
        public String finder;      // 发现人
        public int    ageDays;     // 距今天数（-1=未知）
    }

    /** 统计结果 */
    public static class QueryResult {
        public List<HiddenRecord> records = new ArrayList<>();
        public int totalCount;

        /** 隐患等级统计 */
        public Map<String, Integer> levelMap = new LinkedHashMap<>();
        /** 隐患状态统计 */
        public Map<String, Integer> statusMap = new LinkedHashMap<>();
        /** 隐患归类统计 */
        public Map<String, Integer> categoryMap = new LinkedHashMap<>();

        /** 老化桶（录入时间距今） */
        public int over90Days;
        public int over30Days;
        public int over15Days;
        public int over7Days;
        public int within7Days;

        public String errorMsg;
        public boolean success;
    }

    // ─── 查询入口 ──────────────────────────────────────────────────────────

    /**
     * 查询隐患工单列表并统计（查全部，不按日期过滤）
     *
     * @param pageSize  拉取条数（建议 200）
     */
    public static QueryResult query(int pageSize) {
        return query("", "", pageSize);
    }

    /** 内部重载，保留 startDate/endDate 参数供 buildRequestBody 使用（传空串 = 不过滤日期） */
    private static QueryResult query(String startDate, String endDate, int pageSize) {
        QueryResult result = new QueryResult();
        Session session = Session.get();

        if (session.ommsCookie == null || session.ommsCookie.isEmpty()) {
            result.errorMsg = "OMMS Cookie 未登录，请先在【门禁系统】Tab 点「OMMS登录」";
            return result;
        }

        // GET 页面，提取 ViewState
        String viewState = fetchViewState(session.ommsCookie);

        // Cookie 失效检测
        if ("__SESSION_EXPIRED__".equals(viewState)) {
            result.errorMsg = "OMMS 登录已超时，请重新在【门禁系统】Tab 点「OMMS登录」";
            return result;
        }
        if (viewState == null) {
            result.errorMsg = "获取 OMMS 页面失败，请检查是否在公司内网";
            return result;
        }

        try {
            String body = buildRequestBody(startDate, endDate, pageSize, viewState);
            // ★ A4J AJAX请求（RichFaces 3.x）：浏览器实际发的是A4J请求，服务器返回<?xml ...><html>格式局部更新
            // 必须带 AJAXREQUEST=queryForm 和 Accept: application/xml，否则服务器返回完整初始化页面（无数据）
            String postHeaders = "Accept: application/xml, text/xml, */*; q=0.01\n"
                    + "Accept-Language: zh-CN,zh;q=0.9\n"
                    + "Content-Type: application/x-www-form-urlencoded; charset=UTF-8\n"
                    + "Host: omms.chinatowercom.cn:9000\n"
                    + "Origin: http://omms.chinatowercom.cn:9000\n"
                    + "Proxy-Connection: keep-alive\n"
                    + "Referer: http://omms.chinatowercom.cn:9000/business/hiddenFixMge/monitorList.xhtml\n"
                    + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36\n"
                    + "X-Requested-With: XMLHttpRequest";
            // ★ 调试：直接获取完整HttpResponse（含状态码）
            HttpUtil.HttpResponse httpResp = HttpUtil.postWithHeaders(URL, body, postHeaders, session.ommsCookie);
            String resp = httpResp != null ? httpResp.body : "";
            int httpCode = httpResp != null ? httpResp.code : -1;
            android.util.Log.d(TAG, "POST resp: code=" + httpCode + " len=" + resp.length()
                    + " vs=" + viewState + " bodyLen=" + body.length());
            if (resp.length() > 0) {
                android.util.Log.d(TAG, "resp前300: " + resp.substring(0, Math.min(300, resp.length())));
            }

            if (resp == null || resp.isEmpty()) {
                result.errorMsg = "OMMS无响应(code=" + httpCode + ")，请检查公司内网";
                return result;
            }
            if (resp.contains("doPrevLogin") || resp.contains("没有登录") || resp.contains("登录已超时")) {
                result.errorMsg = "OMMS 登录已超时，请重新在【门禁系统】Tab 点「OMMS登录」";
                return result;
            }

            List<HiddenRecord> records = parseTable(resp);
            result.records = records;
            result.totalCount = records.size();

            // 调试：解析为空时记录响应摘要
            if (records.isEmpty()) {
                String snippet = resp.length() > 200 ? resp.substring(0, 200) : resp;
                snippet = snippet.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                android.util.Log.w(TAG, "parseTable returned 0 rows. HTML snippet: " + snippet);
                android.util.Log.w(TAG, "HTML length=" + resp.length() + " contains '没有数据'=" + resp.contains("没有数据"));
                if (resp.contains("没有数据")) {
                    result.errorMsg = "服务器返回：没有数据（日期范围内无记录）";
                } else {
                    // 取前120字符原始resp（含标签）给用户看
                    String raw120 = resp.length() > 120 ? resp.substring(0, 120) : resp;
                    result.errorMsg = "解析0条 code=" + httpCode + " len=" + resp.length() + " 头:" + raw120;
                }
            }





            // 当前时间用于老化计算
            long nowMs = System.currentTimeMillis();

            for (HiddenRecord r : records) {
                // 等级统计
                String lv = r.level.isEmpty() ? "未知" : r.level;
                result.levelMap.merge(lv, 1, Integer::sum);
                // 状态统计
                String st = r.status.isEmpty() ? "未知" : r.status;
                result.statusMap.merge(st, 1, Integer::sum);
                // 归类统计
                String cat = r.category.isEmpty() ? "其他" : r.category;
                result.categoryMap.merge(cat, 1, Integer::sum);
                // 老化
                if (r.ageDays >= 0) {
                    if (r.ageDays > 90)      result.over90Days++;
                    else if (r.ageDays > 30) result.over30Days++;
                    else if (r.ageDays > 15) result.over15Days++;
                    else if (r.ageDays > 7)  result.over7Days++;
                    else                     result.within7Days++;
                }
            }

            // 按数量降序
            result.levelMap    = sortByCount(result.levelMap);
            result.statusMap   = sortByCount(result.statusMap);
            result.categoryMap = sortByCount(result.categoryMap);

            result.success = true;

        } catch (Exception e) {
            result.errorMsg = "解析失败: " + e.getMessage();
        }
        return result;
    }

    // ─── 私有工具 ──────────────────────────────────────────────────────────

    /**
     * GET 页面，提取 ViewState
     * ★ 抓包确认：触发字段固定为 queryForm:j_id137（非动态）
     */
    private static String fetchViewState(String cookie) {
        try {
            String getHeaders = "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\n"
                    + "Accept-Language: zh-CN,zh;q=0.9\n"
                    + "Host: omms.chinatowercom.cn:9000\n"
                    + "Origin: http://omms.chinatowercom.cn:9000\n"
                    + "Proxy-Connection: keep-alive\n"
                    + "Referer: http://omms.chinatowercom.cn:9000/business/hiddenFixMge/monitorList.xhtml\n"
                    + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";

            String html = HttpUtil.get(URL, getHeaders, cookie);
            if (html == null || html.isEmpty()) return null;

            if (html.contains("doPrevLogin") || html.contains("uac/login")
                    || html.contains("没有登录") || html.contains("登录已超时")) {
                android.util.Log.w(TAG, "fetchViewState: OMMS Cookie 已失效");
                return "__SESSION_EXPIRED__";
            }

            java.util.regex.Pattern vpat = java.util.regex.Pattern.compile(
                    "name=\"javax\\.faces\\.ViewState\"[^>]*value=\"([^\"]+)\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher vm = vpat.matcher(html);
            String vs = null;
            while (vm.find()) vs = vm.group(1);
            android.util.Log.d(TAG, "fetchViewState: vs=" + vs + " htmlLen=" + html.length());
            return vs;
        } catch (Exception e) {
            android.util.Log.e(TAG, "fetchViewState error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 构建请求 body
     *
     * ★ 日期字段传空串 = 不过滤，查全部记录
     */
    private static String buildRequestBody(String startDate, String endDate,
                                            int pageSize, String viewState) throws Exception {
        // 日期为空时传空，服务器不过滤
        String startDt = startDate == null || startDate.isEmpty() ? ""
                : (startDate.length() <= 10 ? startDate + " 00:00" : startDate);
        String endDt   = endDate == null || endDate.isEmpty() ? ""
                : (endDate.length() <= 10 ? endDate + " 23:59" : endDate);

        // currentDate 格式 MM/yyyy（为空时用当前月份）
        java.text.SimpleDateFormat myFmt = new java.text.SimpleDateFormat("MM/yyyy", java.util.Locale.getDefault());
        String curMY = myFmt.format(new java.util.Date());
        String startMY = curMY, endMY = curMY;
        if (!startDate.isEmpty()) {
            String[] sp = startDate.replaceAll(" .*", "").split("-");
            startMY = sp.length >= 2 ? sp[1] + "/" + sp[0] : curMY;
        }
        if (!endDate.isEmpty()) {
            String[] ep = endDate.replaceAll(" .*", "").split("-");
            endMY = ep.length >= 2 ? ep[1] + "/" + ep[0] : curMY;
        }

        String[][] params = {
            // ★ A4J AJAX 触发标识（必须，否则服务器返回完整初始化页面）
            {"AJAXREQUEST",                                        "queryForm"},
            {"queryForm",                                          "queryForm"},
            {"queryForm:unitHidden",                               ""},
            {"queryForm:selectValue",                              ""},
            {"queryForm:queryApprovalStatus",                      ""},
            {"queryForm:userLeaderId",                             ""},
            {"queryForm:currUserId",                               ""},
            {"queryForm:yhCodeArr",                                ""},
            {"queryForm:siteIdArr",                                ""},
            {"queryForm:stationNameInput",                         ""},
            {"queryForm:deviceNameInput",                          ""},
            {"queryForm:stationCodeInput",                         ""},
            {"queryForm:objtype",                                   ""},
            {"queryForm:devname1",                                  ""},
            {"queryForm:devname2",                                  ""},
            {"queryForm:devname3",                                  ""},
            {"queryForm:devname4",                                  ""},
            {"queryForm:hiddenGroup",                               ""},
            {"queryForm:hiddenClass",                               ""},
            {"queryForm:projectName",                               ""},
            {"queryForm:yhCode",                                    ""},
            {"queryForm:addbillstate",                              ""},
            {"queryForm:addhiddengrade",                            ""},
            {"queryForm:queryTypeHid_hiddenValue",                  ""},
            {"queryForm:findtimeStartInputDate",                    ""},
            {"queryForm:findtimeStartInputCurrentDate",             startMY},
            {"queryForm:findtimeEndInputDate",                      ""},
            {"queryForm:findtimeEndInputCurrentDate",               endMY},
            // ★ 按完成时间查询
            {"queryForm:finishtimeStartInputDate",                  startDt},
            {"queryForm:finishtimeStartInputCurrentDate",           startMY},
            {"queryForm:finishtimeEndInputDate",                    endDt},
            {"queryForm:finishtimeEndInputCurrentDate",             endMY},
            {"queryForm:j_id71",                                    ""},
            {"queryForm:fixBillsn",                                 ""},
            {"queryForm:queryType",                                 "N"},
            {"queryForm:approvalStatus",                            ""},
            {"queryForm:hiddenRecordDateStartInputDate",            ""},
            {"queryForm:hiddenRecordDateStartInputCurrentDate",     startMY},
            {"queryForm:hiddenRecordDateEndInputDate",              ""},
            {"queryForm:hiddenRecordDateEndInputCurrentDate",       endMY},
            {"queryForm:hiddenAttribute",                           ""},
            {"queryForm:hiddenCode",                                ""},
            {"queryForm:dispatchId",                                ""},
            {"queryForm:dispatchName",                              ""},
            {"queryForm:disposeId",                                 ""},
            {"queryForm:disposeName",                               ""},
            {"queryForm:sceneClass",                                ""},
            {"queryForm:attributeClass",                            ""},
            {"queryForm:queryStateId",                              ""},
            {"queryForm:staSecondState",                            ""},
            {"queryForm:hiddenAuditDateStartInputDate",             ""},
            {"queryForm:hiddenAuditDateStartInputCurrentDate",      startMY},
            {"queryForm:hiddenAuditDateEndInputDate",               ""},
            {"queryForm:hiddenAuditDateEndInputCurrentDate",        endMY},
            {"queryForm:auditName",                                 ""},
            {"queryForm:auditLogName",                              ""},
            {"queryForm:j_id126",                                   ""},
            {"queryForm:discoverPersonAccount",                     ""},
            {"queryForm:currPageObjId",                             "0"},
            {"queryForm:pageSizeText",                              String.valueOf(pageSize)},
            {"queryForm:approveReasonIdArrayHidden",                ""},
            {"javax.faces.ViewState",                               viewState},
            // ★ 触发字段（固定，抓包确认）
            {"queryForm:j_id137",                                   "查询"},
            // ★★★ A4J parameters 参数（关键！RichFaces 通过此参数判断是哪个组件触发查询）
            // 来源：monitorList.xhtml 页面里 j_id137 按钮的 onclick=A4J.AJAX.Submit('queryForm',event,{'parameters':{'queryForm:j_id138':'queryForm:j_id138'},...})
            {"queryForm:j_id138",                                   "queryForm:j_id138"},
        };

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String[] kv : params) {
            if (!first) sb.append("&");
            sb.append(URLEncoder.encode(kv[0], "UTF-8"))
              .append("=")
              .append(URLEncoder.encode(kv[1], "UTF-8"));
            first = false;
        }
        return sb.toString();
    }

    /** 解析 HTML 表格 */
    private static List<HiddenRecord> parseTable(String html) {
        List<HiddenRecord> list = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        long nowMs = System.currentTimeMillis();

        try {
            Pattern trPat  = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern tdPat  = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern tagPat = Pattern.compile("<[^>]+>");
            Matcher trMat  = trPat.matcher(html);
            int totalRows = 0, skippedCols = 0, skippedDigit = 0;

            while (trMat.find()) {
                String rowHtml = trMat.group(1);
                List<String> cells = new ArrayList<>();
                Matcher tdMat = tdPat.matcher(rowHtml);
                while (tdMat.find()) {
                    String raw = tagPat.matcher(tdMat.group(1)).replaceAll("");
                    cells.add(raw.replaceAll("\\s+", " ").trim());
                }
                totalRows++;
                if (cells.size() < 20) { skippedCols++; continue; }
                if (!cells.get(1).matches("\\d+")) { skippedDigit++; continue; }

                HiddenRecord r = new HiddenRecord();
                r.code        = safeGet(cells, COL_CODE);
                r.status      = safeGet(cells, COL_STATUS);
                r.stationName = safeGet(cells, COL_STATION);
                r.category    = safeGet(cells, COL_CATEGORY);
                r.level       = safeGet(cells, COL_LEVEL);
                r.findDate    = safeGet(cells, COL_FIND_DATE);
                r.enterDate   = safeGet(cells, COL_ENTER_DATE);
                r.finder      = safeGet(cells, COL_FINDER);

                // 计算老化天数
                r.ageDays = -1;
                String dateStr = r.enterDate.isEmpty() ? r.findDate : r.enterDate;
                if (!dateStr.isEmpty()) {
                    try {
                        // 支持 "yyyy-MM-dd HH:mm:ss" 和 "yyyy-MM-dd HH:mm"
                        String ds = dateStr.length() == 16 ? dateStr + ":00" : dateStr;
                        Date d = sdf.parse(ds);
                        if (d != null) {
                            r.ageDays = (int) ((nowMs - d.getTime()) / (1000L * 3600 * 24));
                        }
                    } catch (Exception ignored) {}
                }
                list.add(r);
            }
            android.util.Log.d(TAG, "parseTable: totalRows=" + totalRows
                    + " skippedCols=" + skippedCols
                    + " skippedDigit=" + skippedDigit
                    + " parsed=" + list.size());
        } catch (Exception e) {
            android.util.Log.e(TAG, "parseTable error: " + e.getMessage());
        }
        return list;
    }

    private static String safeGet(List<String> cells, int idx) {
        return (idx < cells.size()) ? cells.get(idx) : "";
    }

    private static Map<String, Integer> sortByCount(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) result.put(e.getKey(), e.getValue());
        return result;
    }
}

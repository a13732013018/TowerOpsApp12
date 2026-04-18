package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日常维修 API — OMMS 隐患维修单统计
 *
 * URL: http://omms.chinatowercom.cn:9000/business/hiddenFixMge/fixBillList.xhtml
 * 认证: Cookie (JSESSIONID + pwdaToken)
 * 查询：queryFlag=已归档，日期范围查询
 *
 * 返回数据按【维修人】统计：
 *   - 总费用 / 有效费用 / 无效费用
 *   - 各隐患归类的金额分布
 */
public class RepairApi {

    private static final String TAG = "RepairApi";

    public static final String URL =
            "http://omms.chinatowercom.cn:9000/business/hiddenFixMge/fixBillList.xhtml";

    // ─── 数据模型 ──────────────────────────────────────────────────────────

    /** 单条维修记录 */
    public static class RepairRecord {
        public String billCode;       // 维修单编码
        public String status;         // 维修单状态
        public String worker;         // 维修人（索引39）
        public String category;       // 隐患归类（索引22）
        public String subCategory;    // 隐患子类（索引23）
        public String level;          // 隐患等级（索引26）
        public String attr;           // 隐患属性（索引27）
        public double cost;           // 实际处理费用（索引42）
        public boolean isInvalid;     // 是否无效（索引49，"是"=无效）
        public String stationName;    // 站址名称（索引14）
        public String findDate;       // 发现时间（索引34）
    }

    /** 按人统计汇总 */
    public static class WorkerStats {
        public String name;
        public int    count;          // 维修单条数
        public double totalCost;      // 总费用
        public double validCost;      // 有效费用（非无效）
        public double invalidCost;    // 无效费用
        public int    invalidCount;   // 无效条数
        /** 各隐患归类 → 金额 */
        public Map<String, Double> categoryMap = new LinkedHashMap<>();
    }

    /** 查询结果 */
    public static class QueryResult {
        public List<RepairRecord> records = new ArrayList<>();
        public List<WorkerStats>  workerStats = new ArrayList<>();
        /** 全局隐患归类汇总 */
        public Map<String, Double> globalCategoryMap = new LinkedHashMap<>();
        public double totalCostAll;
        public double validCostAll;
        public double invalidCostAll;
        public int    totalCount;
        public String errorMsg;
        public boolean success;
    }

    // ─── 字段索引（表格列顺序，与抓包表头对齐）──────────────────────────
    // 序号[1], 维修单编码[2], 维修单状态[3], 处理人[4], 当前处理人[5],
    // 隐患来源[6], 省份[7], 地市[8], 区县[9], 省[10], 市[11], 区[12], 镇[13],
    // 站址名称[14], 站址编码[15], 站址运维ID[16], 站址二级状态[17],
    // 设备名称[18], 设备运维ID[19], 对象类型[20], 隐患设备[21],
    // 隐患归类[22], 隐患子类[23], 隐患内容[24], 隐患描述[25],
    // 隐患等级[26], 隐患属性[27], 隐患标准编码[28], 隐患是否属实[29],
    // 专项名称[30], 六类场景[31], 六类隐患[32], 维修次数[33],
    // 发现时间[34], 隐患录入时间[35], 受理时间[36], 维修方式[37],
    // 维修厂家[38], 维修人[39], 维修地市[40], 结算价格[41],
    // 实际处理费用[42], 是否走代维费[43], 计费方式[44],
    // 是否生成台账[45], 是否支付[46], 财务实际支付金额[47], 财务支付单据编号[48],
    // 是否无效[49], 原因[50], ...
    private static final int COL_BILL_CODE   = 2;
    private static final int COL_STATUS      = 3;
    private static final int COL_STATION     = 14;
    private static final int COL_CATEGORY    = 22;
    private static final int COL_SUB_CAT     = 23;
    private static final int COL_LEVEL       = 26;
    private static final int COL_ATTR        = 27;
    private static final int COL_WORKER      = 39;
    private static final int COL_COST        = 42;
    private static final int COL_INVALID     = 49;
    private static final int COL_FIND_DATE   = 34;

    // ─── 请求构建 ─────────────────────────────────────────────────────────

    /**
     * 查询已归档维修单，按维修人统计费用
     *
     * @param startDate 开始日期，格式 "2026-03-01"
     * @param endDate   结束日期，格式 "2026-03-30"
     * @param pageSize  每页条数，建议 200 拉取全量
     */
    public static QueryResult query(String startDate, String endDate, int pageSize) {
        QueryResult result = new QueryResult();
        Session session = Session.get();

        android.util.Log.d(TAG, "========== RepairApi.query 开始 ==========");
        android.util.Log.d(TAG, "Session.ommsCookie: " + (session.ommsCookie == null ? "NULL" : 
                (session.ommsCookie.isEmpty() ? "EMPTY" : 
                ("长度=" + session.ommsCookie.length() + ", hasJSESSIONID=" + session.ommsCookie.contains("JSESSIONID") 
                + ", hasPwdaToken=" + session.ommsCookie.contains("pwdaToken")
                + ", 前100字符=" + session.ommsCookie.substring(0, Math.min(100, session.ommsCookie.length()))))));

        if (session.ommsCookie == null || session.ommsCookie.isEmpty()) {
            result.errorMsg = "OMMS Cookie 未登录，请先在【门禁系统】Tab 点「OMMS登录」";
            android.util.Log.e(TAG, "★★★ RepairApi: OMMS Cookie为空，跳过查询 ★★★");
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
            // ★ 构造 POST 请求头：与 GET 一致，额外添加 Authorization 头传递 pwdaToken
            // 关键：pwdaToken 是 JWT，需要通过 Authorization 头显式传递（参考 TymjLoginActivity）
            StringBuilder headersBuilder = new StringBuilder();
            headersBuilder.append("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\n");
            headersBuilder.append("Accept-Language: zh-CN,zh;q=0.9\n");
            headersBuilder.append("Content-Type: application/x-www-form-urlencoded; charset=UTF-8\n");
            headersBuilder.append("Host: omms.chinatowercom.cn:9000\n");
            headersBuilder.append("Origin: http://omms.chinatowercom.cn:9000\n");
            headersBuilder.append("Connection: keep-alive\n");
            headersBuilder.append("Referer: http://omms.chinatowercom.cn:9000/business/hiddenFixMge/fixBillList.xhtml\n");
            headersBuilder.append("User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36\n");
            // ★ 关键修复：从 Cookie 中提取 pwdaToken，通过 Authorization 头传递
            String pwdaToken = extractPwdaToken(session.ommsCookie);
            if (pwdaToken != null && !pwdaToken.isEmpty()) {
                headersBuilder.append("Authorization: Bearer ").append(pwdaToken).append("\n");
                headersBuilder.append("pwdaToken: ").append(pwdaToken).append("\n");
                android.util.Log.d(TAG, "POST 添加 Authorization 头: " + pwdaToken.substring(0, Math.min(30, pwdaToken.length())) + "...");
            }
            String postHeaders = headersBuilder.toString();
            // ★★★ 关键修复：将Cookie放在headers中传递，与GET请求一致
            HttpUtil.HttpResponse httpResp = HttpUtil.postWithHeaders(URL, body, postHeaders, session.ommsCookie);
            String resp = httpResp != null ? httpResp.body : "";
            int httpCode = httpResp != null ? httpResp.code : -1;
            android.util.Log.d(TAG, "POST resp: code=" + httpCode + " len=" + resp.length()
                    + " vs=" + viewState
                    + " bodyLen=" + body.length());
            // 调试：输出resp前300字符
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

            // 解析 HTML 表格
            List<RepairRecord> records = parseTable(resp);
            result.records = records;
            result.totalCount = records.size();

            // 调试：如果解析为空，记录响应摘要
            if (records.isEmpty()) {
                String snippet = resp.length() > 200 ? resp.substring(0, 200) : resp;
                // 去掉HTML标签，只看文本
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





            // 统计
            Map<String, WorkerStats> workerMap = new LinkedHashMap<>();
            for (RepairRecord r : records) {
                String worker = r.worker.isEmpty() ? "(未填写)" : r.worker;
                WorkerStats ws = workerMap.computeIfAbsent(worker, k -> {
                    WorkerStats s = new WorkerStats();
                    s.name = k;
                    return s;
                });
                ws.count++;
                ws.totalCost += r.cost;
                if (r.isInvalid) {
                    ws.invalidCost += r.cost;
                    ws.invalidCount++;
                } else {
                    ws.validCost += r.cost;
                }
                // 归类金额
                String cat = r.category.isEmpty() ? "其他" : r.category;
                ws.categoryMap.merge(cat, r.cost, Double::sum);

                // 全局归类
                result.globalCategoryMap.merge(cat, r.cost, Double::sum);
                result.totalCostAll   += r.cost;
                if (r.isInvalid) result.invalidCostAll += r.cost;
                else             result.validCostAll   += r.cost;
            }

            // 按总费用降序排列
            result.workerStats = new ArrayList<>(workerMap.values());
            result.workerStats.sort((a, b) -> Double.compare(b.totalCost, a.totalCost));
            // 每个人的归类也按金额降序
            for (WorkerStats ws : result.workerStats) {
                ws.categoryMap = sortByValue(ws.categoryMap);
            }
            result.globalCategoryMap = sortByValue(result.globalCategoryMap);

            result.success = true;

        } catch (Exception e) {
            result.errorMsg = "解析失败: " + e.getMessage();
        }
        return result;
    }

    // ─── 私有工具方法 ─────────────────────────────────────────────────────

    /**
     * GET 页面，提取 ViewState
     * ★ 抓包确认：fixBillList 页面 ViewState 为短 ID（如 j_id407），非动态长串
     *   触发字段固定为 queryForm:searchData（通过抓包确认，非动态）
     */
    private static String fetchViewState(String cookie) {
        try {
            android.util.Log.d(TAG, "========== RepairApi fetchViewState 开始 ==========");
            android.util.Log.d(TAG, "Cookie详情: len=" + (cookie != null ? cookie.length() : 0)
                    + " hasPwda=" + (cookie != null && cookie.contains("pwdaToken"))
                    + " hasJsid=" + (cookie != null && cookie.contains("JSESSIONID"))
                    + " hasAcctId=" + (cookie != null && cookie.contains("acctId")));
            android.util.Log.d(TAG, "Cookie完整内容: " + (cookie != null ? cookie : "NULL"));

            String getHeaders = "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\n"
                    + "Accept-Language: zh-CN,zh;q=0.9\n"
                    + "Host: omms.chinatowercom.cn:9000\n"
                    + "Origin: http://omms.chinatowercom.cn:9000\n"
                    + "Proxy-Connection: keep-alive\n"
                    + "Referer: http://omms.chinatowercom.cn:9000/business/hiddenFixMge/fixBillList.xhtml\n"
                    + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";

            android.util.Log.d(TAG, "准备发送GET请求到: " + URL);
            String html = HttpUtil.get(URL, getHeaders, cookie);
            android.util.Log.d(TAG, "========== RepairApi fetchViewState GET完成 ==========");
            android.util.Log.d(TAG, "html len=" + (html != null ? html.length() : 0));
            android.util.Log.d(TAG, "html前300: " + (html != null ? html.substring(0, Math.min(300, html.length())) : "NULL"));

            if (html == null || html.isEmpty()) {
                android.util.Log.e(TAG, "★★★ fetchViewState: HTML为空/null，可能网络不通或Cookie无效 ★★★");
                return null;
            }

            if (html.contains("doPrevLogin") || html.contains("uac/login")
                    || html.contains("没有登录") || html.contains("登录已超时")) {
                android.util.Log.w(TAG, "★★★ fetchViewState: OMMS Cookie 已失效（被重定向到登录页）★★★");
                return "__SESSION_EXPIRED__";
            }

            // 提取 ViewState
            java.util.regex.Pattern vpat = java.util.regex.Pattern.compile(
                    "name=\"javax\\.faces\\.ViewState\"[^>]*value=\"([^\"]+)\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher vm = vpat.matcher(html);
            String vs = null;
            while (vm.find()) vs = vm.group(1);
            android.util.Log.d(TAG, "ViewState提取结果: " + (vs != null ? ("长度=" + vs.length() + ", 内容=" + vs) : "NULL"));
            return vs;
        } catch (Exception e) {
            android.util.Log.e(TAG, "fetchViewState EXCEPTION: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 构建 POST body
     *
     * ★ 抓包确认的 RichFaces 3.x 请求格式：
     *   - 触发字段固定：queryForm:searchData（固定名称，非动态 j_id）
     *   - 日期字段：starttimeInputDate / endtimeInputDate，格式 "yyyy-MM-dd HH:mm"
     *   - 归档标志：queryFlagHidden=已归档（单选按钮 queryFlag 同步）
     *   - 不带 AJAXREQUEST（普通表单POST，服务器返回完整HTML，从中解析数据表格）
     */
    private static String buildRequestBody(String startDate, String endDate,
                                            int pageSize, String viewState) throws Exception {
        // 日期格式：yyyy-MM-dd → yyyy-MM-dd HH:mm（OMMS 日历控件要求带时间）
        String startDt = startDate + " 00:00";
        String endDt   = endDate   + " 23:59";
        // currentDate 格式：MM/yyyy
        String[] sp = startDate.split("-");
        String[] ep = endDate.split("-");
        String startMY = sp[1] + "/" + sp[0];
        String endMY   = ep[1] + "/" + ep[0];

        StringBuilder sb = new StringBuilder();
        String[][] params = {
            // ★ A4J AJAX 触发标识（必须，否则服务器返回完整初始化页面）
            {"AJAXREQUEST",                                  "queryForm"},
            // 表单 ID
            {"queryForm",                                    "queryForm"},
            // 各 hidden 字段（抓包原值）
            {"queryForm:unitHidden",                         ""},
            {"queryForm:workType",                           ""},
            {"queryForm:stationCodeInput",                   ""},
            {"queryForm:j_id15",                             ""},
            {"queryForm:stationNameInput",                   ""},
            {"queryForm:deviceNameInput",                    ""},
            {"queryForm:queryCrewProvinceId",                ""},
            {"queryForm:queryCrewCityId",                    ""},
            {"queryForm:queryCrewAreaId",                    ""},
            {"queryForm:queryCrewVillageId",                 ""},
            {"queryForm:hideFlag",                           ""},
            {"queryForm:queryCrewVillageName",               ""},
            // ★ 日期（带时间，yyyy-MM-dd HH:mm）
            {"queryForm:starttimeInputDate",                 startDt},
            {"queryForm:starttimeInputCurrentDate",          startMY},
            {"queryForm:endtimeInputDate",                   endDt},
            {"queryForm:endtimeInputCurrentDate",            endMY},
            {"queryForm:dealTimeStartInputDate",             startDt},
            {"queryForm:dealTimeStartInputCurrentDate",      startMY},
            {"queryForm:dealTimeEndInputDate",               endDt},
            {"queryForm:dealTimeEndInputCurrentDate",        endMY},
            {"queryForm:deleteproviceIdHidden",              ""},
            {"queryForm:deletecityIdHidden",                 ""},
            {"queryForm:deletecountryIdHidden",              ""},
            {"queryForm:queryDeleteCountyName",              ""},
            // ★ 归档标志
            {"queryForm:queryFlagHidden",                    "已归档"},
            {"queryForm:queryFlag",                          "已归档"},
            {"queryForm:querySiteDelHidden",                 "否"},
            {"queryForm:querySiteDel",                       "否"},
            {"queryForm:hiddenRecordDateStartInputDate",     ""},
            {"queryForm:hiddenRecordDateStartInputCurrentDate", startMY},
            {"queryForm:hiddenRecordDateEndInputDate",       ""},
            {"queryForm:hiddenRecordDateEndInputCurrentDate",endMY},
            // 分页
            {"queryForm:currPageObjId",                      "0"},
            {"queryForm:pageSizeText",                       String.valueOf(pageSize)},
            // ViewState
            {"javax.faces.ViewState",                        viewState},
            // ★ 触发字段（固定，抓包确认）
            {"queryForm:searchData",                         "查询"},
            // ★★★ A4J parameters 参数（关键！RichFaces 通过此参数判断是哪个组件触发查询）
            // 来源：fixBillList.xhtml 页面里 searchData 按钮的 onclick=A4J.AJAX.Submit('queryForm',event,{'parameters':{'queryForm:j_id136':'queryForm:j_id136'},...})
            {"queryForm:j_id136",                            "queryForm:j_id136"},
        };

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

    /** 解析 HTML 表格，提取数据行（正则解析，无需 Jsoup）*/
    private static List<RepairRecord> parseTable(String html) {
        List<RepairRecord> list = new ArrayList<>();
        try {
            // 找所有 <tr>...</tr>
            Pattern trPat  = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern tdPat  = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern tagPat = Pattern.compile("<[^>]+>");
            Matcher trMat = trPat.matcher(html);
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
                // 序号列（索引1）必须是纯数字
                if (!cells.get(1).matches("\\d+")) { skippedDigit++; continue; }

                RepairRecord r = new RepairRecord();
                r.billCode    = safeCell(cells, COL_BILL_CODE);
                r.status      = safeCell(cells, COL_STATUS);
                r.stationName = safeCell(cells, COL_STATION);
                r.category    = safeCell(cells, COL_CATEGORY);
                r.subCategory = safeCell(cells, COL_SUB_CAT);
                r.level       = safeCell(cells, COL_LEVEL);
                r.attr        = safeCell(cells, COL_ATTR);
                r.worker      = safeCell(cells, COL_WORKER);
                r.findDate    = safeCell(cells, COL_FIND_DATE);
                String costStr = safeCell(cells, COL_COST);
                try { r.cost = costStr.isEmpty() ? 0 : Double.parseDouble(costStr); }
                catch (NumberFormatException ignored) { r.cost = 0; }
                r.isInvalid = "是".equals(safeCell(cells, COL_INVALID));
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

    private static String safeCell(List<String> cells, int idx) {
        return (idx < cells.size()) ? cells.get(idx) : "";
    }

    /** 按 value 降序排序 Map */
    private static Map<String, Double> sortByValue(Map<String, Double> map) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(map.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : entries) result.put(e.getKey(), e.getValue());
        return result;
    }

    /**
     * 从 Cookie 字符串中提取 pwdaToken
     * 参考 TymjLoginActivity 的做法：pwdaToken 需要通过 Authorization 头传递
     */
    private static String extractPwdaToken(String cookie) {
        if (cookie == null || cookie.isEmpty()) return null;
        try {
            // 格式: pwdaToken=eyJhbGc...; 或 pwdaToken=eyJhbGc...
            int idx = cookie.indexOf("pwdaToken=");
            if (idx < 0) {
                android.util.Log.w(TAG, "extractPwdaToken: Cookie中未找到pwdaToken");
                return null;
            }
            int start = idx + "pwdaToken=".length();
            int end = cookie.indexOf(";", start);
            if (end < 0) end = cookie.length();
            String token = cookie.substring(start, end).trim();
            android.util.Log.d(TAG, "extractPwdaToken: 成功提取, 长度=" + token.length()
                    + ", 前30字符=" + token.substring(0, Math.min(30, token.length())));
            return token;
        } catch (Exception e) {
            android.util.Log.e(TAG, "extractPwdaToken 异常: " + e.getMessage());
            return null;
        }
    }
}

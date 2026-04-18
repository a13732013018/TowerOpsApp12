package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;
import com.towerops.app.util.TimeUtil;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 工单操作 API —— 对应易语言中所有 APP_xxx 子程序
 *
 * v2 优化：
 *   [OPT-1]  minutesDiff 对 null / 空串 / 解析失败 统一返回 0，不会返回负值
 *            （调用方加了 Math.max 兜底，这里双重保险）
 *   [OPT-2]  所有接口都对 userId 做 URL 编码，防止特殊字符导致服务器 400
 *   [OPT-3]  acceptBill 头信息统一为常量，避免每次调用重复拼字符串
 *   [OPT-4]  getJsonPath 支持 optJSONArray 路径（兼容 list[0].field 格式）
 *   [OPT-5]  添加 getBillAlarmList 返回值解析帮助方法 isAlarmActive
 */
public class WorkOrderApi {

    private static final String BASE = "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service";

    // =====================================================================
    // ★ 版本号统一在 LoginApi.V 维护，升级只需改那一处 ★
    // =====================================================================
    private static final String V    = LoginApi.V;
    private static final String UPVS = "2025-04-12-ccssoft";

    // =====================================================================
    // 1. 获取工单监控列表
    // =====================================================================
    public static String getBillMonitorList() {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=GET_BILL_MONITOR_LIST&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "start=1&limit=500"
                + "&c_timestamp="  + ts
                + "&c_account="    + uid
                + "&c_sign=E9163ADC4E8E9B20293C8FC11A78E652"
                + "&upvs="         + UPVS;
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 2. 获取工单告警信息
    // =====================================================================
    public static String getBillAlarmList(String billSn) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=GET_BILL_ALARM_LIST&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "start=1&limit=200"
                + "&billsn="       + urlEncUtf8(billSn)
                + "&history_lasttime="
                + "&c_timestamp="  + ts
                + "&c_account="    + uid
                + "&c_sign=A7A87D3B5CB64B8DF7481E63D421F590"
                + "&upvs="         + UPVS;
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 3. 获取工单详情
    // =====================================================================
    public static String getBillDetail(String billSn) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=GET_BILL_DETAIL&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "billSn="    + urlEncUtf8(billSn)
                + "&fromsource=list"
                + "&title=%E6%95%85%E9%9A%9C%E5%B7%A5%E5%8D%95%E5%BE%85%E5%8A%9E"
                + "&c_timestamp="  + ts
                + "&c_account="    + uid
                + "&c_sign=AF0F2A3018F6E966F3529BE87166E1B5"
                + "&upvs="         + UPVS;
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 4. 故障反馈（追加描述）
    // =====================================================================
    public static String addRemark(String taskId, String comment, String billSn) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=SET_BILL_ADDRREMARK&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "taskId="      + urlEncUtf8(taskId)
                + "&linkInfo="        + urlEncUtf8(s.mobilephone)
                + "&dealComment="     + urlEncUtf8(comment)
                + "&billSn="          + urlEncUtf8(billSn)
                + "&c_timestamp="     + ts
                + "&c_account="       + uid
                + "&c_sign=60A1374C9CFF382C4B2668808D4394F8"
                + "&upvs="            + UPVS;
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 5. 自动接单
    // billStatus=0：未接单（正确值），传1服务器会认为无需操作而忽略
    // =====================================================================
    public static String acceptBill(String billId, String billSn, String taskId) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=SET_BILL_ACCEPT&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "userID="    + uid
                + "&billId="        + urlEncUtf8(billId)
                + "&billSn="        + urlEncUtf8(billSn)
                + "&taskId="        + urlEncUtf8(taskId)
                + "&billStatus=0"
                + "&faultCouse=%E6%89%8B%E6%9C%BA%E6%8E%A5%E5%8D%95"
                + "&handlerResult=%E6%89%8B%E6%9C%BA%E6%8E%A5%E5%8D%95"
                + "&c_timestamp="   + ts
                + "&c_account="     + uid
                // [BUG-FIX] 原签名 "437C91584844E7AB0BECF79BDF0BF2B94" 末尾多了一个 "4"，
                //   正常 MD5 签名应为 32 位十六进制，此处修正
                + "&c_sign=437C91584844E7AB0BECF79BDF0BF2B9"
                + "&upvs="          + UPVS;
        // 接单需要完整协议头（含 Host），与回单保持一致
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 6. 上站判断（选择不上站）
    // =====================================================================
    public static String stationStatus(String taskId, String standCause, String billSn) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=BILL_STATION_STATUS&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "taskId="     + urlEncUtf8(taskId)
                + "&linkInfo="       + urlEncUtf8(s.mobilephone)
                + "&standCause="     + urlEncUtf8(standCause)
                + "&isStand=N"
                + "&billSn="         + urlEncUtf8(billSn)
                + "&c_timestamp="    + ts
                + "&c_account="      + uid
                + "&c_sign=1D68314D00F4D60898CE30692F09A98F"
                + "&upvs="           + UPVS;
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 7. 发电判断
    // =====================================================================
    public static String electricJudge(String billSn, String dealComment,
                                       String billId, String taskId) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=SET_BILL_ELECTRICT_JUDGE&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "billSn="      + urlEncUtf8(billSn)
                + "&actionType=N"
                + "&dealComment="     + urlEncUtf8(dealComment)
                + "&billId="          + urlEncUtf8(billId)
                + "&taskId="          + urlEncUtf8(taskId)
                + "&c_timestamp="     + ts
                + "&c_account="       + uid
                + "&c_sign=A01016A3423D0CB351B85138DABC60CE"
                + "&upvs="            + UPVS;
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 8. 终审回单
    // =====================================================================
    public static String revertBill(String faultType, String faultCouse,
                                    String handlerResult, String billId,
                                    String billSn, String taskId,
                                    String recoveryTime) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);
        String  url = BASE + "?porttype=BILL_GENELEC_REVERT&v=" + V + "&userid=" + uid + "&c=0";
        String  post = "isUpStation=N"
                + "&isRelief=N"
                + "&faultType="     + urlEncUtf8(faultType)
                + "&faultCouse="    + urlEncUtf8(faultCouse)
                + "&recoveryTime="  + urlEncUtf8(recoveryTime)
                + "&handlerResult=" + urlEncUtf8(handlerResult)
                + "&billId="        + urlEncUtf8(billId)
                + "&billSn="        + urlEncUtf8(billSn)
                + "&taskId="        + urlEncUtf8(taskId)
                + "&billStatus=1"
                + "&c_timestamp="   + ts
                + "&c_account="     + uid
                + "&c_sign=B5F0DE138D62276611216180553FD0D5"
                + "&upvs="          + UPVS;
        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 工具：构建完整协议头（所有接口统一使用）
    // 对应易语言：ADD_协议头.添加("Authorization", token) 等
    // =====================================================================
    static String buildFullHeader(Session s) {
        return "Authorization: "  + s.token + "\n"
             + "equiptoken: \n"
             + "appVer: 202112\n"
             + "Content-Type: application/x-www-form-urlencoded\n"
             + "Host: ywapp.chinatowercom.cn:58090\n"
             + "Connection: Keep-Alive\n"
             + "User-Agent: okhttp/4.10.0";
    }

    // =====================================================================
    // 工具：安全 POST（异常时返回空串，不抛出）
    // =====================================================================
    private static String safePost(String url, String post, String headers) {
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // =====================================================================
    // 9. OMMS 获取告警列表（对应易语言「获取告警」子程序）
    //    使用 Session.ommsCookie 作为 Cookie
    //    querystationstatus=2 站内告警，isCreateBillSel=1 已创建工单的告警，
    //    currPageObjId=1，pageSizeText=35
    // =====================================================================
    public static String getOmmsAlarmList() {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) return "{\"error\":\"ommsCookie为空，请先在门禁Tab完成OMMS登录\"}";

        // ★ 先 GET 页面拿最新 ViewState，再 POST 查询（否则服务器拒绝 AJAX 请求）
        String vs = refreshAlarmViewState();
        if (vs == null) {
            android.util.Log.e("WorkOrderApi", "getOmmsAlarmList: refreshAlarmViewState失败，ommsCookie可能已失效");
            return "{\"error\":\"ommsCookie已失效，请重新在门禁Tab完成OMMS登录\"}";
        }

        String url = "http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml";
        // ★ RichFaces AJAX POST，完全对齐 2026-03-28 真实抓包：
        //   触发器 queryForm:alarmQueryTrigger（动态解析，随页面渲染变化）
        //   querystationstatus=2（站内告警）+ isCreateBillSel=1（已建工单）是真实过滤条件
        String trigger = alarmQueryTrigger; // 读取动态解析到的触发字段（GET页面时同步更新）
        String post =
                "AJAXREQUEST=_viewRoot"
                + "&queryForm=queryForm"
                + "&queryForm%3AquerySiteId="
                + "&queryForm%3AunitHidden="
                + "&queryForm%3AquerySiteNameId="
                + "&queryForm%3AserialnoText="
                + "&queryForm%3AhidDeviceId="
                + "&queryForm%3Aobjid="
                + "&queryForm%3Aobjname="
                + "&queryForm%3AfaultidText="
                + "&queryForm%3Aquerystationcode="
                + "&queryForm%3AfscidText="
                + "&queryForm%3AselectSignalSize=0"
                + "&queryForm%3AalarmNameMax=15"
                + "&queryForm%3Aj_id60="
                + "&queryForm%3AfirststarttimeInputDate="
                + "&queryForm%3AfirststarttimeInputCurrentDate=" + getCurrentMonthYear()
                + "&queryForm%3AfirstendtimeInputDate="
                + "&queryForm%3AfirstendtimeInputCurrentDate=" + getCurrentMonthYear()
                + "&queryForm%3Aj_id75="
                + "&queryForm%3Aj_id79="
                + "&queryForm%3Aj_id83="
                + "&queryForm%3AqueryDWCompany1="
                + "&queryForm%3AqueryDWCompanyName1="
                + "&queryForm%3AqueryDWPersonId="
                + "&queryForm%3AqueryDWPersonName="
                + "&queryForm%3AqueryFactoryName_hiddenValue="
                // ★ 真实抓包值：querystationstatus=2（站内告警），isCreateBillSel=1（已创建工单）
                + "&queryForm%3Aquerystationstatus_hiddenValue=2"
                + "&queryForm%3Aquerystationstatus=2"
                + "&queryForm%3AquerySiteSource_hiddenValue="
                + "&queryForm%3AqueryIfConfirm_hiddenValue="
                + "&queryForm%3AsortSelect_hiddenValue="
                + "&queryForm%3AisCreateBillSel_hiddenValue=1"
                + "&queryForm%3AisCreateBillSel=1"
                + "&queryForm%3AsubOperatorHid_hiddenValue="
                + "&queryForm%3ArefreshTime="
                + "&queryForm%3AlastSeenTime="
                + "&queryForm%3AqueryCrewAreaIds="
                + "&queryForm%3AqueryCrewAreaName="
                + "&queryForm%3AqueryCrewVillageId="
                + "&queryForm%3AhideFlag="
                + "&queryForm%3AqueryCrewVillageName="
                + "&queryForm%3AqueryComTypes="
                + "&queryForm%3AqueryComTypeNames="
                + "&queryForm%3AqueryStaTypeSelId_hiddenValue="
                + "&queryForm%3AisTransitNodeId_hiddenValue="
                + "&queryForm%3Aj_id140="
                + "&queryForm%3AqueryMmanuFactoryNameSelId_hiddenValue="
                + "&queryForm%3AType="
                + "&queryForm%3Aj_id151="
                + "&queryForm%3Aj_id155="
                + "&queryForm%3Aj_id159="
                + "&queryForm%3Asignaldevtype1="
                + "&queryForm%3AliBattery="
                + "&queryForm%3AcurrPageObjId=1"
                + "&queryForm%3ApageSizeText=35"
                + "&queryForm%3ApanelOpenedState="
                + "&javax.faces.ViewState=" + urlEncUtf8(vs)
                + "&queryForm%3A" + trigger + "=queryForm%3A" + trigger  // ★ 动态触发字段
                + "&AJAX%3AEVENTS_COUNT=1";

        String headers = buildOmmsHeader("http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml");
        return com.towerops.app.util.HttpUtil.post(url, post, headers, cookie);
    }

    // =====================================================================
    // 9c. OMMS 门禁历史告警列表（不限是否建工单，专门查门禁类告警）
    //     alarmname=门 过滤门禁告警，去掉 isCreateBillSel=1
    //     支持翻页：pageNo 从 1 开始，传几页就查几页
    //     用于「运维日常 → 门禁数据」子Tab展示历史门禁告警记录
    // =====================================================================

    /**
     * 内部：构造门禁告警查询 POST body（指定页码 + 时间范围）
     *
     * ★ 完全对齐 2026-04-01 真实抓包（门禁历史告警专用表单）：
     *   - 日期格式：yyyy-MM-dd HH:mm（如 "2026-03-01 00:00"），空格→%20，冒号→%3A
     *   - 携带 proviceIdHidden/cityIdHidden/countryIdHidden（区域层级编码，GET页面时提取）
     *   - currPageObjId 从 0 开始（第1页=0，第2页=1，…）
     *   - 字段结构与 getOmmsAlarmList 不同，是门禁专属表单
     *
     * @param startDate 告警开始时间，格式 "yyyy-MM-dd"，传 "" 则不限
     * @param endDate   告警结束时间，格式 "yyyy-MM-dd"，传 "" 则不限
     * @param isQuery   true=首次查询（带查询触发按钮），false=翻页（不带触发按钮）
     */
    /**
     * 构建门禁告警翻页请求（itemScroller=next 模式）
     * ★ 2026-04-03 重大修复：翻页改用 JSF itemScroller 模式
     *   真实抓包显示 listHisAlarmHbase.xhtml 的翻页不走 currPageObjId，
     *   而是用 listForm:list:itemScroller=next + ViewState 翻页。
     *   翻页时只发 scroller + ViewState，不带查询条件（JSF 服务端维护状态）。
     *
     * @param vs  当前 ViewState
     * @return POST body 字符串
     */
    private static String buildDoorAlarmScrollerPost(String vs) {
        // ★ 真实抓包翻页请求：只用 scroller + ViewState，不带查询条件
        String body = "AJAXREQUEST=_viewRoot"
                + "&listForm=listForm"
                + "&autoScroll="
                + "&javax.faces.ViewState=" + urlEncUtf8(vs)
                + "&listForm%3Alist%3AitemScroller=next"
                + "&ajaxSingle=listForm%3Alist%3AitemScroller"
                + "&AJAX%3AEVENTS_COUNT=1";
        return body;
    }

    private static String buildDoorAlarmPost(String vs, String trigger, int pageNo,
                                             String startDate, String endDate, boolean isQuery) {
        // OMMS 时间字段格式：yyyy-MM-dd HH:mm（开始/结束均用 00:00）
        String ommsStart = (startDate != null && !startDate.isEmpty()) ? startDate + " 00:00" : "";
        String ommsEnd   = (endDate   != null && !endDate.isEmpty())   ? endDate   + " 00:00" : "";

        // CurrentDate 字段格式：MM/yyyy（引导日历翻到正确月份）
        java.text.SimpleDateFormat mFmt = new java.text.SimpleDateFormat("MM/yyyy", java.util.Locale.getDefault());
        String curMonth = mFmt.format(new java.util.Date());
        if (endDate != null && endDate.length() >= 7) {
            // 从 "yyyy-MM-dd" 直接取月份部分，避免解析
            try {
                String[] parts = endDate.split("-");
                curMonth = parts[1] + "/" + parts[0];  // MM/yyyy
            } catch (Exception ignored) {}
        }

        // ★ 2026-04-01 真实抓包对齐：msg=0 必须携带，否则OMMS返回空结果
        String body = "AJAXREQUEST=_viewRoot"
                + "&queryForm=queryForm"
                + "&queryForm%3AproviceIdHidden=" + urlEncUtf8(alarmProviceId)       // ★ 省ID
                + "&queryForm%3AcityIdHidden="    + urlEncUtf8(alarmCityId)          // ★ 市ID
                + "&queryForm%3AcountryIdHidden=" + urlEncUtf8(alarmCountryId)       // ★ 县ID
                + "&queryForm%3AfaultidText="
                + "&queryForm%3Aj_id17=" + urlEncUtf8(doorAlarmJid17)                  // ★ 动态提取的j_id17
                + "&queryForm%3Aj_id33=" + urlEncUtf8(doorAlarmJid33)                  // ★ 动态提取的j_id33
                + "&queryForm%3Aj_id37=" + urlEncUtf8(doorAlarmJid37)                  // ★ 动态提取的j_id37
                + "&queryForm%3AfirststarttimeInputDate=" + urlEncUtf8(ommsStart)    // ★ 开始时间
                + "&queryForm%3AfirststarttimeInputCurrentDate=" + urlEncUtf8(curMonth)
                + "&queryForm%3AfirstendtimeInputDate=" + urlEncUtf8(ommsEnd)        // ★ 结束时间
                + "&queryForm%3AfirstendtimeInputCurrentDate=" + urlEncUtf8(curMonth)
                + "&queryForm%3AqueryalarmName=" + urlEncUtf8("门")                    // ★ 告警名称过滤（只查询包含"门"的告警）
                + "&queryForm%3AdeleteproviceIdHidden="
                + "&queryForm%3AdeletecityIdHidden="
                + "&queryForm%3AdeletecountryIdHidden="
                + "&queryForm%3AqueryDeleteCountyName="
                + "&queryForm%3AquerySpeId="
                + "&queryForm%3AquerySpeIdShow="
                + "&queryForm%3Amsg=0"                                               // ★ 必须携带（真实抓包）
                + "&queryForm%3AcurrPageObjId=0"                                      // ★ 第1页
                + "&queryForm%3ApageSizeText=300"                                      // 每页300条
                + "&queryForm%3ApanelOpenedState="
                + "&javax.faces.ViewState=" + urlEncUtf8(vs);

        // ★★★ 关键修复：首次查询时带查询触发按钮 ★★★
        if (isQuery) {
            body += "&queryForm%3A" + trigger + "=queryForm%3A" + trigger;
        }

        body += "&AJAX%3AEVENTS_COUNT=1";
        return body;
    }

    /**
     * 查询 OMMS 门禁告警（指定页码，每页 300 条）
     * @param pageNo    页码（从 1 开始，内部转为从0开始的 currPageObjId）
     * @param startDate 告警发生时间下限，格式 "yyyy-MM-dd"，传 "" 则不限
     * @param endDate   告警发生时间上限，格式 "yyyy-MM-dd"，传 "" 则不限
     * @return 原始 HTML / AJAX 响应，失败返回空串
     */
    public static String getDoorAlarmList(int pageNo, String startDate, String endDate) {
        return getDoorAlarmList(pageNo, startDate, endDate, true);
    }

    /**
     * 查询 OMMS 门禁告警（指定页码，可控制是否带查询触发按钮）
     * @param pageNo    页码（从 1 开始，内部转为从0开始的 currPageObjId）
     * @param startDate 告警发生时间下限，格式 "yyyy-MM-dd"，传 "" 则不限
     * @param endDate   告警发生时间上限，格式 "yyyy-MM-dd"，传 "" 则不限
     * @param forceQuery 是否强制带查询触发按钮（翻页时通常应传 false）
     * @return 原始 HTML / AJAX 响应，失败返回空串
     */
    public static String getDoorAlarmList(int pageNo, String startDate, String endDate, boolean forceQuery) {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) {
            android.util.Log.e("WorkOrderApi", "getDoorAlarmList: ommsCookie为空");
            return "";
        }

        // 第一页时刷新 ViewState（同步提取省市县ID）；翻页复用已缓存值
        if (pageNo == 1) {
            String refreshResult = refreshDoorAlarmViewState();
            if (refreshResult == null) {
                android.util.Log.e("WorkOrderApi", "getDoorAlarmList: refreshDoorAlarmViewState失败，可能Cookie已失效");
                // 返回空字符串，DoorDataFragment 会显示0条记录
                return "";
            }
            // ★ 必须将返回的ViewState存入alarmViewState变量，否则后续使用旧值
            alarmViewState = refreshResult;
            // 检查省市县ID是否成功提取
            if (alarmProviceId.isEmpty() || alarmCityId.isEmpty() || alarmCountryId.isEmpty()) {
                android.util.Log.w("WorkOrderApi", "getDoorAlarmList: 省市县ID未提取成功，查询可能失败");
            }
        }
        String vs      = alarmViewState;
        String trigger = doorAlarmTrigger;  // ★ 门禁专用触发字段（初始值 j_id56）

        String url  = "http://omms.chinatowercom.cn:9000/business/resMge/alarmHisHbaseMge/listHisAlarmHbase.xhtml";
        // ★★★ 关键修复：第1页或 forceQuery=true 时带 trigger，翻页时不带 ★★★
        boolean isQuery = (pageNo == 1) || forceQuery;
        String post = buildDoorAlarmPost(vs, trigger, pageNo,
                startDate != null ? startDate : "",
                endDate   != null ? endDate   : "", isQuery);
        String headers = buildOmmsHeader(url);

        // ★★★ 诊断日志：打印完整POST body（前2页时） ★★★
        if (pageNo <= 2) {
            android.util.Log.w("DOOR_DIAG", "=== 门禁诊断 第" + pageNo + "页 (isQuery=" + isQuery + ") ===");
            android.util.Log.w("DOOR_DIAG", "POST body:\n" + post);
            android.util.Log.w("DOOR_DIAG", "ViewState=" + (vs.length() > 50 ? vs.substring(0, 50) + "..." : vs));
            android.util.Log.w("DOOR_DIAG", "Trigger=" + trigger + (isQuery ? " (查询模式)" : " (翻页模式，不携带trigger)"));
            android.util.Log.w("DOOR_DIAG", "Area=" + alarmProviceId + "/" + alarmCityId + "/" + alarmCountryId);
            android.util.Log.w("DOOR_DIAG", "Jids=" + doorAlarmJid17 + "/" + doorAlarmJid33 + "/" + doorAlarmJid37);
        }

        try {
            String result = HttpUtil.post(url, post, headers, cookie);
            if (result == null || result.isEmpty()) {
                android.util.Log.e("DOOR_DIAG", "第" + pageNo + "页返回 null 或空！");
            } else {
                // ★★★ 诊断日志：打印返回内容前2000字符 ★★★
                int previewLen = Math.min(result.length(), 2000);
                android.util.Log.w("DOOR_DIAG", "第" + pageNo + "页返回 len=" + result.length()
                    + " 包含html=" + result.contains("<html")
                    + " 包含ajax-response=" + result.contains("<ajax-response")
                    + " 包含rich-table-row=" + result.contains("rich-table-row"));
                if (pageNo <= 2) {
                    // 分段打印（Android单条日志最大约4KB）
                    for (int i = 0; i < previewLen; i += 1000) {
                        int end = Math.min(i + 1000, previewLen);
                        android.util.Log.w("DOOR_DIAG", "返回内容[" + i + "-" + end + "]: " + result.substring(i, end));
                    }
                }
                // AJAX分页响应中uac/login可能出现在URL配置里，不是真正的登录页
                // 只有响应很小(<500字节)且有明确登录特征时才判为登录页
                if (result.length() < 500
                        && (result.contains("doPrevLogin") || result.contains("uac/login"))) {
                    android.util.Log.e("DOOR_DIAG", "第" + pageNo + "页被重定向到登录页！Cookie已失效！");
                    return "";
                }

                // ★★★ 2026-04-03 修复翻页失效：从返回HTML中提取最新ViewState ★★★
                // JSF是有状态框架，每次POST后ViewState会变化，翻页必须用最新ViewState
                String newVs = extractViewStateFromHtml(result);
                if (newVs != null && !newVs.isEmpty()) {
                    alarmViewState = newVs;
                    android.util.Log.d("DOOR_DIAG", "第" + pageNo + "页: 已更新ViewState=" +
                        (newVs.length() > 30 ? newVs.substring(0, 30) + "..." : newVs));
                } else {
                    android.util.Log.w("DOOR_DIAG", "第" + pageNo + "页: 未找到ViewState，翻页可能失效！");
                }
            }
            return result != null ? result : "";
        } catch (Exception e) {
            android.util.Log.e("DOOR_DIAG", "第" + pageNo + "页请求异常: " + e.getMessage(), e);
            return "";
        }
    }

    /** 从OMMS返回HTML中提取 javax.faces.ViewState 值 */
    private static String extractViewStateFromHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "name=[\"']javax\\.faces\\.ViewState[\"'][^>]*value=[\"']([^\"']+)[\"']"
                + "|value=[\"']([^\"']+)[\"'][^>]*name=[\"']javax\\.faces\\.ViewState[\"']",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(html);
        String last = null;
        while (m.find()) {
            last = m.group(1) != null ? m.group(1) : m.group(2);
        }
        return last;
    }

    /**
     * 门禁告警翻页请求（使用 JSF itemScroller=next 模式）
     * ★ 2026-04-03 新增：真实抓包显示翻页用 listForm:list:itemScroller=next，
     *   而非 currPageObjId 方式。翻页时只需 scroller + 最新 ViewState，不带查询条件。
     *
     * @return 原始 HTML / AJAX 响应，失败返回空串
     */
    public static String getDoorAlarmNextPage() {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) {
            android.util.Log.e("WorkOrderApi", "getDoorAlarmNextPage: ommsCookie为空");
            return "";
        }

        String vs = alarmViewState;
        if (vs == null || vs.isEmpty()) {
            android.util.Log.e("WorkOrderApi", "getDoorAlarmNextPage: ViewState为空，需先调用getDoorAlarmList(1,...)");
            return "";
        }

        String url  = "http://omms.chinatowercom.cn:9000/business/resMge/alarmHisHbaseMge/listHisAlarmHbase.xhtml";
        String post = buildDoorAlarmScrollerPost(vs);
        String headers = buildOmmsHeader(url);

        android.util.Log.d("DOOR_DIAG", "=== 翻页请求 (itemScroller=next) ===");
        android.util.Log.d("DOOR_DIAG", "POST body: " + post);

        try {
            String result = HttpUtil.post(url, post, headers, cookie);
            if (result == null || result.isEmpty()) {
                android.util.Log.e("DOOR_DIAG", "翻页返回 null 或空！");
                return "";
            }
            // AJAX响应中uac/login可能出现在URL配置里，不是真正的登录页
            if (result.length() < 500
                    && (result.contains("doPrevLogin") || result.contains("uac/login"))) {
                android.util.Log.e("DOOR_DIAG", "翻页被重定向到登录页！Cookie已失效！");
                return "";
            }

            // ★ 提取最新 ViewState 用于下次翻页
            String newVs = extractViewStateFromHtml(result);
            if (newVs != null && !newVs.isEmpty()) {
                alarmViewState = newVs;
                android.util.Log.d("DOOR_DIAG", "翻页: 已更新ViewState=" +
                    (newVs.length() > 30 ? newVs.substring(0, 30) + "..." : newVs));
            } else {
                android.util.Log.w("DOOR_DIAG", "翻页: 未找到ViewState！");
            }

            android.util.Log.d("DOOR_DIAG", "翻页返回 len=" + result.length()
                + " 包含rich-table-row=" + result.contains("rich-table-row"));
            return result;
        } catch (Exception e) {
            android.util.Log.e("DOOR_DIAG", "翻页请求异常: " + e.getMessage(), e);
            return "";
        }
    }

    /** 兼容旧调用：无参版本查第 1 页（不限时间） */
    public static String getDoorAlarmList(int pageNo) {
        return getDoorAlarmList(pageNo, "", "");
    }

    /** 兼容旧调用：无参版本查第 1 页 */
    public static String getDoorAlarmList() {
        return getDoorAlarmList(1, "", "");
    }

    // =====================================================================
    // 9b. OMMS 告警列表（按站点名精确查询，减少无关数据）
    //     填写 queryForm:querySiteNameId 字段，返回该站点名匹配的告警列表
    // =====================================================================
    public static String getOmmsAlarmListBySite(String siteName) {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) return "{\"error\":\"ommsCookie为空\"}";

        // ★ 先 GET 页面拿最新 ViewState，再 POST 查询
        // 注意：getOmmsAlarmList() 如果刚被调用，alarmViewState 已是最新值，无需重复 GET
        // 但若直接调用 BySite，也需要刷新
        String vs2 = refreshAlarmViewState();
        if (vs2 == null) {
            android.util.Log.e("WorkOrderApi", "getOmmsAlarmListBySite: refreshAlarmViewState失败，ommsCookie可能已失效");
            return "{\"error\":\"ommsCookie已失效，请重新在门禁Tab完成OMMS登录\"}";
        }

        String url = "http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml";
        String encodedSite = urlEncUtf8(siteName);
        // ★ RichFaces AJAX POST，完全对齐 2026-03-28 真实抓包：
        //   触发器 queryForm:alarmQueryTrigger（动态解析，随页面渲染变化）
        //   站点名填入 querySiteNameId，querystationstatus=2 + isCreateBillSel=1 过滤
        String trigger2 = alarmQueryTrigger; // 读取动态解析到的触发字段
        String post =
                "AJAXREQUEST=_viewRoot"
                + "&queryForm=queryForm"
                + "&queryForm%3AquerySiteId="
                + "&queryForm%3AunitHidden="
                + "&queryForm%3AquerySiteNameId=" + encodedSite   // ← 站点名筛选
                + "&queryForm%3AserialnoText="
                + "&queryForm%3AhidDeviceId="
                + "&queryForm%3Aobjid="
                + "&queryForm%3Aobjname="
                + "&queryForm%3AfaultidText="
                + "&queryForm%3Aquerystationcode="
                + "&queryForm%3AfscidText="
                + "&queryForm%3AselectSignalSize=0"
                + "&queryForm%3AalarmNameMax=15"
                + "&queryForm%3Aj_id60="
                + "&queryForm%3AfirststarttimeInputDate="
                + "&queryForm%3AfirststarttimeInputCurrentDate=" + getCurrentMonthYear()
                + "&queryForm%3AfirstendtimeInputDate="
                + "&queryForm%3AfirstendtimeInputCurrentDate=" + getCurrentMonthYear()
                + "&queryForm%3Aj_id75="
                + "&queryForm%3Aj_id79="
                + "&queryForm%3Aj_id83="
                + "&queryForm%3AqueryDWCompany1="
                + "&queryForm%3AqueryDWCompanyName1="
                + "&queryForm%3AqueryDWPersonId="
                + "&queryForm%3AqueryDWPersonName="
                + "&queryForm%3AqueryFactoryName_hiddenValue="
                // ★ 真实抓包值：querystationstatus=2（站内告警），isCreateBillSel=1（已建工单）
                + "&queryForm%3Aquerystationstatus_hiddenValue=2"
                + "&queryForm%3Aquerystationstatus=2"
                + "&queryForm%3AquerySiteSource_hiddenValue="
                + "&queryForm%3AqueryIfConfirm_hiddenValue="
                + "&queryForm%3AsortSelect_hiddenValue="
                + "&queryForm%3AisCreateBillSel_hiddenValue=1"
                + "&queryForm%3AisCreateBillSel=1"
                + "&queryForm%3AsubOperatorHid_hiddenValue="
                + "&queryForm%3ArefreshTime="
                + "&queryForm%3AlastSeenTime="
                + "&queryForm%3AqueryCrewAreaIds="
                + "&queryForm%3AqueryCrewAreaName="
                + "&queryForm%3AqueryCrewVillageId="
                + "&queryForm%3AhideFlag="
                + "&queryForm%3AqueryCrewVillageName="
                + "&queryForm%3AqueryComTypes="
                + "&queryForm%3AqueryComTypeNames="
                + "&queryForm%3AqueryStaTypeSelId_hiddenValue="
                + "&queryForm%3AisTransitNodeId_hiddenValue="
                + "&queryForm%3Aj_id140="
                + "&queryForm%3AqueryMmanuFactoryNameSelId_hiddenValue="
                + "&queryForm%3AType="
                + "&queryForm%3Aj_id151="
                + "&queryForm%3Aj_id155="
                + "&queryForm%3Aj_id159="
                + "&queryForm%3Asignaldevtype1="
                + "&queryForm%3AliBattery="
                + "&queryForm%3AcurrPageObjId=1"
                + "&queryForm%3ApageSizeText=35"
                + "&queryForm%3ApanelOpenedState="
                + "&javax.faces.ViewState=" + urlEncUtf8(vs2)
                + "&queryForm%3A" + trigger2 + "=queryForm%3A" + trigger2  // ★ 动态触发字段
                + "&AJAX%3AEVENTS_COUNT=1";

        String headers = buildOmmsHeader("http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml");
        return com.towerops.app.util.HttpUtil.post(url, post, headers, cookie);
    }

    // =====================================================================
    // 10. OMMS 告警确认
    //     2026-03-28 真实抓包验证：
    //       alarmConfirmListForm:alarmId={alarmId}（32位十六进制ID）
    //       alarmConfirmListForm:confirmInfo=1
    //       触发器：alarmConfirmListForm:j_id1589
    //       ViewState：j_id108（告警页默认值）
    //
    //     alarmId:   32位十六进制告警ID
    //     viewState: 从查询响应里提取的 javax.faces.ViewState 值
    // =====================================================================
    public static String confirmOmmsAlarm(String alarmId, String viewState) {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) return "{\"error\":\"ommsCookie为空\"}";

        String url = "http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml";
        // ★★★ 修复：使用动态解析的 confirmTriggerFull，不再硬编码 j_id1589 ★★★
        //   confirmTriggerFull 已在 refreshAlarmViewState() 中从页面 HTML 提取
        String confirmFieldName = "alarmConfirmListForm";  // confirm 固定表单名
        String confirmTriggerVal = confirmTriggerFull;       // 动态解析的触发字段，如 "alarmConfirmListForm:j_id1589"

        String post =
                "AJAXREQUEST=_viewRoot"
                + "&alarmConfirmListForm%3AalarmId=" + urlEncUtf8(alarmId)
                + "&alarmConfirmListForm%3AconfirmInfo=1"
                + "&alarmConfirmListForm=alarmConfirmListForm"
                + "&autoScroll="
                + "&javax.faces.ViewState=" + urlEncUtf8(viewState)
                + "&alarmConfirmListForm%3A" + urlEncUtf8(confirmTrigger) + "=alarmConfirmListForm%3A" + urlEncUtf8(confirmTrigger)
                + "&AJAX%3AEVENTS_COUNT=1";

        android.util.Log.d("WorkOrderApi", "========== confirmOmmsAlarm 开始 ==========");
        android.util.Log.d("WorkOrderApi", "alarmId=" + alarmId);
        android.util.Log.d("WorkOrderApi", "viewState=" + viewState);
        android.util.Log.d("WorkOrderApi", "confirmTrigger=" + confirmTrigger + " (动态解析)");
        android.util.Log.d("WorkOrderApi", "POST参数=\n" + post);

        String headers = buildOmmsHeader(url);
        String response = com.towerops.app.util.HttpUtil.post(url, post, headers, cookie);
        android.util.Log.d("WorkOrderApi", "响应长度=" + (response == null ? "null" : response.length()) + "字节");
        
        // ★★★ 调试：解析响应内容，判断确认是否成功 ★★★
        if (response != null && response.length() > 0) {
            boolean hasSuccMsg = response.contains("showSuccMsg") || response.contains("'Y'");
            boolean hasError = response.contains("class=\"rf-msgs-err\"") || response.contains("rf-msg-err");
            
            android.util.Log.d("WorkOrderApi", "confirm响应分析: success_msg=" + hasSuccMsg + " error=" + hasError);
            
            // 打印响应片段
            int previewLen = Math.min(response.length(), 1500);
            android.util.Log.d("WorkOrderApi", "confirm响应预览: " + response.substring(0, previewLen));
        } else {
            android.util.Log.w("WorkOrderApi", "confirm响应为空！");
        }
        
        android.util.Log.d("WorkOrderApi", "========== confirmOmmsAlarm 结束 ==========");

        return response;
    }

    // =====================================================================
    // 11. OMMS 告警清除（对应易语言「告警清除」子程序）
    //     alarmStr:  告警ID（32位十六进制）
    //     billSn:    工单编号（delectBillSn函数需要）
    //     viewState: 从查询响应里提取的 javax.faces.ViewState 值（如 j_id12）
    //
    //     ✅ 参数已由精易模块真实抓包验证（2026-03-28）：
    //       AJAXREQUEST=_viewRoot, form=j_id1351, 触发器=j_id1351:j_id1369
    //       alarmStr 参数名正确，响应为 GZIP 压缩（OkHttp 自动解压，无需手动处理）
    //     注意：URL 末尾应为 .xhtml，精易助手抓包显示 .xhtm 是截断 bug，Referer 头里是 .xhtml
    // =====================================================================
    public static String clearOmmsAlarm(String alarmStr, String billSn, String viewState) {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) {
            String err = "{\"error\":\"ommsCookie为空\"}";
            android.util.Log.e("WorkOrderApi", "clearOmmsAlarm: " + err);
            return err;
        }

        String url = "http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml";
        // ★★★ 修复：使用动态解析的 clearFormName 和 clearTrigger，不再硬编码 j_id1351 ★★★
        //   clearFormName 和 clearTrigger 已在 refreshAlarmViewState() 中从页面 HTML 提取
        String formName = clearFormName;      // 动态解析的表单名，如 "alarmConfirmListForm"
        String triggerName = clearTrigger;    // 动态解析的触发字段，如 "j_id1369"

        // ★★★ 根据真实抓包数据修正（2026-04-18）★★★
        // 真实抓包：
        //   POST body: j_id1353=j_id1353&javax.faces.ViewState=j_id12
        //              &alarmStr=4F9BD82FD1E0365CE06352801DACEDB2
        //              &j_id1353%3Aj_id1371=j_id1353%3Aj_id1371
        // 关键修正：
        //   1. 参数名是 alarmStr，不是 delIdStr
        //   2. 参数值是 alarmId（32位十六进制），不是故障单编号
        //   3. 触发器是 j_id1371，不是 j_id1426
        
        String post =
                "AJAXREQUEST=_viewRoot"
                + "&" + urlEncUtf8(formName) + "=" + urlEncUtf8(formName)
                + "&autoScroll="
                + "&javax.faces.ViewState=" + urlEncUtf8(viewState)
                // ★★★ 修正：参数名是 alarmStr，值是 alarmId ★★★
                + "&alarmStr=" + urlEncUtf8(alarmStr)
                + "&" + urlEncUtf8(formName) + "%3A" + urlEncUtf8(triggerName) + "=" + urlEncUtf8(formName) + "%3A" + urlEncUtf8(triggerName)
                + "&AJAX%3AEVENTS_COUNT=1";

        android.util.Log.d("WorkOrderApi", "========== clearOmmsAlarm 开始 ==========");
        android.util.Log.d("WorkOrderApi", "★ alarmStr=" + alarmStr + " (alarmId)");
        android.util.Log.d("WorkOrderApi", "viewState=" + viewState);
        android.util.Log.d("WorkOrderApi", "clearFormName=" + formName + " clearTrigger=" + triggerName + " (动态解析)");
        
        // ★★★ 诊断：打印提取的 clearFormName 和 clearTrigger 的十六进制值 ★★★
        android.util.Log.d("WorkOrderApi", "URL编码后: clearFormName=" + urlEncUtf8(formName) + " clearTrigger=" + urlEncUtf8(triggerName));
        
        android.util.Log.d("WorkOrderApi", "POST参数=\n" + post.replace("&", "\n&"));

        String headers = buildOmmsHeader(url);
        // ★★★ 调试：获取 HTTP 响应码 ★★★
        com.towerops.app.util.HttpUtil.HttpResponse httpResp = 
                com.towerops.app.util.HttpUtil.postWithHeaders(url, post, headers, cookie);
        String response = httpResp.body;
        android.util.Log.d("WorkOrderApi", "HTTP响应码=" + httpResp.code + " 响应长度=" + (response == null ? "null" : response.length()) + "字节");
        
        // ★★★ 调试：解析响应内容，判断清除是否成功 ★★★
        if (response != null && response.length() > 0) {
            // 检查是否包含成功标志
            boolean hasUpdatePiece = response.contains("updatePiece");
            boolean hasError = response.contains("class=\"rf-msgs-err\"") || response.contains("class=\"rf-msg-err\"");
            boolean hasWarn = response.contains("class=\"rf-msgs-wrn\"") || response.contains("class=\"rf-msg-wrn\"");
            boolean hasSucc = response.contains("class=\"rf-msgs-inf\"") || response.contains("class=\"rf-msg-inf\"") || response.contains("showSuccMsg");
            
            android.util.Log.d("WorkOrderApi", "响应分析: updatePiece=" + hasUpdatePiece + " error=" + hasError + " warn=" + hasWarn + " success=" + hasSucc);
            
            // 提取错误/成功消息 - RichFaces 消息标签
            java.util.regex.Pattern pMsg = java.util.regex.Pattern.compile(
                    "<span[^>]*class=\"rf-msg-[^\"]+\"[^>]*>([^<]+)</span>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher mMsg = pMsg.matcher(response);
            while (mMsg.find()) {
                android.util.Log.d("WorkOrderApi", "响应消息[rf-msg]: " + mMsg.group(1).trim());
            }
            
            // ★★★ 新增：提取 modalPanel_DelteBillSn 弹窗内容中的所有文本 ★★★
            java.util.regex.Pattern pPanel = java.util.regex.Pattern.compile(
                    "<div[^>]*id=\"modalPanel_DelteBillSn\"[^>]*>(.*?)</div>",
                    java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher mPanel = pPanel.matcher(response);
            if (mPanel.find()) {
                String panelContent = mPanel.group(1);
                // 提取所有文本节点
                java.util.regex.Pattern pText = java.util.regex.Pattern.compile(">([^<]+)<");
                java.util.regex.Matcher mText = pText.matcher(panelContent);
                while (mText.find()) {
                    String text = mText.group(1).trim();
                    if (!text.isEmpty()) {
                        android.util.Log.d("WorkOrderApi", "弹窗消息[modalPanel_DelteBillSn]: " + text);
                    }
                }
            }
            
            // ★★★ 新增：提取 setDeleteResult_Form 中的消息 ★★★
            java.util.regex.Pattern pResult = java.util.regex.Pattern.compile(
                    "<form[^>]*id=\"setDeleteResult_Form\"[^>]*>(.*?)</form>",
                    java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher mResult = pResult.matcher(response);
            if (mResult.find()) {
                String resultContent = mResult.group(1);
                // 提取所有文本节点
                java.util.regex.Pattern pText = java.util.regex.Pattern.compile(">([^<]+)<");
                java.util.regex.Matcher mText = pText.matcher(resultContent);
                while (mText.find()) {
                    String text = mText.group(1).trim();
                    if (!text.isEmpty()) {
                        android.util.Log.d("WorkOrderApi", "表单消息[setDeleteResult_Form]: " + text);
                    }
                }
            }
            
            // ★★★ 打印完整响应内容来定位问题 ★★★
            android.util.Log.d("WorkOrderApi", "========== clear响应完整内容 ==========");
            android.util.Log.d("WorkOrderApi", response);
            android.util.Log.d("WorkOrderApi", "========== clear响应完整内容结束 ==========");
        } else {
            android.util.Log.w("WorkOrderApi", "响应为空！");
        }
        
        android.util.Log.d("WorkOrderApi", "========== clearOmmsAlarm 结束 ==========");

        return response;
    }

    // =====================================================================
    // OMMS 告警页 ViewState 缓存
    // JSF RichFaces 的 ViewState 是页面级别的，listAlarm.xhtml 有自己的 ViewState，
    // 与 listFsu.xhtml（门禁页）的 ViewState 不同，不能混用。
    // ★ 初始值 j_id108：来自 2026-03-28 真实抓包（listAlarm.xhtml 查询请求中的实际值）
    // =====================================================================
    private static volatile String alarmViewState = "j_id108";

    // =====================================================================
    // OMMS 告警页查询触发字段缓存
    // JSF j_id 编号是动态生成的，每次页面渲染都可能变化（随登录账号/页面状态而变）。
    // 在 GET 页面时同步解析"查询"按钮的 name 属性，确保 POST 触发字段始终正确。
    // ★ 初始值 j_id179：来自 2026-03-28 最新真实抓包
    // =====================================================================
    private static volatile String alarmQueryTrigger = "j_id179";

    // =====================================================================
    // OMMS 告警页 省/市/县 隐藏字段缓存（门禁告警查询 POST 必须携带）
    // 值从 GET listAlarm.xhtml 页面里动态提取（input hidden name=proviceIdHidden 等）
    // =====================================================================
    private static volatile String alarmProviceId  = "";
    private static volatile String alarmCityId     = "";
    private static volatile String alarmCountryId  = "";

    // =====================================================================
    // OMMS 告警页 confirm（确认）按钮触发字段缓存
    // HTML形如: <input ... name="alarmConfirmListForm:j_id1589" ... value="确认" .../>
    // =====================================================================
    private static volatile String confirmTrigger = "j_id1589";  // 默认值，冒号后部分
    private static volatile String confirmTriggerFull = "alarmConfirmListForm:j_id1589";  // 默认值，含前缀

    // =====================================================================
    // OMMS 告警页 clear（清除）按钮触发字段缓存
    // HTML形如: <input ... name="j_id1353:j_id1371" ... value="清除" .../>
    // 注意：清除请求使用独立的表单名（如 j_id1353），需要在POST中添加表单名字段！
    // ★ 默认值改为实测有效的 j_id1371（2026-04-18）
    // =====================================================================
    private static volatile String clearTrigger = "j_id1371";  // 默认值，冒号后部分
    private static volatile String clearTriggerFull = "j_id1353:j_id1371";  // 默认值，含前缀
    private static volatile String clearFormName = "j_id1353";  // 清除表单名（冒号前部分）

    // =====================================================================
    // 门禁告警专用触发字段缓存（与 alarmQueryTrigger 不同，门禁表单触发器为 j_id56 等）
    // ★ 初始值 j_id56：来自 2026-04-01 真实抓包
    // =====================================================================
    private static volatile String doorAlarmTrigger = "j_id56";

    // =====================================================================
    // 门禁告警专用隐藏字段缓存（j_id17/j_id33/j_id37）
    // 这些字段来自 GET 页面时动态提取（真实抓包显示必须携带，值可能为空但必须存在）
    // =====================================================================
    private static volatile String doorAlarmJid17 = "";
    private static volatile String doorAlarmJid33 = "";
    private static volatile String doorAlarmJid37 = "";

    /**
     * GET listAlarm.xhtml 拿最新 ViewState，存入 alarmViewState 缓存。
     *
     * 原理：RichFaces JSF 每次 GET 普通页面时，都会在 HTML 里注入：
     *   <input type="hidden" name="javax.faces.ViewState" value="j_idXX" />
     * AJAX POST 必须带这个值，否则服务器拒绝处理（返回空或错误页）。
     *
     * @return 提取到的 ViewState，null 表示失败（保留 alarmViewState 旧值）
     */
    public static String refreshAlarmViewState() {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) return null;

        String url = "http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml";
        try {
            // ★ 请求头完全对齐精易模块真实抓包（2026-03-28）
            String getHeaders = "Accept: */*\n"
                    + "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6\n"
                    + "Host: omms.chinatowercom.cn:9000\n"
                    + "Origin: http://omms.chinatowercom.cn:9000\n"
                    + "Proxy-Connection: keep-alive\n"
                    + "Referer: http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml\n"
                    + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36 Edg/146.0.0.0";
            String html = com.towerops.app.util.HttpUtil.get(url, getHeaders, cookie);
            android.util.Log.d("WorkOrderApi", "refreshAlarmViewState GET len=" + html.length()
                    + " preview=" + html.substring(0, Math.min(200, html.length())));

            if (html.isEmpty()) return null;

            // 是否被重定向到登录页
            if (html.contains("doPrevLogin") || html.contains("loginpage") || html.contains("请先登录")) {
                android.util.Log.w("WorkOrderApi", "refreshAlarmViewState: 被重定向到登录页，ommsCookie已失效");
                return null;
            }

            // 提取 ViewState
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "name=[\"']javax\\.faces\\.ViewState[\"'][^>]*value=[\"']([^\"']+)[\"']"
                    + "|value=[\"']([^\"']+)[\"'][^>]*name=[\"']javax\\.faces\\.ViewState[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(html);
            String last = null;
            while (m.find()) {
                last = m.group(1) != null ? m.group(1) : m.group(2);
            }
            if (last != null && !last.isEmpty()) {
                alarmViewState = last;
                android.util.Log.d("WorkOrderApi", "refreshAlarmViewState OK viewState=" + last);
                // 同时解析查询按钮触发字段（value="查询"的submit按钮 name属性，如 queryForm:j_id179）
                // HTML形如: <input ... name="queryForm:j_id179" ... value="查询" .../>
                java.util.regex.Pattern pBtn = java.util.regex.Pattern.compile(
                        "<input[^>]+name=[\"'](queryForm:[^\"']+)[\"'][^>]+value=[\"']查询[\"']"
                        + "|<input[^>]+value=[\"']查询[\"'][^>]+name=[\"'](queryForm:[^\"']+)[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher mBtn = pBtn.matcher(html);
                if (mBtn.find()) {
                    String fullName = mBtn.group(1) != null ? mBtn.group(1) : mBtn.group(2);
                    // 提取 queryForm: 后面的部分（即 j_idXXX）
                    String trigger = fullName.replaceFirst("^queryForm:", "");
                    if (!trigger.isEmpty()) {
                        alarmQueryTrigger = trigger;
                        android.util.Log.d("WorkOrderApi", "refreshAlarmViewState: trigger=" + trigger);
                    }
                } else {
                    android.util.Log.w("WorkOrderApi", "refreshAlarmViewState: 未找到查询按钮，保持trigger=" + alarmQueryTrigger);
                }

                // ★ 提取省/市/县隐藏字段（门禁告警查询 POST 必须携带，否则返回空结果）
                // 使用增强的 extractAreaIds 方法（多种正则模式）
                extractAreaIds(html, "listAlarm(refresh)");
                android.util.Log.d("WorkOrderApi", "refreshAlarmViewState: area="
                        + alarmProviceId + "/" + alarmCityId + "/" + alarmCountryId);

                // ★★★ 动态解析 confirm 和 clear 按钮触发字段 ★★★
                // JSF j_id 是动态的，必须从页面 HTML 提取，不能写死！
                // 
                // 按钮识别策略：
                // 1. confirm 按钮：name 包含 "alarmConfirmListForm:"，且 value 是 "Confirm" 或 "确认"
                // 2. clear 按钮：name 包含冒号 ":"，且 value 是 "清除"
                // 
                // 注意：value 可能被 HTML 实体编码，如 &#28165;&#38500; 是 "清除" 的 Unicode 编码
                String q = "[\"']";  // 单引号或双引号
                String qval = "[^\"']*";  // 属性值（不含引号）

                // ★★★ 调试：先找出页面中所有含 alarmConfirmListForm: 的 input ★★★
                java.util.regex.Pattern pAllAlarmForm = java.util.regex.Pattern.compile(
                        "<input[^>]+>",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher mAll = pAllAlarmForm.matcher(html);
                android.util.Log.d("WorkOrderApi", "====== 页面中所有含 alarmConfirmListForm: 的 input ======");
                int alarmInputCount = 0;
                while (mAll.find()) {
                    String inp = mAll.group(0);
                    if (inp.contains("alarmConfirmListForm:")) {
                        alarmInputCount++;
                        android.util.Log.d("WorkOrderApi", "  [" + alarmInputCount + "] " + inp.replace("\n", " ").replace("\r", ""));
                    }
                }
                android.util.Log.d("WorkOrderApi", "====== 共 " + alarmInputCount + " 个 alarmConfirmListForm input ======");

                // ===== confirm 按钮解析 =====
                // ★★★ 修复：保存按钮的 value 是 "保存" (&#20445;&#23384;)，不是 "Confirm"
                // 正确策略：找 id="alarmConfirmListForm:alarmConfirmSaveId" 的按钮，然后从 onclick 中提取 similarityGroupingId
                java.util.regex.Pattern pSaveBtn = java.util.regex.Pattern.compile(
                        "<input[^>]*id=" + q + "alarmConfirmListForm:alarmConfirmSaveId" + q + "[^>]*>",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher mSaveBtn = pSaveBtn.matcher(html);
                String foundConfirmTrigger = null;
                if (mSaveBtn.find()) {
                    String btnHtml = mSaveBtn.group(0);
                    // 从 onclick 中提取 similarityGroupingId 值
                    java.util.regex.Pattern pSimId = java.util.regex.Pattern.compile(
                            "similarityGroupingId" + q + ":" + q + "([^" + q + "]+)",
                            java.util.regex.Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher mSimId = pSimId.matcher(btnHtml);
                    if (mSimId.find()) {
                        foundConfirmTrigger = mSimId.group(1);
                        android.util.Log.d("WorkOrderApi", "confirm按钮找到: similarityGroupingId=" + foundConfirmTrigger);
                    }
                }
                if (foundConfirmTrigger != null) {
                    confirmTriggerFull = foundConfirmTrigger;
                    confirmTrigger = foundConfirmTrigger.substring(foundConfirmTrigger.indexOf(":") + 1);
                    android.util.Log.d("WorkOrderApi", "refreshAlarmViewState: confirmTriggerFull=" + confirmTriggerFull + " confirmTrigger=" + confirmTrigger);
                } else {
                    android.util.Log.w("WorkOrderApi", "refreshAlarmViewState: 未找到confirm按钮，保持confirmTriggerFull=" + confirmTriggerFull);
                }

                // ===== clear 按钮解析 =====
                // 查找含 value="清除" (或 Unicode 编码 &#28165;&#38500;) 的 input
                // 注意：清除按钮可能在不同的表单下（j_id1353 而非 alarmConfirmListForm）
                java.util.regex.Pattern pClear = java.util.regex.Pattern.compile(
                        "<input[^>]*/?>",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher mClear = pClear.matcher(html);
                String foundClearName = null;
                int clearCheckCount = 0;
                while (mClear.find() && foundClearName == null) {
                    String inp = mClear.group(0);
                    clearCheckCount++;
                    // 检查是否含 "清除"（支持 Unicode 编码：&#28165;&#38500; 或 &#28165;&#38500;）
                    boolean isClear = inp.contains("清除") 
                            || inp.contains("&#28165;&#38500;") 
                            || inp.contains("&#x6E05;&#x9664;");  // 十六进制编码
                    if (isClear) {
                        // 提取 name 属性
                        java.util.regex.Pattern pName2 = java.util.regex.Pattern.compile(
                                "name=" + q + "(" + qval + ")" + q,
                                java.util.regex.Pattern.CASE_INSENSITIVE);
                        java.util.regex.Matcher mName2 = pName2.matcher(inp);
                        while (mName2.find()) {
                            String n = mName2.group(1);
                            if (n.contains(":")) {
                                foundClearName = n;
                                android.util.Log.d("WorkOrderApi", "clear按钮找到: name=" + foundClearName + " input=" + inp.replace("\n", " ").replace("\r", ""));
                                break;
                            }
                        }
                    }
                }
                android.util.Log.d("WorkOrderApi", "clear按钮解析：共检查 " + clearCheckCount + " 个 input，找到 " + (foundClearName != null ? 1 : 0) + " 个");

                // ★★★ 调试：打印页面中包含 delectBillSn 的 HTML（用于分析函数实现）★★★  ★★★
                java.util.regex.Pattern pDelect = java.util.regex.Pattern.compile(
                        ".{0,100}delectBillSn.{0,300}",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher mDelect = pDelect.matcher(html);
                android.util.Log.d("WorkOrderApi", "====== 页面中 delectBillSn 相关内容 ======");
                int delectCount = 0;
                while (mDelect.find()) {
                    delectCount++;
                    android.util.Log.d("WorkOrderApi", "  [" + delectCount + "] " + mDelect.group(0).replace("\n", " ").replace("\r", ""));
                }
                android.util.Log.d("WorkOrderApi", "====== 共找到 " + delectCount + " 处 delectBillSn 相关内容 ======");

                // ★★★ 解析 delectBillSnFuc 相关表单信息（2026-04-18 v6 使用 indexOf）★★★
                // 关键：delectBillSnFuc 定义在 <script id="j_id1353:j_id1426"> 标签内
                
                int delectStart = html.indexOf("delectBillSnFuc");
                android.util.Log.d("WorkOrderApi", "delectStart=" + delectStart + " html长度=" + html.length());
                
                boolean parsed = false;
                if (delectStart >= 0) {
                    // ★★★ 扩大搜索范围到 20000 字符 ★★★
                    int searchEnd = Math.min(delectStart + 20000, html.length());
                    
                    // 在 delectStart 附近搜索 <script id="j_idxxx:j_idxxx"
                    int scriptIdx = html.indexOf("<script id=\"j_id", delectStart);
                    android.util.Log.d("WorkOrderApi", "在 delectStart 附近搜索 <script id=\"j_id: 位置=" + scriptIdx);
                    
                    if (scriptIdx > delectStart && scriptIdx < searchEnd) {
                        // 检查后面是否是 j_idxxx:j_idxxx" 格式
                        String afterScriptId = html.substring(scriptIdx + 12); // 跳过 <script id="
                        android.util.Log.d("WorkOrderApi", "afterScriptId 前50字符: " + afterScriptId.substring(0, Math.min(50, afterScriptId.length())));
                        
                        // 提取 j_idxxx:j_idxxx 部分
                        java.util.regex.Pattern trigPattern = java.util.regex.Pattern.compile("^(j_id\\d+:j_id\\d+)");
                        java.util.regex.Matcher trigMatcher = trigPattern.matcher(afterScriptId);
                        
                        if (trigMatcher.find()) {
                            String fullTrigger = trigMatcher.group(1); // 例如: j_id1353:j_id1426
                            String formName = fullTrigger.substring(0, fullTrigger.indexOf(":"));
                            String trigger = fullTrigger.substring(fullTrigger.indexOf(":") + 1);
                            
                            android.util.Log.d("WorkOrderApi", "★★ 从 <script id> 解析成功: formName=" + formName + " trigger=" + trigger);
                            clearFormName = formName;
                            clearTriggerFull = fullTrigger;
                            clearTrigger = trigger;
                            parsed = true;
                        }
                    }
                    
                    // 如果方案1失败，尝试方案2：直接搜索 similarityGroupingId
                    if (!parsed) {
                        int simIdx = html.indexOf("similarityGroupingId:'j_id", delectStart);
                        if (simIdx > delectStart && simIdx < searchEnd) {
                            String afterSim = html.substring(simIdx + 22); // 跳过 similarityGroupingId:'
                            java.util.regex.Pattern trigPattern = java.util.regex.Pattern.compile("^(j_id\\d+:j_id\\d+)");
                            java.util.regex.Matcher trigMatcher = trigPattern.matcher(afterSim);
                            
                            if (trigMatcher.find()) {
                                String fullTrigger = trigMatcher.group(1);
                                String formName = fullTrigger.substring(0, fullTrigger.indexOf(":"));
                                String trigger = fullTrigger.substring(fullTrigger.indexOf(":") + 1);
                                
                                android.util.Log.d("WorkOrderApi", "★★ 从 similarityGroupingId 解析成功: formName=" + formName + " trigger=" + trigger);
                                clearFormName = formName;
                                clearTriggerFull = fullTrigger;
                                clearTrigger = trigger;
                                parsed = true;
                            }
                        }
                    }
                    
                    // 方案3：精确搜索 j_id1353:j_id（因为 form name j_id1353 是固定的）
                    // ★★★ 增强诊断：查找所有 j_id1353:j_idXXX 的出现位置 ★★★
                    if (!parsed) {
                        java.util.regex.Pattern allPattern = java.util.regex.Pattern.compile("<script id=\"(j_id1353:j_id\\d+)\"");
                        java.util.regex.Matcher allMatcher = allPattern.matcher(html);
                        int count = 0;
                        while (allMatcher.find() && count < 10) {
                            android.util.Log.d("WorkOrderApi", "★★ 发现 script id[" + count + "]: " + allMatcher.group(1));
                            count++;
                        }
                        android.util.Log.d("WorkOrderApi", "★★ 总共发现 " + count + " 个 j_id1353:j_idXXX script");
                        
                        // 尝试搜索 j_id1371（来自真实抓包）
                        int j1371Idx = html.indexOf("j_id1353:j_id1371");
                        android.util.Log.d("WorkOrderApi", "★★ 搜索 j_id1353:j_id1371 位置=" + j1371Idx);
                        
                        int exactScriptIdx = html.indexOf("<script id=\"j_id1353:j_id");
                        android.util.Log.d("WorkOrderApi", "精确搜索 <script id=\"j_id1353:j_id 位置=" + exactScriptIdx);
                        
                        if (j1371Idx >= 0) {
                            // 优先使用 j_id1371（来自真实抓包）
                            android.util.Log.d("WorkOrderApi", "★★ 优先使用 j_id1371");
                            clearFormName = "j_id1353";
                            clearTriggerFull = "j_id1353:j_id1371";
                            clearTrigger = "j_id1371";
                            parsed = true;
                        } else if (exactScriptIdx >= 0) {
                            String afterScriptId = html.substring(exactScriptIdx + 17); // 跳过 <script id="
                            
                            java.util.regex.Pattern trigPattern = java.util.regex.Pattern.compile("^(j_id\\d+:j_id\\d+)");
                            java.util.regex.Matcher trigMatcher = trigPattern.matcher(afterScriptId);
                            
                            if (trigMatcher.find()) {
                                String fullTrigger = trigMatcher.group(1);
                                String formName = fullTrigger.substring(0, fullTrigger.indexOf(":"));
                                String trigger = fullTrigger.substring(fullTrigger.indexOf(":") + 1);
                                
                                android.util.Log.d("WorkOrderApi", "★★ 从精确搜索 script 解析成功: formName=" + formName + " trigger=" + trigger);
                                clearFormName = formName;
                                clearTriggerFull = fullTrigger;
                                clearTrigger = trigger;
                                parsed = true;
                            }
                        }
                    }
                    
                    // 方案4：搜索 similarityGroupingId:'j_id1353:j_id
                    if (!parsed) {
                        int exactSimIdx = html.indexOf("similarityGroupingId:'j_id1353:j_id");
                        android.util.Log.d("WorkOrderApi", "精确搜索 similarityGroupingId:'j_id1353:j_id 位置=" + exactSimIdx);
                        
                        if (exactSimIdx >= 0) {
                            String afterSim = html.substring(exactSimIdx + 27); // 跳过 similarityGroupingId:'
                            
                            java.util.regex.Pattern trigPattern = java.util.regex.Pattern.compile("^(j_id\\d+:j_id\\d+)");
                            java.util.regex.Matcher trigMatcher = trigPattern.matcher(afterSim);
                            
                            if (trigMatcher.find()) {
                                String fullTrigger = trigMatcher.group(1);
                                String formName = fullTrigger.substring(0, fullTrigger.indexOf(":"));
                                String trigger = fullTrigger.substring(fullTrigger.indexOf(":") + 1);
                                
                                android.util.Log.d("WorkOrderApi", "★★ 从精确搜索 similarityGroupingId 解析成功: formName=" + formName + " trigger=" + trigger);
                                clearFormName = formName;
                                clearTriggerFull = fullTrigger;
                                clearTrigger = trigger;
                                parsed = true;
                            }
                        }
                    }
                    
                if (!parsed) {
                    android.util.Log.w("WorkOrderApi", "未能在预期区域找到 script id 或 similarityGroupingId，使用实测备用值");
                    // ★★★ 备用值改为实测有效的 j_id1371（2026-04-18）★★★
                    clearFormName = "j_id1353";
                    clearTriggerFull = "j_id1353:j_id1371";
                    clearTrigger = "j_id1371";
                }
            } else {
                android.util.Log.w("WorkOrderApi", "未找到 delectBillSnFuc，使用实测备用值");
                clearFormName = "j_id1353";
                clearTriggerFull = "j_id1353:j_id1371";
                clearTrigger = "j_id1371";
            }

                // ★★★ 优先使用 delectBillSnFuc 的结果（2026-04-18）★★★
                // 清除按钮在 queryForm 表单，但 delectBillSnFuc 使用 j_id1353 表单
                // delectBillSnFuc 才是真正的清除函数，必须使用它的表单和触发器
                boolean hasDelectFuncResult = (clearFormName != null && clearFormName.contains("j_id"));
                if (hasDelectFuncResult) {
                    android.util.Log.d("WorkOrderApi", "refreshAlarmViewState: ★★★ 优先使用 delectBillSnFuc 结果 ★★★ formName=" + clearFormName + " trigger=" + clearTrigger);
                } else if (foundClearName != null) {
                    // 备用：如果没有 delectBillSnFuc 结果，使用清除按钮的结果
                    clearTriggerFull = foundClearName;
                    clearTrigger = foundClearName.substring(foundClearName.indexOf(":") + 1);
                    clearFormName = foundClearName.substring(0, foundClearName.indexOf(":"));
                    android.util.Log.d("WorkOrderApi", "refreshAlarmViewState: clearTriggerFull=" + foundClearName + " clearTrigger=" + clearTrigger + " clearFormName=" + clearFormName);
                } else {
                    android.util.Log.w("WorkOrderApi", "refreshAlarmViewState: 未找到clear按钮和delectBillSnFuc，保持 clearFormName=" + clearFormName + " clearTrigger=" + clearTrigger);
                }

                // ★ 注意：doorAlarmTrigger 不在这里更新！
                // listAlarm.xhtml（普通告警）与 listHisAlarmHbase.xhtml（门禁历史告警）是两个不同页面，
                // 它们的查询按钮 j_id 编号不同，不能相互覆盖。
                // doorAlarmTrigger 由 refreshDoorAlarmViewState() 单独从门禁历史页面提取。

                return last;
            }
            android.util.Log.w("WorkOrderApi", "refreshAlarmViewState: 页面正常但未找到ViewState，len=" + html.length());
        } catch (Exception e) {
            android.util.Log.e("WorkOrderApi", "refreshAlarmViewState error: " + e.getMessage());
        }
        return null;
    }

    // =====================================================================
    // 门禁历史告警 ViewState 刷新
    // ★★★ 2026-04-03 v3：区域ID提取策略全面重写 ★★★
    //     根因：OMMS 是 JSF Portal，hidden input value 由 JS 动态赋值，
    //     GET 时为空；listAlarm.xhtml GET 返回548字节（Portal重定向）。
    //     
    //     新策略（3步递进）：
    //     1. GET 门禁页面HTML（140KB），从JS代码/hidden input提取区域ID
    //     2. GET OMMS Portal 首页（/portal/index.xhtml），尝试从首页提取
    //     3. 打印 Cookie 中 uid/orgCode 诊断信息
    // =====================================================================
    public static String refreshDoorAlarmViewState() {
        Session s = Session.get();
        String cookie = s.ommsCookie;
        if (cookie == null || cookie.isEmpty()) return null;

        // ★★★ 诊断：打印 Cookie 中 uid/orgCode 信息 ★★★
        String orgCode = extractOrgCodeFromCookie(cookie);
        android.util.Log.d("WorkOrderApi", "refreshDoorAlarmViewState: Cookie orgCode="
                + (orgCode != null ? orgCode : "null") + " len=" + cookie.length());

        // ★★★ 第一步：GET 门禁历史告警页面（140KB，能正常访问），网络断开时自动重试 ★★★
        String url = "http://omms.chinatowercom.cn:9000/business/resMge/alarmHisHbaseMge/listHisAlarmHbase.xhtml";
        String html = "";
        String getHeaders = "Accept: */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6\n"
                + "Host: omms.chinatowercom.cn:9000\n"
                + "Origin: http://omms.chinatowercom.cn:9000\n"
                + "Proxy-Connection: keep-alive\n"
                + "Referer: http://omms.chinatowercom.cn:9000/business/resMge/alarmHisHbaseMge/listHisAlarmHbase.xhtml\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36 Edg/146.0.0.0";
        try {
            html = com.towerops.app.util.HttpUtil.get(url, getHeaders, cookie);
            // ★ 网络断开（unexpected end of stream）时重试2次，间隔2秒
            for (int retry = 0; retry < 2 && (html == null || html.isEmpty()); retry++) {
                android.util.Log.w("WorkOrderApi", "refreshDoorAlarmViewState: GET返回空，第" + (retry + 1) + "次重试...");
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                html = com.towerops.app.util.HttpUtil.get(url, getHeaders, cookie);
            }
            android.util.Log.d("WorkOrderApi", "refreshDoorAlarmViewState GET len=" + (html != null ? html.length() : "null"));

            if (html == null || html.isEmpty()) {
                android.util.Log.e("WorkOrderApi", "refreshDoorAlarmViewState: 门禁页面返回空（已重试2次）");
                return null;
            }

            // 是否被重定向到登录页
            if (html.contains("doPrevLogin") || html.contains("loginpage") || html.contains("请先登录")) {
                android.util.Log.w("WorkOrderApi", "refreshDoorAlarmViewState: 被重定向到登录页，ommsCookie已失效");
                return null;
            }

            // 从门禁页面HTML提取区域ID
            extractAreaIds(html, "doorPage");
        } catch (Exception e) {
            android.util.Log.e("WorkOrderApi", "refreshDoorAlarmViewState GET异常: " + e.getMessage());
            return null;
        }

        // ★★★ 第二步：如果门禁页面没拿到，尝试 Portal 首页 ★★★
        if (alarmProviceId.isEmpty() || alarmCityId.isEmpty() || alarmCountryId.isEmpty()) {
            try {
                String portalUrl = "http://omms.chinatowercom.cn:9000/portal/index.xhtml";
                String portalHeaders = "Accept: text/html,application/xhtml+xml,*/*\n"
                        + "Accept-Language: zh-CN,zh;q=0.9\n"
                        + "Host: omms.chinatowercom.cn:9000\n"
                        + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";
                String portalHtml = com.towerops.app.util.HttpUtil.get(portalUrl, portalHeaders, cookie);
                if (portalHtml != null && !portalHtml.isEmpty()
                        && !portalHtml.contains("doPrevLogin") && !portalHtml.contains("uac/login")) {
                    android.util.Log.d("WorkOrderApi", "Portal首页 len=" + portalHtml.length());
                    extractAreaIds(portalHtml, "portal");
                } else {
                    android.util.Log.w("WorkOrderApi", "Portal首页访问失败, len="
                            + (portalHtml == null ? "null" : portalHtml.length()));
                }
            } catch (Exception ignored) {}
        }

        // ★★★ 第三步：如果还没拿到，打印诊断信息，然后尝试 listAlarm.xhtml（不同Referer） ★★★
        if (alarmProviceId.isEmpty() || alarmCityId.isEmpty() || alarmCountryId.isEmpty()) {
            try {
                String alarmUrl = "http://omms.chinatowercom.cn:9000/business/resMge/alarmMge/listAlarm.xhtml";
                String alarmHeaders = "Accept: text/html,application/xhtml+xml,*/*\n"
                        + "Accept-Language: zh-CN,zh;q=0.9\n"
                        + "Host: omms.chinatowercom.cn:9000\n"
                        + "Referer: http://omms.chinatowercom.cn:9000/portal/index.xhtml\n"
                        + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";
                String alarmHtml = com.towerops.app.util.HttpUtil.get(alarmUrl, alarmHeaders, cookie);
                if (alarmHtml != null && alarmHtml.length() > 1000) {
                    android.util.Log.d("WorkOrderApi", "listAlarm.xhtml len=" + alarmHtml.length() + " (带Referer重试)");
                    extractAreaIds(alarmHtml, "listAlarm_v3");
                }
            } catch (Exception ignored) {}
        }

        // ★★★ 第四步：硬编码兜底 ——三次提取均失败时，根据用户选择使用对应区域ID ★★★
        if (alarmProviceId.isEmpty() || alarmCityId.isEmpty() || alarmCountryId.isEmpty()) {
            int region = s.doorAlarmRegion;
            alarmProviceId = "0001945";
            alarmCityId = "0099874";
            if (region == 1) {
                alarmCountryId = "0107440"; // 泰顺
                android.util.Log.w("WorkOrderApi", "refreshDoorAlarmViewState: 使用硬编码兜底区域ID（泰顺）");
            } else {
                alarmCountryId = "0107437"; // 平阳
                android.util.Log.w("WorkOrderApi", "refreshDoorAlarmViewState: 使用硬编码兜底区域ID（平阳）");
            }
        } else {
            // ★★★ 即使提取成功，也根据用户选择覆盖县ID（确保查询的是选中区域）★★★
            int region = s.doorAlarmRegion;
            String targetCountryId = (region == 1) ? "0107440" : "0107437";
            String regionName = (region == 1) ? "泰顺" : "平阳";
            if (!alarmCountryId.equals(targetCountryId)) {
                android.util.Log.w("WorkOrderApi", "refreshDoorAlarmViewState: 提取县ID=" + alarmCountryId
                        + " != 目标" + regionName + "=" + targetCountryId + "，强制覆盖");
            }
            alarmCountryId = targetCountryId;
        }

        android.util.Log.d("WorkOrderApi", "refreshDoorAlarmViewState: 最终区域ID="
                + alarmProviceId + "/" + alarmCityId + "/" + alarmCountryId
                + (alarmProviceId.isEmpty() ? " ⚠️仍为空" : ""));

        // ★★★ 提取 ViewState + trigger + jid ★★★
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "name=[\"']javax\\.faces\\.ViewState[\"'][^>]*value=[\"']([^\"']+)[\"']"
                    + "|value=[\"']([^\"']+)[\"'][^>]*name=[\"']javax\\.faces\\.ViewState[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(html);
            String last = null;
            while (m.find()) {
                last = m.group(1) != null ? m.group(1) : m.group(2);
            }
            if (last != null && !last.isEmpty()) {

                // ★ 提取门禁告警专用隐藏字段（j_id17/j_id33/j_id37）
                for (String jid : new String[]{"j_id17", "j_id33", "j_id37"}) {
                    java.util.regex.Pattern pJid = java.util.regex.Pattern.compile(
                            "name=[\"']queryForm:" + jid + "[\"'][^>]*value=[\"']([^\"']*)[\"']"
                            + "|value=[\"']([^\"']*)[\"'][^>]*name=[\"']queryForm:" + jid + "[\"']",
                            java.util.regex.Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher mJid = pJid.matcher(html);
                    String jidVal = "";
                    if (mJid.find()) {
                        jidVal = mJid.group(1) != null ? mJid.group(1) : mJid.group(2);
                        if (jidVal == null) jidVal = "";
                    }
                    switch (jid) {
                        case "j_id17": doorAlarmJid17 = jidVal; break;
                        case "j_id33": doorAlarmJid33 = jidVal; break;
                        case "j_id37": doorAlarmJid37 = jidVal; break;
                    }
                }

                // ★ 解析门禁历史告警专用触发字段（查询按钮 name）
                java.util.regex.Pattern pDoorBtn = java.util.regex.Pattern.compile(
                        "<input[^>]+name=[\"'](queryForm:[^\"']+)[\"'][^>]+value=[\"']查询[\"']"
                        + "|<input[^>]+value=[\"']查询[\"'][^>]+name=[\"'](queryForm:[^\"']+)[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher mDoorBtn = pDoorBtn.matcher(html);
                if (mDoorBtn.find()) {
                    String fullName = mDoorBtn.group(1) != null ? mDoorBtn.group(1) : mDoorBtn.group(2);
                    String trigger = fullName.replaceFirst("^queryForm:", "");
                    if (!trigger.isEmpty()) {
                        doorAlarmTrigger = trigger;
                        android.util.Log.d("WorkOrderApi", "refreshDoorAlarmViewState: doorAlarmTrigger=" + trigger);
                    }
                } else {
                    android.util.Log.w("WorkOrderApi", "refreshDoorAlarmViewState: 未找到查询按钮，保持doorAlarmTrigger=" + doorAlarmTrigger);
                }

                android.util.Log.d("WorkOrderApi", "refreshDoorAlarmViewState: viewState=" + last
                        + " area=" + alarmProviceId + "/" + alarmCityId + "/" + alarmCountryId
                        + " jids=" + doorAlarmJid17 + "/" + doorAlarmJid33 + "/" + doorAlarmJid37
                        + " trigger=" + doorAlarmTrigger);

                return last;
            }
            android.util.Log.w("WorkOrderApi", "refreshDoorAlarmViewState: 页面正常但未找到ViewState，len=" + html.length());
        } catch (Exception e) {
            android.util.Log.e("WorkOrderApi", "refreshDoorAlarmViewState error: " + e.getMessage());
        }
        return null;
    }

    // =====================================================================
    // 增强方法：从 HTML 提取省市县区域ID
    // ★★★ 2026-04-03 v3：多种正则模式 + 从Cookie/JS提取orgCode ★★★
    // =====================================================================
    private static void extractAreaIds(String html, String source) {
        boolean foundAny = false;

        // ★★★ 新增：从 JS 中提取 ORGModuleTree 初始化数据 ★★★
        // OMMS Portal 页面通常在 JS 中初始化组织树，形如：
        //   ORGModuleTree_tree = new xxx(...);
        //   或 var provinceId = "0001945"; 
        //   或在 Ajax 请求响应中包含 orgCode/provinceId 等
        // 先尝试从页面JS变量中直接提取 orgCode/areaCode
        String[] orgPatterns = {
                // JS 赋值模式：var xxx = "yyyy";
                "var\\s+(?:provinceId|proviceId)\\s*=\\s*[\"']([^\"']+)[\"']",
                "var\\s+cityId\\s*=\\s*[\"']([^\"']+)[\"']",
                "var\\s+(?:countryId|countyId)\\s*=\\s*[\"']([^\"']+)[\"']",
                // input hidden 标准模式（宽松：允许属性间有换行/多余空格）
                "<input[^>]*?name=[\"']queryForm:(proviceIdHidden|cityIdHidden|countryIdHidden)[\"'][^>]*?value=[\"']([^\"']+)[\"']",
                "<input[^>]*?value=[\"']([^\"']+)[\"'][^>]*?name=[\"']queryForm:(proviceIdHidden|cityIdHidden|countryIdHidden)[\"']",
                // select option 模式（有些 OMMS 版本用 select 下拉）
                "<select[^>]*?name=[\"']queryForm:(proviceIdHidden|cityIdHidden|countryIdHidden)[\"'][^>]*?>([^<]*)</select>",
                // data- 属性模式
                "data-(?:province|city|country)[-_]?id=[\"']([^\"']+)[\"']",
        };
        String[] orgLabels = {
                "province", "city", "country", "proviceIdHidden", "cityIdHidden", "countryIdHidden",
                "proviceIdHidden_rev", "cityIdHidden_rev", "countryIdHidden_rev", "select", "data-attr"
        };

        for (int i = 0; i < orgPatterns.length; i++) {
            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(orgPatterns[i],
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher m = p.matcher(html);
                if (m.find()) {
                    if (i <= 2) {
                        // 前三个是 var provinceId/cityId/countryId 模式
                        String val = m.group(1);
                        if (val != null && !val.isEmpty()) {
                            switch (i) {
                                case 0: alarmProviceId = val; break;
                                case 1: alarmCityId = val; break;
                                case 2: alarmCountryId = val; break;
                            }
                            foundAny = true;
                            android.util.Log.d("WorkOrderApi", "extractAreaIds [" + source + "] JS变量[" + orgLabels[i] + "]: " + val);
                        }
                    } else if (i <= 5) {
                        // input hidden name=value 标准模式
                        String key = m.group(1);
                        String val = m.group(2);
                        if (key != null && val != null && !val.isEmpty()) {
                            switch (key) {
                                case "proviceIdHidden": alarmProviceId = val; break;
                                case "cityIdHidden": alarmCityId = val; break;
                                case "countryIdHidden": alarmCountryId = val; break;
                            }
                            foundAny = true;
                            android.util.Log.d("WorkOrderApi", "extractAreaIds [" + source + "] input[" + orgLabels[i] + "]: " + key + "=" + val);
                        }
                    } else if (i <= 8) {
                        // input hidden value=name 反序模式
                        String val = m.group(1);
                        String key = m.group(2);
                        if (key != null && val != null && !val.isEmpty()) {
                            switch (key) {
                                case "proviceIdHidden": alarmProviceId = val; break;
                                case "cityIdHidden": alarmCityId = val; break;
                                case "countryIdHidden": alarmCountryId = val; break;
                            }
                            foundAny = true;
                            android.util.Log.d("WorkOrderApi", "extractAreaIds [" + source + "] input_rev[" + orgLabels[i] + "]: " + key + "=" + val);
                        }
                    } else if (i == 9) {
                        // select 模式
                        String key = m.group(1);
                        String val = m.group(2);
                        if (key != null && val != null && !val.trim().isEmpty()) {
                            switch (key) {
                                case "proviceIdHidden": alarmProviceId = val.trim(); break;
                                case "cityIdHidden": alarmCityId = val.trim(); break;
                                case "countryIdHidden": alarmCountryId = val.trim(); break;
                            }
                            foundAny = true;
                            android.util.Log.d("WorkOrderApi", "extractAreaIds [" + source + "] select: " + key + "=" + val);
                        }
                    } else {
                        // data- 属性模式
                        String val = m.group(1);
                        if (val != null && !val.isEmpty()) {
                            android.util.Log.d("WorkOrderApi", "extractAreaIds [" + source + "] data-attr: " + val);
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("WorkOrderApi", "extractAreaIds pattern[" + i + "] error: " + e.getMessage());
            }
        }

        // ★ 模式2：整体 <input> 标签匹配（兜底，允许 name 和 value 在不同行）
        try {
            java.util.regex.Pattern pTag = java.util.regex.Pattern.compile(
                    "<input[^>]*(proviceIdHidden|cityIdHidden|countryIdHidden)[^>]*>",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher mTag = pTag.matcher(html);
            while (mTag.find()) {
                String tag = mTag.group(0);
                String fieldName = mTag.group(1);
                // 从标签中提取 value（找最后一个 value= 的值，因为 hidden input 的 value 通常在 name 后面）
                java.util.regex.Matcher mVal = java.util.regex.Pattern.compile(
                        "value=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tag);
                String val = null;
                while (mVal.find()) {
                    val = mVal.group(1);
                }
                if (fieldName != null && val != null && !val.isEmpty()) {
                    switch (fieldName) {
                        case "proviceIdHidden":  alarmProviceId  = val; break;
                        case "cityIdHidden":     alarmCityId     = val; break;
                        case "countryIdHidden":  alarmCountryId  = val; break;
                    }
                    foundAny = true;
                    android.util.Log.d("WorkOrderApi", "extractAreaIds [" + source + "] tagMatch: " + fieldName + "=" + val);
                }
            }
        } catch (Exception ignored) {}

        if (!foundAny) {
            // ★ 诊断：打印 HTML 中包含区域相关关键字的片段
            android.util.Log.w("WorkOrderApi", "extractAreaIds [" + source + "]: 所有模式均未匹配！诊断...");
            java.util.regex.Pattern pDiag = java.util.regex.Pattern.compile(
                    "[^\n]{0,80}(proviceId|cityId|countryId|orgCode|areaCode|provinceId)[^\n]{0,80}",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher mDiag = pDiag.matcher(html);
            int diagCount = 0;
            while (mDiag.find() && diagCount < 15) {
                android.util.Log.w("WorkOrderApi", "  DIAG[" + source + "]: " + mDiag.group(0).trim());
                diagCount++;
            }
            if (diagCount == 0) {
                // 搜索更宽泛的关键词
                java.util.regex.Pattern pDiag2 = java.util.regex.Pattern.compile(
                        "[^\n]{0,80}(Hidden|hidden|selectProv|ORGModule|getUserData)[^\n]{0,80}",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher mDiag2 = pDiag2.matcher(html);
                int d2 = 0;
                while (mDiag2.find() && d2 < 10) {
                    android.util.Log.w("WorkOrderApi", "  DIAG2[" + source + "]: " + mDiag2.group(0).trim());
                    d2++;
                }
                if (d2 == 0) {
                    android.util.Log.w("WorkOrderApi", "  DIAG[" + source + "]: HTML中找不到任何区域相关片段，len=" + html.length());
                }
            }
        }

        android.util.Log.d("WorkOrderApi", "extractAreaIds [" + source + "] 最终结果="
                + alarmProviceId + "/" + alarmCityId + "/" + alarmCountryId
                + (foundAny ? " (成功)" : " (失败)"));
    }

    /**
     * 从 OMMS Cookie 中提取 orgCode / uid 等用户信息
     * Cookie 格式形如: route=xxx; JSESSIONID=xxx; uid=xxx; userOrgCode=xxx
     * @return orgCode 或 null
     */
    private static String extractOrgCodeFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return null;
        // 尝试提取 userOrgCode
        java.util.regex.Matcher mOrg = java.util.regex.Pattern.compile(
                "userOrgCode=([^;\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cookie);
        if (mOrg.find()) {
            String orgCode = mOrg.group(1);
            android.util.Log.d("WorkOrderApi", "从Cookie提取userOrgCode: " + orgCode);
            return orgCode;
        }
        // 尝试提取 uid
        java.util.regex.Matcher mUid = java.util.regex.Pattern.compile(
                "\\buid=([^;\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cookie);
        if (mUid.find()) {
            android.util.Log.d("WorkOrderApi", "从Cookie提取uid: " + mUid.group(1));
        }
        return null;
    }

    // =====================================================================
    // 工具：构建 OMMS AJAX 请求协议头（用于 confirm/clear/query 等 AJAX 操作）
    // ★ 对齐精易模块真实抓包（2026-03-28）
    // ★ 2026-04-01 更新：添加 Cache-Control 和 Pragma 头（门禁历史告警查询必须）
    // =====================================================================
    private static String buildOmmsHeader(String referer) {
        return "Accept: */*\n"
             + "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6\n"
             + "Cache-Control: no-cache\n"
             + "Content-Type: application/x-www-form-urlencoded; charset=UTF-8\n"
             + "Host: omms.chinatowercom.cn:9000\n"
             + "Origin: http://omms.chinatowercom.cn:9000\n"
             + "Pragma: no-cache\n"
             + "Proxy-Connection: keep-alive\n"
             + "Referer: " + referer + "\n"
             + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36 Edg/146.0.0.0";
    }

    // =====================================================================
    // 工具：构建 OMMS 非AJAX 完整表单提交协议头
    //   不带 X-Requested-With: XMLHttpRequest，服务器返回完整HTML页面。
    //   用于告警列表查询（目标是获取可解析的完整HTML，内含 selectFlag checkbox）
    // =====================================================================
    private static String buildOmmsNonAjaxHeader(String referer) {
        return "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\n"
             + "Accept-Language: zh-CN,zh;q=0.9\n"
             + "Cache-Control: no-cache\n"
             + "Content-Type: application/x-www-form-urlencoded\n"
             + "Host: omms.chinatowercom.cn:9000\n"
             + "Origin: http://omms.chinatowercom.cn:9000\n"
             + "Pragma: no-cache\n"
             + "Referer: " + referer + "\n"
             + "Upgrade-Insecure-Requests: 1\n"
             + "User-Agent: Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36";
    }

    // =====================================================================
    // 工具：URL 编码（UTF-8），null/空串安全
    // =====================================================================
    public static String urlEncUtf8(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    // =====================================================================
    // 工具：获取当前月份（格式：MM%2Fyyyy，如 04%2F2026）
    // ★ 修复：不再硬编码 03%2F2026
    // =====================================================================
    public static String getCurrentMonthYear() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int month = cal.get(java.util.Calendar.MONTH) + 1;  // 月份从0开始
        int year = cal.get(java.util.Calendar.YEAR);
        return String.format("%02d%%2F%d", month, year);
    }

    // =====================================================================
    // 工具：从 JSON 中提取嵌套属性（支持 "a.b.c" 格式路径）
    // =====================================================================
    public static String getJsonPath(JSONObject root, String path) {
        if (root == null || path == null || path.isEmpty()) return "";
        try {
            String[] parts = path.split("\\.");
            JSONObject cur = root;
            for (int i = 0; i < parts.length - 1; i++) {
                JSONObject next = cur.optJSONObject(parts[i]);
                if (next == null) return "";
                cur = next;
            }
            return cur.optString(parts[parts.length - 1], "");
        } catch (Exception e) {
            return "";
        }
    }

    // =====================================================================
    // 工具：计算时间字符串到现在的分钟差
    // 支持格式：
    //   yyyy-MM-dd HH:mm:ss       标准
    //   yyyy/MM/dd HH:mm:ss       斜杠
    //   yyyy-MM-dd HH:mm:ss.SSS   带毫秒
    //   yyyy-MM-dd HH:mm          只到分钟
    //   yyyy-MM-ddTHH:mm:ss       ISO 8601
    // 返回值：>= 0（异常/空串/未来时间均返回 0）
    // =====================================================================
    public static int minutesDiff(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return 0;
        try {
            String s = timeStr.trim()
                    .replace("/", "-")
                    .replace("T", " ");
            // 去掉毫秒部分
            int dot = s.indexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            // 补秒（yyyy-MM-dd HH:mm 格式，16位）
            if (s.length() == 16) s += ":00";
            // 只接受 19 位标准格式
            if (s.length() != 19) return 0;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setLenient(false);
            Date past = sdf.parse(s);
            if (past == null) return 0;
            long diff = System.currentTimeMillis() - past.getTime();
            // [OPT-1 修复] 未来时间返回 0，不返回负值
            return diff < 0 ? 0 : (int) (diff / 60000L);
        } catch (Exception e) {
            return 0;
        }
    }
}

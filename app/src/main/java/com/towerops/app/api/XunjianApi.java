package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;
import com.towerops.app.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 巡检工单 API
 *
 * 接口列表：
 * 1. NEW_TASK_UNSTART_LIST      - 未巡检工单列表（按钮5逻辑）
 * 2. mom/xunjian/notStartedLyPage - 智联巡检未开始列表（按钮32逻辑）
 * 3. NEW_TASK_PLAN_LIST         - 主计划列表（APP质检按钮27）
 * 4. NEW_TASK_MONITOR_LIST      - 巡检任务（按计划ID获取任务）
 * 5. NEW_TASK_MODULAR_LIST_ALL  - 巡检项目列表
 * 6. SIX_TASK_INFO              - 巡检项目取签到距离
 * 7. NEW_TASK_MODULAR_DETAIL    - 巡检项目详情
 */
public class XunjianApi {

    private static final String BASE = "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service";
    private static final String V = LoginApi.V;

    // 固定坐标
    private static final String LON = "120.540200";
    private static final String LAT = "27.601945";

    // ===== 分组常量（站名关键词，用于分组匹配）=====
    // 真实常量值需用户在实际项目中配置；此处以空字符串占位，
    // 分组匹配逻辑在 XunjianFragment 里改为按 taskuser 匹配
    public static final String GROUP_ZH1 = ""; // 综合1组站名关键词
    public static final String GROUP_ZH2 = ""; // 综合2组站名关键词
    public static final String GROUP_ZH3 = ""; // 综合3组站名关键词
    public static final String GROUP_ZH4 = ""; // 综合4组站名关键词
    public static final String GROUP_ZH5 = ""; // 综合5组站名关键词
    public static final String GROUP_SF1 = ""; // 室分1组站名关键词

    // 分组名称
    public static final String[] GROUP_NAMES = {
        "综合1组", "综合2组", "综合3组", "综合4组", "综合5组", "室分1组"
    };

    // taskuser 分组映射（区域0=平阳）
    public static final String[][] TASKUSER_GROUPS_AREA0 = {
        {"陶大取", "卢智伟"},   // 综合1组
        {"陈龙", "林元龙"},     // 综合2组
        {"高树调", "倪传井"},   // 综合3组
        {"苏忠前", "许方喜"},   // 综合4组
        {"黄经兴", "蔡亮"},     // 综合5组
    };

    // taskuser 分组映射（区域1=泰顺）
    public static final String[][] TASKUSER_GROUPS_AREA1 = {
        {"朱兴达"},   // 罗阳片区
        {"王成"},     // 雅阳片区
        {"夏念悦"},   // 泗溪片区
        {"胡叙渐"},   // 仕阳片区
    };

    // 区域1分组名称
    public static final String[] GROUP_NAMES_AREA1 = {
        "罗阳片区", "雅阳片区", "泗溪片区", "仕阳片区"
    };

    // =========================================================
    // 数据模型
    // =========================================================

    /** 未巡检工单条目 */
    public static class UnstartTask {
        public String seq;          // 序号
        public String groupName;    // 分组名称
        public String tasksn;       // 工单号
        public String deviceid;     // 设备ID
        public String stationname;  // 站点名
        public String applymajor;   // 专业
        public String date;         // 日期
        public String mainplanname; // 计划名
        public String stationcode;  // 站址编码
    }

    /** 未巡检统计矩阵行 */
    public static class StatRow {
        public String groupName;
        public int[] counts = new int[5]; // 5个专业
        public int rowTotal;
    }

    /** 智联巡检条目 */
    public static class ZhilianXunjianTask {
        public String seq;
        public String groupName;
        public String tasksn;
        public String deviceid;
        public String stationName;
        public String applymajor;
        public String createtime;
        public String mainPlanName;
        public String inspecttime;
    }

    /** APP质检任务条目（汇总列表） */
    public static class AppQualityTask {
        public String seq;
        public String groupName;
        public String tasksn;
        public String deviceid;
        public String stationcode;
        public String stationname;
        public String remark;
        public String applymajor;
        public String mainplanname;
        public String progress;    // finishnum/allnum
        public String starttime;
        public String endtime;
        public String pollingperiod;
        public String inspecttime;
        public String taskuser;
    }

    /** APP质检详情条目 */
    public static class AppQualityDetail {
        public String seq;
        public String groupName;
        public String mainplanname;
        public String sitecode;
        public String sitename;
        public String applymajor;
        public String devname;
        public String projectname;
        public String request;
        public String actualfill;
        public String ishidden;
        public String remark;
        public String range_site;
        public String imagecount;
        public String starttime;
        public String endtime;
        public String taskuser;
        public String tasksn;
        public String qualityPass = "";   // 是/否/""
        public String qualityReason = ""; // 质检问题描述
    }

    /** 任务包（用于线程间传递） */
    public static class TaskPackage {
        public String mainplanname;
        public String applymajor;
        public String allnum;
        public String finishnum;
        public String remark;
        public String endtime;
        public String starttime;
        public String stationname;
        public String taskuser;
        public String stationcode;
        public String inspecttime;
        public String taskid;
        public String taskuserid;
    }

    // =========================================================
    // 1. 未巡检工单列表（NEW_TASK_UNSTART_LIST）
    // =========================================================
    public static String getUnstartList() {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = s.userid;
        String url = BASE + "?porttype=NEW_TASK_UNSTART_LIST&v=" + V + "&userid=" + uid + "&c=0";
        String post = "start=2&limit=5000&sortCond=time&applymajor=1&start_status=N"
                + "&lon=" + LON + "&lat=" + LAT
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=B02C894F2CDC9C7A4BF2481DE4A432BA"
                + "&upvs=2026-01-01-ccssoft";
        String headers = buildAuthHeaders(s);
        return HttpUtil.post(url, post, headers, null);
    }

    // =========================================================
    // 2. 智联巡检未开始列表（GET请求）
    // =========================================================
    public static String getZhilianXunjianList() {
        Session s = Session.get();
        String uid = s.userid;
        String url = "http://ywapp.chinatowercom.cn:58090/mom/xunjian/twInspstandTask/notStartedLyPage"
                + "?lat=27.602048&lon=120.540239&current=1&size=1000"
                + "&taskuserid=" + uid
                + "&stationname=&sort=&istimeout=&applymajor=1";
        String headers = buildAuthHeaders(s);
        return HttpUtil.get(url, headers, null);
    }

    // =========================================================
    // 3. 主计划列表（NEW_TASK_PLAN_LIST）
    // =========================================================
    public static String getPlanList(String planname) {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = s.userid;
        String url = BASE + "?porttype=NEW_TASK_PLAN_LIST&v=" + V + "&userid=" + uid + "&c=0";
        String encodedPlan;
        try {
            encodedPlan = URLEncoder.encode(planname, "UTF-8");
        } catch (Exception e) {
            encodedPlan = planname;
        }
        String post = "start=1&limit=50"
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=4989612FA577E2955FF7385F9871F39E"
                + "&upvs=2025-03-21-ccssoft"
                + "&stationname=&stationcode="
                + "&planname=" + encodedPlan;
        String headers = buildAuthHeaders(s);
        return HttpUtil.post(url, post, headers, null);
    }

    // =========================================================
    // 4. 巡检任务列表（NEW_TASK_MONITOR_LIST）—— 按主计划ID
    // =========================================================
    public static String getTaskListByPlanId(String mainplanid) {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = s.userid;
        String url = BASE + "?porttype=NEW_TASK_MONITOR_LIST&v=" + V + "&userid=" + uid + "&c=0";
        String post = "start=1&limit=2000"
                + "&mainplanid=" + mainplanid
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=1E9E0C69DAFC15430E63C459EBFA3B5C"
                + "&upvs=2025-03-21-ccssoft";
        String headers = buildAuthHeaders(s);
        return HttpUtil.post(url, post, headers, null);
    }

    // =========================================================
    // 5. 巡检项目列表（NEW_TASK_MODULAR_LIST_ALL）
    // =========================================================
    public static String getModularList(String taskid) {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = s.userid;
        String url = BASE + "?porttype=NEW_TASK_MODULAR_LIST_ALL&v=" + V + "&userid=" + uid + "&c=0";
        String post = "start=1&limit=1000"
                + "&taskid=" + taskid
                + "&fromflag=Y"
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=E1BB784E27534F4ED3F3FBF7ACAD2334"
                + "&upvs=2025-03-22-ccssoft";
        String headers = buildAuthHeaders(s);
        return HttpUtil.post(url, post, headers, null);
    }

    // =========================================================
    // 6. 巡检项目取签到距离（SIX_TASK_INFO）
    // =========================================================
    public static String getTaskSignInfo(String billid) {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = s.userid;
        String url = BASE + "?porttype=SIX_TASK_INFO&v=" + V + "&userid=" + uid + "&c=0";
        String post = "userid=" + uid
                + "&type=xj"
                + "&billid=" + billid
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=64BCB96BB79C6CBE30C89BE5364026EE"
                + "&upvs=2025-03-23-ccssoft";
        String headers = buildAuthHeaders(s);
        return HttpUtil.post(url, post, headers, null);
    }

    // =========================================================
    // 7. 巡检项目详情（NEW_TASK_MODULAR_DETAIL）
    // =========================================================
    public static String getModularDetail(String taskid, String modularid) {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = s.userid;
        String url = BASE + "?porttype=NEW_TASK_MODULAR_DETAIL&v=" + V + "&userid=" + uid + "&c=0";
        String post = "type=NEW_XUNJIAN_MONITOR"
                + "&taskid=" + taskid
                + "&modularid=" + modularid
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=2752496F667021EAC8221DAE0B8A8156"
                + "&upvs=2025-03-22-ccssoft";
        String headers = buildAuthHeaders(s);
        return HttpUtil.post(url, post, headers, null);
    }

    // =========================================================
    // 工具方法
    // =========================================================

    /** 构建通用 Authorization 协议头字符串（格式: "Key: Value\nKey2: Value2"）*/
    private static String buildAuthHeaders(Session s) {
        return "Authorization: " + s.token + "\n"
                + "appVer: 202112\n"
                + "Content-Type: application/x-www-form-urlencoded\n"
                + "Host: ywapp.chinatowercom.cn:58090\n"
                + "Connection: Keep-Alive";
    }

    /** null/空值清洗 */
    public static String cleanNull(String v) {
        if (v == null || "null".equalsIgnoreCase(v.trim())) return "";
        return v.trim();
    }

    /** applymajor 英中互译 */
    public static String translateMajor(String major) {
        if (major == null) return "";
        major = major.replace("TOWER", "铁塔")
                     .replace("ROOM", "机房")
                     .replace("CABINET", "机柜")
                     .replace("RRU", "拉远");
        return major;
    }

    /**
     * 专业归属分类（返回1-4）
     * 1=拉远/室分/微站  2=机柜  3=铁塔  4=机房(默认)
     */
    public static int getMajorCategory(String applymajor) {
        if (applymajor == null) return 4;
        if (applymajor.contains("拉远") || applymajor.contains("RRU")
                || applymajor.contains("室分") || applymajor.contains("微站")) return 1;
        if (applymajor.contains("机柜") || applymajor.contains("柜")) return 2;
        if (applymajor.contains("铁塔") || applymajor.contains("塔")
                || applymajor.contains("桅") || applymajor.contains("天面")) return 3;
        return 4;
    }
}

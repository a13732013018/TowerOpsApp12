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
 * 智联工单 API —— 对应易语言智联工单相关接口
 *
 * 接口列表：
 * 1. ZNDW_ZL_TASK_LIST      - 获取未领取工单列表
 * 2. ZNDW_ZL_TASKDRAW_LIST  - 获取已领取工单列表
 * 3. ZNDW_TASK_GET          - 接单
 * 4. ZNDW_TASK_FILL_ORDER_INSERT - 回单
 */
public class ZhilianApi {

    private static final String BASE = "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service";

    // =====================================================================
    // ★ 版本号统一在 LoginApi.V 维护，升级只需改那一处
    // =====================================================================
    private static final String V    = LoginApi.V;
    private static final String UPVS = "2025-07-05-ccssoft";

    // 固定坐标（从易语言代码中提取）
    private static final String LON = "120.540310";
    private static final String LAT = "27.601740";

    // =====================================================================
    // 1. 获取未领取工单列表
    // =====================================================================
    public static String getUnclaimedTaskList() {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);

        String url = BASE + "?porttype=ZNDW_ZL_TASK_LIST&v=" + V + "&userid=" + uid + "&c=0";

        String post = "start=1&limit=20"
                + "&busiType=0"
                + "&interfaceFrom="
                + "&sortCond=time"
                + "&lon=" + LON
                + "&lat=" + LAT
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=" + s.cSign  // 从Session获取动态签名
                + "&upvs=" + UPVS;

        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 2. 获取已领取工单列表
    // =====================================================================
    public static String getClaimedTaskList() {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);

        String url = BASE + "?porttype=ZNDW_ZL_TASKDRAW_LIST&v=" + V + "&userid=" + uid + "&c=0";

        String post = "start=1&limit=20"
                + "&busiType=0"
                + "&interfaceFrom="
                + "&sortCond=time"
                + "&lon=120.540374"
                + "&lat=27.601687"
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=" + s.cSign
                + "&upvs=" + UPVS;

        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 3. 接单
    // =====================================================================
    public static String acceptTask(String id, String versionId) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);

        String url = BASE + "?porttype=ZNDW_TASK_GET&v=" + V + "&userid=" + uid + "&c=0";

        // 获取运维账号用户名
        String username = s.username;
        if (s.accountConfig != null && s.accountConfig.length > 1) {
            username = s.accountConfig[1]; // 第二列是用户名
        }

        String post = "username=" + urlEncUtf8(username)
                + "&id=" + urlEncUtf8(id)
                + "&userid=" + uid
                + "&versionid=" + urlEncUtf8(versionId)
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=8AC513EA055F0773B4307B980D1723A9"
                + "&upvs=" + UPVS;

        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 4. 回单
    // =====================================================================
    public static String revertTask(String id, String siteId) {
        Session s   = Session.get();
        String  ts  = TimeUtil.getCurrentTimestamp();
        String  uid = urlEncUtf8(s.userid);

        String url = BASE + "?porttype=ZNDW_TASK_FILL_ORDER_INSERT&v=" + V + "&userid=" + uid + "&c=0";

        // URL编码的字段值
        String col141 = urlEncUtf8("电力局停电断电");  // 故障原因
        String col139 = urlEncUtf8("市电设施");        // 故障类型
        String col142 = urlEncUtf8("浙江八方电信有限公司"); // 代维公司
        String col143 = urlEncUtf8("来电恢复/发电恢复");   // 处理方式

        String post = "id=" + urlEncUtf8(id)
                + "&COL_141=" + col141
                + "&faultdesc=COL_141," + col141
                + "&COL_139=" + col139
                + "&faulttype=COL_139," + col139
                + "&COL_142=" + col142
                + "&company=COL_142," + col142
                + "&COL_143=" + col143
                + "&fixway=COL_143," + col143
                + "&site_id=" + urlEncUtf8(siteId)
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=0BD90C3BF148F21728C7C7CD4A3FBE78"
                + "&upvs=2026-02-05-ccssoft"
                + "&alarmEndTime=";

        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 解析未领取工单列表
    // =====================================================================
    public static List<ZhilianTaskInfo> parseUnclaimedList(String jsonStr) {
        List<ZhilianTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray arr = root.optJSONArray("listUnclaimedZndw");
            if (arr == null) return list;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                ZhilianTaskInfo info = new ZhilianTaskInfo();
                info.id = item.optString("id", "");
                info.versionId = item.optString("version_id", "");
                info.siteName = item.optString("site_name", "");
                info.siteId = item.optString("site_id", "");
                info.createTime = item.optString("create_time", "");
                info.taskName = item.optString("task_name", "");
                info.taskTypeName = item.optString("task_type_name", "");
                info.status = "未领取";
                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // =====================================================================
    // 解析已领取工单列表
    // =====================================================================
    public static List<ZhilianTaskInfo> parseClaimedList(String jsonStr) {
        List<ZhilianTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray arr = root.optJSONArray("listClaimedList");
            if (arr == null) return list;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                ZhilianTaskInfo info = new ZhilianTaskInfo();
                info.id = item.optString("id", "");
                info.siteName = item.optString("site_name", "");
                info.siteId = item.optString("site_id", "");
                info.createTime = item.optString("create_time", "");
                info.getTaskTime = item.optString("get_task_time", "");
                info.taskName = item.optString("task_name", "");
                info.taskTypeName = item.optString("task_type_name", "");
                info.doDealName = item.optString("do_deal_name", "");
                info.status = "已领取";
                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // =====================================================================
    // 工具方法
    // =====================================================================

    public static class ZhilianTaskInfo {
        public String id;
        public String versionId;
        public String siteName;
        public String siteId;
        public String createTime;
        public String getTaskTime;
        public String taskName;
        public String taskTypeName;
        public String doDealName;
        public String status;
    }

    private static String buildFullHeader(Session s) {
        return "Authorization: "  + s.token + "\n"
                + "equiptoken: \n"
                + "appVer: 202112\n"
                + "Content-Type: application/x-www-form-urlencoded\n"
                + "Host: ywapp.chinatowercom.cn:58090\n"
                + "Connection: Keep-Alive\n"
                + "User-Agent: okhttp/4.10.0";
    }

    private static String safePost(String url, String post, String headers) {
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String urlEncUtf8(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public static boolean isSuccess(String result) {
        if (result == null || result.isEmpty()) return false;
        return result.contains("OK")
                || result.contains("\"status\":\"ok\"")
                || result.contains("\"status\": \"ok\"")
                || result.contains("success")
                || result.contains("成功")
                || result.contains("操作成功")
                || result.contains("处理成功")
                || result.contains("提交成功");
    }
}

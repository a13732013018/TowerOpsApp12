package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 门禁审批 API
 * 接口1: GET LIST -> POST http://36.111.4.4:8090/api/unified/workOrder/unfinishedList/byUser
 * 接口2: APPROVE  -> POST http://workoc.chinatowercom.cn:20010/api/flow/order/orderApproval?taskId=
 */
public class DoorApprovalApi {

    private static final String LIST_URL     = "http://36.111.4.4:8090/api/unified/workOrder/unfinishedList/byUser";
    private static final String APPROVE_URL  = "http://workoc.chinatowercom.cn:20010/api/flow/order/orderApproval";
    // workoc 接口 URL
    private static final String GET_TASK_ID_URL = "http://workoc.chinatowercom.cn:20010/api/flow/appWorkItem/appAgentList";
    private static final String RECEIVE_URL     = "http://workoc.chinatowercom.cn:20010/api/flow/order/receive";

    /** 门禁审批工单数据模型 */
    public static class ApprovalItem {
        public String selfTaskName;   // 任务名称，如"铁塔出入站管理-朱国标_平阳台头村_2026-04-16"
        public String taskId;         // 工单任务ID（出入站工单ID）
        public String taskType;       // 工单类型，如"简易出入站"
        public String status;         // 状态，如"一级审批"
        public String createTime;     // 创建时间
        public String siteName;       // 站点名称
        public String loginName;      // 申请人账号
        public String busiType;       // 业务类型
        public String appUrl;         // 审批链接（含审批taskId参数）
        public String stationCode;    // 站址编码

        /**
         * 从 appUrl 中解析出审批接口所需的 taskId
         * appUrl 格式：http://...?showType=2&worderId=xxx&taskId=260416130020227994
         */
        public String getApproveTaskId() {
            if (appUrl == null || appUrl.isEmpty()) return "";
            int idx = appUrl.lastIndexOf("taskId=");
            if (idx >= 0) {
                String rest = appUrl.substring(idx + 7);
                int end = rest.indexOf("&");
                return end >= 0 ? rest.substring(0, end) : rest;
            }
            return "";
        }

        /** 获取站点简短名（去除前缀） */
        public String getShortName() {
            if (siteName != null && !siteName.isEmpty()) return siteName;
            if (selfTaskName == null || selfTaskName.isEmpty()) return "";
            int u1 = selfTaskName.indexOf('_');
            int u2 = u1 >= 0 ? selfTaskName.indexOf('_', u1 + 1) : -1;
            if (u1 >= 0 && u2 > u1) return selfTaskName.substring(u1 + 1, u2);
            return selfTaskName;
        }
    }

    /**
     * 构建门禁审批接口的请求头
     * 参考易语言成功请求的协议头格式
     */
    private static String buildHeaders(Session s) {
        StringBuilder sb = new StringBuilder();
        // 易语言成功请求头格式
        sb.append("Content-Type: application/json\n");
        sb.append("Accept: */*\n");
        sb.append("Origin: http://36.111.4.4:8090\n");
        sb.append("X-Requested-With: com.chinatower.mom\n");
        sb.append("Referer: http://36.111.4.4:8090/\n");

        // X-Auth-Token（必须，来自 OBTAIN_TOKEN_NEW 接口）
        if (s.doorApprovalXAuthToken != null && !s.doorApprovalXAuthToken.isEmpty()) {
            sb.append("X-Auth-Token: ").append(s.doorApprovalXAuthToken).append("\n");
        }
        // loginAcct（必须，如 wx-linjy22）
        if (s.doorApprovalLoginAcct != null && !s.doorApprovalLoginAcct.isEmpty()) {
            sb.append("loginAcct: ").append(s.doorApprovalLoginAcct).append("\n");
        }
        // acctId（必须，如 203349045）
        if (s.doorApprovalAcctId != null && !s.doorApprovalAcctId.isEmpty()) {
            sb.append("acctId: ").append(s.doorApprovalAcctId).append("\n");
        }
        // 易语言不使用 Authorization Bearer，移除
        // 易语言不带 Cookie
        return sb.toString();
    }

    /**
     * 构建 workoc 接口（workoc.chinatowercom.cn:20010）的认证请求头
     * 参考易语言 #常量_认证协议头
     */
    private static String buildWorkOcHeaders(Session s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Content-Type: application/json\n");
        sb.append("Accept: application/json, text/plain, */*\n");
        sb.append("loginName: ").append(s.doorApprovalLoginAcct).append("\n");
        sb.append("appSyscode: CHNTRMS2\n");
        sb.append("token: ").append(s.doorApprovalXAuthToken).append("\n");
        sb.append("Origin: http://workoc.chinatowercom.cn:20010\n");
        sb.append("X-Requested-With: com.chinatower.mom\n");
        sb.append("Referer: http://workoc.chinatowercom.cn:20010/\n");
        // Cookie 中需要包含 token
        sb.append("Cookie: token=").append(s.doorApprovalXAuthToken).append("\n");
        return sb.toString();
    }

    /**
     * 检查认证信息是否完整
     * @return 缺失的字段名，空串表示完整
     */
    public static String checkAuth() {
        Session s = Session.get();
        android.util.Log.d("DoorApproval", "========== 认证检查 ==========");
        android.util.Log.d("DoorApproval", "X-Auth-Token: [" + (s.doorApprovalXAuthToken == null ? "NULL" : s.doorApprovalXAuthToken) + "]");
        android.util.Log.d("DoorApproval", "loginAcct:    [" + (s.doorApprovalLoginAcct == null ? "NULL" : s.doorApprovalLoginAcct) + "]");
        android.util.Log.d("DoorApproval", "acctId:       [" + (s.doorApprovalAcctId == null ? "NULL" : s.doorApprovalAcctId) + "]");
        android.util.Log.d("DoorApproval", "ommsCookie:   [" + (s.ommsCookie == null ? "NULL" : s.ommsCookie) + "]");
        android.util.Log.d("DoorApproval", "token:        [" + (s.token == null ? "NULL" : s.token) + "]");
        android.util.Log.d("DoorApproval", "userid:       [" + (s.userid == null ? "NULL" : s.userid) + "]");
        if (s.doorApprovalXAuthToken == null || s.doorApprovalXAuthToken.isEmpty()) {
            android.util.Log.d("DoorApproval", "缺失: X-Auth-Token");
            return "X-Auth-Token";
        }
        if (s.doorApprovalLoginAcct == null || s.doorApprovalLoginAcct.isEmpty()) {
            android.util.Log.d("DoorApproval", "缺失: loginAcct");
            return "loginAcct";
        }
        if (s.doorApprovalAcctId == null || s.doorApprovalAcctId.isEmpty()) {
            android.util.Log.d("DoorApproval", "缺失: acctId");
            return "acctId";
        }
        android.util.Log.d("DoorApproval", "认证完整，开始请求");
        return "";
    }

    /**
     * 获取待审批工单列表（所有页，最多拉5页）
     *
     * @return 审批列表，失败返回空列表
     */
    public static List<ApprovalItem> getUnfinishedList() {
        List<ApprovalItem> all = new ArrayList<>();
        Session s = Session.get();
        String headers = buildHeaders(s);
        android.util.Log.d("DoorApproval", "========== 请求门禁审批列表 ==========");
        android.util.Log.d("DoorApproval", "URL: " + LIST_URL);
        android.util.Log.d("DoorApproval", "Headers:\n" + headers);

        try {
            int page = 1;
            int maxPage = 5;
            while (page <= maxPage) {
                // 注意：orderTitle 必须是空字符串，不筛选
                String body = "{\"page\":" + page + ",\"size\":50,\"obj\":"
                        + "{\"busiType\":\"\",\"siteCode\":\"\",\"taskId\":\"\","
                        + "\"siteName\":\"\",\"orderTitle\":\"\"}}";
                android.util.Log.d("DoorApproval", "第" + page + "页请求 body: " + body);

                HttpUtil.HttpResponse resp = HttpUtil.postWithHeaders(LIST_URL, body, headers, null);
                if (resp == null || resp.body == null || resp.body.isEmpty()) {
                    android.util.Log.d("DoorApproval", "第" + page + "页: 响应为空");
                    break;
                }

                android.util.Log.d("DoorApproval", "第" + page + "页响应: " + resp.body);
                JSONObject json = new JSONObject(resp.body);
                int code = json.optInt("code", -1);
                android.util.Log.d("DoorApproval", "第" + page + "页: code=" + code);
                if (code != 10000) {
                    android.util.Log.w("DoorApproval", "查询失败 code=" + code + " body=" + resp.body);
                    break;
                }

                JSONObject data = json.optJSONObject("data");
                if (data == null) {
                    android.util.Log.d("DoorApproval", "第" + page + "页: data=null");
                    break;
                }

                JSONArray records = data.optJSONArray("records");
                if (records == null || records.length() == 0) {
                    android.util.Log.d("DoorApproval", "第" + page + "页: records=null或空");
                    break;
                }

                for (int i = 0; i < records.length(); i++) {
                    JSONObject r = records.getJSONObject(i);
                    ApprovalItem item = new ApprovalItem();
                    item.selfTaskName = r.optString("selfTaskName", "");
                    item.taskId       = r.optString("taskId", "");
                    item.taskType     = r.optString("taskType", "");
                    item.status       = r.optString("status", "");
                    item.createTime   = r.optString("createTime", "");
                    item.siteName     = r.optString("siteName", "");
                    item.loginName    = r.optString("loginName", "");
                    item.busiType     = r.optString("busiType", "");
                    item.appUrl       = r.optString("appUrl", "");
                    item.stationCode  = r.optString("stationCode", "");
                    all.add(item);
                }

                int total = data.optInt("total", 0);
                android.util.Log.d("DoorApproval", "第" + page + "页：返回 " + records.length() + " 条，总共 " + total + " 条");
                if (all.size() >= total) break;
                page++;
            }
        } catch (Exception e) {
            android.util.Log.e("DoorApproval", "getUnfinishedList 异常", e);
        }
        android.util.Log.d("DoorApproval", "获取完成，共 " + all.size() + " 条");
        return all;
    }

    /**
     * 审批通过单个工单（两步流程）
     * 列表返回的 appUrl 中已包含 TASK_ID，格式: taskId=260416130020227967
     * 
     * 1. 接单: POST /api/flow/order/receive
     * 2. 回单: POST /api/flow/order/orderApproval?taskId=TASK_ID
     *
     * @param appUrl 列表接口返回的 appUrl，包含 taskId 参数
     * @return true=成功，false=失败
     */
    public static boolean approveByAppUrl(String appUrl) {
        Session s = Session.get();
        String workOcHeaders = buildWorkOcHeaders(s);
        android.util.Log.d("DoorApprovalApi", "========== 两步审核开始 ==========");
        android.util.Log.d("DoorApprovalApi", "appUrl: " + appUrl);

        // 从 appUrl 中提取 TASK_ID
        String taskId = extractTaskIdFromUrl(appUrl);
        if (taskId == null || taskId.isEmpty()) {
            android.util.Log.e("DoorApprovalApi", "从 appUrl 提取 TASK_ID 失败");
            return false;
        }
        android.util.Log.d("DoorApprovalApi", "从 appUrl 提取到 TASK_ID: " + taskId);

        try {
            // ========== 步骤1: 接单 ==========
            android.util.Log.d("DoorApprovalApi", "步骤1: 接单...");
            String receiveBody = "{\"taskId\":\"" + taskId + "\"}";
            String receiveResp = HttpUtil.post(RECEIVE_URL, receiveBody, workOcHeaders, null);
            android.util.Log.d("DoorApprovalApi", "接单响应: " + receiveResp);

            // ========== 步骤2: 回单 ==========
            android.util.Log.d("DoorApprovalApi", "步骤2: 回单...");
            String approveUrl = APPROVE_URL + "?taskId=" + taskId;
            String approveBody = "{\"cols\":[],\"approvalResult\":\"01\",\"approvalComment\":\"同意\"}";
            String approveResp = HttpUtil.post(approveUrl, approveBody, workOcHeaders, null);
            android.util.Log.d("DoorApprovalApi", "回单响应: " + approveResp);

            if (approveResp == null || approveResp.isEmpty()) {
                android.util.Log.e("DoorApprovalApi", "回单响应为空");
                return false;
            }

            JSONObject json = new JSONObject(approveResp);
            String code = json.optString("code", "");
            boolean success = "0000".equals(code);
            android.util.Log.d("DoorApprovalApi", "两步审核完成: " + (success ? "成功" : "失败 code=" + code));
            return success;
        } catch (Exception e) {
            android.util.Log.e("DoorApprovalApi", "approve 异常", e);
            return false;
        }
    }

    /**
     * 从 appUrl 中提取 TASK_ID
     * appUrl 格式: http://workoc.chinatowercom.cn:20010/#/audit-detailsAPP?showType=2&worderId=xxx&taskId=260416130020227967
     */
    private static String extractTaskIdFromUrl(String appUrl) {
        if (appUrl == null || appUrl.isEmpty()) return null;
        // 查找 taskId= 后面的值
        int idx = appUrl.indexOf("taskId=");
        if (idx < 0) return null;
        idx += 7; // 跳过 "taskId="
        int end = appUrl.indexOf("&", idx);
        if (end < 0) end = appUrl.length();
        return appUrl.substring(idx, end);
    }

    /**
     * 从响应中提取 TASK_ID
     * 格式: "TASK_ID":"xxx","DETAIL"
     */
    private static String extractTaskId(String resp) {
        if (resp == null || resp.isEmpty()) return null;
        // 查找 "TASK_ID":" 开头和 ","DETAIL 结尾之间的内容
        int start = resp.indexOf("\"TASK_ID\":\"");
        if (start < 0) start = resp.indexOf("TASK_ID\":\"");
        if (start < 0) return null;
        start += 11; // 跳过 "TASK_ID":" 
        int end = resp.indexOf("\",\"DETAIL\"", start);
        if (end < 0) end = resp.indexOf("\",DETAIL\"", start);
        if (end < 0) end = resp.indexOf("\"}", start);
        if (end < 0) end = resp.indexOf("}", start);
        if (end < 0) return resp.substring(start);
        return resp.substring(start, end);
    }
}

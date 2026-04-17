package com.towerops.app.api;

import android.util.Log;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 数运综合上站报表 API
 * 接口：POST http://zjtowercom.cn:8998/api/report/report/dataTable/20251230003
 */
public class ShuyunReportApi {

    private static final String TAG = "ShuyunReportApi";
    private static final String BASE_URL = "http://zjtowercom.cn:8998";
    private static final String REPORT_ID = "20251230003";

    /**
     * 综合上站报表数据项（按10列表头）
     */
    public static class ReportItem {
        public String areaName;       // 地市
        public String cityName;       // 区县
        public String dateType;       // 起止日期
        public int easyOrder;         // 中台派单(简易派单)
        public int sysOrder;          // 系统派单(不算简易派单)
        public int appOrder;          // 综合上站APP
        public int otherOrder;        // 非综合上站
        public String dailyReply;     // 单日单站回单
        public String efficiency;     // 综合上站效能
        public String dwShort;        // 代维公司

        @Override
        public String toString() {
            return "ReportItem{" +
                    "areaName='" + areaName + '\'' +
                    ", cityName='" + cityName + '\'' +
                    ", dateType='" + dateType + '\'' +
                    ", easyOrder=" + easyOrder +
                    ", sysOrder=" + sysOrder +
                    ", appOrder=" + appOrder +
                    ", otherOrder=" + otherOrder +
                    ", efficiency='" + efficiency + '\'' +
                    ", dwShort='" + dwShort + '\'' +
                    '}';
        }
    }

    /**
     * 获取综合上站报表
     * @param areaName 区域名称（如"温州市"）
     * @param startTime 开始时间（格式：yyyy-MM-dd）
     * @param endTime 结束时间（格式：yyyy-MM-dd）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 报表数据列表
     */
    public static List<ReportItem> getReportList(String areaName, String startTime, String endTime, int page, int pageSize) {
        List<ReportItem> result = new ArrayList<>();

        String url = BASE_URL + "/api/report/report/dataTable/" + REPORT_ID;

        // 构建请求体
        JSONObject body = new JSONObject();
        try {
            body.put("areaName-in-", areaName);
            body.put("start_time", startTime);
            body.put("end_time", endTime);
        } catch (Exception e) {
            e.printStackTrace();
            return result;
        }

        // 获取 PC Token（双token机制）
        Session session = Session.get();
        String authToken = session.shuyunPcToken;
        String cookieToken = session.shuyunPcTokenCookie;
        
        // 兼容：如果没有cookieToken，使用authToken
        if (cookieToken == null || cookieToken.isEmpty()) {
            cookieToken = authToken;
        }

        if (authToken == null || authToken.isEmpty()) {
            Log.e(TAG, "PC Token 为空，请先登录PC版");
            return result;
        }

        // 使用与ShuyunApi相同的完整请求头格式
        String cookie = "towerNumber-Token=" + cookieToken;
        String extraHeaders = 
                "Accept: application/json, text/plain, */*\n" +
                "Accept-Language: zh-CN,zh;q=0.9\n" +
                "Authorization: " + authToken + "\n" +
                "Cache-Control: no-cache\n" +
                "Connection: keep-alive\n" +
                "Content-Type: application/json;charset=UTF-8\n" +
                "Cookie: " + cookie + "\n" +
                "Host: zjtowercom.cn:8998\n" +
                "Origin: http://zjtowercom.cn:8998\n" +
                "Pragma: no-cache\n" +
                "Referer: http://zjtowercom.cn:8998/dashboard\n" +
                "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";

        Log.d(TAG, "请求头 Cookie: towerNumber-Token=" + (cookieToken.length() > 20 ? cookieToken.substring(0, 20) + "..." : cookieToken));
        Log.d(TAG, "请求体: " + body.toString());

        try {
            String response = HttpUtil.post(url, body.toString(), extraHeaders, null);
            Log.d(TAG, "报表响应: " + (response != null && response.length() > 500 ? response.substring(0, 500) + "..." : response));

            if (response == null || response.isEmpty()) {
                Log.e(TAG, "报表响应为空");
                return result;
            }

            JSONObject root = new JSONObject(response);
            int statusCode = root.optInt("status", 0);
            int code = root.optInt("code", 0);
            if (statusCode == 40301 || code == 40301) {
                Log.e(TAG, "Token过期，请在数运监控中重新登录PC版");
                return result;
            }
            if (statusCode != 1 && code != 1) {
                Log.e(TAG, "报表接口返回错误: status=" + statusCode + ", code=" + code + ", message=" + root.optString("message"));
                return result;
            }

            // 支持data为JSONArray或包含records的JSONObject
            JSONArray records = null;
            records = root.optJSONArray("data");
            if (records == null || records.length() == 0) {
                JSONObject dataObj = root.optJSONObject("data");
                if (dataObj != null) {
                    records = dataObj.optJSONArray("records");
                }
            }
            
            if (records == null || records.length() == 0) {
                Log.d(TAG, "报表记录为空");
                return result;
            }

            for (int i = 0; i < records.length(); i++) {
                JSONObject item = records.optJSONObject(i);
                if (item == null) continue;

                ReportItem reportItem = new ReportItem();
                reportItem.areaName = item.optString("area_name", "");
                reportItem.cityName = item.optString("city_name", "");
                reportItem.dateType = item.optString("dateType", "");
                reportItem.dwShort = item.optString("dw_short", "");
                
                // 字段映射（根据实际API返回调整）
                // 这些字段需要根据实际的API返回字段来映射
                reportItem.easyOrder = item.optInt("n1_num3", 0);           // 故障上站数作为中台派单
                reportItem.sysOrder = item.optInt("num3", 0) - item.optInt("n1_num3", 0);  // 上站总数-故障
                reportItem.appOrder = item.optInt("week_zh_num", 0) + item.optInt("week_jy_num", 0);  // 纵横+竞维
                reportItem.otherOrder = 0;  // 待确定
                reportItem.dailyReply = "-";  // 待确定
                reportItem.efficiency = item.optString("n4_num3", "0");  // 及时率

                result.add(reportItem);
            }

            Log.d(TAG, "解析到 " + result.size() + " 条报表记录");

        } catch (Exception e) {
            Log.e(TAG, "解析报表数据异常: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }
}

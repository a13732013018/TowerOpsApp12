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
     * 综合上站报表数据项
     */
    public static class ReportItem {
        public String areaName;      // 区域名（温州市）
        public String cityName;      // 区县名
        public String cityCode;      // 区县代码
        public String areaCode;      // 区域代码
        public String dateType;      // 日期类型（2026-04-01~2026-04-17 或 2026-04）
        public int n1Num3;           // 故障上站数
        public String n3Num3;        // 平均断电退服时长(小时)
        public String n4Num3;        // 上站及时率
        public int weekZhNum;        // 纵横周巡检上站数
        public int weekJyNum;        // 竞维周巡检上站数
        public String dwShort;       // 代维简称
        public int num3;             // 上站总数

        @Override
        public String toString() {
            return "ReportItem{" +
                    "cityName='" + cityName + '\'' +
                    ", dateType='" + dateType + '\'' +
                    ", n1Num3=" + n1Num3 +
                    ", n3Num3='" + n3Num3 + '\'' +
                    ", n4Num3='" + n4Num3 + '\'' +
                    ", weekZhNum=" + weekZhNum +
                    ", weekJyNum=" + weekJyNum +
                    ", dwShort='" + dwShort + '\'' +
                    ", num3=" + num3 +
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

        // 获取 PC Token
        Session session = Session.get();
        String pcToken = session.shuyunPcToken;

        if (pcToken == null || pcToken.isEmpty()) {
            Log.e(TAG, "PC Token 为空，请先登录PC版");
            return result;
        }

        // 构建请求头
        String pageStr = page + "-" + pageSize;
        String cookies = "towerNumber-Token=" + pcToken + "; sysName=%E4%BC%8A%E4%B8%96%E8%B1%AA";

        // 构建自定义协议头（Accept和Content-Type行会被自动添加）
        String extraHeaders = "";

        try {
            // 使用 HttpUtil.post 方法发送 JSON 请求
            String response = HttpUtil.post(url, body.toString(), extraHeaders, cookies);
            Log.d(TAG, "报表响应: " + response);

            if (response == null || response.isEmpty()) {
                Log.e(TAG, "报表响应为空");
                return result;
            }

            JSONObject root = new JSONObject(response);
            int code = root.optInt("code", 0);
            if (code != 1) {
                Log.e(TAG, "报表接口返回错误: code=" + code + ", message=" + root.optString("message"));
                return result;
            }

            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                Log.e(TAG, "报表数据为空");
                return result;
            }

            JSONArray records = data.optJSONArray("records");
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
                reportItem.cityCode = item.optString("city_code", "");
                reportItem.areaCode = item.optString("area_code", "");
                reportItem.dateType = item.optString("dateType", "");
                reportItem.n1Num3 = item.optInt("n1_num3", 0);
                reportItem.n3Num3 = item.optString("n3_num3", "0");
                reportItem.n4Num3 = item.optString("n4_num3", "0");
                reportItem.weekZhNum = item.optInt("week_zh_num", 0);
                reportItem.weekJyNum = item.optInt("week_jy_num", 0);
                reportItem.dwShort = item.optString("dw_short", "");
                reportItem.num3 = item.optInt("num3", 0);

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

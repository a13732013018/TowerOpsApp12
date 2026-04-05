package com.towerops.app.api;

import com.towerops.app.model.Session;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 省内工单处理及时率 API
 * 数据来源: http://zjtowercom.cn:8998/api/report/report/dataTable/20221125001
 */
public class ProvinceOrderRateApi {

    private static final String BASE_URL = "http://zjtowercom.cn:8998/api/report/report/dataTable/20221125001";

    // 数运协议头（不含Authorization）
    private static final String BASE_HEADERS =
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n" +
            "Content-Type: application/json\r\n" +
            "Accept: application/json\r\n" +
            "Referer: http://zjtowercom.cn:8998/reportManage/provincialTask\r\n";

    /**
     * 构建带Authorization的请求头
     */
    private static String buildHeaders() {
        String token = Session.get().shuyunPcToken;
        return BASE_HEADERS +
                "Authorization: " + (token != null && !token.isEmpty() ? token : "") + "\r\n" +
                "Cookie: towerNumber-Token=" + (token != null && !token.isEmpty() ? token : "") + "\r\n";
    }

    /**
     * 省内工单及时率数据模型
     */
    public static class ProvinceOrderRateItem {
        public String statTime;        // 统计时间
        public String createSheet;     // 当月派发工单
        public String noStatusSheet;  // 未归档工单
        public String statusSheet;     // 当月归档工单
        public String csStatusSheet;   // 当月归档超时工单
        public String csNoStatusSheet; // 未归档超时工单
        public String clRate;         // 处理及时率
        public String cityName;       // 区县名称
    }

    /**
     * 获取省内工单处理及时率数据（供MetricsFragment调用）
     * @param pcToken PC Token（未使用，仅保持接口一致性）
     * @param cookieToken Cookie Token（未使用）
     * @param dataDate 统计日期，格式如 2026-01-01
     * @return JSON字符串
     */
    public static String queryProvinceOrderRate(String pcToken, String cookieToken, String dataDate) {
        try {
            String postData = String.format(
                    "{\"region_type\":\"3\",\"cityid\":\"330300\",\"data_date\":\"%s\"}",
                    dataDate
            );
            return doPost(postData);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取省内工单处理及时率数据
     * @param dataDate 统计日期，格式如 2026-01-01
     * @return 工单及时率列表
     */
    public static List<ProvinceOrderRateItem> getProvinceOrderRateList(String dataDate) {
        List<ProvinceOrderRateItem> result = new ArrayList<>();

        try {
            // region_type=3 表示省内工单
            String postData = String.format(
                    "{\"region_type\":\"3\",\"cityid\":\"330300\",\"data_date\":\"%s\"}",
                    dataDate
            );

            String jsonStr = doPost(postData);
            if (jsonStr == null || jsonStr.isEmpty()) {
                return result;
            }

            JSONObject root = new JSONObject(jsonStr);
            JSONArray dataArray = root.optJSONArray("data");

            if (dataArray == null) {
                return result;
            }

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                ProvinceOrderRateItem entry = new ProvinceOrderRateItem();

                entry.cityName = item.optString("CITY_NAME", "");
                entry.statTime = item.optString("DATA_DATE", "");
                entry.createSheet = item.optString("THIS_CREATE_SHEET", "0");
                entry.noStatusSheet = item.optString("THIS_NOSTATUS_SHEET", "0");
                entry.statusSheet = item.optString("THIS_STATUS_SHEET", "0");
                entry.csStatusSheet = item.optString("CS_STATUS_SHEET", "0");
                entry.csNoStatusSheet = item.optString("CS_NOSTATUS_SHEET", "0");
                entry.clRate = item.optString("CL_RATE", "0%");

                result.add(entry);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 执行POST请求
     */
    private static String doPost(String postData) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            // 设置请求头（含Authorization）
            String headers = buildHeaders();
            String[] headerLines = headers.split("\r\n");
            for (String header : headerLines) {
                if (header.contains(":")) {
                    String[] parts = header.split(":", 2);
                    conn.setRequestProperty(parts[0].trim(), parts[1].trim());
                }
            }

            // 发送请求数据
            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("UTF-8"));
            os.flush();
            os.close();

            // 读取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                return response.toString();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }
}

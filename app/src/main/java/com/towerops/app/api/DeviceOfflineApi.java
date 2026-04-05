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
 * 设备离线 API
 * 数据来源: http://zjtowercom.cn:8998/api/report/report/dataTable/20240716001
 */
public class DeviceOfflineApi {

    private static final String BASE_URL = "http://zjtowercom.cn:8998/api/report/report/dataTable/20240716001";

    // 数运协议头（不含Authorization）
    private static final String BASE_HEADERS =
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n" +
            "Content-Type: application/json\r\n" +
            "Accept: application/json\r\n" +
            "Referer: http://zjtowercom.cn:8998/reportManage/deviceComprehensive\r\n";

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
     * 设备离线数据模型
     */
    public static class DeviceOfflineItem {
        public String county;        // 区县
        public String teamName;      // 责任小组
        public String siteCode;     // 站址编码
        public String siteId;       // 运维ID
        public String siteName;     // 站点名称
        public String offlineDevice;// 离线设备
        public String isFullOffline;// 是否全设备离线
        public String statTime;     // 统计时间

        @Override
        public String toString() {
            return siteName + " - " + offlineDevice;
        }
    }

    /**
     * 获取设备离线列表
     * @param countyId 区县代码
     * @param limit 每页数量，默认10
     * @param page 页码，默认1
     * @return 设备离线列表
     */
    public static List<DeviceOfflineItem> getDeviceOfflineList(String countyId, int limit, int page) {
        List<DeviceOfflineItem> result = new ArrayList<>();

        try {
            String postData = String.format(
                    "{\"cityid\":\"330300\",\"countyid\":\"%s\",\"site_name\":\"\",\"site_code\":\"\",\"site_id\":\"\",\"limit\":%d,\"page\":%d}",
                    countyId, limit, page
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

            // 分组常量 - 运维ID匹配规则
            String[] teamConstants = {
                    "卢智伟、杨桂",     // 抢修1组
                    "高树调、倪传井",   // 抢修2组
                    "苏忠前、许方喜",   // 抢修3组
                    "黄经兴、蔡亮",     // 抢修4组
                    "陈德岳"           // 室分1组
            };

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                DeviceOfflineItem entry = new DeviceOfflineItem();

                entry.county = item.optString("COUNTY", "");
                entry.siteCode = item.optString("SITE_CODE", "");
                entry.siteId = item.optString("SITE_ID", "");
                entry.siteName = item.optString("SITE_NAME", "");
                entry.offlineDevice = item.optString("REMARK", "");
                entry.isFullOffline = item.optString("IF_LX", "");
                entry.statTime = item.optString("SYS_DATE", "");

                // 根据站点名称匹配责任小组
                entry.teamName = matchTeam(entry.siteId, teamConstants);

                result.add(entry);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 根据运维ID匹配责任小组
     */
    private static String matchTeam(String siteId, String[] teams) {
        // 这里可以根据siteId的特定模式来匹配不同的组
        // 由于原始代码是模糊匹配，这里简化为直接返回第一组或空
        // 实际可根据需求扩展
        for (String team : teams) {
            if (siteId.contains(team)) {
                return team;
            }
        }
        return "";
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

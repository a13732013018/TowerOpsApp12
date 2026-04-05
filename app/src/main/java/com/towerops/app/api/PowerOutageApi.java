package com.towerops.app.api;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;
import com.towerops.app.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 停电监控 API
 * 对应易语言中的取停电告警、取开关电源、取开关电源数据
 */
public class PowerOutageApi {

    private static final String BASE = "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service";

    // 版本号统一在 LoginApi.V 维护，升级只需改那一处
    private static final String V = LoginApi.V;
    private static final String UPVS = "2025-03-23-ccssoft";

    // 分组常量（对应易语言中的局_分组常量）
    private static final String[] GROUP_CONSTANTS = {
            "综合1组", "综合2组", "综合3组", "综合4组", "综合5组", "室分1组"
    };

    private static final String[] GROUP_KEYWORDS = {
            "综合1", "综合2", "综合3", "综合4", "综合5", "室分1"
    };

    // =====================================================================
    // 1. 获取停电告警列表
    // 对应易语言：取停电告警()
    // =====================================================================
    public static String getPowerOutageList() {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = WorkOrderApi.urlEncUtf8(s.userid);

        String url = BASE + "?porttype=FSU_ALARM_LIST&v=" + V + "&userid=" + uid + "&c=0";
        String post = "start=1&limit=2000"
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=2B940AABF527BE2F00AB5A160B361FCB"
                + "&upvs=" + UPVS
                + "&provinceId=&cityId=&areaId=&alarmlevel=&begintimetype=&stationcode=&alarmname=%E5%81%9C%E7%94%B5";

        android.util.Log.d("PowerOutageApi", "getPowerOutageList: 开始请求, token=" + (s.token.isEmpty() ? "空" : "有值"));
        android.util.Log.d("PowerOutageApi", "getPowerOutageList: url=" + url);
        android.util.Log.d("PowerOutageApi", "getPowerOutageList: post=" + post);

        String response = safePost(url, post, buildFullHeader(s));

        android.util.Log.d("PowerOutageApi", "getPowerOutageList: 响应长度=" + (response != null ? response.length() : "null"));
        if (response == null || response.isEmpty()) {
            android.util.Log.e("PowerOutageApi", "getPowerOutageList: 响应为空!");
        } else if (response.contains("error") || response.contains("失败")) {
            android.util.Log.e("PowerOutageApi", "getPowerOutageList: 响应包含错误信息");
            android.util.Log.e("PowerOutageApi", "getPowerOutageList: response=" + response);
        }

        return response;
    }

    // =====================================================================
    // 2. 获取站点设备列表（开关电源）
    // 对应易语言：取开关电源(areaId)
    // =====================================================================
    public static String getStationDeviceList(String areaId) {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = WorkOrderApi.urlEncUtf8(s.userid);

        String url = BASE + "?porttype=GET_STATION_DEVICE_LIST&v=" + V + "&userid=" + uid + "&c=0";
        String post = "areaId=" + (areaId != null ? areaId : "")
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=53862CCD3BD2E38A51A70BF00B0FD74E"
                + "&upvs=" + UPVS;

        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 3. 获取开关电源实时数据
    // 对应易语言：取开关电源数据(stationId, deviceId)
    // =====================================================================
    public static String getPowerDeviceData(String stationId, String deviceId) {
        Session s = Session.get();
        String ts = TimeUtil.getCurrentTimestamp();
        String uid = WorkOrderApi.urlEncUtf8(s.userid);

        String url = BASE + "?porttype=GET_FSU_DEVICE_REALTIME_PERFORMANCE&v=" + V + "&userid=" + uid + "&c=0";
        String post = "type=0&showflag=1"
                + "&stationId=" + stationId
                + "&deviceId=" + deviceId
                + "&loginName=wx-liujj6"
                + "&c_timestamp=" + ts
                + "&c_account=" + uid
                + "&c_sign=E6B7C177B5E46D21F71952E219164C79"
                + "&upvs=" + UPVS;

        return safePost(url, post, buildFullHeader(s));
    }

    // =====================================================================
    // 解析停电告警列表
    // =====================================================================
    public static List<JSONObject> parseAlarmList(String jsonStr) {
        List<JSONObject> result = new ArrayList<>();
        try {
            if (jsonStr == null || jsonStr.isEmpty()) {
                android.util.Log.e("PowerOutageApi", "parseAlarmList: jsonStr is null or empty");
                return result;
            }
            android.util.Log.d("PowerOutageApi", "parseAlarmList: response=" + jsonStr.substring(0, Math.min(200, jsonStr.length())));
            JSONObject root = new JSONObject(jsonStr);
            JSONArray alarmList = root.optJSONArray("alarmList");
            if (alarmList != null) {
                for (int i = 0; i < alarmList.length(); i++) {
                    result.add(alarmList.getJSONObject(i));
                }
                android.util.Log.d("PowerOutageApi", "parseAlarmList: 解析到 " + alarmList.length() + " 个停电告警");
            } else {
                android.util.Log.w("PowerOutageApi", "parseAlarmList: alarmList is null, response=" + jsonStr);
            }
        } catch (Exception e) {
            android.util.Log.e("PowerOutageApi", "parseAlarmList: 解析异常", e);
            e.printStackTrace();
        }
        return result;
    }

    // =====================================================================
    // 解析设备列表，获取开关电源
    // =====================================================================
    public static String findPowerSupplyDeviceCode(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray deviceList = root.optJSONArray("deviceList");
            if (deviceList != null) {
                for (int i = 0; i < deviceList.length(); i++) {
                    JSONObject device = deviceList.getJSONObject(i);
                    if ("开关电源".equals(device.optString("devicename"))) {
                        return device.optString("code");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // =====================================================================
    // 解析开关电源数据
    // =====================================================================
    public static void parsePowerDeviceData(String jsonStr, JSONObject outData) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray list = root.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    String semaphoreName = item.optString("semaphorename");
                    String measureVal = item.optString("measureval");

                    switch (semaphoreName) {
                        case "直流电压":
                            outData.put("dcVoltage", measureVal);
                            break;
                        case "直流负载总电流":
                            outData.put("dcLoadCurrent", measureVal);
                            break;
                        case "配置模块数量":
                            outData.put("moduleCount", measureVal);
                            break;
                        case "浮充电压设定值":
                            outData.put("floatVoltage", measureVal);
                            break;
                        case "移动租户电流":
                            outData.put("mobileCurrent", measureVal);
                            break;
                        case "联通租户电流":
                            outData.put("unicomCurrent", measureVal);
                            break;
                        case "电信租户电流":
                            outData.put("telecomCurrent", measureVal);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================================
    // 根据站点名称匹配分组
    // 对应易语言：匹配分组()
    // =====================================================================
    public static String matchGroup(String stName) {
        if (stName == null || stName.isEmpty()) return "";
        for (int i = 0; i < GROUP_KEYWORDS.length; i++) {
            if (stName.contains(GROUP_KEYWORDS[i])) {
                return GROUP_CONSTANTS[i];
            }
        }
        return "";
    }

    // =====================================================================
    // 工具：构建完整协议头
    // =====================================================================
    static String buildFullHeader(Session s) {
        return "Authorization: " + s.token + "\n"
                + "appVer: 202112\n"
                + "Content-Type: application/x-www-form-urlencoded\n"
                + "Host: ywapp.chinatowercom.cn:58090\n"
                + "User-Agent: okhttp/4.10.0";
    }

    // =====================================================================
    // 工具：安全 POST
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
}

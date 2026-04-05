package com.towerops.app.model;

/**
 * 门禁告警数据模型
 * 对应易语言的内存数组集_内存_站名、集_内存_告警时间等
 */
public class AccessControlItem {

    private int index;           // 序号
    private String stCode;       // 站址编码
    private String stName;       // 站名
    private String alarmTime;    // 告警时间（firstsystemtime）
    private String alarmCause;   // 告警原因（cause）
    private String objId;        // FSU对象ID（objid = fsuid，用于 getRemoteOpenTime/doOpenDoor 第一参数）
    private String doorId;       // 门禁设备ID（entrance_guard_id，用于 doOpenDoor 第二参数）

    // 蓝牙记录（来自数运门禁接口）
    private String bluetoothOutTime = "无蓝牙记录"; // come_out_time
    private String bluetoothInterval = "-9999";    // 蓝牙出站间隔（分钟）

    // 远程开门记录（来自OMMS接口）
    private String remoteOpenTime = "查询中...";   // 远程开门时间
    private String remoteInterval = "-9999";       // 远程开门间隔（分钟）
    private int    currentInterval = -9999;        // 告警距今分钟数

    // 最终状态
    private String status = "查询中..."; // 合格 / 不合格 / 待开门 / 已开门

    public AccessControlItem() {}

    // ===================== Getters & Setters =====================

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getStCode() { return stCode; }
    public void setStCode(String stCode) { this.stCode = stCode; }

    public String getStName() { return stName; }
    public void setStName(String stName) { this.stName = stName; }

    public String getAlarmTime() { return alarmTime; }
    public void setAlarmTime(String alarmTime) { this.alarmTime = alarmTime; }

    public String getAlarmCause() { return alarmCause; }
    public void setAlarmCause(String alarmCause) { this.alarmCause = alarmCause; }

    public String getObjId() { return objId; }
    public void setObjId(String objId) { this.objId = objId; }

    public String getDoorId() { return doorId; }
    public void setDoorId(String doorId) { this.doorId = doorId; }

    public String getBluetoothOutTime() { return bluetoothOutTime; }
    public void setBluetoothOutTime(String bluetoothOutTime) { this.bluetoothOutTime = bluetoothOutTime; }

    public String getBluetoothInterval() { return bluetoothInterval; }
    public void setBluetoothInterval(String bluetoothInterval) { this.bluetoothInterval = bluetoothInterval; }

    public String getRemoteOpenTime() { return remoteOpenTime; }
    public void setRemoteOpenTime(String remoteOpenTime) { this.remoteOpenTime = remoteOpenTime; }

    public String getRemoteInterval() { return remoteInterval; }
    public void setRemoteInterval(String remoteInterval) { this.remoteInterval = remoteInterval; }

    public int getCurrentInterval() { return currentInterval; }
    public void setCurrentInterval(int currentInterval) { this.currentInterval = currentInterval; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * 判断蓝牙记录是否有效（间隔<30分钟且有记录）
     */
    public boolean isBluetoothValid() {
        try {
            int interval = Integer.parseInt(bluetoothInterval);
            return Math.abs(interval) < 30 && !bluetoothOutTime.equals("无蓝牙记录");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断远程开门记录是否有效（间隔<30分钟）
     */
    public boolean isRemoteOpenValid() {
        try {
            int interval = Integer.parseInt(remoteInterval);
            return Math.abs(interval) < 30;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

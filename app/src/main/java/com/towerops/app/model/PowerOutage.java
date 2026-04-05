package com.towerops.app.model;

/**
 * 停电监控数据模型
 * 对应易语言代码中的站点信息
 */
public class PowerOutage {
    // 序号
    private int index;
    // 分组
    private String groupName;
    // 站点编码
    private String stCode;
    // 站点名称
    private String stName;
    // 告警时间
    private String alarmTime;
    // 直流电压
    private String dcVoltage;
    // 直流负载总电流
    private String dcLoadCurrent;
    // 配置模块数量
    private String moduleCount;
    // 浮充电压设定值
    private String floatVoltage;
    // 移动租户电流
    private String mobileCurrent;
    // 电信租户电流
    private String telecomCurrent;
    // 联通租户电流
    private String unicomCurrent;
    // 告警原因
    private String cause;
    // 设备名称
    private String deviceName;
    // 站点ID
    private String stId;

    public PowerOutage() {}

    // Getters and Setters
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getStCode() { return stCode; }
    public void setStCode(String stCode) { this.stCode = stCode; }

    public String getStName() { return stName; }
    public void setStName(String stName) { this.stName = stName; }

    public String getAlarmTime() { return alarmTime; }
    public void setAlarmTime(String alarmTime) { this.alarmTime = alarmTime; }

    public String getDcVoltage() { return dcVoltage; }
    public void setDcVoltage(String dcVoltage) { this.dcVoltage = dcVoltage; }

    public String getDcLoadCurrent() { return dcLoadCurrent; }
    public void setDcLoadCurrent(String dcLoadCurrent) { this.dcLoadCurrent = dcLoadCurrent; }

    public String getModuleCount() { return moduleCount; }
    public void setModuleCount(String moduleCount) { this.moduleCount = moduleCount; }

    public String getFloatVoltage() { return floatVoltage; }
    public void setFloatVoltage(String floatVoltage) { this.floatVoltage = floatVoltage; }

    public String getMobileCurrent() { return mobileCurrent; }
    public void setMobileCurrent(String mobileCurrent) { this.mobileCurrent = mobileCurrent; }

    public String getTelecomCurrent() { return telecomCurrent; }
    public void setTelecomCurrent(String telecomCurrent) { this.telecomCurrent = telecomCurrent; }

    public String getUnicomCurrent() { return unicomCurrent; }
    public void setUnicomCurrent(String unicomCurrent) { this.unicomCurrent = unicomCurrent; }

    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getStId() { return stId; }
    public void setStId(String stId) { this.stId = stId; }

    // 便捷方法：获取电压数值（用于排序）
    public double getVoltageValue() {
        try {
            return dcVoltage != null && !dcVoltage.isEmpty() ? Double.parseDouble(dcVoltage) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 便捷方法：获取负载电流数值（用于排序）
    public double getLoadCurrentValue() {
        try {
            return dcLoadCurrent != null && !dcLoadCurrent.isEmpty() ? Double.parseDouble(dcLoadCurrent) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

package com.towerops.app.model;

/**
 * 智联工单数据模型
 */
public class ZhilianOrder {
    
    private int index;              // 序号
    private String id;              // 工单ID
    private String versionId;       // 版本ID
    private String siteName;        // 站点名称
    private String siteId;          // 站点ID
    private String createTime;      // 创建时间
    private String getTaskTime;     // 领取时间
    private String taskName;        // 任务名称
    private String taskTypeName;    // 任务类型
    private String doDealName;      // 处理人
    private String status;          // 状态：未领取/已领取/已回单
    
    public ZhilianOrder() {
    }
    
    // Getters and Setters
    public int getIndex() {
        return index;
    }
    
    public void setIndex(int index) {
        this.index = index;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getVersionId() {
        return versionId;
    }
    
    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }
    
    public String getSiteName() {
        return siteName;
    }
    
    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }
    
    public String getSiteId() {
        return siteId;
    }
    
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }
    
    public String getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
    
    public String getGetTaskTime() {
        return getTaskTime;
    }
    
    public void setGetTaskTime(String getTaskTime) {
        this.getTaskTime = getTaskTime;
    }
    
    public String getTaskName() {
        return taskName;
    }
    
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }
    
    public String getTaskTypeName() {
        return taskTypeName;
    }
    
    public void setTaskTypeName(String taskTypeName) {
        this.taskTypeName = taskTypeName;
    }
    
    public String getDoDealName() {
        return doDealName;
    }
    
    public void setDoDealName(String doDealName) {
        this.doDealName = doDealName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}

package com.towerops.app.model;

/**
 * 我的待办 —— 单条事项数据模型
 */
public class TodoItem {

    /** 紧急程度常量 */
    public static final int PRIORITY_NORMAL   = 0;  // 普通（蓝色）
    public static final int PRIORITY_HIGH     = 1;  // 重要（橙色）
    public static final int PRIORITY_URGENT   = 2;  // 紧急（红色）

    /** 唯一 ID（System.currentTimeMillis + 随机后缀） */
    private String id;

    /** 事项标题（必填） */
    private String title;

    /** 备注内容（可选） */
    private String content;

    /** 创建时间戳（毫秒） */
    private long createTime;

    /** 是否已完成 */
    private boolean completed;

    /** 完成时间戳（毫秒，0 表示尚未完成） */
    private long completedTime;

    /** 紧急程度：0=普通 1=重要 2=紧急 */
    private int priority;

    /** 要求完成时间（截止时间），毫秒；0 表示不设置 */
    private long dueTime;

    public TodoItem() {}

    public TodoItem(String id, String title, String content, long createTime) {
        this.id          = id;
        this.title       = title;
        this.content     = content;
        this.createTime  = createTime;
        this.completed   = false;
        this.completedTime = 0;
        this.priority    = PRIORITY_NORMAL;
        this.dueTime     = 0;
    }

    // ── getter / setter ────────────────────────────────────────────────

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    public String getTitle()              { return title; }
    public void   setTitle(String title)  { this.title = title; }

    public String getContent()               { return content; }
    public void   setContent(String content) { this.content = content; }

    public long getCreateTime()              { return createTime; }
    public void setCreateTime(long t)        { this.createTime = t; }

    public boolean isCompleted()             { return completed; }
    public void    setCompleted(boolean b)   { this.completed = b; }

    public long getCompletedTime()           { return completedTime; }
    public void setCompletedTime(long t)     { this.completedTime = t; }

    public int  getPriority()                { return priority; }
    public void setPriority(int p)           { this.priority = p; }

    public long getDueTime()                 { return dueTime; }
    public void setDueTime(long t)           { this.dueTime = t; }

    // ── 辅助方法 ────────────────────────────────────────────────────────

    /** 是否已超时（截止时间已过且未完成） */
    public boolean isOverdue() {
        return !completed && dueTime > 0 && System.currentTimeMillis() > dueTime;
    }

    /** 是否今天到期（截止时间在今天之内且未超时） */
    public boolean isDueToday() {
        if (completed || dueTime <= 0) return false;
        long now = System.currentTimeMillis();
        if (now > dueTime) return false;
        // 今天结束时刻
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        cal.set(java.util.Calendar.MILLISECOND, 999);
        return dueTime <= cal.getTimeInMillis();
    }

    /** 优先级显示文字 */
    public String priorityLabel() {
        switch (priority) {
            case PRIORITY_URGENT: return "紧急";
            case PRIORITY_HIGH:   return "重要";
            default:              return "普通";
        }
    }
}

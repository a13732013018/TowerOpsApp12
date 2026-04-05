# 智联工单功能集成文档

## 概述

本文档描述了如何将易语言智联工单代码转换为Java并集成到TowerOpsApp4 Android应用中。

## 文件清单

### 新增文件

1. **模型类**
   - `app/src/main/java/com/towerops/app/model/ZhilianOrder.java` - 智联工单数据模型

2. **API类**
   - `app/src/main/java/com/towerops/app/api/ZhilianApi.java` - 智联工单API封装

3. **任务类**
   - `app/src/main/java/com/towerops/app/worker/ZhilianMonitorTask.java` - 智联工单监控任务

4. **UI类**
   - `app/src/main/java/com/towerops/app/ui/ZhilianFragment.java` - 智联工单Fragment
   - `app/src/main/java/com/towerops/app/ui/ZhilianAdapter.java` - 智联工单列表适配器

5. **布局文件**
   - `app/src/main/res/layout/fragment_zhilian.xml` - 智联工单界面布局
   - `app/src/main/res/layout/item_zhilian_order.xml` - 工单列表项布局
   - `app/src/main/res/drawable/bg_circle_blue.xml` - 圆形背景

### 修改文件

1. **Session.java** - 添加智联工单配置字段
2. **MainPagerAdapter.java** - 支持3个Tab
3. **MainActivity.java** - 添加智联工单Tab标题

## 功能特性

### 1. 自动接单
- 轮询未领取工单列表
- 当工单数量小于10时自动接单
- 仿生延迟：2.5-6秒随机延迟
- 操作时序锁：保证8-15秒操作间隔

### 2. 自动回单
- 轮询已领取工单列表
- 根据创建时间判断（300-720分钟）
- 仿生延迟：5-12秒随机延迟
- 只处理前3条符合条件的工单

### 3. 手动操作
- 支持手动接单
- 支持手动回单
- 实时日志显示

### 4. 网络锁机制
- 全局网络锁防止并发冲突
- 分段锁处理不同工单
- 操作时序锁保证最小间隔

## API接口

### 1. 获取未领取工单列表
```
POST http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service?porttype=ZNDW_ZL_TASK_LIST
```

### 2. 获取已领取工单列表
```
POST http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service?porttype=ZNDW_ZL_TASKDRAW_LIST
```

### 3. 接单
```
POST http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service?porttype=ZNDW_TASK_GET
```

### 4. 回单
```
POST http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service?porttype=ZNDW_TASK_FILL_ORDER_INSERT
```

## 配置说明

### Session新增字段
```java
public volatile String cSign = "E9163ADC4E8E9B20293C8FC11A78E652";  // API签名
public volatile String[] accountConfig = new String[0];              // 账号配置
public volatile String zhilianConfig = "";                           // 智联配置
```

### 智联配置格式
```
enableAccept|enableRevert
```
示例：`true\u0001true` 表示开启自动接单和自动回单

## 界面说明

### Tab布局
1. **工单监控** - 原有故障工单监控
2. **停电监控** - 原有停电告警监控
3. **智联工单** - 新增智联工单监控

### 智联工单界面
- 顶部状态栏：显示监控状态和工单数量
- 控制面板：自动接单/自动回单开关、启动/停止按钮
- Tab切换：未领取/已领取工单列表
- 列表区域：显示工单详情
- 日志区域：实时显示操作日志

## 线程安全

### 锁机制
1. **NET_LOCK** - 全局网络锁，防止并发发包
2. **OP_SEQUENCE_LOCK** - 操作时序锁，保证操作间隔
3. **分段锁** - 按工单ID哈希分段，提高并发性能

### 延迟策略
- 轮询间隔：60-120秒随机
- 接单延迟：2.5-6秒随机
- 回单延迟：5-12秒随机
- 操作间隔：8-15秒随机

## 使用说明

### 启动监控
1. 打开"智联工单"Tab
2. 勾选"自动接单"和/或"自动回单"
3. 点击"启动监控"按钮

### 停止监控
- 点击"停止"按钮即可停止监控

### 手动操作
- 在未领取列表点击"手动接单"
- 在已领取列表点击"手动回单"

## 注意事项

1. **签名配置**：cSign需要从登录响应或配置中获取
2. **坐标固定**：使用固定坐标（120.540310, 27.601740）
3. **版本号**：API版本号固定为1.0.93
4. **线程安全**：所有网络操作都有锁保护

## 与易语言代码对应关系

| 易语言 | Java |
|--------|------|
| 子程序_智联工单监测 | ZhilianMonitorTask.doMonitorRound() |
| 智联_已领取回单 | ZhilianMonitorTask.doMonitorRound()中的回单逻辑 |
| 智联接单 | ZhilianApi.acceptTask() |
| 智联回单 | ZhilianApi.revertTask() |
| 集_网络发包许可证 | NET_LOCK |
| 程序_延时 | sleepRandom() |
| 时间_取现行时间戳 | TimeUtil.getCurrentTimestamp() |

## 后续优化建议

1. 添加cSign动态获取机制
2. 支持配置坐标位置
3. 添加更多回单条件判断
4. 支持批量操作
5. 添加操作统计功能

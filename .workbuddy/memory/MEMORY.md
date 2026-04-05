# MEMORY.md - 长期记忆

## 项目：TowerOpsApp10

### 关键发现：站址运维ID ≠ 站址编码（2026-04-03）
- OMMS告警页面 col[11] = **站址运维ID**（12位，如 `33032600000717`）
- OMMS告警页面 **col[44] = 站址编码**（18位，如 `330326500010001927`）—— 列序号第45列（用户确认）
- 数运蓝牙API station_code = **站址编码**（18位）
- **col[11]和col[44]完全不同！**
- col[44]的18位站址编码可直接与数运API的station_code匹配
- 门禁蓝牙匹配现在可以用站址编码精确匹配（col[44] ↔ station_code），不再只能依赖站名

### 远程开门时间接口修正（2026-04-05 晚）
- **listEntrance.xhtml 是门禁设备列表页，不是远程开门记录页！** 用它查远程开门时间始终0条
- **正确接口：方案C** POST `listFsu.xhtml`，传 `j_id670=j_id670&fsuEntranceId={FSU ID}&j_id670:j_id716=j_id670:j_id716`
- 这是门禁系统Tab（AccessControlFragment）之前成功使用的方案，展开FSU行详情后从HTML中提取时间
- `getAllRemoteOpenTimesByFsuid()` 已从 listEntrance.xhtml 改为方案C
- `getRemoteOpenTime()` 的14位438路由也已移除（不再强制走错误的 listEntrance 流程）
- FSU ID（14位438特征）可直接传给方案C的 `fsuEntranceId` 参数

### 门禁数据匹配规则（2026-04-05 晚）
- **按站分组，每站只取1条展示**
- **合格判定**：蓝牙 OR 远程 |时间差| ≤ 30分钟
- **不合格**：蓝牙 AND 远程都 > 30分钟或无记录
- **合格原因** = 时间差更小的那个（蓝牙 or 远程）
- **取哪条**：有合格→取离当前最近的合格告警；全不合格→取离当前最近的告警
- **时差列**：显示蓝牙/远程中离告警时间更近的那个的差值
- **蓝牙/远程时间颜色**：合格来源用绿色(蓝牙)/橙色(远程)标注


- 用户需求：4A登录后不需要手动点"OMMS登录"按钮、不需要看到WebView页面
- 实现：4A登录成功后自动调用 `TowerLoginApi.autoGetOmmsCookie()` 静默获取OMMS Cookie
- `startMonitor()` 时如果 `ommsCookie` 为空但有 `tower4aSessionCookie`，也会自动尝试获取
- 新增 `autoFetchOmmsCookie(loginName)` 方法：后台获取OMMS Cookie + 自动刷新ViewState
- 短信验证码仍需用户手动输入（4A系统强制要求）

### UI升级 v6.0 — Material Design 3（2026-04-04）
- 配色：完整M3 tonal palette（Primary/Secondary/Tertiary/Surface容器层级）
- 组件：MaterialButton(ripple+24dp圆角)、MaterialSwitch、MaterialCardView(20dp圆角+stroke)
- 图标：14个M3风格矢量图标（账号/蓝牙/盾牌/播放/停止等）
- 布局：8dp网格、16dp padding、10dp gap、0.02 letterSpacing
- Chip：日期快捷筛选用M3 Filter Chip替代TextView
- Java适配：Button→MaterialButton, CheckBox→MaterialSwitch, TextView→Chip

### TowerOpsApp10 子Tab调整（2026-04-05）
- **设备离线**：从独立顶层Tab改为"数运工单"Tab下的子Tab（在"任务工单"后面，position 4）
- **省内工单处理及时率**：从独立顶层Tab改为"指标查询"Tab下的子Tab（在"超频告警整治"后面，position 8）
- 顶层Tab数量：11 → 9（移除2个独立Tab）
- 修改文件：
  - ShuyunFragment.java: TAB_TITLES 数组新增"设备离线"
  - ShuyunSubPagerAdapter.java: TAB_COUNT 4→5, case 4→DeviceOfflineFragment
  - MetricsFragment.java: TAB_NAMES 数组新增"省内工单处理及时率", METRIC_COUNT 7→8
  - ProvinceOrderRateApi.java: 新增 queryProvinceOrderRate(pcToken, cookieToken, date) 方法
  - MainPagerAdapter.java: 移除独立Tab，TAB_COUNT 11→9
  - MainActivity.java: Tab标题和 offscreenPageLimit 更新

### 排序问题修复（2026-04-05）
1. **WorkOrderFragment**: 修复 updateSortButtonStyles 方法，ALERT_TIME_DESC 和 ALERT_TIME_ASC 分开处理，正确显示排序方向箭头
2. **WorkOrderAdapter**: 添加排序调试日志（Log.d）
3. **DeviceOfflineFragment**: 添加排序调试日志（Log.d）
4. **布局修复**: 三个页面的区县Spinner扩大宽度（90dp）和下拉宽度（180dp），添加 spinnerMode="dropdown"

### 用户偏好
- 喜欢直接做事，少废话
- 注重效率和产出
- Android Java项目，使用Java 8语法（不能用<>推断匿名内部类）

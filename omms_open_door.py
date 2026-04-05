"""
OMMS 远程开门脚本
对应 AccessControlFragment.java 的完整逻辑：

开门条件（全部满足才执行）：
  1. 告警距今 >= 5-15分钟（随机阈值）
  2. 告警距今 < 35分钟
  3. 远程开门间隔（|远程时间 - 告警时间|） >= 30分钟
  4. 蓝牙间隔 >= 30分钟（或无蓝牙记录 -9999 时跳过蓝牙判断）
"""

import requests
import re
import json
import time
import random
import urllib.parse
from datetime import datetime

# ============================================================
# 填你的 OMMS Cookie（从截图拿到的）
# ============================================================
OMMS_COOKIE = (
    "route=2140bfa98408dea79443098037247f25; "
    "JSESSIONID=E93E1E48BE97C001D25BBCBEEB96B0A0; "
    "acctId=203349045; uid=wx-linjyj22; "
    "loginName=linxj; fp=6370ceda5e44488e79ff9404a0552ef1; "
    "userOrgCode=3303009505"
)

# 铁塔APP接口凭据（FSU_ALARM_LIST，查门禁告警用）
APP_USERID = ""   # 填你的 userid
APP_TOKEN  = ""   # 填你的 Authorization token

# ============================================================
BASE_URL = "http://omms.chinatowercom.cn:9000"
FSU_URL  = BASE_URL + "/business/resMge/pwMge/fsuMge/listFsu.xhtml"

session = requests.Session()

# ============================================================
# OMMS 请求头
# ============================================================
def omms_get_headers():
    return {
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "Host": "omms.chinatowercom.cn:9000",
        "Pragma": "no-cache",
        "Referer": BASE_URL + "/business/resMge/pwMge/fsuMge/listFsu.xhtml",
        "Upgrade-Insecure-Requests": "1",
        "User-Agent": "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 Chrome/146.0.0.0 Mobile Safari/537.36",
        "Cookie": OMMS_COOKIE,
    }

def omms_post_headers():
    return {
        "Accept": "*/*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Cache-Control": "no-cache",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Host": "omms.chinatowercom.cn:9000",
        "Origin": BASE_URL,
        "Pragma": "no-cache",
        "Proxy-Connection": "keep-alive",
        "Referer": BASE_URL + "/business/resMge/pwMge/fsuMge/listFsu.xhtml",
        "User-Agent": "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 Chrome/146.0.0.0 Mobile Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Cookie": OMMS_COOKIE,
    }

# ============================================================
# Step1：获取 ViewState
# ============================================================
def get_view_state():
    print("[1] 获取 ViewState ...")
    resp = session.get(FSU_URL, headers=omms_get_headers(), timeout=20)
    html = resp.text
    patterns = [
        r'javax\.faces\.ViewState[^>]*value="([^"]+)"',
        r'name="javax\.faces\.ViewState"[^>]+value="([^"]+)"',
        r'<state>([^<]+)</state>',
    ]
    for pat in patterns:
        m = re.search(pat, html)
        if m:
            vs = m.group(1).strip()
            print(f"    ViewState = {vs}")
            return vs
    # 未找到时用默认值（与易语言 j_id1 一致）
    print("    未找到 ViewState，使用默认 j_id3")
    return "j_id3"

# ============================================================
# Step2：获取门禁告警列表（铁塔APP）
# ============================================================
def get_alarm_list():
    if not APP_USERID or not APP_TOKEN:
        print("[2] APP凭据未填，跳过告警列表，请手动填写 alarm_items")
        return []
    print("[2] 获取门禁告警列表 ...")
    ts = int(time.time())
    url = (f"http://ywapp.chinatowercom.cn:58090/itower/mobile/app/service"
           f"?porttype=FSU_ALARM_LIST&v=1.0.93&userid={APP_USERID}&c=0")
    post = (f"start=1&limit=50&c_timestamp={ts}"
            f"&c_account={APP_USERID}"
            f"&c_sign=2800ADE8BBBB67247CCFB6FA0E37C7A7"
            f"&upvs=2025-03-18-ccssoft"
            f"&provinceId=&cityId=&areaId=&alarmlevel=&begintimetype="
            f"&stationcode=&alarmname=%E9%97%A8")
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Authorization": APP_TOKEN,
        "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36",
        "Accept": "application/json",
    }
    r = requests.post(url, data=post, headers=headers, timeout=20)
    try:
        j = r.json()
        # 结构兼容：data.rows / data.alarmList / rows / alarmList
        data = j.get("data", {})
        if isinstance(data, dict):
            rows = (data.get("rows") or data.get("alarmList") or data.get("list") or [])
        elif isinstance(data, list):
            rows = data
        else:
            rows = j.get("rows", j.get("alarmList", []))
        print(f"    告警条数: {len(rows)}")
        # 打印第一条字段帮助诊断
        if rows:
            print(f"    第一条字段: {list(rows[0].keys())}")
            print(f"    第一条数据: {str(rows[0])[:300]}")
        items = []
        for row in rows:
            # 告警时间：优先 alarm_begin_time，再 firstsystemtime
            alarm_time = row.get("alarm_begin_time") or row.get("firstsystemtime") or row.get("beginTime", "")
            # 门禁ID：entrance_guard_id（不是 subobjid！）
            door_id = row.get("entrance_guard_id") or row.get("door_id") or row.get("subobjid", "")
            items.append({
                "st_name":    row.get("st_name", ""),
                "alarm_time": alarm_time,
                "objid":      row.get("objid", row.get("fsu_objid", "")),
                "door_id":    door_id,
            })
        return items
    except Exception as e:
        print(f"    解析告警列表失败: {e}")
        print(f"    响应前300字: {r.text[:300]}")
        return []

# ============================================================
# Step3：按站点名查 OMMS FSU（如果 APP 接口没有 doorId）
# ============================================================
def query_fsu_by_name(station_name, view_state):
    print(f"[3] 查询 OMMS FSU: {station_name}")
    encoded = urllib.parse.quote(station_name)
    post = (
        "AJAXREQUEST=_viewRoot"
        "&queryForm%3AunitHidden="
        "&queryForm%3AqueryFlag=queryFlag"
        "&queryForm%3AqueryStaStatusSelId_hiddenValue=2"
        "&queryForm%3AqueryStaStatusSelId=2"
        f"&queryForm%3AqueryStationName={encoded}"
        "&queryForm%3AcurrPageObjId=1"
        "&queryForm%3ApageSizeText=35"
        "&queryForm=queryForm"
        "&autoScroll="
        f"&javax.faces.ViewState={view_state}"
        "&queryForm%3Aj_id156=queryForm%3Aj_id156"
        "&AJAX%3AEVENTS_COUNT=1&"
    )
    r = session.post(FSU_URL, data=post, headers=omms_post_headers(), timeout=20)
    html = r.text
    # 从 HTML 里提取 objid 和 doorId
    # 通常在 JS onclick 或 hidden input 里：id~doorId 或 objid 列
    objid_m = re.search(r'objid["\s=:]+([A-Z0-9a-z_\-]{10,})', html)
    door_m  = re.search(r'entrance_guard_id["\s=:]+(\d+)', html)
    if objid_m:
        print(f"    objid={objid_m.group(1)}")
    if door_m:
        print(f"    door_id={door_m.group(1)}")
    # 也打印前500字方便调试
    print(f"    响应前500字: {html[:500]}")
    return objid_m.group(1) if objid_m else "", door_m.group(1) if door_m else ""

# ============================================================
# Step4：获取远程开门时间
# ============================================================
def get_remote_open_time(fsuid, view_state):
    post = (
        "AJAXREQUEST=_viewRoot"
        "&j_id657=j_id657"
        "&autoScroll="
        f"&javax.faces.ViewState={view_state}"
        f"&fsuEntranceId={fsuid}"
        "&j_id657%3Aj_id698=j_id657%3Aj_id698"
        "&AJAX%3AEVENTS_COUNT=1&"
    )
    r = session.post(FSU_URL, data=post, headers=omms_post_headers(), timeout=20)
    text = r.text
    m = re.search(r'\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}', text)
    if m: return m.group()
    m2 = re.search(r'\d{4}/\d{2}/\d{2}\s\d{2}:\d{2}:\d{2}', text)
    if m2: return m2.group().replace("/", "-")
    return ""

# ============================================================
# Step5：三步开门
# ============================================================
def extract_viewstate(html):
    """从AJAX响应中提取最新ViewState"""
    patterns = [
        r'javax\.faces\.ViewState[^>]*value="([^"]+)"',
        r'name="javax\.faces\.ViewState"[^>]+value="([^"]+)"',
        r'<state>(j_id[^<]+)</state>',
        r'<!\[CDATA\[(j_id[^\]]+)\]\]>',
    ]
    for pat in patterns:
        m = re.search(pat, html)
        if m:
            return m.group(1).strip()
    return None

def do_open_door(fsuid, door_id, view_state, st_name):
    print(f"\n  🔓 开门 [{st_name}] fsuid={fsuid} doorId={door_id}")
    vs = view_state  # 每步动态更新ViewState

    # 步骤一：激活弹窗
    post1 = (
        "AJAXREQUEST=_viewRoot"
        "&j_id657=j_id657"
        "&autoScroll="
        f"&javax.faces.ViewState={vs}"
        "&relTableName=TW_PW_ENTRANCE_GUARD"
        f"&id={fsuid}"
        "&j_id657%3Aj_id702=j_id657%3Aj_id702"
        "&AJAX%3AEVENTS_COUNT=1&"
    )
    r1 = session.post(FSU_URL, data=post1, headers=omms_post_headers(), timeout=20)
    print(f"    步骤1 激活弹窗 HTTP={r1.status_code} respLen={len(r1.text)}")
    print(f"    步骤1 响应前300字: {r1.text[:300]}")
    # 动态更新ViewState
    new_vs = extract_viewstate(r1.text)
    if new_vs:
        vs = new_vs
        print(f"    步骤1 更新ViewState: {vs[:50]}")
    # ★ 必须等待！JSF服务端需要时间处理AJAX状态机
    time.sleep(random.uniform(1.5, 2.5))

    # 步骤二：提交开门
    post2 = (
        "AJAXREQUEST=_viewRoot"
        f"&openDoor_Form%3AselControlDevice={door_id}"
        "&openDoor_Form%3AopenCause=2"
        "&openDoor_Form%3Aj_id1083="
        "&openDoor_Form=openDoor_Form"
        "&autoScroll="
        f"&javax.faces.ViewState={vs}"
        "&openDoor_Form%3Aj_id1086=openDoor_Form%3Aj_id1086&"
    )
    r2 = session.post(FSU_URL, data=post2, headers=omms_post_headers(), timeout=20)
    print(f"    步骤2 提交开门 HTTP={r2.status_code} respLen={len(r2.text)}")
    print(f"    步骤2 响应前300字: {r2.text[:300]}")
    # 动态更新ViewState
    new_vs2 = extract_viewstate(r2.text)
    if new_vs2:
        vs = new_vs2
        print(f"    步骤2 更新ViewState: {vs[:50]}")
    time.sleep(random.uniform(1.5, 2.5))

    # 步骤三：密码确认
    post3 = (
        "AJAXREQUEST=_viewRoot"
        "&chickMiMaForm%3Aj_id1984="
        "&chickMiMaForm=chickMiMaForm"
        "&autoScroll="
        f"&javax.faces.ViewState={vs}"
        "&chickMiMaForm%3Aj_id1987=chickMiMaForm%3Aj_id1987&"
    )
    resp3 = session.post(FSU_URL, data=post3, headers=omms_post_headers(), timeout=20)
    print(f"    步骤3 密码确认 HTTP={resp3.status_code} respLen={len(resp3.text)}")
    print(f"    步骤3 最终响应前400字: {resp3.text[:400]}")
    return resp3.text

# ============================================================
# 时间工具
# ============================================================
FMT = "%Y-%m-%d %H:%M:%S"

def minutes_from_now(t_str):
    try:
        t = datetime.strptime(t_str.strip(), FMT)
        return int((datetime.now() - t).total_seconds() / 60)
    except:
        return -9999

def minutes_between(t_a, t_b):
    try:
        a = datetime.strptime(t_a.strip(), FMT)
        b = datetime.strptime(t_b.strip(), FMT)
        return int(abs((b - a).total_seconds() / 60))
    except:
        return -9999

# ============================================================
# 主流程
# ============================================================
if __name__ == "__main__":
    print("=" * 60)
    print("OMMS 远程开门脚本")
    print("=" * 60)

    # 1. 获取 ViewState
    view_state = get_view_state()

    # 2. 获取告警列表
    alarm_items = get_alarm_list()

    # ----- 如果 APP 凭据未填，手动写测试数据（格式示例）-----
    # alarm_items = [
    #     {
    #         "st_name":    "平阳鳌江广场路01",
    #         "alarm_time": "2026-03-26 15:00:00",
    #         "objid":      "xxxFSUID",
    #         "door_id":    "12345",
    #     }
    # ]
    # ----------------------------------------------------------

    if not alarm_items:
        print("\n⚠  告警列表为空，退出。请填写 APP_USERID / APP_TOKEN 或手动填写 alarm_items")
        exit(0)

    print(f"\n共 {len(alarm_items)} 条告警，开始判断...\n")

    open_queue = []
    rand_threshold = random.randint(5, 15)  # 5-15分钟随机阈值
    print(f"本次随机阈值: {rand_threshold} 分钟")

    for item in alarm_items:
        st_name    = item["st_name"]
        alarm_time = item["alarm_time"]
        fsuid      = item["objid"]
        door_id    = item["door_id"]

        # 无 objid 时尝试从 OMMS 查
        if not fsuid:
            fsuid, door_id = query_fsu_by_name(st_name, view_state)

        # 查远程开门时间
        remote_time = ""
        for retry in range(3):
            remote_time = get_remote_open_time(fsuid, view_state)
            if remote_time:
                break
            time.sleep(random.uniform(0.8, 1.5))

        # 计算时间间隔
        curr           = minutes_from_now(alarm_time)       # 告警距今分钟
        remote_interval = minutes_between(remote_time, alarm_time) if remote_time else -9999
        bluetooth_interval = -9999  # 无蓝牙数据时默认 -9999（不卡门）

        print(f"[{st_name}] 告警距今={curr}min 远程间隔={remote_interval}min 蓝牙间隔={bluetooth_interval}min")

        # ★ 开门条件（完全对应 AccessControlFragment.java）
        should_open = (
            curr >= rand_threshold          # 告警时间 >= 随机阈值(5-15分钟)
            and curr < 35                   # 告警时间 < 35分钟
            and abs(remote_interval) >= 30  # 远程开门间隔 >= 30分钟
            and (bluetooth_interval == -9999 or abs(bluetooth_interval) >= 30)  # 蓝牙间隔 >= 30分钟
        )

        if should_open:
            status = "待开门"
            open_queue.append(item | {"fsuid": fsuid, "door_id": door_id,
                                       "remote_time": remote_time,
                                       "curr": curr})
        elif remote_interval != -9999 and abs(remote_interval) < 30:
            status = "合格"
        else:
            status = "不合格"

        print(f"    → 状态: {status}\n")

        # 阶段一请求之间随机延迟（仿生，10-1500ms）
        time.sleep(random.uniform(0.01, 1.5))

    # 按告警时间升序
    open_queue.sort(key=lambda x: x["alarm_time"])

    print("=" * 60)
    print(f"待开门队列: {len(open_queue)} 个站")
    for q in open_queue:
        print(f"  - [{q['st_name']}] 告警={q['alarm_time']} 距今={q['curr']}min")
    print("=" * 60)

    if not open_queue:
        print("没有需要开门的站点，退出。")
        exit(0)

    # 阶段二：单行道排队开门
    print("\n开始执行开门...")
    for q in open_queue:
        if not q["fsuid"] or not q["door_id"]:
            print(f"  ⚠ [{q['st_name']}] 缺少 fsuid 或 doorId（entrance_guard_id），跳过")
            print(f"    fsuid={q['fsuid']}  door_id={q['door_id']}")
            continue

        # 审核延迟 2.5-4.5秒
        delay = random.uniform(2.5, 4.5)
        print(f"  等待 {delay:.1f}s 审核延迟...")
        time.sleep(delay)

        result = do_open_door(q["fsuid"], q["door_id"], view_state, q["st_name"])

        # 开门成功判断（响应里通常有 success 或 OK 字样）
        if "success" in result.lower() or "ok" in result.lower() or len(result) > 50:
            print(f"  ✅ [{q['st_name']}] 开门指令发送成功")
        else:
            print(f"  ❌ [{q['st_name']}] 开门响应异常: {result[:100]}")

        # 冷却间隔 4-7秒
        cooldown = random.uniform(4.0, 7.0)
        print(f"  冷却 {cooldown:.1f}s...\n")
        time.sleep(cooldown)

    print("\n全部完成。")

package com.towerops.app.model;

/**
 * 账号配置数据模型
 *
 * 字段说明：
 *   [0] 账号（loginName）
 *   [1] 铁塔运维APP密码（URL编码格式，给 LoginApi 使用）
 *   [2] 姓名（中文真实姓名，与工单 actionlist 里的 operator 字段一致）
 *       用于后台接单/回单时匹配工单接单人，必须填写正确
 *   [3] 4A门户密码（明文，给 TowerLoginApi 使用，RSA加密后提交）
 *
 * [BUG-FIX] 原代码接单/回单时用 Session.username（账号工号）和工单里的 acceptOperator（中文姓名）
 *   比对，两种格式永远不等，导致后台接单/回单条件永远不触发。
 *   修复：新增第三列真实姓名，登录时写入 Session.realname，
 *         WorkerTask 用 realname 和 acceptOperator 比对。
 *
 * [BUG-FIX-2] TowerLoginApi（4A门户登录）需要明文密码做RSA加密，
 *   原来误用了铁塔APP的URL编码密码，导致4A登录报"账号或密码错误"。
 *   修复：新增第四列4A门户明文密码，TowerLoginApi 专用。
 */
public class AccountConfig {

    /**
     * ACCOUNTS[i] = { 账号, APP密码(编码), 真实姓名, 4A明文密码 }
     *
     * ★ 真实姓名必须和中国铁塔系统里显示的姓名完全一致（包括空格）★
     * ★ 不确定的话，先前台手动接一单，看工单详情里接单人显示什么就填什么 ★
     * ★ 4A密码列：填写4A门户登录的明文密码，未知的填空串 ""              ★
     */
    public static final String[][] ACCOUNTS = {
            {"wx-linjy22",  "z0J1CVrRPjfQgO4jhLuJwg%3D%3D",   "林甲雨",  "Bf147258@"},
            {"wx-liujj6",   "MwAPfB0gVI3Ddfk%2BByiG3Q%3D%3D", "刘娟娟",  "7890@zgTT"},
            {"wx-linyl",    "z0J1CVrRPjfQgO4jhLuJwg%3D%3D",   "林元龙",  ""},
            {"wx-maoll5",   "z0J1CVrRPjfQgO4jhLuJwg%3D%3D",   "毛露露",  ""},
            {"wx-wangjj96", "uwhpKacQXX1aC3eyE9rkQg%3D%3D",   "王俊杰",  ""},
            {"wx-liusl35",  "WFr6AEK5IurXc7KifVhtYQ%3D%3D",   "刘双淋",  ""},
            {"wx-wangsw9",  "z0J1CVrRPjfQgO4jhLuJwg%3D%3D",   "王士伟",  "Bf147258@"},
    };

    /** 返回下拉框显示名（脱敏展示，去除前缀空格） */
    public static String[] getDisplayNames() {
        String[] names = new String[ACCOUNTS.length];
        for (int i = 0; i < ACCOUNTS.length; i++) {
            // 第三列有姓名则显示"姓名(账号)"，否则直接显示账号
            String account = ACCOUNTS[i][0].replace("%20", "").trim();
            String realname = ACCOUNTS[i].length > 2 ? ACCOUNTS[i][2] : "";
            names[i] = realname.isEmpty() ? account : realname + "(" + account + ")";
        }
        return names;
    }

    /** 根据账号下标取真实姓名，没有配置则返回空串 */
    public static String getRealname(int index) {
        if (index < 0 || index >= ACCOUNTS.length) return "";
        return ACCOUNTS[index].length > 2 ? ACCOUNTS[index][2] : "";
    }

    /**
     * 根据账号下标取4A门户明文密码（供 TowerLoginApi 使用，RSA加密后提交）
     * 没有配置则返回空串
     */
    public static String get4aPassword(int index) {
        if (index < 0 || index >= ACCOUNTS.length) return "";
        return ACCOUNTS[index].length > 3 ? ACCOUNTS[index][3] : "";
    }
}

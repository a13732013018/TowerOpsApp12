package com.towerops.app.shuyun;

/**
 * 数运系统账号配置
 * 支持APP端和PC端账号
 */
public class ShuyunAccountConfig {

    /**
     * 单个账号信息
     */
    public static class Account {
        public String username;  // 用户名（手机号）
        public String password;  // 加密后的密码
        public String imei;      // 设备识别码（APP端用）
        public String nickname;  // 昵称（用于显示）

        public Account(String username, String password, String imei, String nickname) {
            this.username = username;
            this.password = password;
            this.imei = imei;
            this.nickname = nickname;
        }

        // PC端账号构造函数（不需要IMEI）
        public Account(String username, String password, String nickname) {
            this.username = username;
            this.password = password;
            this.imei = "";
            this.nickname = nickname;
        }
    }

    /**
     * APP端账号数组
     */
    public static final Account[] APP_ACCOUNTS = {
        // 账号1 - 13732013018
        new Account(
            "13732013018",
            "F8TVasxWplZNK7AJq4T1cA==",
            "ba9f03beaacd4c05",
            "账号1 (13732013018)"
        ),
        // 账号2 - 15858734252
        new Account(
            "15858734252",
            "0/YBW5U6t/yAZggx3MfHCQ==",
            "a873a215e542edab",
            "账号2 (15858734252)"
        )
    };

    /**
     * PC端账号数组
     */
    public static final Account[] PC_ACCOUNTS = {
        // PC账号1 - 13566295657
        new Account(
            "13566295657",
            "MLcQylxc4733Iav/cNx+oQ==",
            "PC账号1 (13566295657)"
        )
    };

    /**
     * 获取APP端账号显示名称列表
     */
    public static String[] getAPPDisplayNames() {
        String[] names = new String[APP_ACCOUNTS.length];
        for (int i = 0; i < APP_ACCOUNTS.length; i++) {
            names[i] = APP_ACCOUNTS[i].nickname;
        }
        return names;
    }

    /**
     * 获取PC端账号显示名称列表
     */
    public static String[] getPCDisplayNames() {
        String[] names = new String[PC_ACCOUNTS.length];
        for (int i = 0; i < PC_ACCOUNTS.length; i++) {
            names[i] = PC_ACCOUNTS[i].nickname;
        }
        return names;
    }

    /**
     * 根据索引获取APP端账号
     */
    public static Account getAPPAccount(int index) {
        if (index >= 0 && index < APP_ACCOUNTS.length) {
            return APP_ACCOUNTS[index];
        }
        return null;
    }

    /**
     * 根据索引获取PC端账号
     */
    public static Account getPCAccount(int index) {
        if (index >= 0 && index < PC_ACCOUNTS.length) {
            return PC_ACCOUNTS[index];
        }
        return null;
    }
}

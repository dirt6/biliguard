package com.biliguard.app;

/**
 * 屏幕文字与白名单标题的匹配。
 * 只保留字母数字（含中文），去掉标点、空格、emoji，避免 B 站标题排版差异导致漏匹配。
 */
public class Match {

    /** 取标题前多少个“有效字符”作为指纹。太短容易误命中，太长容易因截断漏命中。 */
    private static final int KEY_LEN = 12;

    public static String norm(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 取标题的匹配指纹（前 12 个有效字符），太短的标题返回 null。
     */
    public static String key(String normTitle) {
        if (normTitle == null || normTitle.length() < 4) return null;
        int n = Math.min(normTitle.length(), KEY_LEN);
        return normTitle.substring(0, n);
    }

    /**
     * @param normTitle   已归一化的白名单标题
     * @param normScreen  已归一化的整屏文字
     */
    public static boolean hit(String normTitle, String normScreen) {
        if (normTitle == null || normScreen == null) return false;
        if (normTitle.length() < 4) return false; // 太短的标题不可信
        if (normScreen.length() == 0) return false;
        int n = Math.min(normTitle.length(), KEY_LEN);
        String key = normTitle.substring(0, n);
        if (normScreen.contains(key)) return true;
        // 标题被 UI 截断时，屏幕文字只是一部分：退一步用更短的指纹再试
        if (key.length() > 8 && normScreen.contains(key.substring(0, 8))) return true;
        return false;
    }
}

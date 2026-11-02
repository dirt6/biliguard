package com.biliguard.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class Prefs {

    public static final int PASS_PER_DAY = 2;
    public static final long PASS_DURATION_MS = 10 * 60 * 1000L;

    /** 默认受管：B站（国内/国际/HD）、抖音、快手、小红书、小黑盒。 */
    public static final String DEFAULT_APPS =
            "tv.danmaku.bili,com.bilibili.app.in,tv.danmaku.bilibilihd,"
                    + "com.ss.android.ugc.aweme,com.smile.gifmaker,com.xingin.xhs,com.max.xiaoheihe";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("biliguard", Context.MODE_PRIVATE);
    }

    public boolean enabled() {
        return sp.getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        sp.edit().putBoolean("enabled", v).apply();
    }

    public String windowsRaw() {
        return sp.getString("windows", "11:30-13:00;17:30-19:30");
    }

    public void setWindowsRaw(String v) {
        sp.edit().putString("windows", v).apply();
    }

    public String keywords() {
        return sp.getString("keywords", "");
    }

    public void setKeywords(String v) {
        sp.edit().putString("keywords", v).apply();
    }

    public String maskUri() {
        return sp.getString("mask_uri", "");
    }

    public void setMaskUri(String v) {
        sp.edit().putString("mask_uri", v).apply();
    }

    public String uiBgUri() {
        return sp.getString("ui_bg_uri", "");
    }

    public void setUiBgUri(String v) {
        sp.edit().putString("ui_bg_uri", v).apply();
    }

    public void setMaskUris(String v) {
        sp.edit().putString("mask_uris", v == null ? "" : v).apply();
    }

    /** 遮罩图可以有多张，用换行分隔；兼容只有一张的旧版本。 */
    public java.util.List<String> maskUrisList() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String raw = sp.getString("mask_uris", "");
        if (raw != null && raw.length() > 0) {
            for (String s : raw.split("\n")) {
                s = s.trim();
                if (s.length() > 0) out.add(s);
            }
        }
        if (out.isEmpty()) {
            String one = maskUri();
            if (one != null && one.length() > 0) out.add(one);
        }
        return out;
    }

    public String joinUris(java.util.List<String> uris) {
        StringBuilder sb = new StringBuilder();
        for (String u : uris) {
            if (u == null || u.length() == 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(u);
        }
        return sb.toString();
    }

    public String whitelistJson() {
        return sp.getString("whitelist", "[]");
    }

    public void setWhitelistJson(String v) {
        sp.edit().putString("whitelist", v).apply();
    }

    public long tempPassUntil() {
        return sp.getLong("temp_pass_until", 0L);
    }

    public void setTempPassUntil(long v) {
        sp.edit().putLong("temp_pass_until", v).apply();
    }

    public boolean allowUnknown() {
        return sp.getBoolean("allow_unknown", true);
    }

    public void setAllowUnknown(boolean v) {
        sp.edit().putBoolean("allow_unknown", v).apply();
    }

    /** 从 App 里点开学习视频后的宽限期：这段时间内不判定，等播放页把标题渲染出来。 */
    public long graceUntil() {
        return sp.getLong("grace_until", 0L);
    }

    public void setGraceUntil(long v) {
        sp.edit().putLong("grace_until", v).apply();
    }

    /** 诊断用：记录服务最近一次判断时的真实状态。 */
    public void setDebug(String pkg, String act, boolean showing, long ts, String reason) {
        sp.edit()
                .putString("dbg_pkg", pkg == null ? "" : pkg)
                .putString("dbg_act", act == null ? "" : act)
                .putBoolean("dbg_show", showing)
                .putLong("dbg_ts", ts)
                .putString("dbg_reason", reason == null ? "" : reason)
                .apply();
    }

    public Set<String> managedApps() {
        Set<String> out = new HashSet<String>();
        String raw = sp.getString("managed_apps", DEFAULT_APPS);
        for (String s : raw.split("[,;\\s]+")) {
            s = s.trim();
            if (s.length() > 0) out.add(s);
        }
        return out;
    }

    public void setManagedApps(Set<String> apps) {
        StringBuilder sb = new StringBuilder();
        for (String a : apps) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a);
        }
        sp.edit().putString("managed_apps", sb.toString()).apply();
    }

    private String todayKey() {
        Calendar c = Calendar.getInstance();
        return String.format("%04d%02d%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public int passLeft() {
        String d = sp.getString("pass_date", "");
        int n = sp.getInt("pass_count", 0);
        if (!todayKey().equals(d)) return PASS_PER_DAY;
        return Math.max(0, PASS_PER_DAY - n);
    }

    public boolean consumePass() {
        String today = todayKey();
        String d = sp.getString("pass_date", "");
        int n = sp.getInt("pass_count", 0);
        if (!today.equals(d)) {
            n = 0;
            d = today;
        }
        if (n >= PASS_PER_DAY) return false;
        sp.edit().putString("pass_date", d).putInt("pass_count", n + 1).apply();
        return true;
    }
}

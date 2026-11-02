package com.biliguard.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Model {

    public static class Video {
        public String bvid;
        public String title;

        public Video(String b, String t) {
            bvid = b == null ? "" : b;
            title = t == null ? "" : t;
        }
    }

    public static class Range {
        public int start; // minute of day
        public int end;

        public Range(int s, int e) {
            start = s;
            end = e;
        }

        public boolean crossDay() {
            return end <= start;
        }
    }

    public static List<Video> loadVideos(Prefs p) {
        List<Video> out = new ArrayList<Video>();
        try {
            JSONArray a = new JSONArray(p.whitelistJson());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Video(o.optString("bvid"), o.optString("title")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void saveVideos(Prefs p, List<Video> list) {
        JSONArray a = new JSONArray();
        try {
            for (Video v : list) {
                JSONObject o = new JSONObject();
                o.put("bvid", v.bvid);
                o.put("title", v.title);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        p.setWhitelistJson(a.toString());
    }

    public static List<Range> parseRanges(String raw) {
        List<Range> out = new ArrayList<Range>();
        if (raw == null) return out;
        String cleaned = raw.replace('；', ';').replace('，', ';').replace('、', ';');
        String[] parts = cleaned.split("[,;\\n]+");
        for (String part : parts) {
            part = part.trim();
            if (part.length() == 0) continue;
            String[] se = part.split("[-~—–至到]+");
            if (se.length != 2) continue;
            int s = parseHM(se[0].trim());
            int e = parseHM(se[1].trim());
            if (s < 0 || e < 0) continue;
            if (s == e) continue; // 零长度
            out.add(new Range(s, e));
        }
        return out;
    }

    static int parseHM(String s) {
        String[] p = s.split("[:：]");
        if (p.length != 2) return -1;
        try {
            int h = Integer.parseInt(p[0].trim());
            int m = Integer.parseInt(p[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String hm(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    public static String fmtRange(Range r) {
        return hm(r.start) + "–" + hm(r.end);
    }

    public static int nowMinute() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    public static String humanLeft(int minutes) {
        if (minutes < 0) minutes = 0;
        if (minutes < 60) return minutes + " 分钟";
        int h = minutes / 60;
        int m = minutes % 60;
        if (m == 0) return h + " 小时";
        return h + " 小时 " + m + " 分钟";
    }

    public static boolean isFreeTime(String raw, int minute) {
        for (Range r : parseRanges(raw)) {
            if (r.crossDay()) {
                if (minute >= r.start || minute < r.end) return true;
            } else {
                if (minute >= r.start && minute < r.end) return true;
            }
        }
        return false;
    }

    public static String nextFreeText(String raw, int minute) {
        List<Range> ranges = parseRanges(raw);
        if (ranges.isEmpty()) {
            return "还没有设置任何自由时段，所以整天都在学习模式里";
        }
        int bestWait = Integer.MAX_VALUE;
        Range best = null;
        for (Range r : ranges) {
            int wait;
            if (r.start > minute) {
                wait = r.start - minute;
            } else if (r.start == minute) {
                wait = 0;
            } else {
                wait = 1440 - minute + r.start;
            }
            if (wait < bestWait) {
                bestWait = wait;
                best = r;
            }
        }
        if (best == null) return "";
        if (bestWait == 0) return "自由时段现在就开始（" + fmtRange(best) + "）";
        return "下一个自由时段 " + fmtRange(best) + "，还有 " + humanLeft(bestWait) + "开始";
    }
}

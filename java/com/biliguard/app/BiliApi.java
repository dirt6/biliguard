package com.biliguard.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只用 B 站公开的视频信息接口拿标题，不登录、不上传任何东西。 */
public class BiliApi {

    private static final Pattern BVID = Pattern.compile("BV[1-9A-HJ-NP-Za-km-z]{10}");

    public static String extractBvid(String input) {
        if (input == null) return null;
        Matcher m = BVID.matcher(input);
        if (m.find()) return m.group();
        return null;
    }

    /** 一次粘贴多条链接时，把所有 BV 号都抽出来。 */
    public static List<String> extractAllBvids(String input) {
        List<String> out = new ArrayList<String>();
        if (input == null) return out;
        Matcher m = BVID.matcher(input);
        while (m.find()) {
            String bv = m.group();
            if (!out.contains(bv)) out.add(bv);
        }
        return out;
    }

    public static class Result {
        public String title;
        public String error;

        public boolean ok() {
            return title != null && title.length() > 0;
        }
    }

    public static Result fetchTitle(String bvid) {
        Result r = new Result();
        HttpURLConnection c = null;
        BufferedReader br = null;
        try {
            URL u = new URL("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            c.setRequestProperty("Referer", "https://www.bilibili.com/");
            int code = c.getResponseCode();
            if (code != 200) {
                r.error = "HTTP " + code;
                return r;
            }
            br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONObject o = new JSONObject(sb.toString());
            int rc = o.optInt("code", -1);
            if (rc != 0) {
                r.error = "接口返回 code=" + rc + " " + o.optString("message");
                return r;
            }
            r.title = o.getJSONObject("data").optString("title", "");
            if (r.title.length() == 0) r.error = "接口没有返回标题";
            return r;
        } catch (Exception e) {
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            return r;
        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {
            }
            if (c != null) c.disconnect();
        }
    }

    /**
     * 抓任意网页的标题，给抖音 / 小红书 / 微博这类分享链接用。
     * 会跟随 3xx 跳转（短链），优先读 og:title，其次读 &lt;title&gt;。
     */
    public static String fetchPageTitle(String url) {
        HttpURLConnection c = null;
        BufferedReader br = null;
        try {
            String cur = url;
            for (int hop = 0; hop < 5; hop++) {
                URL u = new URL(cur);
                c = (HttpURLConnection) u.openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) return null;
                    if (!loc.startsWith("http")) loc = new URL(u, loc).toString();
                    cur = loc;
                    continue;
                }
                if (code != 200) return null;
                br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                int lines = 0;
                while ((line = br.readLine()) != null && lines < 500) {
                    sb.append(line).append('\n');
                    lines++;
                }
                return extractTitle(sb.toString());
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {
            }
            if (c != null) c.disconnect();
        }
    }

    private static String extractTitle(String html) {
        if (html == null) return null;
        Matcher m = Pattern.compile(
                "<meta[^>]+(?:property|name)\\s*=\\s*[\"']og:title[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) {
            Matcher cm = Pattern.compile("content\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                    .matcher(m.group());
            if (cm.find()) return unescapeHtml(cm.group(1));
        }
        m = Pattern.compile(
                "<meta[^>]+content\\s*=\\s*[\"']([^\"']+)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']og:title[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return unescapeHtml(m.group(1));

        m = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (m.find()) {
            String t = m.group(1).trim();
            if (t.length() > 0) return unescapeHtml(t);
        }
        return null;
    }

    private static String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
                .replace("&nbsp;", " ").trim();
    }
}

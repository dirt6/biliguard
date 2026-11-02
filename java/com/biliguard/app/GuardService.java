package com.biliguard.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 无障碍服务：盯着当前前台 App。
 * 如果在受管 App（B 站）里，且不在自由时段，且当前视频不在学习名单里，就盖遮罩。
 */
public class GuardService extends AccessibilityService {

    public static volatile GuardService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private Overlay overlay;
    private String curPkg = "";
    private String curActivity = "";
    private boolean evalPending = false;
    private boolean inEval = false;

    /**
     * 兜底轮询：不依赖事件。某些操作下系统不会发无障碍事件，
     * 只靠事件驱动会出现"完全不弹"的情况。
     */
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            try {
                evaluate();
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 3000);
        }
    };

    /** 确认是学习视频后的"宽限记忆"时长：期间即使在播放页向下滚、或全屏，也不再拦。 */
    private static final long STICKY_MS = 15 * 60 * 1000L;
    private long stickyUntil = 0L;

    /** 点「离开」后的抑制期 */
    private long suppressUntil = 0L;

    public void suppress(long ms) {
        suppressUntil = System.currentTimeMillis() + ms;
    }

    private final Runnable evalTask = new Runnable() {
        @Override
        public void run() {
            evalPending = false;
            evaluate();
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = new Prefs(this);
        overlay = new Overlay(this, prefs);
        loadIgnorePkgs();
        // 服务可能在用户已经处在 B 站里时才被系统拉起，先问一下前台是谁
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                curPkg = root.getPackageName().toString();
            }
        } catch (Exception ignored) {
        }
        handler.postDelayed(evalTask, 120);
        handler.postDelayed(evalTask, 1500);
        handler.postDelayed(pollTask, 2000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event == null) return;
            CharSequence pkgCs = event.getPackageName();
            if (pkgCs != null) {
                String p = pkgCs.toString();
                if (getPackageName().equals(p)) {
                    // 自己遮罩窗口产生的事件：绝不能拿来判断"前台是谁"
                    schedule(150);
                    return;
                }
                // 输入法、系统 UI 会浮在最上层，不能当成前台 App
                if (ignorePkgs.contains(p)) {
                    schedule(150);
                    return;
                }
                // 受管 App 的事件立即生效：进入要快，遮罩才灵敏。
                // 非受管包（桌面、系统组件、悬浮窗）不在这里改判，
                // 交给 evaluate 里的"持续 1.5 秒确认"，避免遮罩刚出来就被撤。
                if (prefs.managedApps().contains(p)) {
                    curPkg = p;
                    candidatePkg = "";
                }
            }
            schedule(150);
        } catch (Throwable ignored) {
            // 无障碍服务里抛出的异常会让系统永久停用本服务，必须全部兜住
        }
    }

    /** 当前是不是 B 站那种视频播放详情页（能滚动、能全屏的那种）。 */
    private boolean isDetailPage() {
        String a = curActivity;
        return a.contains("Detail") || a.contains("Player") || a.contains("Video");
    }

    private long lastForegroundCheck = 0L;
    private long lastFgCheck = 0L;
    private String candidatePkg = "";
    private long candidateSince = 0L;

    /** 某个包是否还有可见的应用窗口。 */
    private boolean isWindowVisible(String pkg) {
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            if (ws == null) return false;
            for (AccessibilityWindowInfo w : ws) {
                if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo r = null;
                try {
                    r = w.getRoot();
                } catch (Throwable ignored) {
                }
                if (r == null) continue;
                CharSequence p = r.getPackageName();
                if (p != null && pkg.equals(p.toString())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 前台切换分两种情况：
     * 进入受管 App —— 立刻认，遮罩要及时出现；
     * 离开（桌面、其它 App）—— 要持续 1.5 秒才认，避免系统组件/悬浮窗瞬时抢占
     * 导致遮罩刚出来就被撤掉。
     */
    private void confirmForeground(String fg, long now) {
        if (fg.equals(curPkg)) {
            candidatePkg = "";
            return;
        }
        if (prefs.managedApps().contains(fg)) {
            curPkg = fg;
            candidatePkg = "";
            return;
        }
        if (!fg.equals(candidatePkg)) {
            candidatePkg = fg;
            candidateSince = now;
            return;
        }
        if (now - candidateSince >= 1500) {
            curPkg = fg;
            candidatePkg = "";
        }
    }

    /** 输入法、系统 UI 这类窗口会跑到最上层，不能当成"前台 App" */
    private java.util.Set<String> ignorePkgs = new java.util.HashSet<String>();

    private void loadIgnorePkgs() {
        ignorePkgs.add("com.android.systemui");
        ignorePkgs.add("com.miui.securitycenter");
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (android.view.inputmethod.InputMethodInfo info : imm.getEnabledInputMethodList()) {
                    if (info != null && info.getPackageName() != null) {
                        ignorePkgs.add(info.getPackageName());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 按窗口层级取最上层的可见应用窗口：这就是前台 App。 */
    private String foregroundFromWindows() {
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            if (ws == null || ws.isEmpty()) return null;
            for (AccessibilityWindowInfo w : ws) {
                if (w == null) continue;
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = null;
                try {
                    root = w.getRoot();
                } catch (Throwable ignored) {
                }
                if (root == null) continue;
                CharSequence p = root.getPackageName();
                if (p == null) continue;
                String s = p.toString();
                if (getPackageName().equals(s)) continue;
                if (ignorePkgs.contains(s)) continue;
                return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 从 UsageStats 里取最近切到前台的应用，作为窗口判断的补充。 */
    private String foregroundFromUsage() {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = usm.queryEvents(now - 8000L, now);
            if (ev == null) return null;
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String last = null;
            long lastTs = 0L;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (e.getTimeStamp() >= lastTs) {
                        lastTs = e.getTimeStamp();
                        last = e.getPackageName();
                    }
                }
            }
            return last;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 直接看活动窗口的根节点归属，比等事件可靠；比遍历窗口列表稳（后者容易误判）。 */
    private void refreshForeground() {
        long now = System.currentTimeMillis();
        if (now - lastForegroundCheck < 700) return;
        lastForegroundCheck = now;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            CharSequence p = root.getPackageName();
            if (p == null) return;
            String s = p.toString();
            if (getPackageName().equals(s)) return;
            // 输入法/系统窗口在最上层时，保持原来的前台判断不动
            if (ignorePkgs.contains(s)) return;
            if (!s.equals(curPkg)) {
                curPkg = s;
                CharSequence cn = null;
                try {
                    cn = root.getClassName();
                } catch (Throwable ignored) {
                }
                if (cn != null) curActivity = cn.toString();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (overlay != null) overlay.hide();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    private void schedule(long delayMs) {
        // 注意不能用 removeCallbacks 做防抖：
        // B 站播放页事件几乎不间断，那样会永远推迟、一次都不执行。
        if (evalPending) return;
        evalPending = true;
        handler.postDelayed(evalTask, delayMs);
    }

    private void evaluate() {
        if (inEval) return;
        inEval = true;
        try {
            evaluateInner();
        } catch (Throwable ignored) {
            // 同上：一次判断失败不能被系统当成服务崩溃
        } finally {
            inEval = false;
            writeDebug();
        }
    }

    private long lastDbg = 0L;

    private void writeDebug() {
        long now = System.currentTimeMillis();
        if (now - lastDbg < 2000) return;
        lastDbg = now;
        try {
            prefs.setDebug(curPkg, curActivity, overlay != null && overlay.isShowing(), now);
        } catch (Throwable ignored) {
        }
    }

    private void evaluateInner() {
        if (prefs == null || overlay == null) return;

        // 点了「离开」之后的抑制期：给系统一点时间切回桌面，别立刻又盖上
        if (System.currentTimeMillis() < suppressUntil) {
            clear();
            return;
        }

        // 先按窗口层级判断（最上层可见的应用窗口就是前台），
        // 不行再退到使用情况统计，最后才用事件里的包名。
        // 三条路互补，不同 ROM 上总有能用的。
        long nowMs = System.currentTimeMillis();

        // 进入受管 App：由它的无障碍事件设置 curPkg（可靠）。
        // 离开：不再猜"谁是前台"，而是检查受管 App 的窗口是否还存在于窗口列表。
        // 窗口列表反映的是真实可见窗口，比"活动窗口是谁"稳定得多。
        if (curPkg.length() > 0 && prefs.managedApps().contains(curPkg)) {
            if (!isWindowVisible(curPkg)) {
                if (!"__gone__".equals(candidatePkg)) {
                    candidatePkg = "__gone__";
                    candidateSince = nowMs;
                } else if (nowMs - candidateSince >= 1200) {
                    curPkg = "";
                    candidatePkg = "";
                }
            } else {
                candidatePkg = "";
            }
        }

        if (curPkg.length() == 0) {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null && root.getPackageName() != null) {
                    String s = root.getPackageName().toString();
                    if (!getPackageName().equals(s) && !ignorePkgs.contains(s)) {
                        curPkg = s;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 前台已经不是受管 App（回了桌面、切到别的应用）：立刻撤，不能有任何延迟
        if (!prefs.managedApps().contains(curPkg)) {
            clear();
            return;
        }

        // 遮罩刚出现的一小段时间内不做撤销判断，
        // 避免页面还在加载、判断结果抖动时一会儿挡一会儿撤
        if (overlay.isShowing() && System.currentTimeMillis() - overlay.shownAt() < 1200) return;

        if (!prefs.enabled() || !prefs.managedApps().contains(curPkg)) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < prefs.graceUntil()) {
            clear();
            return;
        }
        if (now < prefs.tempPassUntil()) {
            clear();
            return;
        }
        int minute = Model.nowMinute();
        if (Model.isFreeTime(prefs.windowsRaw(), minute)) {
            clear();
            return;
        }
        if (isStudyOnScreen()) {
            clear();
            return;
        }
        overlay.show(Model.nextFreeText(prefs.windowsRaw(), minute));
        // 遮罩盖上之后继续复查：B 站标题往往要一两秒才渲染出来，
        // 那时应该自动把遮罩撤掉，而不是让学习视频一直被挡。
        if (retryCount < 45) {
            retryCount++;
            handler.removeCallbacks(retryTask);
            handler.postDelayed(retryTask, 3000);
        }
    }

    private void clear() {
        retryCount = 0;
        handler.removeCallbacks(retryTask);
        overlay.hide();
    }

    private int retryCount = 0;

    private final Runnable retryTask = new Runnable() {
        @Override
        public void run() {
            evalPending = false;
            evaluate();
        }
    };

    private static class NodeText {
        final String text;
        final int top;

        NodeText(String t, int y) {
            text = t;
            top = y;
        }
    }

    /**
     * 当前屏幕上是否有学习内容。
     * 只看屏幕上半部分的文字：B 站播放页的标题在那里，下方的相关推荐不算，
     * 否则推荐里出现同名视频就会误放行。
     */
    private boolean isStudyOnScreen() {
        List<Model.Video> videos = Model.loadVideos(prefs);
        List<String> kws = keywords();
        if (videos.isEmpty() && kws.isEmpty()) return false;

        List<NodeText> nodes = grabNodes();
        long now = System.currentTimeMillis();

        // 1) 精确层：B 站给"当前正在看的视频标题"挂了一个带「互动视频」的 content-desc，
        //    推荐列表里的标题没有这个标记。用它判定，向下滚、标题滚出屏幕都不受影响。
        String currentTitle = null;
        for (NodeText n : nodes) {
            if (n.text.startsWith("互动视频")) {
                currentTitle = n.text;
                break;
            }
        }
        if (currentTitle != null) {
            if (matchesStudy(Match.norm(currentTitle), videos, kws)) {
                stickyUntil = now + STICKY_MS;
                return true;
            }
            stickyUntil = 0L;
            return false;
        }

        // 2) 位置层：没有「互动视频」标记的页面（番剧等），只看屏幕上半部分
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int limitTop = (int) (screenH * 0.58f);
        int total = 0;
        for (NodeText n : nodes) total += n.text.length();

        for (NodeText n : nodes) {
            if (n.top > limitTop) continue;
            if (matchesStudy(Match.norm(n.text), videos, kws)) {
                stickyUntil = now + STICKY_MS;
                return true;
            }
        }

        // 3) 记忆层：标题读不到，但刚才确认过是学习视频，而且人还在播放详情页
        if (stickyUntil > now && isDetailPage()) return true;

        if (total < 80) return prefs.allowUnknown();
        return false;
    }

    private boolean matchesStudy(String normText, List<Model.Video> videos, List<String> kws) {
        if (normText == null || normText.length() < 4) return false;
        for (Model.Video v : videos) {
            String key = Match.key(Match.norm(v.title));
            if (key != null && normText.contains(key)) return true;
        }
        for (String k : kws) {
            String nk = Match.norm(k);
            if (nk.length() >= 2 && normText.contains(nk)) return true;
        }
        return false;
    }

    private List<NodeText> grabNodes() {
        List<NodeText> out = new ArrayList<NodeText>();
        // 遮罩显示后，getRootInActiveWindow() 拿到的是我们自己的遮罩窗口，
        // 所以遍历所有窗口，跳过本应用的窗口，才能读到 B 站真正的标题。
        List<AccessibilityWindowInfo> windows = null;
        try {
            windows = getWindows();
        } catch (Exception ignored) {
        }
        if (windows != null && !windows.isEmpty()) {
            String self = getPackageName();
            for (AccessibilityWindowInfo w : windows) {
                if (w == null) continue;
                AccessibilityNodeInfo root = null;
                try {
                    root = w.getRoot();
                } catch (Exception ignored) {
                }
                if (root == null) continue;
                CharSequence p = null;
                try {
                    p = root.getPackageName();
                } catch (Exception ignored) {
                }
                if (p != null && self.equals(p.toString())) continue;
                int[] count = new int[]{0};
                walk(root, out, 0, count);
            }
        }
        if (out.isEmpty()) {
            AccessibilityNodeInfo root = null;
            try {
                root = getRootInActiveWindow();
            } catch (Exception ignored) {
            }
            if (root != null) {
                int[] count = new int[]{0};
                walk(root, out, 0, count);
            }
        }
        return out;
    }

    private void walk(AccessibilityNodeInfo node, List<NodeText> out, int depth, int[] count) {
        if (node == null || depth > 24 || count[0] > 160) return;
        count[0]++;
        int top = 0;
        CharSequence t;
        CharSequence d;
        int n;
        try {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            top = r.top;
            t = node.getText();
            d = node.getContentDescription();
            n = node.getChildCount();
        } catch (Throwable e) {
            // 节点在遍历过程中可能已经失效，直接放弃这一支
            return;
        }
        try {
            if (t != null && t.length() > 0 && t.length() < 220) out.add(new NodeText(t.toString(), top));
            if (d != null && d.length() > 0 && d.length() < 220) out.add(new NodeText(d.toString(), top));
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
            } catch (Throwable ignored) {
            }
            if (child != null) walk(child, out, depth + 1, count);
        }
    }

    private List<String> keywords() {
        List<String> out = new ArrayList<String>();
        String raw = prefs.keywords();
        if (raw == null) return out;
        String[] parts = raw.replace('；', ';').replace('，', ',').split("[,;\\n]+");
        for (String s : parts) {
            s = s.trim();
            if (s.length() >= 2) out.add(s);
        }
        return out;
    }
}

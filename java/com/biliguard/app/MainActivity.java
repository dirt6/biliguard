package com.biliguard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int REQ_IMAGE = 1001;
    private static final int REQ_BG = 1002;

    private Prefs prefs;
    private ScrollView scroller;

    private TextView serviceState;
    private TextView usageState;
    private Switch enableSwitch;
    private TextView appsInfo;
    private LinearLayout whitelistBox;
    private EditText videoInput;
    private EditText keywordInput;
    private EditText windowsInput;
    private ImageView preview;
    private TextView maskCount;
    private Switch unknownSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(buildUi());
        refreshAll();
        askNotificationPermission();
    }

    private void askNotificationPermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2001);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- UI 构建

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        scroller = sv;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, dp(36));
        sv.addView(root);

        TextView h = new TextView(this);
        h.setText("当真好好学习");
        h.setTextSize(25);
        h.setTextColor(0xFF15181D);
        h.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(h);

        TextView sub = new TextView(this);
        sub.setText("只放行学习清单里的内容；其他内容要等到自由时段。对抖音、小黑盒这类没有学习内容的 App，就是纯时段管控。");
        sub.setTextSize(13);
        sub.setTextColor(0xFF6B7280);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(6);
        root.addView(sub, subLp);

        // 状态卡
        LinearLayout st = card(root);
        serviceState = new TextView(this);
        serviceState.setTextSize(14);
        st.addView(serviceState);

        usageState = new TextView(this);
        usageState.setTextSize(13);
        LinearLayout.LayoutParams usLp = new LinearLayout.LayoutParams(-1, -2);
        usLp.topMargin = dp(10);
        st.addView(usageState, usLp);

        Button usage = new Button(this);
        usage.setText("授予「使用情况访问」权限");
        usage.setAllCaps(false);
        usage.setTextColor(0xFF1E6FBF);
        usage.setBackgroundTintList(ColorStateList.valueOf(0xFFE8EEF6));
        usage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        LinearLayout.LayoutParams usbLp = new LinearLayout.LayoutParams(-1, dp(44));
        usbLp.topMargin = dp(8);
        st.addView(usage, usbLp);

        Button overlayBtn = new Button(this);
        overlayBtn.setText("授予「显示在其他应用上层」权限");
        overlayBtn.setAllCaps(false);
        overlayBtn.setTextColor(0xFF1E6FBF);
        overlayBtn.setBackgroundTintList(ColorStateList.valueOf(0xFFE8EEF6));
        overlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        LinearLayout.LayoutParams ovLp = new LinearLayout.LayoutParams(-1, dp(44));
        ovLp.topMargin = dp(8);
        st.addView(overlayBtn, ovLp);

        Button open = new Button(this);
        open.setText("打开系统无障碍设置");
        open.setAllCaps(false);
        open.setTextColor(Color.WHITE);
        open.setBackgroundTintList(ColorStateList.valueOf(0xFF1E6FBF));
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception e) {
                    toast("打不开系统设置，请手动搜索「无障碍」");
                }
            }
        });
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, dp(46));
        openLp.topMargin = dp(10);
        st.addView(open, openLp);

        // 总开关
        LinearLayout sw = card(root);
        enableSwitch = new Switch(this);
        enableSwitch.setText("启用管控");
        enableSwitch.setTextSize(15);
        enableSwitch.setPadding(0, dp(2), 0, dp(2));
        enableSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                prefs.setEnabled(isChecked);
            }
        });
        sw.addView(enableSwitch);

        TextView swNote = new TextView(this);
        swNote.setText("关掉之后遮罩不会再出现，但白名单和时段设置都会保留。");
        swNote.setTextSize(12);
        swNote.setTextColor(0xFF8A9099);
        sw.addView(swNote);

        // 受管应用
        LinearLayout apps = card(root);
        sectionTitle(apps, "管控哪些 App");
        appsInfo = new TextView(this);
        appsInfo.setTextSize(13);
        appsInfo.setTextColor(0xFF4B5563);
        appsInfo.setLineSpacing(dp(3), 1f);
        apps.addView(appsInfo);
        Button pick = new Button(this);
        pick.setText("选择应用");
        pick.setAllCaps(false);
        pick.setTextColor(0xFF1E6FBF);
        pick.setBackgroundTintList(ColorStateList.valueOf(0xFFE8EEF6));
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickApps();
            }
        });
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(-1, dp(44));
        pickLp.topMargin = dp(10);
        apps.addView(pick, pickLp);

        // 学习白名单
        LinearLayout wl = card(root);
        sectionTitle(wl, "学习白名单（随时可看）");
        TextView wlNote = new TextView(this);
        wlNote.setText("粘贴视频链接（B站 / 抖音 / 小红书等）或 B 站 BV 号，自动读取标题。这些内容不受时段限制。");
        wlNote.setTextSize(12);
        wlNote.setTextColor(0xFF8A9099);
        wl.addView(wlNote);

        videoInput = new EditText(this);
        videoInput.setHint("BV1xx411c7mD 或任意视频分享链接");
        videoInput.setTextSize(14);
        videoInput.setSingleLine(true);
        LinearLayout.LayoutParams viLp = new LinearLayout.LayoutParams(-1, -2);
        viLp.topMargin = dp(8);
        wl.addView(videoInput, viLp);

        Button add = new Button(this);
        add.setText("添加到白名单");
        add.setAllCaps(false);
        add.setTextColor(Color.WHITE);
        add.setBackgroundTintList(ColorStateList.valueOf(0xFF1E6FBF));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addVideo();
            }
        });
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, dp(44));
        addLp.topMargin = dp(6);
        wl.addView(add, addLp);

        whitelistBox = new LinearLayout(this);
        whitelistBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.topMargin = dp(8);
        wl.addView(whitelistBox, boxLp);

        TextView kwLabel = new TextView(this);
        kwLabel.setText("标题关键词（可选，逗号分隔）");
        kwLabel.setTextSize(12);
        kwLabel.setTextColor(0xFF6B7280);
        LinearLayout.LayoutParams kwLp = new LinearLayout.LayoutParams(-1, -2);
        kwLp.topMargin = dp(14);
        wl.addView(kwLabel, kwLp);

        keywordInput = new EditText(this);
        keywordInput.setHint("例如：考研数学, 线性代数");
        keywordInput.setTextSize(14);
        keywordInput.setSingleLine(true);
        wl.addView(keywordInput);

        Button kwSave = new Button(this);
        kwSave.setText("保存关键词");
        kwSave.setAllCaps(false);
        kwSave.setTextColor(0xFF1E6FBF);
        kwSave.setBackgroundTintList(ColorStateList.valueOf(0xFFE8EEF6));
        kwSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setKeywords(keywordInput.getText().toString().trim());
                toast("关键词已保存");
            }
        });
        wl.addView(kwSave, new LinearLayout.LayoutParams(-1, dp(44)));

        // 自由时段
        LinearLayout tm = card(root);
        sectionTitle(tm, "自由（娱乐）时段");
        TextView tmNote = new TextView(this);
        tmNote.setText("多个时段用 ; 或 , 分隔。支持跨天，例如 23:00-01:00。默认是午饭和晚饭。");
        tmNote.setTextSize(12);
        tmNote.setTextColor(0xFF8A9099);
        tmNote.setLineSpacing(dp(3), 1f);
        tm.addView(tmNote);

        windowsInput = new EditText(this);
        windowsInput.setTextSize(14);
        windowsInput.setSingleLine(true);
        LinearLayout.LayoutParams wiLp = new LinearLayout.LayoutParams(-1, -2);
        wiLp.topMargin = dp(8);
        tm.addView(windowsInput, wiLp);

        LinearLayout tmRow = new LinearLayout(this);
        tmRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(6);
        tm.addView(tmRow, rowLp);

        Button saveWin = new Button(this);
        saveWin.setText("保存时段");
        saveWin.setAllCaps(false);
        saveWin.setTextColor(Color.WHITE);
        saveWin.setBackgroundTintList(ColorStateList.valueOf(0xFF1E6FBF));
        saveWin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveWindows();
            }
        });
        tmRow.addView(saveWin, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button preset = new Button(this);
        preset.setText("恢复默认");
        preset.setAllCaps(false);
        preset.setTextColor(0xFF1E6FBF);
        preset.setBackgroundTintList(ColorStateList.valueOf(0xFFE8EEF6));
        preset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowsInput.setText("11:30-13:00;17:30-19:30");
                saveWindows();
            }
        });
        LinearLayout.LayoutParams preLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        preLp.leftMargin = dp(8);
        tmRow.addView(preset, preLp);

        // 遮罩图片（可多张，随机显示）
        LinearLayout mask = card(root);
        sectionTitle(mask, "遮罩背景图");
        TextView mNote = new TextView(this);
        mNote.setText("可以放多张，每次被拦时随机抽一张铺满屏幕。不上传就用纯黑。");
        mNote.setTextSize(12);
        mNote.setTextColor(0xFF8A9099);
        mask.addView(mNote);

        maskCount = new TextView(this);
        maskCount.setTextSize(13);
        maskCount.setTextColor(0xFF4B5563);
        LinearLayout.LayoutParams mcLp = new LinearLayout.LayoutParams(-1, -2);
        mcLp.topMargin = dp(8);
        mask.addView(maskCount, mcLp);

        Button choose = new Button(this);
        choose.setText("添加图片（可多选）");
        choose.setAllCaps(false);
        choose.setTextColor(0xFF1E6FBF);
        choose.setBackgroundTintList(ColorStateList.valueOf(0xFFE8EEF6));
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseImage(REQ_IMAGE);
            }
        });
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(-1, dp(44));
        chLp.topMargin = dp(10);
        mask.addView(choose, chLp);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(-1, dp(150));
        pvLp.topMargin = dp(10);
        mask.addView(preview, pvLp);

        Button clear = new Button(this);
        clear.setText("清空遮罩图");
        clear.setAllCaps(false);
        clear.setTextColor(0xFFB42318);
        clear.setBackgroundTintList(ColorStateList.valueOf(0xFFFDECEA));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setMaskUris("");
                prefs.setMaskUri("");
                refreshPreview();
            }
        });
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(-1, dp(42));
        clLp.topMargin = dp(6);
        mask.addView(clear, clLp);

        // 主界面背景
        TextView uiNote = new TextView(this);
        uiNote.setText("主界面背景（同理，卡片会半透明让图透出来）");
        uiNote.setTextSize(12);
        uiNote.setTextColor(0xFF8A9099);
        LinearLayout.LayoutParams uiLp = new LinearLayout.LayoutParams(-1, -2);
        uiLp.topMargin = dp(16);
        mask.addView(uiNote, uiLp);

        Button uiBg = new Button(this);
        uiBg.setText("设置主界面背景");
        uiBg.setAllCaps(false);
        uiBg.setTextColor(0xFF1E6FBF);
        uiBg.setBackgroundTintList(ColorStateList.valueOf(0xFFE8EEF6));
        uiBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseImage(REQ_BG);
            }
        });
        LinearLayout.LayoutParams ubLp = new LinearLayout.LayoutParams(-1, dp(44));
        ubLp.topMargin = dp(8);
        mask.addView(uiBg, ubLp);

        Button uiBgClear = new Button(this);
        uiBgClear.setText("清除主界面背景");
        uiBgClear.setAllCaps(false);
        uiBgClear.setTextColor(0xFFB42318);
        uiBgClear.setBackgroundTintList(ColorStateList.valueOf(0xFFFDECEA));
        uiBgClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setUiBgUri("");
                applyUiBackground();
            }
        });
        LinearLayout.LayoutParams ubcLp = new LinearLayout.LayoutParams(-1, dp(42));
        ubcLp.topMargin = dp(6);
        mask.addView(uiBgClear, ubcLp);

        // 高级
        LinearLayout adv = card(root);
        sectionTitle(adv, "高级");
        unknownSwitch = new Switch(this);
        unknownSwitch.setText("读不到界面文字时放行");
        unknownSwitch.setTextSize(14);
        unknownSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                prefs.setAllowUnknown(isChecked);
            }
        });
        adv.addView(unknownSwitch);
        TextView advNote = new TextView(this);
        advNote.setText("默认开。全屏播放时读不到标题会放行；关掉更严格，但可能把全屏网课也拦掉。");
        advNote.setTextSize(12);
        advNote.setTextColor(0xFF8A9099);
        adv.addView(advNote);

        // 说明
        TextView foot = new TextView(this);
        foot.setText("说明\n"
                + "1. 遮罩靠系统「无障碍服务」实现，不是悬浮窗，不会挡住别的东西。\n"
                + "2. 临时放行每天 2 次、每次 10 分钟，用来应付误拦。\n"
                + "3. 白名单匹配看的是屏幕上的标题文字，B 站改版可能导致匹配失效，届时把标题关键词加进去即可。\n"
                + "4. 这个 App 不联网上传任何数据，只有添加白名单时会去 B 站公开接口读标题。");
        foot.setTextSize(12);
        foot.setTextColor(0xFF8A9099);
        foot.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-1, -2);
        fLp.topMargin = dp(18);
        root.addView(foot, fLp);

        return sv;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(14));
        gd.setColor(0xCCFFFFFF); // 80% 不透明，主界面背景图能透出来
        c.setBackground(gd);
        int pad = dp(14);
        c.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private void sectionTitle(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(0xFF15181D);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        parent.addView(t);
    }

    // ---------------------------------------------------------------- 刷新

    private void refreshAll() {
        boolean inSettings = isServiceInSettings();
        boolean running = GuardService.instance != null;
        if (running) {
            serviceState.setText("无障碍服务：运行中 ✓");
            serviceState.setTextColor(0xFF12805C);
        } else if (inSettings) {
            serviceState.setText("无障碍服务：已开启但没在运行 —— 重启一次手机就能恢复");
            serviceState.setTextColor(0xFFB42318);
        } else {
            serviceState.setText("无障碍服务：未开启，遮罩不会生效");
            serviceState.setTextColor(0xFFB42318);
        }

        enableSwitch.setChecked(prefs.enabled());

        if (usageState != null) {
            boolean ua = hasUsageAccess();
            usageState.setText(ua
                    ? "使用情况访问：已授予 ✓（前台判断准确）"
                    : "使用情况访问：未授予 —— 前台判断会不准，建议授予");
            usageState.setTextColor(ua ? 0xFF12805C : 0xFFB42318);
        }

        Set<String> apps = prefs.managedApps();
        if (apps.isEmpty()) {
            appsInfo.setText("还没有选择任何 App");
        } else {
            StringBuilder sb = new StringBuilder("正在管控 " + apps.size() + " 个 App：");
            PackageManager pm = getPackageManager();
            for (String pkg : apps) {
                sb.append("\n· ").append(appLabel(pm, pkg));
            }
            appsInfo.setText(sb.toString());
        }

        windowsInput.setText(prefs.windowsRaw());
        keywordInput.setText(prefs.keywords());
        unknownSwitch.setChecked(prefs.allowUnknown());

        rebuildWhitelist();
        refreshPreview();
        applyUiBackground();
    }

    private String appLabel(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg + "（未安装）";
        }
    }

    private void rebuildWhitelist() {
        whitelistBox.removeAllViews();
        List<Model.Video> list = Model.loadVideos(prefs);
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有学习视频。先加几个，否则 B 站会被完全拦住。");
            empty.setTextSize(12);
            empty.setTextColor(0xFFB42318);
            whitelistBox.addView(empty);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final Model.Video v = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = dp(8);
            whitelistBox.addView(row, rowLp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(0, dp(6), 0, dp(6));
            texts.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openVideo(v.bvid);
                }
            });
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView t = new TextView(this);
            t.setText(v.title);
            t.setTextSize(13);
            t.setTextColor(0xFF15181D);
            t.setLineSpacing(dp(2), 1f);
            texts.addView(t);

            TextView b = new TextView(this);
            b.setText(v.bvid + " · 点一下就能看");
            b.setTextSize(11);
            b.setTextColor(0xFF8A9099);
            texts.addView(b);

            TextView del = new TextView(this);
            del.setText("删除");
            del.setTextSize(12);
            del.setTextColor(0xFFB42318);
            del.setPadding(dp(12), dp(8), dp(4), dp(8));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    List<Model.Video> all = Model.loadVideos(prefs);
                    List<Model.Video> keep = new ArrayList<Model.Video>();
                    for (Model.Video x : all) {
                        if (!x.bvid.equals(v.bvid)) keep.add(x);
                    }
                    Model.saveVideos(prefs, keep);
                    rebuildWhitelist();
                }
            });
            row.addView(del);
        }
    }

    private void refreshPreview() {
        List<String> uris = prefs.maskUrisList();
        if (maskCount != null) {
            maskCount.setText(uris.isEmpty()
                    ? "还没有设置遮罩图，会使用纯黑背景"
                    : "已设置 " + uris.size() + " 张，每次随机显示一张");
        }
        if (uris.isEmpty()) {
            preview.setImageDrawable(null);
            preview.setBackgroundColor(0xFFEDEFF2);
            return;
        }
        try {
            String uriStr = uris.get(0);
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(Uri.parse(uriStr));
            BitmapFactory.decodeStream(in, null, probe);
            if (in != null) in.close();
            int sample = 1;
            while (probe.outWidth / sample > 720 || probe.outHeight / sample > 720) sample *= 2;
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            InputStream in2 = getContentResolver().openInputStream(Uri.parse(uriStr));
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, real);
            if (in2 != null) in2.close();
            preview.setBackgroundColor(0xFFEDEFF2);
            preview.setImageBitmap(bmp);
        } catch (Exception e) {
            preview.setImageDrawable(null);
            preview.setBackgroundColor(0xFFEDEFF2);
            toast("图片读取失败，换一张试试");
        }
    }

    private void applyUiBackground() {
        if (scroller == null) return;
        String uriStr = prefs.uiBgUri();
        if (uriStr == null || uriStr.length() == 0) {
            scroller.setBackgroundColor(0xFFF5F6F8);
            return;
        }
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(Uri.parse(uriStr));
            BitmapFactory.decodeStream(in, null, probe);
            if (in != null) in.close();
            int sample = 1;
            while (probe.outWidth / sample > 1080 || probe.outHeight / sample > 2400) sample *= 2;
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            InputStream in2 = getContentResolver().openInputStream(Uri.parse(uriStr));
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, real);
            if (in2 != null) in2.close();
            if (bmp != null) {
                scroller.setBackground(new BitmapDrawable(getResources(), bmp));
            } else {
                scroller.setBackgroundColor(0xFFF5F6F8);
            }
        } catch (Exception e) {
            scroller.setBackgroundColor(0xFFF5F6F8);
        }
    }

    private boolean hasUsageAccess() {
        try {
            android.app.AppOpsManager aom =
                    (android.app.AppOpsManager) getSystemService(android.content.Context.APP_OPS_SERVICE);
            if (aom == null) return false;
            int mode = aom.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isServiceInSettings() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String pkg = getPackageName();
            String full = GuardService.class.getName();
            String simple = GuardService.class.getSimpleName();
            return enabled.contains(pkg + "/" + full)
                    || enabled.contains(pkg + "/." + simple)
                    || (enabled.contains(pkg) && enabled.contains(simple));
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 动作

    private void addVideo() {
        String raw = videoInput.getText().toString().trim();
        if (raw.length() == 0) {
            toast("先粘贴链接或 BV 号");
            return;
        }
        final List<String> bvids = BiliApi.extractAllBvids(raw);

        final List<String> urls = new ArrayList<String>();
        Matcher um = Pattern.compile("https?://[^\\s，,、）)]+").matcher(raw);
        while (um.find()) {
            String u = um.group();
            if (BiliApi.extractBvid(u) == null && !urls.contains(u)) urls.add(u);
        }
        if (bvids.isEmpty() && urls.isEmpty()) {
            toast("没找到可识别的链接或 BV 号");
            return;
        }
        videoInput.setText("");
        toast("正在获取标题…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String[]> okList = new ArrayList<String[]>();
                final List<String> badList = new ArrayList<String>();
                String firstErr = "";
                for (String bv : bvids) {
                    BiliApi.Result r = BiliApi.fetchTitle(bv);
                    if (r.ok()) {
                        okList.add(new String[]{bv, r.title});
                    } else {
                        badList.add(bv);
                        if (firstErr.length() == 0) firstErr = r.error;
                    }
                }
                for (String u : urls) {
                    String t = BiliApi.fetchPageTitle(u);
                    if (t != null && t.length() > 0) {
                        okList.add(new String[]{u, t});
                    } else {
                        badList.add(u);
                        if (firstErr.length() == 0) firstErr = "抓不到网页标题";
                    }
                }
                final String err = firstErr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        List<Model.Video> list = Model.loadVideos(prefs);
                        Set<String> have = new HashSet<String>();
                        for (Model.Video v : list) have.add(v.bvid);
                        int added = 0;
                        for (String[] pair : okList) {
                            if (!have.contains(pair[0])) {
                                list.add(new Model.Video(pair[0], pair[1]));
                                have.add(pair[0]);
                                added++;
                            }
                        }
                        Model.saveVideos(prefs, list);
                        rebuildWhitelist();
                        StringBuilder msg = new StringBuilder("已添加 " + added + " 个");
                        if (!badList.isEmpty()) msg.append("，失败 ").append(badList.size()).append(" 个");
                        toast(msg.toString());
                        if (badList.size() == 1) {
                            askTitleManually(badList.get(0), err);
                        }
                    }
                });
            }
        }).start();
    }

    /** 点白名单里的条目：B 站用 scheme 直接进播放页，其它平台就正常打开链接。 */
    private void openVideo(String id) {
        prefs.setGraceUntil(System.currentTimeMillis() + 8000L);
        if (id != null && id.startsWith("http")) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(id));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                toast("打不开这个链接");
            }
            return;
        }
        Intent direct = new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/" + id));
        direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(direct);
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.bilibili.com/video/" + id)));
        } catch (Exception e) {
            toast("没找到能打开 B 站的 App");
        }
    }

    private void askTitleManually(final String bvid, String err) {
        final EditText et = new EditText(this);
        et.setHint("视频标题");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("自动获取标题失败")
                .setMessage(err + "\n\n可以手动填标题（从 B 站视频页复制）：")
                .setView(et)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String t = et.getText().toString().trim();
                        if (t.length() == 0) {
                            toast("标题不能为空");
                            return;
                        }
                        saveVideo(bvid, t);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveVideo(String bvid, String title) {
        List<Model.Video> list = Model.loadVideos(prefs);
        for (Model.Video v : list) {
            if (v.bvid.equals(bvid)) {
                v.title = title;
                Model.saveVideos(prefs, list);
                rebuildWhitelist();
                toast("已更新：" + title);
                return;
            }
        }
        list.add(new Model.Video(bvid, title));
        Model.saveVideos(prefs, list);
        rebuildWhitelist();
        toast("已加入白名单");
    }

    private void saveWindows() {
        String s = windowsInput.getText().toString().trim();
        List<Model.Range> rs = Model.parseRanges(s);
        if (rs.isEmpty()) {
            toast("没解析出合法时段，格式如 11:30-13:00;17:30-19:30");
            return;
        }
        prefs.setWindowsRaw(s);
        toast("已保存 " + rs.size() + " 个时段");
    }

    private void pickApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(intent, 0);

        final List<String> pkgs = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : ris) {
            if (ri == null || ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg == null || pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            pkgs.add(pkg);
            labels.add(appLabel(pm, pkg));
        }
        if (pkgs.isEmpty()) {
            toast("读不到已安装应用列表");
            return;
        }
        // 按拼音排序：中文名不能落到字母后面去
        final Collator collator = Collator.getInstance(java.util.Locale.CHINA);
        List<Integer> idx = new ArrayList<Integer>();
        for (int i = 0; i < labels.size(); i++) idx.add(i);
        Collections.sort(idx, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return collator.compare(labels.get(a), labels.get(b));
            }
        });

        final String[] items = new String[idx.size()];
        final String[] orderedPkgs = new String[idx.size()];
        Set<String> current = prefs.managedApps();
        final boolean[] checked = new boolean[idx.size()];
        for (int i = 0; i < idx.size(); i++) {
            int src = idx.get(i);
            orderedPkgs[i] = pkgs.get(src);
            items[i] = labels.get(src) + "\n" + pkgs.get(src);
            checked[i] = current.contains(pkgs.get(src));
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要管控的 App")
                .setMultiChoiceItems(items, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Set<String> out = new HashSet<String>();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) out.add(orderedPkgs[i]);
                        }
                        prefs.setManagedApps(out);
                        refreshAll();
                        toast("已保存 " + out.size() + " 个 App");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void chooseImage(int req) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, req == REQ_IMAGE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, req);
        } catch (Exception e) {
            toast("打不开图片选择器");
        }
    }

    private void takePersist(Uri u) {
        try {
            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_BG) {
            Uri u = data.getData();
            if (u == null) return;
            takePersist(u);
            prefs.setUiBgUri(u.toString());
            applyUiBackground();
            toast("主界面背景已设置");
            return;
        }

        if (requestCode == REQ_IMAGE) {
            List<String> list = prefs.maskUrisList();
            int added = 0;
            if (data.getClipData() != null) {
                ClipData cd = data.getClipData();
                for (int i = 0; i < cd.getItemCount(); i++) {
                    Uri u = cd.getItemAt(i).getUri();
                    if (u == null) continue;
                    takePersist(u);
                    list.add(u.toString());
                    added++;
                }
            } else if (data.getData() != null) {
                Uri u = data.getData();
                takePersist(u);
                list.add(u.toString());
                added++;
            }
            if (added > 0) {
                prefs.setMaskUris(prefs.joinUris(list));
                refreshPreview();
                toast("已添加 " + added + " 张遮罩图");
            }
        }
    }
}

package com.biliguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 盖在 B 站上面的全屏遮罩。用无障碍服务自带的 overlay 窗口，不需要悬浮窗权限。 */
public class Overlay {

    private final AccessibilityService svc;
    private final Prefs prefs;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private TextView hintView;
    private TextView clockView;
    private long shownAt = 0L;

    public long shownAt() {
        return shownAt;
    }

    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (root == null) return;
            if (clockView != null) clockView.setText(clockFmt.format(new Date()));
            handler.postDelayed(this, 15000);
        }
    };

    public Overlay(AccessibilityService s, Prefs p) {
        svc = s;
        prefs = p;
        wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(int v) {
        return (int) (v * svc.getResources().getDisplayMetrics().density + 0.5f);
    }

    public boolean isShowing() {
        return root != null;
    }

    public void show(String hint) {
        if (root != null) {
            if (hintView != null) hintView.setText(hint);
            return;
        }
        try {
            build(hint);
        } catch (Throwable t) {
            root = null;
            return;
        }
        int winType;
        if (android.os.Build.VERSION.SDK_INT >= 26
                && android.provider.Settings.canDrawOverlays(svc)) {
            // 有悬浮窗权限时用系统标准类型，Android 14+ 上比无障碍覆盖窗口可靠
            winType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            winType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            root = null;
            return;
        }
        shownAt = System.currentTimeMillis();
        handler.post(clockTick);
    }

    public void hide() {
        handler.removeCallbacks(clockTick);
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        root = null;
        hintView = null;
        clockView = null;
        shownAt = 0L;
    }

    private void build(String hint) {
        FrameLayout container = new FrameLayout(svc);
        container.setBackgroundColor(Color.parseColor("#0E1116"));
        // 别让遮罩自己进入无障碍树，否则服务读到的是遮罩文字而不是 B 站的
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        Bitmap bmp = loadMaskBitmap();
        if (bmp != null) {
            ImageView bg = new ImageView(svc);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setImageBitmap(bmp);
            container.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        }

        View scrim = new View(svc);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55000000, 0xCC000000, 0xEE000000});
        scrim.setBackground(gd);
        container.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(svc);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(26);
        col.setPadding(pad, dp(32), pad, dp(26));
        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(-1, -1);
        container.addView(col, colLp);

        // 顶部：时间
        clockView = new TextView(svc);
        clockView.setText(clockFmt.format(new Date()));
        clockView.setTextColor(0x99FFFFFF);
        clockView.setTextSize(13);
        col.addView(clockView);

        spacer(col, 40);

        TextView brand = new TextView(svc);
        brand.setText("当真好好学习 · 学习时间");
        brand.setTextColor(0xB3FFFFFF);
        brand.setTextSize(14);
        brand.setGravity(Gravity.CENTER);
        col.addView(brand);

        spacer(col, 14);

        TextView title = new TextView(svc);
        title.setText("现在是学习时间");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        spacer(col, 12);

        hintView = new TextView(svc);
        hintView.setText(hint);
        hintView.setTextColor(0xCCFFFFFF);
        hintView.setTextSize(15);
        hintView.setGravity(Gravity.CENTER);
        hintView.setLineSpacing(dp(4), 1f);
        col.addView(hintView);

        spacer(col, 10);

        TextView note = new TextView(svc);
        note.setText("学习清单里的内容不受限制；其他内容等自由时段。");
        note.setTextColor(0x8AFFFFFF);
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        col.addView(note);

        spacer(col, 36);

        // 剩余空间把按钮压到底部
        View flex = new View(svc);
        LinearLayout.LayoutParams flexLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        col.addView(flex, flexLp);

        final int left = prefs.passLeft();
        TextView pass = button("临时放行 10 分钟" + (left > 0 ? "（今天还剩 " + left + " 次）" : "（今天已用完）"),
                0x33FFFFFF, 0xFFFFFFFF);
        pass.setEnabled(left > 0);
        pass.setAlpha(left > 0 ? 1f : 0.45f);
        pass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!prefs.consumePass()) {
                    Toast.makeText(svc, "今天的临时放行次数用完了", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.setTempPassUntil(System.currentTimeMillis() + Prefs.PASS_DURATION_MS);
                hide();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(52));
        btnLp.bottomMargin = dp(10);
        col.addView(pass, btnLp);

        TextView home = button("离开，去干正事", 0x00000000, 0xAAFFFFFF);
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 先进入抑制期再撤遮罩，否则 B 站 还在前台，
                // 撤掉的一瞬间会再次判定并重新盖上，人已经到桌面了还被挡
                if (svc instanceof GuardService) {
                    ((GuardService) svc).suppress(6000);
                }
                hide();
                try {
                    Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                    homeIntent.addCategory(Intent.CATEGORY_HOME);
                    homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    svc.startActivity(homeIntent);
                } catch (Exception e) {
                    try {
                        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        col.addView(home, new LinearLayout.LayoutParams(-1, dp(48)));

        root = container;
    }

    private void spacer(LinearLayout parent, int h) {
        View v = new View(svc);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private TextView button(String text, int bgColor, int textColor) {
        TextView tv = new TextView(svc);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(textColor);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(24));
        gd.setColor(bgColor);
        if (bgColor == 0x33FFFFFF) {
            gd.setStroke(dp(1), 0x55FFFFFF);
        }
        tv.setBackground(gd);
        return tv;
    }

    private Bitmap loadMaskBitmap() {
        java.util.List<String> uris = prefs.maskUrisList();
        if (uris.isEmpty()) return null;
        // 多张图时每次被拦随机抽一张
        String pick = uris.get(new java.util.Random().nextInt(uris.size()));
        Uri uri = Uri.parse(pick);
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            InputStream in1 = svc.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in1, null, probe);
            if (in1 != null) in1.close();

            int sample = 1;
            int w = probe.outWidth;
            int h = probe.outHeight;
            if (w > 0 && h > 0) {
                while (w / sample > 1080 || h / sample > 2400) sample *= 2;
            }
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            InputStream in2 = svc.getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, real);
            if (in2 != null) in2.close();
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }
}

package com.biliguard.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;
import java.util.Random;

/**
 * 全屏拦截页。
 * 小米 HyperOS 会移除无障碍覆盖窗口，但允许有悬浮窗权限的应用后台启动 Activity，
 * 所以改用标准 Activity 来盖住当前应用。
 */
public class MaskActivity extends Activity {

    private Prefs prefs;
    private TextView hintView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        String hint = "";
        try {
            hint = getIntent().getStringExtra("hint");
        } catch (Throwable ignored) {
        }
        if (hint == null) hint = "";
        setContentView(build(hint));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try {
            String hint = intent.getStringExtra("hint");
            if (hint != null && hintView != null) hintView.setText(hint);
        } catch (Throwable ignored) {
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View build(String hint) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0E1116);

        Bitmap pic = loadPic();
        if (pic != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(pic);
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }

        View scrim = new View(this);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55000000, 0xCC000000, 0xEE000000});
        scrim.setBackground(g);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(26), dp(48), dp(26), dp(30));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView brand = new TextView(this);
        brand.setText("当真好好学习 · 学习时间");
        brand.setTextColor(0xB3FFFFFF);
        brand.setTextSize(14);
        brand.setGravity(Gravity.CENTER);
        col.addView(brand);

        TextView title = new TextView(this);
        title.setText("现在是学习时间");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(14);
        col.addView(title, tlp);

        hintView = new TextView(this);
        hintView.setText(hint);
        hintView.setTextColor(0xCCFFFFFF);
        hintView.setTextSize(15);
        hintView.setGravity(Gravity.CENTER);
        hintView.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(12);
        col.addView(hintView, hlp);

        View flex = new View(this);
        col.addView(flex, new LinearLayout.LayoutParams(-1, 0, 1f));

        final int left = prefs.passLeft();
        TextView pass = button("临时放行 10 分钟" + (left > 0 ? "（今天还剩 " + left + " 次）" : "（今天已用完）"),
                0x33FFFFFF, Color.WHITE);
        pass.setEnabled(left > 0);
        pass.setAlpha(left > 0 ? 1f : 0.45f);
        pass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (prefs.consumePass()) {
                    prefs.setTempPassUntil(System.currentTimeMillis() + Prefs.PASS_DURATION_MS);
                }
                finish();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(52));
        plp.bottomMargin = dp(10);
        col.addView(pass, plp);

        TextView leave = button("离开，去干正事", 0x00000000, 0xAAFFFFFF);
        leave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_HOME);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable ignored) {
                }
                finish();
            }
        });
        col.addView(leave, new LinearLayout.LayoutParams(-1, dp(48)));

        return root;
    }

    private TextView button(String text, int bg, int textColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(textColor);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(bg);
        if (bg == 0x33FFFFFF) g.setStroke(dp(1), 0x55FFFFFF);
        tv.setBackground(g);
        return tv;
    }

    private Bitmap loadPic() {
        try {
            List<String> uris = prefs.maskUrisList();
            if (uris.isEmpty()) return null;
            Uri uri = Uri.parse(uris.get(new Random().nextInt(uris.size())));
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, probe);
            if (in != null) in.close();
            int sample = 1;
            while (probe.outWidth / sample > 1080 || probe.outHeight / sample > 2400) sample *= 2;
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            InputStream in2 = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, real);
            if (in2 != null) in2.close();
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        // 返回键不关闭，避免一按就绕过
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.twoyi;

import android.app.Activity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.cleveroad.androidmanimation.LoadingAnimationView;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.twoyi.utils.AppKV;
import io.twoyi.utils.NavUtils;
import io.twoyi.utils.RomUtil;
import io.twoyi.utils.UIHelper;

/**
 * @author weishu
 * @date 2021/10/20.
 */
public class Render2Activity extends Activity implements View.OnTouchListener {

    private static final String TAG = "Render2Activity";

    static {
        System.loadLibrary("twoyi");
    }

    private SurfaceView mSurfaceView;

    private ViewGroup mRootView;
    private LoadingAnimationView mLoadingView;
    private TextView mLoadingText;
    private View mLoadingLayout;
    private View mBootLogView;

    private final AtomicBoolean mIsExtracting = new AtomicBoolean(false);

    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Surface surface = holder.getSurface();
            WindowManager windowManager = getWindowManager();
            Display defaultDisplay = windowManager.getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            defaultDisplay.getRealMetrics(displayMetrics);

            float xdpi = displayMetrics.xdpi;
            float ydpi = displayMetrics.ydpi;
            Renderer.init(surface, xdpi, ydpi, (int) getBestFps());

            Log.i(TAG, "surfaceCreated");
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            Surface surface = holder.getSurface();
            Renderer.resetWindow(surface, 0, 0, mSurfaceView.getWidth(), mSurfaceView.getHeight());
            Log.i(TAG, "surfaceChanged: " + mSurfaceView.getWidth() + "x" + mSurfaceView.getHeight());
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            Renderer.removeWindow(holder.getSurface());
            Log.i(TAG, "surfaceDestroyed!");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        NavUtils.hideNavigation(getWindow());

        super.onCreate(savedInstanceState);

        setContentView(R.layout.ac_render);

        mRootView = findViewById(R.id.root);

        mSurfaceView = new SurfaceView(this);
        mSurfaceView.getHolder().addCallback(mSurfaceCallback);

        mLoadingLayout = findViewById(R.id.loadingLayout);
        mLoadingView = findViewById(R.id.loading);
        mLoadingText = findViewById(R.id.loadingText);
        mBootLogView = findViewById(R.id.bootlog);

        mLoadingLayout.setVisibility(View.VISIBLE);
        mLoadingView.startAnimation();

        if (!RomUtil.romExist(this) || RomUtil.needsUpgrade(this)) {
            Log.i(TAG, "extracting rom...");

            showTipsForFirstBoot();

            new Thread(() -> {
                mIsExtracting.set(true);
                RomUtil.extractRootfs(getApplicationContext());
                mIsExtracting.set(false);
                runOnUiThread(() -> {
                    mRootView.addView(mSurfaceView, 0);
                    showBootingProcedure();
                });
            }, "extract-rom").start();
        } else {
            mRootView.addView(mSurfaceView, 0);
            showBootingProcedure();
        }

        mSurfaceView.setOnTouchListener(this);

        showAndroid12Tips();
    }

    private void showTipsForFirstBoot() {
        mLoadingText.setText(R.string.extracting_tips);
        mRootView.postDelayed(() -> {
            if (mIsExtracting.get()) {
                mLoadingText.setText(R.string.first_boot_tips);
            }
        }, 5000);

        mRootView.postDelayed(() -> {
            if (mIsExtracting.get()) {
                mLoadingText.setText(R.string.first_boot_tips2);
            }
        }, 10 * 1000);

        mRootView.postDelayed(() -> {
            if (mIsExtracting.get()) {
                mLoadingText.setText(R.string.first_boot_tips3);
            }
        }, 15 * 1000);
    }

    private void showBootingProcedure() {
        // mLoadingText.setText(R.string.booting_tips);
        mLoadingText.setVisibility(View.GONE);
        mBootLogView.setVisibility(View.VISIBLE);
        new Thread(() -> {

            if (true) {
                boolean success = false;
                try {
                    success = TwoyiStatusManager.getInstance().waitBoot(15, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }

                if (!success) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.boot_failed, Toast.LENGTH_SHORT).show());

                    finish();
                    System.exit(0);
                    return;
                }
            }

            runOnUiThread(() -> {
                mLoadingView.stopAnimation();
                mLoadingLayout.setVisibility(View.GONE);
            });
        }, "waiting-boot").start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            NavUtils.hideNavigation(getWindow());
        }

        // Update global visibility.
        TwoyiStatusManager.getInstance().updateVisibility(hasFocus);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Renderer.handleTouch(event);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // TODO: 2021/10/26 Add Volume control
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // super.onBackPressed();
        Renderer.sendKeycode(KeyEvent.KEYCODE_HOME);
    }

    private float getBestFps() {
        WindowManager windowManager = getWindowManager();
        Display defaultDisplay = windowManager.getDefaultDisplay();
        Display.Mode[] supportedModes = defaultDisplay.getSupportedModes();
        float fps = 60;
        for (Display.Mode supportedMode : supportedModes) {
            float refreshRate = supportedMode.getRefreshRate();
            if (refreshRate > fps) {
                // fps = refreshRate;
            }
        }

        Log.w(TAG, "current fps: " + fps);
        return fps;
    }

    private void showAndroid12Tips() {
        boolean showTips = AppKV.getBooleanConfig(this, AppKV.SHOW_ANDROID12_TIPS, true);

        if (!RomUtil.isAndroid12() || !showTips) {
            return;
        }

        UIHelper.getDialogBuilder(this)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(R.string.tips_for_android12)
                .setCancelable(false)
                .setPositiveButton(R.string.look_it, ((dialog, which) -> {
                    UIHelper.visitSite(this, "https://twoyi.app/guide/android-12.html");
                    dialog.dismiss();
                }))
                .setNegativeButton(R.string.donate_never_show, (dialog, which) -> {
                    AppKV.setBooleanConfig(this, AppKV.SHOW_ANDROID12_TIPS, false);
                    dialog.dismiss();
                })
                .show();
    }
}

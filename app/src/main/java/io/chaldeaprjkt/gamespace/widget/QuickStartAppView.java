/*
 * Copyright (C) 2023-2024 the risingOS Android Project           
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.widget;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.window.SplashScreen;

import androidx.annotation.Nullable;

import java.util.List;

import io.chaldeaprjkt.gamespace.R;

public class QuickStartAppView extends LinearLayout {

    private ImageView appIcon1;
    private ImageView appIcon2;
    private ImageView appIcon3;
    private ImageView appIcon4;
    private Context mContext;
    private SettingsContentObserver mObserver;
    private PackageManager mPackageManager;
    private ActivityOptions mActivityOptions;

    public QuickStartAppView(Context context) {
        super(context);
        init(context);
    }

    public QuickStartAppView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QuickStartAppView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        appIcon1 = findViewById(R.id.app_icon_1);
        appIcon2 = findViewById(R.id.app_icon_2);
        appIcon3 = findViewById(R.id.app_icon_3);
        appIcon4 = findViewById(R.id.app_icon_4);
        updateAppIcons();
    }

    private void init(Context context) {
        mContext = context;
        mObserver = new SettingsContentObserver(new Handler());
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor("quick_start_apps"), true, mObserver);
        mPackageManager = mContext.getPackageManager();
        mActivityOptions = ActivityOptions.makeBasic();
        mActivityOptions.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM);
    }

    private void updateAppIcons() {
        String appPackageNames = Settings.System.getString(mContext.getContentResolver(), "quick_start_apps");
        if (appPackageNames != null && !appPackageNames.isEmpty()) {
            String[] packages = appPackageNames.split(",");
            if (packages.length > 0) {
                setupAppIcons(packages);
                setVisibility(View.VISIBLE);
            } else {
                setVisibility(View.GONE);
            }
        } else {
            setVisibility(View.GONE);
        }
    }

    private void setupAppIcons(String[] packages) {
        hideAllIcons();
        for (int i = 0; i < packages.length; i++) {
            String packageName = packages[i];
            switch (i) {
                case 0:
                    setupAppIcon(appIcon1, packageName);
                    break;
                case 1:
                    setupAppIcon(appIcon2, packageName);
                    break;
                case 2:
                    setupAppIcon(appIcon3, packageName);
                    break;
                case 3:
                    setupAppIcon(appIcon4, packageName);
                    break;
                default:
                    break;
            }
        }
    }

    private void hideAllIcons() {
        appIcon1.setVisibility(GONE);
        appIcon2.setVisibility(GONE);
        appIcon3.setVisibility(GONE);
        appIcon4.setVisibility(GONE);
    }

    private void setupAppIcon(ImageView imageView, @Nullable String packageName) {
        if (imageView == null) return;
        if (packageName == null || packageName.isEmpty()) {
            imageView.setVisibility(GONE);
        } else {
            try {
                ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(packageName, 0);
                imageView.setImageDrawable(mContext.getPackageManager().getApplicationIcon(appInfo));
                imageView.setOnClickListener(v -> launchAppInFreeformMode(packageName));
                imageView.setVisibility(VISIBLE);
            } catch (PackageManager.NameNotFoundException e) {
                imageView.setVisibility(GONE);
            }
        }
    }

    private void launchAppInFreeformMode(String packageName) {
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        // force stop the app before launching in freeform to avoid ui glitches - follows legacy freeform behaviour
        if (am != null) {
            am.forceStopPackage(packageName);
        }
        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);
        int halfWidth = screenSize.x / 2;
        int halfHeight = screenSize.y / 2;
        Configuration configuration = mContext.getResources().getConfiguration();
        boolean isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        Rect launchBounds;
        if (isLandscape) {
            launchBounds = new Rect(0, 0, halfWidth, halfHeight);
        } else {
            int centerX = screenSize.x / 2;
            int centerY = screenSize.y / 2;
            int width = Math.min(screenSize.x, screenSize.y) * 3 / 4;
            int height = width;
            launchBounds = new Rect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2);
        }
        mActivityOptions.setLaunchBounds(launchBounds);
        mActivityOptions.setTaskAlwaysOnTop(true);
        mActivityOptions.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        mActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        mActivityOptions.setPendingIntentBackgroundActivityLaunchAllowedByPermission(true);
        try {
            Intent startAppIntent = mPackageManager.getLaunchIntentForPackage(packageName);
            if (startAppIntent != null) {
                mContext.startActivity(startAppIntent, mActivityOptions.toBundle());
            }
        } catch (Exception e) {}
    }

    private class SettingsContentObserver extends ContentObserver {
        SettingsContentObserver(Handler handler) {
            super(handler);
        }
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            updateAppIcons();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.getContentResolver().unregisterContentObserver(mObserver);
    }
}
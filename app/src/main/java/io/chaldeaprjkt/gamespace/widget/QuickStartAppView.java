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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.window.SplashScreen;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.chaldeaprjkt.gamespace.R;

public class QuickStartAppView extends LinearLayout {
    private RecyclerView recyclerView;
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
        recyclerView = findViewById(R.id.quick_start_app_list);
        updateAppIcons();
    }

    private void init(Context context) {
        mContext = context;
        mObserver = new SettingsContentObserver(new Handler());
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor("quick_start_apps"), true, mObserver);
        mPackageManager = mContext.getPackageManager();
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
        recyclerView.setHasFixedSize(true);//设置固定大小
        recyclerView.setItemAnimator(new DefaultItemAnimator());//设置默认动画
        LinearLayoutManager mLayoutManage = new LinearLayoutManager(mContext);
        mLayoutManage.setOrientation(RecyclerView.HORIZONTAL);//设置滚动方向，横向滚动
        recyclerView.setLayoutManager(mLayoutManage);
        recyclerView.setAdapter(new MyRecyclerViewAdapter(recyclerView, Arrays.asList(packages)));

        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    Math.max(v.getPaddingBottom(), bars.bottom));
            return insets;
        });
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
        try {
            PackageManager packageManager = mContext.getPackageManager();
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                String activityName = launchIntent.getComponent().getClassName();
                Intent freeformIntent = new Intent("com.libremobileos.freeform.START_FREEFORM")
                        .setPackage("com.libremobileos.freeform")
                        .putExtra("packageName", packageName)
                        .putExtra("activityName", activityName)
                        .putExtra("userId", UserHandle.myUserId());
                mContext.sendBroadcast(freeformIntent);
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

    public class MyRecyclerViewAdapter extends RecyclerView.Adapter<MyRecyclerViewAdapter.RecyclerHolder> {
        private Context mContext;
        private List<String> dataList = new ArrayList<>();

        public MyRecyclerViewAdapter(RecyclerView recyclerView) {
            this.mContext = recyclerView.getContext();
        }

        public MyRecyclerViewAdapter(RecyclerView recyclerView, List<String> dataList) {
            this.mContext = recyclerView.getContext();
            setData(dataList);
        }

        public void setData(List<String> dataList) {
            if (null != dataList) {
                this.dataList.clear();
                this.dataList.addAll(dataList);
                notifyDataSetChanged();
            }
        }

        @Override
        public RecyclerHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(mContext).inflate(R.layout.quick_start_app_item, parent, false);
            return new RecyclerHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerHolder holder, int position) {
            setupAppIcon(holder.imageView, dataList.get(position));
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        class RecyclerHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            private RecyclerHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.app_icon);
                imageView.setVisibility(GONE);
            }
        }
    }
}

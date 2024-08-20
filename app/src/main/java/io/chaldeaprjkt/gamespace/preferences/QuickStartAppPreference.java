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
package io.chaldeaprjkt.gamespace.preferences;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;

import java.util.ArrayList;
import java.util.List;

public class QuickStartAppPreference extends DialogPreference {

    private String[] appPackageNames;
    private String[] appNames;
    private Drawable[] appIcons;

    public QuickStartAppPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadInstalledUserApps(context);
    }

    private void loadInstalledUserApps(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<String> packageNames = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Drawable> icons = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                packageNames.add(app.packageName);
                names.add(pm.getApplicationLabel(app).toString());
                icons.add(pm.getApplicationIcon(app));
            }
        }
        appPackageNames = packageNames.toArray(new String[0]);
        appNames = names.toArray(new String[0]);
        appIcons = icons.toArray(new Drawable[0]);
        sortAppsAlphabetically();
    }

    private void sortAppsAlphabetically() {
        for (int i = 0; i < appNames.length - 1; i++) {
            for (int j = i + 1; j < appNames.length; j++) {
                if (appNames[i].compareToIgnoreCase(appNames[j]) > 0) {
                    swapApps(i, j);
                }
            }
        }
    }

    private void swapApps(int i, int j) {
        String tempName = appNames[i];
        Drawable tempIcon = appIcons[i];
        String tempPackage = appPackageNames[i];
        appNames[i] = appNames[j];
        appIcons[i] = appIcons[j];
        appPackageNames[i] = appPackageNames[j];
        appNames[j] = tempName;
        appIcons[j] = tempIcon;
        appPackageNames[j] = tempPackage;
    }

    public String[] getAppPackageNames() {
        return appPackageNames;
    }

    public String[] getAppNames() {
        return appNames;
    }

    public Drawable[] getAppIcons() {
        return appIcons;
    }

    public void saveValue(String value) {
        persistString(value);
    }
}
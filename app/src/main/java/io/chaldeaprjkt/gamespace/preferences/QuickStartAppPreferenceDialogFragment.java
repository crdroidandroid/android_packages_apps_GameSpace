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
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import io.chaldeaprjkt.gamespace.R;

public class QuickStartAppPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

    private Set<String> selectedPackages = new HashSet<>();

    public static QuickStartAppPreferenceDialogFragment newInstance(String key) {
        final QuickStartAppPreferenceDialogFragment fragment = new QuickStartAppPreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        QuickStartAppPreference preference = (QuickStartAppPreference) getPreference();
        Context context = getContext();
        if (preference == null || context == null) {
            return;
        }
        String savedApps = Settings.System.getString(context.getContentResolver(), "quick_start_apps");
        if (savedApps != null && !savedApps.isEmpty()) {
            String[] savedAppsArray = savedApps.split(",");
            for (String app : savedAppsArray) {
                selectedPackages.add(app);
            }
        }
        boolean[] checkedItems = new boolean[preference.getAppNames().length];
        String[] appPackageNames = preference.getAppPackageNames();
        for (int i = 0; i < appPackageNames.length; i++) {
            checkedItems[i] = selectedPackages.contains(appPackageNames[i]);
        }
        builder.setTitle(R.string.quick_start_apps_title)
                .setMultiChoiceItems(preference.getAppNames(), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String selectedApp = appPackageNames[which];
                        if (isChecked) {
                            selectedPackages.add(selectedApp);
                        } else {
                            selectedPackages.remove(selectedApp);
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveSelectedApps(context);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {}

    private void saveSelectedApps(Context context) {
        String[] selectedArray = selectedPackages.toArray(new String[0]);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedArray.length; i++) {
            sb.append(selectedArray[i]);

            if (i < selectedArray.length - 1) {
                sb.append(",");
            }
        }
        Settings.System.putString(context.getContentResolver(), "quick_start_apps", sb.toString());
        QuickStartAppPreference preference = (QuickStartAppPreference) getPreference();
        if (preference != null) {
            preference.saveValue(sb.toString());
        }
    }
}
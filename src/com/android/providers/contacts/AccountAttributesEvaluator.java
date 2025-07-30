/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.providers.contacts;


import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentService;
import android.content.Intent;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Settings.AccountAttributes;
import android.text.TextUtils;
import android.util.Log;

import com.android.providers.contacts.util.NeededForTesting;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


/**
 * Determines the attributes for a given account based on its type and system settings.
 */
public class AccountAttributesEvaluator {
    private static final String TAG = "AccountAttributesEvaluator";

    // Constants for finding and parsing contacts.xml
    private static final String SYNC_META_DATA = "android.content.SyncAdapter";
    private static final List<String> METADATA_CONTACTS_NAMES = Arrays.asList(
            "android.provider.ALTERNATE_CONTACTS_STRUCTURE",
            "android.provider.CONTACTS_STRUCTURE");

    private static final String CONTACTS_ACCOUNT_TYPE_TAG = "ContactsAccountType";
    private static final String CONTACTS_SOURCE_TAG = "ContactsSource";
    private static final String DATA_SET_TAG = "dataSet";
    private static final String CONTACTS_DATA_KIND_TAG = "ContactsDataKind";
    private static final String MIMETYPE_ATTRIBUTE_KEY = "mimeType";
    private static final String ANDROID_RES_APK_URL = "http://schemas.android.com/apk/res/android";

    private final PackageManager mPackageManager;
    private final ContactsDatabaseHelper mDbHelper;
    private final AccountManager mAccountManager;
    private final IContentService mContentService;

    public AccountAttributesEvaluator(Context context, ContactsDatabaseHelper dbHelper) {
        this(context.getPackageManager(), dbHelper, AccountManager.get(context),
                ContentResolver.getContentService());
    }

    @NeededForTesting
    AccountAttributesEvaluator(PackageManager packageManager, ContactsDatabaseHelper dbHelper,
            AccountManager accountManager, IContentService contentService) {
        this.mPackageManager = packageManager;
        this.mDbHelper = dbHelper;
        this.mAccountManager = accountManager;
        this.mContentService = contentService;
    }

    /**
     * Evaluates the account's attributes by inspecting its origin and sync adapter settings.
     *
     * @param accountWithDataSet The account to evaluate.
     * @return A {@code long} bitmask representing the combined account attributes.
     */
    public long evaluate(AccountWithDataSet accountWithDataSet) {
        return getDataOriginAttributes(accountWithDataSet) | getSyncModeAttributes(
                accountWithDataSet) | getDataTypeAttributes(accountWithDataSet);
    }

    private long getDataOriginAttributes(AccountWithDataSet accountWithDataSet) {
        if (accountWithDataSet.isLocalAccount()) {
            return AccountAttributes.ATTRIBUTE_DATA_ORIGIN_LOCAL;
        } else if (accountWithDataSet.inSimAccounts(mDbHelper.getAllSimAccounts())) {
            return AccountAttributes.ATTRIBUTE_DATA_ORIGIN_SIM;
        } else if (accountWithDataSet.inSystemAccounts(mAccountManager.getAccounts())) {
            return AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD;
        }
        return 0L;
    }

    private long getSyncModeAttributes(AccountWithDataSet accountWithDataSet) {
        // Local accounts do not have sync adapters.
        if (accountWithDataSet.isLocalAccount() || accountWithDataSet.getAccountType() == null) {
            return 0L;
        }

        try {
            SyncAdapterType[] syncs = mContentService.getSyncAdapterTypes();

            // Find the specific sync adapter registered for this account type.
            for (SyncAdapterType sync : syncs) {
                if (ContactsContract.AUTHORITY.equals(sync.authority)
                        && accountWithDataSet.getAccountType().equals(sync.accountType)) {

                    // Found the first matching adapter. Determine its attributes.
                    long attributes = AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC;
                    if (sync.supportsUploading()) {
                        attributes |= AccountAttributes.ATTRIBUTE_SYNC_MODE_UP_SYNC;
                    }
                    // Return immediately once the first match is found and evaluated.
                    return attributes;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Could not acquire sync adapter types; returning no sync attributes.", e);
        }

        return 0L;
    }


    /**
     * Determines if an account has custom data types defined in its contacts.xml.
     */
    private long getDataTypeAttributes(AccountWithDataSet accountWithDataSet) {
        if (accountWithDataSet.isLocalAccount() || accountWithDataSet.getAccountType() == null) {
            return 0L;
        }

        final String packageName = findAuthenticatorPackage(accountWithDataSet.getAccountType());
        if (packageName == null) {
            return 0L;
        }

        try (XmlResourceParser parser = loadContactsXmlParser(packageName)) {
            if (parser == null) {
                return 0L;
            }

            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Skipping comments and whitespace.
            }

            if (type != XmlPullParser.START_TAG || !isRecognizableRootTag(parser.getName())) {
                return 0L;
            }

            String xmlDataSet = parser.getAttributeValue(null, DATA_SET_TAG);
            if (!TextUtils.equals(xmlDataSet, accountWithDataSet.getDataSet())) {
                return 0L;
            }

            final int depth = parser.getDepth();
            while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && type != XmlPullParser.END_DOCUMENT) {

                if (type == XmlPullParser.START_TAG && parser.getName().equals(
                        CONTACTS_DATA_KIND_TAG)) {
                    String mimeType = parser.getAttributeValue(
                            ANDROID_RES_APK_URL, MIMETYPE_ATTRIBUTE_KEY);
                    if (!TextUtils.isEmpty(mimeType)) {
                        return AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED;
                    }
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to parse contacts.xml for package: " + packageName, e);
        }

        return 0L;
    }

    private static boolean isRecognizableRootTag(String rootTag) {
        return CONTACTS_ACCOUNT_TYPE_TAG.equals(rootTag) || CONTACTS_SOURCE_TAG.equals(rootTag);
    }


    /**
     * Finds and loads the contacts.xml metadata for a given package.
     */
    @Nullable
    private XmlResourceParser loadContactsXmlParser(String resPackageName) {
        final Intent intent = new Intent(SYNC_META_DATA).setPackage(resPackageName);
        final List<ResolveInfo> intentServices =
                mPackageManager.queryIntentServices(intent, PackageManager.GET_META_DATA);

        if (intentServices != null) {
            for (final ResolveInfo resolveInfo : intentServices) {
                final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
                if (serviceInfo == null || serviceInfo.metaData == null) {
                    continue;
                }
                for (String metadataName : METADATA_CONTACTS_NAMES) {
                    final XmlResourceParser parser = serviceInfo.loadXmlMetaData(mPackageManager,
                            metadataName);
                    if (parser != null) {
                        Log.d(TAG, "Metadata loaded from: " + serviceInfo.packageName + ", "
                                + serviceInfo.name + ", " + metadataName);
                        return parser;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the package name of the authenticator for a given account type.
     */
    @Nullable
    private String findAuthenticatorPackage(String accountType) {
        for (AuthenticatorDescription authenticator : mAccountManager.getAuthenticatorTypes()) {
            if (accountType.equals(authenticator.type)) {
                return authenticator.packageName;
            }
        }
        return null;
    }

}

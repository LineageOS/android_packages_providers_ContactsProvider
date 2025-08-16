/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.providers.contacts.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.accounts.AuthenticatorDescription;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.providers.contacts.util.LogUtils.AccountSyncMode;
import com.android.providers.contacts.util.LogUtils.CallerAccountTypeOwnership;
import com.android.providers.contacts.util.LogUtils.CallerType;
import com.android.providers.contacts.util.LogUtils.ResultType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LogFieldsBuilderTest {

    @Mock
    private PackageManager mPackageManager;

    private static final String TEST_ACCOUNT_TYPE = "com.example.account";
    private static final String TEST_PACKAGE_NAME = "com.example.package";
    private static final int TEST_UID = 12345;
    private static final int OTHER_UID = 54321;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void detectCallerAccountTypeOwnership_nullAccountType() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields().setUid(TEST_UID);
        final AuthenticatorDescription[] descs = new AuthenticatorDescription[]{};

        builder.detectCallerAccountTypeOwnership(mPackageManager, descs);

        assertEquals(CallerAccountTypeOwnership.UNSPECIFIED,
                builder.build().getCallerAccountTypeOwnership());
    }

    @Test
    public void detectCallerAccountTypeOwnership_notOwned_noMatchingAuthenticator() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType("other.account.type")
                .setUid(TEST_UID);
        final AuthenticatorDescription desc = new AuthenticatorDescription(TEST_ACCOUNT_TYPE,
                TEST_PACKAGE_NAME, 0, 0, 0, 0);
        final AuthenticatorDescription[] descs = new AuthenticatorDescription[]{desc};

        builder.detectCallerAccountTypeOwnership(mPackageManager, descs);

        assertEquals(CallerAccountTypeOwnership.NOT_OWNED,
                builder.build().getCallerAccountTypeOwnership());
    }

    @Test
    public void detectCallerAccountTypeOwnership_notOwned_uidMismatch() throws Exception {
        when(mPackageManager.getPackageUid(TEST_PACKAGE_NAME, 0)).thenReturn(OTHER_UID);

        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType(TEST_ACCOUNT_TYPE)
                .setUid(TEST_UID);
        final AuthenticatorDescription desc = new AuthenticatorDescription(TEST_ACCOUNT_TYPE,
                TEST_PACKAGE_NAME, 0, 0, 0, 0);
        final AuthenticatorDescription[] descs = new AuthenticatorDescription[]{desc};

        builder.detectCallerAccountTypeOwnership(mPackageManager, descs);

        assertEquals(CallerAccountTypeOwnership.NOT_OWNED,
                builder.build().getCallerAccountTypeOwnership());
    }

    @Test
    public void detectCallerAccountTypeOwnership_owned() throws Exception {
        when(mPackageManager.getPackageUid(TEST_PACKAGE_NAME, 0)).thenReturn(TEST_UID);

        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType(TEST_ACCOUNT_TYPE)
                .setUid(TEST_UID);
        final AuthenticatorDescription desc = new AuthenticatorDescription(TEST_ACCOUNT_TYPE,
                TEST_PACKAGE_NAME, 0, 0, 0, 0);
        final AuthenticatorDescription[] descs = new AuthenticatorDescription[]{desc};

        builder.detectCallerAccountTypeOwnership(mPackageManager, descs);

        assertEquals(CallerAccountTypeOwnership.OWNED,
                builder.build().getCallerAccountTypeOwnership());
    }

    @Test
    public void detectCallerAccountTypeOwnership_packageManagerException() throws Exception {
        when(mPackageManager.getPackageUid(TEST_PACKAGE_NAME, 0)).thenThrow(
                new PackageManager.NameNotFoundException());

        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType(TEST_ACCOUNT_TYPE)
                .setUid(TEST_UID);
        final AuthenticatorDescription desc = new AuthenticatorDescription(TEST_ACCOUNT_TYPE,
                TEST_PACKAGE_NAME, 0, 0, 0, 0);
        final AuthenticatorDescription[] descs = new AuthenticatorDescription[]{desc};

        builder.detectCallerAccountTypeOwnership(mPackageManager, descs);

        assertEquals(CallerAccountTypeOwnership.NOT_OWNED,
                builder.build().getCallerAccountTypeOwnership());
    }

    @Test
    public void detectAccountSyncMode_nullAccountType() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields();
        final SyncAdapterType[] types = new SyncAdapterType[]{};

        builder.detectAccountSyncMode(types);

        assertEquals(AccountSyncMode.UNSPECIFIED, builder.build().getAccountSyncMode());
    }

    @Test
    public void detectAccountSyncMode_noMatchingSyncAdapter() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType("other.account.type");
        final SyncAdapterType type = new SyncAdapterType(ContactsContract.AUTHORITY,
                TEST_ACCOUNT_TYPE, true, true);
        final SyncAdapterType[] types = new SyncAdapterType[]{type};

        builder.detectAccountSyncMode(types);

        assertEquals(AccountSyncMode.UNSPECIFIED, builder.build().getAccountSyncMode());
    }

    @Test
    public void detectAccountSyncMode_bidirectional() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType(TEST_ACCOUNT_TYPE);
        final SyncAdapterType type = new SyncAdapterType(ContactsContract.AUTHORITY,
                TEST_ACCOUNT_TYPE, true, true);
        final SyncAdapterType[] types = new SyncAdapterType[]{type};

        builder.detectAccountSyncMode(types);

        assertEquals(AccountSyncMode.BIDIRECTIONAL, builder.build().getAccountSyncMode());
    }



    @Test
    public void detectAccountSyncMode_downOnly() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType(TEST_ACCOUNT_TYPE);
        final SyncAdapterType type = new SyncAdapterType(ContactsContract.AUTHORITY,
                TEST_ACCOUNT_TYPE, true, false);
        final SyncAdapterType[] types = new SyncAdapterType[]{type};

        builder.detectAccountSyncMode(types);

        assertEquals(AccountSyncMode.DOWN_ONLY, builder.build().getAccountSyncMode());
    }

    @Test
    public void detectAccountSyncMode_authorityMismatch() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields()
                .setAccountType(TEST_ACCOUNT_TYPE);
        final SyncAdapterType type = new SyncAdapterType("other.authority",
                TEST_ACCOUNT_TYPE, true, true);
        final SyncAdapterType[] types = new SyncAdapterType[]{type};

        builder.detectAccountSyncMode(types);

        assertEquals(AccountSyncMode.UNSPECIFIED, builder.build().getAccountSyncMode());
    }

    @Test
    public void getResultType_success() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields();

        builder.setException(null);

        assertEquals(ResultType.SUCCESS, builder.build().getResultType());
    }

    @Test
    public void getResultType_illegalArgument() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields();

        builder.setException(new IllegalArgumentException("some message"));

        assertEquals(ResultType.ILLEGAL_ARGUMENT, builder.build().getResultType());
    }

    @Test
    public void getResultType_unsupportedOperation() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields();

        builder.setException(new UnsupportedOperationException());

        assertEquals(ResultType.UNSUPPORTED_OPERATION, builder.build().getResultType());
    }

    @Test
    public void getResultType_fail() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields();

        builder.setException(new RuntimeException());

        assertEquals(ResultType.FAIL, builder.build().getResultType());
    }

    @Test
    public void getCallerType_isSyncAdapter() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields();

        builder.setCallerIsSyncAdapter(true);

        assertEquals(CallerType.CALLER_IS_SYNC_ADAPTER, builder.build().getCallerType());
    }

    @Test
    public void getCallerType_isNotSyncAdapter() {
        final LogFields.Builder builder = LogFields.Builder.aLogFields();

        builder.setCallerIsSyncAdapter(false);

        assertEquals(CallerType.CALLER_IS_NOT_SYNC_ADAPTER, builder.build().getCallerType());
    }
}

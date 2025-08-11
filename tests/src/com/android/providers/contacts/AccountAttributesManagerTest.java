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

import static android.provider.ContactsContract.Settings.AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD;
import static android.provider.ContactsContract.Settings.AccountAttributes.ATTRIBUTE_DATA_ORIGIN_LOCAL;
import static android.provider.ContactsContract.Settings.AccountAttributes.ATTRIBUTE_DATA_ORIGIN_SIM;
import static android.provider.ContactsContract.Settings.AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.accounts.Account;
import android.provider.ContactsContract.SimAccount;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(JUnit4.class)
public class AccountAttributesManagerTest extends BaseContactsProvider2Test {

    private static final Account SYSTEM_ACCOUNT = new Account("user@android.com",
            "com.android.account");
    private static final Account SIM_ACCOUNT = new Account("12345", "com.android.sim");
    private static final Account UNKNOWN_ACCOUNT = new Account("user@unknown.com",
            "com.unknown.account");

    private static final AccountWithDataSet SYSTEM_ACCOUNT_WITH_DATA_SET =
            new AccountWithDataSet(SYSTEM_ACCOUNT.name, SYSTEM_ACCOUNT.type, null);
    private static final AccountWithDataSet SIM_ACCOUNT_WITH_DATA_SET =
            new AccountWithDataSet(SIM_ACCOUNT.name, SIM_ACCOUNT.type, null);
    private static final AccountWithDataSet UNKNOWN_ACCOUNT_WITH_DATA_SET =
            new AccountWithDataSet(UNKNOWN_ACCOUNT.name, UNKNOWN_ACCOUNT.type, null);

    @Mock
    private AccountAttributesEvaluator mMockEvaluator;

    private ContactsDatabaseHelper mDbHelper;
    private AccountAttributesManager mManager;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        mDbHelper = getContactsProvider().getDatabaseHelper();
        mManager = new AccountAttributesManager(mDbHelper, mMockEvaluator);
        mDbHelper.createSimAccountIdInTransaction(SIM_ACCOUNT_WITH_DATA_SET, 1,
                SimAccount.ADN_EF_TYPE);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetAccountAttributes_firstTime_evaluatesAndStoresAttributes() {
        // Arrange: The evaluator will return a specific attribute set
        when(mMockEvaluator.evaluate(SYSTEM_ACCOUNT_WITH_DATA_SET)).thenReturn(
                ATTRIBUTE_DATA_ORIGIN_CLOUD | ATTRIBUTE_SYNC_MODE_DOWN_SYNC);

        // Act: Get attributes for the first time
        long attributes = mManager.getAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET,
                new Account[]{SYSTEM_ACCOUNT});

        // Assert: The manager returns the evaluated attributes
        assertEquals(ATTRIBUTE_DATA_ORIGIN_CLOUD | ATTRIBUTE_SYNC_MODE_DOWN_SYNC, attributes);
        // Assert: The evaluator was called
        verify(mMockEvaluator, times(1)).evaluate(SYSTEM_ACCOUNT_WITH_DATA_SET);
        // Assert: The attributes were stored in the database
        long storedAttrs = mDbHelper.getAccountAttributes(
                SYSTEM_ACCOUNT_WITH_DATA_SET.getAccountName(),
                SYSTEM_ACCOUNT_WITH_DATA_SET.getAccountType(),
                SYSTEM_ACCOUNT_WITH_DATA_SET.getDataSet());
        assertEquals(attributes, storedAttrs);
    }

    @Test
    public void testGetAccountAttributes_attributesExistButStale_reEvaluates() {
        // Arrange
        when(mMockEvaluator.evaluate(SYSTEM_ACCOUNT_WITH_DATA_SET)).thenReturn(
                ATTRIBUTE_DATA_ORIGIN_LOCAL);
        long attributes = mManager.getAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET,
                new Account[]{SYSTEM_ACCOUNT});
        assertEquals(ATTRIBUTE_DATA_ORIGIN_LOCAL, attributes);

        mManager.setAccountAttributesUpdateRateLimit(0L);

        when(mMockEvaluator.evaluate(SYSTEM_ACCOUNT_WITH_DATA_SET)).thenReturn(
                ATTRIBUTE_DATA_ORIGIN_CLOUD);

        // Act: Get attributes again. This should now trigger a re-evaluation.
        attributes = mManager.getAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET,
                new Account[]{SYSTEM_ACCOUNT});

        // Assert: The new, re-evaluated attributes are returned.
        assertEquals(ATTRIBUTE_DATA_ORIGIN_CLOUD, attributes);
        // Assert: The evaluator was called twice in total (once for priming, once for
        // re-evaluation).
        verify(mMockEvaluator, times(2)).evaluate(SYSTEM_ACCOUNT_WITH_DATA_SET);
    }

    @Test
    public void testGetAccountAttributes_calledTwiceWithinRateLimit_evaluatesOnlyOnce() {
        // Arrange
        when(mMockEvaluator.evaluate(SYSTEM_ACCOUNT_WITH_DATA_SET)).thenReturn(
                ATTRIBUTE_DATA_ORIGIN_CLOUD);

        // Act: Call twice in a row
        mManager.getAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET, new Account[]{SYSTEM_ACCOUNT});
        mManager.getAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET, new Account[]{SYSTEM_ACCOUNT});

        // Assert: The expensive evaluation method is only called the first time
        verify(mMockEvaluator, times(1)).evaluate(SYSTEM_ACCOUNT_WITH_DATA_SET);
    }

    @Test
    public void testGetAccountAttributes_forSimAccount_succeeds() {
        // Arrange: SIM account is already set up in @Before
        when(mMockEvaluator.evaluate(SIM_ACCOUNT_WITH_DATA_SET)).thenReturn(
                ATTRIBUTE_DATA_ORIGIN_SIM);

        // Act
        long attributes = mManager.getAccountAttributes(SIM_ACCOUNT_WITH_DATA_SET,
                new Account[]{SYSTEM_ACCOUNT});

        // Assert
        assertEquals(ATTRIBUTE_DATA_ORIGIN_SIM, attributes);
    }

    @Test
    public void testGetAccountAttributes_forInvalidAccount_throwsException() {
        // Assert that an exception is thrown for an account that is not a system or SIM account
        assertThrows(IllegalArgumentException.class, () -> {
            mManager.getAccountAttributes(UNKNOWN_ACCOUNT_WITH_DATA_SET,
                    new Account[]{SYSTEM_ACCOUNT});
        });
    }

    @Test
    public void testUpdateAccountAttributes_validUpdate_storesAttributes() {
        // Act: Update attributes manually
        long newAttributes = ATTRIBUTE_DATA_ORIGIN_CLOUD | ATTRIBUTE_SYNC_MODE_DOWN_SYNC;
        mManager.updateAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET, newAttributes,
                new Account[]{SYSTEM_ACCOUNT});

        // Assert: The new attributes are correctly stored in the database
        long storedAttrs = mDbHelper.getAccountAttributes(
                SYSTEM_ACCOUNT_WITH_DATA_SET.getAccountName(),
                SYSTEM_ACCOUNT_WITH_DATA_SET.getAccountType(),
                SYSTEM_ACCOUNT_WITH_DATA_SET.getDataSet());
        assertEquals(newAttributes, storedAttrs);
    }

    @Test
    public void testUpdateAccountAttributes_withUndefinedBit_throwsException() {
        // Arrange: Create attributes with a bit that is not defined in the contract
        long undefinedAttributes = 1L << 50;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            mManager.updateAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET, undefinedAttributes,
                    new Account[]{SYSTEM_ACCOUNT});
        });
    }

    @Test
    public void testUpdateAccountAttributes_withConflictingOriginBits_throwsException() {
        // Arrange: Create attributes with two DATA_ORIGIN bits set
        long conflictingAttributes = ATTRIBUTE_DATA_ORIGIN_LOCAL | ATTRIBUTE_DATA_ORIGIN_SIM;

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            mManager.updateAccountAttributes(SYSTEM_ACCOUNT_WITH_DATA_SET, conflictingAttributes,
                    new Account[]{SYSTEM_ACCOUNT});
        });
    }

    @Test
    public void testUpdateAccountAttributes_forInvalidAccount_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            mManager.updateAccountAttributes(UNKNOWN_ACCOUNT_WITH_DATA_SET,
                    ATTRIBUTE_DATA_ORIGIN_CLOUD,
                    new Account[]{SYSTEM_ACCOUNT});
        });
    }
}

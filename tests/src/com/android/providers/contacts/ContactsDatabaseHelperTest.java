/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.accounts.Account;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Settings;
import android.test.MoreAsserts;
import android.text.TextUtils;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import com.android.providers.contacts.ContactsDatabaseHelper.MimetypesColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.RawContactsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;

import com.google.android.collect.Sets;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
public class ContactsDatabaseHelperTest extends BaseContactsProvider2Test {
    private static final String TAG = "ContactsDHT";

    private ContactsDatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDbHelper = getContactsProvider().getDatabaseHelper();
        mDb = mDbHelper.getWritableDatabase();
    }

    public void testGetOrCreateAccountId() {
        final AccountWithDataSet a1 = null;
        final AccountWithDataSet a2 = new AccountWithDataSet("a", null, null);
        final AccountWithDataSet a3 = new AccountWithDataSet(null, "b", null);
        final AccountWithDataSet a4 = new AccountWithDataSet(null, null, "c");
        final AccountWithDataSet a5 = new AccountWithDataSet("a", "b", "c");

        // First, there's no accounts.  getAccountIdOrNull() always returns null.
        assertNull(mDbHelper.getAccountIdOrNull(a1));
        assertNull(mDbHelper.getAccountIdOrNull(a2));
        assertNull(mDbHelper.getAccountIdOrNull(a3));
        assertNull(mDbHelper.getAccountIdOrNull(a4));
        assertNull(mDbHelper.getAccountIdOrNull(a5));

        // getOrCreateAccountId should create accounts.
        final long a1id = mDbHelper.getOrCreateAccountIdInTransaction(a1);
        final long a2id = mDbHelper.getOrCreateAccountIdInTransaction(a2);
        final long a3id = mDbHelper.getOrCreateAccountIdInTransaction(a3);
        final long a4id = mDbHelper.getOrCreateAccountIdInTransaction(a4);
        final long a5id = mDbHelper.getOrCreateAccountIdInTransaction(a5);

        // The IDs should be all positive and unique.
        assertTrue(a1id > 0);
        assertTrue(a2id > 0);
        assertTrue(a3id > 0);
        assertTrue(a4id > 0);
        assertTrue(a5id > 0);

        final Set<Long> ids = Sets.newHashSet();
        ids.add(a1id);
        ids.add(a2id);
        ids.add(a3id);
        ids.add(a4id);
        ids.add(a5id);
        assertEquals(5, ids.size());

        // Second call: This time getOrCreateAccountId will return the existing IDs.
        assertEquals(a1id, mDbHelper.getOrCreateAccountIdInTransaction(a1));
        assertEquals(a2id, mDbHelper.getOrCreateAccountIdInTransaction(a2));
        assertEquals(a3id, mDbHelper.getOrCreateAccountIdInTransaction(a3));
        assertEquals(a4id, mDbHelper.getOrCreateAccountIdInTransaction(a4));
        assertEquals(a5id, mDbHelper.getOrCreateAccountIdInTransaction(a5));

        // Now getAccountIdOrNull() returns IDs too.
        assertEquals((Long) a1id, mDbHelper.getAccountIdOrNull(a1));
        assertEquals((Long) a2id, mDbHelper.getAccountIdOrNull(a2));
        assertEquals((Long) a3id, mDbHelper.getAccountIdOrNull(a3));
        assertEquals((Long) a4id, mDbHelper.getAccountIdOrNull(a4));
        assertEquals((Long) a5id, mDbHelper.getAccountIdOrNull(a5));

        // null and AccountWithDataSet.NULL should be treated as the same thing.
        assertEquals(a1id, mDbHelper.getOrCreateAccountIdInTransaction(AccountWithDataSet.LOCAL));
        assertEquals((Long) a1id, mDbHelper.getAccountIdOrNull(AccountWithDataSet.LOCAL));

        // Remove all accounts.
        mDbHelper.getWritableDatabase().execSQL("delete from " + Tables.ACCOUNTS);

        assertNull(mDbHelper.getAccountIdOrNull(AccountWithDataSet.LOCAL));
        assertNull(mDbHelper.getAccountIdOrNull(a1));
        assertNull(mDbHelper.getAccountIdOrNull(a2));
        assertNull(mDbHelper.getAccountIdOrNull(a3));
        assertNull(mDbHelper.getAccountIdOrNull(a4));
        assertNull(mDbHelper.getAccountIdOrNull(a5));

        // Logically same as a5, but physically different object.
        final AccountWithDataSet a5b = new AccountWithDataSet("a", "b", "c");
        // a5 and a5b should have the same ID.
        assertEquals(
                mDbHelper.getOrCreateAccountIdInTransaction(a5),
                mDbHelper.getOrCreateAccountIdInTransaction(a5b));
    }

    /**
     * Test for {@link ContactsDatabaseHelper#queryIdWithOneArg} and
     * {@link ContactsDatabaseHelper#insertWithOneArgAndReturnId}.
     */
    public void testQueryIdWithOneArg_insertWithOneArgAndReturnId() {
        final String query =
                "SELECT " + MimetypesColumns._ID +
                        " FROM " + Tables.MIMETYPES +
                        " WHERE " + MimetypesColumns.MIMETYPE + "=?";

        final String insert =
                "INSERT INTO " + Tables.MIMETYPES + "("
                        + MimetypesColumns.MIMETYPE +
                        ") VALUES (?)";

        // First, the table is empty.
        assertEquals(-1, ContactsDatabaseHelper.queryIdWithOneArg(mDb, query, "value1"));
        assertEquals(-1, ContactsDatabaseHelper.queryIdWithOneArg(mDb, query, "value2"));

        // Insert one value.
        final long id1 = ContactsDatabaseHelper.insertWithOneArgAndReturnId(mDb, insert, "value1");
        MoreAsserts.assertNotEqual(-1, id1);

        assertEquals(id1, ContactsDatabaseHelper.queryIdWithOneArg(mDb, query, "value1"));
        assertEquals(-1, ContactsDatabaseHelper.queryIdWithOneArg(mDb, query, "value2"));


        // Insert one value.
        final long id2 = ContactsDatabaseHelper.insertWithOneArgAndReturnId(mDb, insert, "value2");
        MoreAsserts.assertNotEqual(-1, id2);

        assertEquals(id1, ContactsDatabaseHelper.queryIdWithOneArg(mDb, query, "value1"));
        assertEquals(id2, ContactsDatabaseHelper.queryIdWithOneArg(mDb, query, "value2"));

        // Insert the same value and cause a conflict.
        assertEquals(-1, ContactsDatabaseHelper.insertWithOneArgAndReturnId(mDb, insert, "value2"));
    }

    /**
     * Test for {@link ContactsDatabaseHelper#getPackageId(String)}
     */
    public void testGetPackageId() {
        // Test for getPackageId.
        final long packageId1 = mDbHelper.getPackageId("value1");
        final long packageId2 = mDbHelper.getPackageId("value2");
        final long packageId3 = mDbHelper.getPackageId("value3");

        // Make sure they're all different.
        final HashSet<Long> set = new HashSet<>();
        set.add(packageId1);
        set.add(packageId2);
        set.add(packageId3);
        assertEquals(3, set.size());

        // Make sure that repeated calls return the same value
        assertEquals(packageId1, mDbHelper.getPackageId("value1"));
    }

    /**
     * Test for {@link ContactsDatabaseHelper#getMimeTypeId(String)}
     */
    public void testGetMimeTypeId() {
        // Test for getMimeTypeId.
        final long mimetypeId1 = mDbHelper.getMimeTypeId("value1");
        final long mimetypeId2 = mDbHelper.getMimeTypeId("value2");
        final long mimetypeId3 = mDbHelper.getMimeTypeId("value3");

        // Make sure they're all different.
        final HashSet<Long> set = new HashSet<>();
        set.clear();
        set.add(mimetypeId1);
        set.add(mimetypeId2);
        set.add(mimetypeId3);
        assertEquals(3, set.size());

        // Make sure repeated calls return the same value
        assertEquals(mimetypeId1, mDbHelper.getMimeTypeId("value1"));
    }

    /**
     * Test for cache {@link ContactsDatabaseHelper#mCommonMimeTypeIdsCache} which stores ids for
     * common mime types for faster access.
     */
    public void testGetCommonMimeTypeIds() {
        // getMimeTypeId should return the same value as the value stored in the cache
        for (String commonMimeType : ContactsDatabaseHelper.COMMON_MIME_TYPES) {
            assertEquals(mDbHelper.mCommonMimeTypeIdsCache.get(commonMimeType).longValue(),
                    mDbHelper.getMimeTypeId(commonMimeType));
        }

        // The ids should be available even after deleting them from the table
        mDb.execSQL("DELETE FROM " + Tables.MIMETYPES + ";");

        for (String commonMimeType : ContactsDatabaseHelper.COMMON_MIME_TYPES) {
            assertEquals(mDbHelper.mCommonMimeTypeIdsCache.get(commonMimeType).longValue(),
                    mDbHelper.getMimeTypeId(commonMimeType));
        }
    }

    /**
     * Try to cause conflicts in getMimeTypeId() by calling it from multiple threads with
     * the current time as the argument and make sure it won't crash.
     *
     * We don't know from the test if there have actually been conflits, but if you look at
     * logcat you'll see a lot of conflict warnings.
     */
    @LargeTest
    public void testGetMimeTypeId_conflict() {

        final int NUM_THREADS = 4;
        final int DURATION_SECONDS = 5;

        final long finishTime = System.currentTimeMillis() + DURATION_SECONDS * 1000;

        final Runnable r = new Runnable() {
            @Override
            public void run() {
                for (;;) {
                    final long now = System.currentTimeMillis();
                    if (now >= finishTime) {
                        return;
                    }
                    assertTrue(mDbHelper.getMimeTypeId(String.valueOf(now)) > 0);
                }
            }
        };
        final Thread[] threads = new Thread[NUM_THREADS];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(r);
            threads[i].setDaemon(true);
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ignore) {
            }
        }
    }

    public void testUpgradeHashId() {
        // Create an account.
        final long accountId = mDbHelper.getOrCreateAccountIdInTransaction(
                AccountWithDataSet.LOCAL);
        // Create a raw contact.
        ContentValues rawContactValues = new ContentValues();
        rawContactValues.put(ContactsDatabaseHelper.RawContactsColumns.ACCOUNT_ID, accountId);
        final long rawContactId = mDb.insert(Tables.RAW_CONTACTS,null, rawContactValues);
        assertTrue(rawContactId > 0);
        // Create data for the raw contact Id.
        final StringBuilder data1 = new StringBuilder();
        for (int i = 0; i < 2048; i++) {
            data1.append("L");
        }
        final String dataString = data1.toString();
        final String hashId = mDbHelper.generateHashId(dataString, null);
        final int mimeType = 1;
        final ContentValues values = new ContentValues();
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsDatabaseHelper.DataColumns.MIMETYPE_ID, mimeType);
        values.put(ContactsContract.Data.DATA1, dataString);
        for (int i = 0; i < 2048; i++) {
            assertTrue(mDb.insert(Tables.DATA, null, values) > 0);
        }
        mDbHelper.upgradeToVersion1101(mDb);
        final Cursor c = mDb.query(Tables.DATA, new String[]{ContactsContract.Data.HASH_ID},
                null, null, null, null, null);
        try {
            assertEquals(2048, c.getCount());
            while (c.moveToNext()) {
                final String expectedHashId = c.getString(0);
                assertEquals(expectedHashId, hashId);
            }
        } finally {
            c.close();
        }
    }

    public void testUpgradeHashIdForPhoto() {
        // Create an account.
        final long accountId = mDbHelper.getOrCreateAccountIdInTransaction(
                AccountWithDataSet.LOCAL);
        // Create a raw contact.
        ContentValues rawContactValues = new ContentValues();
        rawContactValues.put(ContactsDatabaseHelper.RawContactsColumns.ACCOUNT_ID, accountId);
        final long rawContactId = mDb.insert(Tables.RAW_CONTACTS,null, rawContactValues);
        assertTrue(rawContactId > 0);

        // Create data for the raw contact Id.
        final long mimeType = mDbHelper.getMimeTypeId(
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
        final String photoHashId = mDbHelper.getPhotoHashId();
        final ContentValues values = new ContentValues();
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsDatabaseHelper.DataColumns.MIMETYPE_ID, mimeType);
        for (int i = 0; i < 2048; i++) {
            assertTrue(mDb.insert(Tables.DATA, null, values) > 0);
        }
        mDbHelper.upgradeToVersion1110(mDb);
        final Cursor c = mDb.query(Tables.DATA, new String[]{ContactsContract.Data.HASH_ID},
                null, null, null, null, null);
        try {
            assertEquals(2048, c.getCount());
            while (c.moveToNext()) {
                final String actualHashId = c.getString(0);
                assertEquals(photoHashId, actualHashId);
            }
        } finally {
            c.close();
        }
    }

    public void testUpgradeToVersion111_SetPrimaryPhonebookBucketToNumberBucket() {
        // Zero primary phone book bucket and null primary sort key
        final ContentValues contentValues = new ContentValues();
        contentValues.put(RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY, 0);
        mDb.insert(Tables.RAW_CONTACTS, null, contentValues);

        mDbHelper.upgradeToVersion1111(mDb);

        // Assert that the primary phone book bucket/label has been set to the number bucket/label
        final ContactLocaleUtils localeUtils = ContactLocaleUtils.getInstance();
        final int numberBucket = localeUtils.getNumberBucketIndex();
        final String numberLabel = localeUtils.getBucketLabel(numberBucket);
        assertUpgradeToVersion1111(numberBucket, numberLabel,
                RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY,
                RawContactsColumns.PHONEBOOK_LABEL_PRIMARY);
    }

    public void testUpgradeToVersion111_SetAltPhonebookBucketToNumberBucket() {
        // Zero alt phone book bucket and null alt sort key
        final ContentValues contentValues = new ContentValues();
        contentValues.put(RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE, 0);
        mDb.insert(Tables.RAW_CONTACTS, null, contentValues);

        mDbHelper.upgradeToVersion1111(mDb);

        // Assert that the alt phone book bucket/label has been set to the number bucket/label
        final ContactLocaleUtils localeUtils = ContactLocaleUtils.getInstance();
        final int numberBucket = localeUtils.getNumberBucketIndex();
        final String numberLabel = localeUtils.getBucketLabel(numberBucket);
        assertUpgradeToVersion1111(numberBucket, numberLabel,
                RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE);
    }

    public void testUpgradeToVersion111_NonZeroPrimaryPhonebookBucket() {
        // Non-zero primary phone book bucket
        final int primaryBucket = 1;
        final ContentValues contentValues = new ContentValues();
        contentValues.put(RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY, primaryBucket);
        mDb.insert(Tables.RAW_CONTACTS, null, contentValues);

        mDbHelper.upgradeToVersion1111(mDb);

        // Assert that the primary phone book bucket/label is unchanged
        assertUpgradeToVersion1111(primaryBucket, null, RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY,
                RawContactsColumns.PHONEBOOK_LABEL_PRIMARY);
    }

    public void testUpgradeToVersion111_NonNullPrimarySortKey() {
        // Non-null primary sort key
        final ContentValues contentValues = new ContentValues();
        contentValues.put(RawContacts.SORT_KEY_PRIMARY, "sort_key_primary");
        mDb.insert(Tables.RAW_CONTACTS, null, contentValues);

        mDbHelper.upgradeToVersion1111(mDb);

        // Assert that the primary phone book bucket/label is unchanged
        assertUpgradeToVersion1111(0, null, RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY,
                RawContactsColumns.PHONEBOOK_LABEL_PRIMARY);
    }

    public void testUpgradeToVersion111_NonZeroAltPhonebookBucket() {
        // Non-zero alt phone book bucket
        final int altBucket = 1;
        final ContentValues contentValues = new ContentValues();
        contentValues.put(RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE, altBucket);
        mDb.insert(Tables.RAW_CONTACTS, null, contentValues);

        mDbHelper.upgradeToVersion1111(mDb);

        // Assert that the alt phone book bucket/label is unchanged
        assertUpgradeToVersion1111(altBucket, null, RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE);
    }

    public void testUpgradeToVersion111_NonNullAltSortKeyToNumber() {
        // Non-null alt sort key
        final ContentValues contentValues = new ContentValues();
        contentValues.put(RawContacts.SORT_KEY_ALTERNATIVE, "sort_key_alt");
        mDb.insert(Tables.RAW_CONTACTS, null, contentValues);

        mDbHelper.upgradeToVersion1111(mDb);

        // Assert that the alt phone book bucket/label is unchanged
        assertUpgradeToVersion1111(0, null, RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE);
    }

    private void assertUpgradeToVersion1111(int expectedBucket, String expectedLabel,
            String bucketColumn, String labelColumn) {
        final Cursor cursor = mDb.query(Tables.RAW_CONTACTS,
                new String[]{bucketColumn, labelColumn}, null, null, null, null, null);
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToNext());
            assertEquals(expectedBucket, cursor.getInt(0));
            assertEquals(expectedLabel, cursor.getString(1));
        } finally {
            cursor.close();
        }
    }

    private Integer getIntegerFromExpression(String expression) {
        try (Cursor c = mDb.rawQuery("SELECT " + expression, null)) {
            assertTrue(c.moveToPosition(0));
            if (c.isNull(0)) {
                return null;
            }
            return c.getInt(0);
        }
    }

    public void testNotifyProviderStatusChange() throws Exception {
        final AtomicReference<Uri> calledUri = new AtomicReference<>();

        final Handler h = new Handler(Looper.getMainLooper());

        final CountDownLatch latch = new CountDownLatch(1);

        final ContentObserver observer = new ContentObserver(h) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                calledUri.set(uri);
                latch.countDown();
            }
        };

        // Notify on ProviderStatus.CONTENT_URI.
        getContext().getContentResolver().registerContentObserver(
                ProviderStatus.CONTENT_URI,
                /* notifyForDescendants= */ false, observer);

        // This should trigger it.
        calledUri.set(null);
        ContactsDatabaseHelper.notifyProviderStatusChange(getContext());

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(ProviderStatus.CONTENT_URI, calledUri.get());
    }

    public void testOpenTimestamp() {
        final long startTime = System.currentTimeMillis();

        final String dbFilename = "testOpenTimestamp.db";

        getContext().deleteDatabase(dbFilename);

        final ContactsDatabaseHelper dbHelper = ContactsDatabaseHelper.getNewInstanceForTest(
                mContext, dbFilename);

        dbHelper.getReadableDatabase(); // Open the DB.

        final long creationTime = dbHelper.getDatabaseCreationTime();

        assertTrue("Expected " + creationTime + " >= " + startTime, creationTime >= startTime);

        dbHelper.close();

        // Open again.
        final ContactsDatabaseHelper dbHelper2 = ContactsDatabaseHelper.getNewInstanceForTest(
                mContext, dbFilename);

        dbHelper2.getReadableDatabase(); // Open the DB.

        assertEquals(creationTime, dbHelper2.getDatabaseCreationTime());
    }

    public void testGetAndSetDefaultAccount() {
        // Test: Initially, no default account exists
        Account[] accounts = mDbHelper.getDefaultAccountIfAny();
        assertEquals(0, accounts.length); // Check for empty array

        // Test: Setting and getting valid default account
        mDbHelper.setDefaultAccount("a", "b");
        accounts = mDbHelper.getDefaultAccountIfAny();
        assertEquals(1, accounts.length);
        assertEquals("a", accounts[0].name);
        assertEquals("b", accounts[0].type);

        mDbHelper.setDefaultAccount("c", "d");
        accounts = mDbHelper.getDefaultAccountIfAny();
        assertEquals(1, accounts.length);
        assertEquals("c", accounts[0].name);
        assertEquals("d", accounts[0].type);

        // Test: set the default account to NULL.
        mDbHelper.setDefaultAccount(null, null);
        accounts = mDbHelper.getDefaultAccountIfAny();
        assertEquals(1, accounts.length);
        assertNull(accounts[0]);

        // Test: Invalid account (non-null name, null type)
        try {
            mDbHelper.setDefaultAccount("name", null);
            fail("Setting default account to an invalid account should fail.");
        } catch (IllegalArgumentException e) {
            // Expected exception
        }
        accounts = mDbHelper.getDefaultAccountIfAny();
        assertEquals(1, accounts.length);
        assertNull(accounts[0]);

        // Test: Update default account to an existing account
        mDbHelper.setDefaultAccount("a", "b");
        accounts = mDbHelper.getDefaultAccountIfAny();
        assertEquals(1, accounts.length);
        assertEquals("a", accounts[0].name);
        assertEquals("b", accounts[0].type);

        // Test: Unset the default account.
        ContentValues values = new ContentValues();
        values.put(ContactsDatabaseHelper.AccountsColumns.IS_DEFAULT, 0);
        mDb.update(Tables.ACCOUNTS, values, null, null);

        accounts = mDbHelper.getDefaultAccountIfAny();
        assertEquals(0, accounts.length); // Check for empty array

        // Test: Verify total accounts in the database (including added defaults)
        try (Cursor cursor = mDbHelper.getReadableDatabase().query(Tables.ACCOUNTS, new String[]{
                ContactsDatabaseHelper.AccountsColumns.ACCOUNT_NAME,
                ContactsDatabaseHelper.AccountsColumns.ACCOUNT_TYPE
        }, null, null, null, null, null)) {
            assertEquals(3, cursor.getCount());
        }
    }

    // Test method for get and update account attributes
    public void testGetAndUpdateAccountCapabilities() {
        final String accountName1 = "testAccount1";
        final String accountType1 = "testType1";
        final String dataSet1 = "testDataSet1";
        final long attributes1 = Settings.AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD;

        final String accountName2 = "testAccount2";
        final String accountType2 = "testType2";
        final String dataSet2 = null; // No dataSet
        final long attributes2 =
                Settings.AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED;

        // Set and get a cloud account attributes with a dataSet
        mDbHelper.setAccountAttributes(accountName1, accountType1, dataSet1, attributes1, true);
        long fetchedCapabilities1 = mDbHelper.getAccountAttributes(accountName1, accountType1,
                dataSet1);
        assertEquals(attributes1, fetchedCapabilities1);

        // Set and get a cloud account attributes without a dataSet
        mDbHelper.setAccountAttributes(accountName2, accountType2, dataSet2, attributes2, true);
        long fetchedCapabilities2 = mDbHelper.getAccountAttributes(accountName2, accountType2,
                dataSet2);
        assertEquals(attributes2, fetchedCapabilities2);

        // Update an existing account's attributes
        final long updatedCapabilities1 =
                Settings.AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC;
        mDbHelper.setAccountAttributes(accountName1, accountType1, dataSet1, updatedCapabilities1,
                true);
        long reFetchedCapabilities1 = mDbHelper.getAccountAttributes(accountName1, accountType1,
                dataSet1);
        assertEquals(updatedCapabilities1, reFetchedCapabilities1);

        // Verify the attributes remains unchanged after the failed attempt
        assertEquals(updatedCapabilities1,
                mDbHelper.getAccountAttributes(accountName1, accountType1, dataSet1).longValue());

        // Invalid arguments for setCloudAccountCapabilities (accountName non-null,
        // accountType null)
        try {
            mDbHelper.setAccountAttributes("someName", null, null, attributes1, true);
            fail("Setting account attributes with null accountType (and non-null name) should "
                    + "fail.");
        } catch (IllegalArgumentException e) {
            // Expected exception
        }

        // Invalid arguments for setCloudAccountCapabilities (accountName null, dataSet
        // non-null)
        try {
            mDbHelper.setAccountAttributes(null, "someType", "someDataSet", attributes1, true);
            fail("Setting account attributes with null accountName (and non-null dataSet) should "
                    + "fail.");
        } catch (IllegalArgumentException e) {
            // Expected exception
        }

        // Get attributes for a non-existent account
        Long nonExistentCapabilities = mDbHelper.getAccountAttributes("nonExistent",
                "nonExistent", null);
        assertNull(nonExistentCapabilities);

        // Verify total accounts in the database after adding categories
        try (Cursor cursor = mDbHelper.getReadableDatabase().query(Tables.ACCOUNTS, new String[]{
                ContactsDatabaseHelper.AccountsColumns.ACCOUNT_NAME,
                ContactsDatabaseHelper.AccountsColumns.ACCOUNT_TYPE,
                ContactsDatabaseHelper.AccountsColumns.DATA_SET,
                ContactsDatabaseHelper.AccountsColumns.ACCOUNT_ATTRIBUTES
        }, null, null, null, null, null)) {
            // Should have 2 accounts added in this test, plus any from
            // testGetAndSetDefaultAccount if run together
            // For isolated testing, this would be 2.
            // If running tests in isolation, make sure to clear the database between tests.
            // For demonstration, let's assume this is the only test modifying accounts table.
            assertEquals(2, cursor.getCount());

            // You can also iterate and verify each row's data
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsDatabaseHelper.AccountsColumns.ACCOUNT_NAME));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsDatabaseHelper.AccountsColumns.ACCOUNT_TYPE));
                String dataSet = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsDatabaseHelper.AccountsColumns.DATA_SET));
                int accountCapabilities = cursor.getInt(cursor.getColumnIndexOrThrow(
                        ContactsDatabaseHelper.AccountsColumns.ACCOUNT_ATTRIBUTES));

                if (accountName1.equals(name) && accountType1.equals(type) && dataSet1.equals(
                        dataSet)) {
                    assertEquals(updatedCapabilities1, accountCapabilities);
                } else if (accountName2.equals(name) && accountType2.equals(type)
                        && TextUtils.isEmpty(dataSet)) {
                    assertEquals(attributes2, accountCapabilities);
                }
            }
        }
    }

    void createRawContact(AccountWithDataSet account) {
        createRawContact(account, /* deleted= */ false);
    }

    void createRawContact(AccountWithDataSet account, boolean deleted) {
        // Create an account.
        final long accountId = mDbHelper.getOrCreateAccountIdInTransaction(account);
        // Create a raw contact.
        ContentValues rawContactValues = new ContentValues();
        rawContactValues.put(ContactsDatabaseHelper.RawContactsColumns.ACCOUNT_ID, accountId);
        if (deleted) {
            rawContactValues.put(RawContactsColumns.CONCRETE_DELETED, 1);
        }
        mDb.insert(Tables.RAW_CONTACTS, null, rawContactValues);
    }

    public void testCountRawContactsForAccount() {
        createRawContact(
                new AccountWithDataSet("testName", "testType", /* dataSet= */ null));

        int count = mDbHelper.countRawContactsQuery(Set.of(
                new AccountWithDataSet("testName", "testType", /* dataSet= */ null)
        ));

        assertEquals(1, count);
    }

    public void testCountRawContactsForAccountsNullAccount() {
        createRawContact(new AccountWithDataSet(null, null, null));
        createRawContact(new AccountWithDataSet(null, null, null));

        int count = mDbHelper.countRawContactsQuery(Set.of(
                new AccountWithDataSet(null, null, null)
        ));

        assertEquals(2, count);
    }

    public void testCountRawContactsDoesNotIncludeDeletedContacts() {
        createRawContact(new AccountWithDataSet(null, null, null));
        createRawContact(new AccountWithDataSet(null, null, null),
                /* deleted= */ true
        );

        int count = mDbHelper.countRawContactsQuery(Set.of(
                new AccountWithDataSet(null, null, null)
        ));

        assertEquals(1, count);
    }

    public void testCountRawContactsForAccountsEmptyLocalAccount() {
        int count = mDbHelper.countRawContactsQuery(Set.of(AccountWithDataSet.LOCAL));

        assertEquals(0, count);
    }

    public void testCountRawContactsForUnrelatedAccount() {
        createRawContact(
                new AccountWithDataSet("testName", "testType", /* dataSet= */ null));

        int count = mDbHelper.countRawContactsQuery(Set.of(
                new AccountWithDataSet(null, null, null)
        ));

        assertEquals(0, count);
    }
}

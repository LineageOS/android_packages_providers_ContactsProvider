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

import android.accounts.Account;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.provider.ContactsContract.Settings.AccountAttributes;
import android.util.Log;

import com.android.providers.contacts.util.NeededForTesting;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages account attributes.
 *
 * <p>This class is responsible for initializing attributes for new accounts, retrieving them, and
 * handling periodic, rate-limited updates. It also provides a mechanism for external callers to
 * override these attributes.
 *
 * <p>This class is designed to be thread-safe.
 */
public class AccountAttributesManager {
    private static final String TAG = "AccountAttributesManager";

    /** Rate limit (in milliseconds) for account attributes updating. Do it at most once per day. */
    private static final long DEFAULT_ACCOUNT_ATTRIBUTES_UPDATE_RATE_LIMIT = 24 * 60 * 60 * 1000;
    private final ContactsDatabaseHelper mDbHelper;

    // In-memory cache of the last update time for account attributes. It's refreshed when the
    // provider starts up.
    private Map<AccountWithDataSet, Long> mLastAccountAttributeUpdate = new ConcurrentHashMap<>();

    private final AccountAttributesEvaluator mAccountAttributesEvaluator;

    private long mAccountAttributeUpdateRateLimit = DEFAULT_ACCOUNT_ATTRIBUTES_UPDATE_RATE_LIMIT;


    /**
     * Constructs a new AccountAttributesManager.
     *
     * @param dbHelper                   The database helper for persistence.
     * @param accountAttributesEvaluator The evaluator that determines default attributes for an
     *                                   account.
     */
    public AccountAttributesManager(ContactsDatabaseHelper dbHelper,
            AccountAttributesEvaluator accountAttributesEvaluator) {
        this.mDbHelper = dbHelper;
        this.mAccountAttributesEvaluator = accountAttributesEvaluator;
    }

    /**
     * Gets the attributes for a given account.
     *
     * <p>This method first attempts to retrieve attributes from the database. If the attributes are
     * not present or if they are considered stale based on a rate limit, it will trigger a
     * re-evaluation and persist the new attributes.
     *
     * <p>This operation is thread-safe. If multiple threads request attributes for the same account
     * simultaneously, it uses a double-checked locking pattern to ensure the evaluation only
     * happens once.
     *
     * @param accountWithDataSet The account for which to retrieve attributes.
     * @param systemAccounts     An array of current system accounts used for validation.
     * @return The account attributes as a {@code Long} value, which is a bitmask of capabilities.
     * @throws IllegalArgumentException if the provided account is not a valid system, local, or
     *                                  SIM account.
     */
    public Long getAccountAttributes(AccountWithDataSet accountWithDataSet,
            Account[] systemAccounts) {
        AccountAttributesInfo accountAttributesInfo = mDbHelper.getAccountAttributesInfo(
                accountWithDataSet.getAccountName(),
                accountWithDataSet.getAccountType(), accountWithDataSet.getDataSet());

        boolean isSystemOrLocalAccount =
                accountWithDataSet.isLocalAccount() || accountWithDataSet.inSystemAccounts(
                        systemAccounts);
        if (!isSystemOrLocalAccount && !accountWithDataSet.inSimAccounts(
                mDbHelper.getAllSimAccounts())) {
            throw new IllegalArgumentException(
                    "Cannot get account attributes for invalid accounts.");
        }

        long now = SystemClock.elapsedRealtime();
        boolean needsUpdate = needsAccountAttributesUpdate(accountWithDataSet,
                accountAttributesInfo,
                now);

        if (needsUpdate) {
            // Initialize or update the account attributes, subjected to rate limit.
            // The update should be thread-safe: if multiple thread updates the account attributes,
            // the account attributes might be updated multiple times.
            // TODO(b/430941895): Skip account attributes initialization/reevaluation if an app
            //  override it before.
            // Synchronize to prevent the race condition
            synchronized (this) {
                // Re-check the condition inside the synchronized block
                // another thread might have finished the update while we were waiting.
                needsUpdate = needsAccountAttributesUpdate(accountWithDataSet,
                        accountAttributesInfo,
                        now);

                if (needsUpdate) {
                    // Now, it's safe to perform the update.
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Start to initialize or refresh account attribute");
                    }
                    Long accountAttributes = initializeAccountAttributes(accountWithDataSet,
                            isSystemOrLocalAccount);
                    mLastAccountAttributeUpdate.put(accountWithDataSet, now);
                    return accountAttributes;
                }
            }
        }
        return accountAttributesInfo == null ? null : accountAttributesInfo.attributes;

    }

    /**
     * Refreshes attributes for all known accounts, subject to a rate limit.
     *
     * <p>This method iterates through all accounts stored in the database. For each account, it
     * checks if its attributes need to be re-evaluated based on the configured rate limit. If an
     * update is needed, it re-evaluates and stores the new attributes. Each account update is
     * performed in its own atomic database transaction.
     *
     * <p>Accounts are validated before their attributes are refreshed. An account's attributes will
     * only be updated if it is a valid local account, is present in the provided {@code
     * systemAccounts} array, or is a known SIM account.
     *
     * @param systemAccounts An array of current system accounts used to validate which accounts
     *                       should be refreshed.
     */
    public synchronized void refreshAllAccountAttributes(Account[] systemAccounts) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Started to refresh all account attributes");
        }

        final Set<AccountWithDataSet> knownAccountsWithDataSets =
                mDbHelper.getAllAccountsWithDataSets();

        long now = SystemClock.elapsedRealtime();
        for (AccountWithDataSet accountWithDataSet : knownAccountsWithDataSets) {
            AccountAttributesInfo accountAttributesInfo = mDbHelper.getAccountAttributesInfo(
                    accountWithDataSet.getAccountName(),
                    accountWithDataSet.getAccountType(), accountWithDataSet.getDataSet());
            if (!needsAccountAttributesUpdate(accountWithDataSet, accountAttributesInfo, now)) {
                // Account attributes has been updated recently, skip refreshing.
                continue;
            }

            // Refresh the attributes.
            try {
                initializeAccountAttributes(accountWithDataSet,
                        accountWithDataSet.isLocalAccount() || accountWithDataSet.inSystemAccounts(
                                systemAccounts));
                mLastAccountAttributeUpdate.put(accountWithDataSet, now);
            } catch (IllegalArgumentException e) {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, String.format("Ignore invalid account: %s", accountWithDataSet));
                }
            }
        }
    }

    private boolean needsAccountAttributesUpdate(AccountWithDataSet accountWithDataSet,
            AccountAttributesInfo accountAttributesInfo, long currentTimestamp) {
        long lastUpdate = mLastAccountAttributeUpdate.getOrDefault(accountWithDataSet, 0L);
        return accountAttributesInfo == null
                || (!accountAttributesInfo.hasOwnerSetAttributes
                && currentTimestamp > lastUpdate + getAccountAttributesEvaluationRateLimit());
    }

    /**
     * Initializes or re-evaluates attributes for an account based on system logic.
     *
     * <p>This method calculates the default attributes using the
     * {@link AccountAttributesEvaluator} and persists them to the database. It marks the
     * attributes as system-evaluated (i.e., not overridden by an owner). This method is
     * effectively used to "reset" an account's attributes to their default state.
     *
     * @param accountWithDataSet The account to initialize.
     * @param isSystemOrLocalAccount A boolean indicating if the account is a valid system or
     * local account.
     * @return The newly evaluated attributes, or {@code null} if the information could not be
     * retrieved after saving.
     * @throws IllegalArgumentException if the account becomes invalid during the transaction.
     * @hide
     */
    public Long initializeAccountAttributes(AccountWithDataSet accountWithDataSet,
            boolean isSystemOrLocalAccount) {
        long newAccountAttributes = mAccountAttributesEvaluator.evaluate(accountWithDataSet);

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();

        // Query the fresh SIM account state inside the same transaction with
        // account attribute initialization, so that  if the SIM account state
        // changed since last call, the account attributes
        // initialization is based on an up-to-date SIM account.
        try {
            if (!isSystemOrLocalAccount && !accountWithDataSet.inSimAccounts(
                    mDbHelper.getAllSimAccounts())) {
                throw new IllegalArgumentException(
                        "Account state changed and is now invalid");
            }

            mDbHelper.setAccountAttributes(accountWithDataSet.getAccountName(),
                    accountWithDataSet.getAccountType(), accountWithDataSet.getDataSet(),
                    newAccountAttributes, false);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        AccountAttributesInfo accountAttributesInfo = mDbHelper.getAccountAttributesInfo(
                accountWithDataSet.getAccountName(),
                accountWithDataSet.getAccountType(), accountWithDataSet.getDataSet());
        return accountAttributesInfo == null ? null : accountAttributesInfo.attributes;
    }

    private void setAccountAttributesWithOverride(AccountWithDataSet accountWithDataSet,
            long capabilities) {
        mDbHelper.setAccountAttributes(accountWithDataSet.getAccountName(),
                accountWithDataSet.getAccountType(), accountWithDataSet.getDataSet(), capabilities,
                true);
    }

    /**
     * Updates the attributes for a given account with a specific value.
     *
     * <p>This method allows an external caller to explicitly set the attributes, effectively
     * overriding the system-evaluated values. The provided attributes are validated to ensure they
     * do not contain undefined flags or logical conflicts (e.g., multiple data origin types).
     *
     * @param accountWithDataSet The account to update.
     * @param accountAttributes  The new attributes to set, as a bitmask.
     * @param systemAccounts     An array of current system accounts used for validation.
     * @throws IllegalArgumentException if the account is invalid or if the provided attributes
     *                                  contain undefined bits.
     * @throws IllegalStateException    if the provided attributes contain logical conflicts.
     */
    public void updateAccountAttributes(AccountWithDataSet accountWithDataSet,
            long accountAttributes, Account[] systemAccounts) {
        boolean isSystemOrLocalAccount =
                accountWithDataSet.isLocalAccount() || accountWithDataSet.inSystemAccounts(
                        systemAccounts);

        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();

        try {
            if (!isSystemOrLocalAccount && !accountWithDataSet.inSimAccounts(
                    mDbHelper.getAllSimAccounts())) {
                throw new IllegalArgumentException(
                        "Cannot overwrite account capabilities for invalid accounts.");
            }
            updateAccountAttributesInternal(accountWithDataSet, accountAttributes);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void updateAccountAttributesInternal(AccountWithDataSet accountWithDataSet,
            long attributes) {

        final long dataOriginMask = AccountAttributes.ATTRIBUTE_DATA_ORIGIN_LOCAL
                | AccountAttributes.ATTRIBUTE_DATA_ORIGIN_SIM
                | AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD;

        final long syncModeMask = AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC
                | AccountAttributes.ATTRIBUTE_SYNC_MODE_UP_SYNC;

        final long allDefinedCapabilities = dataOriginMask | syncModeMask
                | AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED;

        if ((attributes & ~allDefinedCapabilities) != 0) {
            throw new IllegalArgumentException("An undefined attribute bit was provided.");
        }

        // Check for semantic conflicts in the new state:
        // Check DATA_ORIGIN category: ensure at most one bit is set.
        // TODO(b/432284382): do we want to also enforce at least one bits is set?
        final long dataOriginBits = attributes & dataOriginMask;
        if ((dataOriginBits & (dataOriginBits - 1)) != 0) {
            throw new IllegalStateException(
                    "Conflict: The resulting attributes would contain more than one DATA_ORIGIN"
                            + " type.");
        }

        setAccountAttributesWithOverride(accountWithDataSet, attributes);
    }

    private long getAccountAttributesEvaluationRateLimit() {
        return mAccountAttributeUpdateRateLimit;
    }

    @NeededForTesting
    void setAccountAttributesUpdateRateLimit(long rateLimit) {
        this.mAccountAttributeUpdateRateLimit = rateLimit;
    }
}

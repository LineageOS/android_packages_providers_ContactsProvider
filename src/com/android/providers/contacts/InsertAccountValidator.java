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
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState;
import android.text.TextUtils;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;

/**
 * Validates the account specified for insertions of account scoped data (e.g. raw contacts and
 * groups).
 */
public class InsertAccountValidator {

    /** The result of validating the account arguments. */
    public enum ValidationResult {
        /** The account was valid. */
        PASS,
        /** No account was specified for the operation and hence validation doesn't really apply. */
        ACCOUNT_NOT_SPECIFIED,
        /**
         * The account arguments didn't satisfy the publicly documented constraints.
         *
         * <p>The primary constraint is that either both the name and type should be non-empty
         * or they should both be empty or null.
         */
        FAILURE_INVALID_ACCOUNT_ARGS,
        /**
         * Multiple accounts were specified but they did not match and hence it's ambiguous which
         * one should be used.
         */
        FAILURE_ACCOUNT_NOT_MATCHING,
        /**
         * The DefaultAccount is set to cloud but a SIM or local account was specified.
         */
        FAILURE_DEFAULT_ACCOUNT_CLOUD_RESTRICTION,
        /**
         * The account was not found in any of the standard sources (e.g. AccountManager or SIM
         * accounts) and did not match the local account either.
         *
         * <p>An account that is not found will eventually be removed by the contacts provider and
         * hence adding data to it should be avoided.
         */
        FAILURE_ACCOUNT_NOT_FOUND
    }

    private final DefaultAccountAndState mDefaultAccountAndState;
    // The current SIM accounts returned by ContactsDatabaseHelper.getAllSimAccounts
    private final List<ContactsContract.SimAccount> mSimAccounts;
    // The current accounts from AccountManager.getAccounts
    private final Account[] mSystemAccounts;
    private boolean mAllowSimWriteOnCloudDcaBypassEnabled;

    private ValidationResult mValidationResult = ValidationResult.PASS;
    // The account that was requested by the caller for the operation (e.g. by providing it
    // in the Uri or ContentValues).
    private AccountWithDataSet mRequestedAccount;
    // The SIM account from mSimAccounts that matched the requested account or null if none matched.
    private ContactsContract.SimAccount mMatchingSimAccount;
    // Whether the mRequestedAccount was found in mSystemAccounts.
    private boolean mIsSystemAccount;

    public InsertAccountValidator(DefaultAccountAndState defaultAccountAndState,
            List<ContactsContract.SimAccount> simAccounts, Account[] systemAccounts) {
        mDefaultAccountAndState = Objects.requireNonNull(defaultAccountAndState);
        mSimAccounts = Objects.requireNonNull(simAccounts);
        mSystemAccounts = Objects.requireNonNull(systemAccounts);
    }

    public void setAllowSimWriteOnCloudDcaBypassEnabled(
            boolean allowSimWriteOnCloudDcaBypassEnabled) {
        mAllowSimWriteOnCloudDcaBypassEnabled = allowSimWriteOnCloudDcaBypassEnabled;
    }

    /**
     * Checks whether the account specified by the arguments is a valid account to use for contact
     * or group creation.
     */
    public ValidationResultWithDetails getValidationResult(String accountName, String accountType,
            String dataSet) {
        setRequestedAccount(accountName, accountType, dataSet);
        return getValidationResultForRequestedAccount(null);
    }


    /**
     * Extracts the account specified in the Uri and/or values and checks whether it is a valid
     * account to use for contact or group creation.
     */
    public ValidationResultWithDetails getValidationResult(Uri uri, ContentValues values) {
        String accountName = null;
        String accountType = null;
        String dataSet = null;
        if (uri != null) {
            accountName = ContactsProvider2.getQueryParameter(uri,
                    ContactsContract.RawContacts.ACCOUNT_NAME);
            accountType = ContactsProvider2.getQueryParameter(uri,
                    ContactsContract.RawContacts.ACCOUNT_TYPE);
            dataSet = ContactsProvider2.getQueryParameter(uri,
                    ContactsContract.RawContacts.DATA_SET);
        }
        if (accountName != null || accountType != null || dataSet != null) {
            setRequestedAccount(accountName, accountType, dataSet);
        }
        if (values != null && (values.containsKey(ContactsContract.RawContacts.ACCOUNT_NAME)
                || values.containsKey(ContactsContract.RawContacts.ACCOUNT_TYPE)
                || values.containsKey(ContactsContract.RawContacts.DATA_SET))) {
            accountName = values.getAsString(ContactsContract.RawContacts.ACCOUNT_NAME);
            accountType = values.getAsString(ContactsContract.RawContacts.ACCOUNT_TYPE);
            dataSet = values.getAsString(ContactsContract.RawContacts.DATA_SET);
            setRequestedAccount(accountName, accountType, dataSet);
        }
        return getValidationResultForRequestedAccount(uri);
    }

    private void setRequestedAccount(String accountName, String accountType, String dataSet) {
        if (mValidationResult != ValidationResult.PASS) {
            // Already failed so nothing to do.
            return;
        }
        if (TextUtils.isEmpty(accountName) ^ TextUtils.isEmpty(accountType)) {
            mValidationResult = ValidationResult.FAILURE_INVALID_ACCOUNT_ARGS;
            return;
        }
        // Ignore the dataSet if the name and type are empty or null.
        AccountWithDataSet requestedAccount = TextUtils.isEmpty(accountName)
                ? new AccountWithDataSet(null, null, null)
                : new AccountWithDataSet(accountName, accountType, dataSet);
        // mRequestedAccount might already be set if the account is specified in multiple
        // ways (e.g. in both the uri and the values). In that case all the specified accounts
        // must be the same to avoid ambiguity.
        if (mRequestedAccount != null && !mRequestedAccount.equals(requestedAccount)) {
            mValidationResult = ValidationResult.FAILURE_ACCOUNT_NOT_MATCHING;
        } else {
            // So far so good.
            mValidationResult = ValidationResult.PASS;
            mRequestedAccount = requestedAccount;
            mMatchingSimAccount = requestedAccount.findMatchingSimAccount(mSimAccounts);
            mIsSystemAccount = requestedAccount.inSystemAccounts(mSystemAccounts);
        }
    }

    private ValidationResultWithDetails getValidationResultForRequestedAccount(Uri uri) {
        if (mValidationResult != ValidationResult.PASS) {
            return createValidationResult(uri);
        } else if (mRequestedAccount == null) {
            mValidationResult = ValidationResult.ACCOUNT_NOT_SPECIFIED;
            return createValidationResult(uri);
        }

        if (mDefaultAccountAndState.getState()
                == DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD) {
            if (mRequestedAccount.isLocalAccount()) {
                mValidationResult = ValidationResult.FAILURE_DEFAULT_ACCOUNT_CLOUD_RESTRICTION;
            } else if (!mAllowSimWriteOnCloudDcaBypassEnabled && mMatchingSimAccount != null) {
                mValidationResult = ValidationResult.FAILURE_DEFAULT_ACCOUNT_CLOUD_RESTRICTION;
            }
        }
        if (mValidationResult == ValidationResult.PASS
                && !mRequestedAccount.isLocalAccount() && mMatchingSimAccount == null
                && !mIsSystemAccount) {
            mValidationResult = ValidationResult.FAILURE_ACCOUNT_NOT_FOUND;
        }

        return createValidationResult(uri);
    }

    private ValidationResultWithDetails createValidationResult(Uri uri) {
        return new ValidationResultWithDetails(mValidationResult, mRequestedAccount,
                mDefaultAccountAndState, mIsSystemAccount, mMatchingSimAccount, uri);
    }

    /** Contains the result of validation along with accompanying details for logging purposes. */
    public static class ValidationResultWithDetails {
        private final ValidationResult mValidationResult;
        private final AccountWithDataSet mRequestedAccount;
        private final DefaultAccountAndState mDefaultAccountAndState;
        private final boolean mIsSystemAccount;
        private final ContactsContract.SimAccount mMatchingSimAccount;
        private final Uri mUri;


        @VisibleForTesting
        public ValidationResultWithDetails(
                ValidationResult validationResult, AccountWithDataSet requestedAccount,
                DefaultAccountAndState defaultAccountAndState,
                boolean isSystemAccount, ContactsContract.SimAccount matchingSimAccount, Uri uri) {
            mValidationResult = validationResult;
            mRequestedAccount = requestedAccount;
            mDefaultAccountAndState = defaultAccountAndState;
            mIsSystemAccount = isSystemAccount;
            mMatchingSimAccount = matchingSimAccount;
            mUri = uri;
        }

        public ValidationResult getValidationResult() {
            return mValidationResult;
        }

        public AccountWithDataSet getRequestedAccount() {
            return mRequestedAccount;
        }

        public DefaultAccountAndState getDefaultAccountAndState() {
            return mDefaultAccountAndState;
        }

        public Uri getUri() {
            return mUri;
        }

        public String getRequestedAccountType() {
            return mRequestedAccount != null ? mRequestedAccount.getAccountType() : null;
        }

        public int getDefaultAccountState() {
            return mDefaultAccountAndState.getState();
        }

        public ContactsContract.SimAccount getMatchingSimAccount() {
            return mMatchingSimAccount;
        }

        public boolean isSystemAccount() {
            return mIsSystemAccount;
        }

        public boolean isLocalAccount() {
            return mRequestedAccount != null && mRequestedAccount.isLocalAccount();
        }
    }

}

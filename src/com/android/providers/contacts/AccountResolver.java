/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState;
import android.provider.ContactsContract.SimAccount;
import android.text.TextUtils;
import android.util.Log;

import com.android.providers.contacts.InsertAccountValidator.ValidationResultWithDetails;

import java.util.List;

public class AccountResolver {
    public static final String UNABLE_TO_WRITE_TO_LOCAL_OR_SIM_EXCEPTION_MESSAGE =
            "Cannot add contacts to local or SIM accounts when default account is set to cloud";
    private static final String TAG = "AccountResolver";

    private final ContactsDatabaseHelper mDbHelper;
    private final DefaultAccountManager mDefaultAccountManager;
    private final AccountManager mAccountManager;

    public AccountResolver(ContactsDatabaseHelper dbHelper,
            DefaultAccountManager defaultAccountManager, AccountManager accountManager) {
        mDbHelper = dbHelper;
        mDefaultAccountManager = defaultAccountManager;
        mAccountManager = accountManager;
    }

    private static Account getLocalAccount() {
        if (TextUtils.isEmpty(AccountWithDataSet.LOCAL.getAccountName())) {
            // AccountWithDataSet.LOCAL's getAccountType() must be null as well, thus we return
            // the NULL account.
            return null;
        } else {
            // AccountWithDataSet.LOCAL's getAccountType() must not be null as well, thus we return
            // the customized local account.
            return new Account(AccountWithDataSet.LOCAL.getAccountName(),
                    AccountWithDataSet.LOCAL.getAccountType());
        }
    }

    /**
     * Resolves the account and builds an {@link AccountWithDataSet} based on the data set specified
     * in the URI or values (if any).
     *
     * @param uri                                     Current {@link Uri} being operated on.
     * @param values                                  {@link ContentValues} to read and possibly
     *                                                update.
     * @param applyDefaultAccount                     Whether to look up default account during
     *                                                account resolution.
     * @param shouldValidateAccountForContactAddition Whether to validate the account accepts new
     *                                                contacts.
     */
    public AccountWithDataSet resolveAccountWithDataSet(Uri uri, ContentValues values,
            boolean applyDefaultAccount, boolean shouldValidateAccountForContactAddition,
            boolean allowSimWriteOnCloudDcaBypassEnabled) {
        final Account[] accounts = resolveAccount(uri, values);
        final Account account = applyDefaultAccount ? getAccountWithDefaultAccountApplied(accounts,
                shouldValidateAccountForContactAddition, allowSimWriteOnCloudDcaBypassEnabled)
                : getFirstAccountOrNull(accounts);

        AccountWithDataSet accountWithDataSet = null;
        if (account != null) {
            String dataSet = ContactsProvider2.getQueryParameter(uri, RawContacts.DATA_SET);
            if (dataSet == null) {
                dataSet = values.getAsString(RawContacts.DATA_SET);
            } else {
                values.put(RawContacts.DATA_SET, dataSet);
            }
            accountWithDataSet = AccountWithDataSet.get(account.name, account.type, dataSet);
        }

        return accountWithDataSet;
    }

    /**
     * Resolves the account to use for a contact (or group) creation operation based on the provided
     * validationResult.
     *
     * @param validationResult                        The result of validating the account
     *                                                specified
     *                                                for the operation.
     *                                                See
     *                                                {@link
     *
     *
     *
     *
     *                                            #getAccountValidationResultForContactAddition(Uri,
     *                                                ContentValues, boolean)}
     * @param applyDefaultAccount                     Whether to use the default account if no
     *                                                account was specified.
     * @param shouldValidateAccountForContactAddition Whether to validate the account accepts new
     *                                                contacts.
     * @throws IllegalArgumentException if the validation result indicates a failure and the failure
     *                                  is enforced.
     */
    public AccountWithDataSet resolveAccountWithDataSet(
            ValidationResultWithDetails validationResult, boolean applyDefaultAccount,
            boolean shouldValidateAccountForContactAddition) {
        return switch (validationResult.getValidationResult()) {
            case PASS -> {
                AccountWithDataSet account = validationResult.getRequestedAccount();
                if (account != null && account.isLocalAccount()) {
                    // This is a little confusing but the existing ContactProvider2 logic
                    // represents the
                    // local account with a null value until the last step and then converts it to
                    // AccountWithDataSet.LOCAL. It should be equivalent to convert to
                    // AccountWithDataSet.LOCAL eagerly but for now we'll do it this way.
                    yield null;
                } else {
                    yield account;
                }
            }
            case ACCOUNT_NOT_SPECIFIED -> {
                int defaultAccountState = validationResult.getDefaultAccountAndState().getState();

                if (!applyDefaultAccount || defaultAccountState
                        == DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_NOT_SET
                        || defaultAccountState
                        == DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL) {
                    // See comment in above PASS case; null means use the local account in
                    // ContactsProvider2.
                    yield null;
                } else {
                    yield AccountWithDataSet.get(
                            validationResult.getDefaultAccountAndState().getAccount(), null);
                }
            }
            case FAILURE_INVALID_ACCOUNT_ARGS -> throw new IllegalArgumentException(
                    mDbHelper.exceptionMessage(
                            "Must specify both or neither of ACCOUNT_NAME and ACCOUNT_TYPE",
                            validationResult.getUri()));
            case FAILURE_ACCOUNT_NOT_MATCHING -> throw new IllegalArgumentException(
                    mDbHelper.exceptionMessage(
                            "When both specified, ACCOUNT_NAME and ACCOUNT_TYPE must match",
                            validationResult.getUri()));
            case FAILURE_DEFAULT_ACCOUNT_CLOUD_RESTRICTION -> {
                AccountWithDataSet account = validationResult.getRequestedAccount();
                if (shouldValidateAccountForContactAddition) {
                    throw new IllegalArgumentException(
                            UNABLE_TO_WRITE_TO_LOCAL_OR_SIM_EXCEPTION_MESSAGE);
                } else if (account != null && account.isLocalAccount()) {
                    yield null;
                } else {
                    yield account;
                }
            }
            // Note: not enforcing account existence yet.
            case FAILURE_ACCOUNT_NOT_FOUND -> validationResult.getRequestedAccount();
        };
    }

    /**
     * Checks that the provided validationResult is valid and throws an appropriate exception if
     * needed.
     */
    public void requireValidAccount(ValidationResultWithDetails validationResult,
            boolean shouldValidateAccountForContactAddition) {
        // Attempting to resolve the account that was specified will throw if it was invalid.
        AccountWithDataSet unused = resolveAccountWithDataSet(validationResult,
                /** applyDefaultAccount=*/false, shouldValidateAccountForContactAddition);
    }

    /**
     * Checks that the account specified by the provided Uri and values is valid for creating
     * contacts.
     *
     * @return the validation result which indicates whether the account is valid and details
     * about the account validity.
     */
    public ValidationResultWithDetails getAccountValidationResultForContactAddition(Uri uri,
            ContentValues values, boolean allowSimWriteOnCloudDcaBypassEnabled) {
        InsertAccountValidator validator = new InsertAccountValidator(
                mDefaultAccountManager.pullDefaultAccount(), mDbHelper.getAllSimAccounts(),
                mAccountManager.getAccounts());
        validator.setAllowSimWriteOnCloudDcaBypassEnabled(allowSimWriteOnCloudDcaBypassEnabled);
        return validator.getValidationResult(uri, values);
    }

    /**
     * Checks that the account is valid for creating contacts.
     *
     * @return the validation result which indicates whether the account is valid and details
     * about the account validity.
     */
    public ValidationResultWithDetails getAccountValidationResultForContactAddition(
            String accountName, String accountType, String dataSet,
            boolean allowSimWriteOnCloudDcaBypassEnabled) {
        InsertAccountValidator validator = new InsertAccountValidator(
                mDefaultAccountManager.pullDefaultAccount(), mDbHelper.getAllSimAccounts(),
                mAccountManager.getAccounts());
        validator.setAllowSimWriteOnCloudDcaBypassEnabled(allowSimWriteOnCloudDcaBypassEnabled);
        return validator.getValidationResult(accountName, accountType, dataSet);
    }


    /**
     * Resolves the account to be used, taking into consideration the default account settings.
     *
     * @param accounts 1-size array which contains specified account, or empty array if account is
     *                 not specified.
     * @return The resolved account, or null if it's the default device (aka "NULL") account.
     * @throws IllegalArgumentException If there's an issue with the account resolution due to
     *                                  default account incompatible account types.
     */
    private Account getAccountWithDefaultAccountApplied(Account[] accounts,
            boolean shouldValidateAccountForContactAddition,
            boolean allowSimWriteOnCloudDcaBypassEnabled) throws IllegalArgumentException {
        if (accounts.length == 0) {
            DefaultAccountAndState defaultAccountAndState =
                    mDefaultAccountManager.pullDefaultAccount();
            if (defaultAccountAndState.getState()
                    == DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_NOT_SET
                    || defaultAccountAndState.getState()
                    == DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL) {
                return getLocalAccount();
            } else {
                return defaultAccountAndState.getAccount();
            }
        } else {
            validateAccountForContactAdditionInternal(accounts[0],
                    shouldValidateAccountForContactAddition, allowSimWriteOnCloudDcaBypassEnabled);
            return accounts[0];
        }
    }

    /**
     * Checks if new contacts in specified account is accepted.
     *
     * <p>This method checks if contacts can be written to the given account based on the
     * current default account settings. It throws an {@link IllegalArgumentException} if
     * the contacts cannot be created in the given account .</p>
     *
     * @param accountName The name of the account to check.
     * @param accountType The type of the account to check.
     * @throws IllegalArgumentException if either of the following conditions are met:
     *                                  <ul>
     *                                      <li>Only one of <code>accountName</code> or
     *                                      <code>accountType</code> is
     *                                          specified.</li>
     *                                      <li>The default account is set to cloud and the
     *                                      specified account is a local
     *                                          (device or SIM) account.</li>
     *                                  </ul>
     */
    public void validateAccountForContactAddition(String accountName, String accountType,
            boolean shouldValidateAccountForContactAddition,
            boolean allowSimWriteOnCloudDcaBypassEnabled) {
        if (shouldValidateAccountForContactAddition) {
            if (TextUtils.isEmpty(accountName) ^ TextUtils.isEmpty(accountType)) {
                throw new IllegalArgumentException(
                        "Must specify both or neither of ACCOUNT_NAME and ACCOUNT_TYPE");
            }
        }

        if (TextUtils.isEmpty(accountName)) {
            validateAccountForContactAdditionInternal(/*account=*/null,
                    shouldValidateAccountForContactAddition, allowSimWriteOnCloudDcaBypassEnabled);
        } else {
            validateAccountForContactAdditionInternal(new Account(accountName, accountType),
                    shouldValidateAccountForContactAddition, allowSimWriteOnCloudDcaBypassEnabled);
        }
    }


    private void validateAccountForContactAdditionInternal(Account account,
            boolean enforceCloudDefaultAccountRestriction,
            boolean allowSimWriteOnCloudDcaBypassEnabled) throws IllegalArgumentException {
        DefaultAccountAndState defaultAccount = mDefaultAccountManager.pullDefaultAccount();

        if (defaultAccount.getState() == DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD) {
            if (allowSimWriteOnCloudDcaBypassEnabled ? isDeviceAccount(account)
                    : isDeviceOrSimAccount(account)) {
                if (enforceCloudDefaultAccountRestriction) {
                    throw new IllegalArgumentException(
                            UNABLE_TO_WRITE_TO_LOCAL_OR_SIM_EXCEPTION_MESSAGE);
                } else {
                    Log.w(TAG, "Cloud default account: Local/SIM contact creation allowed (target "
                            + "SDK <36), but restricted in target SDK 36+. Avoid "
                            + "local/SIM writes in target SDK 36+.");
                }
            }
        }
    }

    /**
     * Gets the first account from the array, or null if the array is empty.
     *
     * @param accounts The array of accounts.
     * @return The first account, or null if the array is empty.
     */
    private Account getFirstAccountOrNull(Account[] accounts) {
        return accounts.length > 0 ? accounts[0] : null;
    }


    private boolean isDeviceOrSimAccount(Account account) {
        AccountWithDataSet accountWithDataSet = account == null ? new AccountWithDataSet(null, null,
                null) : new AccountWithDataSet(account.name, account.type, null);

        List<SimAccount> simAccounts = mDbHelper.getAllSimAccounts();
        return accountWithDataSet.isLocalAccount() || accountWithDataSet.inSimAccounts(simAccounts);
    }

    private boolean isDeviceAccount(Account account) {
        AccountWithDataSet accountWithDataSet = account == null ? new AccountWithDataSet(null, null,
                null) : new AccountWithDataSet(account.name, account.type, null);

        return accountWithDataSet.isLocalAccount();
    }

    /**
     * If account is non-null then store it in the values. If the account is
     * already specified in the values then it must be consistent with the
     * account, if it is non-null.
     *
     * @param uri    Current {@link Uri} being operated on.
     * @param values {@link ContentValues} to read and possibly update.
     * @return 1-size array which contains account specified by {@link Uri} and
     * {@link ContentValues}, or empty array if account is not specified.
     * @throws IllegalArgumentException when only one of
     *                                  {@link RawContacts#ACCOUNT_NAME} or
     *                                  {@link RawContacts#ACCOUNT_TYPE} is specified, leaving the
     *                                  other undefined.
     * @throws IllegalArgumentException when {@link RawContacts#ACCOUNT_NAME}
     *                                  and {@link RawContacts#ACCOUNT_TYPE} are inconsistent
     *                                  between
     *                                  the given {@link Uri} and {@link ContentValues}.
     */
    private Account[] resolveAccount(Uri uri, ContentValues values)
            throws IllegalArgumentException {
        String accountName = ContactsProvider2.getQueryParameter(uri, RawContacts.ACCOUNT_NAME);
        String accountType = ContactsProvider2.getQueryParameter(uri, RawContacts.ACCOUNT_TYPE);
        final boolean partialUri = TextUtils.isEmpty(accountName) ^ TextUtils.isEmpty(accountType);

        if (accountName == null && accountType == null && !values.containsKey(
                RawContacts.ACCOUNT_NAME) && !values.containsKey(RawContacts.ACCOUNT_TYPE)) {
            // Account is not specified.
            return new Account[0];
        }

        String valueAccountName = values.getAsString(RawContacts.ACCOUNT_NAME);
        String valueAccountType = values.getAsString(RawContacts.ACCOUNT_TYPE);

        final boolean partialValues = TextUtils.isEmpty(valueAccountName) ^ TextUtils.isEmpty(
                valueAccountType);

        if (partialUri || partialValues) {
            // Throw when either account is incomplete.
            throw new IllegalArgumentException(mDbHelper.exceptionMessage(
                    "Must specify both or neither of ACCOUNT_NAME and ACCOUNT_TYPE", uri));
        }

        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validUri = !TextUtils.isEmpty(accountName);
        final boolean validValues = !TextUtils.isEmpty(valueAccountName);

        if (validValues && validUri) {
            // Check that accounts match when both present
            final boolean accountMatch = TextUtils.equals(accountName, valueAccountName)
                    && TextUtils.equals(accountType, valueAccountType);
            if (!accountMatch) {
                throw new IllegalArgumentException(mDbHelper.exceptionMessage(
                        "When both specified, ACCOUNT_NAME and ACCOUNT_TYPE must match", uri));
            }
        } else if (validUri) {
            // Fill values from the URI when not present.
            values.put(RawContacts.ACCOUNT_NAME, accountName);
            values.put(RawContacts.ACCOUNT_TYPE, accountType);
        } else if (validValues) {
            accountName = valueAccountName;
            accountType = valueAccountType;
        } else {
            return new Account[]{null};
        }

        return new Account[]{new Account(accountName, accountType)};
    }
}

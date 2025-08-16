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

import static com.android.providers.contacts.flags.Flags.logContactSaveInvalidAccountError;

import android.accounts.AuthenticatorDescription;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import com.android.providers.contacts.AccountResolver;
import com.android.providers.contacts.util.LogUtils.AccountSyncMode;
import com.android.providers.contacts.util.LogUtils.CallerAccountTypeOwnership;

import com.google.common.base.Strings;

import java.util.Objects;

public final class LogFields {
    private static final String TAG = "Cp2LogFields";

    private final int mApiType;

    private final int mUriType;

    private final int mTaskType;

    private final int mCallerType;

    private final long mStartNanos;

    private final int mResultType;

    private final Uri mResultUri;

    private final int mResultCount;

    private final int mMethodCalled;

    private final int mUid;

    private final String mAccountType;

    private final int mAccountDataOrigin;

    private final int mDefaultAccountState;

    private final int mCallerAccountTypeOwnership;

    private final int mAccountSyncMode;


    public LogFields(int apiType, int uriType, int taskType, int callerType, long startNanos,
            int resultType, Uri resultUri, int resultCount, int methodCalled, int uid,
            String accountType, int accountDataOrigin, int defaultAccountState,
            int callerAccountTypeOwnership, int accountSyncMode) {
        mApiType = apiType;
        mUriType = uriType;
        mTaskType = taskType;
        mCallerType = callerType;
        mStartNanos = startNanos;
        mResultType = resultType;
        mResultUri = resultUri;
        mResultCount = resultCount;
        mMethodCalled = methodCalled;
        mUid = uid;
        mAccountType = accountType;
        mAccountDataOrigin = accountDataOrigin;
        mDefaultAccountState = defaultAccountState;
        mCallerAccountTypeOwnership = callerAccountTypeOwnership;
        mAccountSyncMode = accountSyncMode;
    }

    public int getApiType() {
        return mApiType;
    }

    public int getUriType() {
        return mUriType;
    }

    public int getTaskType() {
        return mTaskType;
    }

    public int getCallerType() {
        return mCallerType;
    }

    public long getStartNanos() {
        return mStartNanos;
    }

    public int getResultType() {
        return mResultType;
    }

    public Uri getResultUri() {
        return mResultUri;
    }

    public int getResultCount() {
        return mResultCount;
    }

    public int getMethodCalled() {
        return mMethodCalled;
    }

    public int getUid() {
        return mUid;
    }

    public String getAccountType() {
        return Strings.nullToEmpty(mAccountType);
    }

    public int getAccountDataOrigin() {
        return mAccountDataOrigin;
    }

    public int getDefaultAccountState() {
        return mDefaultAccountState;
    }

    public int getCallerAccountTypeOwnership() {
        return mCallerAccountTypeOwnership;
    }

    public int getAccountSyncMode() {
        return mAccountSyncMode;
    }


    public static final class Builder {
        private int mApiType;
        private int mUriType;
        private int mTaskType;
        private boolean mCallerIsSyncAdapter;
        private long mStartNanos;
        private Exception mException;
        private Uri mResultUri;
        private int mResultCount;
        private int mMethodCalled;
        private int mUid;
        private String mAccountType;
        private boolean mIsSystemAccount;
        private boolean mIsLocalAccount;
        private ContactsContract.SimAccount mSimAccount;
        private int mDefaultAccountState;
        private int mCallerAccountTypeOwnership;
        private int mAccountSyncMode;

        private Builder() {
        }

        public static Builder aLogFields() {
            return new Builder();
        }

        public Builder setApiType(int apiType) {
            this.mApiType = apiType;
            return this;
        }

        public Builder setUriType(int uriType) {
            this.mUriType = uriType;
            return this;
        }

        public Builder setTaskType(int taskType) {
            this.mTaskType = taskType;
            return this;
        }

        public Builder setCallerIsSyncAdapter(boolean callerIsSyncAdapter) {
            this.mCallerIsSyncAdapter = callerIsSyncAdapter;
            return this;
        }

        public Builder setStartNanos(long startNanos) {
            this.mStartNanos = startNanos;
            return this;
        }

        public Builder setException(Exception exception) {
            this.mException = exception;
            return this;
        }

        public Builder setResultUri(Uri resultUri) {
            this.mResultUri = resultUri;
            return this;
        }

        public Builder setResultCount(int resultCount) {
            this.mResultCount = resultCount;
            return this;
        }

        /**
         * Sets the method called.
         *
         * @param methodCalled The method called.
         * @return This {@code Builder} object for chaining.
         */
        public Builder setMethodCalled(int methodCalled) {
            this.mMethodCalled = methodCalled;
            return this;
        }

        public Builder setUid(int uid) {
            this.mUid = uid;
            return this;
        }

        public Builder setAccountType(String accountType) {
            mAccountType = accountType;
            return this;
        }

        public Builder setSystemAccount(boolean isSystemAccount) {
            mIsSystemAccount = isSystemAccount;
            return this;
        }

        public Builder setLocalAccount(boolean isLocalAccount) {
            mIsLocalAccount = isLocalAccount;
            return this;
        }

        public Builder setSimAccount(ContactsContract.SimAccount simAccount) {
            mSimAccount = simAccount;
            return this;
        }

        public Builder setDefaultAccountState(int defaultAccountState) {
            mDefaultAccountState = defaultAccountState;
            return this;
        }

        /**
         * Detects and sets whether the current UID owns the current account type.
         *
         * {@link #setAccountType(String)} and {@link #setUid(int)} should be called before this
         * method.
         */
        public Builder detectCallerAccountTypeOwnership(PackageManager packageManager,
                AuthenticatorDescription[] authenticatorDescriptions) {
            if (mAccountType == null) {
                return this;
            }
            mCallerAccountTypeOwnership = CallerAccountTypeOwnership.NOT_OWNED;
            for (AuthenticatorDescription authenticatorDescription : authenticatorDescriptions) {
                if (mAccountType.equals(authenticatorDescription.type)) {
                    try {
                        int authenticatorUid = packageManager.getPackageUid(
                                authenticatorDescription.packageName, 0);
                        if (authenticatorUid == mUid) {
                            mCallerAccountTypeOwnership = CallerAccountTypeOwnership.OWNED;
                            break;
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        // Debug level since this is for logging and hence not essential.
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "detectAuthenticatorOwnership failed", e);
                        }
                    }
                }
            }
            return this;
        }

        /** Detects and sets the sync mode of the current account. */
        public Builder detectAccountSyncMode(SyncAdapterType[] syncAdapterTypes) {
            if (mAccountType == null) {
                return this;
            }
            for (SyncAdapterType syncAdapterType : syncAdapterTypes) {
                if (Objects.equals(syncAdapterType.authority, ContactsContract.AUTHORITY)
                        && syncAdapterType.accountType.equals(mAccountType)) {
                    if (syncAdapterType.supportsUploading()) {
                        mAccountSyncMode = AccountSyncMode.BIDIRECTIONAL;
                    } else {
                        mAccountSyncMode = AccountSyncMode.DOWN_ONLY;
                    }
                }
            }
            return this;
        }

        public LogFields build() {
            return new LogFields(mApiType, mUriType, mTaskType, getCallerType(), mStartNanos,
                    getResultType(), mResultUri, mResultCount, mMethodCalled, mUid, mAccountType,
                    getAccountDataOrigin(), mDefaultAccountState, mCallerAccountTypeOwnership,
                    mAccountSyncMode);
        }

        private int getResultType() {
            if (mException == null) {
                return LogUtils.ResultType.SUCCESS;
            } else if (mException instanceof IllegalArgumentException) {
                if (logContactSaveInvalidAccountError()
                        && AccountResolver.UNABLE_TO_WRITE_TO_LOCAL_OR_SIM_EXCEPTION_MESSAGE.equals(
                        mException.getMessage())) {
                    return LogUtils.ResultType.INVALID_ACCOUNT;
                }
                return LogUtils.ResultType.ILLEGAL_ARGUMENT;
            } else if (mException instanceof UnsupportedOperationException) {
                return LogUtils.ResultType.UNSUPPORTED_OPERATION;
            } else {
                return LogUtils.ResultType.FAIL;
            }
        }

        private int getCallerType() {
            return mCallerIsSyncAdapter ? LogUtils.CallerType.CALLER_IS_SYNC_ADAPTER
                    : LogUtils.CallerType.CALLER_IS_NOT_SYNC_ADAPTER;
        }

        private int getAccountDataOrigin() {
            if (mIsSystemAccount) {
                return LogUtils.AccountDataOrigin.CLOUD;
            } else if (mIsLocalAccount) {
                return LogUtils.AccountDataOrigin.LOCAL;
            } else if (mSimAccount != null) {
                return switch (mSimAccount.getEfType()) {
                    case ContactsContract.SimAccount.ADN_EF_TYPE ->
                            LogUtils.AccountDataOrigin.SIM_ADN;
                    case ContactsContract.SimAccount.FDN_EF_TYPE ->
                            LogUtils.AccountDataOrigin.SIM_FDN;
                    case ContactsContract.SimAccount.SDN_EF_TYPE ->
                            LogUtils.AccountDataOrigin.SIM_SDN;
                    default -> LogUtils.AccountDataOrigin.UNSPECIFIED;
                };
            } else {
                return LogUtils.AccountDataOrigin.UNSPECIFIED;
            }
        }

    }
}

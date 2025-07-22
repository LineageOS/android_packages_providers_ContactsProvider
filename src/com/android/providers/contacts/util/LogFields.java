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

import android.net.Uri;

public final class LogFields {

    private final int mApiType;

    private final int mUriType;

    private final int mTaskType;

    private final boolean mCallerIsSyncAdapter;

    private final long mStartNanos;

    private final Exception mException;

    private final Uri mResultUri;

    private final int mResultCount;

    private final int mMethodCalled;

    private final int mUid;

    public LogFields(
            int apiType, int uriType, int taskType, boolean callerIsSyncAdapter, long startNanos,
            Exception exception, Uri resultUri, int resultCount, int methodCalled, int uid
    ) {
        mApiType = apiType;
        mUriType = uriType;
        mTaskType = taskType;
        mCallerIsSyncAdapter = callerIsSyncAdapter;
        mStartNanos = startNanos;
        mException = exception;
        mResultUri = resultUri;
        mResultCount = resultCount;
        mMethodCalled = methodCalled;
        mUid = uid;
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

    public boolean isCallerIsSyncAdapter() {
        return mCallerIsSyncAdapter;
    }

    public long getStartNanos() {
        return mStartNanos;
    }

    public Exception getException() {
        return mException;
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

        public LogFields build() {
            return new LogFields(
                    mApiType,
                    mUriType,
                    mTaskType,
                    mCallerIsSyncAdapter,
                    mStartNanos,
                    mException,
                    mResultUri,
                    mResultCount,
                    mMethodCalled,
                    mUid);
        }
    }
}

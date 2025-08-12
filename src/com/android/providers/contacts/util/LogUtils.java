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

import static com.android.providers.contacts.flags.Flags.logCallMethod;
import static com.android.providers.contacts.flags.Flags.logContactSaveInvalidAccountError;

import android.os.SystemClock;

import com.android.providers.contacts.AccountResolver;

public class LogUtils {

    public interface ResultType {
        int SUCCESS =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__RESULT_TYPE__SUCCESS;
        int FAIL = ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__RESULT_TYPE__FAIL;
        int ILLEGAL_ARGUMENT =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__RESULT_TYPE__ILLEGAL_ARGUMENT;
        int UNSUPPORTED_OPERATION =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__RESULT_TYPE__UNSUPPORTED_OPERATION;
        int INVALID_ACCOUNT =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__RESULT_TYPE__INCORRECT_ACCOUNT;
    }

    public interface ApiType {
        int QUERY = ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__API_TYPE__QUERY;
        int INSERT = ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__API_TYPE__INSERT;
        int UPDATE = ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__API_TYPE__UPDATE;
        int DELETE = ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__API_TYPE__DELETE;
        int CALL = ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__API_TYPE__CALL;
        int GAL_CALL =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__API_TYPE__GAL_CALL;
    }

    public interface TaskType {
        int DANGLING_CONTACTS_CLEANUP_TASK =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__TASK_TYPE__DANGLING_CONTACTS_CLEANUP_TASK;
    }

    public interface MethodCall {
        int UNKNOWN_METHOD =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__UNKNOWN_METHOD;
        int ADD_SIM_ACCOUNTS =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__ADD_SIM_ACCOUNTS;
        int REMOVE_SIM_ACCOUNTS =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__REMOVE_SIM_ACCOUNTS;
        int GET_SIM_ACCOUNTS =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__GET_SIM_ACCOUNTS;
        int SET_DEFAULT_ACCOUNT_FOR_NEW_CONTACTS =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__SET_DEFAULT_ACCOUNT_FOR_NEW_CONTACTS;
        int GET_DEFAULT_ACCOUNT_FOR_NEW_CONTACTS =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__GET_DEFAULT_ACCOUNT_FOR_NEW_CONTACTS;
        int MOVE_LOCAL_CONTACTS_TO_DEFAULT_ACCOUNT =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__MOVE_LOCAL_CONTACTS_TO_DEFAULT_ACCOUNT;
        int MOVE_SIM_CONTACTS_TO_DEFAULT_ACCOUNT =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__MOVE_SIM_CONTACTS_TO_DEFAULT_ACCOUNT;
        int GET_ELIGIBLE_CLOUD_ACCOUNTS =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__METHOD_CALLED__GET_ELIGIBLE_CLOUD_ACCOUNTS;
    }

    public interface CallerType {
        int CALLER_IS_SYNC_ADAPTER =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__CALLER_TYPE__CALLER_IS_SYNC_ADAPTER;
        int CALLER_IS_NOT_SYNC_ADAPTER =
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__CALLER_TYPE__CALLER_IS_NOT_SYNC_ADAPTER;
    }

    public static void log(LogFields logFields) {
        ContactsProviderStatsLog.write(
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED,
                logFields.getApiType(),
                logFields.getUriType(),
                getCallerType(logFields.isCallerIsSyncAdapter()),
                getResultType(logFields.getException()),
                logFields.getResultCount(),
                getLatencyMicros(logFields.getStartNanos()),
                logFields.getTaskType(),
                logCallMethod() ? logFields.getMethodCalled() : 0,
                logFields.getUid(),
                /*accountType=*/"",
                ContactsProviderStatsLog.CONTACTS_PROVIDER_STATUS_REPORTED__ACCOUNT_DATA_ORIGIN__ACCOUNT_DATA_ORIGIN_UNSPECIFIED,
                /*defaultAccountState*/0
                );
    }

    private static int getCallerType(boolean callerIsSyncAdapter) {
        return callerIsSyncAdapter
                ? CallerType.CALLER_IS_SYNC_ADAPTER : CallerType.CALLER_IS_NOT_SYNC_ADAPTER;
    }


    private static int getResultType(Exception exception) {
        if (exception == null) {
            return ResultType.SUCCESS;
        } else if (exception instanceof IllegalArgumentException) {
            if (logContactSaveInvalidAccountError()
                    && AccountResolver.UNABLE_TO_WRITE_TO_LOCAL_OR_SIM_EXCEPTION_MESSAGE.equals(
                    exception.getMessage())) {
                return ResultType.INVALID_ACCOUNT;
            }
            return ResultType.ILLEGAL_ARGUMENT;
        } else if (exception instanceof UnsupportedOperationException) {
            return ResultType.UNSUPPORTED_OPERATION;
        } else {
            return ResultType.FAIL;
        }
    }

    private static long getLatencyMicros(long startNanos) {
        return (SystemClock.elapsedRealtimeNanos() - startNanos) / 1000;
    }
}

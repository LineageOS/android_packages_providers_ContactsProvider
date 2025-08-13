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

import static com.google.common.truth.Truth.assertThat;

import android.accounts.Account;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(JUnit4.class)
public class InsertAccountValidatorTest {

    @Test
    public void testGetValidationResult_uriAndValuesWithNoAccountSpecified_returnsNoAccountSpecified() {
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{});

        // null account is allowed
        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                ContactsContract.RawContacts.CONTENT_URI, new ContentValues());

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.ACCOUNT_NOT_SPECIFIED);
    }

    @Test
    public void testGetValidationResult_uriAccountNameOnly_returnsResultWithInvalidAccountArgs() {
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{});

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(
                        ContactsContract.RawContacts.ACCOUNT_NAME, "foo").build(),
                new ContentValues());

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.FAILURE_INVALID_ACCOUNT_ARGS);
    }

    @Test
    public void testGetValidationResult_valuesAccountNameOnly_returnsResultWithInvalidAccountArgs() {
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{});

        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "foo");
        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                ContactsContract.RawContacts.CONTENT_URI, values);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.FAILURE_INVALID_ACCOUNT_ARGS);
    }

    @Test
    public void testGetValidationResult_nullUriAndNullValues_returnsResultWithNoAccountSpecified() {
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{});

        // null account is allowed
        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                null, null);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.ACCOUNT_NOT_SPECIFIED);
    }

    @Test
    public void testGetValidationResult_uriAndValuesWithValidAccount_returnsPass() {
        Account account = new Account("test_name", "test_type");
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{account});

        Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(
                ContactsContract.RawContacts.ACCOUNT_NAME, "test_name").appendQueryParameter(
                ContactsContract.RawContacts.ACCOUNT_TYPE, "test_type").build();
        ContentValues values = new ContentValues();

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                uri, values);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.PASS);
    }

    @Test
    public void testGetValidationResult_valuesOnlyWithValidAccount_returnsPass() {
        Account account = new Account("test_name", "test_type");
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{account});

        Uri uri = ContactsContract.RawContacts.CONTENT_URI;
        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "test_name");
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "test_type");

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                uri, values);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.PASS);
    }

    @Test
    public void testGetValidationResult_uriAndValuesWithMatchingAccount_returnsPass() {
        Account account = new Account("test_name", "test_type");
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{account});

        Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(
                ContactsContract.RawContacts.ACCOUNT_NAME, "test_name").appendQueryParameter(
                ContactsContract.RawContacts.ACCOUNT_TYPE, "test_type").build();
        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "test_name");
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "test_type");

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                uri, values);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.PASS);
    }

    @Test
    public void testGetValidationResult_uriAccountMismatchValuesAccount_returnsFailureAccountMismatch() {
        Account account = new Account("test_name", "test_type");
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofNotSet(), Collections.emptyList(), new Account[]{account});

        Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(
                ContactsContract.RawContacts.ACCOUNT_NAME, "test_name_uri").appendQueryParameter(
                ContactsContract.RawContacts.ACCOUNT_TYPE, "test_type_uri").build();
        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "test_name_values");
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "test_type_values");

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                uri, values);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.FAILURE_ACCOUNT_NOT_MATCHING);
    }

    @Test
    public void testGetValidationResult_localAccountRequestedAndDefaultAccountCloud_returnsFailureDefaultAccountCloudRestriction() {
        Account account = new Account("test_name", "test_type");
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofCloud(account), Collections.emptyList(),
                new Account[]{account});

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                null, null, null);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.FAILURE_DEFAULT_ACCOUNT_CLOUD_RESTRICTION);
    }

    @Test
    public void testGetValidationResult_localAccountRequestedAndDefaultAccountLocal_returnsPass() {
        Account account = new Account("test_name", "test_type");
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofLocal(), Collections.emptyList(), new Account[]{account});

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                null, null, null);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.PASS);
    }

    @Test
    public void testGetValidationResult_simAccountRequestedAndDefaultAccountCloud_returnsFailureDefaultAccountCloudRestriction() {
        Account account = new Account("test_name", "test_type");
        List<ContactsContract.SimAccount> simAccounts = new ArrayList<>();
        simAccounts.add(new ContactsContract.SimAccount("SIM1", "sim", 0,
                ContactsContract.SimAccount.ADN_EF_TYPE));
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofCloud(account), simAccounts, new Account[]{account});

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                "SIM1", "sim", null);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.FAILURE_DEFAULT_ACCOUNT_CLOUD_RESTRICTION);
    }

    @Test
    public void testGetValidationResult_simAccountRequestedAndDefaultAccountCloudAndAllowSimWriteOnCloudDcaBypassEnabled_returnsPass() {
        Account account = new Account("test_name", "test_type");
        List<ContactsContract.SimAccount> simAccounts = new ArrayList<>();
        simAccounts.add(new ContactsContract.SimAccount("SIM1", "sim", 0,
                ContactsContract.SimAccount.ADN_EF_TYPE));
        InsertAccountValidator validator = new InsertAccountValidator(
                DefaultAccountAndState.ofCloud(account), simAccounts, new Account[]{account});
        validator.setAllowSimWriteOnCloudDcaBypassEnabled(true);

        InsertAccountValidator.ValidationResultWithDetails result = validator.getValidationResult(
                "SIM1", "sim", null);

        assertThat(result.getValidationResult()).isEqualTo(
                InsertAccountValidator.ValidationResult.PASS);
    }

}

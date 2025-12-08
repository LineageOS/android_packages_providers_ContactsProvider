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

import static android.provider.ContactsContract.SimAccount.SDN_EF_TYPE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.content.IContentService;
import android.content.Intent;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Settings.AccountAttributes;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.Collections;

@SmallTest
@RunWith(JUnit4.class)
public class AccountAttributesEvaluatorTest extends BaseContactsProvider2Test {

    private static final Account CLOUD_ACCOUNT = new Account("user@cloud.com", "com.cloud.type");
    private static final Account SIM_ACCOUNT = new Account("8675309", "com.sim.type");
    private static final Account XML_ACCOUNT = new Account("user@xml.com", "com.xml.type");

    @Mock
    private AccountManager mMockAccountManager;
    @Mock
    private IContentService mMockContentService;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private Context mMockContext;

    private ContactsDatabaseHelper mDbHelper;
    private AccountAttributesEvaluator mEvaluator;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        mDbHelper = getContactsProvider().getDatabaseHelper();

        // Mock the context to return our mocked PackageManager
        doReturn(mMockContext).when(mMockContext).getApplicationContext();

        // By default, there is no sync adapter and authenticator types.
        doReturn(new SyncAdapterType[0]).when(mMockContentService).getSyncAdapterTypes();
        doReturn(new AuthenticatorDescription[0])
                .when(mMockAccountManager).getAuthenticatorTypes();
        doReturn(new Account[0]).when(mMockAccountManager).getAccounts();

        mEvaluator = new AccountAttributesEvaluator(mMockPackageManager, mDbHelper,
                mMockAccountManager, mMockContentService);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testEvaluate_localAccount_hasLocalAttribute() {
        AccountWithDataSet localAccount = AccountWithDataSet.LOCAL;
        long attributes = mEvaluator.evaluate(localAccount);
        assertEquals(AccountAttributes.ATTRIBUTE_DATA_ORIGIN_LOCAL, attributes);
    }

    @Test
    public void testEvaluate_simAccount_hasSimAttribute() {
        AccountWithDataSet simAccount = new AccountWithDataSet(SIM_ACCOUNT.name, SIM_ACCOUNT.type,
                null);
        mDbHelper.createSimAccountIdInTransaction(simAccount, 1, SDN_EF_TYPE);
        long attributes = mEvaluator.evaluate(simAccount);
        assertTrue((attributes & AccountAttributes.ATTRIBUTE_DATA_ORIGIN_SIM) != 0);
    }

    @Test
    public void testEvaluate_cloudAccount_hasCloudAttribute() {
        doReturn(new Account[]{CLOUD_ACCOUNT}).when(mMockAccountManager).getAccounts();
        AccountWithDataSet cloudAccount = new AccountWithDataSet(CLOUD_ACCOUNT.name,
                CLOUD_ACCOUNT.type, null);
        long attributes = mEvaluator.evaluate(cloudAccount);
        assertTrue((attributes & AccountAttributes.ATTRIBUTE_DATA_ORIGIN_CLOUD) != 0);
    }

    @Test
    public void testEvaluate_biDirectionalSync_hasUpAndDownSyncAttributes() throws Exception {
        setupSyncAdapter(CLOUD_ACCOUNT.type, true);
        AccountWithDataSet cloudAccount = new AccountWithDataSet(CLOUD_ACCOUNT.name,
                CLOUD_ACCOUNT.type, null);
        long attributes = mEvaluator.evaluate(cloudAccount);
        long expectedSyncAttrs = AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC
                | AccountAttributes.ATTRIBUTE_SYNC_MODE_UP_SYNC;
        assertTrue((attributes & expectedSyncAttrs) == expectedSyncAttrs);
    }

    @Test
    public void testEvaluate_downloadOnlySync_hasOnlyDownSyncAttribute() throws Exception {
        setupSyncAdapter(CLOUD_ACCOUNT.type, false);
        AccountWithDataSet cloudAccount = new AccountWithDataSet(CLOUD_ACCOUNT.name,
                CLOUD_ACCOUNT.type, null);
        long attributes = mEvaluator.evaluate(cloudAccount);
        assertTrue((attributes & AccountAttributes.ATTRIBUTE_SYNC_MODE_DOWN_SYNC) != 0);
        assertEquals(0, (attributes & AccountAttributes.ATTRIBUTE_SYNC_MODE_UP_SYNC));
    }

    @Test
    public void testDataType_success_returnsCustomAttribute() throws Exception {
        // 1. Arrange: Create XML with a valid custom mimetype.
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<ContactsAccountType xmlns:android=\"http://schemas.android"
                + ".com/apk/res/android\">"
                + "  <ContactsDataKind android:mimeType=\"vnd.android.cursor.item/test.custom"
                + ".mimetype\"/>"
                + "</ContactsAccountType>";
        XmlResourceParser parser = createXmlParserFromContent(xml);
        setupMocksForDataTypeTest(XML_ACCOUNT, parser);

        // 2. Act
        AccountWithDataSet account = new AccountWithDataSet(XML_ACCOUNT.name, XML_ACCOUNT.type,
                null);
        long attributes = mEvaluator.evaluate(account);

        // 3. Assert
        assertTrue("The custom data type attribute should be set",
                (attributes & AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED) != 0);
    }

    @Test
    public void testDataType_noContactsXml_returnsZero() throws Exception {
        // Arrange: Setup mocks to return null when loading the XML parser.
        setupMocksForDataTypeTest(XML_ACCOUNT, null);
        AccountWithDataSet account = new AccountWithDataSet(XML_ACCOUNT.name, XML_ACCOUNT.type,
                null);

        // Act & Assert
        assertEquals(0, mEvaluator.evaluate(account)
                & AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED);
    }

    @Test
    public void testDataType_dataSetMismatch_returnsZero() throws Exception {
        // Arrange: Create XML with dataSet="plus" but test an account with dataSet=null.
        final String xml = "<ContactsAccountType dataSet=\"plus\"></ContactsAccountType>";
        setupMocksForDataTypeTest(XML_ACCOUNT, createXmlParserFromContent(xml));
        AccountWithDataSet account = new AccountWithDataSet(XML_ACCOUNT.name, XML_ACCOUNT.type,
                null);

        // Act & Assert
        assertEquals(0, mEvaluator.evaluate(account)
                & AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED);
    }

    @Test
    public void testDataType_malformedXml_returnsZero() throws Exception {
        // Arrange: Create an unclosed tag, which will cause an XmlPullParserException.
        final String xml = "<ContactsAccountType><ContactsDataKind>";
        setupMocksForDataTypeTest(XML_ACCOUNT, createXmlParserFromContent(xml));
        AccountWithDataSet account = new AccountWithDataSet(XML_ACCOUNT.name, XML_ACCOUNT.type,
                null);

        // Act & Assert
        assertEquals(0, mEvaluator.evaluate(account)
                & AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED);
    }

    @Test
    public void testDataType_missingMimeTypeAttribute_returnsZero() throws Exception {
        // Arrange: The XML is valid but the required attribute is missing.
        final String xml = "<ContactsAccountType><ContactsDataKind/></ContactsAccountType>";
        setupMocksForDataTypeTest(XML_ACCOUNT, createXmlParserFromContent(xml));
        AccountWithDataSet account = new AccountWithDataSet(XML_ACCOUNT.name, XML_ACCOUNT.type,
                null);

        // Act & Assert
        assertEquals(0, mEvaluator.evaluate(account)
                & AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED);
    }

    @Test
    public void testDataType_emptyMimeTypeString_returnsZero() throws Exception {
        // Arrange: The attribute exists but is an empty string.
        final String xml =
                "<ContactsAccountType xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                        + "  <ContactsDataKind android:mimeType=\"\"/>"
                        + "</ContactsAccountType>";
        setupMocksForDataTypeTest(XML_ACCOUNT, createXmlParserFromContent(xml));
        AccountWithDataSet account = new AccountWithDataSet(XML_ACCOUNT.name, XML_ACCOUNT.type,
                null);

        // Act & Assert
        assertEquals(0, mEvaluator.evaluate(account)
                & AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED);
    }

    @Test
    public void testDataType_noAuthenticator_returnsZero() {
        // Arrange: Mock AccountManager to return no authenticators for the account type.
        doReturn(new AuthenticatorDescription[0]).when(mMockAccountManager).getAuthenticatorTypes();
        AccountWithDataSet account = new AccountWithDataSet(XML_ACCOUNT.name, XML_ACCOUNT.type,
                null);

        // Act & Assert
        assertEquals(0, mEvaluator.evaluate(account)
                & AccountAttributes.ATTRIBUTE_DATA_TYPE_CUSTOM_DECLARED);
    }


    private void setupSyncAdapter(String accountType, boolean supportsUploading) throws Exception {
        SyncAdapterType syncAdapter = new SyncAdapterType(
                ContactsContract.AUTHORITY, accountType, true, supportsUploading);
        doReturn(new SyncAdapterType[]{syncAdapter}).when(
                mMockContentService).getSyncAdapterTypes();
    }

    /**
     * Creates a mock XmlResourceParser that reads from the given string content.
     * <p>
     * This is a test utility to dynamically generate XML for parsing, rather than
     * relying on static XML files in test resources. It uses a real XmlPullParser
     * internally and delegates calls from the mock to the real parser.
     *
     * @param xmlContent The string containing the XML document to be parsed.
     * @return A mock XmlResourceParser ready for use.
     */
    private XmlResourceParser createXmlParserFromContent(String xmlContent) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();

        factory.setNamespaceAware(true);

        XmlPullParser realParser = factory.newPullParser();
        realParser.setInput(new StringReader(xmlContent));

        XmlResourceParser mockParser = Mockito.mock(XmlResourceParser.class);

        when(mockParser.next()).thenAnswer(invocation -> realParser.next());
        when(mockParser.getDepth()).thenAnswer(invocation -> realParser.getDepth());
        when(mockParser.getName()).thenAnswer(invocation -> realParser.getName());
        when(mockParser.getAttributeValue(any(), any())).thenAnswer(
                invocation -> realParser.getAttributeValue(
                        invocation.getArgument(0), invocation.getArgument(1)));

        doNothing().when(mockParser).close();

        return mockParser;
    }

    /**
     * Sets up the complex mock chain required for data type attribute testing.
     * <p>
     * This helper configures mocks for AccountManager and PackageManager to lead the
     * code under test to use the provided XmlResourceParser for a given account.
     *
     * @param account The account for which to set up the mocks.
     * @param parser  The XmlResourceParser to be returned, or null if no contacts.xml
     *                should be found.
     */
    private void setupMocksForDataTypeTest(Account account, XmlResourceParser parser)
            throws Exception {
        final String accountType = account.type;
        final String packageName = mContext.getPackageName();

        AuthenticatorDescription authenticator = new AuthenticatorDescription(
                accountType, packageName, 0, 0, 0, 0);
        doReturn(new AuthenticatorDescription[]{authenticator})
                .when(mMockAccountManager).getAuthenticatorTypes();

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = Mockito.spy(new ServiceInfo());
        resolveInfo.serviceInfo.metaData = new Bundle();
        doReturn(Collections.singletonList(resolveInfo))
                .when(mMockPackageManager).queryIntentServices(any(Intent.class), anyInt());

        doReturn(parser)
                .when(resolveInfo.serviceInfo).loadXmlMetaData(eq(mMockPackageManager),
                        any(String.class));
    }
}

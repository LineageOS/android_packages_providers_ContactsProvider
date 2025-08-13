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

import android.annotation.Nullable;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;

import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.ContactsProviderStatusReported;
import com.android.providers.contacts.util.ContactsProviderStatsLog;

import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Test implementation of {@link ContactsProviderStatsLog} that records atoms in-memory instead of
 * writing them so that the logging can be verified.
 */
public class RecordingContactsProviderStatsLog extends ContactsProviderStatsLog {

    private final List<Atom> mLoggedAtoms = new CopyOnWriteArrayList<>();
    private final ExtensionRegistryLite mExtensionRegistry = ExtensionRegistryLite.newInstance();

    @Override
    public void write(int code, int arg1, int arg2, int arg3, int arg4, int arg5, long arg6,
            int arg7, int arg8, int arg9, String arg10, int arg11, int arg12) {
        StatsEvent event = StatsEvent.newBuilder()
                .setAtomId(code)
                .writeInt(arg1)
                .writeInt(arg2)
                .writeInt(arg3)
                .writeInt(arg4)
                .writeInt(arg5)
                .writeLong(arg6)
                .writeInt(arg7)
                .writeInt(arg8)
                .writeInt(arg9)
                .writeString(arg10)
                .writeInt(arg11)
                .writeInt(arg12)
                .build();
        try {
            mLoggedAtoms.add(StatsEventTestUtils.convertToAtom(event, mExtensionRegistry));
        } catch (InvalidProtocolBufferException e) {
            // OK to blow up since this is testing code.
            throw new RuntimeException(e);
        }
    }

    /** Gets the count of events that have been logged so far. */
    public int getLoggedEventCount() {
        return mLoggedAtoms.size();
    }

    /** Gets the count of events that satisfy the given predicate that were logged. */
    public int getLoggedEventCount(Predicate<ContactsProviderStatusReported> predicate) {
        return (int) getLoggedEvents().stream().filter(predicate).count();
    }

    /** Gets the events that have been logged. */
    public List<ContactsProviderStatusReported> getLoggedEvents() {
        return mLoggedAtoms.stream().map(Atom::getContactsProviderStatusReported).collect(
                Collectors.toList());
    }

    /** Gets the events that satisfy the given predicate that were logged. */
    public List<ContactsProviderStatusReported> getLoggedEvents(
            Predicate<ContactsProviderStatusReported> predicate) {
        return mLoggedAtoms.stream().map(Atom::getContactsProviderStatusReported).filter(predicate)
                .collect(Collectors.toList());
    }

    /** Gets the last logged event. */
    @Nullable
    public ContactsProviderStatusReported getLastLoggedEvent() {
        Atom lastLoggedAtom = mLoggedAtoms.isEmpty() ? null : mLoggedAtoms.getLast();
        if (lastLoggedAtom == null) {
            return null;
        }
        return lastLoggedAtom.getContactsProviderStatusReported();
    }

    /** Gets the last logged event that satisfies the given predicate. */
    @Nullable
    public ContactsProviderStatusReported getLastLoggedEvent(
            Predicate<ContactsProviderStatusReported> predicate) {
        return getLoggedEvents().reversed().stream().filter(predicate).findFirst().orElse(null);
    }

    /** Indicates whether the given event is a raw contact insert event. */
    public static boolean isRawContactInsertEvent(ContactsProviderStatusReported event) {
        return event.getUriType() == ContactsProvider2.RAW_CONTACTS
                && event.getApiType() == ContactsProviderStatusReported.ApiType.INSERT;
    }
}

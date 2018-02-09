package org.thoughtcrime.securesms.recipients;

import android.os.Parcel;

import org.junit.Test;
import org.thoughtcrime.securesms.BaseUnitTest;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.recipients.RecipientProvider.RecipientDetails;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.whispersystems.libsignal.util.guava.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RecipientTest extends BaseUnitTest {
    @Test
    public void testChatNameInitialization() {
        Parcel p = mock(Parcel.class);
        when(p.readString()).thenReturn("New Address");

        Address address = new Address(p);
        RecipientSettings mockSettings = mock(RecipientSettings.class);
        when(mockSettings.getChatName()).thenReturn("Test");
        Optional<RecipientDetails> details = Optional.of(new RecipientDetails("Test", 123L, true, mockSettings, null));

        Recipient recipient = new Recipient(address, null, details, mock(ListenableFutureTask.class));

        assertEquals(recipient.getChatName(), "Test");
    }

    @Test
    public void testChatNameSetter() {
        Parcel p = mock(Parcel.class);
        when(p.readString()).thenReturn("New Address");

        Address address = new Address(p);
        RecipientProvider.RecipientDetails details = new RecipientProvider.RecipientDetails("Test", 123L, true, null, null);
        Recipient recipient = new Recipient(address, details);

        recipient.setChatName("Test");
        assertEquals(recipient.getChatName(), "Test");
    }
}

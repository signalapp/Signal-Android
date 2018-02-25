package org.thoughtcrime.securesms;

import android.content.Context;

import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;


@RunWith(PowerMockRunner.class)
@PrepareForTest({DatabaseFactory.class})
public class NicknameMocks extends BaseUnitTest {
    protected DatabaseFactory   databaseFactory;
    protected Recipient         recipient;
    protected RecipientDatabase recipientDatabase;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        databaseFactory = mock(DatabaseFactory.class);
        recipient = mock(Recipient.class);
        recipientDatabase = mock(RecipientDatabase.class);
    }

    protected void setupSetNicknameMethod() {
        when(recipientDatabase.setNickname(recipient, "test")).thenReturn(true)
                .thenReturn(false);

        when(recipientDatabase.setNickname(recipient, "new test")).thenReturn(true)
                .thenReturn(false);

    }

    // TODO add removeNickname mock
    protected void setupRemoveNicknameMethod() {

    }

    protected void setupRecipientObject() {
        recipient = mock(Recipient.class);
    }

    protected void setupStaticRecipientDatabase() {
        PowerMockito.mockStatic(DatabaseFactory.class);
        BDDMockito.given(DatabaseFactory.getInstance(context)).willReturn(databaseFactory);
        BDDMockito.given(DatabaseFactory.getRecipientDatabase(context)).willReturn(recipientDatabase);
    }
}

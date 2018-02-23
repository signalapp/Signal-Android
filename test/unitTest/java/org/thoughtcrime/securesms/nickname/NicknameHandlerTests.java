package org.thoughtcrime.securesms.nickname;

import org.junit.Before;
import org.junit.Test;
import org.thoughtcrime.securesms.NicknameHandler;
import org.thoughtcrime.securesms.NicknameMocks;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by BABO99 on 2018-02-23.
 */

public class NicknameHandlerTests extends NicknameMocks {
    @Before
    public void setUp() throws Exception {
        super.setUp();

        super.databaseFactory = mock(DatabaseFactory.class);
        super.recipient = mock(Recipient.class);
        super.recipient2 = mock(Recipient.class);
        super.recipientDatabase = mock(RecipientDatabase.class);

        super.setupRecipientObject();
        super.setupRemoveNicknameMethod();
        super.setupStaticRecipientDatabase();
        super.setupSetNicknameMethod();

    }

    @Test
    public void testSetNicknameNewNickname() {
        super.setupSetNicknameMethod();

        NicknameHandler handler = new NicknameHandler(super.context);
        boolean res = handler.setRecipientDatabase(DatabaseFactory.getRecipientDatabase(super.context))
                .setNickname(recipient, "test");

        assertTrue(res);
        verify(recipientDatabase).setNickname(recipient,"test");
    }

    @Test
    public void testSetNicknameSameOldeName() {
        super.setupSetNicknameMethod();

        NicknameHandler handler = new NicknameHandler(super.context);
        boolean resNewNickname = handler.setRecipientDatabase(DatabaseFactory.getRecipientDatabase(super.context))
                .setNickname(recipient, "test");

        boolean resSameNickname = handler.setRecipientDatabase(DatabaseFactory.getRecipientDatabase(super.context))
                .setNickname(recipient, "test");

        assertTrue(resNewNickname);
        assertFalse(resSameNickname);
        verify(recipientDatabase, times(2)).setNickname(recipient,"test");
    }

    @Test
    public void testSetNicknameSameOldAndThenNew() {
        super.setupSetNicknameMethod();

        NicknameHandler handler = new NicknameHandler(super.context);
        boolean resNewNickname = handler.setRecipientDatabase(DatabaseFactory.getRecipientDatabase(super.context))
                .setNickname(recipient, "test");

        boolean resSameNickname = handler.setRecipientDatabase(DatabaseFactory.getRecipientDatabase(super.context))
                .setNickname(recipient, "test");

        boolean resNewNicknameNewInput = handler.setRecipientDatabase(DatabaseFactory.getRecipientDatabase(super.context))
                .setNickname(recipient, "new test");

        boolean resNewNicknameSameNewInput = handler.setRecipientDatabase(DatabaseFactory.getRecipientDatabase(super.context))
                .setNickname(recipient, "new test");

        assertTrue(resNewNickname);
        assertFalse(resSameNickname);

        assertTrue(resNewNicknameNewInput);
        assertFalse(resNewNicknameSameNewInput);

        verify(recipientDatabase, times(2)).setNickname(recipient,"test");
        verify(recipientDatabase, times(2)).setNickname(recipient,"new test");


    }
}

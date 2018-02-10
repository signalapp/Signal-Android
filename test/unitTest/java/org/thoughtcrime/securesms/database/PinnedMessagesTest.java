package org.thoughtcrime.securesms.database;

import org.junit.runners.JUnit4;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.thoughtcrime.securesms.PinnedMessagesMocks;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


/**
 * Created by BABO99 on 2018-02-08.
 */

@RunWith(JUnit4.class)
public class PinnedMessagesTest extends PinnedMessagesMocks {


    @Override
    public void setUp() {
        messagingDatabase = mock(MessagingDatabase.class);
        mmsSmsDatabase = mock(MmsSmsDatabase.class);

        super.setUpPinMethod();
        super.setUpUnpinMethod();
    }

    @Test
    // This test is to check the behaviour of the method
    public void testPinMessageMethod() {
        assertEquals(messagingDatabase.pinMessage(1), true);
        assertEquals(messagingDatabase.pinMessage(2), false);
        assertEquals(messagingDatabase.pinMessage(3), false);

        verify(messagingDatabase).pinMessage(3);
        verify(messagingDatabase).pinMessage(3);
        verify(messagingDatabase).pinMessage(3);
    }

    @Test
    // This test is to check the behaviour of the method
    public void testUnpinMessageMethod() {
        assertEquals(messagingDatabase.unpinMessage(1), true);
        assertEquals(messagingDatabase.unpinMessage(2), false);
        assertEquals(messagingDatabase.unpinMessage(3), false);

        verify(messagingDatabase).unpinMessage(1);
        verify(messagingDatabase).unpinMessage(2);
        verify(messagingDatabase).unpinMessage(3);
    }

}

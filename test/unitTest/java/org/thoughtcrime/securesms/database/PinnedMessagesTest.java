package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.runners.JUnit4;
import org.junit.runner.RunWith;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import org.mockito.Mockito;
import org.thoughtcrime.securesms.BaseUnitTest;

import static junit.framework.Assert.assertEquals;


/**
 * Created by BABO99 on 2018-02-08.
 */

@RunWith(JUnit4.class)
public class PinnedMessagesTest extends BaseUnitTest {
    private CursorRecyclerViewAdapter adapter;
    private Context context;
    private Cursor cursor;
    private MmsSmsDatabase mmsSmsDb;
    private DatabaseFactory dbFactory;
    private MessagingDatabase messagingDb;

    @Override
    public void setUp() {
        messagingDb = mock(MessagingDatabase.class);
        mmsSmsDb    = mock(MmsSmsDatabase.class);

        this.setUpPinMethod();
        this.setUpUnpinMethod();
    }

    private void setUpPinMethod() {
        when(messagingDb.pinMessage(1)).thenReturn(true); // message id 1 is pinned
        when(messagingDb.pinMessage(2)).thenReturn(false); // message id 2 is already pinned
        when(messagingDb.pinMessage(3)).thenReturn(false); // message id 3 does not exists
    }

    private void setUpUnpinMethod() {
        when(messagingDb.unpinMessage(1)).thenReturn(true);
        when(messagingDb.unpinMessage(2)).thenReturn(false);
        when(messagingDb.unpinMessage(3)).thenReturn(false);
    }

    @Test
    // This test is to check the behaviour of the method
    public void testPinMessageMethod() {
        assertEquals(messagingDb.pinMessage(1), true);
        assertEquals(messagingDb.pinMessage(2), false);
        assertEquals(messagingDb.pinMessage(3), false);

        verify(messagingDb).pinMessage(3);
        verify(messagingDb).pinMessage(3);
        verify(messagingDb).pinMessage(3);
    }

    @Test
    // This test is to check the behaviour of the method
    public void testUnpinMessageMethod() {
        assertEquals(messagingDb.unpinMessage(1), true);
        assertEquals(messagingDb.unpinMessage(2), false);
        assertEquals(messagingDb.unpinMessage(3), false);

        verify(messagingDb).unpinMessage(1);
        verify(messagingDb).unpinMessage(2);
        verify(messagingDb).unpinMessage(3);
    }

}

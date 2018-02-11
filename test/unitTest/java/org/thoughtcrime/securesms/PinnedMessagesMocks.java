package org.thoughtcrime.securesms;

import android.content.Context;

import org.junit.runner.RunWith;
import org.mockito.BDDMockito;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;

import static org.mockito.Mockito.when;


/**
 * Created by BABO99 on 2018-02-09.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(DatabaseFactory.class)
public class PinnedMessagesMocks extends BaseUnitTest {
    protected Context                   context;
    protected DatabaseFactory           databaseFactory;
    protected MessageRecord             messageRecordSms;
    protected MessageRecord             messageRecordMms;
    protected MessagingDatabase         messagingDatabase;
    protected MmsDatabase               mmsDatabase;
    protected MmsSmsDatabase            mmsSmsDatabase;
    protected SmsDatabase               smsDatabase;

    protected void setUpPinMethod() {
        when(messagingDatabase.pinMessage(1)).thenReturn(true); // message id 1 is pinned
        when(messagingDatabase.pinMessage(2)).thenReturn(false); // message id 2 is already pinned
        when(messagingDatabase.pinMessage(3)).thenReturn(false); // message id 3 does not exists
    }

    protected void setUpUnpinMethod() {
        when(messagingDatabase.unpinMessage(1)).thenReturn(true);
        when(messagingDatabase.unpinMessage(2)).thenReturn(false);
        when(messagingDatabase.unpinMessage(3)).thenReturn(false);
    }

    protected void setUpMessageRecord() {
        when(messageRecordSms.isMms()).thenReturn(false);
        when(messageRecordMms.isMms()).thenReturn(true);

        when(messageRecordSms.getId()).thenReturn(1l);
        when(messageRecordMms.getId()).thenReturn(1l);
    }

    protected void setUpStaticMessagingDatabase() throws Exception {
        PowerMockito.mockStatic(DatabaseFactory.class);
        BDDMockito.given(DatabaseFactory.getInstance(context)).willReturn(databaseFactory);
        BDDMockito.given(DatabaseFactory.getSmsDatabase(context)).willReturn(smsDatabase);
        BDDMockito.given(DatabaseFactory.getMmsDatabase(context)).willReturn(mmsDatabase);

        when( smsDatabase.pinMessage(1)).thenReturn(true);
        when( smsDatabase.pinMessage(2)).thenReturn(false);
        when( smsDatabase.pinMessage(3)).thenReturn(false);

        when( mmsDatabase.pinMessage(1)).thenReturn(true);
        when( mmsDatabase.pinMessage(2)).thenReturn(false);
        when( mmsDatabase.pinMessage(3)).thenReturn(false);

        when( smsDatabase.unpinMessage(1)).thenReturn(false); // originally not pinned
        when( smsDatabase.unpinMessage(2)).thenReturn(true);  // pinned

        when( mmsDatabase.unpinMessage(1)).thenReturn(false); // originally not pinned
        when( mmsDatabase.unpinMessage(2)).thenReturn(true);  // pinned
    }

    public void setMockGetIdReturnValue(long val) {
        when(messageRecordSms.getId()).thenReturn(val);
        when(messageRecordMms.getId()).thenReturn(val);
    }
}

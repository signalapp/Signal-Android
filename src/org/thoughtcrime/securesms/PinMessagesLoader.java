package org.thoughtcrime.securesms;

import android.content.Context;
import android.database.Cursor;

import javax.annotation.Nullable;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;

/**
 * Created by BABO99 on 2018-02-05.
 */

public class PinMessagesLoader extends AbstractCursorLoader {

    private final long      threadID;
    private       long      limit;

    public PinMessagesLoader(Context context, long threadID, @Nullable long limit) {
        super(context);
        this.threadID = threadID;
        this.limit    = limit;
    }

    @Override
    public Cursor getCursor() {
        MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(getContext());
        return db.getPinnedMessages(this.threadID);
    }
}

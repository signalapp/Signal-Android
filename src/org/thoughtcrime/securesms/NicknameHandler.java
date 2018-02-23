package org.thoughtcrime.securesms;

import android.content.Context;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;

public class NicknameHandler {
    private Context context;
    private RecipientDatabase recipientDatabase;

    public NicknameHandler(Context context) {
        this.context = context;
    }

    public NicknameHandler setContext(Context context) {
        this.context = context;
        return this;
    }

    public NicknameHandler setRecipientDatabase(RecipientDatabase recipientDatabase) {
        this.recipientDatabase = recipientDatabase;
        return this;
    }

    public boolean setNickname(Recipient recipient, String nickname) {
        setupDatabaseHandler();
        return recipientDatabase.setNickname(recipient, nickname);
    }

    // TODO by DAN
    public boolean removeNickname(Recipient recipient) {
        setupDatabaseHandler();

        // TODO call the correct remove method
        return true;
    }

    public NicknameHandler setupDatabaseHandler() {
        if (this.recipientDatabase == null) {
            this.recipientDatabase = DatabaseFactory.getRecipientDatabase(this.context);
        }
        return this;
    }
}

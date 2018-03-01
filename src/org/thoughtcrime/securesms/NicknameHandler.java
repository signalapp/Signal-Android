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

    /**
     * This method allows the developer to set the underlining database
     * to any kind of database object passed in the parameter
     * This method is implemented for only testing purposes inorder
     * to pass mocked database object
     * @param recipientDatabase
     * @return
     */
    public NicknameHandler setRecipientDatabase(RecipientDatabase recipientDatabase) {
        this.recipientDatabase = recipientDatabase;
        return this;
    }

    /**
     * This method responsible for setting the nick name
     * of a recipient. If the new nickname is identical to the
     * old one, then it will return false, otherwise, the nickname
     * will be updated and this will return true.
     * @param recipient
     * @param nickname
     * @return boolean indicating whether the nickname was set or not
     */
    public void setNickname(Recipient recipient, String nickname) {
        this.setupDatabaseHandler();
        this.recipientDatabase.setNickname(recipient, nickname);
    }

    public void removeNickname(Recipient recipient) {
        this.setupDatabaseHandler();
        this.recipientDatabase.setNickname(recipient, null);
    }

    /**
     * The method helps the developer to set the proper database
     * without worrying about the details. This method is used
     * in production only.
     * @return NicknameHandler
     */
    public NicknameHandler setupDatabaseHandler() {
        if (this.recipientDatabase == null) {
            this.recipientDatabase = DatabaseFactory.getRecipientDatabase(this.context);
        }
        return this;
    }
}

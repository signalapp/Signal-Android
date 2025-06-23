package org.thoughtcrime.securesms.mms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * Represents the information required to find the {@link MessageRecord} pointed to by a quote.
 */
public class QuoteId {

  private static final String TAG = Log.tag(QuoteId.class);

  private static final String ID                 = "id";
  private static final String AUTHOR_DEPRECATED  = "author";
  private static final String AUTHOR             = "author_id";

  private final long        id;
  private final RecipientId author;

  public QuoteId(long id, @NonNull RecipientId author) {
    this.id     = id;
    this.author = author;
  }

  public long getId() {
    return id;
  }

  public @NonNull RecipientId getAuthor() {
    return author;
  }

  public @NonNull String serialize() {
    try {
      JSONObject object = new JSONObject();
      object.put(ID, id);
      object.put(AUTHOR, author.serialize());
      return object.toString();
    } catch (JSONException e) {
      Log.e(TAG, "Failed to serialize to json", e);
      return "";
    }
  }

  public static @Nullable QuoteId deserialize(@NonNull Context context, @NonNull String serialized) {
    try {
      JSONObject  json = new JSONObject(serialized);
      RecipientId id;
      if (json.has(AUTHOR)) {
        id = RecipientId.from(json.getString(AUTHOR));
      } else {
        Recipient recipient = Recipient.external(json.getString(AUTHOR_DEPRECATED));
        if (recipient != null) {
          id = recipient.getId();
        } else {
          return null;
        }
      }

      return new QuoteId(json.getLong(ID), id);
    } catch (JSONException e) {
      Log.e(TAG, "Failed to deserialize from json", e);
      return null;
    }
  }
}

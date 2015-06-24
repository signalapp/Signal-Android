package de.gdata.messaging.util;

import android.content.Context;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by jan on 23.06.15.
 */
public class ProfileAccessor {

  private static ImageSlide profilePicture;
  private static GDataPreferences preferences;

  public static GDataPreferences getPreferences(Context context) {
    if(preferences == null) {
      preferences = new GDataPreferences(context);
    }
    return preferences;
  }

  public static void setProfilePicture(Context context, ImageSlide profileP) {
    if(profileP != null) {
      profilePicture = profileP;
      getPreferences(context).setProfilePictureUri(profilePicture.getUri().toString());
    }
  }

  public static Slide getProfilePictureSlide(Context context) {
    if(profilePicture == null) {
      Uri profilePictureUri = Uri.parse(getPreferences(context).getProfilePictureUri());
      try {
        profilePicture = new ImageSlide(context, profilePictureUri);
        Log.d("MYLOG","MYLOG IMAGE " + profilePicture.getContentType());
      } catch (IOException e) {
        Log.w("GDATA", e);
      } catch (BitmapDecodingException e) {
        Log.w("GDATA",e);
      }
    }
    return profilePicture;
  }
  public static void setProfileStatus(Context context, String status) {
      getPreferences(context).setProfileStatus(status);
  }
  public static String getProfileStatus(Context context) {
    return getPreferences(context).getProfileStatus();
  }
  public static void sendProfileUpdate(final Context context, final MasterSecret masterSecret, Recipients recipients)
      throws InvalidMessageException {
    Log.d("MYLOG","sendProfileUpdate");
    SlideDeck slideDeck = new SlideDeck();
    slideDeck.addSlide(ProfileAccessor.getProfilePictureSlide(context));
    OutgoingMediaMessage outgoingMessage = new OutgoingMediaMessage(context, recipients, slideDeck,
        getProfileStatus(context), ThreadDatabase.DistributionTypes.DEFAULT);

    if (true/*encrypted*/) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessage);
      outgoingMessage.setProfileUpdateMessage();
      new AsyncTask<OutgoingMediaMessage, Void, Long>() {
        @Override
        protected Long doInBackground(OutgoingMediaMessage... messages) {
          return MessageSender.send(context, masterSecret, messages[0], 0, false);
        }

        @Override
        protected void onPostExecute(Long result) {
        //  sendComplete(result);
        }
      }.execute(outgoingMessage);
    }
  }
}

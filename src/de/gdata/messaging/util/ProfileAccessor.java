package de.gdata.messaging.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;

import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.ThumbnailTransform;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import java.io.IOException;
import java.util.List;

import ws.com.google.android.mms.pdu.PduPart;

/**
 * Created by jan on 23.06.15.
 */
public class ProfileAccessor {

  private static ImageSlide profilePicture;
  private static GDataPreferences preferences;
  private static MasterSecret mMasterSecret;


  public static GDataPreferences getPreferences(Context context) {
    if (preferences == null) {
      preferences = new GDataPreferences(context);
    }
    return preferences;
  }

  public static void setProfilePartId(Context context, Long profileId, Long partId) {
    DatabaseFactory.getPartDatabase(context).deleteParts(getPartId(context, profileId+"").getUniqueId());
    getPreferences(context).setProfilePartId(profileId + "", partId);
  }
  public static void setProfilePartRow(Context context, Long profileId, Long partRow) {
    getPreferences(context).setProfilePartRow(profileId + "", partRow);
  }

  public static PartDatabase.PartId getPartId(Context context, String profileId) {
    return new PartDatabase.PartId(getPreferences(context).getProfilePartRow(profileId + ""), getPreferences(context).getProfilePartId(profileId + ""));
  }

  public static void setProfilePicture(Context context, ImageSlide profileP) {
    if (profileP != null) {
      profilePicture = profileP;
      if (profilePicture.getThumbnailUri() != null && profilePicture.getUri() != null) {
        getPreferences(context).setProfilePictureUri(profilePicture.getUri().toString());
      }
    }
  }
  public static void deleteMyProfilePicture(Context context) {
      if (profilePicture.getThumbnailUri() != null && profilePicture.getUri() != null) {
        getPreferences(context).setProfilePictureUri("");
        profilePicture = null;
      }
  }
  public static ImageSlide getMyProfilePicture(Context context) {
    Uri profilePictureUri = Uri.parse(getPreferences(context).getProfilePictureUri());
    if(TextUtils.isEmpty(profilePictureUri.toString())) {
      Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.drawable.ic_contact_picture_removed);
      profilePictureUri = uri;
    }
    try {
      profilePicture = new ImageSlide(context, profilePictureUri);
    } catch (IOException e) {
      Log.w("GDATA", e);
    } catch (BitmapDecodingException e) {
      Log.w("GDATA", e);
    }
    return profilePicture;
  }
  public static PartDatabase.PartId getPartIdForUri(Context context, String uri) {
    return new PartDatabase.PartId(getPreferences(context).getProfilePartRow(uri + ""), getPreferences(context).getProfilePartId(uri + ""));
  }
  public static void savePartIdForUri(Context context, String uri, Long partId) {
    getPreferences(context).setProfilePartId(uri + "", partId);
  }
  public static void savePartRowForUri(Context context, String uri, Long partRow) {
    getPreferences(context).setProfilePartRow(uri + "", partRow);
  }
  public static ImageSlide getSlideForUri(Context context, MasterSecret masterSecret, String uriToPart) {
    PartDatabase database = DatabaseFactory.getPartDatabase(context);
    PduPart part = database.getPart(ProfileAccessor.getPartIdForUri(context, uriToPart));
    mMasterSecret = masterSecret;
    if (part != null) {
      return new ImageSlide(context, masterSecret, part);
    }
    return null;
  }
  public static void setProfileStatus(Context context, String status) {
    getPreferences(context).setProfileStatus(status);
  }

  public static String getProfileStatus(Context context) {
    return getPreferences(context).getProfileStatus();
  }
  public static void setStatusForProfileId(Context context, String profileId, String status) {
    getPreferences(context).setProfileStatusForProfileId(profileId, status);
  }
  public static void setUpdateTimeForProfileId(Context context, String profileId, Long date) {
    getPreferences(context).setProfilUpdateTimeForProfileId(profileId, date);
  }
  public static Long getProfileUpdateTimeForRecepient(Context context, String profileId) {
    return getPreferences(context).getProfileUpdateTimeForProfileId(profileId);
  }
  public static String getProfileStatusForRecepient(Context context, String profileId) {
    return getPreferences(context).getProfileStatusForProfileId(profileId);
  }
  public static void sendProfileUpdate(final Context context, final MasterSecret masterSecret, Recipients recipients) throws InvalidMessageException {
    mMasterSecret = masterSecret;
    SlideDeck slideDeck = new SlideDeck();
    slideDeck.addSlide(ProfileAccessor.getMyProfilePicture(context));
      OutgoingMediaMessage outgoingMessage = new OutgoingMediaMessage(context, recipients, slideDeck,
          getProfileStatus(context), ThreadDatabase.DistributionTypes.BROADCAST);

      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessage);
      outgoingMessage.setProfileUpdateMessage(true);
      new AsyncTask<OutgoingMediaMessage, Void, Long>() {
        @Override
        protected Long doInBackground(OutgoingMediaMessage... messages) {
          return MessageSender.send(context, masterSecret, messages[0], 0, false);
        }

        @Override
        protected void onPostExecute(Long result) {
          Log.d("GDATA", "RESULT " + result);
        }
      }.execute(outgoingMessage);
  }

  public static ImageSlide getProfileAsImageSlide(Context context, MasterSecret masterSecret, String profileId) {
    PartDatabase database = DatabaseFactory.getPartDatabase(context);
    PduPart part = database.getPart(ProfileAccessor.getPartId(context, profileId));
    mMasterSecret = masterSecret;
    if (part != null) {
      if (mMasterSecret != null && context != null) {
        ImageSlide slide = new ImageSlide(context, masterSecret, part);
        return slide.getThumbnailUri() != null ? slide : null;
      }
    }
    return null;
  }
  public static ImageSlide getProfileAsImageSlide(Context context, String number) {
    String profileId = GUtil.numberToLong(number)+"";
    PartDatabase database = DatabaseFactory.getPartDatabase(context);
    PduPart part = database.getPart(ProfileAccessor.getPartId(context, profileId));

    if (part != null) {
      if(mMasterSecret != null && context != null) {
        ImageSlide slide = new ImageSlide(context, mMasterSecret, part);
        return slide.getThumbnailUri() != null ? slide : null;
      }
    }
    return null;
  }
  public static void saveActiveContacts(Context context, List<ContactTokenDetails> activeTokens) {
    String[] activeContacts = new String[activeTokens.size()];
    int i = 0;
    for (ContactTokenDetails token : activeTokens) {
      activeContacts[i] = GUtil.numberToLong(token.getNumber() + "") + "";
      i++;
    }
    getPreferences(context).saveActiveContacts(activeContacts);
  }

  public static String[] getActiveContacts(Context context) {
    return getPreferences(context).getActiveContacts();
  }

  public static void sendProfileUpdateToAllContacts(final Context context, final MasterSecret masterSecret) {
    String[] activeContacts = getActiveContacts(context);
    mMasterSecret = masterSecret;
    for (int i = 0; i < activeContacts.length; i++) {
        updateProfileInformations(RecipientFactory.getRecipientsFromString(context, activeContacts[i], false));
    }
  }
  public static void sendProfileUpdateToAllContactsWithThread(final Context context, final MasterSecret masterSecret) {
    String[] activeContacts = getActiveContacts(context);
    mMasterSecret = masterSecret;
    for (int i = 0; i < activeContacts.length; i++) {
      sendProfileUpdateToContactWithThread(context, activeContacts[i]);
    }
  }
  public static void updateProfileInformations(Recipients recipients) {
    SessionStore sessionStore = new TextSecureSessionStore(GService.appContext, mMasterSecret);
    Recipient primaryRecipient = recipients == null ? null : recipients.getPrimaryRecipient();
    boolean isPushDestination = DirectoryHelper.isPushDestination(GService.appContext, recipients);
    AxolotlAddress axolotlAddress = new AxolotlAddress(primaryRecipient.getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);
    boolean isSecureDestination = isSingleConversation(recipients) && sessionStore.containsSession(axolotlAddress);

    if(isPushDestination && isSecureDestination) {
      try {
        ProfileAccessor.sendProfileUpdate(GService.appContext, mMasterSecret, recipients);
      } catch (InvalidMessageException e) {
        Log.w("GDATA", e);
      }
    }
  }
  private static boolean isSingleConversation(Recipients recipients) {
    return recipients != null && recipients.isSingleRecipient() && !recipients.isGroupRecipient();
  }
  public static void setMasterSecred(MasterSecret ma) {
    mMasterSecret = ma;
  }
  public static GenericRequestBuilder buildGlideRequest(@NonNull Slide slide)
  {
    final GenericRequestBuilder builder;
      builder = buildThumbnailGlideRequest(slide, mMasterSecret);
    return builder.error(R.drawable.ic_missing_thumbnail_picture);
  }
  public static GenericRequestBuilder buildThumbnailGlideRequest(Slide slide, MasterSecret masterSecret) {

    final GenericRequestBuilder builder;
    if (slide.isDraft()) builder = buildDraftGlideRequest(slide);
    else builder = buildEncryptedPartGlideRequest(slide, masterSecret);
    return builder;
  }
  public static GenericRequestBuilder buildDraftGlideRequest(Slide slide) {
    return Glide.with(GService.appContext).load(slide.getThumbnailUri()).asBitmap()
        .fitCenter();
  }

  public static GenericRequestBuilder buildEncryptedPartGlideRequest(Slide slide, MasterSecret masterSecret) {
    if (masterSecret == null) {
      throw new IllegalStateException("null MasterSecret when loading non-draft thumbnail");
    }
    if(GService.appContext!=null) {
      throw new IllegalStateException("null context");
    }
    return  Glide.with(GService.appContext).load(new DecryptableStreamUriLoader.DecryptableUri(masterSecret, slide.getThumbnailUri()))
        .transform(new ThumbnailTransform(GService.appContext));
  }

  public static void sendProfileUpdateToContactWithThread(Context activity, String profileId) {
    Recipients recipients = RecipientFactory.getRecipientsFromString(activity, profileId, false);
    long       threadId   = DatabaseFactory.getThreadDatabase(activity).getThreadIdFor(recipients);
    boolean hasConversation = threadId > 0 ? true : false;
    if(hasConversation) {
      updateProfileInformations(RecipientFactory.getRecipientsFromString(activity, profileId, false));
    }
  }
  public static MasterSecret getMasterSecred() {
    return mMasterSecret;
  }
}

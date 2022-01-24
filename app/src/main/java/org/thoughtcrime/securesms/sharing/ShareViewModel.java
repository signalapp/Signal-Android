package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;

public class ShareViewModel extends ViewModel {

  private static final String TAG = Log.tag(ShareViewModel.class);

  private final Context                              context;
  private final ShareRepository                      shareRepository;
  private final MutableLiveData<Optional<ShareData>> shareData;
  private final MutableLiveData<Set<ShareContact>>   selectedContacts;
  private final LiveData<SmsShareRestriction>        smsShareRestriction;

  private boolean mediaUsed;
  private boolean externalShare;

  private ShareViewModel() {
    this.context             = ApplicationDependencies.getApplication();
    this.shareRepository     = new ShareRepository();
    this.shareData           = new MutableLiveData<>();
    this.selectedContacts    = new DefaultValueLiveData<>(Collections.emptySet());
    this.smsShareRestriction = Transformations.map(selectedContacts, this::updateShareRestriction);
  }

  void onSingleMediaShared(@NonNull Uri uri, @Nullable String mimeType) {
    externalShare = true;
    shareRepository.getResolved(uri, mimeType, shareData::postValue);
  }

  void onMultipleMediaShared(@NonNull List<Uri> uris) {
    externalShare = true;
    shareRepository.getResolved(uris, shareData::postValue);
  }

  boolean isMultiShare() {
    return selectedContacts.getValue().size() > 1;
  }

  @NonNull Single<ContactSelectResult> onContactSelected(@NonNull ShareContact selectedContact) {
    return Single.fromCallable(() -> {
      if (selectedContact.getRecipientId().isPresent()) {
        Recipient recipient = Recipient.resolved(selectedContact.getRecipientId().get());

        if (recipient.isPushV2Group()) {
          Optional<GroupDatabase.GroupRecord> record = SignalDatabase.groups().getGroup(recipient.requireGroupId());

          if (record.isPresent() && record.get().isAnnouncementGroup() && !record.get().isAdmin(Recipient.self())) {
            return ContactSelectResult.FALSE_AND_SHOW_PERMISSION_TOAST;
          }
        } else if (SmsShareRestriction.DISALLOW_SMS_CONTACTS.equals(smsShareRestriction.getValue()) &&
                   (!recipient.isRegistered() || recipient.isForceSmsSelection())) {
          return ContactSelectResult.FALSE_AND_SHOW_SMS_MULTISELECT_TOAST;
        }
      }

      Set<ShareContact> contacts = new LinkedHashSet<>(selectedContacts.getValue());
      if (contacts.add(selectedContact)) {
        selectedContacts.postValue(contacts);
        return ContactSelectResult.TRUE;
      } else {
        return ContactSelectResult.FALSE;
      }
    });
  }

  void onContactDeselected(@NonNull ShareContact selectedContact) {
    Set<ShareContact> contacts = new LinkedHashSet<>(selectedContacts.getValue());
    if (contacts.remove(selectedContact)) {
      selectedContacts.setValue(contacts);
    }
  }

  @NonNull Set<ShareContact> getShareContacts() {
    Set<ShareContact> contacts = selectedContacts.getValue();
    if (contacts == null) {
      return Collections.emptySet();
    } else {
      return contacts;
    }
  }

  @NonNull LiveData<MappingModelList> getSelectedContactModels() {
    return Transformations.map(selectedContacts, set -> Stream.of(set)
                                                              .mapIndexed((i, c) -> new ShareSelectionMappingModel(c, i == 0))
                                                              .collect(MappingModelList.toMappingModelList()));
  }

  @NonNull LiveData<SmsShareRestriction> getSmsShareRestriction() {
    return Transformations.distinctUntilChanged(smsShareRestriction);
  }

  void onNonExternalShare() {
    shareData.setValue(Optional.absent());
    externalShare = false;
  }

  public void onSuccessfulShare() {
    mediaUsed = true;
  }

  @NonNull LiveData<Optional<ShareData>> getShareData() {
    return shareData;
  }

  boolean isExternalShare() {
    return externalShare;
  }

  @Override
  protected void onCleared() {
    ShareData data = shareData.getValue() != null ? shareData.getValue().orNull() : null;

    if (data != null && data.isExternal()  && data.isForIntent() && !mediaUsed) {
      Log.i(TAG, "Clearing out unused data.");
      BlobProvider.getInstance().delete(context, data.getUri());
    }
  }

  private @NonNull SmsShareRestriction updateShareRestriction(@NonNull Set<ShareContact> shareContacts) {
    if (shareContacts.isEmpty()) {
      return SmsShareRestriction.NO_RESTRICTIONS;
    } else if (shareContacts.size() == 1) {
      ShareContact shareContact = shareContacts.iterator().next();

      if (shareContact.getRecipientId().isPresent()) {
        Recipient recipient = Recipient.live(shareContact.getRecipientId().get()).get();

        if (!recipient.isRegistered() || recipient.isForceSmsSelection()) {
          return SmsShareRestriction.DISALLOW_MULTI_SHARE;
        } else {
          return SmsShareRestriction.DISALLOW_SMS_CONTACTS;
        }
      } else {
        return SmsShareRestriction.DISALLOW_MULTI_SHARE;
      }
    } else {
      return SmsShareRestriction.DISALLOW_SMS_CONTACTS;
    }
  }

  enum ContactSelectResult {
    TRUE, FALSE, FALSE_AND_SHOW_PERMISSION_TOAST, FALSE_AND_SHOW_SMS_MULTISELECT_TOAST
  }

  enum SmsShareRestriction {
    NO_RESTRICTIONS,
    DISALLOW_SMS_CONTACTS,
    DISALLOW_MULTI_SHARE
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ShareViewModel());
    }
  }
}

package org.thoughtcrime.securesms.contactshare;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.util.cjkv.CJKVUtil;

import static org.thoughtcrime.securesms.contactshare.Contact.Name;

public class ContactNameEditViewModel extends ViewModel {

  private final MutableLiveData<String> displayName;

  private String givenName;
  private String familyName;
  private String middleName;
  private String prefix;
  private String suffix;

  public ContactNameEditViewModel() {
    this.displayName = new MutableLiveData<>();
  }

  void setName(@NonNull Name name) {
    givenName  = name.getGivenName();
    familyName = name.getFamilyName();
    middleName = name.getMiddleName();
    prefix     = name.getPrefix();
    suffix     = name.getSuffix();

    displayName.postValue(buildDisplayName());
  }

  Name getName() {
    return new Name(givenName, familyName, prefix, suffix, middleName, null);
  }

  void updateGivenName(@NonNull String givenName) {
    this.givenName = givenName;
    displayName.postValue(buildDisplayName());
  }

  void updateFamilyName(@NonNull String familyName) {
    this.familyName = familyName;
    displayName.postValue(buildDisplayName());
  }

  void updatePrefix(@NonNull String prefix) {
    this.prefix = prefix;
    displayName.postValue(buildDisplayName());
  }

  void updateSuffix(@NonNull String suffix) {
    this.suffix = suffix;
    displayName.postValue(buildDisplayName());
  }

  void updateMiddleName(@NonNull String middleName) {
    this.middleName = middleName;
    displayName.postValue(buildDisplayName());
  }

  private String buildDisplayName() {
    boolean isCJKV = CJKVUtil.isCJKV(givenName)  &&
                     CJKVUtil.isCJKV(middleName) &&
                     CJKVUtil.isCJKV(familyName) &&
                     CJKVUtil.isCJKV(prefix)     &&
                     CJKVUtil.isCJKV(suffix);

    if (isCJKV) {
      return joinString(familyName, givenName, prefix, suffix, middleName);
    }
    return joinString(prefix, givenName, middleName, familyName, suffix);
  }

  private String joinString(String... values) {
    StringBuilder builder = new StringBuilder();

    for (String value : values) {
      if (!TextUtils.isEmpty(value)) {
        builder.append(value).append(' ');
      }
    }

    return builder.toString().trim();
  }

}

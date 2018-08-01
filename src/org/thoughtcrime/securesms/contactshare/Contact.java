package org.thoughtcrime.securesms.contactshare;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Contact implements Parcelable {

  @JsonProperty
  private final Name                name;

  @JsonProperty
  private final String              organization;

  @JsonProperty
  private final List<Phone>         phoneNumbers;

  @JsonProperty
  private final List<Email>         emails;

  @JsonProperty
  private final List<PostalAddress> postalAddresses;

  @JsonProperty
  private final Avatar              avatar;

  public Contact(@JsonProperty("name")            @NonNull  Name                name,
                 @JsonProperty("organization")    @Nullable String              organization,
                 @JsonProperty("phoneNumbers")    @NonNull  List<Phone>         phoneNumbers,
                 @JsonProperty("emails")          @NonNull  List<Email>         emails,
                 @JsonProperty("postalAddresses") @NonNull  List<PostalAddress> postalAddresses,
                 @JsonProperty("avatar")          @Nullable Avatar              avatar)
  {
    this.name            = name;
    this.organization    = organization;
    this.phoneNumbers    = Collections.unmodifiableList(phoneNumbers);
    this.emails          = Collections.unmodifiableList(emails);
    this.postalAddresses = Collections.unmodifiableList(postalAddresses);
    this.avatar          = avatar;
  }

  public Contact(@NonNull Contact contact, @Nullable Avatar avatar) {
    this(contact.getName(),
         contact.getOrganization(),
         contact.getPhoneNumbers(),
         contact.getEmails(),
         contact.getPostalAddresses(),
         avatar);
  }

  private Contact(Parcel in) {
    this(in.readParcelable(Name.class.getClassLoader()),
         in.readString(),
         in.createTypedArrayList(Phone.CREATOR),
         in.createTypedArrayList(Email.CREATOR),
         in.createTypedArrayList(PostalAddress.CREATOR),
         in.readParcelable(Avatar.class.getClassLoader()));
  }

  public @NonNull Name getName() {
    return name;
  }

  public @Nullable String getOrganization() {
    return organization;
  }

  public @NonNull List<Phone> getPhoneNumbers() {
    return phoneNumbers;
  }

  public @NonNull List<Email> getEmails() {
    return emails;
  }

  public @NonNull List<PostalAddress> getPostalAddresses() {
    return postalAddresses;
  }

  public @Nullable Avatar getAvatar() {
    return avatar;
  }

  @JsonIgnore
  public @Nullable Attachment getAvatarAttachment() {
    return avatar != null ? avatar.getAttachment() : null;
  }

  public String serialize() throws IOException {
    return JsonUtils.toJson(this);
  }

  public static Contact deserialize(@NonNull String serialized) throws IOException {
    return JsonUtils.fromJson(serialized, Contact.class);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(name, flags);
    dest.writeString(organization);
    dest.writeTypedList(phoneNumbers);
    dest.writeTypedList(emails);
    dest.writeTypedList(postalAddresses);
    dest.writeParcelable(avatar, flags);
  }

  public static final Creator<Contact> CREATOR = new Creator<Contact>() {
    @Override
    public Contact createFromParcel(Parcel in) {
      return new Contact(in);
    }

    @Override
    public Contact[] newArray(int size) {
      return new Contact[size];
    }
  };

  public static class Name implements Parcelable {

    @JsonProperty
    private final String displayName;

    @JsonProperty
    private final String givenName;

    @JsonProperty
    private final String familyName;

    @JsonProperty
    private final String prefix;

    @JsonProperty
    private final String suffix;

    @JsonProperty
    private final String middleName;

    Name(@JsonProperty("displayName") @Nullable String displayName,
         @JsonProperty("givenName")   @Nullable String givenName,
         @JsonProperty("familyName")  @Nullable String familyName,
         @JsonProperty("prefix")      @Nullable String prefix,
         @JsonProperty("suffix")      @Nullable String suffix,
         @JsonProperty("middleName")  @Nullable String middleName)
    {
      this.displayName = displayName;
      this.givenName  = givenName;
      this.familyName = familyName;
      this.prefix     = prefix;
      this.suffix     = suffix;
      this.middleName = middleName;
    }

    private Name(Parcel in) {
      this(in.readString(), in.readString(), in.readString(), in.readString(), in.readString(), in.readString());
    }

    public @Nullable String getDisplayName() {
      return displayName;
    }

    public @Nullable String getGivenName() {
      return givenName;
    }

    public @Nullable String getFamilyName() {
      return familyName;
    }

    public @Nullable String getPrefix() {
      return prefix;
    }

    public @Nullable String getSuffix() {
      return suffix;
    }

    public @Nullable String getMiddleName() {
      return middleName;
    }

    public boolean isEmpty() {
      return TextUtils.isEmpty(displayName) &&
             TextUtils.isEmpty(givenName)   &&
             TextUtils.isEmpty(familyName)  &&
             TextUtils.isEmpty(prefix)      &&
             TextUtils.isEmpty(suffix)      &&
             TextUtils.isEmpty(middleName);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(displayName);
      dest.writeString(givenName);
      dest.writeString(familyName);
      dest.writeString(prefix);
      dest.writeString(suffix);
      dest.writeString(middleName);
    }

    public static final Creator<Name> CREATOR = new Creator<Name>() {
      @Override
      public Name createFromParcel(Parcel in) {
        return new Name(in);
      }

      @Override
      public Name[] newArray(int size) {
        return new Name[size];
      }
    };
  }

  public static class Phone implements Selectable, Parcelable {

    @JsonProperty
    private final String number;

    @JsonProperty
    private final Type   type;

    @JsonProperty
    private final String label;

    @JsonIgnore
    private boolean selected;

    Phone(@JsonProperty("number") @NonNull  String number,
          @JsonProperty("type")   @NonNull  Type   type,
          @JsonProperty("label")  @Nullable String label)
    {
      this.number   = number;
      this.type     = type;
      this.label    = label;
      this.selected = true;
    }

    private Phone(Parcel in) {
      this(in.readString(), Type.valueOf(in.readString()), in.readString());
    }

    public @NonNull String getNumber() {
      return number;
    }

    public @NonNull Type getType() {
      return type;
    }

    public @Nullable String getLabel() {
      return label;
    }

    @Override
    public void setSelected(boolean selected) {
      this.selected = selected;
    }

    @Override
    public boolean isSelected() {
      return selected;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(number);
      dest.writeString(type.name());
      dest.writeString(label);
    }

    public static final Creator<Phone> CREATOR = new Creator<Phone>() {
      @Override
      public Phone createFromParcel(Parcel in) {
        return new Phone(in);
      }

      @Override
      public Phone[] newArray(int size) {
        return new Phone[size];
      }
    };

    public enum Type {
      HOME, MOBILE, WORK, CUSTOM
    }
  }

  public static class Email implements Selectable, Parcelable {

    @JsonProperty
    private final String email;

    @JsonProperty
    private final Type   type;

    @JsonProperty
    private final String label;

    @JsonIgnore
    private boolean selected;

    Email(@JsonProperty("email") @NonNull  String email,
          @JsonProperty("type")  @NonNull  Type   type,
          @JsonProperty("label") @Nullable String label)
    {
      this.email    = email;
      this.type     = type;
      this.label    = label;
      this.selected = true;
    }

    private Email(Parcel in) {
      this(in.readString(), Type.valueOf(in.readString()), in.readString());
    }

    public @NonNull String getEmail() {
      return email;
    }

    public @NonNull Type getType() {
      return type;
    }

    public @NonNull String getLabel() {
      return label;
    }

    @Override
    public void setSelected(boolean selected) {
      this.selected = selected;
    }

    @Override
    public boolean isSelected() {
      return selected;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(email);
      dest.writeString(type.name());
      dest.writeString(label);
    }

    public static final Creator<Email> CREATOR = new Creator<Email>() {
      @Override
      public Email createFromParcel(Parcel in) {
        return new Email(in);
      }

      @Override
      public Email[] newArray(int size) {
        return new Email[size];
      }
    };

    public enum Type {
      HOME, MOBILE, WORK, CUSTOM
    }
  }

  public static class PostalAddress implements Selectable, Parcelable {

    @JsonProperty
    private final Type   type;

    @JsonProperty
    private final String label;

    @JsonProperty
    private final String street;

    @JsonProperty
    private final String poBox;

    @JsonProperty
    private final String neighborhood;

    @JsonProperty
    private final String city;

    @JsonProperty
    private final String region;

    @JsonProperty
    private final String postalCode;

    @JsonProperty
    private final String country;

    @JsonIgnore
    private boolean selected;

    PostalAddress(@JsonProperty("type")         @NonNull  Type   type,
                  @JsonProperty("label")        @Nullable String label,
                  @JsonProperty("street")       @Nullable String street,
                  @JsonProperty("poBox")        @Nullable String poBox,
                  @JsonProperty("neighborhood") @Nullable String neighborhood,
                  @JsonProperty("city")         @Nullable String city,
                  @JsonProperty("region")       @Nullable String region,
                  @JsonProperty("postalCode")   @Nullable String postalCode,
                  @JsonProperty("country")      @Nullable String country)
    {
      this.type         = type;
      this.label        = label;
      this.street       = street;
      this.poBox        = poBox;
      this.neighborhood = neighborhood;
      this.city         = city;
      this.region       = region;
      this.postalCode   = postalCode;
      this.country      = country;
      this.selected     = true;
    }

    private PostalAddress(Parcel in) {
      this(Type.valueOf(in.readString()),
          in.readString(),
          in.readString(),
          in.readString(),
          in.readString(),
          in.readString(),
          in.readString(),
          in.readString(),
          in.readString());
    }

    public @NonNull Type getType() {
      return type;
    }

    public @Nullable String getLabel() {
      return label;
    }

    public @Nullable String getStreet() {
      return street;
    }

    public @Nullable String getPoBox() {
      return poBox;
    }

    public @Nullable String getNeighborhood() {
      return neighborhood;
    }

    public @Nullable String getCity() {
      return city;
    }

    public @Nullable String getRegion() {
      return region;
    }

    public @Nullable String getPostalCode() {
      return postalCode;
    }

    public @Nullable String getCountry() {
      return country;
    }

    @Override
    public void setSelected(boolean selected) {
      this.selected = selected;
    }

    @Override
    public boolean isSelected() {
      return selected;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(type.name());
      dest.writeString(label);
      dest.writeString(street);
      dest.writeString(poBox);
      dest.writeString(neighborhood);
      dest.writeString(city);
      dest.writeString(region);
      dest.writeString(postalCode);
      dest.writeString(country);
    }

    public static final Creator<PostalAddress> CREATOR = new Creator<PostalAddress>() {
      @Override
      public PostalAddress createFromParcel(Parcel in) {
        return new PostalAddress(in);
      }

      @Override
      public PostalAddress[] newArray(int size) {
        return new PostalAddress[size];
      }
    };

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();

      if (!TextUtils.isEmpty(street)) {
        builder.append(street).append('\n');
      }

      if (!TextUtils.isEmpty(poBox)) {
        builder.append(poBox).append('\n');
      }

      if (!TextUtils.isEmpty(neighborhood)) {
        builder.append(neighborhood).append('\n');
      }

      if (!TextUtils.isEmpty(city) && !TextUtils.isEmpty(region)) {
        builder.append(city).append(", ").append(region);
      } else if (!TextUtils.isEmpty(city)) {
        builder.append(city).append(' ');
      } else if (!TextUtils.isEmpty(region)) {
        builder.append(region).append(' ');
      }

      if (!TextUtils.isEmpty(postalCode)) {
        builder.append(postalCode);
      }

      if (!TextUtils.isEmpty(country)) {
        builder.append('\n').append(country);
      }

      return builder.toString().trim();
    }

    public enum Type {
      HOME, WORK, CUSTOM
    }
  }

  public static class Avatar implements Selectable, Parcelable {

    @JsonProperty
    private final AttachmentId attachmentId;

    @JsonProperty
    private final boolean      isProfile;

    @JsonIgnore
    private final Attachment   attachment;

    @JsonIgnore
    private boolean selected;

    public Avatar(@Nullable AttachmentId attachmentId, @Nullable Attachment attachment, boolean isProfile) {
      this.attachmentId = attachmentId;
      this.attachment   = attachment;
      this.isProfile    = isProfile;
      this.selected     = true;
    }

    Avatar(@Nullable Uri attachmentUri, boolean isProfile) {
      this(null, attachmentFromUri(attachmentUri), isProfile);
    }

    @JsonCreator
    private Avatar(@JsonProperty("attachmentId") @Nullable AttachmentId attachmentId, @JsonProperty("isProfile") boolean isProfile) {
      this(attachmentId, null, isProfile);
    }

    private Avatar(Parcel in) {
      this((Uri) in.readParcelable(Uri.class.getClassLoader()), in.readByte() != 0);
    }

    public @Nullable AttachmentId getAttachmentId() {
      return attachmentId;
    }

    public @Nullable Attachment getAttachment() {
      return attachment;
    }

    public boolean isProfile() {
      return isProfile;
    }

    @Override
    public void setSelected(boolean selected) {
      this.selected = selected;
    }

    @Override
    public boolean isSelected() {
      return selected;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    private static Attachment attachmentFromUri(@Nullable Uri uri) {
      if (uri == null) return null;
      return new UriAttachment(uri, MediaUtil.IMAGE_JPEG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, 0, null, false, false);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeParcelable(attachment != null ? attachment.getDataUri() : null, flags);
      dest.writeByte((byte) (isProfile ? 1 : 0));
    }

    public static final Creator<Avatar> CREATOR = new Creator<Avatar>() {
      @Override
      public Avatar createFromParcel(Parcel in) {
        return new Avatar(in);
      }

      @Override
      public Avatar[] newArray(int size) {
        return new Avatar[size];
      }
    };
  }
}

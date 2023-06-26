package org.thoughtcrime.securesms.contactshare

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.IOException
import java.util.Collections.unmodifiableList

class Contact(
  @JsonProperty val name: Name,
  @JsonProperty val organization: String?,
  @JsonProperty phoneNumbers: List<Phone?>,
  @JsonProperty emails: List<Email?>,
  @JsonProperty postalAddresses: List<PostalAddress?>,
  @JsonProperty val avatar: Avatar?
) : Parcelable {

  @JsonProperty
  val phoneNumbers: List<Phone?> = unmodifiableList(phoneNumbers)

  @JsonProperty
  val emails: List<Email?> = unmodifiableList(emails)

  @JsonProperty
  val postalAddresses: List<PostalAddress?> = unmodifiableList(postalAddresses)

  constructor(contact: Contact, avatar: Avatar?) : this(
    contact.name, contact.organization, contact.phoneNumbers,
    contact.emails, contact.postalAddresses, avatar
  )

  private constructor(p: Parcel) : this(
    p.readParcelable<Name>(Name::class.java.classLoader)!!,
    p.readString(),
    p.createTypedArrayList<Phone?>(Phone.CREATOR)!!,
    p.createTypedArrayList<Email?>(Email.CREATOR)!!,
    p.createTypedArrayList<PostalAddress?>(PostalAddress.CREATOR)!!,
    p.readParcelable<Avatar>(Avatar::class.java.classLoader)
  )

  val avatarAttachment: Attachment?
    @JsonIgnore get() = avatar?.attachment

  @Throws(IOException::class)
  fun serialize(): String = JsonUtils.toJson(this)

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeParcelable(name, flags)
    dest.writeString(organization)
    dest.writeTypedList(phoneNumbers)
    dest.writeTypedList(emails)
    dest.writeTypedList(postalAddresses)
    dest.writeParcelable(avatar, flags)
  }

  class Name internal constructor(
    @JsonProperty val displayName: String?,
    @JsonProperty val givenName: String?,
    @JsonProperty val familyName: String?,
    @JsonProperty val prefix: String?,
    @JsonProperty val suffix: String?,
    @JsonProperty val middleName: String?
  ) : Parcelable {

    private constructor(p: Parcel) : this(p.readString(),
      p.readString(), p.readString(), p.readString(),
      p.readString(), p.readString())

    val isEmpty get() =
      displayName.isNullOrEmpty() &&
        givenName.isNullOrEmpty() &&
        familyName.isNullOrEmpty() &&
        prefix.isNullOrEmpty() &&
        suffix.isNullOrEmpty() &&
        middleName.isNullOrEmpty()

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeString(displayName)
      dest.writeString(givenName)
      dest.writeString(familyName)
      dest.writeString(prefix)
      dest.writeString(suffix)
      dest.writeString(middleName)
    }

    companion object CREATOR: Parcelable.Creator<Name> {
      override fun createFromParcel(p: Parcel) = Name(p)
      override fun newArray(size: Int) = arrayOfNulls<Name>(size)
    }
  }

  class Phone internal constructor(
    @JsonProperty val number: String,
    @JsonProperty val type: Type,
    @JsonProperty val label: String?
  ) : Selectable, Parcelable {

    @JsonIgnore
    override var isSelected = true

    private constructor(p: Parcel) :
      this(p.readString()!!,Type.valueOf(p.readString()!!), p.readString())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeString(number)
      dest.writeString(type.name)
      dest.writeString(label)
    }

    enum class Type { HOME, MOBILE, WORK, CUSTOM }

    companion object CREATOR: Parcelable.Creator<Phone> {
      override fun createFromParcel(p: Parcel) = Phone(p)
      override fun newArray(size: Int) = arrayOfNulls<Phone>(size)
    }
  }

  class Email internal constructor(
    @JsonProperty val email: String,
    @JsonProperty val type: Type,
    @JsonProperty label: String?
  ) : Selectable, Parcelable {

    @JsonProperty
    val label: String = label!!

    @JsonIgnore
    override var isSelected = true

    private constructor(p: Parcel) : this(p.readString()!!, Type.valueOf(p.readString()!!), p.readString())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeString(email)
      dest.writeString(type.name)
      dest.writeString(label)
    }

    enum class Type { HOME, MOBILE, WORK, CUSTOM }

    companion object CREATOR: Parcelable.Creator<Email> {
      override fun createFromParcel(p: Parcel) = Email(p)
      override fun newArray(size: Int) = arrayOfNulls<Email>(size)
    }
  }

  class PostalAddress internal constructor(
    @JsonProperty val type: Type,
    @JsonProperty val label: String?,
    @JsonProperty val street: String?,
    @JsonProperty val poBox: String?,
    @JsonProperty val neighborhood: String?,
    @JsonProperty val city: String?,
    @JsonProperty val region: String?,
    @JsonProperty val postalCode: String?,
    @JsonProperty val country: String?
  ) : Selectable, Parcelable {

    @JsonIgnore
    override var isSelected = true

    private constructor(p: Parcel) : this(
      Type.valueOf(p.readString()!!),
      p.readString(),
      p.readString(),
      p.readString(),
      p.readString(),
      p.readString(),
      p.readString(),
      p.readString(),
      p.readString()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeString(type.name)
      dest.writeString(label)
      dest.writeString(street)
      dest.writeString(poBox)
      dest.writeString(neighborhood)
      dest.writeString(city)
      dest.writeString(region)
      dest.writeString(postalCode)
      dest.writeString(country)
    }

    override fun toString(): String {
      val builder = StringBuilder()
      if (!street.isNullOrEmpty()) builder.append(street).append('\n')
      if (!poBox.isNullOrEmpty()) builder.append(poBox).append('\n')
      if (!neighborhood.isNullOrEmpty()) builder.append(neighborhood).append('\n')
      if (!city.isNullOrEmpty() && !region.isNullOrEmpty())
        builder.append(city).append(", ").append(region)
      else if (!city.isNullOrEmpty())
        builder.append(city).append(' ')
      else if (!region.isNullOrEmpty())
        builder.append(region).append(' ')
      if (!postalCode.isNullOrEmpty())
        builder.append(postalCode)
      if (!country.isNullOrEmpty())
        builder.append('\n').append(country)
      return builder.toString().trim { it <= ' ' }
    }

    enum class Type { HOME, WORK, CUSTOM }

    companion object CREATOR: Parcelable.Creator<PostalAddress> {
      override fun createFromParcel(p: Parcel) = PostalAddress(p)
      override fun newArray(size: Int) = arrayOfNulls<PostalAddress>(size)
    }
  }

  class Avatar(@JsonProperty val attachmentId: AttachmentId?,
               @JsonIgnore val attachment: Attachment?,
               @JsonProperty val isProfile: Boolean
               ) : Selectable, Parcelable {

    @JsonIgnore
    override var isSelected = true

    internal constructor(attachmentUri: Uri?, isProfile: Boolean) :
      this(null, attachmentFromUri(attachmentUri), isProfile)

    @JsonCreator
    private constructor(@JsonProperty attachmentId: AttachmentId?, @JsonProperty isProfile: Boolean) :
      this(attachmentId, null, isProfile)

    private constructor(p: Parcel) :
      this(p.readParcelable<Parcelable>(Uri::class.java.classLoader) as Uri?, p.readByte().toInt() != 0)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeParcelable(attachment?.uri, flags)
      dest.writeByte((if (isProfile) 1 else 0).toByte())
    }

    companion object CREATOR: Parcelable.Creator<Avatar> {
      private fun attachmentFromUri(uri: Uri?): Attachment? =
        if (uri == null) null else UriAttachment(
          uri, MediaUtil.IMAGE_JPEG, AttachmentTable.TRANSFER_PROGRESS_DONE, 0,
          null, false, false, false, false, null, null, null, null, null)

      override fun createFromParcel(p: Parcel) = Avatar(p)

      override fun newArray(size: Int) = arrayOfNulls<Avatar>(size)
    }
  }

  companion object CREATOR: Parcelable.Creator<Contact> {
    @Throws(IOException::class)
    fun deserialize(serialized: String): Contact =
      JsonUtils.fromJson(serialized, Contact::class.java)

    override fun createFromParcel(p: Parcel) = Contact(p)

    override fun newArray(size: Int) = arrayOfNulls<Contact>(size)
  }
}
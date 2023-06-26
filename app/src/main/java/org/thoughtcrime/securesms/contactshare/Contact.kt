package org.thoughtcrime.securesms.contactshare

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
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
    contact.name,
    contact.organization,
    contact.phoneNumbers,
    contact.emails,
    contact.postalAddresses,
    avatar
  )

  private constructor(`in`: Parcel) : this(
    `in`.readParcelable<Name>(Name::class.java.classLoader)!!,
    `in`.readString(),
    `in`.createTypedArrayList<Phone?>(Phone.CREATOR)!!,
    `in`.createTypedArrayList<Email?>(Email.CREATOR)!!,
    `in`.createTypedArrayList<PostalAddress?>(PostalAddress.CREATOR)!!,
    `in`.readParcelable<Avatar>(Avatar::class.java.classLoader)
  )

  val avatarAttachment: Attachment?
    @JsonIgnore get() = avatar?.attachment

  @Throws(IOException::class)
  fun serialize() = JsonUtils.toJson(this)

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

    private constructor(`in`: Parcel) : this(`in`.readString(), `in`.readString(), `in`.readString(), `in`.readString(), `in`.readString(), `in`.readString())

    val isEmpty: Boolean
      get() = TextUtils.isEmpty(displayName) &&
        TextUtils.isEmpty(givenName) &&
        TextUtils.isEmpty(familyName) &&
        TextUtils.isEmpty(prefix) &&
        TextUtils.isEmpty(suffix) &&
        TextUtils.isEmpty(middleName)

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
      override fun createFromParcel(`in`: Parcel) = Name(`in`)
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

    private constructor(`in`: Parcel) :
      this(`in`.readString()!!,Type.valueOf(`in`.readString()!!), `in`.readString())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeString(number)
      dest.writeString(type.name)
      dest.writeString(label)
    }

    enum class Type { HOME, MOBILE, WORK, CUSTOM }

    companion object CREATOR: Parcelable.Creator<Phone> {
      override fun createFromParcel(`in`: Parcel) = Phone(`in`)
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

    private constructor(`in`: Parcel) : this(`in`.readString()!!, Type.valueOf(`in`.readString()!!), `in`.readString())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeString(email)
      dest.writeString(type.name)
      dest.writeString(label)
    }

    enum class Type { HOME, MOBILE, WORK, CUSTOM }

    companion object CREATOR: Parcelable.Creator<Email> {
      override fun createFromParcel(`in`: Parcel) = Email(`in`)
      override fun newArray(size: Int) = arrayOfNulls<Email>(size)
    }
  }

  class PostalAddress internal constructor(
    @field:JsonProperty @param:JsonProperty("type") val type: Type,
    @field:JsonProperty @param:JsonProperty("label") val label: String?,
    @field:JsonProperty @param:JsonProperty("street") val street: String?,
    @field:JsonProperty @param:JsonProperty("poBox") val poBox: String?,
    @field:JsonProperty @param:JsonProperty("neighborhood") val neighborhood: String?,
    @field:JsonProperty @param:JsonProperty("city") val city: String?,
    @field:JsonProperty @param:JsonProperty("region") val region: String?,
    @field:JsonProperty @param:JsonProperty("postalCode") val postalCode: String?,
    @field:JsonProperty @param:JsonProperty("country") val country: String?
  ) : Selectable, Parcelable {

    @JsonIgnore
    override var isSelected = true

    private constructor(`in`: Parcel) : this(
      Type.valueOf(`in`.readString()!!),
      `in`.readString(),
      `in`.readString(),
      `in`.readString(),
      `in`.readString(),
      `in`.readString(),
      `in`.readString(),
      `in`.readString(),
      `in`.readString()
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
      if (!TextUtils.isEmpty(street)) {
        builder.append(street).append('\n')
      }
      if (!TextUtils.isEmpty(poBox)) {
        builder.append(poBox).append('\n')
      }
      if (!TextUtils.isEmpty(neighborhood)) {
        builder.append(neighborhood).append('\n')
      }
      if (!TextUtils.isEmpty(city) && !TextUtils.isEmpty(region)) {
        builder.append(city).append(", ").append(region)
      } else if (!TextUtils.isEmpty(city)) {
        builder.append(city).append(' ')
      } else if (!TextUtils.isEmpty(region)) {
        builder.append(region).append(' ')
      }
      if (!TextUtils.isEmpty(postalCode)) {
        builder.append(postalCode)
      }
      if (!TextUtils.isEmpty(country)) {
        builder.append('\n').append(country)
      }
      return builder.toString().trim { it <= ' ' }
    }

    enum class Type { HOME, WORK, CUSTOM }

    companion object CREATOR: Parcelable.Creator<PostalAddress> {
      override fun createFromParcel(`in`: Parcel) = PostalAddress(`in`)
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
    private constructor(@JsonProperty("attachmentId") attachmentId: AttachmentId?, @JsonProperty("isProfile") isProfile: Boolean) : this(attachmentId, null, isProfile)

    private constructor(`in`: Parcel) : this(`in`.readParcelable<Parcelable>(Uri::class.java.classLoader) as Uri?, `in`.readByte().toInt() != 0)

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

      override fun createFromParcel(`in`: Parcel) = Avatar(`in`)

      override fun newArray(size: Int) = arrayOfNulls<Avatar>(size)
    }
  }

  companion object CREATOR: Parcelable.Creator<Contact> {
    @Throws(IOException::class)
    fun deserialize(serialized: String): Contact =
      JsonUtils.fromJson(serialized, Contact::class.java)

    override fun createFromParcel(`in`: Parcel) = Contact(`in`)

    override fun newArray(size: Int) = arrayOfNulls<Contact>(size)
  }
}
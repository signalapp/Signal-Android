/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.Parcelize
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.JsonUtils
import org.signal.core.util.readParcelableCompat
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.IOException

/**
 * A contact card, either one being shared or one that was received.
 *
 * Persisted as JSON in [org.thoughtcrime.securesms.database.MessageTable.SHARED_CONTACTS], so the
 * Jackson property names are a storage format and cannot be renamed. Unknown properties are ignored
 * on read (see [JsonUtils]), which is what lets new fields be added without a migration.
 */
class Contact @JsonCreator constructor(
  @JsonProperty("name") name: Name?,
  @JsonProperty("organization") val organization: String?,
  @JsonProperty("phoneNumbers") phoneNumbers: List<Phone>,
  @JsonProperty("emails") emails: List<Email>,
  @JsonProperty("postalAddresses") postalAddresses: List<PostalAddress>,
  @JsonProperty("avatar") val avatar: Avatar?,
  /**
   * The ACI of the person on the card, when the sharer knew they were on Signal. A canonical UUID
   * string rather than an [org.signal.core.models.ServiceId.ACI] so that it survives JSON without a
   * converter; the mappers parse it at the wire boundary and drop it if it is not valid.
   */
  @JsonProperty("aci") val aci: String? = null,
  /** The sharer's own Signal nickname for this contact, which is not the vcard nickname on [Name]. */
  @JsonProperty("nickname") val nickname: SignalNickname? = null,
  /** The sharer's own Signal note about this contact. */
  @JsonProperty("note") val note: String? = null
) : Parcelable {

  @get:JsonProperty("name")
  val name: Name = name ?: Name.EMPTY_NAME

  @get:JsonProperty("phoneNumbers")
  val phoneNumbers: List<Phone> = phoneNumbers.toList()

  @get:JsonProperty("emails")
  val emails: List<Email> = emails.toList()

  @get:JsonProperty("postalAddresses")
  val postalAddresses: List<PostalAddress> = postalAddresses.toList()

  /** Replaces the avatar, keeping everything else. Used when an attachment id becomes known. */
  constructor(contact: Contact, avatar: Avatar?) : this(
    contact.name,
    contact.organization,
    contact.phoneNumbers,
    contact.emails,
    contact.postalAddresses,
    avatar,
    contact.aci,
    contact.nickname,
    contact.note
  )

  private constructor(parcel: Parcel) : this(
    parcel.readParcelableCompat(Name::class.java),
    parcel.readString(),
    parcel.readParcelableListCompat(Phone::class.java),
    parcel.readParcelableListCompat(Email::class.java),
    parcel.readParcelableListCompat(PostalAddress::class.java),
    parcel.readParcelableCompat(Avatar::class.java),
    parcel.readString(),
    parcel.readParcelableCompat(SignalNickname::class.java),
    parcel.readString()
  )

  @get:JsonIgnore
  val avatarAttachment: Attachment?
    get() = avatar?.attachment

  @Throws(IOException::class)
  fun serialize(): String = JsonUtils.toJson(this)

  override fun describeContents(): Int = 0

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeParcelable(name, flags)
    dest.writeString(organization)
    dest.writeParcelableListCompat(phoneNumbers, flags)
    dest.writeParcelableListCompat(emails, flags)
    dest.writeParcelableListCompat(postalAddresses, flags)
    dest.writeParcelable(avatar, flags)
    dest.writeString(aci)
    dest.writeParcelable(nickname, flags)
    dest.writeString(note)
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<Contact> = object : Parcelable.Creator<Contact> {
      override fun createFromParcel(parcel: Parcel): Contact = Contact(parcel)
      override fun newArray(size: Int): Array<Contact?> = arrayOfNulls(size)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun deserialize(serialized: String): Contact = JsonUtils.fromJson(serialized, Contact::class.java)
  }

  @Parcelize
  class Name @JsonCreator constructor(
    @JsonProperty("givenName") val givenName: String?,
    @JsonProperty("familyName") val familyName: String?,
    @JsonProperty("prefix") val prefix: String?,
    @JsonProperty("suffix") val suffix: String?,
    @JsonProperty("middleName") val middleName: String?,
    @JsonProperty("nickname") val nickname: String?
  ) : Parcelable {

    @get:JsonIgnore
    val isEmpty: Boolean
      get() = givenName.isNullOrEmpty() &&
        familyName.isNullOrEmpty() &&
        prefix.isNullOrEmpty() &&
        suffix.isNullOrEmpty() &&
        middleName.isNullOrEmpty() &&
        nickname.isNullOrEmpty()

    companion object {
      @JvmField
      val EMPTY_NAME = Name("", "", "", "", "", "")
    }
  }

  /**
   * The sharer's Signal nickname for the contact. Absent when both halves are empty, since a
   * nickname with nothing in it says nothing.
   */
  @Parcelize
  class SignalNickname @JsonCreator constructor(
    @JsonProperty("given") val given: String?,
    @JsonProperty("family") val family: String?
  ) : Parcelable {

    @get:JsonIgnore
    val isEmpty: Boolean
      get() = given.isNullOrEmpty() && family.isNullOrEmpty()
  }

  @Parcelize
  class Phone @JsonCreator constructor(
    @JsonProperty("number") val number: String,
    @JsonProperty("type") val type: Type,
    @JsonProperty("label") val label: String?
  ) : Selectable, Parcelable {

    @JsonIgnore
    private var selected = true

    @JsonIgnore
    override fun isSelected(): Boolean = selected

    override fun setSelected(selected: Boolean) {
      this.selected = selected
    }

    enum class Type {
      HOME, MOBILE, WORK, CUSTOM
    }
  }

  @Parcelize
  class Email @JsonCreator constructor(
    @JsonProperty("email") val email: String,
    @JsonProperty("type") val type: Type,
    @JsonProperty("label") val label: String?
  ) : Selectable, Parcelable {

    @JsonIgnore
    private var selected = true

    @JsonIgnore
    override fun isSelected(): Boolean = selected

    override fun setSelected(selected: Boolean) {
      this.selected = selected
    }

    enum class Type {
      HOME, MOBILE, WORK, CUSTOM
    }
  }

  @Parcelize
  class PostalAddress @JsonCreator constructor(
    @JsonProperty("type") val type: Type,
    @JsonProperty("label") val label: String?,
    @JsonProperty("street") val street: String?,
    @JsonProperty("poBox") val poBox: String?,
    @JsonProperty("neighborhood") val neighborhood: String?,
    @JsonProperty("city") val city: String?,
    @JsonProperty("region") val region: String?,
    @JsonProperty("postalCode") val postalCode: String?,
    @JsonProperty("country") val country: String?
  ) : Selectable, Parcelable {

    @JsonIgnore
    private var selected = true

    @JsonIgnore
    override fun isSelected(): Boolean = selected

    override fun setSelected(selected: Boolean) {
      this.selected = selected
    }

    override fun toString(): String {
      val builder = StringBuilder()

      if (!street.isNullOrEmpty()) {
        builder.append(street).append('\n')
      }

      if (!poBox.isNullOrEmpty()) {
        builder.append(poBox).append('\n')
      }

      if (!neighborhood.isNullOrEmpty()) {
        builder.append(neighborhood).append('\n')
      }

      if (!city.isNullOrEmpty() && !region.isNullOrEmpty()) {
        builder.append(city).append(", ").append(region)
      } else if (!city.isNullOrEmpty()) {
        builder.append(city).append(' ')
      } else if (!region.isNullOrEmpty()) {
        builder.append(region).append(' ')
      }

      if (!postalCode.isNullOrEmpty()) {
        builder.append(postalCode)
      }

      if (!country.isNullOrEmpty()) {
        builder.append('\n').append(country)
      }

      return builder.toString().trim()
    }

    enum class Type {
      HOME, WORK, CUSTOM
    }
  }

  class Avatar(
    @get:JsonProperty("attachmentId") val attachmentId: AttachmentId?,
    @get:JsonIgnore val attachment: Attachment?,
    @get:JsonProperty("isProfile") val isProfile: Boolean
  ) : Selectable, Parcelable {

    constructor(attachmentUri: Uri?, isProfile: Boolean) : this(null, attachmentFromUri(attachmentUri), isProfile)

    @JsonCreator
    private constructor(
      @JsonProperty("attachmentId") attachmentId: AttachmentId?,
      @JsonProperty("isProfile") isProfile: Boolean
    ) : this(attachmentId, null, isProfile)

    private constructor(parcel: Parcel) : this(parcel.readParcelableCompat(Uri::class.java), parcel.readByte() != 0.toByte())

    @JsonIgnore
    private var selected = true

    @JsonIgnore
    override fun isSelected(): Boolean = selected

    override fun setSelected(selected: Boolean) {
      this.selected = selected
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeParcelable(attachment?.uri, flags)
      dest.writeByte(if (isProfile) 1 else 0)
    }

    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<Avatar> = object : Parcelable.Creator<Avatar> {
        override fun createFromParcel(parcel: Parcel): Avatar = Avatar(parcel)
        override fun newArray(size: Int): Array<Avatar?> = arrayOfNulls(size)
      }

      private fun attachmentFromUri(uri: Uri?): Attachment? {
        if (uri == null) {
          return null
        }

        return UriAttachment(uri, MediaUtil.IMAGE_JPEG, AttachmentTable.TRANSFER_PROGRESS_DONE, 0, null, false, false, false, false, null, null, null, null, null, null)
      }
    }
  }
}

/**
 * [Parcelize] does not expose a `CREATOR` to source in the same compilation, so the lists are
 * written element by element rather than as a typed list. Only ever read back by the matching
 * helper below, and parcels never outlive the process, so the format is free to differ from the
 * one the Java version used.
 */
private fun <T : Parcelable> Parcel.writeParcelableListCompat(values: List<T>, flags: Int) {
  writeInt(values.size)
  values.forEach { writeParcelable(it, flags) }
}

private fun <T : Parcelable> Parcel.readParcelableListCompat(clazz: Class<T>): List<T> {
  return (0 until readInt()).mapNotNull { readParcelableCompat(clazz) }
}

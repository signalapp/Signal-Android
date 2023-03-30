package org.thoughtcrime.securesms.contactshare;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.util.AttachmentPointerUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.thoughtcrime.securesms.contactshare.Contact.Avatar;
import static org.thoughtcrime.securesms.contactshare.Contact.Email;
import static org.thoughtcrime.securesms.contactshare.Contact.Name;
import static org.thoughtcrime.securesms.contactshare.Contact.Phone;
import static org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;

public class ContactModelMapper {

  private static final String TAG = Log.tag(ContactModelMapper.class);

  public static SharedContact.Builder localToRemoteBuilder(@NonNull Contact contact) {
    List<SharedContact.Phone>         phoneNumbers    = new ArrayList<>(contact.getPhoneNumbers().size());
    List<SharedContact.Email>         emails          = new ArrayList<>(contact.getEmails().size());
    List<SharedContact.PostalAddress> postalAddresses = new ArrayList<>(contact.getPostalAddresses().size());

    for (Phone phone : contact.getPhoneNumbers()) {
      phoneNumbers.add(new SharedContact.Phone.Builder().setValue(phone.getNumber())
                                                        .setType(localToRemoteType(phone.getType()))
                                                        .setLabel(phone.getLabel())
                                                        .build());
    }

    for (Email email : contact.getEmails()) {
      emails.add(new SharedContact.Email.Builder().setValue(email.getEmail())
                                                  .setType(localToRemoteType(email.getType()))
                                                  .setLabel(email.getLabel())
                                                  .build());
    }

    for (PostalAddress postalAddress : contact.getPostalAddresses()) {
      postalAddresses.add(new SharedContact.PostalAddress.Builder().setType(localToRemoteType(postalAddress.getType()))
                                                                   .setLabel(postalAddress.getLabel())
                                                                   .setStreet(postalAddress.getStreet())
                                                                   .setPobox(postalAddress.getPoBox())
                                                                   .setNeighborhood(postalAddress.getNeighborhood())
                                                                   .setCity(postalAddress.getCity())
                                                                   .setRegion(postalAddress.getRegion())
                                                                   .setPostcode(postalAddress.getPostalCode())
                                                                   .setCountry(postalAddress.getCountry())
                                                                   .build());
    }

    SharedContact.Name name = new SharedContact.Name.Builder().setDisplay(contact.getName().getDisplayName())
                                                              .setGiven(contact.getName().getGivenName())
                                                              .setFamily(contact.getName().getFamilyName())
                                                              .setPrefix(contact.getName().getPrefix())
                                                              .setSuffix(contact.getName().getSuffix())
                                                              .setMiddle(contact.getName().getMiddleName())
                                                              .build();

    return new SharedContact.Builder().setName(name)
                                      .withOrganization(contact.getOrganization())
                                      .withPhones(phoneNumbers)
                                      .withEmails(emails)
                                      .withAddresses(postalAddresses);
  }

  public static Contact remoteToLocal(@NonNull SharedContact sharedContact) {
    Name name = new Name(sharedContact.getName().getDisplay().orElse(null),
        sharedContact.getName().getGiven().orElse(null),
        sharedContact.getName().getFamily().orElse(null),
        sharedContact.getName().getPrefix().orElse(null),
        sharedContact.getName().getSuffix().orElse(null),
        sharedContact.getName().getMiddle().orElse(null));

    List<Phone> phoneNumbers = new LinkedList<>();
    if (sharedContact.getPhone().isPresent()) {
      for (SharedContact.Phone phone : sharedContact.getPhone().get()) {
        phoneNumbers.add(new Phone(phone.getValue(),
                                   remoteToLocalType(phone.getType()),
                                   phone.getLabel().orElse(null)));
      }
    }

    List<Email> emails = new LinkedList<>();
    if (sharedContact.getEmail().isPresent()) {
      for (SharedContact.Email email : sharedContact.getEmail().get()) {
        emails.add(new Email(email.getValue(),
                             remoteToLocalType(email.getType()),
                             email.getLabel().orElse(null)));
      }
    }

    List<PostalAddress> postalAddresses = new LinkedList<>();
    if (sharedContact.getAddress().isPresent()) {
      for (SharedContact.PostalAddress postalAddress : sharedContact.getAddress().get()) {
        postalAddresses.add(new PostalAddress(remoteToLocalType(postalAddress.getType()),
                                              postalAddress.getLabel().orElse(null),
                                              postalAddress.getStreet().orElse(null),
                                              postalAddress.getPobox().orElse(null),
                                              postalAddress.getNeighborhood().orElse(null),
                                              postalAddress.getCity().orElse(null),
                                              postalAddress.getRegion().orElse(null),
                                              postalAddress.getPostcode().orElse(null),
                                              postalAddress.getCountry().orElse(null)));
      }
    }

    Avatar avatar = null;
    if (sharedContact.getAvatar().isPresent()) {
      Attachment attachment = PointerAttachment.forPointer(Optional.of(sharedContact.getAvatar().get().getAttachment().asPointer())).get();
      boolean    isProfile  = sharedContact.getAvatar().get().isProfile();

      avatar = new Avatar(null, attachment, isProfile);
    }

    return new Contact(name, sharedContact.getOrganization().orElse(null), phoneNumbers, emails, postalAddresses, avatar);
  }

  public static Contact remoteToLocal(@NonNull SignalServiceProtos.DataMessage.Contact contact) {
    Name name = new Name(contact.getName().getDisplayName(),
                         contact.getName().getGivenName(),
                         contact.getName().getFamilyName(),
                         contact.getName().getPrefix(),
                         contact.getName().getSuffix(),
                         contact.getName().getMiddleName());

    List<Phone> phoneNumbers = new ArrayList<>(contact.getNumberCount());
    for (SignalServiceProtos.DataMessage.Contact.Phone phone : contact.getNumberList()) {
      phoneNumbers.add(new Phone(phone.getValue(),
                                 remoteToLocalType(phone.getType()),
                                 phone.getLabel()));
    }

    List<Email> emails = new ArrayList<>(contact.getEmailCount());
    for (SignalServiceProtos.DataMessage.Contact.Email email : contact.getEmailList()) {
      emails.add(new Email(email.getValue(),
                           remoteToLocalType(email.getType()),
                           email.getLabel()));
    }

    List<PostalAddress> postalAddresses = new ArrayList<>(contact.getAddressCount());
    for (SignalServiceProtos.DataMessage.Contact.PostalAddress postalAddress : contact.getAddressList()) {
      postalAddresses.add(new PostalAddress(remoteToLocalType(postalAddress.getType()),
                                            postalAddress.getLabel(),
                                            postalAddress.getStreet(),
                                            postalAddress.getPobox(),
                                            postalAddress.getNeighborhood(),
                                            postalAddress.getCity(),
                                            postalAddress.getRegion(),
                                            postalAddress.getPostcode(),
                                            postalAddress.getCountry()));
    }

    Avatar avatar = null;
    if (contact.hasAvatar()) {
      try {
        SignalServiceAttachmentPointer attachmentPointer = AttachmentPointerUtil.createSignalAttachmentPointer(contact.getAvatar().getAvatar());
        Attachment                     attachment        = PointerAttachment.forPointer(Optional.of(attachmentPointer.asPointer())).get();
        boolean                        isProfile         = contact.getAvatar().getIsProfile();

        avatar = new Avatar(null, attachment, isProfile);
      } catch (InvalidMessageStructureException e) {
        Log.w(TAG, "Unable to create avatar for contact", e);
      }
    }

    return new Contact(name, contact.getOrganization(), phoneNumbers, emails, postalAddresses, avatar);
  }

  private static Phone.Type remoteToLocalType(SharedContact.Phone.Type type) {
    switch (type) {
      case HOME:   return Phone.Type.HOME;
      case MOBILE: return Phone.Type.MOBILE;
      case WORK:   return Phone.Type.WORK;
      default:     return Phone.Type.CUSTOM;
    }
  }

  private static Phone.Type remoteToLocalType(SignalServiceProtos.DataMessage.Contact.Phone.Type type) {
    switch (type) {
      case HOME:   return Phone.Type.HOME;
      case MOBILE: return Phone.Type.MOBILE;
      case WORK:   return Phone.Type.WORK;
      default:     return Phone.Type.CUSTOM;
    }
  }

  private static Email.Type remoteToLocalType(SharedContact.Email.Type type) {
    switch (type) {
      case HOME:   return Email.Type.HOME;
      case MOBILE: return Email.Type.MOBILE;
      case WORK:   return Email.Type.WORK;
      default:     return Email.Type.CUSTOM;
    }
  }

  private static Email.Type remoteToLocalType(SignalServiceProtos.DataMessage.Contact.Email.Type type) {
    switch (type) {
      case HOME:   return Email.Type.HOME;
      case MOBILE: return Email.Type.MOBILE;
      case WORK:   return Email.Type.WORK;
      default:     return Email.Type.CUSTOM;
    }
  }

  private static PostalAddress.Type remoteToLocalType(SharedContact.PostalAddress.Type type) {
    switch (type) {
      case HOME:   return PostalAddress.Type.HOME;
      case WORK:   return PostalAddress.Type.WORK;
      default:     return PostalAddress.Type.CUSTOM;
    }
  }

  private static PostalAddress.Type remoteToLocalType(SignalServiceProtos.DataMessage.Contact.PostalAddress.Type type) {
    switch (type) {
      case HOME:   return PostalAddress.Type.HOME;
      case WORK:   return PostalAddress.Type.WORK;
      default:     return PostalAddress.Type.CUSTOM;
    }
  }

  private static SharedContact.Phone.Type localToRemoteType(Phone.Type type) {
    switch (type) {
      case HOME:   return SharedContact.Phone.Type.HOME;
      case MOBILE: return SharedContact.Phone.Type.MOBILE;
      case WORK:   return SharedContact.Phone.Type.WORK;
      default:     return SharedContact.Phone.Type.CUSTOM;
    }
  }

  private static SharedContact.Email.Type localToRemoteType(Email.Type type) {
    switch (type) {
      case HOME:   return SharedContact.Email.Type.HOME;
      case MOBILE: return SharedContact.Email.Type.MOBILE;
      case WORK:   return SharedContact.Email.Type.WORK;
      default:     return SharedContact.Email.Type.CUSTOM;
    }
  }

  private static SharedContact.PostalAddress.Type localToRemoteType(PostalAddress.Type type) {
    switch (type) {
      case HOME: return SharedContact.PostalAddress.Type.HOME;
      case WORK: return SharedContact.PostalAddress.Type.WORK;
      default:   return SharedContact.PostalAddress.Type.CUSTOM;
    }
  }
}

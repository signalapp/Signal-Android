package org.thoughtcrime.securesms.contactshare;

import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ezvcard.Ezvcard;
import ezvcard.VCard;

public final class VCardUtil {

    private VCardUtil(){}

    private static final String TAG = Log.tag(VCardUtil.class);

    public static List<Contact> parseContacts(@NonNull String vCardData) {
        List<VCard> vContacts = Ezvcard.parse(vCardData).all();
        List<Contact> contacts = new LinkedList<>();
        for (VCard vCard: vContacts){
            contacts.add(getContactFromVcard(vCard));
        }
        return contacts;
    }

    static @Nullable Contact getContactFromVcard(@NonNull VCard vcard) {
        ezvcard.property.StructuredName  vName            = vcard.getStructuredName();
        List<ezvcard.property.Telephone> vPhones          = vcard.getTelephoneNumbers();
        List<ezvcard.property.Email>     vEmails          = vcard.getEmails();
        List<ezvcard.property.Address>   vPostalAddresses = vcard.getAddresses();

        String organization = vcard.getOrganization() != null && !vcard.getOrganization().getValues().isEmpty() ? vcard.getOrganization().getValues().get(0) : null;
        String displayName  = vcard.getFormattedName() != null ? vcard.getFormattedName().getValue() : null;

        if (displayName == null && vName != null) {
            displayName = vName.getGiven();
        }

        if (displayName == null && vcard.getOrganization() != null) {
            displayName = organization;
        }

        if (displayName == null) {
            Log.w(TAG, "Failed to parse the vcard: No valid name.");
            return null;
        }

        Contact.Name name = new Contact.Name(
                vName != null ? vName.getGiven() : null,
                vName != null ? vName.getFamily() : null,
                vName != null && !vName.getPrefixes().isEmpty() ? vName.getPrefixes().get(0) : null,
                vName != null && !vName.getSuffixes().isEmpty() ? vName.getSuffixes().get(0) : null,
                null,
                null);


        List<Contact.Phone> phoneNumbers = new ArrayList<>(vPhones.size());
        for (ezvcard.property.Telephone vEmail : vPhones) {
            String label = !vEmail.getTypes().isEmpty() ? getCleanedVcardType(vEmail.getTypes().get(0).getValue()) : null;

            // Phone number is stored in the uri field in v4.0 only. In other versions, it is in the text field.
            String phoneNumberFromText  = vEmail.getText();
            String extractedPhoneNumber = phoneNumberFromText == null ? vEmail.getUri().getNumber() : phoneNumberFromText;
            phoneNumbers.add(new Contact.Phone(extractedPhoneNumber, phoneTypeFromVcardType(label), label));
        }

        List<Contact.Email> emails = new ArrayList<>(vEmails.size());
        for (ezvcard.property.Email vEmail : vEmails) {
            String label = !vEmail.getTypes().isEmpty() ? getCleanedVcardType(vEmail.getTypes().get(0).getValue()) : null;
            emails.add(new Contact.Email(vEmail.getValue(), emailTypeFromVcardType(label), label));
        }

        List<Contact.PostalAddress> postalAddresses = new ArrayList<>(vPostalAddresses.size());
        for (ezvcard.property.Address vPostalAddress : vPostalAddresses) {
            String label = !vPostalAddress.getTypes().isEmpty() ? getCleanedVcardType(vPostalAddress.getTypes().get(0).getValue()) : null;
            postalAddresses.add(new Contact.PostalAddress(postalAddressTypeFromVcardType(label),
                    label,
                    vPostalAddress.getStreetAddress(),
                    vPostalAddress.getPoBox(),
                    null,
                    vPostalAddress.getLocality(),
                    vPostalAddress.getRegion(),
                    vPostalAddress.getPostalCode(),
                    vPostalAddress.getCountry()));
        }

        return new Contact(name, organization, phoneNumbers, emails, postalAddresses, null);
    }

    static Contact.Phone.Type phoneTypeFromContactType(int type) {
        switch (type) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                return Contact.Phone.Type.HOME;
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                return Contact.Phone.Type.MOBILE;
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                return Contact.Phone.Type.WORK;
        }
        return Contact.Phone.Type.CUSTOM;
    }

    private static Contact.Phone.Type phoneTypeFromVcardType(@Nullable String type) {
        if      ("home".equalsIgnoreCase(type)) return Contact.Phone.Type.HOME;
        else if ("cell".equalsIgnoreCase(type)) return Contact.Phone.Type.MOBILE;
        else if ("work".equalsIgnoreCase(type)) return Contact.Phone.Type.WORK;
        else                                    return Contact.Phone.Type.CUSTOM;
    }

    static Contact.Email.Type emailTypeFromContactType(int type) {
        switch (type) {
            case ContactsContract.CommonDataKinds.Email.TYPE_HOME:
                return Contact.Email.Type.HOME;
            case ContactsContract.CommonDataKinds.Email.TYPE_MOBILE:
                return Contact.Email.Type.MOBILE;
            case ContactsContract.CommonDataKinds.Email.TYPE_WORK:
                return Contact.Email.Type.WORK;
        }
        return Contact.Email.Type.CUSTOM;
    }

    private static Contact.Email.Type emailTypeFromVcardType(@Nullable String type) {
        if      ("home".equalsIgnoreCase(type)) return Contact.Email.Type.HOME;
        else if ("cell".equalsIgnoreCase(type)) return Contact.Email.Type.MOBILE;
        else if ("work".equalsIgnoreCase(type)) return Contact.Email.Type.WORK;
        else                                    return Contact.Email.Type.CUSTOM;
    }

    static Contact.PostalAddress.Type postalAddressTypeFromContactType(int type) {
        switch (type) {
            case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME:
                return Contact.PostalAddress.Type.HOME;
            case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK:
                return Contact.PostalAddress.Type.WORK;
        }
        return Contact.PostalAddress.Type.CUSTOM;
    }

    private static Contact.PostalAddress.Type postalAddressTypeFromVcardType(@Nullable String type) {
        if      ("home".equalsIgnoreCase(type)) return Contact.PostalAddress.Type.HOME;
        else if ("work".equalsIgnoreCase(type)) return Contact.PostalAddress.Type.WORK;
        else                                    return Contact.PostalAddress.Type.CUSTOM;
    }

    private static String getCleanedVcardType(@Nullable String type) {
        if (TextUtils.isEmpty(type)) return "";

        if (type.startsWith("x-") && type.length() > 2) {
            return type.substring(2);
        }

        return type;
    }

}

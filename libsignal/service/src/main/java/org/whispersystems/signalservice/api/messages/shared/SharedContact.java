package org.whispersystems.signalservice.api.messages.shared;


import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class SharedContact {

  private final Name                          name;
  private final Optional<Avatar>              avatar;
  private final Optional<List<Phone>>         phone;
  private final Optional<List<Email>>         email;
  private final Optional<List<PostalAddress>> address;
  private final Optional<String>              organization;

  public SharedContact(Name name,
                       Optional<Avatar> avatar,
                       Optional<List<Phone>> phone,
                       Optional<List<Email>> email,
                       Optional<List<PostalAddress>> address,
                       Optional<String> organization)
  {
    this.name         = name;
    this.avatar       = avatar;
    this.phone        = phone;
    this.email        = email;
    this.address      = address;
    this.organization = organization;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public Name getName() {
    return name;
  }

  public Optional<Avatar> getAvatar() {
    return avatar;
  }

  public Optional<List<Phone>> getPhone() {
    return phone;
  }

  public Optional<List<Email>> getEmail() {
    return email;
  }

  public Optional<List<PostalAddress>> getAddress() {
    return address;
  }

  public Optional<String> getOrganization() {
    return organization;
  }

  public static class Avatar {
    private final SignalServiceAttachment attachment;
    private final boolean                 isProfile;

    public Avatar(SignalServiceAttachment attachment, boolean isProfile) {
      this.attachment = attachment;
      this.isProfile  = isProfile;
    }

    public SignalServiceAttachment getAttachment() {
      return attachment;
    }

    public boolean isProfile() {
      return isProfile;
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    public static class Builder {
      private SignalServiceAttachment attachment;
      private boolean                 isProfile;

      public Builder withAttachment(SignalServiceAttachment attachment) {
        this.attachment = attachment;
        return this;
      }

      public Builder withProfileFlag(boolean isProfile) {
        this.isProfile = isProfile;
        return this;
      }

      public Avatar build() {
        return new Avatar(attachment, isProfile);
      }
    }
  }

  public static class Name {

    private final Optional<String> display;
    private final Optional<String> given;
    private final Optional<String> family;
    private final Optional<String> prefix;
    private final Optional<String> suffix;
    private final Optional<String> middle;

    public Name(Optional<String> display, Optional<String> given, Optional<String> family, Optional<String> prefix, Optional<String> suffix, Optional<String> middle) {
      this.display = display;
      this.given   = given;
      this.family  = family;
      this.prefix  = prefix;
      this.suffix  = suffix;
      this.middle  = middle;
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    public Optional<String> getDisplay() {
      return display;
    }

    public Optional<String> getGiven() {
      return given;
    }

    public Optional<String> getFamily() {
      return family;
    }

    public Optional<String> getPrefix() {
      return prefix;
    }

    public Optional<String> getSuffix() {
      return suffix;
    }

    public Optional<String> getMiddle() {
      return middle;
    }

    public static class Builder {
      private String display;
      private String given;
      private String family;
      private String prefix;
      private String suffix;
      private String middle;

      public Builder setDisplay(String display) {
        this.display = display;
        return this;
      }

      public Builder setGiven(String given) {
        this.given = given;
        return this;
      }

      public Builder setFamily(String family) {
        this.family = family;
        return this;
      }

      public Builder setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
      }

      public Builder setSuffix(String suffix) {
        this.suffix = suffix;
        return this;
      }

      public Builder setMiddle(String middle) {
        this.middle = middle;
        return this;
      }

      public Name build() {
        return new Name(Optional.fromNullable(display),
                        Optional.fromNullable(given),
                        Optional.fromNullable(family),
                        Optional.fromNullable(prefix),
                        Optional.fromNullable(suffix),
                        Optional.fromNullable(middle));
      }
    }
  }

  public static class Phone {

    public enum Type {
      HOME, WORK, MOBILE, CUSTOM
    }

    private final String           value;
    private final Type             type;
    private final Optional<String> label;

    public Phone(String value, Type type, Optional<String> label) {
      this.value = value;
      this.type  = type;
      this.label = label;
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    public String getValue() {
      return value;
    }

    public Type getType() {
      return type;
    }

    public Optional<String> getLabel() {
      return label;
    }

    public static class Builder {
      private String value;
      private Type   type;
      private String label;

      public Builder setValue(String value) {
        this.value = value;
        return this;
      }

      public Builder setType(Type type) {
        this.type = type;
        return this;
      }

      public Builder setLabel(String label) {
        this.label = label;
        return this;
      }

      public Phone build() {
        return new Phone(value, type, Optional.fromNullable(label));
      }
    }
  }

  public static class Email {

    public enum Type {
      HOME, WORK, MOBILE, CUSTOM
    }

    private final String           value;
    private final Type             type;
    private final Optional<String> label;

    public Email(String value, Type type, Optional<String> label) {
      this.value = value;
      this.type  = type;
      this.label = label;
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    public String getValue() {
      return value;
    }

    public Type getType() {
      return type;
    }

    public Optional<String> getLabel() {
      return label;
    }

    public static class Builder {
      private String value;
      private Type   type;
      private String label;

      public Builder setValue(String value) {
        this.value = value;
        return this;
      }

      public Builder setType(Type type) {
        this.type = type;
        return this;
      }

      public Builder setLabel(String label) {
        this.label = label;
        return this;
      }

      public Email build() {
        return new Email(value, type, Optional.fromNullable(label));
      }
    }
  }

  public static class PostalAddress {

    public enum Type {
      HOME, WORK, CUSTOM
    }

    private final Type   type;
    private final Optional<String> label;
    private final Optional<String> street;
    private final Optional<String> pobox;
    private final Optional<String> neighborhood;
    private final Optional<String> city;
    private final Optional<String> region;
    private final Optional<String> postcode;
    private final Optional<String> country;

    public PostalAddress(Type type, Optional<String> label, Optional<String> street,
                         Optional<String> pobox, Optional<String> neighborhood,
                         Optional<String> city, Optional<String> region,
                         Optional<String> postcode, Optional<String> country)
    {
      this.type         = type;
      this.label        = label;
      this.street       = street;
      this.pobox        = pobox;
      this.neighborhood = neighborhood;
      this.city         = city;
      this.region       = region;
      this.postcode     = postcode;
      this.country      = country;
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    public Type getType() {
      return type;
    }

    public Optional<String> getLabel() {
      return label;
    }

    public Optional<String> getStreet() {
      return street;
    }

    public Optional<String> getPobox() {
      return pobox;
    }

    public Optional<String> getNeighborhood() {
      return neighborhood;
    }

    public Optional<String> getCity() {
      return city;
    }

    public Optional<String> getRegion() {
      return region;
    }

    public Optional<String> getPostcode() {
      return postcode;
    }

    public Optional<String> getCountry() {
      return country;
    }

    public static class Builder {
      private Type   type;
      private String label;
      private String street;
      private String pobox;
      private String neighborhood;
      private String city;
      private String region;
      private String postcode;
      private String country;

      public Builder setType(Type type) {
        this.type = type;
        return this;
      }

      public Builder setLabel(String label) {
        this.label = label;
        return this;
      }

      public Builder setStreet(String street) {
        this.street = street;
        return this;
      }

      public Builder setPobox(String pobox) {
        this.pobox = pobox;
        return this;
      }

      public Builder setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
        return this;
      }

      public Builder setCity(String city) {
        this.city = city;
        return this;
      }

      public Builder setRegion(String region) {
        this.region = region;
        return this;
      }

      public Builder setPostcode(String postcode) {
        this.postcode = postcode;
        return this;
      }

      public Builder setCountry(String country) {
        this.country = country;
        return this;
      }

      public PostalAddress build() {
        return new PostalAddress(type, Optional.fromNullable(label), Optional.fromNullable(street),
                                 Optional.fromNullable(pobox), Optional.fromNullable(neighborhood),
                                 Optional.fromNullable(city), Optional.fromNullable(region),
                                 Optional.fromNullable(postcode), Optional.fromNullable(country));
      }
    }
  }

  public static class Builder {
    private Name   name;
    private Avatar avatar;
    private String organization;

    private List<Phone>         phone   = new LinkedList<>();
    private List<Email>         email   = new LinkedList<>();
    private List<PostalAddress> address = new LinkedList<>();

    public Builder setName(Name name) {
      this.name = name;
      return this;
    }

    public Builder withOrganization(String organization) {
      this.organization = organization;
      return this;
    }

    public Builder setAvatar(Avatar avatar) {
      this.avatar = avatar;
      return this;
    }

    public Builder withPhone(Phone phone) {
      this.phone.add(phone);
      return this;
    }

    public Builder withPhones(List<Phone> phones) {
      this.phone.addAll(phones);
      return this;
    }

    public Builder withEmail(Email email) {
      this.email.add(email);
      return this;
    }

    public Builder withEmails(List<Email> emails) {
      this.email.addAll(emails);
      return this;
    }

    public Builder withAddress(PostalAddress address) {
      this.address.add(address);
      return this;
    }

    public Builder withAddresses(List<PostalAddress> addresses) {
      this.address.addAll(addresses);
      return this;
    }

    public SharedContact build() {
      return new SharedContact(name, Optional.fromNullable(avatar),
                               phone.isEmpty()   ? Optional.<List<Phone>>absent() : Optional.of(phone),
                               email.isEmpty()   ? Optional.<List<Email>>absent() : Optional.of(email),
                               address.isEmpty() ? Optional.<List<PostalAddress>>absent() : Optional.of(address),
                               Optional.fromNullable(organization));
    }
  }
}

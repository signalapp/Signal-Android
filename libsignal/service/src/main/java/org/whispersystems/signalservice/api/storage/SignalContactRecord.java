package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;

import java.util.Arrays;
import java.util.Objects;

public final class SignalContactRecord implements SignalRecord {

  private final byte[]               key;
  private final SignalServiceAddress address;
  private final Optional<String>     givenName;
  private final Optional<String>     familyName;
  private final Optional<byte[]>     profileKey;
  private final Optional<String>     username;
  private final Optional<byte[]>     identityKey;
  private final IdentityState        identityState;
  private final boolean              blocked;
  private final boolean              profileSharingEnabled;
  private final Optional<String>     nickname;
  private final int                  protoVersion;

  private SignalContactRecord(byte[] key,
                              SignalServiceAddress address,
                              String givenName,
                              String familyName,
                              byte[] profileKey,
                              String username,
                              byte[] identityKey,
                              IdentityState identityState,
                              boolean blocked,
                              boolean profileSharingEnabled,
                              String nickname,
                              int protoVersion)
  {
    this.key                   = key;
    this.address               = address;
    this.givenName             = Optional.fromNullable(givenName);
    this.familyName            = Optional.fromNullable(familyName);
    this.profileKey            = Optional.fromNullable(profileKey);
    this.username              = Optional.fromNullable(username);
    this.identityKey           = Optional.fromNullable(identityKey);
    this.identityState         = identityState != null ? identityState : IdentityState.DEFAULT;
    this.blocked               = blocked;
    this.profileSharingEnabled = profileSharingEnabled;
    this.nickname              = Optional.fromNullable(nickname);
    this.protoVersion          = protoVersion;
  }

  @Override
  public byte[] getKey() {
    return key;
  }

  public SignalServiceAddress getAddress() {
    return address;
  }

  public Optional<String> getGivenName() {
    return givenName;
  }

  public Optional<String> getFamilyName() {
    return familyName;
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<String> getUsername() {
    return username;
  }

  public Optional<byte[]> getIdentityKey() {
    return identityKey;
  }

  public IdentityState getIdentityState() {
    return identityState;
  }

  public boolean isBlocked() {
    return blocked;
  }

  public boolean isProfileSharingEnabled() {
    return profileSharingEnabled;
  }

  public Optional<String> getNickname() {
    return nickname;
  }

  public int getProtoVersion() {
    return protoVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalContactRecord contact = (SignalContactRecord) o;
    return blocked == contact.blocked &&
        profileSharingEnabled == contact.profileSharingEnabled &&
        Arrays.equals(key, contact.key) &&
        Objects.equals(address, contact.address) &&
        givenName.equals(contact.givenName) &&
        familyName.equals(contact.familyName) &&
        OptionalUtil.byteArrayEquals(profileKey, contact.profileKey) &&
        username.equals(contact.username) &&
        OptionalUtil.byteArrayEquals(identityKey, contact.identityKey) &&
        identityState == contact.identityState &&
        Objects.equals(nickname, contact.nickname);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(address, givenName, familyName, username, identityState, blocked, profileSharingEnabled, nickname);
    result = 31 * result + Arrays.hashCode(key);
    result = 31 * result + OptionalUtil.byteArrayHashCode(profileKey);
    result = 31 * result + OptionalUtil.byteArrayHashCode(identityKey);
    return result;
  }

  public static final class Builder {
    private final byte[]               key;
    private final SignalServiceAddress address;

    private String        givenName;
    private String        familyName;
    private byte[]        profileKey;
    private String        username;
    private byte[]        identityKey;
    private IdentityState identityState;
    private boolean       blocked;
    private boolean       profileSharingEnabled;
    private String        nickname;
    private int           version;

    public Builder(byte[] key, SignalServiceAddress address) {
      this.key     = key;
      this.address = address;
    }

    public Builder setGivenName(String givenName) {
      this.givenName = givenName;
      return this;
    }

    public Builder setFamilyName(String familyName) {
      this.familyName = familyName;
      return this;
    }

    public Builder setProfileKey(byte[] profileKey) {
      this.profileKey = profileKey;
      return this;
    }

    public Builder setUsername(String username) {
      this.username = username;
      return this;
    }

    public Builder setIdentityKey(byte[] identityKey) {
      this.identityKey = identityKey;
      return this;
    }

    public Builder setIdentityState(IdentityState identityState) {
      this.identityState = identityState;
      return this;
    }

    public Builder setBlocked(boolean blocked) {
      this.blocked = blocked;
      return this;
    }

    public Builder setProfileSharingEnabled(boolean profileSharingEnabled) {
      this.profileSharingEnabled = profileSharingEnabled;
      return this;
    }

    public Builder setNickname(String nickname) {
      this.nickname = nickname;
      return this;
    }

    Builder setProtoVersion(int version) {
      this.version = version;
      return this;
    }

    public SignalContactRecord build() {
      return new SignalContactRecord(key,
                                      address,
                                      givenName,
                                      familyName,
                                      profileKey,
                                      username,
                                      identityKey,
                                      identityState,
                                      blocked,
                                      profileSharingEnabled,
                                      nickname,
                                      version);
    }
  }

  public enum IdentityState {
    DEFAULT, VERIFIED,  UNVERIFIED
  }
}

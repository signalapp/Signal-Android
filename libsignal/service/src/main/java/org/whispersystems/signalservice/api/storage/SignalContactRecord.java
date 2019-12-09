package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Arrays;
import java.util.Objects;

public class SignalContactRecord {

  private final byte[]               key;
  private final SignalServiceAddress address;
  private final Optional<String>     profileName;
  private final Optional<byte[]>     profileKey;
  private final Optional<String>     username;
  private final Optional<byte[]>     identityKey;
  private final IdentityState        identityState;
  private final boolean              blocked;
  private final boolean              profileSharingEnabled;
  private final Optional<String>     nickname;
  private final int protoVersion;

  private SignalContactRecord(byte[] key,
                              SignalServiceAddress address,
                              String profileName,
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
    this.profileName           = Optional.fromNullable(profileName);
    this.profileKey            = Optional.fromNullable(profileKey);
    this.username              = Optional.fromNullable(username);
    this.identityKey           = Optional.fromNullable(identityKey);
    this.identityState         = identityState != null ? identityState : IdentityState.DEFAULT;
    this.blocked               = blocked;
    this.profileSharingEnabled = profileSharingEnabled;
    this.nickname              = Optional.fromNullable(nickname);
    this.protoVersion = protoVersion;
  }

  public byte[] getKey() {
    return key;
  }

  public SignalServiceAddress getAddress() {
    return address;
  }

  public Optional<String> getProfileName() {
    return profileName;
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
        Objects.equals(profileName, contact.profileName) &&
        Objects.equals(profileKey, contact.profileKey) &&
        Objects.equals(username, contact.username) &&
        Objects.equals(identityKey, contact.identityKey) &&
        identityState == contact.identityState &&
        Objects.equals(nickname, contact.nickname);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(address, profileName, profileKey, username, identityKey, identityState, blocked, profileSharingEnabled, nickname);
    result = 31 * result + Arrays.hashCode(key);
    return result;
  }

  public static final class Builder {
    private final byte[]               key;
    private final SignalServiceAddress address;

    private String        profileName;
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

    public Builder setProfileName(String profileName) {
      this.profileName = profileName;
      return this;
    }

    public Builder setProfileKey(byte[] profileKey) {
      this.profileKey= profileKey;
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
                                      profileName,
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

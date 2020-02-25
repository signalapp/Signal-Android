package org.signal.zkgroup.profiles;

import org.signal.zkgroup.InvalidInputException;

import java.util.Arrays;

/**
 * Unlike the rest of this place-holder library, this does function as a wrapper around the
 * traditional byte array used for profile keys.
 */
public final class ProfileKey {

  public static final int SIZE = 32;

  private final byte[] profileKey;

  public ProfileKey(byte[] profileKey) throws InvalidInputException {
    if (profileKey == null || profileKey.length != SIZE) {
      throw new InvalidInputException();
    }

    this.profileKey = profileKey.clone();
  }

  public ProfileKeyVersion getProfileKeyVersion() {
    throw new AssertionError();
  }

  public ProfileKeyCommitment getCommitment() {
    throw new AssertionError();
  }

  public byte[] serialize() {
    return this.profileKey.clone();
  }

  @Override
  public boolean equals(Object o) {
    if(o == null || o.getClass() != getClass()) return false;

    ProfileKey other = (ProfileKey) o;

    return Arrays.equals(profileKey, other.profileKey);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(profileKey);
  }
}

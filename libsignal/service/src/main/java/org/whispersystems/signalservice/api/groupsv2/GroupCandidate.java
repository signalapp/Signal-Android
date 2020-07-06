package org.whispersystems.signalservice.api.groupsv2;

import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a potential new member of a group.
 * <p>
 * The entry may or may not have a {@link ProfileKeyCredential}.
 * <p>
 * If it does not, then this user can only be invited.
 * <p>
 * Equality by UUID only used to makes sure Sets only contain one copy.
 */
public final class GroupCandidate {

  private final UUID                           uuid;
  private final Optional<ProfileKeyCredential> profileKeyCredential;

  public GroupCandidate(UUID uuid, Optional<ProfileKeyCredential> profileKeyCredential) {
    this.uuid                 = uuid;
    this.profileKeyCredential = profileKeyCredential;
  }

  public UUID getUuid() {
    return uuid;
  }

  public Optional<ProfileKeyCredential> getProfileKeyCredential() {
    return profileKeyCredential;
  }

  public boolean hasProfileKeyCredential() {
    return profileKeyCredential.isPresent();
  }

  public static Set<GroupCandidate> withoutProfileKeyCredentials(Set<GroupCandidate> groupCandidates) {
    HashSet<GroupCandidate> result = new HashSet<>(groupCandidates.size());

    for (GroupCandidate candidate: groupCandidates) {
      result.add(candidate.withoutProfileKeyCredential());
    }

    return result;
  }

  public GroupCandidate withoutProfileKeyCredential() {
    return hasProfileKeyCredential() ? new GroupCandidate(uuid, Optional.absent())
                                     : this;
  }

  public GroupCandidate withProfileKeyCredential(ProfileKeyCredential profileKeyCredential) {
    return new GroupCandidate(uuid, Optional.of(profileKeyCredential));
  }

  public static List<UUID> toUuidList(Collection<GroupCandidate> candidates) {
    final List<UUID> uuidList = new ArrayList<>(candidates.size());

    for (GroupCandidate candidate : candidates) {
      uuidList.add(candidate.getUuid());
    }

    return uuidList;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }

    GroupCandidate other = (GroupCandidate) obj;
    return other.uuid == uuid;
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }
}

package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a potential new member of a group.
 * <p>
 * The entry may or may not have a {@link ExpiringProfileKeyCredential}.
 * <p>
 * If it does not, then this user can only be invited.
 * <p>
 * Equality by UUID only used to makes sure Sets only contain one copy.
 */
public final class GroupCandidate {

  private final UUID                                   uuid;
  private final Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential;

  public GroupCandidate(UUID uuid, Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential) {
    this.uuid                         = uuid;
    this.expiringProfileKeyCredential = expiringProfileKeyCredential;
  }

  public UUID getUuid() {
    return uuid;
  }

  public Optional<ExpiringProfileKeyCredential> getExpiringProfileKeyCredential() {
    return expiringProfileKeyCredential;
  }

  public ExpiringProfileKeyCredential requireExpiringProfileKeyCredential() {
    if (expiringProfileKeyCredential.isPresent()) {
      return expiringProfileKeyCredential.get();
    }
    throw new IllegalStateException("no profile key credential");
  }

  public boolean hasValidProfileKeyCredential() {
    return expiringProfileKeyCredential.map(ExpiringProfileCredentialUtil::isValid).orElse(false);
  }

  public static Set<GroupCandidate> withoutExpiringProfileKeyCredentials(Set<GroupCandidate> groupCandidates) {
    HashSet<GroupCandidate> result = new HashSet<>(groupCandidates.size());

    for (GroupCandidate candidate : groupCandidates) {
      result.add(candidate.withoutExpiringProfileKeyCredential());
    }

    return result;
  }

  public GroupCandidate withoutExpiringProfileKeyCredential() {
    return expiringProfileKeyCredential.isPresent() ? new GroupCandidate(uuid, Optional.empty())
                                                    : this;
  }

  public GroupCandidate withExpiringProfileKeyCredential(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
    return new GroupCandidate(uuid, Optional.of(expiringProfileKeyCredential));
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

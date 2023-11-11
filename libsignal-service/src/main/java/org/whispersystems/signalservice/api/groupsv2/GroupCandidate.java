package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a potential new member of a group.
 * <p>
 * The entry may or may not have a {@link ExpiringProfileKeyCredential}.
 * <p>
 * If it does not, then this user can only be invited.
 * <p>
 * Equality by ServiceId only used to makes sure Sets only contain one copy.
 */
public final class GroupCandidate {

  private final ServiceId                              serviceId;
  private final Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential;

  public GroupCandidate(ServiceId serviceId, Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential) {
    this.serviceId                    = serviceId;
    this.expiringProfileKeyCredential = expiringProfileKeyCredential;
  }

  public ServiceId getServiceId() {
    return serviceId;
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
    return expiringProfileKeyCredential.isPresent() ? new GroupCandidate(serviceId, Optional.empty())
                                                    : this;
  }

  public GroupCandidate withExpiringProfileKeyCredential(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
    return new GroupCandidate(serviceId, Optional.of(expiringProfileKeyCredential));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }

    GroupCandidate other = (GroupCandidate) obj;
    return other.serviceId.equals(serviceId);
  }

  @Override
  public int hashCode() {
    return serviceId.hashCode();
  }
}

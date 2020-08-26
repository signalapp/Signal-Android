package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;

public final class GroupLinkUrlAndStatus {

  public static final GroupLinkUrlAndStatus NONE = new GroupLinkUrlAndStatus(false, false, "");

  private final boolean enabled;
  private final boolean requiresApproval;
  private final String  url;

  public GroupLinkUrlAndStatus(boolean enabled,
                               boolean requiresApproval,
                               @NonNull String url)
  {
    this.enabled          = enabled;
    this.requiresApproval = requiresApproval;
    this.url              = url;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isRequiresApproval() {
    return requiresApproval;
  }

  public @NonNull String getUrl() {
    return url;
  }
}

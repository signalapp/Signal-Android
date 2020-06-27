package org.thoughtcrime.securesms.database.model;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GV2AccessLevelUtil;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

final class GroupsV2UpdateMessageProducer {

  @NonNull private final Context                context;
  @NonNull private final DescribeMemberStrategy descriptionStrategy;
  @NonNull private final UUID                   selfUuid;
  @NonNull private final ByteString             selfUuidBytes;

  /**
   * @param descriptionStrategy Strategy for member description.
   */
  GroupsV2UpdateMessageProducer(@NonNull Context context,
                                @NonNull DescribeMemberStrategy descriptionStrategy,
                                @NonNull UUID selfUuid)
  {
    this.context             = context;
    this.descriptionStrategy = descriptionStrategy;
    this.selfUuid            = selfUuid;
    this.selfUuidBytes       = UuidUtil.toByteString(selfUuid);
  }

  /**
   * Describes a group that is new to you, use this when there is no available change record.
   * <p>
   * Invitation and groups you create are the most common cases where no change is available.
   */
  String describeNewGroup(@NonNull DecryptedGroup group) {
    Optional<DecryptedPendingMember> selfPending = DecryptedGroupUtil.findPendingByUuid(group.getPendingMembersList(), selfUuid);
    if (selfPending.isPresent()) {
      return context.getString(R.string.MessageRecord_s_invited_you_to_the_group, describe(selfPending.get().getAddedByUuid()));
    }

    if (group.getRevision() == 0) {
      Optional<DecryptedMember> foundingMember = DecryptedGroupUtil.firstMember(group.getMembersList());
      if (foundingMember.isPresent()) {
        ByteString foundingMemberUuid = foundingMember.get().getUuid();
        if (selfUuidBytes.equals(foundingMemberUuid)) {
          return context.getString(R.string.MessageRecord_you_created_the_group);
        } else {
          return context.getString(R.string.MessageRecord_s_added_you, describe(foundingMemberUuid));
        }
      }
    }

    if (DecryptedGroupUtil.findMemberByUuid(group.getMembersList(), selfUuid).isPresent()) {
      return context.getString(R.string.MessageRecord_you_joined_the_group);
    } else {
      return context.getString(R.string.MessageRecord_group_updated);
    }
  }

  List<String> describeChange(@NonNull DecryptedGroupChange change) {
    List<String> updates = new LinkedList<>();

    describeMemberAdditions(change, updates);
    describeMemberRemovals(change, updates);
    describeModifyMemberRoles(change, updates);
    describeInvitations(change, updates);
    describeRevokedInvitations(change, updates);
    describePromotePending(change, updates);
    describeNewTitle(change, updates);
    describeNewAvatar(change, updates);
    describeNewTimer(change, updates);
    describeNewAttributeAccess(change, updates);
    describeNewMembershipAccess(change, updates);

    if (updates.isEmpty()) {
      describeUnknownChange(change, updates);
    }

    return updates;
  }

  /**
   * Handles case of future protocol versions where we don't know what has changed.
   */
  private void describeUnknownChange(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (editorIsYou) {
      updates.add(context.getString(R.string.MessageRecord_you_updated_group));
    } else {
      updates.add(context.getString(R.string.MessageRecord_s_updated_group, describe(change.getEditor())));
    }
  }

  private void describeMemberAdditions(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (DecryptedMember member : change.getNewMembersList()) {
      boolean newMemberIsYou = member.getUuid().equals(selfUuidBytes);

      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(context.getString(R.string.MessageRecord_you_joined_the_group));
        } else {
          updates.add(context.getString(R.string.MessageRecord_you_added_s, describe(member.getUuid())));
        }
      } else {
        if (newMemberIsYou) {
          updates.add(context.getString(R.string.MessageRecord_s_added_you, describe(change.getEditor())));
        } else {
          if (member.getUuid().equals(change.getEditor())) {
            updates.add(context.getString(R.string.MessageRecord_s_joined_the_group, describe(member.getUuid())));
          } else {
            updates.add(context.getString(R.string.MessageRecord_s_added_s, describe(change.getEditor()), describe(member.getUuid())));
          }
        }
      }
    }
  }

  private void describeMemberRemovals(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (ByteString member : change.getDeleteMembersList()) {
      boolean removedMemberIsYou = member.equals(selfUuidBytes);

      if (editorIsYou) {
        if (removedMemberIsYou) {
          updates.add(context.getString(R.string.MessageRecord_you_left_the_group));
        } else {
          updates.add(context.getString(R.string.MessageRecord_you_removed_s, describe(member)));
        }
      } else {
        if (removedMemberIsYou) {
          updates.add(context.getString(R.string.MessageRecord_s_removed_you_from_the_group, describe(change.getEditor())));
        } else {
          if (member.equals(change.getEditor())) {
            updates.add(context.getString(R.string.MessageRecord_s_left_the_group, describe(member)));
          } else {
            updates.add(context.getString(R.string.MessageRecord_s_removed_s, describe(change.getEditor()), describe(member)));
          }
        }
      }
    }
  }

  private void describeModifyMemberRoles(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (DecryptedModifyMemberRole roleChange : change.getModifyMemberRolesList()) {
      if (roleChange.getRole() == Member.Role.ADMINISTRATOR) {
        boolean newMemberIsYou = roleChange.getUuid().equals(selfUuidBytes);
        if (editorIsYou) {
          updates.add(context.getString(R.string.MessageRecord_you_made_s_an_admin, describe(roleChange.getUuid())));
        } else {
          if (newMemberIsYou) {
            updates.add(context.getString(R.string.MessageRecord_s_made_you_an_admin, describe(change.getEditor())));
          } else {
            updates.add(context.getString(R.string.MessageRecord_s_made_s_an_admin, describe(change.getEditor()), describe(roleChange.getUuid())));

          }
        }
      } else {
        boolean newMemberIsYou = roleChange.getUuid().equals(selfUuidBytes);
        if (editorIsYou) {
          updates.add(context.getString(R.string.MessageRecord_you_revoked_admin_privileges_from_s, describe(roleChange.getUuid())));
        } else {
          if (newMemberIsYou) {
            updates.add(context.getString(R.string.MessageRecord_s_revoked_your_admin_privileges, describe(change.getEditor())));
          } else {
            updates.add(context.getString(R.string.MessageRecord_s_revoked_admin_privileges_from_s, describe(change.getEditor()), describe(roleChange.getUuid())));
          }
        }
      }
    }
  }

  private void describeInvitations(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou       = change.getEditor().equals(selfUuidBytes);
    int     notYouInviteCount = 0;

    for (DecryptedPendingMember invitee : change.getNewPendingMembersList()) {
      boolean newMemberIsYou = invitee.getUuid().equals(selfUuidBytes);

      if (newMemberIsYou) {
        updates.add(context.getString(R.string.MessageRecord_s_invited_you_to_the_group, describe(change.getEditor())));
      } else {
        if (editorIsYou) {
          updates.add(context.getString(R.string.MessageRecord_you_invited_s_to_the_group, describe(invitee.getUuid())));
        } else {
          notYouInviteCount++;
        }
      }
    }

    if (notYouInviteCount > 0) {
      updates.add(context.getResources().getQuantityString(R.plurals.MessageRecord_s_invited_members, notYouInviteCount, describe(change.getEditor()), notYouInviteCount));
    }
  }

  private void describeRevokedInvitations(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou     = change.getEditor().equals(selfUuidBytes);
    int     notDeclineCount = 0;

    for (DecryptedPendingMemberRemoval invitee : change.getDeletePendingMembersList()) {
      boolean decline = invitee.getUuid().equals(change.getEditor());
      if (decline) {
        if (editorIsYou) {
          updates.add(context.getString(R.string.MessageRecord_you_declined_the_invitation_to_the_group));
        } else {
          updates.add(context.getString(R.string.MessageRecord_someone_declined_an_invitation_to_the_group));
        }
      } else {
        notDeclineCount++;
      }
    }

    if (notDeclineCount > 0) {
      if (editorIsYou) {
        updates.add(context.getResources().getQuantityString(R.plurals.MessageRecord_you_revoked_invites, notDeclineCount, notDeclineCount));
      } else {
        updates.add(context.getResources().getQuantityString(R.plurals.MessageRecord_s_revoked_invites, notDeclineCount, describe(change.getEditor()), notDeclineCount));
      }
    }
  }

  private void describePromotePending(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (DecryptedMember newMember : change.getPromotePendingMembersList()) {
      ByteString uuid           = newMember.getUuid();
      boolean    newMemberIsYou = uuid.equals(selfUuidBytes);

      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(context.getString(R.string.MessageRecord_you_accepted_invite));
        } else {
          updates.add(context.getString(R.string.MessageRecord_you_added_invited_member_s, describe(uuid)));
        }
      } else {
        if (newMemberIsYou) {
          updates.add(context.getString(R.string.MessageRecord_s_added_you, describe(change.getEditor())));
        } else {
          if (uuid.equals(change.getEditor())) {
            updates.add(context.getString(R.string.MessageRecord_s_accepted_invite, describe(uuid)));
          } else {
            updates.add(context.getString(R.string.MessageRecord_s_added_invited_member_s, describe(change.getEditor()), describe(uuid)));
          }
        }
      }
    }
  }

  private void describeNewTitle(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.hasNewTitle()) {
      if (editorIsYou) {
        updates.add(context.getString(R.string.MessageRecord_you_changed_the_group_name_to_s, change.getNewTitle().getValue()));
      } else {
        updates.add(context.getString(R.string.MessageRecord_s_changed_the_group_name_to_s, describe(change.getEditor()), change.getNewTitle().getValue()));
      }
    }
  }

  private void describeNewAvatar(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.hasNewAvatar()) {
      if (editorIsYou) {
        updates.add(context.getString(R.string.MessageRecord_you_changed_the_group_avatar));
      } else {
        updates.add(context.getString(R.string.MessageRecord_s_changed_the_group_avatar, describe(change.getEditor())));
      }
    }
  }

  private void describeNewTimer(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.hasNewTimer()) {
      String time = ExpirationUtil.getExpirationDisplayValue(context, change.getNewTimer().getDuration());
      if (editorIsYou) {
        updates.add(context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time));
      } else {
        updates.add(context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, describe(change.getEditor()), time));
      }
    }
  }

  private void describeNewAttributeAccess(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.getNewAttributeAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewAttributeAccess());
      if (editorIsYou) {
        updates.add(context.getString(R.string.MessageRecord_you_changed_who_can_edit_group_info_to_s, accessLevel));
      } else {
        updates.add(context.getString(R.string.MessageRecord_s_changed_who_can_edit_group_info_to_s, describe(change.getEditor()), accessLevel));
      }
    }
  }

  private void describeNewMembershipAccess(@NonNull DecryptedGroupChange change, @NonNull List<String> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.getNewMemberAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewMemberAccess());
      if (editorIsYou) {
        updates.add(context.getString(R.string.MessageRecord_you_changed_who_can_edit_group_membership_to_s, accessLevel));
      } else {
        updates.add(context.getString(R.string.MessageRecord_s_changed_who_can_edit_group_membership_to_s, describe(change.getEditor()), accessLevel));
      }
    }
  }

  private @NonNull String describe(@NonNull ByteString uuid) {
    return descriptionStrategy.describe(UuidUtil.fromByteString(uuid));
  }

  interface DescribeMemberStrategy {

    /**
     * Map a UUID to a string that describes the group member.
     */
    @NonNull String describe(@NonNull UUID uuid);
  }
}
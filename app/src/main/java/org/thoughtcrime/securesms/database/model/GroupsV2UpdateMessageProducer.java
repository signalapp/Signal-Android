package org.thoughtcrime.securesms.database.model;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.protobuf.ByteString;

import org.signal.core.util.StringUtil;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GV2AccessLevelUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceIds;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class GroupsV2UpdateMessageProducer {

  @NonNull private final  Context               context;
  @NonNull private final  ServiceIds            selfIds;
  @Nullable private final Consumer<RecipientId> recipientClickHandler;

  GroupsV2UpdateMessageProducer(@NonNull Context context, @NonNull ServiceIds selfIds, @Nullable Consumer<RecipientId> recipientClickHandler) {
    this.context               = context;
    this.selfIds               = selfIds;
    this.recipientClickHandler = recipientClickHandler;
  }

  /**
   * Describes a group that is new to you, use this when there is no available change record.
   * <p>
   * Invitation and revision 0 groups are the most common use cases for this.
   * <p>
   * When invited, it's possible there's no change available.
   * <p>
   * When the revision of the group is 0, the change is very noisy and only the editor is useful.
   */
  UpdateDescription describeNewGroup(@NonNull DecryptedGroup group, @NonNull DecryptedGroupChange decryptedGroupChange) {
    Optional<DecryptedPendingMember> selfPending = DecryptedGroupUtil.findPendingByUuid(group.getPendingMembersList(), selfIds.getAci().uuid());
    if (!selfPending.isPresent() && selfIds.getPni() != null) {
      selfPending = DecryptedGroupUtil.findPendingByUuid(group.getPendingMembersList(), selfIds.getPni().uuid());
    }

    if (selfPending.isPresent()) {
      return updateDescription(R.string.MessageRecord_s_invited_you_to_the_group, selfPending.get().getAddedByUuid(), R.drawable.ic_update_group_add_16);
    }

    ByteString foundingMemberUuid = decryptedGroupChange.getEditor();
    if (!foundingMemberUuid.isEmpty()) {
      if (selfIds.matches(foundingMemberUuid)) {
        return updateDescription(context.getString(R.string.MessageRecord_you_created_the_group), R.drawable.ic_update_group_16);
      } else {
        return updateDescription(R.string.MessageRecord_s_added_you, foundingMemberUuid, R.drawable.ic_update_group_add_16);
      }
    }

    if (DecryptedGroupUtil.findMemberByUuid(group.getMembersList(), selfIds.getAci().uuid()).isPresent() ||
        (selfIds.getPni() != null && DecryptedGroupUtil.findMemberByUuid(group.getMembersList(), selfIds.getPni().uuid()).isPresent()))
    {
      return updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group), R.drawable.ic_update_group_add_16);
    } else {
      return updateDescription(context.getString(R.string.MessageRecord_group_updated), R.drawable.ic_update_group_16);
    }
  }

  List<UpdateDescription> describeChanges(@Nullable DecryptedGroup previousGroupState, @NonNull DecryptedGroupChange change) {
    if (DecryptedGroup.getDefaultInstance().equals(previousGroupState)) {
      previousGroupState = null;
    }

    List<UpdateDescription> updates = new LinkedList<>();

    if (change.getEditor().isEmpty() || UuidUtil.UNKNOWN_UUID.equals(UuidUtil.fromByteString(change.getEditor()))) {
      describeUnknownEditorMemberAdditions(change, updates);

      describeUnknownEditorModifyMemberRoles(change, updates);
      describeUnknownEditorInvitations(change, updates);
      describeUnknownEditorRevokedInvitations(change, updates);
      describeUnknownEditorPromotePending(change, updates);
      describeUnknownEditorNewTitle(change, updates);
      describeUnknownEditorNewDescription(change, updates);
      describeUnknownEditorNewAvatar(change, updates);
      describeUnknownEditorNewTimer(change, updates);
      describeUnknownEditorNewAttributeAccess(change, updates);
      describeUnknownEditorNewMembershipAccess(change, updates);
      describeUnknownEditorNewGroupInviteLinkAccess(previousGroupState, change, updates);
      describeRequestingMembers(change, updates);
      describeUnknownEditorRequestingMembersApprovals(change, updates);
      describeUnknownEditorRequestingMembersDeletes(change, updates);
      describeUnknownEditorAnnouncementGroupChange(change, updates);
      describeUnknownEditorPromotePendingPniAci(change, updates);

      describeUnknownEditorMemberRemovals(change, updates);

      if (updates.isEmpty()) {
        describeUnknownEditorUnknownChange(updates);
      }

    } else {
      describeMemberAdditions(change, updates);

      describeModifyMemberRoles(change, updates);
      describeInvitations(change, updates);
      describeRevokedInvitations(change, updates);
      describePromotePending(change, updates);
      describeNewTitle(change, updates);
      describeNewDescription(change, updates);
      describeNewAvatar(change, updates);
      describeNewTimer(change, updates);
      describeNewAttributeAccess(change, updates);
      describeNewMembershipAccess(change, updates);
      describeNewGroupInviteLinkAccess(previousGroupState, change, updates);
      describeRequestingMembers(change, updates);
      describeRequestingMembersApprovals(change, updates);
      describeRequestingMembersDeletes(change, updates);
      describeAnnouncementGroupChange(change, updates);
      describePromotePendingPniAci(change, updates);

      describeMemberRemovals(change, updates);

      if (updates.isEmpty()) {
        describeUnknownChange(change, updates);
      }
    }

    return updates;
  }

  /**
   * Handles case of future protocol versions where we don't know what has changed.
   */
  private void describeUnknownChange(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (editorIsYou) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_you_updated_group), R.drawable.ic_update_group_16));
    } else {
      updates.add(updateDescription(R.string.MessageRecord_s_updated_group, change.getEditor(), R.drawable.ic_update_group_16));
    }
  }

  private void describeUnknownEditorUnknownChange(@NonNull List<UpdateDescription> updates) {
    updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_was_updated), R.drawable.ic_update_group_16));
  }

  private void describeMemberAdditions(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    for (DecryptedMember member : change.getNewMembersList()) {
      boolean newMemberIsYou = selfIds.matches(member.getUuid());

      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(0, updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group_via_the_group_link), R.drawable.ic_update_group_accept_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_you_added_s, member.getUuid(), R.drawable.ic_update_group_add_16));
        }
      } else {
        if (newMemberIsYou) {
          updates.add(0, updateDescription(R.string.MessageRecord_s_added_you, change.getEditor(), R.drawable.ic_update_group_add_16));
        } else {
          if (member.getUuid().equals(change.getEditor())) {
            updates.add(updateDescription(R.string.MessageRecord_s_joined_the_group_via_the_group_link, member.getUuid(), R.drawable.ic_update_group_accept_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_added_s, change.getEditor(), member.getUuid(), R.drawable.ic_update_group_add_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorMemberAdditions(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedMember member : change.getNewMembersList()) {
      boolean newMemberIsYou = selfIds.matches(member.getUuid());

      if (newMemberIsYou) {
        updates.add(0, updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group), R.drawable.ic_update_group_add_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_joined_the_group, member.getUuid(), R.drawable.ic_update_group_add_16));
      }
    }
  }

  private void describeMemberRemovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    for (ByteString member : change.getDeleteMembersList()) {
      boolean removedMemberIsYou = selfIds.matches(member);

      if (editorIsYou) {
        if (removedMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_left_the_group), R.drawable.ic_update_group_leave_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_you_removed_s, member, R.drawable.ic_update_group_remove_16));
        }
      } else {
        if (removedMemberIsYou) {
          updates.add(updateDescription(R.string.MessageRecord_s_removed_you_from_the_group, change.getEditor(), R.drawable.ic_update_group_remove_16));
        } else {
          if (member.equals(change.getEditor())) {
            updates.add(updateDescription(R.string.MessageRecord_s_left_the_group, member, R.drawable.ic_update_group_leave_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_removed_s, change.getEditor(), member, R.drawable.ic_update_group_remove_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorMemberRemovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (ByteString member : change.getDeleteMembersList()) {
      boolean removedMemberIsYou = selfIds.matches(member);

      if (removedMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_are_no_longer_in_the_group), R.drawable.ic_update_group_leave_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_is_no_longer_in_the_group, member, R.drawable.ic_update_group_leave_16));
      }
    }
  }

  private void describeModifyMemberRoles(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    for (DecryptedModifyMemberRole roleChange : change.getModifyMemberRolesList()) {
      boolean changedMemberIsYou = selfIds.matches(roleChange.getUuid());
      if (roleChange.getRole() == Member.Role.ADMINISTRATOR) {
        if (editorIsYou) {
          updates.add(updateDescription(R.string.MessageRecord_you_made_s_an_admin, roleChange.getUuid(), R.drawable.ic_update_group_role_16));
        } else {
          if (changedMemberIsYou) {
            updates.add(updateDescription(R.string.MessageRecord_s_made_you_an_admin, change.getEditor(), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_made_s_an_admin, change.getEditor(), roleChange.getUuid(), R.drawable.ic_update_group_role_16));

          }
        }
      } else {
        if (editorIsYou) {
          updates.add(updateDescription(R.string.MessageRecord_you_revoked_admin_privileges_from_s, roleChange.getUuid(), R.drawable.ic_update_group_role_16));
        } else {
          if (changedMemberIsYou) {
            updates.add(updateDescription(R.string.MessageRecord_s_revoked_your_admin_privileges, change.getEditor(), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_revoked_admin_privileges_from_s, change.getEditor(), roleChange.getUuid(), R.drawable.ic_update_group_role_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorModifyMemberRoles(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedModifyMemberRole roleChange : change.getModifyMemberRolesList()) {
      boolean changedMemberIsYou = selfIds.matches(roleChange.getUuid());

      if (roleChange.getRole() == Member.Role.ADMINISTRATOR) {
        if (changedMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_are_now_an_admin), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_s_is_now_an_admin, roleChange.getUuid(), R.drawable.ic_update_group_role_16));
        }
      } else {
        if (changedMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_are_no_longer_an_admin), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_s_is_no_longer_an_admin, roleChange.getUuid(), R.drawable.ic_update_group_role_16));
        }
      }
    }
  }

  private void describeInvitations(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou       = selfIds.matches(change.getEditor());
    int     notYouInviteCount = 0;

    for (DecryptedPendingMember invitee : change.getNewPendingMembersList()) {
      boolean newMemberIsYou = selfIds.matches(invitee.getUuid());

      if (newMemberIsYou) {
        updates.add(0, updateDescription(R.string.MessageRecord_s_invited_you_to_the_group, change.getEditor(), R.drawable.ic_update_group_add_16));
      } else {
        if (editorIsYou) {
          updates.add(updateDescription(R.string.MessageRecord_you_invited_s_to_the_group, invitee.getUuid(), R.drawable.ic_update_group_add_16));
        } else {
          notYouInviteCount++;
        }
      }
    }

    if (notYouInviteCount > 0) {
      updates.add(updateDescription(R.plurals.MessageRecord_s_invited_members, notYouInviteCount, change.getEditor(), notYouInviteCount, R.drawable.ic_update_group_add_16));
    }
  }

  private void describeUnknownEditorInvitations(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    int notYouInviteCount = 0;

    for (DecryptedPendingMember invitee : change.getNewPendingMembersList()) {
      boolean newMemberIsYou = selfIds.matches(invitee.getUuid());

      if (newMemberIsYou) {
        UUID uuid = UuidUtil.fromByteStringOrUnknown(invitee.getAddedByUuid());

        if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
          updates.add(0, updateDescription(context.getString(R.string.MessageRecord_you_were_invited_to_the_group), R.drawable.ic_update_group_add_16));
        } else {
          updates.add(0, updateDescription(R.string.MessageRecord_s_invited_you_to_the_group, invitee.getAddedByUuid(), R.drawable.ic_update_group_add_16));
        }
      } else {
        notYouInviteCount++;
      }
    }

    if (notYouInviteCount > 0) {
      updates.add(updateDescription(context.getResources().getQuantityString(R.plurals.MessageRecord_d_people_were_invited_to_the_group, notYouInviteCount, notYouInviteCount), R.drawable.ic_update_group_add_16));
    }
  }

  private void describeRevokedInvitations(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou     = selfIds.matches(change.getEditor());
    int     notDeclineCount = 0;

    for (DecryptedPendingMemberRemoval invitee : change.getDeletePendingMembersList()) {
      boolean decline = invitee.getUuid().equals(change.getEditor());
      if (decline) {
        if (editorIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_declined_the_invitation_to_the_group), R.drawable.ic_update_group_decline_16));
        } else {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_someone_declined_an_invitation_to_the_group), R.drawable.ic_update_group_decline_16));
        }
      } else if (selfIds.matches(invitee.getUuid())) {
        updates.add(updateDescription(R.string.MessageRecord_s_revoked_your_invitation_to_the_group, change.getEditor(), R.drawable.ic_update_group_decline_16));
      } else {
        notDeclineCount++;
      }
    }

    if (notDeclineCount > 0) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getResources().getQuantityString(R.plurals.MessageRecord_you_revoked_invites, notDeclineCount, notDeclineCount), R.drawable.ic_update_group_decline_16));
      } else {
        updates.add(updateDescription(R.plurals.MessageRecord_s_revoked_invites, notDeclineCount, change.getEditor(), notDeclineCount, R.drawable.ic_update_group_decline_16));
      }
    }
  }

  private void describeUnknownEditorRevokedInvitations(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    int notDeclineCount = 0;

    for (DecryptedPendingMemberRemoval invitee : change.getDeletePendingMembersList()) {
      boolean inviteeWasYou = selfIds.matches(invitee.getUuid());

      if (inviteeWasYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_an_admin_revoked_your_invitation_to_the_group), R.drawable.ic_update_group_decline_16));
      } else {
        notDeclineCount++;
      }
    }

    if (notDeclineCount > 0) {
      updates.add(updateDescription(context.getResources().getQuantityString(R.plurals.MessageRecord_d_invitations_were_revoked, notDeclineCount, notDeclineCount), R.drawable.ic_update_group_decline_16));
    }
  }

  private void describePromotePending(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    for (DecryptedMember newMember : change.getPromotePendingMembersList()) {
      ByteString uuid           = newMember.getUuid();
      boolean    newMemberIsYou = selfIds.matches(uuid);

      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_accepted_invite), R.drawable.ic_update_group_accept_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_you_added_invited_member_s, uuid, R.drawable.ic_update_group_add_16));
        }
      } else {
        if (newMemberIsYou) {
          updates.add(updateDescription(R.string.MessageRecord_s_added_you, change.getEditor(), R.drawable.ic_update_group_add_16));
        } else {
          if (uuid.equals(change.getEditor())) {
            updates.add(updateDescription(R.string.MessageRecord_s_accepted_invite, uuid, R.drawable.ic_update_group_accept_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_added_invited_member_s, change.getEditor(), uuid, R.drawable.ic_update_group_add_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorPromotePending(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedMember newMember : change.getPromotePendingMembersList()) {
      ByteString uuid           = newMember.getUuid();
      boolean    newMemberIsYou = selfIds.matches(uuid);

      if (newMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group), R.drawable.ic_update_group_add_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_joined_the_group, uuid, R.drawable.ic_update_group_add_16));
      }
    }
  }

  private void describeNewTitle(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (change.hasNewTitle()) {
      String newTitle = StringUtil.isolateBidi(change.getNewTitle().getValue());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_the_group_name_to_s, newTitle), R.drawable.ic_update_group_name_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_changed_the_group_name_to_s, change.getEditor(), newTitle, R.drawable.ic_update_group_name_16));
      }
    }
  }

  private void describeNewDescription(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (change.hasNewDescription()) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_the_group_description), R.drawable.ic_update_group_name_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_changed_the_group_description, change.getEditor(), R.drawable.ic_update_group_name_16));
      }
    }
  }

  private void describeUnknownEditorNewTitle(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.hasNewTitle()) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_name_has_changed_to_s, StringUtil.isolateBidi(change.getNewTitle().getValue())), R.drawable.ic_update_group_name_16));
    }
  }

  private void describeUnknownEditorNewDescription(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.hasNewDescription()) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_description_has_changed), R.drawable.ic_update_group_name_16));
    }
  }

  private void describeNewAvatar(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (change.hasNewAvatar()) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_the_group_avatar), R.drawable.ic_update_group_avatar_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_changed_the_group_avatar, change.getEditor(), R.drawable.ic_update_group_avatar_16));
      }
    }
  }

  private void describeUnknownEditorNewAvatar(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.hasNewAvatar()) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_group_avatar_has_been_changed), R.drawable.ic_update_group_avatar_16));
    }
  }

  void describeNewTimer(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (change.hasNewTimer()) {
      String time = ExpirationUtil.getExpirationDisplayValue(context, change.getNewTimer().getDuration());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time), R.drawable.ic_update_timer_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_set_disappearing_message_time_to_s, change.getEditor(), time, R.drawable.ic_update_timer_16));
      }
    }
  }

  private void describeUnknownEditorNewTimer(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.hasNewTimer()) {
      String time = ExpirationUtil.getExpirationDisplayValue(context, change.getNewTimer().getDuration());
      updates.add(updateDescription(context.getString(R.string.MessageRecord_disappearing_message_time_set_to_s, time), R.drawable.ic_update_timer_16));
    }
  }

  private void describeNewAttributeAccess(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (change.getNewAttributeAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewAttributeAccess());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_who_can_edit_group_info_to_s, accessLevel), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_changed_who_can_edit_group_info_to_s, change.getEditor(), accessLevel, R.drawable.ic_update_group_role_16));
      }
    }
  }

  private void describeUnknownEditorNewAttributeAccess(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.getNewAttributeAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewAttributeAccess());
      updates.add(updateDescription(context.getString(R.string.MessageRecord_who_can_edit_group_info_has_been_changed_to_s, accessLevel), R.drawable.ic_update_group_role_16));
    }
  }

  private void describeNewMembershipAccess(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (change.getNewMemberAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewMemberAccess());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_who_can_edit_group_membership_to_s, accessLevel), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_changed_who_can_edit_group_membership_to_s, change.getEditor(), accessLevel, R.drawable.ic_update_group_role_16));
      }
    }
  }

  private void describeUnknownEditorNewMembershipAccess(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.getNewMemberAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewMemberAccess());
      updates.add(updateDescription(context.getString(R.string.MessageRecord_who_can_edit_group_membership_has_been_changed_to_s, accessLevel), R.drawable.ic_update_group_role_16));
    }
  }

  private void describeNewGroupInviteLinkAccess(@Nullable DecryptedGroup previousGroupState,
                                                @NonNull DecryptedGroupChange change,
                                                @NonNull List<UpdateDescription> updates)
  {
    AccessControl.AccessRequired previousAccessControl = null;

    if (previousGroupState != null) {
      previousAccessControl = previousGroupState.getAccessControl().getAddFromInviteLink();
    }

    boolean editorIsYou      = selfIds.matches(change.getEditor());
    boolean groupLinkEnabled = false;

    switch (change.getNewInviteLinkAccess()) {
      case ANY:
        groupLinkEnabled = true;
        if (editorIsYou) {
          if (previousAccessControl == AccessControl.AccessRequired.ADMINISTRATOR) {
            updates.add(updateDescription(context.getString(R.string.MessageRecord_you_turned_off_admin_approval_for_the_group_link), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(context.getString(R.string.MessageRecord_you_turned_on_the_group_link_with_admin_approval_off), R.drawable.ic_update_group_role_16));
          }
        } else {
          if (previousAccessControl == AccessControl.AccessRequired.ADMINISTRATOR) {
            updates.add(updateDescription(R.string.MessageRecord_s_turned_off_admin_approval_for_the_group_link, change.getEditor(), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_turned_on_the_group_link_with_admin_approval_off, change.getEditor(), R.drawable.ic_update_group_role_16));
          }
        }
        break;
      case ADMINISTRATOR:
        groupLinkEnabled = true;
        if (editorIsYou) {
          if (previousAccessControl == AccessControl.AccessRequired.ANY) {
            updates.add(updateDescription(context.getString(R.string.MessageRecord_you_turned_on_admin_approval_for_the_group_link), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(context.getString(R.string.MessageRecord_you_turned_on_the_group_link_with_admin_approval_on), R.drawable.ic_update_group_role_16));
          }
        } else {
          if (previousAccessControl == AccessControl.AccessRequired.ANY) {
            updates.add(updateDescription(R.string.MessageRecord_s_turned_on_admin_approval_for_the_group_link, change.getEditor(), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_turned_on_the_group_link_with_admin_approval_on, change.getEditor(), R.drawable.ic_update_group_role_16));
          }
        }
        break;
      case UNSATISFIABLE:
        if (editorIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_turned_off_the_group_link), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_s_turned_off_the_group_link, change.getEditor(), R.drawable.ic_update_group_role_16));
        }
        break;
    }

    if (!groupLinkEnabled && change.getNewInviteLinkPassword().size() > 0) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_reset_the_group_link), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_reset_the_group_link, change.getEditor(), R.drawable.ic_update_group_role_16));
      }
    }
  }

  private void describeUnknownEditorNewGroupInviteLinkAccess(@Nullable DecryptedGroup previousGroupState,
                                                             @NonNull DecryptedGroupChange change,
                                                             @NonNull List<UpdateDescription> updates)
  {
    AccessControl.AccessRequired previousAccessControl = null;

    if (previousGroupState != null) {
      previousAccessControl = previousGroupState.getAccessControl().getAddFromInviteLink();
    }

    switch (change.getNewInviteLinkAccess()) {
      case ANY:
        if (previousAccessControl == AccessControl.AccessRequired.ADMINISTRATOR) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_the_admin_approval_for_the_group_link_has_been_turned_off), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_link_has_been_turned_on_with_admin_approval_off), R.drawable.ic_update_group_role_16));
        }
        break;
      case ADMINISTRATOR:
        if (previousAccessControl == AccessControl.AccessRequired.ANY) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_the_admin_approval_for_the_group_link_has_been_turned_on), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_link_has_been_turned_on_with_admin_approval_on), R.drawable.ic_update_group_role_16));
        }
        break;
      case UNSATISFIABLE:
        updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_link_has_been_turned_off), R.drawable.ic_update_group_role_16));
        break;
    }

    if (change.getNewInviteLinkPassword().size() > 0) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_link_has_been_reset), R.drawable.ic_update_group_role_16));
    }
  }

  private void describeRequestingMembers(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    Set<ByteString> deleteRequestingUuids = new HashSet<>(change.getDeleteRequestingMembersList());

    for (DecryptedRequestingMember member : change.getNewRequestingMembersList()) {
      boolean requestingMemberIsYou = selfIds.matches(member.getUuid());

      if (requestingMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_sent_a_request_to_join_the_group), R.drawable.ic_update_group_16));
      } else {
        if (deleteRequestingUuids.contains(member.getUuid())) {
          updates.add(updateDescription(R.plurals.MessageRecord_s_requested_and_cancelled_their_request_to_join_via_the_group_link,
                                        change.getDeleteRequestingMembersCount(),
                                        member.getUuid(),
                                        change.getDeleteRequestingMembersCount(),
                                        R.drawable.ic_update_group_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_s_requested_to_join_via_the_group_link, member.getUuid(), R.drawable.ic_update_group_16));
        }
      }
    }
  }

  private void describeRequestingMembersApprovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedApproveMember requestingMember : change.getPromoteRequestingMembersList()) {
      boolean requestingMemberIsYou = selfIds.matches(requestingMember.getUuid());

      if (requestingMemberIsYou) {
        updates.add(updateDescription(R.string.MessageRecord_s_approved_your_request_to_join_the_group, change.getEditor(), R.drawable.ic_update_group_accept_16));
      } else {
        boolean editorIsYou = selfIds.matches(change.getEditor());

        if (editorIsYou) {
          updates.add(updateDescription(R.string.MessageRecord_you_approved_a_request_to_join_the_group_from_s, requestingMember.getUuid(), R.drawable.ic_update_group_accept_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_s_approved_a_request_to_join_the_group_from_s, change.getEditor(), requestingMember.getUuid(), R.drawable.ic_update_group_accept_16));
        }
      }
    }
  }

  private void describeUnknownEditorRequestingMembersApprovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedApproveMember requestingMember : change.getPromoteRequestingMembersList()) {
      boolean requestingMemberIsYou = selfIds.matches(requestingMember.getUuid());

      if (requestingMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_your_request_to_join_the_group_has_been_approved), R.drawable.ic_update_group_accept_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_a_request_to_join_the_group_from_s_has_been_approved, requestingMember.getUuid(), R.drawable.ic_update_group_accept_16));
      }
    }
  }

  private void describeRequestingMembersDeletes(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    Set<ByteString> newRequestingUuids = change.getNewRequestingMembersList().stream().map(DecryptedRequestingMember::getUuid).collect(Collectors.toSet());

    boolean editorIsYou = selfIds.matches(change.getEditor());

    for (ByteString requestingMember : change.getDeleteRequestingMembersList()) {
      if (newRequestingUuids.contains(requestingMember)) {
        continue;
      }

      boolean requestingMemberIsYou = selfIds.matches(requestingMember);

      if (requestingMemberIsYou) {
        if (editorIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_canceled_your_request_to_join_the_group), R.drawable.ic_update_group_decline_16));
        } else {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_your_request_to_join_the_group_has_been_denied_by_an_admin), R.drawable.ic_update_group_decline_16));
        }
      } else {
        boolean editorIsCanceledMember = change.getEditor().equals(requestingMember);

        if (editorIsCanceledMember) {
          updates.add(updateDescription(R.string.MessageRecord_s_canceled_their_request_to_join_the_group, requestingMember, R.drawable.ic_update_group_decline_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_s_denied_a_request_to_join_the_group_from_s, change.getEditor(), requestingMember, R.drawable.ic_update_group_decline_16));
        }
      }
    }
  }

  private void describeUnknownEditorRequestingMembersDeletes(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (ByteString requestingMember : change.getDeleteRequestingMembersList()) {
      boolean requestingMemberIsYou = selfIds.matches(requestingMember);

      if (requestingMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_your_request_to_join_the_group_has_been_denied_by_an_admin), R.drawable.ic_update_group_decline_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_a_request_to_join_the_group_from_s_has_been_denied, requestingMember, R.drawable.ic_update_group_decline_16));
      }
    }
  }

  private void describeAnnouncementGroupChange(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    if (change.getNewIsAnnouncementGroup() == EnabledState.ENABLED) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_allow_only_admins_to_send), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_allow_only_admins_to_send, change.getEditor(), R.drawable.ic_update_group_role_16));
      }
    } else if (change.getNewIsAnnouncementGroup() == EnabledState.DISABLED) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_allow_all_members_to_send), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_allow_all_members_to_send, change.getEditor(), R.drawable.ic_update_group_role_16));
      }
    }
  }

  private void describeUnknownEditorAnnouncementGroupChange(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.getNewIsAnnouncementGroup() == EnabledState.ENABLED) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_allow_only_admins_to_send), R.drawable.ic_update_group_role_16));
    } else if (change.getNewIsAnnouncementGroup() == EnabledState.DISABLED) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_allow_all_members_to_send), R.drawable.ic_update_group_role_16));
    }
  }

  private void describePromotePendingPniAci(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = selfIds.matches(change.getEditor());

    for (DecryptedMember newMember : change.getPromotePendingPniAciMembersList()) {
      ByteString uuid           = newMember.getUuid();
      boolean    newMemberIsYou = selfIds.matches(uuid);

      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_accepted_invite), R.drawable.ic_update_group_accept_16));
        } else {
          updates.add(updateDescription(R.string.MessageRecord_you_added_invited_member_s, uuid, R.drawable.ic_update_group_add_16));
        }
      } else {
        if (newMemberIsYou) {
          updates.add(updateDescription(R.string.MessageRecord_s_added_you, change.getEditor(), R.drawable.ic_update_group_add_16));
        } else {
          if (uuid.equals(change.getEditor())) {
            updates.add(updateDescription(R.string.MessageRecord_s_accepted_invite, uuid, R.drawable.ic_update_group_accept_16));
          } else {
            updates.add(updateDescription(R.string.MessageRecord_s_added_invited_member_s, change.getEditor(), uuid, R.drawable.ic_update_group_add_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorPromotePendingPniAci(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedMember newMember : change.getPromotePendingPniAciMembersList()) {
      ByteString uuid           = newMember.getUuid();
      boolean    newMemberIsYou = selfIds.matches(uuid);

      if (newMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group), R.drawable.ic_update_group_add_16));
      } else {
        updates.add(updateDescription(R.string.MessageRecord_s_joined_the_group, uuid, R.drawable.ic_update_group_add_16));
      }
    }
  }

  private static UpdateDescription updateDescription(@NonNull String string, @DrawableRes int iconResource) {
    return UpdateDescription.staticDescription(string, iconResource);
  }

  private UpdateDescription updateDescription(@StringRes int stringRes,
                                              @NonNull ByteString uuid1Bytes,
                                              @DrawableRes int iconResource)
  {
    ServiceId   serviceId   = ServiceId.fromByteStringOrUnknown(uuid1Bytes);
    RecipientId recipientId = RecipientId.from(serviceId);

    return UpdateDescription.mentioning(
        Collections.singletonList(serviceId),
        () -> {
          List<RecipientId> recipientIdList = Collections.singletonList(recipientId);
          String            templateString  = context.getString(stringRes, makePlaceholders(recipientIdList, null));

          return makeRecipientsClickable(context, templateString, recipientIdList, recipientClickHandler);
        },
        iconResource);
  }

  private UpdateDescription updateDescription(@StringRes int stringRes,
                                              @NonNull ByteString uuid1Bytes,
                                              @NonNull ByteString uuid2Bytes,
                                              @DrawableRes int iconResource)
  {
    ServiceId sid1 = ServiceId.fromByteStringOrUnknown(uuid1Bytes);
    ServiceId sid2 = ServiceId.fromByteStringOrUnknown(uuid2Bytes);

    RecipientId recipientId1 = RecipientId.from(sid1);
    RecipientId recipientId2 = RecipientId.from(sid2);

    return UpdateDescription.mentioning(
        Arrays.asList(sid1, sid2),
        () -> {
          List<RecipientId> recipientIdList = Arrays.asList(recipientId1, recipientId2);
          String            templateString  = context.getString(stringRes, makePlaceholders(recipientIdList, null));

          return makeRecipientsClickable(context, templateString, recipientIdList, recipientClickHandler);
        },
        iconResource
    );
  }

  private UpdateDescription updateDescription(@StringRes int stringRes,
                                              @NonNull ByteString uuid1Bytes,
                                              @NonNull Object formatArg,
                                              @DrawableRes int iconResource)
  {
    ServiceId   serviceId   = ServiceId.fromByteStringOrUnknown(uuid1Bytes);
    RecipientId recipientId = RecipientId.from(serviceId);

    return UpdateDescription.mentioning(
        Collections.singletonList(serviceId),
        () -> {
          List<RecipientId> recipientIdList = Collections.singletonList(recipientId);
          String            templateString  = context.getString(stringRes, makePlaceholders(recipientIdList, Collections.singletonList(formatArg)));

          return makeRecipientsClickable(context, templateString, recipientIdList, recipientClickHandler);
        },
        iconResource
    );
  }

  private UpdateDescription updateDescription(@PluralsRes int stringRes,
                                              int quantity,
                                              @NonNull ByteString uuid1Bytes,
                                              @NonNull Object formatArg,
                                              @DrawableRes int iconResource)
  {
    ServiceId   serviceId   = ServiceId.fromByteStringOrUnknown(uuid1Bytes);
    RecipientId recipientId = RecipientId.from(serviceId);

    return UpdateDescription.mentioning(
        Collections.singletonList(serviceId),
        () -> {
          List<RecipientId> recipientIdList = Collections.singletonList(recipientId);
          String            templateString  = context.getResources().getQuantityString(stringRes, quantity, makePlaceholders(recipientIdList, Collections.singletonList(formatArg)));

          return makeRecipientsClickable(context, templateString, recipientIdList, recipientClickHandler);
        },
        iconResource
    );
  }

  private static @NonNull Object[] makePlaceholders(@NonNull List<RecipientId> recipientIds, @Nullable List<Object> formatArgs) {
    List<Object> args = recipientIds.stream().map(GroupsV2UpdateMessageProducer::makePlaceholder).collect(Collectors.toList());

    if (formatArgs != null) {
      args.addAll(formatArgs);
    }

    return args.toArray();
  }

  @VisibleForTesting
  static @NonNull Spannable makeRecipientsClickable(@NonNull Context context, @NonNull String template, @NonNull List<RecipientId> recipientIds, @Nullable Consumer<RecipientId> clickHandler) {
    SpannableStringBuilder builder    = new SpannableStringBuilder();
    int                    startIndex = 0;

    Map<String, RecipientId> idByPlaceholder = new HashMap<>();
    for (RecipientId id : recipientIds) {
      idByPlaceholder.put(makePlaceholder(id), id);
    }

    while (startIndex < template.length()) {
      Map.Entry<String, RecipientId> nearestEntry    = null;
      int                            nearestPosition = Integer.MAX_VALUE;

      for (Map.Entry<String, RecipientId> entry : idByPlaceholder.entrySet()) {
        String placeholder      = entry.getKey();
        int    placeholderStart = template.indexOf(placeholder, startIndex);

        if (placeholderStart >= 0 && placeholderStart < nearestPosition) {
          nearestPosition = placeholderStart;
          nearestEntry    = entry;
        }
      }

      if (nearestEntry != null) {
        String      placeholder = nearestEntry.getKey();
        RecipientId recipientId = nearestEntry.getValue();

        String beforeChunk = template.substring(startIndex, nearestPosition);

        builder.append(beforeChunk);
        builder.append(SpanUtil.clickable(Recipient.resolved(recipientId).getDisplayName(context), ContextCompat.getColor(context, R.color.conversation_item_update_text_color), v -> {
          if (!recipientId.isUnknown() && clickHandler != null) {
            clickHandler.accept(recipientId);
          }
        }));

        startIndex = nearestPosition + placeholder.length();
      } else {
        builder.append(template.substring(startIndex));
        startIndex = template.length();
      }
    }

    return builder;
  }

  @VisibleForTesting
  static @NonNull String makePlaceholder(@NonNull RecipientId recipientId) {
    return "{{SPAN_PLACEHOLDER_" + recipientId + "}}";
  }
}

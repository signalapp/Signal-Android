package org.thoughtcrime.securesms.database.model;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

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
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.StringUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Arrays;
import java.util.Collections;
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
                                @NonNull UUID selfUuid) {
    this.context             = context;
    this.descriptionStrategy = descriptionStrategy;
    this.selfUuid            = selfUuid;
    this.selfUuidBytes       = UuidUtil.toByteString(selfUuid);
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
    Optional<DecryptedPendingMember> selfPending = DecryptedGroupUtil.findPendingByUuid(group.getPendingMembersList(), selfUuid);
    if (selfPending.isPresent()) {
      return updateDescription(selfPending.get().getAddedByUuid(), inviteBy -> context.getString(R.string.MessageRecord_s_invited_you_to_the_group, inviteBy), R.drawable.ic_update_group_add_16);
    }

    ByteString foundingMemberUuid = decryptedGroupChange.getEditor();
    if (!foundingMemberUuid.isEmpty()) {
      if (selfUuidBytes.equals(foundingMemberUuid)) {
        return updateDescription(context.getString(R.string.MessageRecord_you_created_the_group), R.drawable.ic_update_group_16);
      } else {
        return updateDescription(foundingMemberUuid, creator -> context.getString(R.string.MessageRecord_s_added_you, creator), R.drawable.ic_update_group_add_16);
      }
    }

    if (DecryptedGroupUtil.findMemberByUuid(group.getMembersList(), selfUuid).isPresent()) {
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
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (editorIsYou) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_you_updated_group), R.drawable.ic_update_group_16));
    } else {
      updates.add(updateDescription(change.getEditor(), (editor) -> context.getString(R.string.MessageRecord_s_updated_group, editor), R.drawable.ic_update_group_16));
    }
  }

  private void describeUnknownEditorUnknownChange(@NonNull List<UpdateDescription> updates) {
    updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_was_updated), R.drawable.ic_update_group_16));
  }

  private void describeMemberAdditions(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (DecryptedMember member : change.getNewMembersList()) {
      boolean newMemberIsYou = member.getUuid().equals(selfUuidBytes);

      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(0, updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group_via_the_group_link), R.drawable.ic_update_group_accept_16));
        } else {
          updates.add(updateDescription(member.getUuid(), added -> context.getString(R.string.MessageRecord_you_added_s, added), R.drawable.ic_update_group_add_16));
        }
      } else {
        if (newMemberIsYou) {
          updates.add(0, updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_added_you, editor), R.drawable.ic_update_group_add_16));
        } else {
          if (member.getUuid().equals(change.getEditor())) {
            updates.add(updateDescription(member.getUuid(), newMember -> context.getString(R.string.MessageRecord_s_joined_the_group_via_the_group_link, newMember), R.drawable.ic_update_group_accept_16));
          } else {
            updates.add(updateDescription(change.getEditor(), member.getUuid(), (editor, newMember) -> context.getString(R.string.MessageRecord_s_added_s, editor, newMember), R.drawable.ic_update_group_add_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorMemberAdditions(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedMember member : change.getNewMembersList()) {
      boolean newMemberIsYou = member.getUuid().equals(selfUuidBytes);

      if (newMemberIsYou) {
        updates.add(0, updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group), R.drawable.ic_update_group_add_16));
      } else {
        updates.add(updateDescription(member.getUuid(), newMember -> context.getString(R.string.MessageRecord_s_joined_the_group, newMember), R.drawable.ic_update_group_add_16));
      }
    }
  }

  private void describeMemberRemovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (ByteString member : change.getDeleteMembersList()) {
      boolean removedMemberIsYou = member.equals(selfUuidBytes);

      if (editorIsYou) {
        if (removedMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_left_the_group), R.drawable.ic_update_group_leave_16));
        } else {
          updates.add(updateDescription(member, removedMember -> context.getString(R.string.MessageRecord_you_removed_s, removedMember), R.drawable.ic_update_group_remove_16));
        }
      } else {
        if (removedMemberIsYou) {
          updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_removed_you_from_the_group, editor), R.drawable.ic_update_group_remove_16));
        } else {
          if (member.equals(change.getEditor())) {
            updates.add(updateDescription(member, leavingMember -> context.getString(R.string.MessageRecord_s_left_the_group, leavingMember), R.drawable.ic_update_group_leave_16));
          } else {
            updates.add(updateDescription(change.getEditor(), member, (editor, removedMember) -> context.getString(R.string.MessageRecord_s_removed_s, editor, removedMember), R.drawable.ic_update_group_remove_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorMemberRemovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (ByteString member : change.getDeleteMembersList()) {
      boolean removedMemberIsYou = member.equals(selfUuidBytes);

      if (removedMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_are_no_longer_in_the_group), R.drawable.ic_update_group_leave_16));
      } else {
        updates.add(updateDescription(member, oldMember -> context.getString(R.string.MessageRecord_s_is_no_longer_in_the_group, oldMember), R.drawable.ic_update_group_leave_16));
      }
    }
  }

  private void describeModifyMemberRoles(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (DecryptedModifyMemberRole roleChange : change.getModifyMemberRolesList()) {
      boolean changedMemberIsYou = roleChange.getUuid().equals(selfUuidBytes);
      if (roleChange.getRole() == Member.Role.ADMINISTRATOR) {
        if (editorIsYou) {
          updates.add(updateDescription(roleChange.getUuid(), newAdmin -> context.getString(R.string.MessageRecord_you_made_s_an_admin, newAdmin), R.drawable.ic_update_group_role_16));
        } else {
          if (changedMemberIsYou) {
            updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_made_you_an_admin, editor), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(change.getEditor(), roleChange.getUuid(), (editor, newAdmin) -> context.getString(R.string.MessageRecord_s_made_s_an_admin, editor, newAdmin), R.drawable.ic_update_group_role_16));

          }
        }
      } else {
        if (editorIsYou) {
          updates.add(updateDescription(roleChange.getUuid(), oldAdmin -> context.getString(R.string.MessageRecord_you_revoked_admin_privileges_from_s, oldAdmin), R.drawable.ic_update_group_role_16));
        } else {
          if (changedMemberIsYou) {
            updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_revoked_your_admin_privileges, editor), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(change.getEditor(), roleChange.getUuid(), (editor, oldAdmin) -> context.getString(R.string.MessageRecord_s_revoked_admin_privileges_from_s, editor, oldAdmin), R.drawable.ic_update_group_role_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorModifyMemberRoles(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedModifyMemberRole roleChange : change.getModifyMemberRolesList()) {
      boolean changedMemberIsYou = roleChange.getUuid().equals(selfUuidBytes);

      if (roleChange.getRole() == Member.Role.ADMINISTRATOR) {
        if (changedMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_are_now_an_admin), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(roleChange.getUuid(), newAdmin -> context.getString(R.string.MessageRecord_s_is_now_an_admin, newAdmin), R.drawable.ic_update_group_role_16));
        }
      } else {
        if (changedMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_are_no_longer_an_admin), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(roleChange.getUuid(), oldAdmin -> context.getString(R.string.MessageRecord_s_is_no_longer_an_admin, oldAdmin), R.drawable.ic_update_group_role_16));
        }
      }
    }
  }

  private void describeInvitations(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);
    int notYouInviteCount = 0;

    for (DecryptedPendingMember invitee : change.getNewPendingMembersList()) {
      boolean newMemberIsYou = invitee.getUuid().equals(selfUuidBytes);

      if (newMemberIsYou) {
        updates.add(0, updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_invited_you_to_the_group, editor), R.drawable.ic_update_group_add_16));
      } else {
        if (editorIsYou) {
          updates.add(updateDescription(invitee.getUuid(), newInvitee -> context.getString(R.string.MessageRecord_you_invited_s_to_the_group, newInvitee), R.drawable.ic_update_group_add_16));
        } else {
          notYouInviteCount++;
        }
      }
    }

    if (notYouInviteCount > 0) {
      final int notYouInviteCountFinalCopy = notYouInviteCount;
      updates.add(updateDescription(change.getEditor(), editor -> context.getResources().getQuantityString(R.plurals.MessageRecord_s_invited_members, notYouInviteCountFinalCopy, editor, notYouInviteCountFinalCopy), R.drawable.ic_update_group_add_16));
    }
  }

  private void describeUnknownEditorInvitations(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    int notYouInviteCount = 0;

    for (DecryptedPendingMember invitee : change.getNewPendingMembersList()) {
      boolean newMemberIsYou = invitee.getUuid().equals(selfUuidBytes);

      if (newMemberIsYou) {
        UUID uuid = UuidUtil.fromByteStringOrUnknown(invitee.getAddedByUuid());

        if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
          updates.add(0, updateDescription(context.getString(R.string.MessageRecord_you_were_invited_to_the_group), R.drawable.ic_update_group_add_16));
        } else {
          updates.add(0, updateDescription(invitee.getAddedByUuid(), editor -> context.getString(R.string.MessageRecord_s_invited_you_to_the_group, editor), R.drawable.ic_update_group_add_16));
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
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);
    int notDeclineCount = 0;

    for (DecryptedPendingMemberRemoval invitee : change.getDeletePendingMembersList()) {
      boolean decline = invitee.getUuid().equals(change.getEditor());
      if (decline) {
        if (editorIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_declined_the_invitation_to_the_group), R.drawable.ic_update_group_decline_16));
        } else {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_someone_declined_an_invitation_to_the_group), R.drawable.ic_update_group_decline_16));
        }
      } else if (invitee.getUuid().equals(selfUuidBytes)) {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_revoked_your_invitation_to_the_group, editor), R.drawable.ic_update_group_decline_16));
      } else {
        notDeclineCount++;
      }
    }

    if (notDeclineCount > 0) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getResources().getQuantityString(R.plurals.MessageRecord_you_revoked_invites, notDeclineCount, notDeclineCount), R.drawable.ic_update_group_decline_16));
      } else {
        final int notDeclineCountFinalCopy = notDeclineCount;
        updates.add(updateDescription(change.getEditor(), editor -> context.getResources().getQuantityString(R.plurals.MessageRecord_s_revoked_invites, notDeclineCountFinalCopy, editor, notDeclineCountFinalCopy), R.drawable.ic_update_group_decline_16));
      }
    }
  }

  private void describeUnknownEditorRevokedInvitations(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    int notDeclineCount = 0;

    for (DecryptedPendingMemberRemoval invitee : change.getDeletePendingMembersList()) {
      boolean inviteeWasYou = invitee.getUuid().equals(selfUuidBytes);

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
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (DecryptedMember newMember : change.getPromotePendingMembersList()) {
      ByteString uuid = newMember.getUuid();
      boolean newMemberIsYou = uuid.equals(selfUuidBytes);

      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_accepted_invite), R.drawable.ic_update_group_accept_16));
        } else {
          updates.add(updateDescription(uuid, newPromotedMember -> context.getString(R.string.MessageRecord_you_added_invited_member_s, newPromotedMember), R.drawable.ic_update_group_add_16));
        }
      } else {
        if (newMemberIsYou) {
          updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_added_you, editor), R.drawable.ic_update_group_add_16));
        } else {
          if (uuid.equals(change.getEditor())) {
            updates.add(updateDescription(uuid, newAcceptedMember -> context.getString(R.string.MessageRecord_s_accepted_invite, newAcceptedMember), R.drawable.ic_update_group_accept_16));
          } else {
            updates.add(updateDescription(change.getEditor(), uuid, (editor, newAcceptedMember) -> context.getString(R.string.MessageRecord_s_added_invited_member_s, editor, newAcceptedMember), R.drawable.ic_update_group_add_16));
          }
        }
      }
    }
  }

  private void describeUnknownEditorPromotePending(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedMember newMember : change.getPromotePendingMembersList()) {
      ByteString uuid = newMember.getUuid();
      boolean newMemberIsYou = uuid.equals(selfUuidBytes);

      if (newMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_joined_the_group), R.drawable.ic_update_group_add_16));
      } else {
        updates.add(updateDescription(uuid, newMemberName -> context.getString(R.string.MessageRecord_s_joined_the_group, newMemberName), R.drawable.ic_update_group_add_16));
      }
    }
  }

  private void describeNewTitle(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.hasNewTitle()) {
      String newTitle = StringUtil.isolateBidi(change.getNewTitle().getValue());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_the_group_name_to_s, newTitle), R.drawable.ic_update_group_name_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_changed_the_group_name_to_s, editor, newTitle), R.drawable.ic_update_group_name_16));
      }
    }
  }

  private void describeNewDescription(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.hasNewDescription()) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_the_group_description), R.drawable.ic_update_group_name_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_changed_the_group_description, editor), R.drawable.ic_update_group_name_16));
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
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.hasNewAvatar()) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_the_group_avatar), R.drawable.ic_update_group_avatar_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_changed_the_group_avatar, editor), R.drawable.ic_update_group_avatar_16));
      }
    }
  }

  private void describeUnknownEditorNewAvatar(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    if (change.hasNewAvatar()) {
      updates.add(updateDescription(context.getString(R.string.MessageRecord_the_group_group_avatar_has_been_changed), R.drawable.ic_update_group_avatar_16));
    }
  }

  void describeNewTimer(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.hasNewTimer()) {
      String time = ExpirationUtil.getExpirationDisplayValue(context, change.getNewTimer().getDuration());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time), R.drawable.ic_update_timer_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, editor, time), R.drawable.ic_update_timer_16));
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
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.getNewAttributeAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewAttributeAccess());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_who_can_edit_group_info_to_s, accessLevel), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_changed_who_can_edit_group_info_to_s, editor, accessLevel), R.drawable.ic_update_group_role_16));
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
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.getNewMemberAccess() != AccessControl.AccessRequired.UNKNOWN) {
      String accessLevel = GV2AccessLevelUtil.toString(context, change.getNewMemberAccess());
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_changed_who_can_edit_group_membership_to_s, accessLevel), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_changed_who_can_edit_group_membership_to_s, editor, accessLevel), R.drawable.ic_update_group_role_16));
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

    boolean editorIsYou      = change.getEditor().equals(selfUuidBytes);
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
            updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_turned_off_admin_approval_for_the_group_link, editor), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_turned_on_the_group_link_with_admin_approval_off, editor), R.drawable.ic_update_group_role_16));
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
            updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_turned_on_admin_approval_for_the_group_link, editor), R.drawable.ic_update_group_role_16));
          } else {
            updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_turned_on_the_group_link_with_admin_approval_on, editor), R.drawable.ic_update_group_role_16));
          }
        }
        break;
      case UNSATISFIABLE:
        if (editorIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_turned_off_the_group_link), R.drawable.ic_update_group_role_16));
        } else {
          updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_turned_off_the_group_link, editor), R.drawable.ic_update_group_role_16));
        }
        break;
    }

    if (!groupLinkEnabled && change.getNewInviteLinkPassword().size() > 0) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_reset_the_group_link), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_reset_the_group_link, editor), R.drawable.ic_update_group_role_16));
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
    for (DecryptedRequestingMember member : change.getNewRequestingMembersList()) {
      boolean requestingMemberIsYou = member.getUuid().equals(selfUuidBytes);

      if (requestingMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_sent_a_request_to_join_the_group), R.drawable.ic_update_group_16));
      } else {
        updates.add(updateDescription(member.getUuid(), requesting -> context.getString(R.string.MessageRecord_s_requested_to_join_via_the_group_link, requesting), R.drawable.ic_update_group_16));
      }
    }
  }

  private void describeRequestingMembersApprovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedApproveMember requestingMember : change.getPromoteRequestingMembersList()) {
      boolean requestingMemberIsYou = requestingMember.getUuid().equals(selfUuidBytes);

      if (requestingMemberIsYou) {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_approved_your_request_to_join_the_group, editor), R.drawable.ic_update_group_accept_16));
      } else {
      boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

        if (editorIsYou) {
          updates.add(updateDescription(requestingMember.getUuid(), requesting -> context.getString(R.string.MessageRecord_you_approved_a_request_to_join_the_group_from_s, requesting), R.drawable.ic_update_group_accept_16));
        } else {
          updates.add(updateDescription(change.getEditor(), requestingMember.getUuid(), (editor, requesting) -> context.getString(R.string.MessageRecord_s_approved_a_request_to_join_the_group_from_s, editor, requesting), R.drawable.ic_update_group_accept_16));
        }
      }
    }
  }

  private void describeUnknownEditorRequestingMembersApprovals(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (DecryptedApproveMember requestingMember : change.getPromoteRequestingMembersList()) {
      boolean requestingMemberIsYou = requestingMember.getUuid().equals(selfUuidBytes);

      if (requestingMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_your_request_to_join_the_group_has_been_approved), R.drawable.ic_update_group_accept_16));
      } else {
        updates.add(updateDescription(requestingMember.getUuid(), requesting -> context.getString(R.string.MessageRecord_a_request_to_join_the_group_from_s_has_been_approved, requesting), R.drawable.ic_update_group_accept_16));
      }
    }
  }

  private void describeRequestingMembersDeletes(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    for (ByteString requestingMember : change.getDeleteRequestingMembersList()) {
      boolean requestingMemberIsYou = requestingMember.equals(selfUuidBytes);

      if (requestingMemberIsYou) {
        if (editorIsYou) {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_you_canceled_your_request_to_join_the_group), R.drawable.ic_update_group_decline_16));
        } else {
          updates.add(updateDescription(context.getString(R.string.MessageRecord_your_request_to_join_the_group_has_been_denied_by_an_admin), R.drawable.ic_update_group_decline_16));
        }
      } else {
        boolean editorIsCanceledMember = change.getEditor().equals(requestingMember);

        if (editorIsCanceledMember) {
          updates.add(updateDescription(requestingMember, editorRequesting -> context.getString(R.string.MessageRecord_s_canceled_their_request_to_join_the_group, editorRequesting), R.drawable.ic_update_group_decline_16));
        } else {
          updates.add(updateDescription(change.getEditor(), requestingMember, (editor, requesting) -> context.getString(R.string.MessageRecord_s_denied_a_request_to_join_the_group_from_s, editor, requesting), R.drawable.ic_update_group_decline_16));
        }
      }
    }
  }

  private void describeUnknownEditorRequestingMembersDeletes(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    for (ByteString requestingMember : change.getDeleteRequestingMembersList()) {
      boolean requestingMemberIsYou = requestingMember.equals(selfUuidBytes);

      if (requestingMemberIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_your_request_to_join_the_group_has_been_denied_by_an_admin), R.drawable.ic_update_group_decline_16));
      } else {
        updates.add(updateDescription(requestingMember, requesting -> context.getString(R.string.MessageRecord_a_request_to_join_the_group_from_s_has_been_denied, requesting), R.drawable.ic_update_group_decline_16));
      }
    }
  }

  private void describeAnnouncementGroupChange(@NonNull DecryptedGroupChange change, @NonNull List<UpdateDescription> updates) {
    boolean editorIsYou = change.getEditor().equals(selfUuidBytes);

    if (change.getNewIsAnnouncementGroup() == EnabledState.ENABLED) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_allow_only_admins_to_send), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_allow_only_admins_to_send, editor), R.drawable.ic_update_group_role_16));
      }
    } else if (change.getNewIsAnnouncementGroup() == EnabledState.DISABLED) {
      if (editorIsYou) {
        updates.add(updateDescription(context.getString(R.string.MessageRecord_you_allow_all_members_to_send), R.drawable.ic_update_group_role_16));
      } else {
        updates.add(updateDescription(change.getEditor(), editor -> context.getString(R.string.MessageRecord_s_allow_all_members_to_send, editor), R.drawable.ic_update_group_role_16));
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

  interface DescribeMemberStrategy {

    /**
     * Map an ACI to a string that describes the group member.
     * @param serviceId
     */
    @NonNull
    @WorkerThread
    String describe(@NonNull ServiceId serviceId);
  }

  private interface StringFactory1Arg {
    String create(String arg1);
  }

  private interface StringFactory2Args {
    String create(String arg1, String arg2);
  }

  private static UpdateDescription updateDescription(@NonNull String string,
                                                     @DrawableRes int iconResource)
  {
    return UpdateDescription.staticDescription(string, iconResource);
  }

  private UpdateDescription updateDescription(@NonNull ByteString uuid1Bytes,
                                              @NonNull StringFactory1Arg stringFactory,
                                              @DrawableRes int iconResource)
  {
    ServiceId serviceId = ServiceId.fromByteStringOrUnknown(uuid1Bytes);

    return UpdateDescription.mentioning(Collections.singletonList(serviceId), () -> stringFactory.create(descriptionStrategy.describe(serviceId)), iconResource);
  }

  private UpdateDescription updateDescription(@NonNull ByteString uuid1Bytes,
                                              @NonNull ByteString uuid2Bytes,
                                              @NonNull StringFactory2Args stringFactory,
                                              @DrawableRes int iconResource)
  {
    ServiceId sid1 = ServiceId.fromByteStringOrUnknown(uuid1Bytes);
    ServiceId sid2 = ServiceId.fromByteStringOrUnknown(uuid2Bytes);

    return UpdateDescription.mentioning(Arrays.asList(sid1, sid2), () -> stringFactory.create(descriptionStrategy.describe(sid1), descriptionStrategy.describe(sid2)), iconResource);
  }
}
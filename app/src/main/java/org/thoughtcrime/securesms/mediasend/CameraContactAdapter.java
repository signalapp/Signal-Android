package org.thoughtcrime.securesms.mediasend;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.adapter.SectionedRecyclerViewAdapter;
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class CameraContactAdapter extends SectionedRecyclerViewAdapter<String, CameraContactAdapter.ContactSection> {

  private static final int  TYPE_INVITE = 1337;
  private static final long ID_INVITE   = Long.MAX_VALUE;

  private static final String TAG_RECENT = "recent";
  private static final String TAG_ALL    = "all";
  private static final String TAG_GROUPS = "groups";

  private final RequestManager        requestManager;
  private final Set<Recipient>        selected;
  private final CameraContactListener cameraContactListener;


  private final List<ContactSection> sections = new ArrayList<ContactSection>(3) {{
    ContactSection recentContacts = new ContactSection(TAG_RECENT, R.string.CameraContacts_recent_contacts, Collections.emptyList(), 0);
    ContactSection allContacts    = new ContactSection(TAG_ALL, R.string.CameraContacts_signal_contacts, Collections.emptyList(), recentContacts.size());
    ContactSection groups         = new ContactSection(TAG_GROUPS, R.string.CameraContacts_signal_groups, Collections.emptyList(), recentContacts.size() + allContacts.size());

    add(recentContacts);
    add(allContacts);
    add(groups);
  }};

  CameraContactAdapter(@NonNull RequestManager requestManager, @NonNull CameraContactListener listener) {
    this.requestManager        = requestManager;
    this.selected              = new HashSet<>();
    this.cameraContactListener = listener;
  }

  @Override
  protected @NonNull List<ContactSection> getSections() {
    return sections;
  }

  @Override
  public long getItemId(int globalPosition) {
    if (isInvitePosition(globalPosition)) {
      return ID_INVITE;
    } else {
      return super.getItemId(globalPosition);
    }
  }

  @Override
  public int getItemViewType(int globalPosition) {
    if (isInvitePosition(globalPosition)) {
      return TYPE_INVITE;
    } else {
      return super.getItemViewType(globalPosition);
    }
  }

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
    if (viewType == TYPE_INVITE) {
      return new InviteViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.camera_contact_invite_item, viewGroup, false));
    } else {
      return super.onCreateViewHolder(viewGroup, viewType);
    }
  }

  @Override
  protected @NonNull RecyclerView.ViewHolder createHeaderViewHolder(@NonNull ViewGroup parent) {
    return new HeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.camera_contact_header_item, parent, false));
  }

  @Override
  protected @NonNull RecyclerView.ViewHolder createContentViewHolder(@NonNull ViewGroup parent) {
    return new ContactViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.camera_contact_contact_item, parent, false));
  }

  @Override
  protected @Nullable RecyclerView.ViewHolder createEmptyViewHolder(@NonNull ViewGroup viewGroup) {
    return null;
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int globalPosition) {
    if (isInvitePosition(globalPosition)) {
      ((InviteViewHolder) holder).bind(cameraContactListener);
    } else {
      super.onBindViewHolder(holder, globalPosition);
    }
  }

  @Override
  protected void bindViewHolder(@NonNull RecyclerView.ViewHolder holder, @NonNull ContactSection section, int localPosition) {
    section.bind(holder, localPosition, selected, requestManager, cameraContactListener);
  }

  @Override
  public int getItemCount() {
    return super.getItemCount() + 1;
  }

  public void setContacts(@NonNull CameraContacts contacts, @NonNull Collection<Recipient> selected) {
    ContactSection recentContacts = new ContactSection(TAG_RECENT, R.string.CameraContacts_recent_contacts, contacts.getRecents(), 0);
    ContactSection allContacts    = new ContactSection(TAG_ALL, R.string.CameraContacts_signal_contacts, contacts.getContacts(), recentContacts.size());
    ContactSection groups         = new ContactSection(TAG_GROUPS, R.string.CameraContacts_signal_groups, contacts.getGroups(), recentContacts.size() + allContacts.size());

    sections.clear();
    sections.add(recentContacts);
    sections.add(allContacts);
    sections.add(groups);

    this.selected.clear();
    this.selected.addAll(selected);

    notifyDataSetChanged();
  }

  private boolean isInvitePosition(int globalPosition) {
    return globalPosition == getItemCount() - 1;
  }

  public static class ContactSection extends SectionedRecyclerViewAdapter.Section<String> {

    private final String          tag;
    private final int             titleResId;
    private final List<Recipient> recipients;

    public ContactSection(@NonNull String tag, @StringRes int titleResId, @NonNull List<Recipient> recipients, int offset) {
      super(offset);
      this.tag        = tag;
      this.titleResId = titleResId;
      this.recipients = recipients;
    }

    @Override
    public boolean hasEmptyState() {
      return false;
    }

    @Override
    public int getContentSize() {
      return recipients.size();
    }

    @Override
    public long getItemId(@NonNull StableIdGenerator<String> idGenerator, int globalPosition) {
      int localPosition = getLocalPosition(globalPosition);

      if (localPosition == 0) {
        return idGenerator.getId(tag);
      } else {
        return idGenerator.getId(recipients.get(localPosition - 1).getId().serialize());
      }
    }

    void bind(@NonNull RecyclerView.ViewHolder viewHolder,
              int localPosition,
              @NonNull Set<Recipient> selected,
              @NonNull RequestManager requestManager,
              @NonNull CameraContactListener cameraContactListener)
    {
      if (localPosition == 0) {
        ((HeaderViewHolder) viewHolder).bind(titleResId);
      } else {
        Recipient recipient = recipients.get(localPosition - 1);
        ((ContactViewHolder) viewHolder).bind(recipient, selected.contains(recipient), requestManager, cameraContactListener);
      }
    }
  }

  private static class HeaderViewHolder extends RecyclerView.ViewHolder {

    private final TextView title;

    HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
      this.title = itemView.findViewById(R.id.camera_contact_header);
    }

    void bind(@StringRes int titleResId) {
      this.title.setText(titleResId);
    }
  }

  private static class ContactViewHolder extends RecyclerView.ViewHolder {

    private final AvatarImageView avatar;
    private final FromTextView    name;
    private final CheckBox        checkbox;

    ContactViewHolder(@NonNull View itemView) {
      super(itemView);

      this.avatar   = itemView.findViewById(R.id.camera_contact_item_avatar);
      this.name     = itemView.findViewById(R.id.camera_contact_item_name);
      this.checkbox = itemView.findViewById(R.id.camera_contact_item_checkbox);
    }

    void bind(@NonNull Recipient recipient,
              boolean selected,
              @NonNull RequestManager requestManager,
              @NonNull CameraContactListener listener)
    {
      avatar.setAvatar(requestManager, recipient, false);
      name.setText(recipient);
      itemView.setOnClickListener(v -> listener.onContactClicked(recipient));
      checkbox.setChecked(selected);
    }
  }

  private static class InviteViewHolder extends RecyclerView.ViewHolder {

    private final View inviteButton;

    public InviteViewHolder(@NonNull View itemView) {
      super(itemView);
      inviteButton = itemView.findViewById(R.id.camera_contact_invite);
    }

    void bind(@NonNull CameraContactListener listener) {
      inviteButton.setOnClickListener(v -> listener.onInviteContactsClicked());
    }
  }

  interface CameraContactListener {
    void onContactClicked(@NonNull Recipient recipient);
    void onInviteContactsClicked();
  }
}

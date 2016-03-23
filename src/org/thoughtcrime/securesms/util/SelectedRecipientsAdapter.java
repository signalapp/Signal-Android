package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SelectedRecipientsAdapter extends BaseAdapter {
  @NonNull  private Context                    context;
  @Nullable private OnRecipientDeletedListener onRecipientDeletedListener;
  @NonNull  private List<RecipientWrapper>     recipients;

  public SelectedRecipientsAdapter(@NonNull Context context) {
    this(context, Collections.<Recipient>emptyList());
  }

  public SelectedRecipientsAdapter(@NonNull Context context,
                                   @NonNull Collection<Recipient> existingRecipients)
  {
    this.context    = context;
    this.recipients = wrapExistingMembers(existingRecipients);
  }

  public void add(@NonNull Recipient recipient, boolean isPush) {
    if (!find(recipient).isPresent()) {
      RecipientWrapper wrapper = new RecipientWrapper(recipient, true, isPush);
      this.recipients.add(0, wrapper);
      notifyDataSetChanged();
    }
  }

  public Optional<RecipientWrapper> find(@NonNull Recipient recipient) {
    RecipientWrapper found = null;
    for (RecipientWrapper wrapper : recipients) {
      if (wrapper.getRecipient().equals(recipient)) found = wrapper;
    }
    return Optional.fromNullable(found);
  }

  public void remove(@NonNull Recipient recipient) {
    Optional<RecipientWrapper> match = find(recipient);
    if (match.isPresent()) {
      recipients.remove(match.get());
      notifyDataSetChanged();
    }
  }

  public Set<Recipient> getRecipients() {
    final Set<Recipient> recipientSet = new HashSet<>(recipients.size());
    for (RecipientWrapper wrapper : recipients) {
      recipientSet.add(wrapper.getRecipient());
    }
    return recipientSet;
  }

  @Override
  public int getCount() {
    return recipients.size();
  }

  public boolean hasNonPushMembers() {
    for (RecipientWrapper wrapper : recipients) {
      if (!wrapper.isPush()) return true;
    }
    return false;
  }

  @Override
  public Object getItem(int position) {
    return recipients.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(final int position, View v, final ViewGroup parent) {
    if (v == null) {
      v = LayoutInflater.from(context).inflate(R.layout.selected_recipient_list_item, parent, false);
    }

    final RecipientWrapper rw         = (RecipientWrapper)getItem(position);
    final Recipient        p          = rw.getRecipient();
    final boolean          modifiable = rw.isModifiable();

    TextView    name   = (TextView)    v.findViewById(R.id.name);
    TextView    phone  = (TextView)    v.findViewById(R.id.phone);
    ImageButton delete = (ImageButton) v.findViewById(R.id.delete);

    name.setText(p.getName());
    phone.setText(p.getNumber());
    delete.setVisibility(modifiable ? View.VISIBLE : View.GONE);
    delete.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (onRecipientDeletedListener != null) {
          onRecipientDeletedListener.onRecipientDeleted(recipients.get(position).getRecipient());
        }
      }
    });

    return v;
  }

  private static List<RecipientWrapper> wrapExistingMembers(Collection<Recipient> recipients) {
    final LinkedList<RecipientWrapper> wrapperList = new LinkedList<>();
    for (Recipient recipient : recipients) {
      wrapperList.add(new RecipientWrapper(recipient, false, true));
    }
    return wrapperList;
  }

  public void setOnRecipientDeletedListener(@Nullable OnRecipientDeletedListener listener) {
    onRecipientDeletedListener = listener;
  }

  public interface OnRecipientDeletedListener {
    void onRecipientDeleted(Recipient recipient);
  }

  public static class RecipientWrapper {
    private final Recipient recipient;
    private final boolean   modifiable;
    private final boolean   push;

    public RecipientWrapper(final @NonNull Recipient recipient,
                            final boolean modifiable,
                            final boolean push)
    {
      this.recipient  = recipient;
      this.modifiable = modifiable;
      this.push       = push;
    }

    public @NonNull Recipient getRecipient() {
      return recipient;
    }

    public boolean isModifiable() {
      return modifiable;
    }

    public boolean isPush() {
      return push;
    }
  }
}
package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContactShareEditAdapter extends RecyclerView.Adapter<ContactShareEditAdapter.ContactEditViewHolder> {

  private final Locale        locale;
  private final GlideRequests glideRequests;
  private final List<Contact> contacts;

  ContactShareEditAdapter(@NonNull GlideRequests glideRequests, @NonNull Locale locale) {
    this.locale        = locale;
    this.glideRequests = glideRequests;
    this.contacts      = new ArrayList<>();
  }

  @Override
  public ContactEditViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    return new ContactEditViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_editable_contact, parent, false), locale);
  }

  @Override
  public void onBindViewHolder(ContactEditViewHolder holder, int position) {
    holder.bind(contacts.get(position), glideRequests);
  }

  @Override
  public int getItemCount() {
    return contacts.size();
  }

  void setContacts(@Nullable List<Contact> contacts) {
    this.contacts.clear();

    if (contacts != null) {
      this.contacts.addAll(contacts);
    }

    notifyDataSetChanged();
  }

  static class ContactEditViewHolder extends RecyclerView.ViewHolder {

    private final AvatarImageView     avatar;
    private final TextView            name;
    private final ContactFieldAdapter fieldAdapter;

    ContactEditViewHolder(View itemView, @NonNull Locale locale) {
      super(itemView);

      this.avatar       = itemView.findViewById(R.id.editable_contact_avatar);
      this.name         = itemView.findViewById(R.id.editable_contact_name);
      this.fieldAdapter = new ContactFieldAdapter(locale, true);

      RecyclerView fields = itemView.findViewById(R.id.editable_contact_fields);
      fields.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
      fields.getLayoutManager().setAutoMeasureEnabled(true);
      fields.setAdapter(fieldAdapter);
    }

    void bind(@NonNull Contact contact, @NonNull GlideRequests glideRequests) {
      Context context = itemView.getContext();

      if (contact.getAvatarAttachment() != null && contact.getAvatarAttachment().getDataUri() != null) {
        glideRequests.load(contact.getAvatarAttachment().getDataUri())
                     .fallback(R.drawable.ic_contact_picture)
                     .circleCrop()
                     .diskCacheStrategy(DiskCacheStrategy.ALL)
                     .into(avatar);
      } else {
        glideRequests.load(R.drawable.ic_contact_picture)
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(avatar);
      }

      name.setText(ContactUtil.getDisplayName(contact));
      fieldAdapter.setFields(context,contact.getPhoneNumbers(), contact.getEmails(), contact.getPostalAddresses());
    }
  }
}

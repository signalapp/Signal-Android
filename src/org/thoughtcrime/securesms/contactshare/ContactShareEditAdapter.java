package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.thoughtcrime.securesms.contactshare.Contact.*;

public class ContactShareEditAdapter extends RecyclerView.Adapter<ContactShareEditAdapter.ContactEditViewHolder> {

  private final GlideRequests glideRequests;
  private final Locale        locale;
  private final EventListener eventListener;
  private final List<Contact> contacts;

  ContactShareEditAdapter(@NonNull GlideRequests glideRequests, @NonNull Locale locale, @NonNull EventListener eventListener) {
    this.glideRequests = glideRequests;
    this.locale        = locale;
    this.eventListener = eventListener;
    this.contacts      = new ArrayList<>();
  }

  @Override
  public @NonNull ContactEditViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ContactEditViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_editable_contact, parent, false),
                                     locale,
                                     glideRequests);
  }

  @Override
  public void onBindViewHolder(@NonNull ContactEditViewHolder holder, int position) {
    holder.bind(position, contacts.get(position), eventListener);
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

    private final TextView            name;
    private final View                nameEditButton;
    private final ContactFieldAdapter fieldAdapter;

    ContactEditViewHolder(View itemView, @NonNull Locale locale, @NonNull GlideRequests glideRequests) {
      super(itemView);

      this.name           = itemView.findViewById(R.id.editable_contact_name);
      this.nameEditButton = itemView.findViewById(R.id.editable_contact_name_edit_button);
      this.fieldAdapter   = new ContactFieldAdapter(locale, glideRequests, true);

      RecyclerView fields = itemView.findViewById(R.id.editable_contact_fields);
      fields.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
      fields.getLayoutManager().setAutoMeasureEnabled(true);
      fields.setAdapter(fieldAdapter);
    }

    void bind(int position, @NonNull Contact contact, @NonNull EventListener eventListener) {
      Context context = itemView.getContext();

      name.setText(ContactUtil.getDisplayName(contact));
      nameEditButton.setOnClickListener(v -> eventListener.onNameEditClicked(position, contact.getName()));
      fieldAdapter.setFields(context, contact.getAvatar(), contact.getPhoneNumbers(), contact.getEmails(), contact.getPostalAddresses());
    }
  }

  interface EventListener {
    void onNameEditClicked(int position, @NonNull Name name);
  }
}

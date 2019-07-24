package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.thoughtcrime.securesms.contactshare.Contact.*;

class ContactFieldAdapter extends RecyclerView.Adapter<ContactFieldAdapter.ContactFieldViewHolder> {

  private final Locale        locale;
  private final boolean       selectable;
  private final List<Field>   fields;
  private final GlideRequests glideRequests;

  public ContactFieldAdapter(@NonNull Locale locale, @NonNull GlideRequests glideRequests, boolean selectable) {
    this.locale        = locale;
    this.glideRequests = glideRequests;
    this.selectable    = selectable;
    this.fields        = new ArrayList<>();
  }

  @Override
  public @NonNull ContactFieldViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ContactFieldViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selectable_contact_field, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ContactFieldViewHolder holder, int position) {
    holder.bind(fields.get(position), glideRequests, selectable);
  }

  @Override
  public void onViewRecycled(@NonNull ContactFieldViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return fields.size();
  }

  void setFields(@NonNull  Context             context,
                 @Nullable Avatar              avatar,
                 @NonNull  List<Phone>         phoneNumbers,
                 @NonNull  List<Email>         emails,
                 @NonNull  List<PostalAddress> postalAddresses)
  {
    fields.clear();

    if (avatar != null) {
      fields.add(new Field(avatar));
    }

    fields.addAll(Stream.of(phoneNumbers).map(phone -> new Field(context, phone, locale)).toList());
    fields.addAll(Stream.of(emails).map(email -> new Field(context, email)).toList());
    fields.addAll(Stream.of(postalAddresses).map(address -> new Field(context, address)).toList());

    notifyDataSetChanged();
  }

  static class ContactFieldViewHolder extends RecyclerView.ViewHolder {

    private final TextView  value;
    private final TextView  label;
    private final ImageView icon;
    private final ImageView avatar;
    private final CheckBox  checkBox;

    ContactFieldViewHolder(View itemView) {
      super(itemView);

      value    = itemView.findViewById(R.id.contact_field_value);
      label    = itemView.findViewById(R.id.contact_field_label);
      icon     = itemView.findViewById(R.id.contact_field_icon);
      avatar   = itemView.findViewById(R.id.contact_field_avatar);
      checkBox = itemView.findViewById(R.id.contact_field_checkbox);
    }

    void bind(@NonNull Field field, @NonNull GlideRequests glideRequests, boolean selectable) {
      value.setMaxLines(field.maxLines);
      value.setText(field.value);
      label.setText(field.label);
      icon.setImageResource(field.iconResId);

      if (field.iconUri != null) {
        avatar.setVisibility(View.VISIBLE);
        glideRequests.load(field.iconUri)
                     .diskCacheStrategy(DiskCacheStrategy.NONE)
                     .skipMemoryCache(true)
                     .circleCrop()
                     .into(avatar);
      } else {
        avatar.setVisibility(View.GONE);
      }

      if (selectable) {
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setOnCheckedChangeListener(null);
        checkBox.setChecked(field.isSelected());
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> field.setSelected(isChecked));
      } else {
        checkBox.setVisibility(View.GONE);
        checkBox.setOnCheckedChangeListener(null);
      }
    }

    void recycle() {
      checkBox.setOnCheckedChangeListener(null);
    }
  }

  static class Field {

    final String     value;
    final String     label;
    final int        iconResId;
    final int        maxLines;
    final Selectable selectable;

    @Nullable
    final Uri        iconUri;

    Field(@NonNull Context context, @NonNull Phone phoneNumber, @NonNull Locale locale) {
      this.value      = ContactUtil.getPrettyPhoneNumber(phoneNumber, locale);
      this.iconResId  = R.drawable.ic_call_white_24dp;
      this.iconUri    = null;
      this.maxLines   = 1;
      this.selectable = phoneNumber;

      switch (phoneNumber.getType()) {
        case HOME:
          label = context.getString(R.string.ContactShareEditActivity_type_home);
          break;
        case MOBILE:
          label = context.getString(R.string.ContactShareEditActivity_type_mobile);
          break;
        case WORK:
          label = context.getString(R.string.ContactShareEditActivity_type_work);
          break;
        case CUSTOM:
          label = phoneNumber.getLabel() != null ? phoneNumber.getLabel() : "";
          break;
        default:
          label = "";
      }
    }

    Field(@NonNull Context context, @NonNull Email email) {
      this.value      = email.getEmail();
      this.iconResId  = R.drawable.baseline_email_white_24;
      this.iconUri    = null;
      this.maxLines   = 1;
      this.selectable = email;

      switch (email.getType()) {
        case HOME:
          label = context.getString(R.string.ContactShareEditActivity_type_home);
          break;
        case MOBILE:
          label = context.getString(R.string.ContactShareEditActivity_type_mobile);
          break;
        case WORK:
          label = context.getString(R.string.ContactShareEditActivity_type_work);
          break;
        case CUSTOM:
          label = email.getLabel() != null ? email.getLabel() : "";
          break;
        default:
          label = "";
      }
    }

    Field(@NonNull Context context, @NonNull PostalAddress postalAddress) {
      this.value      = postalAddress.toString();
      this.iconResId  = R.drawable.ic_location_on_white_24dp;
      this.iconUri    = null;
      this.maxLines   = 3;
      this.selectable = postalAddress;

      switch (postalAddress.getType()) {
        case HOME:
          label = context.getString(R.string.ContactShareEditActivity_type_home);
          break;
        case WORK:
          label = context.getString(R.string.ContactShareEditActivity_type_work);
          break;
        case CUSTOM:
          label = postalAddress.getLabel() != null ? postalAddress.getLabel() : context.getString(R.string.ContactShareEditActivity_type_missing);
          break;
        default:
          label = context.getString(R.string.ContactShareEditActivity_type_missing);
      }
    }

    Field(@NonNull Avatar avatar) {
      this.value      = "";
      this.iconResId  = R.drawable.baseline_account_circle_white_24;
      this.iconUri    = avatar.getAttachment() != null ? avatar.getAttachment().getDataUri() : null;
      this.maxLines   = 1;
      this.selectable = avatar;
      this.label      = "";
    }

    void setSelected(boolean selected) {
      selectable.setSelected(selected);
    }

    boolean isSelected() {
      return selectable.isSelected();
    }
  }
}

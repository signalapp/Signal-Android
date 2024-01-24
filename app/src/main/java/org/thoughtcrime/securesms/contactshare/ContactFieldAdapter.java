package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.thoughtcrime.securesms.contactshare.Contact.Avatar;
import static org.thoughtcrime.securesms.contactshare.Contact.Email;
import static org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;

class ContactFieldAdapter extends RecyclerView.Adapter<ContactFieldAdapter.ContactFieldViewHolder> {

  private final Locale         locale;
  private final boolean        selectable;
  private final List<Field>    fields;
  private final RequestManager requestManager;

  public ContactFieldAdapter(@NonNull Locale locale, @NonNull RequestManager requestManager, boolean selectable) {
    this.locale         = locale;
    this.requestManager = requestManager;
    this.selectable     = selectable;
    this.fields         = new ArrayList<>();
  }

  @Override
  public @NonNull ContactFieldViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ContactFieldViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selectable_contact_field, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ContactFieldViewHolder holder, int position) {
    holder.bind(fields.get(position), requestManager, selectable);
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
    private final ImageView avatar;
    private final CheckBox  checkBox;

    ContactFieldViewHolder(View itemView) {
      super(itemView);

      value    = itemView.findViewById(R.id.contact_field_value);
      label    = itemView.findViewById(R.id.contact_field_label);
      avatar   = itemView.findViewById(R.id.contact_field_avatar);
      checkBox = itemView.findViewById(R.id.contact_field_checkbox);
    }

    void bind(@NonNull Field field, @NonNull RequestManager requestManager, boolean selectable) {
      value.setMaxLines(field.maxLines);
      value.setText(field.value);
      label.setText(field.label);
      label.setVisibility(TextUtils.isEmpty(field.label) ? View.GONE : View.VISIBLE);

      if (field.iconUri != null) {
        avatar.setVisibility(View.VISIBLE);
        requestManager.load(field.iconUri)
                     .diskCacheStrategy(DiskCacheStrategy.NONE)
                     .skipMemoryCache(true)
                     .circleCrop()
                     .into(avatar);
      } else {
        avatar.setVisibility(View.GONE);
      }

      if (selectable) {
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setChecked(field.isSelected());
        itemView.setOnClickListener(unused -> {
          field.setSelected(!field.isSelected());
          checkBox.setChecked(field.isSelected());
        });
      } else {
        checkBox.setVisibility(View.GONE);
        itemView.setOnClickListener(null);
        itemView.setClickable(false);
      }
    }

    void recycle() {
      itemView.setOnClickListener(null);
      itemView.setClickable(false);
    }
  }

  static class Field {

    final String     value;
    final String     label;
    final int        maxLines;
    final Selectable selectable;

    @Nullable
    final Uri        iconUri;

    Field(@NonNull Context context, @NonNull Phone phoneNumber, @NonNull Locale locale) {
      this.value      = ContactUtil.getPrettyPhoneNumber(phoneNumber, locale);
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
      this.iconUri    = avatar.getAttachment() != null ? avatar.getAttachment().getUri() : null;
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

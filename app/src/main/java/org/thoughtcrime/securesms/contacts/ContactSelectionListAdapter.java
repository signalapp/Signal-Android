/**
 * Copyright (C) 2014 Open Whisper Systems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.contacts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller.FastScrollAdapter;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController2;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.ViewHolder;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CharacterIterable;
import org.thoughtcrime.securesms.util.CursorUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * List adapter to display all contacts and their related information
 *
 * @author Jake McGinty
 */
public class ContactSelectionListAdapter extends CursorRecyclerViewAdapter<ViewHolder>
        implements FastScrollAdapter {
  @SuppressWarnings("unused")
  private final static String TAG = Log.tag(ContactSelectionListAdapter.class);

  private static final int VIEW_TYPE_CONTACT = 0;
  private static final int VIEW_TYPE_DIVIDER = 1;
  private static final int VIEW_TYPE_SHARE_CONFIRM = 2;

  private static final float WELCOME_OPTIOON_SCALE_FOCUS = 1.3f;
  private static final float WELCOME_OPTIOON_SCALE_NON_FOCUS = 1.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_FOCUS = 12.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS = 1.0f;

  public static final int PAYLOAD_SELECTION_CHANGE = 1;

  private final boolean           multiSelect;
  private final LayoutInflater    layoutInflater;
  private final ItemClickListener clickListener;
  private final GlideRequests     glideRequests;
  private final Set<RecipientId>  currentContacts;

  private final SelectedContactSet selectedContacts = new SelectedContactSet();

  private final View.OnClickListener shareConfirmClickListener;

  private boolean isSharing = false;

  public String oldtext1 = "";
  public String oldtext2 = "";
  RelativeLayout rlContainer;
  public int mFocusHeight;
  public int mNormalHeight;
  public int mNormalPaddingX;
  public int mFocusPaddingX;
  public int mFocusTextSize;
  public int mNormalTextSize;

  private View.OnFocusChangeListener onFocusChangeListener;

  private Animation animDownAndGone, animDownAndVisible, animUpAndGone, animUpAndVisible;
  private ItemAnimViewController2 mItemAnimController;
  private static boolean isScorllUp = true;
  public void clearSelectedContacts() {
    selectedContacts.clear();
  }

  public boolean isSelectedContact(@NonNull SelectedContact contact) {
    return selectedContacts.contains(contact);
  }

  public void addSelectedContact(@NonNull SelectedContact contact) {
    if (!selectedContacts.add(contact)) {
      Log.i(TAG, "Contact was already selected, possibly by another identifier");
    }
  }

  public void removeFromSelectedContacts(@NonNull SelectedContact selectedContact) {
    int removed = selectedContacts.remove(selectedContact);
    Log.i(TAG, String.format(Locale.US, "Removed %d selected contacts that matched", removed));
  }

  public abstract static class ViewHolder extends RecyclerView.ViewHolder {

    public ViewHolder(View itemView) {
      super(itemView);
    }

    public abstract void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkboxVisible);
    public abstract void unbind(@NonNull GlideRequests glideRequests);
    public abstract void setChecked(boolean checked);
    public void animateChecked(boolean checked) {
      // Intentionally empty.
    }

    public abstract void setEnabled(boolean enabled);

    public void setLetterHeaderCharacter(@Nullable String letterHeaderCharacter) {
      // Intentionally empty.
    }
  }

  public static class ShareConfirmViewHolder extends ViewHolder {
    private TextView shareView;

    ShareConfirmViewHolder(View itemView , View.OnClickListener shareClickListener) {
      super(itemView);
      this.shareView = itemView.findViewById(R.id.share_confirm);
      itemView.setOnClickListener(shareClickListener);
    }

    @Override public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkboxVisible) {
      shareView.setText("Share");
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {}

    @Override
    public void setChecked(boolean checked) {}

    @Override
    public void setEnabled(boolean enabled) {}
  }

  public static class ContactViewHolder extends ViewHolder implements LetterHeaderDecoration.LetterHeaderItem {

    private String letterHeader;

    ContactViewHolder(@NonNull final View itemView,
                      @Nullable final ItemClickListener clickListener)
    {
      super(itemView);
      itemView.setOnClickListener(v -> {
        if (clickListener != null) clickListener.onItemClick(getView());
      });

      itemView.setOnClickListener(v -> {
        if (clickListener != null) clickListener.onItemClick(getView());
      });
      itemView.setOnKeyListener(new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
          if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            setScrollUp(true);
          }
          if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            setScrollUp(false);
          }
          return false;
        }
      });
    }

    public ContactSelectionListItem getView() {
      return (ContactSelectionListItem) itemView;
    }

    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkBoxVisible) {
      getView().set(glideRequests, recipientId, type, name, number, label, about, checkBoxVisible);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {
      getView().unbind(glideRequests);
    }

    @Override
    public void setChecked(boolean checked) {
      getView().setChecked(checked, false);
    }

    @Override
    public void animateChecked(boolean checked) {
      getView().setChecked(checked, true);
    }

    @Override
    public void setEnabled(boolean enabled) {
      getView().setEnabled(enabled);
    }
    @Override
    public @Nullable String getHeaderLetter() {
      return letterHeader;
    }

    @Override
    public void setLetterHeaderCharacter(@Nullable String letterHeaderCharacter) {
      this.letterHeader = letterHeaderCharacter;
    }
  }

  public ContactSelectionListAdapter(@NonNull Context context,
                                     @NonNull GlideRequests glideRequests,
                                     @Nullable Cursor cursor,
                                     @Nullable ItemClickListener clickListener,
                                     boolean multiSelect,
                                     @NonNull Set<RecipientId> currentContacts,
                                     RelativeLayout relativeLayout,
                                     int marginTop,View.OnFocusChangeListener onFocusChangeListener,
                                     View.OnClickListener shareConfirmClickListener,
                                     boolean isSharing) {
    super(context, cursor);
    Resources res = context.getResources();
    mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
    mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

    mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
    mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

    mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
    mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
    //mFocusedColor = res.getColor(R.color.focused_text_color);
    //mNormalColor = res.getColor(R.color.normal_text_color);
    this.layoutInflater = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.multiSelect     = multiSelect;
    this.clickListener   = clickListener;
    this.currentContacts = currentContacts;
//    mItemAnimController = new ItemAnimViewController2(relativeLayout, mFocusTextSize, mFocusHeight, marginTop);
    this.onFocusChangeListener = onFocusChangeListener;
    this.shareConfirmClickListener = shareConfirmClickListener;
    this.isSharing       = isSharing;
  }

  public static class DividerViewHolder extends ViewHolder {

    private final TextView label;

    DividerViewHolder(View itemView) {
      super(itemView);
      this.label = itemView.findViewById(R.id.label);
    }

    @Override
    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkboxVisible) {
      this.label.setText(name);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {}

    @Override
    public void setChecked(boolean checked) {}

    @Override
    public void setEnabled(boolean enabled) {}
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    HeaderViewHolder(View itemView) {
      super(itemView);
    }
  }

  public ContactSelectionListAdapter(@NonNull  Context context,
                                     @NonNull  GlideRequests glideRequests,
                                     @Nullable Cursor cursor,
                                     @Nullable ItemClickListener clickListener,
                                     boolean multiSelect,
                                     @NonNull Set<RecipientId> currentContacts,
                                     View.OnClickListener shareConfirmClickListener,
                                     Boolean isSharing)
  {
    super(context, cursor);
    this.layoutInflater  = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.multiSelect     = multiSelect;
    this.clickListener   = clickListener;
    this.currentContacts = currentContacts;
    this.shareConfirmClickListener = shareConfirmClickListener;
    this.isSharing       = isSharing;
  }

  @Override
  public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    if (viewType == VIEW_TYPE_CONTACT) {
      return new ContactViewHolder(layoutInflater.inflate(R.layout.contact_selection_list_item, parent, false), clickListener);
    }else if (viewType == VIEW_TYPE_SHARE_CONFIRM){
      return new ShareConfirmViewHolder(layoutInflater.inflate(R.layout.contact_selection_share_confirm_item, parent,false),shareConfirmClickListener);
    } else {
      return new DividerViewHolder(layoutInflater.inflate(R.layout.contact_selection_list_divider, parent, false));
    }
  }

  @Override
  public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    String      rawId       = CursorUtil.requireString(cursor, ContactRepository.ID_COLUMN);
    RecipientId id          = rawId != null ? RecipientId.from(rawId) : null;
    int         contactType = CursorUtil.requireInt(cursor, ContactRepository.CONTACT_TYPE_COLUMN);
    String      name        = CursorUtil.requireString(cursor, ContactRepository.NAME_COLUMN);
    String      number      = CursorUtil.requireString(cursor, ContactRepository.NUMBER_COLUMN);
    int         numberType  = CursorUtil.requireInt(cursor, ContactRepository.NUMBER_TYPE_COLUMN);
    String      about       = CursorUtil.requireString(cursor, ContactRepository.ABOUT_COLUMN);
    String      label       = CursorUtil.requireString(cursor, ContactRepository.LABEL_COLUMN);
    String      labelText   = ContactsContract.CommonDataKinds.Phone.getTypeLabel(getContext().getResources(),
                                                                                  numberType, label).toString();


    boolean currentContact = currentContacts.contains(id);

    viewHolder.unbind(glideRequests);
    viewHolder.bind(glideRequests, id, contactType, name, number, labelText, about, multiSelect || currentContact);
    viewHolder.setEnabled(true);
    ContactSelectionListItem CSLitem;
    if (viewHolder.itemView instanceof ContactSelectionListItem) {
      CSLitem = (ContactSelectionListItem) (viewHolder.itemView);
      CSLitem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
//          if (onFocusChangeListener!=null)
          startFocusAnimation(v, hasFocus);
        }
      });
    }else if (viewHolder.getAdapterPosition() == 0){
      View shareConfirmItem = viewHolder.itemView;
      shareConfirmItem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          shareStartFocusAnimation(shareConfirmItem,hasFocus);
        }
      });
    }

    if (currentContact) {
      viewHolder.setChecked(true);
      viewHolder.setEnabled(false);
    } else if (numberType == ContactRepository.NEW_USERNAME_TYPE) {
      viewHolder.setChecked(selectedContacts.contains(SelectedContact.forUsername(id, number)));
    } else {
      viewHolder.setChecked(selectedContacts.contains(SelectedContact.forPhone(id, number)));
    }

    if (isContactRow(contactType)) {
      int position = cursor.getPosition();
      if (position == 0) {
        viewHolder.setLetterHeaderCharacter(getHeaderLetterForDisplayName(cursor));
      } else {
        cursor.moveToPrevious();

        int previousRowContactType = CursorUtil.requireInt(cursor, ContactRepository.CONTACT_TYPE_COLUMN);

        if (!isContactRow(previousRowContactType)) {
          cursor.moveToNext();
          viewHolder.setLetterHeaderCharacter(getHeaderLetterForDisplayName(cursor));
        } else {
          String previousHeaderLetter = getHeaderLetterForDisplayName(cursor);
          cursor.moveToNext();
          String newHeaderLetter = getHeaderLetterForDisplayName(cursor);

          if (Objects.equals(previousHeaderLetter, newHeaderLetter)) {
            viewHolder.setLetterHeaderCharacter(null);
          } else {
            viewHolder.setLetterHeaderCharacter(newHeaderLetter);
          }
        }
      }
    }
  }

  private boolean isContactRow(int contactType) {
    return (contactType & (ContactRepository.NEW_PHONE_TYPE | ContactRepository.NEW_USERNAME_TYPE | ContactRepository.DIVIDER_TYPE)) == 0;
  }

  private @Nullable String getHeaderLetterForDisplayName(@NonNull Cursor cursor) {
    String           name              = CursorUtil.requireString(cursor, ContactRepository.NAME_COLUMN);
    Iterator<String> characterIterator = new CharacterIterable(name).iterator();

    if (!TextUtils.isEmpty(name) && characterIterator.hasNext()) {
      String next = characterIterator.next();

      if (Character.isLetter(next.codePointAt(0))) {
        return next.toUpperCase();
      } else {
        return "#";
      }

    } else {
      return null;
    }
  }

  @Override
  protected void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor, @NonNull List<Object> payloads) {
    if (!arePayloadsValid(payloads)) {
      throw new AssertionError();
    }

    String      rawId      = CursorUtil.requireString(cursor, ContactRepository.ID_COLUMN);
    RecipientId id         = rawId != null ? RecipientId.from(rawId) : null;
    int         numberType = CursorUtil.requireInt(cursor, ContactRepository.NUMBER_TYPE_COLUMN);
    String      number     = CursorUtil.requireString(cursor, ContactRepository.NUMBER_COLUMN);

    viewHolder.setEnabled(true);

    if (currentContacts.contains(id)) {
      viewHolder.animateChecked(true);
      viewHolder.setEnabled(false);
    } else if (numberType == ContactRepository.NEW_USERNAME_TYPE) {
      viewHolder.animateChecked(selectedContacts.contains(SelectedContact.forUsername(id, number)));
    } else {
      viewHolder.animateChecked(selectedContacts.contains(SelectedContact.forPhone(id, number)));
    }
  }

  public static void  setScrollUp(boolean scorllUp) {
    isScorllUp = scorllUp;
  }

  private void startFocusAnimation(View v,boolean focused){

    ValueAnimator va ;
    ContactSelectionListItem CSLitem;
    CSLitem=(ContactSelectionListItem)(v);
    TextView text1 = (TextView)(CSLitem.nameView);
    TextView text2 = (TextView)(CSLitem.numberView);
    TextView text3 = (TextView)(CSLitem.labelView);
     //Log.d(TAG,"focused is:"+focused+" text1 is:"+text1.getText().toString()+" text23 is:"+text2.getText().toString()+" "+text3.getText().toString());
    if(focused){
      va = ValueAnimator.ofFloat(0,1);
    }else{
      va = ValueAnimator.ofFloat(1,0);
    }
//    mItemAnimController.setItemVisibility(false);
//    // v.getLayoutParams().height = (int) (height);
//    if(focused){
//      if(isScorllUp){
//        mItemAnimController.actionUpIn(oldtext1, text1.getText().toString() ,
//                oldtext2, (text2.getText().toString() + " " +text3.getText().toString()));
//      }else{
//        mItemAnimController.actionDownIn(oldtext1, text1.getText().toString() ,
//                oldtext2, (text2.getText().toString() + " " +text3.getText().toString()));
//      }
//    }else{
//      mItemAnimController.setItemVisibility(true);
//    }

    //mItemAnimController
    va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator valueAnimator) {
        float scale = (float)valueAnimator.getAnimatedValue();
        float height = (((float)(mFocusHeight - mNormalHeight))*(scale)+(float)mNormalHeight)*2;
        float textsize = ((float)(mFocusTextSize - mNormalTextSize))*(scale) + mNormalTextSize;
        float padding = (float)mNormalPaddingX -((float)(mNormalPaddingX - mFocusPaddingX))*(scale);
        int alpha = (int)((float)0x81 + (float)((0xff - 0x81))*(scale));
        int color =  alpha*0x1000000 + 0xffffff;
//        if(focused){
//          CSLitem.getLayoutParams().height = (int)height;
//          text1.getLayoutParams().height= (int) height;
//        } else {
//          CSLitem.getLayoutParams().height = (int)height;
//        }
        text1.setTextColor(color);
        text1.setTextSize((int)textsize);
        text1.setTextColor(color);
        text1.getLayoutParams().height = (int)height/2;

        text2.setTextColor(color);
        text2.setTextSize((int)textsize);
        text2.setTextColor(color);
        text2.getLayoutParams().height = (int)height/2;


        CSLitem.setPadding((int) padding,CSLitem.getPaddingTop(),CSLitem.getPaddingRight(),CSLitem.getPaddingBottom());
        CSLitem.getLayoutParams().height = (int)height;
      }
    });

    FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
    va.setInterpolator(FastOutLinearInInterpolator);
    if (focused) {
      text2.setSelected(true);
      va.setDuration(270);
      va.start();
    } else {
      text2.setSelected(false);
      va.setDuration(270);
      va.start();
    }
    oldtext1 = text1.getText().toString();
    oldtext2 = text2.getText().toString() + " " +text3.getText().toString();
  }

  private void shareStartFocusAnimation(View v,boolean focused){

    TextView tv = v.findViewById(R.id.share_confirm);
    ValueAnimator va ;
    if(focused){
      va = ValueAnimator.ofFloat(0,1);
    }else{
      va = ValueAnimator.ofFloat(1,0);
    }
    va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator valueAnimator) {
        float scale = (float)valueAnimator.getAnimatedValue();
        float height = (((float)(mFocusHeight - mNormalHeight))*(scale)+(float)mNormalHeight)*2;
        float textsize = ((float)(mFocusTextSize - mNormalTextSize))*(scale) + mNormalTextSize;
        float padding = (float)mNormalPaddingX -((float)(mNormalPaddingX - mFocusPaddingX))*(scale);
        int alpha = (int)((float)0x81 + (float)((0xff - 0x81))*(scale));
        int color =  alpha*0x1000000 + 0xffffff;
//        if(focused){
//          CSLitem.getLayoutParams().height = (int)height;
//          text1.getLayoutParams().height= (int) height;
//        } else {
//          CSLitem.getLayoutParams().height = (int)height;
//        }
        tv.setTextColor(color);
        tv.setTextSize((int)textsize);
        tv.setTextColor(color);
        tv.getLayoutParams().height = (int)height/2;

        v.setPadding((int) padding,v.getPaddingTop(),v.getPaddingRight(),v.getPaddingBottom());
        v.getLayoutParams().height = (int)height/2;
      }
    });

    FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
    va.setInterpolator(FastOutLinearInInterpolator);
    if (focused) {
      va.setDuration(270);
      va.start();
    } else {
      va.setDuration(270);
      va.start();
    }
  }

  /*@Override
  public int getItemViewType(@NonNull Cursor cursor) {
    if (CursorUtil.requireInt(cursor, ContactRepository.CONTACT_TYPE_COLUMN) == ContactRepository.DIVIDER_TYPE) {
      return VIEW_TYPE_DIVIDER;
    } else {
      return VIEW_TYPE_CONTACT;
    }
  }*/

  @Override
  public int getItemViewType(int position) {
    if (isSharing && position == 0 ){
      return VIEW_TYPE_SHARE_CONFIRM;
    }else{
      if (getContactType(position) == ContactRepository.DIVIDER_TYPE){
        return VIEW_TYPE_DIVIDER;
      }else{
        return VIEW_TYPE_CONTACT;
      }
    }
  }

  @Override
  protected boolean arePayloadsValid(@NonNull List<Object> payloads) {
    return payloads.size() == 1 && payloads.get(0).equals(PAYLOAD_SELECTION_CHANGE);
  }

  @Override
  public void onItemViewRecycled(ViewHolder holder) {
    holder.unbind(glideRequests);
  }

  @Override
  public CharSequence getBubbleText(int position) {
    return getHeaderString(position);
  }

  public List<SelectedContact> getSelectedContacts() {
    return selectedContacts.getContacts();
  }

  public int getSelectedContactsCount() {
    return selectedContacts.size();
  }

  public int getCurrentContactsCount() {
    return currentContacts.size();
  }

  private @NonNull String getHeaderString(int position) {
    int contactType = getContactType(position);

    if ((contactType & ContactRepository.RECENT_TYPE) > 0 || contactType == ContactRepository.DIVIDER_TYPE) {
      return " ";
    }

    Cursor cursor = getCursorAtPositionOrThrow(position);
    String letter = CursorUtil.requireString(cursor, ContactRepository.NAME_COLUMN);

    if (letter != null) {
      letter = letter.trim();
      if (letter.length() > 0) {
        char firstChar = letter.charAt(0);
        if (Character.isLetterOrDigit(firstChar)) {
          return String.valueOf(Character.toUpperCase(firstChar));
        }
      }
    }

    return "#";
  }

  private int getContactType(int position) {
    final Cursor cursor = getCursorAtPositionOrThrow(position);
    return cursor.getInt(cursor.getColumnIndexOrThrow(ContactRepository.CONTACT_TYPE_COLUMN));
  }

  private boolean isPush(int position) {
    return getContactType(position) == ContactRepository.PUSH_TYPE;
  }

  public interface ItemClickListener {
    void onItemClick(ContactSelectionListItem item);
  }
}

package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.support.v4.view.ViewCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.ResUtil;
import org.thoughtcrime.securesms.util.spans.CenterAlignedRelativeSizeSpan;

public class FromTextView extends EmojiTextView {

  private static final String TAG = FromTextView.class.getSimpleName();

  public FromTextView(Context context) {
    super(context);
  }

  public FromTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setText(Recipient recipient) {
    setText(recipient, true);
  }

  public void setText(Recipient recipient, boolean read) {
    int        attributes[] = new int[]{R.attr.conversation_list_item_count_color};
    TypedArray colors       = getContext().obtainStyledAttributes(attributes);
    String     fromString   = recipient.toShortString();

    int typeface;

    if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder();

    SpannableString fromSpan = new SpannableString(fromString);
    fromSpan.setSpan(new StyleSpan(typeface), 0, builder.length(),
                     Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

    if (recipient.getName() == null && recipient.getProfileName() != null) {
      SpannableString profileName = new SpannableString(" (~" + recipient.getProfileName() + ") ");
      profileName.setSpan(new CenterAlignedRelativeSizeSpan(0.75f), 0, profileName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      profileName.setSpan(new TypefaceSpan("sans-serif-light"), 0, profileName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      profileName.setSpan(new ForegroundColorSpan(ResUtil.getColor(getContext(), R.attr.conversation_list_item_subject_color)), 0, profileName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

      if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL){
        builder.append(profileName);
        builder.append(fromSpan);
      } else {
        builder.append(fromSpan);
        builder.append(profileName);
      }
    } else {
      builder.append(fromSpan);
    }

    colors.recycle();

    setText(builder);

    if      (recipient.isBlocked()) setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_grey600_18dp, 0, 0, 0);
    else if (recipient.isMuted())   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_grey600_18dp, 0, 0, 0);
    else                            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
  }


}

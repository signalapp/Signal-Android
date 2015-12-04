/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.securesms.contacts;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientsFormatter;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import de.gdata.messaging.util.GUtil;

/**
 * This adapter is used to filter contacts on both name and number.
 */
public class RecipientsAdapter extends ResourceCursorAdapter {

    public static final int CONTACT_ID_INDEX = 1;
    public static final int TYPE_INDEX       = 2;
    public static final int NUMBER_INDEX     = 3;
    public static final int LABEL_INDEX      = 4;
    public static final int NAME_INDEX       = 5;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private ContactAccessor mContactAccessor;

    public RecipientsAdapter(Context context) {
        super(context, R.layout.recipient_filter_item, null);
        mContext = context;
        mContentResolver = context.getContentResolver();
        mContactAccessor = ContactAccessor.getInstance();
    }

    @Override
    public final CharSequence convertToString(Cursor cursor) {
        String name = cursor.getString(RecipientsAdapter.NAME_INDEX);
        int type = cursor.getInt(RecipientsAdapter.TYPE_INDEX);
        String number = cursor.getString(RecipientsAdapter.NUMBER_INDEX).trim();

        String label = cursor.getString(RecipientsAdapter.LABEL_INDEX);
        CharSequence displayLabel = mContactAccessor.phoneTypeToString(mContext, type, label);

        if (number.length() == 0) {
            return number;
        }

        if (name == null) {
            name = "";
        } else {
            // Names with commas are the bane of the recipient editor's existence.
            // We've worked around them by using spans, but there are edge cases
            // where the spans get deleted. Furthermore, having commas in names
            // can be confusing to the user since commas are used as separators
            // between recipients. The best solution is to simply remove commas
            // from names.
            name = name.replace(", ", " ")
                       .replace(",", " ");  // Make sure we leave a space between parts of names.
        }

        String nameAndNumber = RecipientsFormatter.formatNameAndNumber(name, number);

        SpannableString out = new SpannableString(nameAndNumber);
        int len = out.length();

        if (!TextUtils.isEmpty(name)) {
            out.setSpan(new Annotation("name", name), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            out.setSpan(new Annotation("name", number), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String person_id = cursor.getString(RecipientsAdapter.CONTACT_ID_INDEX);
        out.setSpan(new Annotation("person_id", person_id), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new Annotation("label", displayLabel.toString()), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);              
        out.setSpan(new Annotation("number", number), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return out;
    }

    @Override
    public final void bindView(View view, Context context, Cursor cursor) {
        TextView name = (TextView) view.findViewById(R.id.name);
        name.setText(cursor.getString(NAME_INDEX));

        TextView label = (TextView) view.findViewById(R.id.label);
        int type = cursor.getInt(TYPE_INDEX);
        label.setText(mContactAccessor.phoneTypeToString(mContext, type, cursor.getString(LABEL_INDEX)));

        String numberString = cursor.getString(NUMBER_INDEX);
        String nameString = cursor.getString(NAME_INDEX);
        TextView number = (TextView) view.findViewById(R.id.number);

        if(!GUtil.isValidPhoneNumber(numberString)) {
            number.setText(context.getString(R.string.RegistrationActivity_invalid_number));
            view.setClickable(true);
        } else {
            if(numberString.length() != nameString.length()) {
                number.setText("(" + numberString + ")");
                view.setClickable(false);
            } else {
                number.setText("");
                if(!nameString.matches("[0-9]+")) {
                    view.setClickable(true);
                } else {
                    view.setClickable(false);
                }
            }
        }
    }
    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
       return mContactAccessor.getCursorForRecipientFilter( constraint, mContentResolver );
    }

    /**
     * Returns true if all the characters are meaningful as digits
     * in a phone number -- letters, digits, and a few punctuation marks.
     */
    public static  boolean usefulAsDigits(CharSequence cons) {
        int len = cons.length();

        for (int i = 0; i < len; i++) {
            char c = cons.charAt(i);

            if ((c >= '0') && (c <= '9')) {
                continue;
            }
            if ((c == ' ') || (c == '-') || (c == '(') || (c == ')') || (c == '.') || (c == '+')
                    || (c == '#') || (c == '*')) {
                continue;
            }
            if ((c >= 'A') && (c <= 'Z')) {
                continue;
            }
            if ((c >= 'a') && (c <= 'z')) {
                continue;
            }

            return false;
        }

        return true;
    }
}

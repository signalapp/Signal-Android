package org.thoughtcrime.securesms.util;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Context;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;

public class LongClickSpan extends URLSpan {
    private static final String PREFIX_MAILTO = "mailto:";
    private static final String PREFIX_TEL = "tel:";

    public LongClickSpan(String url) {
        super(url);
    }

    public void onLongClick(View widget) {
        Context context = widget.getContext();
        String preparedUrl = prepareUrl(getURL());
        copyUrl(context, preparedUrl);
        Toast.makeText(context,
                context.getString(R.string.ConversationItem_copied_text, preparedUrl), Toast.LENGTH_LONG).show();
    }

    private void copyUrl(Context context, String url) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
            @SuppressWarnings("deprecation") android.text.ClipboardManager clipboard =
                    (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(url);
        } else {
            copyUriSdk11(context, url);
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.HONEYCOMB)
    private void copyUriSdk11(Context context, String url) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(context.getString(R.string.app_name), url);
        clipboard.setPrimaryClip(clip);
    }

    private String prepareUrl(String url) {
        if (url.startsWith(PREFIX_MAILTO)) {
            return url.substring(PREFIX_MAILTO.length());
        } else if (url.startsWith(PREFIX_TEL)) {
            return url.substring(PREFIX_TEL.length());
        }
        return url;
    }
}

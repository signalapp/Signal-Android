package de.gdata.messaging.util;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.text.Layout;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.MotionEvent;

import org.thoughtcrime.securesms.ConversationFragment;

import de.gdata.messaging.isfaserverdefinitions.IRpcService;

public class GDataLinkMovementMethod extends LinkMovementMethod {

    private static Context movementContext;

    private static GDataLinkMovementMethod linkMovementMethod = new GDataLinkMovementMethod();

    private static ConversationFragment conversationFragment;

    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";

    public boolean onTouchEvent(android.widget.TextView widget, android.text.Spannable buffer,
            android.view.MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
            if (link.length != 0) {
                String url = link[0].getURL();
                if (url.startsWith("https") || url.startsWith("http")) {
                    if (conversationFragment != null) {
                        IRpcService service = conversationFragment.getService();

                        boolean maliciousUrlFound = false;

                        if (service != null) {
                            if (!url.startsWith(HTTP_SCHEME) && !url.startsWith(HTTPS_SCHEME)) {
                                url = HTTP_SCHEME + url;
                            }
                            try {
                                if (service.isMaliciousUrl(url)) {
                                    service.addPhishingException(url);
                                    maliciousUrlFound = true;
                                }
                            } catch (RemoteException e) {
                                Log.e("GDATA", e.getMessage());
                            }
                        }

                        if (maliciousUrlFound) {
                            handlePhishingDetection(url);
                        } else {
                            if (!"".equals(url)) {
                                handleOpenBrowser(url);
                            }
                        }
                    }
                } else if (url.startsWith("tel")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    movementContext.startActivity(intent);
                } else if (url.startsWith("mailto")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    movementContext.startActivity(intent);
                }
                return true;
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    public static android.text.method.MovementMethod getInstance(Context c, ConversationFragment fragment) {
        movementContext = c;
        conversationFragment = fragment;
        return linkMovementMethod;
    }

    private void handlePhishingDetection(final String url) {
        GDDialogFragment dialogFragment = GDDialogFragment.newInstance(GDDialogFragment.TYPE_PHISHING_WARNING, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //do nothing
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                handleOpenBrowser(url);
            }
        });
        dialogFragment.show(conversationFragment.getFragmentManager(), "dialog");
    }

    private void handleOpenBrowser(String url) {
        if (!url.startsWith(HTTP_SCHEME) && !url.startsWith(HTTPS_SCHEME)) {
            url = HTTP_SCHEME + url;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        movementContext.startActivity(intent);
    }

}
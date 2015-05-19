package de.gdata.messaging.util;

import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.text.Layout;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.MotionEvent;

import org.thoughtcrime.securesms.ConversationFragment;

import de.gdata.messaging.isfaserverdefinitions.IRpcService;

public class GDataLinkMovementMethod extends LinkMovementMethod {

    private static Fragment conversationFragment;

    private static GDataLinkMovementMethod linkMovementMethod = new GDataLinkMovementMethod();

    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";
    private static final String TEL_SCHEME = "tel";
    private static final String MAIL_SCHEME = "mailto";


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
                if (url.startsWith(HTTPS_SCHEME) || url.startsWith(HTTP_SCHEME)) {

                        boolean maliciousUrlFound = false;

                        if (GService.isMaliciousUrl(url)) {
                             GService.addPhishingException(url);
                             maliciousUrlFound = true;
                        }
                        if (maliciousUrlFound) {
                            handlePhishingDetection(url);
                        } else {
                            if (!"".equals(url)) {
                                handleOpenBrowser(url);
                            }
                        }
                } else if (url.startsWith(TEL_SCHEME)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                  conversationFragment.startActivity(intent);
                } else if (url.startsWith(MAIL_SCHEME)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                  conversationFragment.startActivity(intent);
                }
                return true;
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    public static android.text.method.MovementMethod getInstance(ConversationFragment fragment) {
      conversationFragment = fragment;
      return linkMovementMethod;
    }

    private void handlePhishingDetection(final String url) {
        GDDialogFragment dialogFragment = GDDialogFragment.newInstance(GDDialogFragment.TYPE_PHISHING_WARNING, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
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
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
      conversationFragment.startActivity(intent);
    }

}
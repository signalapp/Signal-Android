package de.gdata.messaging.util;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by jan on 20.01.15.
 */
public class Util {


    public static final View setFontForFragment(Context context, View root) {
        GDataPreferences prefs = new GDataPreferences(context);
        Typeface font = TypeFaces.getTypeFace(context, prefs.getApplicationFont());
        setFontToLayouts(root, font);
        return root;
    }

    /**
     * Sets the Typeface e.g. Roboto-Thin.tff for an Activity
     *
     * @param container parent View containing the TextViews
     * @param font      Typeface to set
     */
    public static final void setFontToLayouts(Object container, Typeface font) {
        if (container == null || font == null) return;

        if (container instanceof View) {
            if (container instanceof TextView) {
                ((TextView) container).setTypeface(font);
            } else if (container instanceof LinearLayout) {
                final int count = ((LinearLayout) container).getChildCount();
                for (int i = 0; i <= count; i++) {
                    final View child = ((LinearLayout) container).getChildAt(i);
                    if (child instanceof TextView) {
                        // Set the font if it is a TextView.
                        ((TextView) child).setTypeface(font);
                    } else if (child instanceof ViewGroup) {
                        // Recursively attempt another ViewGroup.
                        setFontToLayouts(child, font);
                    }
                }
            } else if (container instanceof FrameLayout) {
                final int count = ((FrameLayout) container).getChildCount();
                for (int i = 0; i <= count; i++) {
                    final View child = ((FrameLayout) container).getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTypeface(font);
                    } else if (child instanceof ViewGroup) {
                        setFontToLayouts(child, font);
                    }
                }
            } else if (container instanceof RelativeLayout) {
                final int count = ((RelativeLayout) container).getChildCount();
                for (int i = 0; i <= count; i++) {
                    final View child = ((RelativeLayout) container).getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTypeface(font);
                    } else if (child instanceof ViewGroup) {
                        setFontToLayouts(child, font);
                    }
                }
            }

        } else if (container instanceof ViewGroup) {
            final int count = ((ViewGroup) container).getChildCount();
            for (int i = 0; i <= count; i++) {
                final View child = ((ViewGroup) container).getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTypeface(font);
                } else if (child instanceof ViewGroup) {
                    setFontToLayouts(child, font);
                }
            }
        }
    }

}

package org.thoughtcrime.securesms.permissions;


import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

public class RationaleDialog {

  public static AlertDialog.Builder createFor(@NonNull Context context, @NonNull String message, @DrawableRes int... drawables) {
    View      view   = LayoutInflater.from(context).inflate(R.layout.permissions_rationale_dialog, null);
    ViewGroup header = view.findViewById(R.id.header_container);
    TextView  text   = view.findViewById(R.id.message);

    for (int i=0;i<drawables.length;i++) {
      ImageView imageView = new ImageView(context);
      imageView.setImageDrawable(context.getResources().getDrawable(drawables[i]));
      imageView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

      header.addView(imageView);

      if (i != drawables.length - 1) {
        TextView plus = new TextView(context);
        plus.setText("+");
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        plus.setTextColor(Color.WHITE);

        LayoutParams layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(ViewUtil.dpToPx(context, 20), 0, ViewUtil.dpToPx(context, 20), 0);

        plus.setLayoutParams(layoutParams);
        header.addView(plus);
      }
    }

    text.setText(message);

    return new AlertDialog.Builder(context, R.style.RationaleDialog).setView(view);
  }

}

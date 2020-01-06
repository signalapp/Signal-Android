package org.thoughtcrime.securesms.maps;

import android.content.Context;
import android.location.Location;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import org.thoughtcrime.securesms.R;

import java.util.Locale;

final class SingleAddressBottomSheet extends CoordinatorLayout {

  private TextView                  placeNameTextView;
  private TextView                  placeAddressTextView;
  private ProgressBar               placeProgressBar;
  private BottomSheetBehavior<View> bottomSheetBehavior;

  public SingleAddressBottomSheet(@NonNull Context context) {
    super(context);
    init();
  }

  public SingleAddressBottomSheet(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public SingleAddressBottomSheet(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    CoordinatorLayout rootView = (CoordinatorLayout) inflate(getContext(), R.layout.activity_map_bottom_sheet_view, this);

    bottomSheetBehavior = BottomSheetBehavior.from(rootView.findViewById(R.id.root_bottom_sheet));
    bottomSheetBehavior.setHideable(true);
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

    bindViews();
  }

  private void bindViews() {
    placeNameTextView    = findViewById(R.id.text_view_place_name);
    placeAddressTextView = findViewById(R.id.text_view_place_address);
    placeProgressBar     = findViewById(R.id.progress_bar_place);
  }

  public void showLoading() {
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    placeNameTextView.setText("");
    placeAddressTextView.setText("");
    placeProgressBar.setVisibility(View.VISIBLE);
  }

  public void showResult(double latitude, double longitude, String addressToShortString, String addressToString) {
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    placeProgressBar.setVisibility(View.GONE);

    if (TextUtils.isEmpty(addressToString)) {
      String longString = Location.convert(longitude, Location.FORMAT_DEGREES);
      String latString  = Location.convert(latitude,  Location.FORMAT_DEGREES);

      placeNameTextView.setText(String.format(Locale.getDefault(), "%s %s", latString, longString));
    } else {
      placeNameTextView.setText(addressToShortString);
      placeAddressTextView.setText(addressToString);
    }
  }

  public void hide() {
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
  }
}

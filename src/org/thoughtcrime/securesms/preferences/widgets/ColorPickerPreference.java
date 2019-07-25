package org.thoughtcrime.securesms.preferences.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.takisoft.colorpicker.ColorPickerDialog;
import com.takisoft.colorpicker.ColorPickerDialog.Size;
import com.takisoft.colorpicker.ColorStateDrawable;

import org.thoughtcrime.securesms.R;

public class ColorPickerPreference extends DialogPreference {

  private static final String TAG = ColorPickerPreference.class.getSimpleName();

  private int[] colors;
  private CharSequence[] colorDescriptions;
  private int color;
  private int columns;
  private int size;
  private boolean sortColors;

  private ImageView colorWidget;
  private OnPreferenceChangeListener listener;

  public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerPreference, defStyleAttr, 0);

    int colorsId = a.getResourceId(R.styleable.ColorPickerPreference_colors, R.array.color_picker_default_colors);

    if (colorsId != 0) {
      colors = context.getResources().getIntArray(colorsId);
    }

    colorDescriptions = a.getTextArray(R.styleable.ColorPickerPreference_colorDescriptions);
    color = a.getColor(R.styleable.ColorPickerPreference_currentColor, 0);
    columns = a.getInt(R.styleable.ColorPickerPreference_columns, 3);
    size = a.getInt(R.styleable.ColorPickerPreference_colorSize, 2);
    sortColors = a.getBoolean(R.styleable.ColorPickerPreference_sortColors, false);

    a.recycle();

    setWidgetLayoutResource(R.layout.preference_widget_color_swatch);
  }

  public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  @SuppressLint("RestrictedApi")
  public ColorPickerPreference(Context context, AttributeSet attrs) {
    this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.dialogPreferenceStyle,
                                                 android.R.attr.dialogPreferenceStyle));
  }

  public ColorPickerPreference(Context context) {
    this(context, null);
  }

  @Override
  public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {
    super.setOnPreferenceChangeListener(listener);
    this.listener = listener;
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);

    colorWidget = (ImageView) holder.findViewById(R.id.color_picker_widget);
    setColorOnWidget(color);
  }

  private void setColorOnWidget(int color) {
    if (colorWidget == null) {
      return;
    }

    Drawable[] colorDrawable = new Drawable[]
        {ContextCompat.getDrawable(getContext(), R.drawable.colorpickerpreference_pref_swatch)};
    colorWidget.setImageDrawable(new ColorStateDrawable(colorDrawable, color));
  }

  /**
   * Returns the current color.
   *
   * @return The current color.
   */
  public int getColor() {
    return color;
  }

  /**
   * Sets the current color.
   *
   * @param color The current color.
   */
  public void setColor(int color) {
    setInternalColor(color, false);
  }

  /**
   * Returns all of the available colors.
   *
   * @return The available colors.
   */
  public int[] getColors() {
    return colors;
  }

  /**
   * Sets the available colors.
   *
   * @param colors The available colors.
   */
  public void setColors(int[] colors) {
    this.colors = colors;
  }

  /**
   * Returns whether the available colors should be sorted automatically based on their HSV
   * values.
   *
   * @return Whether the available colors should be sorted automatically based on their HSV
   * values.
   */
  public boolean isSortColors() {
    return sortColors;
  }

  /**
   * Sets whether the available colors should be sorted automatically based on their HSV
   * values. The sorting does not modify the order of the original colors supplied via
   * {@link #setColors(int[])} or the XML attribute {@code app:colors}.
   *
   * @param sortColors Whether the available colors should be sorted automatically based on their
   *                   HSV values.
   */
  public void setSortColors(boolean sortColors) {
    this.sortColors = sortColors;
  }

  /**
   * Returns the available colors' descriptions that can be used by accessibility services.
   *
   * @return The available colors' descriptions.
   */
  public CharSequence[] getColorDescriptions() {
    return colorDescriptions;
  }

  /**
   * Sets the available colors' descriptions that can be used by accessibility services.
   *
   * @param colorDescriptions The available colors' descriptions.
   */
  public void setColorDescriptions(CharSequence[] colorDescriptions) {
    this.colorDescriptions = colorDescriptions;
  }

  /**
   * Returns the number of columns to be used in the picker dialog for displaying the available
   * colors. If the value is less than or equals to 0, the number of columns will be determined
   * automatically by the system using FlexboxLayoutManager.
   *
   * @return The number of columns to be used in the picker dialog.
   * @see com.google.android.flexbox.FlexboxLayoutManager
   */
  public int getColumns() {
    return columns;
  }

  /**
   * Sets the number of columns to be used in the picker dialog for displaying the available
   * colors. If the value is less than or equals to 0, the number of columns will be determined
   * automatically by the system using FlexboxLayoutManager.
   *
   * @param columns The number of columns to be used in the picker dialog. Use 0 to set it to
   *                'auto' mode.
   * @see com.google.android.flexbox.FlexboxLayoutManager
   */
  public void setColumns(int columns) {
    this.columns = columns;
  }

  /**
   * Returns the size of the color swatches in the dialog. It can be either
   * {@link ColorPickerDialog#SIZE_SMALL} or {@link ColorPickerDialog#SIZE_LARGE}.
   *
   * @return The size of the color swatches in the dialog.
   * @see ColorPickerDialog#SIZE_SMALL
   * @see ColorPickerDialog#SIZE_LARGE
   */
  @Size
  public int getSize() {
    return size;
  }

  /**
   * Sets the size of the color swatches in the dialog. It can be either
   * {@link ColorPickerDialog#SIZE_SMALL} or {@link ColorPickerDialog#SIZE_LARGE}.
   *
   * @param size The size of the color swatches in the dialog. It can be either
   *             {@link ColorPickerDialog#SIZE_SMALL} or {@link ColorPickerDialog#SIZE_LARGE}.
   * @see ColorPickerDialog#SIZE_SMALL
   * @see ColorPickerDialog#SIZE_LARGE
   */
  public void setSize(@Size int size) {
    this.size = size;
  }

  private void setInternalColor(int color, boolean force) {
    int oldColor = getPersistedInt(0);

    boolean changed = oldColor != color;

    if (changed || force) {
      this.color = color;

      persistInt(color);

      setColorOnWidget(color);

      if (listener != null) listener.onPreferenceChange(this, color);
      notifyChanged();
    }
  }

  @Override
  protected Object onGetDefaultValue(TypedArray a, int index) {
    return a.getString(index);
  }

  @Override
  protected void onSetInitialValue(boolean restoreValue, Object defaultValueObj) {
    final String defaultValue = (String) defaultValueObj;
    setInternalColor(restoreValue ? getPersistedInt(0) : (!TextUtils.isEmpty(defaultValue) ? Color.parseColor(defaultValue) : 0), true);
  }
}
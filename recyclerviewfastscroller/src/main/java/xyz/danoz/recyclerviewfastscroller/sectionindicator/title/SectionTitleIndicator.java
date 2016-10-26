package xyz.danoz.recyclerviewfastscroller.sectionindicator.title;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import xyz.danoz.recyclerviewfastscroller.R;
import xyz.danoz.recyclerviewfastscroller.sectionindicator.AbsSectionIndicator;

/**
 * Popup view that gets shown when fast scrolling
 */
public abstract class SectionTitleIndicator<T> extends AbsSectionIndicator<T> {

    private static final int[] STYLEABLE = R.styleable.SectionTitleIndicator;
    private static final int DEFAULT_TITLE_INDICATOR_LAYOUT = R.layout.section_indicator_with_title;
    private static final int DEFAULT_BACKGROUND_COLOR = android.R.color.darker_gray;
    private static final int DEFAULT_TEXT_COLOR = android.R.color.white;

    private final View mIndicatorBackground;
    private final TextView mTitleText;

    public SectionTitleIndicator(Context context) {
        this(context, null);
    }

    public SectionTitleIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SectionTitleIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mIndicatorBackground = findViewById(R.id.section_title_popup);
        mTitleText = (TextView) findViewById(R.id.section_indicator_text);

        TypedArray attributes = getContext().getTheme().obtainStyledAttributes(attrs, STYLEABLE, 0, 0);
        try {
            int customBackgroundColor =
                    attributes.getColor(R.styleable.SectionTitleIndicator_rfs_backgroundColor, getDefaultBackgroundColor());
            applyCustomBackgroundColorAttribute(customBackgroundColor);

            int customTextColor =
                    attributes.getColor(R.styleable.SectionTitleIndicator_rfs_textColor, getDefaultBackgroundColor());
            applyCustomTextColorAttribute(customTextColor);
        } finally {
            attributes.recycle();
        }
    }

    /**
     * @return the default layout for a section indicator with a title. This closely resembles the section indicator
     *         featured in Lollipop's Contact's application
     */
    @Override
    protected int getDefaultLayoutId() {
        return DEFAULT_TITLE_INDICATOR_LAYOUT;
    }

    protected int getDefaultBackgroundColor() {
        return DEFAULT_BACKGROUND_COLOR;
    }

    protected int getDefaultTextColor() {
        return DEFAULT_TEXT_COLOR;
    }

    /**
     * Clients can provide a custom background color for a section indicator
     * @param color provided in XML via the {@link R.styleable#SectionTitleIndicator_rfs_backgroundColor} parameter. If not
     *              specified in XML, this defaults to that which is provided by {@link #getDefaultBackgroundColor()}
     */
    @Override
    protected void applyCustomBackgroundColorAttribute(int color) {
        setIndicatorBackgroundColor(color);
    }

    /**
     * An enhanced method for setting the background color of the indicator that has knowledge of the indicator's
     * layout in instances where calling {@link #setBackgroundColor(int)} won't do.
     * @param color to set as the indicator's background color
     */
    public void setIndicatorBackgroundColor(int color) {
        Drawable backgroundDrawable = mIndicatorBackground.getBackground();

        if (backgroundDrawable instanceof GradientDrawable) {
            GradientDrawable backgroundShape = (GradientDrawable) backgroundDrawable;
            backgroundShape.setColor(color);
        } else {
          mIndicatorBackground.setBackgroundColor(color);
        }
    }

    /**
     * Clients can provide a custom text color for a section indicator
     * @param color provided in XML via the {@link R.styleable#SectionTitleIndicator_rfs_textColor} parameter. If not
     *              specified in XML, this defaults to that which is provided by {@link #getDefaultTextColor()} ()}
     */
    protected void applyCustomTextColorAttribute(int color) {
        setIndicatorTextColor(color);
    }

    /**
     * Allows user to programmatically set the text color of the indicator
     * @param color to set as the indicator's text color
     */
    public void setIndicatorTextColor(int color) {
        mTitleText.setTextColor(color);
    }

    /**
     * Set the text for the section title popup
     * @param text to display in the section title popup
     */
    public void setTitleText(String text) {
        mTitleText.setText(text);
    }

}

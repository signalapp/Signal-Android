package org.thoughtcrime.securesms.util.spans;


import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

public class CenterAlignedRelativeSizeSpan extends MetricAffectingSpan {

  private final float relativeSize;

  public CenterAlignedRelativeSizeSpan(float relativeSize) {
    this.relativeSize = relativeSize;
  }

  @Override
  public void updateMeasureState(TextPaint p) {
    updateDrawState(p);
  }

  @Override
  public void updateDrawState(TextPaint tp) {
    tp.setTextSize(tp.getTextSize() * relativeSize);
    tp.baselineShift += (int) (tp.ascent() * relativeSize) / 4;
  }
}

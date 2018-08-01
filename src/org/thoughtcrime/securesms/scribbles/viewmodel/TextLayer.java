/**
 * Copyright (c) 2016 UPTech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.thoughtcrime.securesms.scribbles.viewmodel;

public class TextLayer extends Layer {

  private String text;
  private Font font;

  public TextLayer() {
  }

  @Override
  protected void reset() {
    super.reset();
    this.text = "";
    this.font = new Font();
  }

  @Override
  protected float getMaxScale() {
    return Limits.MAX_SCALE;
  }

  @Override
  protected float getMinScale() {
    return Limits.MIN_SCALE;
  }

  @Override
  public float initialScale() {
    return Limits.INITIAL_SCALE;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Font getFont() {
    return font;
  }

  public void setFont(Font font) {
    this.font = font;
  }

  @Override
  public void postScale(float scaleDiff) {
    if (scaleDiff > 0)       font.increaseSize(scaleDiff);
    else if (scaleDiff < 0)  font.decreaseSize(Math.abs(scaleDiff));
  }

  public interface Limits {
    /**
     * limit text size to view bounds
     * so that users don't put small font size and scale it 100+ times
     */
    float MAX_SCALE = 1.0F;
    float MIN_SCALE = 0.2F;

    float MIN_BITMAP_HEIGHT = 0.13F;

    float FONT_SIZE_STEP = 0.008F;

    float INITIAL_FONT_SIZE = 0.1F;
    int INITIAL_FONT_COLOR = 0xff000000;

    float INITIAL_SCALE = 0.8F; // set the same to avoid text scaling
  }
}
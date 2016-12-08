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


public class Font {

  /**
   * color value (ex: 0xFF00FF)
   */
  private int color;
  /**
   * name of the font
   */
  private String typeface;
  /**
   * size of the font, relative to parent
   */
  private float size;

  public Font() {
  }

  public void increaseSize(float diff) {
    if (size + diff <= Limits.MAX_FONT_SIZE) {
      size = size + diff;
    }
  }

  public void decreaseSize(float diff) {
    if (size - diff >= Limits.MIN_FONT_SIZE) {
      size = size - diff;
    }
  }

  public int getColor() {
    return color;
  }

  public void setColor(int color) {
    this.color = color;
  }

  public String getTypeface() {
    return typeface;
  }

  public void setTypeface(String typeface) {
    this.typeface = typeface;
  }

  public float getSize() {
    return size;
  }

  public void setSize(float size) {
    this.size = size;
  }

  private interface Limits {
    float MIN_FONT_SIZE = 0.01F;
    float MAX_FONT_SIZE = 0.46F;
  }
}
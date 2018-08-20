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

import android.support.annotation.FloatRange;
import org.thoughtcrime.securesms.logging.Log;

public class Layer {

  /**
   * rotation relative to the layer center, in degrees
   */
  @FloatRange(from = 0.0F, to = 360.0F)
  private float rotationInDegrees;

  private float scale;
  /**
   * top left X coordinate, relative to parent canvas
   */
  private float x;
  /**
   * top left Y coordinate, relative to parent canvas
   */
  private float y;
  /**
   * is layer flipped horizontally (by X-coordinate)
   */
  private boolean isFlipped;

  public Layer() {
    reset();
  }

  protected void reset() {
    this.rotationInDegrees = 0.0F;
    this.scale = 1.0F;
    this.isFlipped = false;
    this.x = 0.0F;
    this.y = 0.0F;
  }

  public void postScale(float scaleDiff) {
    Log.i("Layer", "ScaleDiff: " + scaleDiff);
    float newVal = scale + scaleDiff;
    if (newVal >= getMinScale() && newVal <= getMaxScale()) {
      scale = newVal;
    }
  }

  protected float getMaxScale() {
    return Limits.MAX_SCALE;
  }

  protected float getMinScale() {
    return Limits.MIN_SCALE;
  }

  public void postRotate(float rotationInDegreesDiff) {
    this.rotationInDegrees += rotationInDegreesDiff;
    this.rotationInDegrees %= 360.0F;
  }

  public void postTranslate(float dx, float dy) {
    this.x += dx;
    this.y += dy;
  }

  public void flip() {
    this.isFlipped = !isFlipped;
  }

  public float initialScale() {
    return Limits.INITIAL_ENTITY_SCALE;
  }

  public float getRotationInDegrees() {
    return rotationInDegrees;
  }

  public void setRotationInDegrees(@FloatRange(from = 0.0, to = 360.0) float rotationInDegrees) {
    this.rotationInDegrees = rotationInDegrees;
  }

  public float getScale() {
    return scale;
  }

  public void setScale(float scale) {
    this.scale = scale;
  }

  public float getX() {
    return x;
  }

  public void setX(float x) {
    this.x = x;
  }

  public float getY() {
    return y;
  }

  public void setY(float y) {
    this.y = y;
  }

  public boolean isFlipped() {
    return isFlipped;
  }

  public void setFlipped(boolean flipped) {
    isFlipped = flipped;
  }

  interface Limits {
    float MIN_SCALE = 0.06F;
    float MAX_SCALE = 4.0F;
    float INITIAL_ENTITY_SCALE = 0.4F;
  }
}

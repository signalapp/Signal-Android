/**
 * Copyright (C) 2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

import android.view.Surface;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class SurfaceUtilTest {

  @Test
  public void testFaulty() {
    int degrees = SurfaceUtil.getDegreesFromSurfaceRotation(-1);
    assertThat(degrees, is(0));
  }

  @Test
  public void test0() {
    int degrees = SurfaceUtil.getDegreesFromSurfaceRotation(Surface.ROTATION_0);
    assertThat(degrees, is(0));
  }

  @Test
  public void test90() {
    int degrees = SurfaceUtil.getDegreesFromSurfaceRotation(Surface.ROTATION_90);
    assertThat(degrees, is(90));
  }

  @Test
  public void test180() {
    int degrees = SurfaceUtil.getDegreesFromSurfaceRotation(Surface.ROTATION_180);
    assertThat(degrees, is(180));
  }

  @Test
  public void test270() {
    int degrees = SurfaceUtil.getDegreesFromSurfaceRotation(Surface.ROTATION_270);
    assertThat(degrees, is(270));
  }
}

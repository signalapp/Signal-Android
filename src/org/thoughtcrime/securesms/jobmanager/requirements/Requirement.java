/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.thoughtcrime.securesms.jobmanager.requirements;

import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Job;

import java.io.Serializable;

/**
 * A Requirement that must be satisfied before a Job can run.
 */
public interface Requirement extends Serializable {
  /**
   * @return true if the requirement is satisfied, false otherwise.
   */
  boolean isPresent(@NonNull Job job);

  void onRetry(@NonNull Job job);
}

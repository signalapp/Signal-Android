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

/**
 * Notifies listeners when a {@link org.thoughtcrime.securesms.jobmanager.requirements.Requirement}'s
 * state is likely to have changed.
 */
public interface RequirementProvider {

  /**
   * The {@link org.thoughtcrime.securesms.jobmanager.requirements.RequirementListener} to call when
   * a {@link org.thoughtcrime.securesms.jobmanager.requirements.Requirement}'s status is likely to
   * have changed.
   *
   * @param listener The listener to call.
   */
  public void setListener(RequirementListener listener);
}

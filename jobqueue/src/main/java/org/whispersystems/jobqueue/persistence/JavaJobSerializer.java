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
package org.whispersystems.jobqueue.persistence;

import android.content.Context;
import android.util.Base64;

import org.whispersystems.jobqueue.EncryptionKeys;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.dependencies.ContextDependent;
import org.whispersystems.jobqueue.requirements.Requirement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class JavaJobSerializer implements JobSerializer {

  private final Context context;

  public JavaJobSerializer(Context context) {
    this.context = context;
  }

  @Override
  public String serialize(Job job) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream    oos  = new ObjectOutputStream(baos);
    oos.writeObject(job);

    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
  }

  @Override
  public Job deserialize(EncryptionKeys keys, boolean encrypted, String serialized) throws IOException {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decode(serialized, Base64.NO_WRAP));
      ObjectInputStream    ois  = new ObjectInputStream(bais);

      Job job = (Job)ois.readObject();

      if (job instanceof ContextDependent) {
        ((ContextDependent)job).setContext(context);
      }

      for (Requirement requirement : job.getRequirements()) {
        if (requirement instanceof ContextDependent) {
          ((ContextDependent)requirement).setContext(context);
        }
      }

      job.setEncryptionKeys(keys);

      return job;
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }
}

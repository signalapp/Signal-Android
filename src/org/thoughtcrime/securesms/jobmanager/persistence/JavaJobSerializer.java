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
package org.thoughtcrime.securesms.jobmanager.persistence;

import org.thoughtcrime.securesms.jobmanager.EncryptionKeys;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * An implementation of {@link org.thoughtcrime.securesms.jobmanager.persistence.JobSerializer} that uses
 * Java Serialization.
 *
 * NOTE: This {@link JobSerializer} does not support encryption. Jobs will be serialized normally,
 * but any corresponding {@link Job} encryption keys will be ignored.
 */
public class JavaJobSerializer implements JobSerializer {

  public JavaJobSerializer() {}

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

      return (Job)ois.readObject();
    } catch (ClassNotFoundException e) {
      StringWriter sw = new StringWriter();
      PrintWriter  pw = new PrintWriter(sw);
      e.printStackTrace(pw);

      throw new IOException(e.getMessage() + "\n" + sw.toString());
    }
  }
}

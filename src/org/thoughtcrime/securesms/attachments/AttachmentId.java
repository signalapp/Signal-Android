package org.thoughtcrime.securesms.attachments;

import org.thoughtcrime.securesms.util.Util;

public class AttachmentId {

    private final long rowId;
    private final long uniqueId;

    public AttachmentId(long rowId, long uniqueId) {
        this.rowId    = rowId;
        this.uniqueId = uniqueId;
    }

    public long getRowId() {
        return rowId;
    }

    public long getUniqueId() {
        return uniqueId;
    }

    public String[] toStrings() {
        return new String[] {String.valueOf(rowId), String.valueOf(uniqueId)};
    }

    public String toString() {
        return "(row id: " + rowId + ", unique ID: " + uniqueId + ")";
    }

    public boolean isValid() {
        return rowId >= 0 && uniqueId >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AttachmentId attachmentId = (AttachmentId)o;

        if (rowId != attachmentId.rowId) return false;
        return uniqueId == attachmentId.uniqueId;
    }

    @Override
    public int hashCode() {
        return Util.hashCode(rowId, uniqueId);
    }
}
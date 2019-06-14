package org.thoughtcrime.securesms.imageeditor.model;

import androidx.annotation.NonNull;

/**
 * Flags for an {@link EditorElement}.
 * <p>
 * Values you set are not persisted unless you call {@link #persist()}.
 * <p>
 * This allows temporary state for editing and an easy way to revert to the persisted state via {@link #reset()}.
 */
public final class EditorFlags {

  private static final int ASPECT_LOCK      = 1;
  private static final int ROTATE_LOCK      = 2;
  private static final int SELECTABLE       = 4;
  private static final int VISIBLE          = 8;
  private static final int CHILDREN_VISIBLE = 16;
  private static final int EDITABLE         = 32;

  private int flags;
  private int markedFlags;
  private int persistedFlags;

  EditorFlags() {
    this(ASPECT_LOCK | SELECTABLE | VISIBLE | CHILDREN_VISIBLE | EDITABLE);
  }

  EditorFlags(int flags) {
    this.flags = flags;
    this.persistedFlags = flags;
  }

  public EditorFlags setRotateLocked(boolean rotateLocked) {
    setFlag(ROTATE_LOCK, rotateLocked);
    return this;
  }

  public boolean isRotateLocked() {
    return isFlagSet(ROTATE_LOCK);
  }

  public EditorFlags setAspectLocked(boolean aspectLocked) {
    setFlag(ASPECT_LOCK, aspectLocked);
    return this;
  }

  public boolean isAspectLocked() {
    return isFlagSet(ASPECT_LOCK);
  }

  public EditorFlags setSelectable(boolean selectable) {
    setFlag(SELECTABLE, selectable);
    return this;
  }

  public boolean isSelectable() {
    return isFlagSet(SELECTABLE);
  }

  public EditorFlags setEditable(boolean canEdit) {
    setFlag(EDITABLE, canEdit);
    return this;
  }

  public boolean isEditable() {
    return isFlagSet(EDITABLE);
  }

  public EditorFlags setVisible(boolean visible) {
    setFlag(VISIBLE, visible);
    return this;
  }

  public boolean isVisible() {
    return isFlagSet(VISIBLE);
  }

  public EditorFlags setChildrenVisible(boolean childrenVisible) {
    setFlag(CHILDREN_VISIBLE, childrenVisible);
    return this;
  }

  public boolean isChildrenVisible() {
    return isFlagSet(CHILDREN_VISIBLE);
  }

  private void setFlag(int flag, boolean set) {
    if (set) {
      this.flags |= flag;
    } else {
      this.flags &= ~flag;
    }
  }

  private boolean isFlagSet(int flag) {
    return (flags & flag) != 0;
  }

  int asInt() {
    return persistedFlags;
  }

  int getCurrentState() {
    return flags;
  }

  public void persist() {
    persistedFlags = flags;
  }

  void reset() {
    restoreState(persistedFlags);
  }

  void restoreState(int flags) {
    this.flags = flags;
  }

  void mark() {
    markedFlags = flags;
  }

  void restore() {
    flags = markedFlags;
  }

  public void set(@NonNull EditorFlags from) {
    this.persistedFlags = from.persistedFlags;
    this.flags = from.flags;
  }
}

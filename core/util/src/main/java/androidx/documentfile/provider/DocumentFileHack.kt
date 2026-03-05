package androidx.documentfile.provider

/**
 * Located in androidx package as [TreeDocumentFile] is package protected.
 *
 * @return true if can be used like a tree document file (e.g., use content resolver queries)
 */
fun DocumentFile.isTreeDocumentFile(): Boolean {
  return this is TreeDocumentFile
}

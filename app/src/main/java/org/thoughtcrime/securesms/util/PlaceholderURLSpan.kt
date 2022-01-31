package org.thoughtcrime.securesms.util

/**
 * LinkifyCompat.addLinks() will strip pre-existing URLSpans. This acts as a way to
 * indicate where a link should be added without being stripped. The consumer is
 * responsible for replacing the placeholder with an actual URLSpan.
 */
class PlaceholderURLSpan(url: String) : android.text.Annotation("placeholderUrl", url)

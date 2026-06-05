package org.thoughtcrime.securesms.util

/**
 * Marks a region where a link should later be applied, without storing it as an actual [URLSpan].
 * The consumer is responsible for replacing the placeholder with an appropriate URLSpan when the
 * text is rendered.
 */
class PlaceholderURLSpan(url: String) : android.text.Annotation("placeholderUrl", url)

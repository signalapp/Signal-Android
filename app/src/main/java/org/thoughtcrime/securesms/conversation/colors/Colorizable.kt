package org.thoughtcrime.securesms.conversation.colors

import org.thoughtcrime.securesms.util.Projection

/**
 * Denotes that a class can be colorized. The class is responsible for
 * generating its own projection.
 */
interface Colorizable {
  val colorizerProjections: List<Projection>
}

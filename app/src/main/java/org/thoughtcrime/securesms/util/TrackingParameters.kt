/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Removes tracking parameters from URLs so that opening, copying, or fetching a link
 * does not hand the destination site the analytics token that was attached to it when
 * it was shared.
 *
 * Only query parameters are ever removed. The scheme, host, port, path, and fragment
 * are structurally preserved (the result is built with [okhttp3.HttpUrl.newBuilder]),
 * so a stripped URL can never point at a different destination than the one the user
 * sees; it can only point at the same destination with less information attached.
 * Anything that fails to parse as an HTTP(S) URL is returned untouched.
 *
 * Parameters fall into two groups:
 *  - [GLOBAL_PARAMETERS], which are pure analytics tokens no site needs in order to
 *    resolve a link, and are therefore safe to remove everywhere.
 *  - [HOST_SCOPED_PARAMETERS], which are tracking tokens on the listed hosts but are
 *    short, generic names that may well be load-bearing elsewhere (`si`, `s`, `t`), so
 *    they are only removed on those hosts.
 */
object TrackingParameters {

  /** Matches the whole `utm_*` analytics family. */
  private const val UTM_PREFIX = "utm_"

  /**
   * Tracking parameters removed regardless of host. Every entry here is a click or
   * campaign identifier that exists to attribute a visit, never to select what the URL
   * resolves to.
   */
  private val GLOBAL_PARAMETERS: Set<String> = setOf(
    // Google Ads / DoubleClick
    "gclid", "gclsrc", "dclid", "gbraid", "wbraid",
    // Meta
    "fbclid", "fb_action_ids", "fb_action_types", "fb_source", "fb_ref",
    // Microsoft / Bing
    "msclkid",
    // X / Twitter
    "twclid",
    // TikTok
    "ttclid",
    // Yandex
    "yclid", "_openstat",
    // Mailchimp / Marketo / HubSpot
    "mc_cid", "mc_eid", "mkt_tok", "_hsenc", "_hsmi", "hsctatracking",
    // Instagram
    "igshid", "igsh",
    // Klaviyo / Vero / Omeda and other mail vendors
    "vero_conv", "vero_id", "oly_anon_id", "oly_enc_id", "wickedid",
    // Adobe / generic campaign identifiers
    "icid", "s_cid", "cmpid", "ncid"
  )

  /**
   * Tracking parameters removed only on the hosts that use them for attribution. These
   * names are too generic to strip globally: `t`, for instance, is a legitimate
   * timestamp or token parameter on plenty of other sites.
   *
   * Keys are matched against the URL host as a domain suffix, so `youtube.com` also covers
   * `www.youtube.com` and `m.youtube.com`.
   */
  private val HOST_SCOPED_PARAMETERS: Map<String, Set<String>> = mapOf(
    // YouTube renamed the share-source token from `si` to `is` in early 2026. Both are stripped:
    // `si` still appears throughout existing chat history.
    "youtube.com" to setOf("si", "is", "feature"),
    "youtu.be" to setOf("si", "is", "feature"),
    "spotify.com" to setOf("si"),
    "facebook.com" to setOf("mibextid", "rdid"),
    "fb.watch" to setOf("mibextid"),
    "twitter.com" to setOf("s", "t"),
    "x.com" to setOf("s", "t")
  )

  /**
   * Strips tracking parameters from [url], but only if the user has the setting enabled.
   *
   * This is the entry point call sites should use; [strip] is the pure implementation behind it.
   */
  @JvmStatic
  fun stripIfEnabled(url: String): String {
    return if (SignalStore.settings.isStripLinkTrackingParametersEnabled) {
      strip(url)
    } else {
      url
    }
  }

  /**
   * Strips known tracking parameters from [url], regardless of the user setting.
   *
   * Returns [url] unchanged if it cannot be parsed as an HTTP(S) URL, has no query
   * string, or carries no recognized tracking parameters, so a URL that needs no
   * cleaning is never rewritten or re-encoded.
   */
  @JvmStatic
  fun strip(url: String): String {
    val httpUrl = url.toHttpUrlOrNull() ?: return url

    if (httpUrl.querySize == 0) {
      return url
    }

    val hostScoped: Set<String> = parametersForHost(httpUrl.host)
    val toRemove: List<String> = httpUrl.queryParameterNames.filter { isTracking(it, hostScoped) }

    if (toRemove.isEmpty()) {
      return url
    }

    val builder = httpUrl.newBuilder()
    for (name in toRemove) {
      builder.removeAllQueryParameters(name)
    }

    return builder.build().toString()
  }

  private fun isTracking(name: String, hostScoped: Set<String>): Boolean {
    val normalized = name.lowercase()
    return normalized.startsWith(UTM_PREFIX) ||
      normalized in GLOBAL_PARAMETERS ||
      normalized in hostScoped
  }

  /** Matches [host] as a domain suffix, so subdomains are covered too. */
  private fun parametersForHost(host: String): Set<String> {
    val normalized = host.lowercase()

    return HOST_SCOPED_PARAMETERS
      .filterKeys { domain -> normalized == domain || normalized.endsWith(".$domain") }
      .values
      .flatten()
      .toSet()
  }
}

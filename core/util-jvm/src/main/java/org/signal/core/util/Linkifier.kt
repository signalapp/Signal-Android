/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.net.IDN
import java.util.regex.Pattern

/**
 * Detects links in text.
 *
 * This was created to patch up some of the shoddy link detection in Android's built-in linkifier.
 * Things like trailing `-` not being included in a URL in certain cases, which can break group links.
 *
 * Note that if you want to do additional filtering (like requiring https for link previews), you still
 * need to do that at a higher level.
 */
object Linkifier {

  /** Punctuation we trim from the end of a candidate URL */
  private val TRAILING_PUNCTUATION_TO_TRIM = setOf(
    '.', ',', '!', '?', ';', ':', '\'', '"', '`', '|', '<', '>'
  )

  /** Closing brackets that are trimmed only if the URL has no matching opener inside */
  private val CLOSING_BRACKETS = mapOf(')' to '(', ']' to '[', '}' to '{')

  /**
   * Characters we treat as definitely-not-part-of-a-URL when extending past the host. Commas are
   * allowed inside a URL, but not when they are acting as a separator before another obvious URL.
   */
  private const val URL_CHAR =
    "(?:[^\\s\\u0085\\u00A0\\u1680\\u2000-\\u200D\\u2028\\u2029\\u202A-\\u202F\\u205F\\u2066-\\u2069\\u3000\\uFEFF<>\"'`,;|\\\\]|,(?!https?://|www\\.))"

  /**
   * A single domain label: letter/digit, optional letter/digit/hyphen body. Used for intermediate
   * labels. Note that the TLD validity is enforced separately by [TOP_LEVEL_DOMAINS].
   */
  private const val DOMAIN_LABEL = "[\\p{L}\\p{N}][\\p{L}\\p{N}\\-]*"

  /**
   * Like [DOMAIN_LABEL] but disallows a trailing hyphen, matching RFC 1035. Used in the TLD slot of
   * the bare-domain variant so that text like `signal.com-` doesn't get rejected because the TLD
   * lookup sees `com-` instead of `com`.
   */
  private const val TLD_LABEL = "[\\p{L}\\p{N}](?:[\\p{L}\\p{N}\\-]*[\\p{L}\\p{N}])?"

  /**
   * Match http://..., https://..., www....., or a bare `domain.tld` form.
   * Note that TLD validity is enforced separately by [TOP_LEVEL_DOMAINS].
   */
  private val WEB_URL_PATTERN: Pattern = Pattern.compile(
    "(?i)" +
      "(?:" +
      // Variant 1: explicit scheme (http or https). User intent is unambiguous; no TLD check.
      "https?://" + URL_CHAR + "+" +
      "|" +
      // Variant 2: starts with www. — also unambiguous; no TLD check.
      "www\\." + DOMAIN_LABEL + "(?:\\." + DOMAIN_LABEL + ")+" +
      "(?:[/?#]" + URL_CHAR + "*)?" +
      "|" +
      // Variant 3: bare `domain.tld`. The last label is post-validated against the IANA TLD set.
      DOMAIN_LABEL + "(?:\\." + DOMAIN_LABEL + ")*\\." + TLD_LABEL +
      "(?:[/?#]" + URL_CHAR + "*)?" +
      ")"
  )

  /**
   * Finds all web URLs in [text], in left-to-right order.
   */
  @JvmStatic
  fun findLinks(text: CharSequence): List<DetectedLink> {
    if (text.isEmpty()) {
      return emptyList()
    }

    val matcher = WEB_URL_PATTERN.matcher(text)
    val out = ArrayList<DetectedLink>()
    while (matcher.find()) {
      val start = matcher.start()
      val rawEnd = matcher.end()

      if (!isAtLeadingBoundary(text, start)) {
        continue
      }

      val end = trimTrailingNonUrlChars(text, start, rawEnd)
      if (end <= start) {
        continue
      }

      // Reject if the URL is butted up against a bidi/format/zero-width char on either side —
      // those are used for visual spoofing and shouldn't appear adjacent to a real URL.
      if (end < text.length && isFormatOrZeroWidth(text[end])) {
        continue
      }

      val raw = text.subSequence(start, end).toString()
      if (!isAcceptableCandidate(raw)) {
        continue
      }

      val normalized = if (raw.contains("://")) raw else "http://$raw"
      out.add(DetectedLink(start, end, normalized))
    }
    return out
  }

  private fun isAtLeadingBoundary(text: CharSequence, start: Int): Boolean {
    if (start == 0) {
      return true
    }

    val prev = text[start - 1]

    // Check @ so that we don't pick up the host of an email address
    if (prev.isLetterOrDigit() || prev == '_' || prev == '@') {
      return false
    }

    if (isFormatOrZeroWidth(prev)) {
      return false
    }

    return true
  }

  private fun isFormatOrZeroWidth(c: Char): Boolean {
    return when (c) {
      '​', '‌', '‍', '﻿' -> true // zero-width space / non-joiner / joiner / BOM
      in '‪'..'‮' -> true // bidi formatting overrides
      in '⁦'..'⁩' -> true // bidi isolate controls
      else -> false
    }
  }

  /**
   * Strips trailing characters that are very likely not part of the URL (sentence-ending
   * punctuation, unmatched closing brackets, etc.). Note that '-' is intentionally left alone —
   * that's the bug fix this class is built around.
   */
  private fun trimTrailingNonUrlChars(text: CharSequence, start: Int, initialEnd: Int): Int {
    var end = initialEnd
    while (end > start) {
      val c = text[end - 1]
      if (c in TRAILING_PUNCTUATION_TO_TRIM) {
        end--
        continue
      }

      val opener = CLOSING_BRACKETS[c]
      if (opener != null) {
        val opens = countChar(text, start, end - 1, opener)
        val closes = countChar(text, start, end - 1, c)
        if (closes >= opens) {
          end--
          continue
        }
      }

      break
    }
    return end
  }

  private fun countChar(text: CharSequence, start: Int, end: Int, target: Char): Int {
    var count = 0
    var i = start
    while (i < end) {
      if (text[i] == target) count++
      i++
    }
    return count
  }

  private fun isAcceptableCandidate(candidate: String): Boolean {
    // If the user has specified a scheme, we're much more lenient with url detection,
    // since scheme implies the user really wants it to be a link.
    val schemeIndex = candidate.indexOf("://")
    if (schemeIndex >= 0) {
      val rest = candidate.substring(schemeIndex + 3)
      val host = hostPortion(rest)
      return host.isNotEmpty()
    }

    if (candidate.startsWith("www.", ignoreCase = true)) {
      return true
    }

    val host = hostPortion(candidate)
    if (!host.contains('.')) {
      return false
    }

    val tld = host.substringAfterLast('.')
    return isKnownTld(tld)
  }

  private fun hostPortion(candidate: String): String {
    val end = candidate.indexOfAny(charArrayOf('/', '?', '#'))
    return if (end < 0) candidate else candidate.substring(0, end)
  }

  private fun isKnownTld(tld: String): Boolean {
    if (tld.isEmpty()) {
      return false
    }
    return tld.lowercase() in TOP_LEVEL_DOMAINS
  }

  /** Returns a set containing every entry in [unicode] plus its punycode form (where applicable). */
  private fun withPunycode(unicode: Set<String>): Set<String> {
    val out: MutableSet<String> = HashSet(unicode.size * 2)
    out.addAll(unicode)

    for (tld in unicode) {
      runCatching { IDN.toASCII(tld) }.getOrNull()?.let { out.add(it) }
    }
    return out
  }

  /**
   * A region of text that has been detected as a URL.
   *
   * @property start  Inclusive start offset within the source text.
   * @property end    Exclusive end offset within the source text.
   * @property url    The link target. `http://` is added if no scheme was present in the source.
   */
  data class DetectedLink(
    val start: Int,
    val end: Int,
    val url: String
  ) {
    init {
      require(start in 0..end) { "start=$start, end=$end" }
    }
  }

  /**
   * IANA root top-level domains, used to validate bare-domain candidates.
   *
   * Sourced from the Mozilla Public Suffix List bundled with the JDK
   * (`$JAVA_HOME/lib/security/public_suffix_list.dat`). The literal entries below are unicode
   * forms; the corresponding punycode (`xn--…`) forms are added at init time via [IDN.toASCII] so
   * both spellings of an IDN TLD are accepted.
   */
  private val TOP_LEVEL_DOMAINS: Set<String> = withPunycode(
    setOf(
      "닷넷", "aaa", "aarp", "abb", "abbott", "abbvie", "abc", "able", "abogado", "abudhabi", "ac", "academy",
      "accenture", "accountant", "accountants", "aco", "actor", "ad", "ads", "adult", "ae", "aeg", "aero",
      "aetna", "af", "afl", "africa", "ag", "agakhan", "agency", "ai", "aig", "airbus", "airforce", "airtel",
      "akdn", "al", "alibaba", "alipay", "allfinanz", "allstate", "ally", "alsace", "alstom", "am", "amazon",
      "americanexpress", "americanfamily", "amex", "amfam", "amica", "amsterdam", "analytics", "android",
      "anquan", "anz", "ao", "aol", "apartments", "app", "apple", "aq", "aquarelle", "ar", "arab", "aramco",
      "archi", "army", "arpa", "art", "arte", "as", "asda", "asia", "associates", "at", "athleta", "attorney",
      "au", "auction", "audi", "audible", "audio", "auspost", "author", "auto", "autos", "aw", "aws", "ax",
      "axa", "az", "azure", "ba", "baby", "baidu", "banamex", "band", "bank", "bar", "barcelona", "barclaycard",
      "barclays", "barefoot", "bargains", "baseball", "basketball", "bauhaus", "bayern", "bb", "bbc", "bbt",
      "bbva", "bcg", "bcn", "bd", "be", "beats", "beauty", "beer", "bentley", "berlin", "best", "bestbuy",
      "bet", "bf", "bg", "bh", "bharti", "bi", "bible", "bid", "bike", "bing", "bingo", "bio", "biz", "bj",
      "black", "blackfriday", "blockbuster", "blog", "bloomberg", "blue", "bm", "bms", "bmw", "bn",
      "bnpparibas", "bo", "boats", "boehringer", "bofa", "bom", "bond", "boo", "book", "booking", "bosch",
      "bostik", "boston", "bot", "boutique", "box", "br", "bradesco", "bridgestone", "broadway", "broker",
      "brother", "brussels", "bs", "bt", "build", "builders", "business", "buy", "buzz", "bv", "bw", "by", "bz",
      "bzh", "ca", "cab", "cafe", "cal", "call", "calvinklein", "cam", "camera", "camp", "canon", "capetown",
      "capital", "capitalone", "car", "caravan", "cards", "care", "career", "careers", "cars", "casa", "case",
      "cash", "casino", "cat", "catering", "catholic", "cba", "cbn", "cbre", "cc", "cd", "center", "ceo",
      "cern", "cf", "cfa", "cfd", "cg", "ch", "chanel", "channel", "charity", "chase", "chat", "cheap",
      "chintai", "christmas", "chrome", "church", "ci", "cipriani", "circle", "cisco", "citadel", "citi",
      "citic", "city", "ck", "cl", "claims", "cleaning", "click", "clinic", "clinique", "clothing", "cloud",
      "club", "clubmed", "cm", "cn", "co", "coach", "codes", "coffee", "college", "cologne", "com", "commbank",
      "community", "company", "compare", "computer", "comsec", "condos", "construction", "consulting",
      "contact", "contractors", "cooking", "cool", "coop", "corsica", "country", "coupon", "coupons", "courses",
      "cpa", "cr", "credit", "creditcard", "creditunion", "cricket", "crown", "crs", "cruise", "cruises", "cu",
      "cuisinella", "cv", "cw", "cx", "cy", "cymru", "cyou", "cz", "dabur", "dad", "dance", "data", "date",
      "dating", "datsun", "day", "dclk", "dds", "de", "deal", "dealer", "deals", "degree", "delivery", "dell",
      "deloitte", "delta", "democrat", "dental", "dentist", "desi", "design", "dev", "dhl", "diamonds", "diet",
      "digital", "direct", "directory", "discount", "discover", "dish", "diy", "dj", "dk", "dm", "dnp", "do",
      "docs", "doctor", "dog", "domains", "dot", "download", "drive", "dtv", "dubai", "dunlop", "dupont",
      "durban", "dvag", "dvr", "dz", "earth", "eat", "ec", "eco", "edeka", "edu", "education", "ee", "eg",
      "email", "emerck", "energy", "engineer", "engineering", "enterprises", "epson", "equipment", "er",
      "ericsson", "erni", "es", "esq", "estate", "et", "eu", "eurovision", "eus", "events", "exchange",
      "expert", "exposed", "express", "extraspace", "fage", "fail", "fairwinds", "faith", "family", "fan",
      "fans", "farm", "farmers", "fashion", "fast", "fedex", "feedback", "ferrari", "ferrero", "fi", "fidelity",
      "fido", "film", "final", "finance", "financial", "fire", "firestone", "firmdale", "fish", "fishing",
      "fit", "fitness", "fj", "fk", "flickr", "flights", "flir", "florist", "flowers", "fly", "fm", "fo", "foo",
      "food", "football", "ford", "forex", "forsale", "forum", "foundation", "fox", "fr", "free", "fresenius",
      "frl", "frogans", "frontier", "ftr", "fujitsu", "fun", "fund", "furniture", "futbol", "fyi", "ga", "gal",
      "gallery", "gallo", "gallup", "game", "games", "gap", "garden", "gay", "gb", "gbiz", "gd", "gdn", "ge",
      "gea", "gent", "genting", "george", "gf", "gg", "ggee", "gh", "gi", "gift", "gifts", "gives", "giving",
      "gl", "glass", "gle", "global", "globo", "gm", "gmail", "gmbh", "gmo", "gmx", "gn", "godaddy", "gold",
      "goldpoint", "golf", "goo", "goodyear", "goog", "google", "gop", "got", "gov", "gp", "gq", "gr",
      "grainger", "graphics", "gratis", "green", "gripe", "grocery", "group", "gs", "gt", "gu", "gucci", "guge",
      "guide", "guitars", "guru", "gw", "gy", "hair", "hamburg", "hangout", "haus", "hbo", "hdfc", "hdfcbank",
      "health", "healthcare", "help", "helsinki", "here", "hermes", "hiphop", "hisamitsu", "hitachi", "hiv",
      "hk", "hkt", "hm", "hn", "hockey", "holdings", "holiday", "homedepot", "homegoods", "homes", "homesense",
      "honda", "horse", "hospital", "host", "hosting", "hot", "hotels", "hotmail", "house", "how", "hr", "hsbc",
      "ht", "hu", "hughes", "hyatt", "hyundai", "ibm", "icbc", "ice", "icu", "id", "ie", "ieee", "ifm", "ikano",
      "il", "im", "imamat", "imdb", "immo", "immobilien", "in", "inc", "industries", "infiniti", "info", "ing",
      "ink", "institute", "insurance", "insure", "int", "international", "intuit", "investments", "io",
      "ipiranga", "iq", "ir", "irish", "is", "ismaili", "ist", "istanbul", "it", "itau", "itv", "jaguar",
      "java", "jcb", "je", "jeep", "jetzt", "jewelry", "jio", "jll", "jm", "jmp", "jnj", "jo", "jobs", "joburg",
      "jot", "joy", "jp", "jpmorgan", "jprs", "juegos", "juniper", "kaufen", "kddi", "ke", "kerryhotels",
      "kerrylogistics", "kerryproperties", "kfh", "kg", "kh", "ki", "kia", "kids", "kim", "kindle", "kitchen",
      "kiwi", "km", "kn", "koeln", "komatsu", "kosher", "kp", "kpmg", "kpn", "kr", "krd", "kred", "kuokgroup",
      "kw", "ky", "kyoto", "kz", "la", "lacaixa", "lamborghini", "lamer", "lancaster", "land", "landrover",
      "lanxess", "lasalle", "lat", "latino", "latrobe", "law", "lawyer", "lb", "lc", "lds", "lease", "leclerc",
      "lefrak", "legal", "lego", "lexus", "lgbt", "li", "lidl", "life", "lifeinsurance", "lifestyle",
      "lighting", "like", "lilly", "limited", "limo", "lincoln", "link", "lipsy", "live", "living", "lk", "llc",
      "llp", "loan", "loans", "locker", "locus", "lol", "london", "lotte", "lotto", "love", "lpl",
      "lplfinancial", "lr", "ls", "lt", "ltd", "ltda", "lu", "lundbeck", "luxe", "luxury", "lv", "ly", "ma",
      "madrid", "maif", "maison", "makeup", "man", "management", "mango", "map", "market", "marketing",
      "markets", "marriott", "marshalls", "mattel", "mba", "mc", "mckinsey", "md", "me", "med", "media", "meet",
      "melbourne", "meme", "memorial", "men", "menu", "merckmsd", "mg", "mh", "miami", "microsoft", "mil",
      "mini", "mint", "mit", "mitsubishi", "mk", "ml", "mlb", "mls", "mm", "mma", "mn", "mo", "mobi", "mobile",
      "moda", "moe", "moi", "mom", "monash", "money", "monster", "mormon", "mortgage", "moscow", "moto",
      "motorcycles", "mov", "movie", "mp", "mq", "mr", "ms", "msd", "mt", "mtn", "mtr", "mu", "museum", "music",
      "mv", "mw", "mx", "my", "mz", "na", "nab", "nagoya", "name", "natura", "navy", "nba", "nc", "ne", "nec",
      "net", "netbank", "netflix", "network", "neustar", "new", "news", "next", "nextdirect", "nexus", "nf",
      "nfl", "ng", "ngo", "nhk", "ni", "nico", "nike", "nikon", "ninja", "nissan", "nissay", "nl", "no",
      "nokia", "norton", "now", "nowruz", "nowtv", "np", "nr", "nra", "nrw", "ntt", "nu", "nyc", "nz", "obi",
      "observer", "office", "okinawa", "olayan", "olayangroup", "ollo", "om", "omega", "one", "ong", "onion",
      "onl", "online", "ooo", "open", "oracle", "orange", "org", "organic", "origins", "osaka", "otsuka", "ott",
      "ovh", "pa", "page", "panasonic", "paris", "pars", "partners", "parts", "party", "pay", "pccw", "pe",
      "pet", "pf", "pfizer", "pg", "ph", "pharmacy", "phd", "philips", "phone", "photo", "photography",
      "photos", "physio", "pics", "pictet", "pictures", "pid", "pin", "ping", "pink", "pioneer", "pizza", "pk",
      "pl", "place", "play", "playstation", "plumbing", "plus", "pm", "pn", "pnc", "pohl", "poker", "politie",
      "porn", "post", "pr", "pramerica", "praxi", "press", "prime", "pro", "prod", "productions", "prof",
      "progressive", "promo", "properties", "property", "protection", "pru", "prudential", "ps", "pt", "pub",
      "pw", "pwc", "py", "qa", "qpon", "quebec", "quest", "racing", "radio", "re", "read", "realestate",
      "realtor", "realty", "recipes", "red", "redstone", "redumbrella", "rehab", "reise", "reisen", "reit",
      "reliance", "ren", "rent", "rentals", "repair", "report", "republican", "rest", "restaurant", "review",
      "reviews", "rexroth", "rich", "richardli", "ricoh", "ril", "rio", "rip", "ro", "rocks", "rodeo", "rogers",
      "room", "rs", "rsvp", "ru", "rugby", "ruhr", "run", "rw", "rwe", "ryukyu", "sa", "saarland", "safe",
      "safety", "sakura", "sale", "salon", "samsclub", "samsung", "sandvik", "sandvikcoromant", "sanofi", "sap",
      "sarl", "sas", "save", "saxo", "sb", "sbi", "sbs", "sc", "scb", "schaeffler", "schmidt", "scholarships",
      "school", "schule", "schwarz", "science", "scot", "sd", "se", "search", "seat", "secure", "security",
      "seek", "select", "sener", "services", "seven", "sew", "sex", "sexy", "sfr", "sg", "sh", "shangrila",
      "sharp", "shaw", "shell", "shia", "shiksha", "shoes", "shop", "shopping", "shouji", "show", "si", "silk",
      "sina", "singles", "site", "sj", "sk", "ski", "skin", "sky", "skype", "sl", "sling", "sm", "smart",
      "smile", "sn", "sncf", "so", "soccer", "social", "softbank", "software", "sohu", "solar", "solutions",
      "song", "sony", "soy", "spa", "space", "sport", "spot", "sr", "srl", "ss", "st", "stada", "staples",
      "star", "statebank", "statefarm", "stc", "stcgroup", "stockholm", "storage", "store", "stream", "studio",
      "study", "style", "su", "sucks", "supplies", "supply", "support", "surf", "surgery", "suzuki", "sv",
      "swatch", "swiss", "sx", "sy", "sydney", "systems", "sz", "tab", "taipei", "talk", "taobao", "target",
      "tatamotors", "tatar", "tattoo", "tax", "taxi", "tc", "tci", "td", "tdk", "team", "tech", "technology",
      "tel", "temasek", "tennis", "teva", "tf", "tg", "th", "thd", "theater", "theatre", "tiaa", "tickets",
      "tienda", "tips", "tires", "tirol", "tj", "tjmaxx", "tjx", "tk", "tkmaxx", "tl", "tm", "tmall", "tn",
      "to", "today", "tokyo", "tools", "top", "toray", "toshiba", "total", "tours", "town", "toyota", "toys",
      "tr", "trade", "trading", "training", "travel", "travelers", "travelersinsurance", "trust", "trv", "tt",
      "tube", "tui", "tunes", "tushu", "tv", "tvs", "tw", "tz", "ua", "ubank", "ubs", "ug", "uk", "unicom",
      "university", "uno", "uol", "ups", "us", "uy", "uz", "va", "vacations", "vana", "vanguard", "vc", "ve",
      "vegas", "ventures", "verisign", "vermögensberater", "vermögensberatung", "versicherung", "vet", "vg",
      "vi", "viajes", "video", "vig", "viking", "villas", "vin", "vip", "virgin", "visa", "vision", "viva",
      "vivo", "vlaanderen", "vn", "vodka", "volvo", "vote", "voting", "voto", "voyage", "vu", "wales",
      "walmart", "walter", "wang", "wanggou", "watch", "watches", "weather", "weatherchannel", "webcam",
      "weber", "website", "wed", "wedding", "weibo", "weir", "wf", "whoswho", "wien", "wiki", "williamhill",
      "win", "windows", "wine", "winners", "wme", "wolterskluwer", "woodside", "work", "works", "world", "wow",
      "ws", "wtc", "wtf", "xbox", "xerox", "xihuan", "xin", "xxx", "xyz", "yachts", "yahoo", "yamaxun",
      "yandex", "ye", "yodobashi", "yoga", "yokohama", "you", "youtube", "yt", "yun", "za", "zappos", "zara",
      "zero", "zip", "zm", "zone", "zuerich", "zw", "ελ", "ευ", "бг", "бел", "дети", "ею",
      "католик", "ком", "қаз", "мкд", "мон", "москва", "онлайн", "орг",
      "рус", "рф", "сайт", "срб", "укр", "გე", "հայ", "ישראל", "קום",
      "ابوظبي", "ارامكو", "الاردن", "البحرين", "الجزائر", "السعودية",
      "السعوديه", "السعودیة", "السعودیۃ", "العليان", "المغرب",
      "اليمن", "امارات", "ايران", "ایران", "بارت", "بازار", "بھارت",
      "بيتك", "پاكستان", "پاکستان", "ڀارت", "تونس", "سودان", "سوريا",
      "سورية", "شبكة", "عراق", "عرب", "عمان", "فلسطين", "قطر", "كاثوليك",
      "كوم", "مصر", "مليسيا", "موريتانيا", "موقع", "همراه", "कॉम",
      "नेट", "भारत", "भारतम्", "भारोत", "संगठन",
      "বাংলা", "ভারত", "ভাৰত", "ਭਾਰਤ", "ભારત", "ଭାରତ",
      "இந்தியா", "இலங்கை", "சிங்கப்பூர்", "భారత్",
      "ಭಾರತ", "ഭാരതം", "ලංකා", "คอม", "ไทย", "ລາວ", "アマゾン",
      "グーグル", "クラウド", "コム", "ストア", "セール", "ファッション", "ポイント",
      "みんな", "世界", "中信", "中国", "中國", "中文网", "亚马逊", "企业", "佛山",
      "信息", "健康", "八卦", "公司", "公益", "台湾", "台灣", "商城", "商店", "商标",
      "嘉里", "嘉里大酒店", "在线", "大拿", "天主教", "娱乐", "家電", "广东", "微博",
      "慈善", "我爱你", "手机", "招聘", "政务", "政府", "新加坡", "新闻", "时尚", "書籍",
      "机构", "淡马锡", "游戏", "澳門", "澳门", "点看", "移动", "组织机构", "网址",
      "网店", "网站", "网络", "联通", "臺灣", "谷歌", "购物", "通販", "集团", "電訊盈科",
      "飞利浦", "食品", "餐厅", "香格里拉", "香港"
    )
  )
}

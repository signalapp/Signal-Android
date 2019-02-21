package org.thoughtcrime.securesms.linkpreview;

import java.util.regex.Pattern;

public class LegalUrlPatterns {

  public static final Pattern LATIN    = Pattern.compile("[" +
                                                         "\\x{0041}-\\x{005A}" +
                                                         "\\x{0061}-\\x{007A}" +
                                                         "\\x{00AA}"        +
                                                         "\\x{00BA}"        +
                                                         "\\x{00C0}-\\x{00DC}" +
                                                         "\\x{00D8}-\\x{00F6}" +
                                                         "\\x{00F8}-\\x{01BA}" +
                                                         "]");

  public static final Pattern CYRILLIC = Pattern.compile("[" +
                                                          "\\x{0400}-\\x{0481}" +
                                                          "\\x{0482}"        +
                                                          "\\x{0483}-\\x{0484}" +
                                                          "\\x{0487}"        +
                                                          "\\x{0488}-\\x{0489}" +
                                                          "\\x{048A}-\\x{052F}" +
                                                          "\\x{1C80}-\\x{1C88}" +
                                                          "\\x{1D2B}"        +
                                                          "\\x{1D78}"        +
                                                          "\\x{2DE0}-\\x{2DFF}" +
                                                          "\\x{A640}-\\x{A66D}" +
                                                          "\\x{A66E}"        +
                                                          "\\x{A66F}"        +
                                                          "\\x{A670}-\\x{A672}" +
                                                          "\\x{A673}"        +
                                                          "\\x{A674}-\\x{A67D}" +
                                                          "\\x{A67E}"        +
                                                          "\\x{A67F}"        +
                                                          "\\x{A680}-\\x{A69B}" +
                                                          "\\x{A69C}-\\x{A69D}" +
                                                          "\\x{A69E}-\\x{A69F}" +
                                                          "\\x{FE2E}-\\x{FE2F}" +
                                                          "]");

  public static final Pattern GREEK     = Pattern.compile("[" +
                                                          "\\x{0370}-\\x{0373}" +
                                                          "\\x{0375}" +
                                                          "\\x{0376}-\\x{0377}" +
                                                          "\\x{037A}" +
                                                          "\\x{037B}-\\x{037D}" +
                                                          "\\x{037F}" +
                                                          "\\x{0384}" +
                                                          "\\x{0386}" +
                                                          "\\x{0388}-\\x{038A}" +
                                                          "\\x{038C}" +
                                                          "\\x{038E}-\\x{03A1}" +
                                                          "\\x{03A3}-\\x{03E1}" +
                                                          "\\x{03F0}-\\x{03F5}" +
                                                          "\\x{03F6}" +
                                                          "\\x{03F7}-\\x{03FF}" +
                                                          "\\x{1D26}-\\x{1D2A}" +
                                                          "\\x{1D5D}-\\x{1D61}" +
                                                          "\\x{1D66}-\\x{1D6A}" +
                                                          "\\x{1DBF}" +
                                                          "\\x{1F00}-\\x{1F15}" +
                                                          "\\x{1F18}-\\x{1F1D}" +
                                                          "\\x{1F20}-\\x{1F45}" +
                                                          "\\x{1F48}-\\x{1F4D}" +
                                                          "\\x{1F50}-\\x{1F57}" +
                                                          "\\x{1F59}" +
                                                          "\\x{1F5B}" +
                                                          "\\x{1F5D}" +
                                                          "\\x{1F5F}-\\x{1F7D}" +
                                                          "\\x{1F80}-\\x{1FB4}" +
                                                          "\\x{1FB6}-\\x{1FBC}" +
                                                          "\\x{1FBD}" +
                                                          "\\x{1FBE}" +
                                                          "\\x{1FBF}-\\x{1FC1}" +
                                                          "\\x{1FC2}-\\x{1FC4}" +
                                                          "\\x{1FC6}-\\x{1FCC}" +
                                                          "\\x{1FCD}-\\x{1FCF}" +
                                                          "\\x{1FD0}-\\x{1FD3}" +
                                                          "\\x{1FD6}-\\x{1FDB}" +
                                                          "\\x{1FDD}-\\x{1FDF}" +
                                                          "\\x{1FE0}-\\x{1FEC}" +
                                                          "\\x{1FED}-\\x{1FEF}" +
                                                          "\\x{1FF2}-\\x{1FF4}" +
                                                          "\\x{1FF6}-\\x{1FFC}" +
                                                          "\\x{1FFD}-\\x{1FFE}" +
                                                          "\\x{2126}" +
                                                          "\\x{AB65}" +
                                                          "\\x{10140}-\\x{10174}"+
                                                          "\\x{10175}-\\x{10178}"+
                                                          "\\x{10179}-\\x{10189}"+
                                                          "\\x{1018A}-\\x{1018B}"+
                                                          "\\x{1018C}-\\x{1018E}"+
                                                          "\\x{101A0}"+
                                                          "\\x{1D200}-\\x{1D241}"+
                                                          "\\x{1D242}-\\x{1D244}"+
                                                          "\\x{1D245}"+
                                                          "]");
}

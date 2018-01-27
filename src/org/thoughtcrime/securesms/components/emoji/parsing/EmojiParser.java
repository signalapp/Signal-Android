/**
 * Copyright (c) 2014-present Vincent DURMONT vdurmont@gmail.com
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.thoughtcrime.securesms.components.emoji.parsing;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Based in part on code from emoji-java
 */
public class EmojiParser {

  private final EmojiTree emojiTree;

  public EmojiParser(EmojiTree emojiTree) {
    this.emojiTree = emojiTree;
  }

  public @NonNull CandidateList findCandidates(@Nullable CharSequence text) {
    List<Candidate> results = new LinkedList<>();

    if (text == null) return new CandidateList(results, false);

    boolean allEmojis = text.length() > 0;

    for (int i = 0; i < text.length(); i++) {
      int emojiEnd = getEmojiEndPos(text, i);

      if (emojiEnd != -1) {
        EmojiDrawInfo drawInfo = emojiTree.getEmoji(text, i, emojiEnd);

        if (emojiEnd + 2 <= text.length()) {
          if (Fitzpatrick.fitzpatrickFromUnicode(text, emojiEnd) != null) {
            emojiEnd += 2;
          }
        }

        results.add(new Candidate(i, emojiEnd, drawInfo));

        i = emojiEnd - 1;
      } else {
        allEmojis = false;
      }
    }

    return new CandidateList(results, allEmojis);
  }

  private int getEmojiEndPos(CharSequence text, int startPos) {
    int best = -1;

    for (int j = startPos + 1; j <= text.length(); j++) {
      EmojiTree.Matches status = emojiTree.isEmoji(text, startPos, j);

      if (status.exactMatch()) {
        best = j;
      } else if (status.impossibleMatch()) {
        return best;
      }
    }

    return best;
  }

  public static class Candidate {

    private final int           startIndex;
    private final int           endIndex;
    private final EmojiDrawInfo drawInfo;

    Candidate(int startIndex, int endIndex, EmojiDrawInfo drawInfo) {
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      this.drawInfo = drawInfo;
    }

    public EmojiDrawInfo getDrawInfo() {
      return drawInfo;
    }

    public int getEndIndex() {
      return endIndex;
    }

    public int getStartIndex() {
      return startIndex;
    }
  }

  public static class CandidateList implements Iterable<Candidate> {
    public final List<EmojiParser.Candidate> list;
    public final boolean                     allEmojis;

    public CandidateList(List<EmojiParser.Candidate> candidates, boolean allEmojis) {
      this.list = candidates;
      this.allEmojis = allEmojis;
    }

    public int size() {
      return list.size();
    }

    @Override
    public Iterator<Candidate> iterator() {
      return list.iterator();
    }
  }

}

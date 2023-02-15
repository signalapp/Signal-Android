package org.thoughtcrime.securesms.fonts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TextToScriptTest(
  private val texts: List<CharSequence>,
  private val guess: SupportedScript
) {

  @Test
  fun guessScript() {
    for (text in texts) {
      assertEquals("Expecting $guess for $text", guess, TextToScript.guessScript(text))
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: guessLocale(..) = {1}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      arrayOf(
        listOf("What’s up?", "I really appreciate it.", "How wonderful!", "Let’s grab a bite to eat.", "I’m gonna hit the sack.", "More latin than other Прошу"),
        SupportedScript.LATIN
      ),
      arrayOf(
        listOf("Guten Tag", "Sprechen Sie Englisch?", "Gut, danke", "Gibt es ein Restaurant in der Nähe?", "Haben Sie noch Zimmer frei?", "Haben Sie noch Zimmer frei? Прошу"),
        SupportedScript.LATIN
      ),
      arrayOf(
        listOf("Bom Dia", "Prazer", "Qual é o seu nome?", "Como vai?", "Que horas são?", "Que horas são? Прошу"),
        SupportedScript.LATIN
      ),
      arrayOf(
        listOf("Bună ziua.", "Cum te numeşti?", " Îmi pare rău.", "Unde este toaleta?", "Felicitări!", "Multumesc pentru tot ajutorul Clauz", "Felicitări! Прошу"),
        SupportedScript.LATIN
      ),
      arrayOf(
        listOf("добрий день", "добрий день", "привіт", "дякую", "дякую", "будь ласка", "дуже добре", "Вибачте", "Так", "па-па", "Ви говорите англійською?", "Вибачте abc"),
        SupportedScript.CYRILLIC
      ),
      arrayOf(
        listOf("Да", "Нет", "Пожалуйста", "Спасибо", "Не за что.", "на здоровье", "Прошу прощения.", "Извините.", "Я не понимаю.", "Я не говорю по-Русски.", "Я не понимаю. abc"),
        SupportedScript.CYRILLIC
      ),
      arrayOf(
        listOf("स्वागत", "नमस्ते", "तुम कैसे हो?", "मैं अच्छा हूँ, धन्यवाद। और तुम?", "तुम कहाँ से (आए) हो?", "शुभ रात्रि", "धन्यवाद", "धन्यवाद abc"),
        SupportedScript.DEVANAGARI
      ),
      arrayOf(
        listOf("食咗飯未呀?", "唔該", "多謝", "太貴啦", "好味", "我花生過敏", "地鐵站係邊", "廁所係邊", "無問題", "無問題a"),
        SupportedScript.UNKNOWN_CJK
      ),
      arrayOf(
        listOf("你好嗎", "很高興認識你", "我不會說漢語", "我需要你的幫助", "我丟了手提包", "我丟了手提包abc"),
        SupportedScript.UNKNOWN_CJK
      ),
      arrayOf(
        listOf("你好", "请问", "你会说英语吗", "我听不懂", "洗手间在哪里", "我可以用您的手机吗", "我可以用您的手机吗abcd"),
        SupportedScript.UNKNOWN_CJK
      ),

      arrayOf(
        listOf("はい", "いいえ", "こんばんは", "えいごをはなせますか", "おあいできて　うれしいです", "もっと　ゆっくりはなしてください", "だいじょうぶです", "わかります", "だいじょうぶです123"),
        SupportedScript.JAPANESE
      ),
      arrayOf(
        listOf("ما اسمك؟", "كيف حالك؟", "انا آسف", "أين الحمام؟", "هل يمكنك التحدث بشكل أبطأ من فضلك؟", "مندواعي سروري مقابلتك", "كيفتجري الامور؟", "أنا تائه.", "أنا تائه.12"),
        SupportedScript.ARABIC
      ),
      arrayOf(
        listOf("ППППaaaa", ""),
        SupportedScript.LATIN
      )
    )
  }
}

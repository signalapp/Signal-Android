package org.thoughtcrime.securesms.conversation.ui.inlinequery

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.adapter.mapping.AnyMappingModel

/**
 * Activity (at least) scope view model for managing inline queries. The view model needs to be larger scope so it can
 * be shared between the fragment requesting the search and the instance of [InlineQueryResultsFragment] used for displaying
 * the results.
 */
class InlineQueryViewModel(
  private val emojiSearchRepository: EmojiSearchRepository = EmojiSearchRepository(AppDependencies.application),
  private val recentEmojis: RecentEmojiPageModel = RecentEmojiPageModel(AppDependencies.application, TextSecurePreferences.RECENT_STORAGE_KEY)
) : ViewModel() {

  private val querySubject: PublishSubject<InlineQuery> = PublishSubject.create()
  private val selectionSubject: PublishSubject<InlineQueryReplacement> = PublishSubject.create()

  val results: Observable<List<AnyMappingModel>>
  val selection: Observable<InlineQueryReplacement> = selectionSubject

  init {
    results = querySubject.switchMap { query ->
      when (query) {
        is InlineQuery.Emoji -> queryEmoji(query)
        is InlineQuery.Mention -> Observable.just(emptyList())
        InlineQuery.NoQuery -> Observable.just(emptyList())
      }
    }.subscribeOn(Schedulers.io())
  }

  fun onQueryChange(inlineQuery: InlineQuery) {
    querySubject.onNext(inlineQuery)
  }

  private fun queryEmoji(query: InlineQuery.Emoji): Observable<List<AnyMappingModel>> {
    return emojiSearchRepository
      .submitQuery(query.query)
      .map { r -> toMappingModels(r) }
      .toObservable()
  }

  fun onSelection(model: AnyMappingModel) {
    when (model) {
      is InlineQueryEmojiResult.Model -> {
        recentEmojis.onCodePointSelected(model.preferredEmoji)
        selectionSubject.onNext(InlineQueryReplacement.Emoji(model.preferredEmoji))
      }
    }
  }

  companion object {
    fun toMappingModels(emojiWithLabels: List<String>): List<AnyMappingModel> {
      val emojiValues = SignalStore.emoji
      return emojiWithLabels
        .distinct()
        .map { emoji ->
          InlineQueryEmojiResult.Model(
            canonicalEmoji = emoji,
            preferredEmoji = emojiValues.getPreferredVariation(emoji)
          )
        }
    }
  }
}

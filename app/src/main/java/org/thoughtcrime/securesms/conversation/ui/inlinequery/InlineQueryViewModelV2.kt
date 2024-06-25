package org.thoughtcrime.securesms.conversation.ui.inlinequery

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionViewState
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerRepositoryV2
import org.thoughtcrime.securesms.conversation.v2.ConversationRecipientRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.adapter.mapping.AnyMappingModel

/**
 * Activity (at least) scope view model for managing inline queries. The view model needs to be larger scope so it can
 * be shared between the fragment requesting the search and the fragment used for displaying the results.
 */
class InlineQueryViewModelV2(
  private val recipientRepository: ConversationRecipientRepository,
  private val mentionsPickerRepository: MentionsPickerRepositoryV2 = MentionsPickerRepositoryV2(),
  private val emojiSearchRepository: EmojiSearchRepository = EmojiSearchRepository(AppDependencies.application),
  private val recentEmojis: RecentEmojiPageModel = RecentEmojiPageModel(AppDependencies.application, TextSecurePreferences.RECENT_STORAGE_KEY)
) : ViewModel() {

  private val querySubject: PublishSubject<InlineQuery> = PublishSubject.create()
  private val selectionSubject: PublishSubject<InlineQueryReplacement> = PublishSubject.create()
  private val isMentionsShowingSubject: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(false)

  val results: Observable<Results>
  val selection: Observable<InlineQueryReplacement> = selectionSubject.observeOn(AndroidSchedulers.mainThread())
  val isMentionsShowing: Observable<Boolean> = isMentionsShowingSubject.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread())

  init {
    results = querySubject.switchMap { query ->
      when (query) {
        is InlineQuery.Emoji -> queryEmoji(query)
        is InlineQuery.Mention -> queryMentions(query)
        InlineQuery.NoQuery -> Observable.just(None)
      }
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun onQueryChange(inlineQuery: InlineQuery) {
    querySubject.onNext(inlineQuery)
  }

  private fun queryEmoji(query: InlineQuery.Emoji): Observable<Results> {
    return emojiSearchRepository
      .submitQuery(query.query)
      .map { r -> if (r.isEmpty()) None else EmojiResults(toMappingModels(r)) }
      .toObservable()
  }

  private fun queryMentions(query: InlineQuery.Mention): Observable<Results> {
    return recipientRepository
      .groupRecord
      .take(1)
      .switchMap { group ->
        if (group.isPresent) {
          mentionsPickerRepository.search(query.query, group.get().members)
            .map { results -> if (results.isEmpty()) None else MentionResults(results.map { MentionViewState(it) }) }
            .toObservable()
        } else {
          Observable.just(None)
        }
      }
  }

  fun onSelection(model: AnyMappingModel) {
    when (model) {
      is InlineQueryEmojiResult.Model -> {
        recentEmojis.onCodePointSelected(model.preferredEmoji)
        selectionSubject.onNext(InlineQueryReplacement.Emoji(model.preferredEmoji))
      }
      is MentionViewState -> {
        selectionSubject.onNext(InlineQueryReplacement.Mention(model.recipient))
      }
    }
  }

  fun setIsMentionsShowing(showing: Boolean) {
    isMentionsShowingSubject.onNext(showing)
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

  sealed interface Results
  object None : Results
  data class EmojiResults(val results: List<AnyMappingModel>) : Results
  data class MentionResults(val results: List<AnyMappingModel>) : Results
}

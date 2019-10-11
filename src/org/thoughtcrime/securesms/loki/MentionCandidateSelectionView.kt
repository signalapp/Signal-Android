package org.thoughtcrime.securesms.loki

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.whispersystems.signalservice.loki.messaging.Mention

class MentionCandidateSelectionView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : ListView(context, attrs, defStyleAttr) {
    private var mentionCandidates = listOf<Mention>()
        set(newValue) { field = newValue; mentionCandidateSelectionViewAdapter.mentionCandidates = newValue }
    private var hasGroupContext = false
    var onMentionCandidateSelected: ((Mention) -> Unit)? = null

    private val mentionCandidateSelectionViewAdapter by lazy { Adapter(context) }

    private class Adapter(private val context: Context) : BaseAdapter() {
        var mentionCandidates = listOf<Mention>()
            set(newValue) { field = newValue; notifyDataSetChanged() }
        var hasGroupContext = false

        override fun getCount(): Int {
            return mentionCandidates.count()
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItem(position: Int): Mention {
            return mentionCandidates[position]
        }

        override fun getView(position: Int, cellToBeReused: View?, parent: ViewGroup): View {
            val cell = cellToBeReused as MentionCandidateSelectionViewCell? ?: MentionCandidateSelectionViewCell.inflate(LayoutInflater.from(context), parent)
            val mentionCandidate = getItem(position)
            cell.mentionCandidate = mentionCandidate
            cell.hasGroupContext = hasGroupContext
            return cell
        }
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        adapter = mentionCandidateSelectionViewAdapter
        mentionCandidateSelectionViewAdapter.mentionCandidates = mentionCandidates
        setOnItemClickListener { _, _, position, _ ->
            onMentionCandidateSelected?.invoke(mentionCandidates[position])
        }
    }

    fun show(mentionCandidates: List<Mention>, threadID: Long) {
        hasGroupContext = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadID)!!.isGroupRecipient
        this.mentionCandidates = mentionCandidates
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = toPx(6 + Math.min(mentionCandidates.count(), 4) * 52, resources)
        this.layoutParams = layoutParams
    }

    fun hide() {
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = 0
        this.layoutParams = layoutParams
    }
}
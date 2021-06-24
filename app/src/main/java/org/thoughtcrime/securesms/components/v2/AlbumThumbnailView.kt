package org.thoughtcrime.securesms.components.v2

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.album_thumbnail_view.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.components.CornerMask
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests

class AlbumThumbnailView: FrameLayout {

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private val albumCellContainer by lazy { album_cell_container }
    private lateinit var cornerMask: CornerMask

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.album_thumbnail_view, this)
        cornerMask = CornerMask(this)
        cornerMask.setRadius(80)
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        cornerMask.mask(canvas)
    }

    // endregion

    // region Interaction

    fun bind(glideRequests: GlideRequests, message: MmsMessageRecord, isStart: Boolean, isEnd: Boolean) {
        // TODO: optimize for same size
        val slides = message.slideDeck.thumbnailSlides
        if (slides.isEmpty()) {
            // this should never be encountered because it's checked by parent
            return
        }
        calculateRadius(isStart, isEnd, message.isOutgoing)
        albumCellContainer.removeAllViews()
        LayoutInflater.from(context).inflate(layoutRes(slides.size), albumCellContainer)
        // iterate
        slides.take(5).forEachIndexed { position, slide ->
            getThumbnailView(position).setImageResource(glideRequests, slide, showControls = false, isPreview = false)
        }
    }

    // endregion


    fun layoutRes(slideCount: Int) = when (slideCount) {
        1 -> R.layout.album_thumbnail_1 // single
        2 -> R.layout.album_thumbnail_2// two sidebyside
        3 -> R.layout.album_thumbnail_3// three stacked
        4 -> R.layout.album_thumbnail_4// four square
        5 -> R.layout.album_thumbnail_5//
        else -> R.layout.album_thumbnail_many// five or more
    }

    fun getThumbnailView(position: Int): ThumbnailView = when (position) {
        0 -> albumCellContainer.findViewById<ViewGroup>(R.id.album_cell_container).findViewById(R.id.album_cell_1)
        1 -> albumCellContainer.findViewById<ViewGroup>(R.id.album_cell_container).findViewById(R.id.album_cell_2)
        2 -> albumCellContainer.findViewById<ViewGroup>(R.id.album_cell_container).findViewById(R.id.album_cell_3)
        3 -> albumCellContainer.findViewById<ViewGroup>(R.id.album_cell_container).findViewById(R.id.album_cell_4)
        4 -> albumCellContainer.findViewById<ViewGroup>(R.id.album_cell_container).findViewById(R.id.album_cell_5)
        else -> throw Exception("Can't get thumbnail view for non-existent thumbnail at position: $position")
    }

    fun calculateRadius(isStart: Boolean, isEnd: Boolean, outgoing: Boolean) {
        val roundedDimen = context.resources.getDimension(R.dimen.message_corner_radius).toInt()
        val collapsedDimen = context.resources.getDimension(R.dimen.message_corner_collapse_radius).toInt()
        val (startTop, endTop, startBottom, endBottom) = when {
            // single message, consistent dimen
            isStart && isEnd -> intArrayOf(roundedDimen, roundedDimen, roundedDimen, roundedDimen)
            // start of message cluster, collapsed BL
            isStart -> intArrayOf(roundedDimen, roundedDimen, collapsedDimen, roundedDimen)
            // end of message cluster, collapsed TL
            isEnd -> intArrayOf(collapsedDimen, roundedDimen, roundedDimen, roundedDimen)
            // else in the middle, no rounding left side
            else -> intArrayOf(collapsedDimen, roundedDimen, collapsedDimen, roundedDimen)
        }
        // TL, TR, BR, BL (CW direction)
        cornerMask.setRadii(
                if (!outgoing) startTop else endTop, // TL
                if (!outgoing) endTop else startTop, // TR
                if (!outgoing) endBottom else startBottom, // BR
                if (!outgoing) startBottom else endBottom // BL
        )
    }

}
package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_path.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.loki.utilities.animateSizeChange
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.whispersystems.signalservice.loki.api.onionrequests.OnionRequestAPI
import org.whispersystems.signalservice.loki.api.onionrequests.Snode

class PathActivity : PassphraseRequiredActionBarActivity() {

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_path)
        supportActionBar!!.title = resources.getString(R.string.activity_path_title)
        val youRow = getPathRow("You", null, LineView.Location.Top, 1000, 4000)
        val path = OnionRequestAPI.paths.firstOrNull() ?: return finish()
        val pathRows = path.mapIndexed { index, snode ->
            val isGuardSnode = (OnionRequestAPI.guardSnodes.contains(snode))
            getPathRow(snode, LineView.Location.Middle, index.toLong() * 1000 + 2000, 4000, isGuardSnode)
        }
        val destinationRow = getPathRow("Destination", null, LineView.Location.Bottom, 5000, 4000)
        pathRowsContainer.addView(youRow)
        for (pathRow in pathRows) {
            pathRowsContainer.addView(pathRow)
        }
        pathRowsContainer.addView(destinationRow)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_path, menu)
        return true
    }
    // endregion

    // region General
    private fun getPathRow(title: String, subtitle: String?, location: LineView.Location, dotAnimationStartDelay: Long, dotAnimationRepeatInterval: Long): LinearLayout {
        val mainContainer = LinearLayout(this)
        mainContainer.orientation = LinearLayout.HORIZONTAL
        mainContainer.gravity = Gravity.CENTER_VERTICAL
        val mainContainerLayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        mainContainer.layoutParams = mainContainerLayoutParams
        val lineView = LineView(this, location, dotAnimationStartDelay, dotAnimationRepeatInterval)
        val lineViewLayoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(R.dimen.path_row_expanded_dot_size), resources.getDimensionPixelSize(R.dimen.path_row_height))
        lineView.layoutParams = lineViewLayoutParams
        mainContainer.addView(lineView)
        val titleTextView = TextView(this)
        titleTextView.setTextColor(resources.getColorWithID(R.color.text, theme))
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.medium_font_size))
        titleTextView.text = title
        val titleContainer = LinearLayout(this)
        titleContainer.orientation = LinearLayout.VERTICAL
        titleContainer.addView(titleTextView)
        val titleContainerLayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        titleContainerLayoutParams.marginStart = resources.getDimensionPixelSize(R.dimen.large_spacing)
        titleContainer.layoutParams = titleContainerLayoutParams
        mainContainer.addView(titleContainer)
        if (subtitle != null) {
            val subtitleTextView = TextView(this)
            subtitleTextView.setTextColor(resources.getColorWithID(R.color.text, theme))
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.small_font_size))
            subtitleTextView.text = subtitle
            titleContainer.addView(subtitleTextView)
        }
        return mainContainer
    }

    private fun getPathRow(snode: Snode, location: LineView.Location, dotAnimationStartDelay: Long, dotAnimationRepeatInterval: Long, isGuardSnode: Boolean): LinearLayout {
        val title = if (isGuardSnode) resources.getString(R.string.activity_path_guard_node_row_title) else resources.getString(R.string.activity_path_service_node_row_title)
        val subtitle = snode.toString().removePrefix("https://").substringBefore(":")
        return getPathRow(title, subtitle, location, dotAnimationStartDelay, dotAnimationRepeatInterval)
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when(id) {
            R.id.learnMoreButton -> learnMore()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun learnMore() {
        try {
            val url = "https://getsession.org/faq/#onion-routing"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }
    // endregion

    // region Line View
    private class LineView : RelativeLayout {
        private lateinit var location: Location
        private var dotAnimationStartDelay: Long = 0
        private var dotAnimationRepeatInterval: Long = 0

        private val dotView by lazy {
            val result = View(context)
            result.setBackgroundResource(R.drawable.accent_dot)
            result
        }

        enum class Location {
            Top, Middle, Bottom
        }

        constructor(context: Context, location: Location, dotAnimationStartDelay: Long, dotAnimationRepeatInterval: Long) : super(context) {
            this.location = location
            this.dotAnimationStartDelay = dotAnimationStartDelay
            this.dotAnimationRepeatInterval = dotAnimationRepeatInterval
            setUpViewHierarchy()
        }

        constructor(context: Context) : super(context) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        private fun setUpViewHierarchy() {
            val lineView = View(context)
            lineView.setBackgroundColor(resources.getColorWithID(R.color.text, context.theme))
            val lineViewHeight = when (location) {
                Location.Top, Location.Bottom -> resources.getDimensionPixelSize(R.dimen.path_row_height) / 2
                Location.Middle -> resources.getDimensionPixelSize(R.dimen.path_row_height)
            }
            val lineViewLayoutParams = LayoutParams(1, lineViewHeight)
            when (location) {
                Location.Top -> lineViewLayoutParams.addRule(ALIGN_PARENT_BOTTOM)
                Location.Middle, Location.Bottom -> lineViewLayoutParams.addRule(ALIGN_PARENT_TOP)
            }
            lineViewLayoutParams.addRule(CENTER_HORIZONTAL)
            lineView.layoutParams = lineViewLayoutParams
            addView(lineView)
            val dotViewSize = resources.getDimensionPixelSize(R.dimen.path_row_dot_size)
            val dotViewLayoutParams = LayoutParams(dotViewSize, dotViewSize)
            dotViewLayoutParams.addRule(CENTER_IN_PARENT)
            dotView.layoutParams = dotViewLayoutParams
            addView(dotView)
            Handler().postDelayed({
                performAnimation()
            }, dotAnimationStartDelay)
        }

        private fun performAnimation() {
            expand()
            Handler().postDelayed({
                collapse()
                Handler().postDelayed({
                    performAnimation()
                }, dotAnimationRepeatInterval)
            }, 1000)
        }

        private fun expand() {
            dotView.animateSizeChange(R.dimen.path_row_dot_size, R.dimen.path_row_expanded_dot_size)
        }

        private fun collapse() {
            dotView.animateSizeChange(R.dimen.path_row_expanded_dot_size, R.dimen.path_row_dot_size)
        }
    }
    // endregion
}
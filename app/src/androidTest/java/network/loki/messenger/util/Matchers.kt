package network.loki.messenger.util

import android.view.View
import androidx.annotation.DrawableRes
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarButton

class InputBarButtonDrawableMatcher(@DrawableRes private val expectedId: Int): TypeSafeMatcher<View>() {

    companion object {
        @JvmStatic fun inputButtonWithDrawable(@DrawableRes expectedId: Int) = InputBarButtonDrawableMatcher(expectedId)
    }

    override fun describeTo(description: Description?) {
        description?.appendText("with drawable on button with resource id: $expectedId")
    }

    override fun matchesSafely(item: View): Boolean {
        if (item !is InputBarButton) return false

        return item.getIconID() == expectedId
    }
}
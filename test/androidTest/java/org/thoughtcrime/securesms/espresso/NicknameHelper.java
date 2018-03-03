package org.thoughtcrime.securesms.espresso;

import org.thoughtcrime.securesms.R;


import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

public class NicknameHelper extends Helper<NicknameHelper>{
    public NicknameHelper(HelperSecret s) {}

    public NicknameHelper resetNickname()
    {
        onView(withText("Reset Nickname"))
                .perform(click());

        return new NicknameHelper(new HelperSecret());
    }

    public NicknameHelper setNickname(String s)
    {
        onView(withText("Set Nickname"))
                .perform(click());

        return new NicknameHelper(new HelperSecret());
    }
}

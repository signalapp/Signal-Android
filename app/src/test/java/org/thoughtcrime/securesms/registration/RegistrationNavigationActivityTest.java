package org.thoughtcrime.securesms.registration;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class RegistrationNavigationActivityTest {
    private static final Intent NULL_ORIGINAL_INTENT = null;

    @Test
    public void newIntentForNewRegistration_accepts_null_original_intent() {
        final Context context = mock(Context.class);
        Intent intent = RegistrationNavigationActivity.newIntentForNewRegistration(context, NULL_ORIGINAL_INTENT);
        assertThat(intent).isNotNull();
    }
}

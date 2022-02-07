package org.thoughtcrime.securesms.recipients;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.recipients.RecipientExporter;

//FIXME AC: This test group is outdated.
@Ignore("This test group uses outdated instrumentation and needs a migration to modern tools.")
@RunWith(MockitoJUnitRunner.class)
public final class RecipientExporterTest extends TestCase {

  @Test
  public void asAddContactIntent_with_neither_email_nor_phone() {
    RecipientExporter exporter = RecipientExporter.export(givenRecipient("Bob", mock(Address.class)));

    assertThatThrownBy(exporter::asAddContactIntent).isExactlyInstanceOf(RuntimeException.class)
                                                    .hasMessage("Cannot export Recipient with neither phone nor email");
  }

  private Recipient givenRecipient(String profileName, Address address) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);
    when(recipient.getAddress()).thenReturn(address);
    return recipient;
  }

}

package org.thoughtcrime.securesms.jobmanager.migrations;

/**
 * Unfortunately there was a bug in {@link RecipientIdFollowUpJobMigration} that requires it to be
 * run again.
 */
public class RecipientIdFollowUpJobMigration2 extends RecipientIdFollowUpJobMigration {
  public RecipientIdFollowUpJobMigration2() {
    super(4);
  }
}

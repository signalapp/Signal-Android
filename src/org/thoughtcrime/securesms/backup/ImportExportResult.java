package org.thoughtcrime.securesms.backup;

public enum ImportExportResult {
  SUCCESS, PASSPHRASE_REQUIRED, NO_SD_CARD, ERROR_IO, ERROR_PARSER_CONFIGURATION, ERROR_PARSE,
  INTERNAL_ERROR;
}

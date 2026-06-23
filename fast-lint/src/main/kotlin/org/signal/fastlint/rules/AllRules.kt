package org.signal.fastlint.rules

import org.signal.fastlint.Rule

/** Every rule the fast linter runs. Add a new rule here to register it. */
val ALL_RULES: List<Rule> = listOf(
  LogNotSignalRule,
  LogTagInlinedRule,
  VersionCodeRule,
  ForegroundServiceRule,
  AlertDialogRule,
  DatabaseReferenceRule,
  StringResourceEscapingRule
)

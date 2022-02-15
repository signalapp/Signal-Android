package org.whispersystems.signalservice.api.payments;

import java.util.Locale;

public final class FormatterOptions {

  public final Locale  locale;
  public final boolean alwaysPositive;
  public final boolean alwaysPrefixWithSign;
  public final boolean withSpaceBeforeUnit;
  public final boolean withUnit;
  public final int     maximumFractionDigits;

  FormatterOptions(Builder builder) {
    this.locale                = builder.locale;
    this.alwaysPositive        = builder.alwaysPositive;
    this.alwaysPrefixWithSign  = builder.alwaysPrefixWithSign;
    this.withSpaceBeforeUnit   = builder.withSpaceBeforeUnit;
    this.withUnit              = builder.withUnit;
    this.maximumFractionDigits = builder.maximumFractionDigits;
  }

  public static FormatterOptions defaults() {
    return builder().build();
  }

  public static FormatterOptions defaults(Locale locale) {
    return builder(locale).build();
  }

  public static FormatterOptions.Builder builder() {
    return builder(Locale.getDefault());
  }

  public static FormatterOptions.Builder builder(Locale locale) {
    return new Builder(locale);
  }

  public static final class Builder {

    private final Locale locale;

    private boolean alwaysPositive        = false;
    private boolean alwaysPrefixWithSign  = false;
    private boolean withSpaceBeforeUnit   = true;
    private boolean withUnit              = true;
    private int     maximumFractionDigits = Integer.MAX_VALUE;

    private Builder(Locale locale) {
      this.locale = locale;
    }

    public Builder alwaysPositive() {
      alwaysPositive = true;
      return this;
    }

    public Builder alwaysPrefixWithSign() {
      alwaysPrefixWithSign = true;
      return this;
    }

    public Builder withoutSpaceBeforeUnit() {
      withSpaceBeforeUnit = false;
      return this;
    }

    public Builder withoutUnit() {
      withUnit = false;
      return this;
    }

    public Builder withMaximumFractionDigits(int maximumFractionDigits) {
      this.maximumFractionDigits = Math.max(maximumFractionDigits, 0);
      return this;
    }

    public FormatterOptions build() {
      return new FormatterOptions(this);
    }
  }
}

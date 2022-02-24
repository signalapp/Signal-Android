package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;

public class SignalServiceTextAttachment {

  private final Optional<String>               text;
  private final Optional<Style>                style;
  private final Optional<Integer>              textForegroundColor;
  private final Optional<Integer>              textBackgroundColor;
  private final Optional<SignalServicePreview> preview;
  private final Optional<Gradient>             backgroundGradient;
  private final Optional<Integer>              backgroundColor;

  private SignalServiceTextAttachment(Optional<String>               text,
                                      Optional<Style>                style,
                                      Optional<Integer>              textForegroundColor,
                                      Optional<Integer>              textBackgroundColor,
                                      Optional<SignalServicePreview> preview,
                                      Optional<Gradient>             backgroundGradient,
                                      Optional<Integer>              backgroundColor) {
    this.text                = text;
    this.style               = style;
    this.textForegroundColor = textForegroundColor;
    this.textBackgroundColor = textBackgroundColor;
    this.preview             = preview;
    this.backgroundGradient  = backgroundGradient;
    this.backgroundColor     = backgroundColor;
  }

  public static SignalServiceTextAttachment forGradientBackground(Optional<String>               text,
                                                                  Optional<Style>                style,
                                                                  Optional<Integer>              textForegroundColor,
                                                                  Optional<Integer>              textBackgroundColor,
                                                                  Optional<SignalServicePreview> preview,
                                                                  Gradient                       backgroundGradient) {
    return new SignalServiceTextAttachment(text,
                                           style,
                                           textForegroundColor,
                                           textBackgroundColor,
                                           preview,
                                           Optional.of(backgroundGradient),
                                           Optional.absent());
  }

  public static SignalServiceTextAttachment forSolidBackground(Optional<String>               text,
                                                               Optional<Style>                style,
                                                               Optional<Integer>              textForegroundColor,
                                                               Optional<Integer>              textBackgroundColor,
                                                               Optional<SignalServicePreview> preview,
                                                               int                            backgroundColor) {
    return new SignalServiceTextAttachment(text,
                                           style,
                                           textForegroundColor,
                                           textBackgroundColor,
                                           preview,
                                           Optional.absent(),
                                           Optional.of(backgroundColor));
  }

  public Optional<String> getText() {
    return text;
  }

  public Optional<Style> getStyle() {
    return style;
  }

  public Optional<Integer> getTextForegroundColor() {
    return textForegroundColor;
  }

  public Optional<Integer> getTextBackgroundColor() {
    return textBackgroundColor;
  }

  public Optional<SignalServicePreview> getPreview() {
    return preview;
  }

  public Optional<Gradient> getBackgroundGradient() {
    return backgroundGradient;
  }

  public Optional<Integer> getBackgroundColor() {
    return backgroundColor;
  }

  public static class Gradient {
    private final Optional<Integer> startColor;
    private final Optional<Integer> endColor;
    private final Optional<Integer> angle;

    public Gradient(Optional<Integer> startColor, Optional<Integer> endColor, Optional<Integer> angle) {
      this.startColor = startColor;
      this.endColor   = endColor;
      this.angle      = angle;
    }

    public Optional<Integer> getStartColor() {
      return startColor;
    }

    public Optional<Integer> getEndColor() {
      return endColor;
    }

    public Optional<Integer> getAngle() {
      return angle;
    }
  }

  public enum Style {
    DEFAULT,
    REGULAR,
    BOLD,
    SERIF,
    SCRIPT,
    CONDENSED,
  }
}

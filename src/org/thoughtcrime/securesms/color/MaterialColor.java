package org.thoughtcrime.securesms.color;

import org.thoughtcrime.securesms.util.Util;

import java.util.Map;

public abstract class MaterialColor {

  private final Map<String, Integer> colorWeightMap;

  protected MaterialColor(Map<String, Integer> colorWeightMap) {
    this.colorWeightMap = colorWeightMap;
  }

  public int toConversationColor(ThemeType themeType) {
    if (themeType == ThemeType.DARK) return colorWeightMap.get("900");
    else                             return colorWeightMap.get("500");
  }

  public int toActionBarColor(ThemeType themeType) {
    return toConversationColor(themeType);
  }

  public int toStatusBarColor(ThemeType themeType) {
    return colorWeightMap.get("700");
  }

  public boolean represents(int colorValue) {
    return colorWeightMap.values().contains(colorValue);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof MaterialColor)) return false;
    return serialize().equals(((MaterialColor)other).serialize());
  }

  @Override
  public int hashCode() {
    return Util.hashCode(serialize());
  }

  public abstract String serialize();

  public static MaterialColor fromSerialized(String serialized) throws UnknownColorException {
    switch (serialized) {
      case RedMaterialColor.SERIALIZED_NAME:        return new RedMaterialColor();
      case PinkMaterialColor.SERIALIZED_NAME:       return new PinkMaterialColor();
      case PurpleMaterialColor.SERIALIZED_NAME:     return new PurpleMaterialColor();
      case DeepPurpleMaterialColor.SERIALIZED_NAME: return new DeepPurpleMaterialColor();
      case IndigoMaterialColor.SERIALIZED_NAME:     return new IndigoMaterialColor();
      case BlueMaterialColor.SERIALIZED_NAME:       return new BlueMaterialColor();
      case LightBlueMaterialColor.SERIALIZED_NAME:  return new LightBlueMaterialColor();
      case CyanMaterialColor.SERIALIZED_NAME:       return new CyanMaterialColor();
      case TealMaterialColor.SERIALIZED_NAME:       return new TealMaterialColor();
      case GreenMaterialColor.SERIALIZED_NAME:      return new GreenMaterialColor();
      case LightGreenMaterialColor.SERIALIZED_NAME: return new LightGreenMaterialColor();
      case OrangeMaterialColor.SERIALIZED_NAME:     return new OrangeMaterialColor();
      case DeepOrangeMaterialColor.SERIALIZED_NAME: return new DeepOrangeMaterialColor();
      case BrownMaterialColor.SERIALIZED_NAME:      return new BrownMaterialColor();
      case GreyMaterialColor.SERIALIZED_NAME:       return new GreyMaterialColor();
      case BlueGreyMaterialColor.SERIALIZED_NAME:   return new BlueGreyMaterialColor();

      default: throw new UnknownColorException("Unknown color: " + serialized);
    }
  }

  public static class UnknownColorException extends Exception {
    public UnknownColorException(String message) {
      super(message);
    }
  }
}

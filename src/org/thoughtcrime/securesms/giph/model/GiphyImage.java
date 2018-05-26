package org.thoughtcrime.securesms.giph.model;


import com.fasterxml.jackson.annotation.JsonProperty;

public class GiphyImage {

  @JsonProperty
  private ImageTypes images;

  public String getGifUrl() {
    return images.downsized.url;
  }

  public long getGifSize() {
    return images.downsized.size;
  }

  public String getGifMmsUrl() {
    return images.fixed_height_downsampled.url;
  }

  public long getMmsGifSize() {
    return images.fixed_height_downsampled.size;
  }

  public float getGifAspectRatio() {
    return (float)images.downsized.width / (float)images.downsized.height;
  }

  public int getGifWidth() {
    return images.downsized.width;
  }

  public int getGifHeight() {
    return images.downsized.height;
  }

  public String getStillUrl() {
    return images.downsized_still.url;
  }

  public long getStillSize() {
    return images.downsized_still.size;
  }

  public static class ImageTypes {
    @JsonProperty
    private ImageData fixed_height;
    @JsonProperty
    private ImageData fixed_height_still;
    @JsonProperty
    private ImageData fixed_height_downsampled;
    @JsonProperty
    private ImageData fixed_width;
    @JsonProperty
    private ImageData fixed_width_still;
    @JsonProperty
    private ImageData fixed_width_downsampled;
    @JsonProperty
    private ImageData fixed_width_small;
    @JsonProperty
    private ImageData downsized_medium;
    @JsonProperty
    private ImageData downsized;
    @JsonProperty
    private ImageData downsized_still;
  }

  public static class ImageData {
    @JsonProperty
    private String url;

    @JsonProperty
    private int width;

    @JsonProperty
    private int height;

    @JsonProperty
    private int size;

    @JsonProperty
    private String mp4;

    @JsonProperty
    private String webp;
  }

}

package org.thoughtcrime.securesms.giph.model;


import com.fasterxml.jackson.annotation.JsonProperty;

public class GiphyImage {

  @JsonProperty
  private ImageTypes images;

  public String getGifUrl() {
    return images.downsized_medium.url;
  }

  public float getGifAspectRatio() {
    return (float)images.downsized_medium.width / (float)images.downsized_medium.height;
  }

  public String getStillUrl() {
    return images.fixed_width_still.url;
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

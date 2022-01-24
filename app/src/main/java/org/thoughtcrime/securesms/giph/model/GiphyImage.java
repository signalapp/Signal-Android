package org.thoughtcrime.securesms.giph.model;


import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.thoughtcrime.securesms.util.ByteUnit;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

public class GiphyImage implements MappingModel<GiphyImage> {

  private static final int MAX_SIZE = (int) ByteUnit.MEGABYTES.toBytes(2);

  @JsonProperty
  private ImageTypes images;

  @JsonProperty("is_sticker")
  private boolean isSticker;

  @Override
  public boolean areItemsTheSame(@NonNull GiphyImage newItem) {
    return getMp4Url().equals(newItem.getMp4Url());
  }

  @Override
  public boolean areContentsTheSame(@NonNull GiphyImage newItem) {
    return areItemsTheSame(newItem);
  }

  public boolean isSticker() {
    return isSticker;
  }

  public String getGifUrl() {
    ImageData data = getGifData();
    return data != null ? data.url : null;
  }

  public String getMp4Url() {
    ImageData data = getMp4Data();
    return data != null ? data.mp4 : null;
  }

  public String getMp4PreviewUrl() {
    ImageData data = getMp4PreviewData();
    return data != null ? data.mp4 : null;
  }

  public long getGifSize() {
    ImageData data = getGifData();
    return data != null ? data.size : 0;
  }

  public String getGifMmsUrl() {
    ImageData data = getGifMmsData();
    return data != null ? data.url : null;
  }

  public long getMmsGifSize() {
    ImageData data = getGifMmsData();
    return data != null ? data.size : 0;
  }

  public float getGifAspectRatio() {
    return (float)images.downsized_small.width / (float)images.downsized_small.height;
  }

  public int getGifWidth() {
    ImageData data = getGifData();
    return data != null ? data.width : 0;
  }

  public int getGifHeight() {
    ImageData data = getGifData();
    return data != null ? data.height : 0;
  }

  public String getStillUrl() {
    ImageData data = getStillData();
    return data != null ? data.url : null;
  }

  public long getStillSize() {
    ImageData data = getStillData();
    return data != null ? data.size : 0;
  }

  private @Nullable ImageData getMp4Data() {
    return getLargestMp4WithinSizeConstraint(images.fixed_width, images.fixed_height, images.fixed_width_small, images.fixed_height_small, images.downsized_small);
  }

  private @Nullable ImageData getMp4PreviewData() {
    return images.preview;
  }

  private @Nullable ImageData getGifData() {
    return getLargestGifWithinSizeConstraint(images.downsized, images.fixed_width, images.fixed_height, images.fixed_width_small, images.fixed_height_small);
  }

  private @Nullable ImageData getGifMmsData() {
    return getLargestGifWithinSizeConstraint(images.fixed_width_small, images.fixed_height_small);
  }

  private @Nullable ImageData getStillData() {
    return getFirstNonEmpty(images.fixed_width_small_still, images.fixed_height_small_still);
  }

  private static @Nullable ImageData getFirstNonEmpty(ImageData... data) {
    for (ImageData image : data) {
      if (!TextUtils.isEmpty(image.url)) {
        return image;
      }
    }

    return null;
  }

  private @Nullable ImageData getLargestGifWithinSizeConstraint(ImageData ... buckets) {
    return getLargestWithinSizeConstraint(imageData -> imageData.size, buckets);
  }

  private @Nullable ImageData getLargestMp4WithinSizeConstraint(ImageData ... buckets) {
    return getLargestWithinSizeConstraint(imageData -> imageData.mp4_size, buckets);
  }

  private @Nullable ImageData getLargestWithinSizeConstraint(@NonNull SizeFunction sizeFunction, ImageData ... buckets) {
    ImageData data = null;
    int       size = 0;

    for (final ImageData bucket : buckets) {
      if (bucket == null) continue;

      int bucketSize = sizeFunction.getSize(bucket);
      if (bucketSize <= MAX_SIZE && bucketSize > size) {
        data = bucket;
        size = bucketSize;
      }
    }

    return data;
  }

  private interface SizeFunction {
    int getSize(@NonNull ImageData imageData);
  }

  public static class ImageTypes {
    @JsonProperty
    private ImageData downsized;
    @JsonProperty
    private ImageData fixed_height;
    @JsonProperty
    private ImageData fixed_height_small;
    @JsonProperty
    private ImageData fixed_height_small_still;
    @JsonProperty
    private ImageData fixed_width;
    @JsonProperty
    private ImageData fixed_width_small;
    @JsonProperty
    private ImageData fixed_width_small_still;
    @JsonProperty
    private ImageData downsized_small;
    @JsonProperty
    private ImageData preview;
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

    @JsonProperty
    private int mp4_size;
  }

}

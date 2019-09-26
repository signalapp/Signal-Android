package org.thoughtcrime.securesms;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class ExifTagBlacklist {

  private static final Set<String> EXIF_BLACKLIST = new HashSet<String>() {{
    add(ExifInterface.TAG_BITS_PER_SAMPLE);
    add(ExifInterface.TAG_COMPRESSION);
    add(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION);
    add(ExifInterface.TAG_SAMPLES_PER_PIXEL);
    add(ExifInterface.TAG_PLANAR_CONFIGURATION);
    add(ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING);
    add(ExifInterface.TAG_Y_CB_CR_POSITIONING);
    add(ExifInterface.TAG_X_RESOLUTION);
    add(ExifInterface.TAG_Y_RESOLUTION);
    add(ExifInterface.TAG_RESOLUTION_UNIT);
    add(ExifInterface.TAG_STRIP_OFFSETS);
    add(ExifInterface.TAG_ROWS_PER_STRIP);
    add(ExifInterface.TAG_STRIP_BYTE_COUNTS);
    add(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT);
    add(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH);
    add(ExifInterface.TAG_TRANSFER_FUNCTION);
    add(ExifInterface.TAG_WHITE_POINT);
    add(ExifInterface.TAG_PRIMARY_CHROMATICITIES);
    add(ExifInterface.TAG_Y_CB_CR_COEFFICIENTS);
    add(ExifInterface.TAG_REFERENCE_BLACK_WHITE);
    add(ExifInterface.TAG_IMAGE_DESCRIPTION);
    add(ExifInterface.TAG_MAKE);
    add(ExifInterface.TAG_MODEL);
    add(ExifInterface.TAG_SOFTWARE);
    add(ExifInterface.TAG_ARTIST);
    add(ExifInterface.TAG_COPYRIGHT);
    add(ExifInterface.TAG_EXIF_VERSION);
    add(ExifInterface.TAG_FLASHPIX_VERSION);
    add(ExifInterface.TAG_COLOR_SPACE);
    add(ExifInterface.TAG_GAMMA);
    add(ExifInterface.TAG_PIXEL_X_DIMENSION);
    add(ExifInterface.TAG_PIXEL_Y_DIMENSION);
    add(ExifInterface.TAG_COMPONENTS_CONFIGURATION);
    add(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL);
    add(ExifInterface.TAG_MAKER_NOTE);
    add(ExifInterface.TAG_USER_COMMENT);
    add(ExifInterface.TAG_RELATED_SOUND_FILE);
    add(ExifInterface.TAG_DATETIME_DIGITIZED);
    add(ExifInterface.TAG_SUBSEC_TIME);
    add(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL);
    add(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED);
    add(ExifInterface.TAG_EXPOSURE_TIME);
    add(ExifInterface.TAG_F_NUMBER);
    add(ExifInterface.TAG_EXPOSURE_PROGRAM);
    add(ExifInterface.TAG_SPECTRAL_SENSITIVITY);
    add(ExifInterface.TAG_ISO_SPEED_RATINGS);
    add(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
    add(ExifInterface.TAG_OECF);
    add(ExifInterface.TAG_SENSITIVITY_TYPE);
    add(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY);
    add(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX);
    add(ExifInterface.TAG_ISO_SPEED);
    add(ExifInterface.TAG_ISO_SPEED_LATITUDE_YYY);
    add(ExifInterface.TAG_ISO_SPEED_LATITUDE_ZZZ);
    add(ExifInterface.TAG_SHUTTER_SPEED_VALUE);
    add(ExifInterface.TAG_APERTURE_VALUE);
    add(ExifInterface.TAG_BRIGHTNESS_VALUE);
    add(ExifInterface.TAG_EXPOSURE_BIAS_VALUE);
    add(ExifInterface.TAG_MAX_APERTURE_VALUE);
    add(ExifInterface.TAG_SUBJECT_DISTANCE);
    add(ExifInterface.TAG_METERING_MODE);
    add(ExifInterface.TAG_FLASH);
    add(ExifInterface.TAG_SUBJECT_AREA);
    add(ExifInterface.TAG_FOCAL_LENGTH);
    add(ExifInterface.TAG_FLASH_ENERGY);
    add(ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE);
    add(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION);
    add(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION);
    add(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT);
    add(ExifInterface.TAG_SUBJECT_LOCATION);
    add(ExifInterface.TAG_EXPOSURE_INDEX);
    add(ExifInterface.TAG_SENSING_METHOD);
    add(ExifInterface.TAG_FILE_SOURCE);
    add(ExifInterface.TAG_SCENE_TYPE);
    add(ExifInterface.TAG_CFA_PATTERN);
    add(ExifInterface.TAG_CUSTOM_RENDERED);
    add(ExifInterface.TAG_EXPOSURE_MODE);
    add(ExifInterface.TAG_WHITE_BALANCE);
    add(ExifInterface.TAG_DIGITAL_ZOOM_RATIO);
    add(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
    add(ExifInterface.TAG_SCENE_CAPTURE_TYPE);
    add(ExifInterface.TAG_GAIN_CONTROL);
    add(ExifInterface.TAG_CONTRAST);
    add(ExifInterface.TAG_SATURATION);
    add(ExifInterface.TAG_SHARPNESS);
    add(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION);
    add(ExifInterface.TAG_SUBJECT_DISTANCE_RANGE);
    add(ExifInterface.TAG_IMAGE_UNIQUE_ID);
    add(ExifInterface.TAG_CAMARA_OWNER_NAME);
    add(ExifInterface.TAG_BODY_SERIAL_NUMBER);
    add(ExifInterface.TAG_LENS_SPECIFICATION);
    add(ExifInterface.TAG_LENS_MAKE);
    add(ExifInterface.TAG_LENS_MODEL);
    add(ExifInterface.TAG_LENS_SERIAL_NUMBER);
    add(ExifInterface.TAG_GPS_VERSION_ID);
    add(ExifInterface.TAG_GPS_LATITUDE_REF);
    add(ExifInterface.TAG_GPS_LATITUDE);
    add(ExifInterface.TAG_GPS_LONGITUDE_REF);
    add(ExifInterface.TAG_GPS_LONGITUDE);
    add(ExifInterface.TAG_GPS_ALTITUDE_REF);
    add(ExifInterface.TAG_GPS_TIMESTAMP);
    add(ExifInterface.TAG_GPS_SATELLITES);
    add(ExifInterface.TAG_GPS_STATUS);
    add(ExifInterface.TAG_GPS_MEASURE_MODE);
    add(ExifInterface.TAG_GPS_DOP);
    add(ExifInterface.TAG_GPS_SPEED_REF);
    add(ExifInterface.TAG_GPS_SPEED);
    add(ExifInterface.TAG_GPS_TRACK_REF);
    add(ExifInterface.TAG_GPS_TRACK);
    add(ExifInterface.TAG_GPS_IMG_DIRECTION_REF);
    add(ExifInterface.TAG_GPS_IMG_DIRECTION);
    add(ExifInterface.TAG_GPS_MAP_DATUM);
    add(ExifInterface.TAG_GPS_DEST_LATITUDE_REF);
    add(ExifInterface.TAG_GPS_DEST_LATITUDE);
    add(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF);
    add(ExifInterface.TAG_GPS_DEST_LONGITUDE);
    add(ExifInterface.TAG_GPS_DEST_BEARING_REF);
    add(ExifInterface.TAG_GPS_DEST_BEARING);
    add(ExifInterface.TAG_GPS_DEST_DISTANCE_REF);
    add(ExifInterface.TAG_GPS_DEST_DISTANCE);
    add(ExifInterface.TAG_GPS_PROCESSING_METHOD);
    add(ExifInterface.TAG_GPS_AREA_INFORMATION);
    add(ExifInterface.TAG_GPS_DATESTAMP);
    add(ExifInterface.TAG_GPS_DIFFERENTIAL);
    add(ExifInterface.TAG_GPS_H_POSITIONING_ERROR);
    add(ExifInterface.TAG_INTEROPERABILITY_INDEX);
    add(ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH);
    add(ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH);
    add(ExifInterface.TAG_DNG_VERSION);
    add(ExifInterface.TAG_DEFAULT_CROP_SIZE);
    add(ExifInterface.TAG_ORF_THUMBNAIL_IMAGE);
    add(ExifInterface.TAG_ORF_PREVIEW_IMAGE_START);
    add(ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH);
    add(ExifInterface.TAG_ORF_ASPECT_FRAME);
    add(ExifInterface.TAG_RW2_SENSOR_BOTTOM_BORDER);
    add(ExifInterface.TAG_RW2_SENSOR_LEFT_BORDER);
    add(ExifInterface.TAG_RW2_SENSOR_RIGHT_BORDER);
    add(ExifInterface.TAG_RW2_SENSOR_TOP_BORDER);
    add(ExifInterface.TAG_RW2_ISO);
    add(ExifInterface.TAG_RW2_JPG_FROM_RAW);
    add(ExifInterface.TAG_NEW_SUBFILE_TYPE);
    add(ExifInterface.TAG_SUBFILE_TYPE);
  }};

  public static boolean hasViolations(InputStream imageStream) throws IOException {
    ExifInterface exifInterface = new ExifInterface(imageStream);

    for (String blacklistedTag : EXIF_BLACKLIST) {
      if (exifInterface.getAttribute(blacklistedTag) != null) {
        return true;
      }
    }

    return false;
  }
}

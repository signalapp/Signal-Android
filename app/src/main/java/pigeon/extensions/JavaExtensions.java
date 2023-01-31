package pigeon.extensions;

public class JavaExtensions {

 public static void onFocusTextChangeListener(android.widget.TextView view) {
    view.setOnFocusChangeListener((v, hasFocus) -> {
      float textSize;
      if (hasFocus) {
        textSize = 36f;
      } else {
        textSize = 24f;
      }
      view.setTextSize(textSize);
    });
  }
}
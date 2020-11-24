package org.thoughtcrime.securesms.imageeditor.model;

import android.graphics.Matrix;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class Bisect {

  static final float ACCURACY = 0.001f;

  private static final int MAX_ITERATIONS = 16;

  interface Predicate {
     boolean test();
  }

  interface ModifyElement {
    void applyFactor(@NonNull Matrix matrix, float factor);
  }

  /**
   * Given a predicate function, attempts to finds the boundary between predicate true and predicate false.
   * If it returns true, it will animate the element to the closest true value found to that boundary.
   *
   * @param element          The element to modify.
   * @param outOfBoundsValue The current value, known to be out of bounds. 1 for a scale and 0 for a translate.
   * @param atMost           A value believed to be in bounds.
   * @param predicate        The out of bounds predicate.
   * @param modifyElement    Apply the latest value to the element local matrix.
   * @param invalidate       For animation if finds a result.
   * @return true iff finds a result.
   */
 static boolean bisectToTest(@NonNull EditorElement element,
                             float outOfBoundsValue,
                             float atMost,
                             @NonNull Predicate predicate,
                             @NonNull ModifyElement modifyElement,
                             @NonNull Runnable invalidate)
 {
   Matrix closestSuccesful = bisectToTest(element, outOfBoundsValue, atMost, predicate, modifyElement);

   if (closestSuccesful != null) {
     element.animateLocalTo(closestSuccesful, invalidate);
     return true;
   } else {
     return false;
   }
  }

  /**
   * Given a predicate function, attempts to finds the boundary between predicate true and predicate false.
   * Returns new local matrix for the element if a solution is found.
   *
   * @param element          The element to modify.
   * @param outOfBoundsValue The current value, known to be out of bounds. 1 for a scale and 0 for a translate.
   * @param atMost           A value believed to be in bounds.
   * @param predicate        The out of bounds predicate.
   * @param modifyElement    Apply the latest value to the element local matrix.
   * @return matrix to replace local matrix iff finds a result, null otherwise.
   */
 static @Nullable Matrix bisectToTest(@NonNull EditorElement element,
                                      float outOfBoundsValue,
                                      float atMost,
                                      @NonNull Predicate predicate,
                                      @NonNull ModifyElement modifyElement)
 {
   Matrix  elementMatrix     = element.getLocalMatrix();
   Matrix  original          = new Matrix(elementMatrix);
   Matrix  closestSuccessful = new Matrix();
   boolean haveResult        = false;
   int     attempt           = 0;
   float   successValue      = 0;
   float   inBoundsValue     = atMost;
   float   nextValueToTry    = inBoundsValue;

   do {
     attempt++;

     modifyElement.applyFactor(elementMatrix, nextValueToTry);
     try {

       if (predicate.test()) {
         inBoundsValue = nextValueToTry;

         // if first success or closer to out of bounds than the current closest
         if (!haveResult || Math.abs(nextValueToTry) < Math.abs(successValue)) {
           haveResult = true;
           successValue = nextValueToTry;
           closestSuccessful.set(elementMatrix);
         }
       } else {
         if (attempt == 1) {
           // failure on first attempt means inBoundsValue is actually out of bounds and so no solution
           return null;
         }
         outOfBoundsValue = nextValueToTry;
       }
     } finally {
       // reset
       elementMatrix.set(original);
     }

     nextValueToTry = (inBoundsValue + outOfBoundsValue) / 2f;

   } while (attempt < MAX_ITERATIONS && Math.abs(inBoundsValue - outOfBoundsValue) > ACCURACY);

   if (haveResult) {
     return closestSuccessful;
   }
   return null;
 }

}

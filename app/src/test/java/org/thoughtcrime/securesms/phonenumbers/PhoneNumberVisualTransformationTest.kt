package org.thoughtcrime.securesms.phonenumbers

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert
import org.junit.Test

class PhoneNumberVisualTransformationTest {

  @Test
  fun `given US region, when I enter 5550123, then I expect 555-0123`() {
    val regionCode = "US"
    val transformation = PhoneNumberVisualTransformation(regionCode)
    val given = "5550123"
    val expected = "555-0123"
    val output = transformation.filter(AnnotatedString(given))
    Assert.assertEquals(output.text.text, expected)
  }

  @Test
  fun `given US region, when I enter 555012, then I expect 555-012`() {
    val regionCode = "US"
    val transformation = PhoneNumberVisualTransformation(regionCode)
    val given = "555012"
    val expected = "555-012"
    val output = transformation.filter(AnnotatedString(given))
    Assert.assertEquals(output.text.text, expected)
  }

  @Test
  fun `given US region formatted number, when I originalToTransformed index 0, then I expect index 0`() {
    val regionCode = "US"
    val transformation = PhoneNumberVisualTransformation(regionCode)
    val given = "5550123"
    val output = transformation.filter(AnnotatedString(given))
    val mapping = output.offsetMapping

    val result = mapping.originalToTransformed(0)
    Assert.assertEquals(0, result)
  }

  @Test
  fun `given US region formatted number, when I originalToTransformed index 6, then I expect index 7`() {
    val regionCode = "US"
    val transformation = PhoneNumberVisualTransformation(regionCode)
    val given = "5550123"
    val output = transformation.filter(AnnotatedString(given))
    val mapping = output.offsetMapping

    val result = mapping.originalToTransformed(6)
    Assert.assertEquals(7, result)
  }

  @Test
  fun `given US region formatted number, when I transformedToOriginal index 0, then I expect index 0`() {
    val regionCode = "US"
    val transformation = PhoneNumberVisualTransformation(regionCode)
    val given = "5550123"
    val output = transformation.filter(AnnotatedString(given))
    val mapping = output.offsetMapping

    val result = mapping.transformedToOriginal(0)
    Assert.assertEquals(0, result)
  }

  @Test
  fun `given US region formatted number, when I transformedToOriginal index 7, then I expect index 6`() {
    val regionCode = "US"
    val transformation = PhoneNumberVisualTransformation(regionCode)
    val given = "5550123"
    val output = transformation.filter(AnnotatedString(given))
    val mapping = output.offsetMapping

    val result = mapping.transformedToOriginal(7)
    Assert.assertEquals(6, result)
  }

  @Test
  fun `given US region formatted number with local code, when I originalToTransformed index 7, then I expect index 11`() {
    val regionCode = "US"
    val transformation = PhoneNumberVisualTransformation(regionCode)
    val given = "55501233"
    val output = transformation.filter(AnnotatedString(given))
    val mapping = output.offsetMapping

    val result = mapping.originalToTransformed(7)
    Assert.assertEquals(11, result)
  }
}

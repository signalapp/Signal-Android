package org.thoughtcrime.securesms.recipients

import android.graphics.Color
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.database.RecipientDatabaseTestUtils.createRecipient
import org.thoughtcrime.securesms.keyvalue.ChatColorsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.WallpaperValues
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper

@Suppress("ClassName")
class Recipient_getChatColorsTest : BaseRecipientTest() {

  private val defaultChatColors = ChatColorsPalette.Bubbles.default.withId(ChatColors.Id.Auto)
  private val globalWallpaperChatColor = ChatColors.forColor(ChatColors.Id.BuiltIn, Color.RED)
  private val globalChatColor = ChatColors.forColor(ChatColors.Id.BuiltIn, Color.GREEN)

  private lateinit var wallpaperValues: WallpaperValues
  private lateinit var chatColorsValues: ChatColorsValues

  @Before
  fun setUp() {
    wallpaperValues = mockk()
    chatColorsValues = mockk()

    val globalWallpaper = createWallpaper(globalWallpaperChatColor)
    every { wallpaperValues.wallpaper } answers { globalWallpaper }
    every { chatColorsValues.chatColors } answers { globalChatColor }

    mockkObject(SignalStore)
    every { SignalStore.wallpaper } returns wallpaperValues
    every { SignalStore.chatColors } returns chatColorsValues
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `Given recipient has custom chat color set, when I getChatColors, then I expect the custom chat color`() {
    // GIVEN
    val expected = ChatColors.forColor(ChatColors.Id.Custom(12), Color.BLACK)
    val recipient = createRecipient(chatColors = expected)

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(expected, actual)
  }

  @Test
  fun `Given recipient has built in chat color set, when I getChatColors, then I expect the custom chat color`() {
    // GIVEN
    val expected = ChatColors.forColor(ChatColors.Id.BuiltIn, Color.BLACK)
    val recipient = createRecipient(chatColors = expected)

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(expected, actual)
  }

  @Test
  fun `Given recipient has auto chat color set and wallpaper set, when I getChatColors, then I expect the wallpaper chat color`() {
    // GIVEN
    val auto = ChatColors.forColor(ChatColors.Id.Auto, Color.BLACK)
    val expected = ChatColors.forColor(ChatColors.Id.BuiltIn, Color.WHITE)
    val wallpaper = createWallpaper(chatColors = expected)
    val recipient = createRecipient(chatColors = auto, wallpaper = wallpaper)

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(expected, actual)
  }

  @Test
  fun `Given recipient has auto chat color set and no wallpaper set and global wallpaper set, when I getChatColors, then I expect the global wallpaper chat color`() {
    // GIVEN
    val auto = ChatColors.forColor(ChatColors.Id.Auto, Color.BLACK)
    val recipient = createRecipient(chatColors = auto)

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(globalWallpaperChatColor, actual)
  }

  @Test
  fun `Given recipient has auto chat color set and no wallpaper set and no global wallpaper set, when I getChatColors, then I expect the default chat color`() {
    // GIVEN
    every { wallpaperValues.wallpaper } answers { null }
    val auto = ChatColors.forColor(ChatColors.Id.Auto, Color.BLACK)
    val recipient = createRecipient(chatColors = auto)

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(defaultChatColors, actual)
  }

  @Test
  fun `Given recipient has no chat color set and there is a custom global chat color, when I getChatColors, then I expect the global chat color`() {
    // GIVEN
    val expected = globalChatColor.withId(ChatColors.Id.Custom(12))
    every { chatColorsValues.chatColors } answers { expected }
    val recipient = createRecipient()

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(expected, actual)
  }

  @Test
  fun `Given recipient has no chat color set and there is a built in global chat color, when I getChatColors, then I expect the global chat color`() {
    // GIVEN
    val recipient = createRecipient()

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(globalChatColor, actual)
  }

  @Test
  fun `Given recipient has no chat color set and there is an auto global chat color and the recipient has a wallpaper, when I getChatColors, then I expect the wallpaper chat color`() {
    // GIVEN
    every { chatColorsValues.chatColors } answers { globalChatColor.withId(ChatColors.Id.Auto) }
    val color = ChatColors.forColor(ChatColors.Id.BuiltIn, Color.CYAN)
    val recipient = createRecipient(wallpaper = createWallpaper(color))

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(color, actual)
  }

  @Test
  fun `Given recipient has no chat color set and there is no global chat color and the recipient has a wallpaper, when I getChatColors, then I expect the wallpaper chat color`() {
    // GIVEN
    every { chatColorsValues.chatColors } answers { null }
    val color = ChatColors.forColor(ChatColors.Id.BuiltIn, Color.CYAN)
    val recipient = createRecipient(wallpaper = createWallpaper(color))

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(color, actual)
  }

  @Test
  fun `Given recipient has no chat color set and there is an auto global chat color and the recipient has no wallpaper and global wallpaper set, when I getChatColors, then I expect the global wallpaper chat color`() {
    // GIVEN
    every { chatColorsValues.chatColors } answers { globalChatColor.withId(ChatColors.Id.Auto) }
    val recipient = createRecipient()

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(globalWallpaperChatColor, actual)
  }

  @Test
  fun `Given recipient has no chat color set and there is no global chat color and the recipient has no wallpaper and global wallpaper set, when I getChatColors, then I expect the global wallpaper chat color`() {
    // GIVEN
    every { chatColorsValues.chatColors } answers { null }
    val recipient = createRecipient()

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(globalWallpaperChatColor, actual)
  }

  @Test
  fun `Given no recipient colors and auto global colors and no wallpaper set, when I getChatColors, then I expect default blue`() {
    // GIVEN
    every { wallpaperValues.wallpaper } answers { null }
    every { chatColorsValues.chatColors } answers { globalChatColor.withId(ChatColors.Id.Auto) }
    val recipient = createRecipient()

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(defaultChatColors, actual)
  }

  @Test
  fun `Given no colors or wallpaper set, when I getChatColors, then I expect default blue`() {
    // GIVEN
    every { wallpaperValues.wallpaper } answers { null }
    every { chatColorsValues.chatColors } answers { null }
    val recipient = createRecipient()

    // WHEN
    val actual = recipient.chatColors

    // THEN
    assertEquals(defaultChatColors, actual)
  }

  private fun createWallpaper(
    chatColors: ChatColors
  ): ChatWallpaper {
    return mockk<ChatWallpaper>().apply {
      every { autoChatColors } answers { chatColors }
    }
  }
}

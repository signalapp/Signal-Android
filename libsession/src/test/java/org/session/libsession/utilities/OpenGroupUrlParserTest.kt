package org.session.libsession.utilities

import org.junit.Test
import org.junit.Assert.*

class OpenGroupUrlParserTest {

    @Test
    fun parseUrlTest() {
        val inputUrl = "https://sessionopengroup.co/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val expectedHost = "https://sessionopengroup.co"
        val expectedRoom = "main"
        val expectedPublicKey = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val result = OpenGroupUrlParser.parseUrl(inputUrl)
        assertEquals(expectedHost, result.server)
        assertEquals(expectedRoom, result.room)
        assertEquals(expectedPublicKey, result.serverPublicKey)
    }

    @Test
    fun parseUrlNoHttpTest() {
        val inputUrl = "sessionopengroup.co/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val expectedHost = "http://sessionopengroup.co"
        val expectedRoom = "main"
        val expectedPublicKey = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val result = OpenGroupUrlParser.parseUrl(inputUrl)
        assertEquals(expectedHost, result.server)
        assertEquals(expectedRoom, result.room)
        assertEquals(expectedPublicKey, result.serverPublicKey)
    }

    @Test
    fun parseUrlWithIpTest() {
        val inputUrl = "https://143.198.213.255:80/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val expectedHost = "https://143.198.213.255:80"
        val expectedRoom = "main"
        val expectedPublicKey = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val result = OpenGroupUrlParser.parseUrl(inputUrl)
        assertEquals(expectedHost, result.server)
        assertEquals(expectedRoom, result.room)
        assertEquals(expectedPublicKey, result.serverPublicKey)
    }

    @Test
    fun parseUrlWithIpAndNoHttpTest() {
        val inputUrl = "143.198.213.255/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val expectedHost = "http://143.198.213.255"
        val expectedRoom = "main"
        val expectedPublicKey = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val result = OpenGroupUrlParser.parseUrl(inputUrl)
        assertEquals(expectedHost, result.server)
        assertEquals(expectedRoom, result.room)
        assertEquals(expectedPublicKey, result.serverPublicKey)
    }

    @Test(expected = OpenGroupUrlParser.Error.MalformedURL::class)
    fun parseUrlMalformedUrlTest() {
        val inputUrl = "file:sessionopengroup.co/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }

    @Test(expected = OpenGroupUrlParser.Error.NoRoom::class)
    fun parseUrlNoRoomSpecifiedTest() {
        val inputUrl = "https://sessionopengroup.comain?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }

    @Test(expected = OpenGroupUrlParser.Error.NoPublicKey::class)
    fun parseUrlNoPublicKeySpecifiedTest() {
        val inputUrl = "https://sessionopengroup.co/main"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }

    @Test(expected = OpenGroupUrlParser.Error.InvalidPublicKey::class)
    fun parseUrlInvalidPublicKeyProviedTest() {
        val inputUrl = "https://sessionopengroup.co/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adff"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }
}

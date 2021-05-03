package org.session.libsession.utilities

import org.junit.Test
import org.junit.Assert.*

class OpenGroupUrlParserTest {

    @Test
    fun parseUrlTest() {
        val inputUrl = "https://sessionopengroup.co/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val expectedHost = "sessionopengroup.co"
        val expectedRoom = "main"
        val expectedPublicKey = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val result = OpenGroupUrlParser.parseUrl(inputUrl)
        assertEquals(expectedHost, result.serverHost)
        assertEquals(expectedRoom, result.room)
        assertEquals(expectedPublicKey, result.serverPublicKey)
    }

    @Test
    fun parseUrlWithIpTest() {
        val inputUrl = "https://143.198.213.255:80/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val expectedHost = "143.198.213.255"
        val expectedRoom = "main"
        val expectedPublicKey = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"

        val result = OpenGroupUrlParser.parseUrl(inputUrl)
        assertEquals(expectedHost, result.serverHost)
        assertEquals(expectedRoom, result.room)
        assertEquals(expectedPublicKey, result.serverPublicKey)
    }

    @Test(expected = OpenGroupUrlParser.Error.MalformedUrl::class)
    fun parseUrlMalformedUrlTest() {
        val inputUrl = "sessionopengroup.co/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }

    @Test(expected = OpenGroupUrlParser.Error.NoRoomSpecified::class)
    fun parseUrlNoRoomSpecifiedTest() {
        val inputUrl = "https://sessionopengroup.comain?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }

    @Test(expected = OpenGroupUrlParser.Error.NoPublicKeySpecified::class)
    fun parseUrlNoPublicKeySpecifiedTest() {
        val inputUrl = "https://sessionopengroup.co/main"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }

    @Test(expected = OpenGroupUrlParser.Error.WrongQuery::class)
    fun parseUrlWrongQueryTest() {
        val inputUrl = "https://sessionopengroup.co/main?publickey=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231c"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }

    @Test(expected = OpenGroupUrlParser.Error.InvalidPublicKeyProvided::class)
    fun parseUrlInvalidPublicKeyProviedTest() {
        val inputUrl = "https://sessionopengroup.co/main?public_key=658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adff"
        OpenGroupUrlParser.parseUrl(inputUrl)
    }
}

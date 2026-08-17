/*
 * Ported from NewPipe (org.schabi.newpipe.util.potoken.JavaScriptUtil), GPL-3.0-or-later.
 * Copyright (C) Team NewPipe. See LICENSE.
 *
 * Changed from the original: nanojson swapped for org.json and okio swapped for android.util.Base64,
 * both of which are already on the device. The extractor exposes nanojson at runtime scope only, so
 * compiling against it would mean declaring a second copy of a JSON parser this app does not
 * otherwise need.
 */
package me.vibe.data.remote.potoken

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the raw challenge from the Create endpoint into an object literal that can be pasted
 * straight into a JavaScript snippet.
 */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)

    val challengeData = if (scrambled.length() > 1 && scrambled.opt(1) is String) {
        JSONArray(descramble(scrambled.getString(1)))
    } else {
        scrambled.getJSONArray(0)
    }

    return JSONObject()
        .put("messageId", challengeData.getString(0))
        .put(
            "interpreterJavascript",
            JSONObject()
                .put(
                    "privateDoNotAccessOrElseSafeScriptWrappedValue",
                    firstString(challengeData.optJSONArray(1)),
                )
                .put(
                    "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue",
                    firstString(challengeData.optJSONArray(2)),
                ),
        )
        .put("interpreterHash", challengeData.getString(3))
        .put("program", challengeData.getString(4))
        .put("globalName", challengeData.getString(5))
        .put("clientExperimentsStateBlob", challengeData.getString(7))
        .toString()
}

/**
 * The integrity token as a JavaScript `Uint8Array` literal, plus how many seconds it is good for.
 */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val data = JSONArray(rawIntegrityTokenData)
    return base64ToU8(data.getString(0)) to data.getLong(1)
}

/** A string as a JavaScript `Uint8Array` literal. */
fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray())

/**
 * Takes the output of JavaScript's `Uint8Array::toString()` — bytes as comma-separated integers,
 * so "97,98,99" is "abc" — and re-encodes it in the base64 variant poTokens use.
 */
fun u8ToBase64(poToken: String): String = poToken.split(",")
    .map { it.trim().toUByte().toByte() }
    .toByteArray()
    .let { Base64.encodeToString(it, Base64.NO_WRAP) }
    .replace('+', '-')
    .replace('/', '_')

/** The scrambled challenge is base64 with 97 subtracted from every byte. */
private fun descramble(scrambledChallenge: String): String =
    base64ToBytes(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()

private fun base64ToU8(base64: String): String = newUint8Array(base64ToBytes(base64))

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(",") { it.toUByte().toString() } + "])"

/** YouTube's base64 variant: `-` and `_` for the last two characters, `.` for padding. */
private fun base64ToBytes(base64: String): ByteArray {
    val normalized = base64.replace('-', '+').replace('_', '/').replace('.', '=')
    return try {
        Base64.decode(normalized, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        throw PoTokenException("Cannot base64 decode: ${e.message}")
    }
}

/** The wrapped-value arrays carry one string among other junk; that string is the payload. */
private fun firstString(array: JSONArray?): Any {
    if (array == null) return JSONObject.NULL
    for (i in 0 until array.length()) {
        val value = array.opt(i)
        if (value is String) return value
    }
    return JSONObject.NULL
}

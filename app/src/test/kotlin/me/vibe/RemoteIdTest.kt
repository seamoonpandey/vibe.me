package me.vibe

import me.vibe.data.remoteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Remote ids share one space with MediaStore `_ID`s and with persisted favourites, so the two
 * properties that matter are that they never look like a local id and never move.
 */
class RemoteIdTest {

    /** Real eleven-character YouTube ids. */
    private val ids = listOf(
        "dQw4w9WgXcQ", "kJQP7kiw5Fk", "9bZkp7q19f0", "RgKAFK5djSk", "OPf0YbXqDm0",
        "fJ9rUzIMcZQ", "hT_nvWreIhg", "CevxZvSJLk8", "YQHsXMglC9A", "60ItHLz5WEA",
    )

    @Test
    fun `always negative, so a remote id can never collide with a MediaStore id`() {
        // The empty string hashes to 0, which is the one input that could land on zero without the
        // offset in remoteId. It is the case worth pinning.
        (ids + "").forEach { id ->
            assertTrue("remoteId($id) = ${remoteId(id)}", remoteId(id) < 0)
        }
    }

    @Test
    fun `stable across calls, because favourites are persisted against it`() {
        ids.forEach { assertEquals(remoteId(it), remoteId(it)) }
    }

    @Test
    fun `distinct across a sample`() {
        assertEquals(ids.size, ids.map(::remoteId).toSet().size)
    }

    @Test
    fun `different videos do not share an id`() {
        assertNotEquals(remoteId("dQw4w9WgXcQ"), remoteId("dQw4w9WgXcR"))
    }
}

package cc.midolog.util

import java.util.UUID

object IdGenerator {
    /**
     * Generates a unique identifier with the given prefix.
     *
     * @param prefix The prefix to prepend to the generated ID
     * @return A unique identifier in the format `{prefix}_{12-char-uuid}`
     */
    fun generate(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
}

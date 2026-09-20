package ml.docilealligator.infinityforreddit.bubble

import android.content.Intent

/**
 * Everything a bubbled thread needs to identify itself. It is carried in the bubble's intent
 * extras rather than stored: a bubble exists only while the user keeps it open, so there is
 * nothing to remember between sessions.
 *
 * Shortcut intents are persisted by the system as a PersistableBundle, so every field here has
 * to be a plain String.
 */
data class BubbleThread(
    val accountName: String,
    val postId: String,
    val commentId: String,
    val title: String,
    val subredditName: String,
    val iconUrl: String?
) {
    val threadId: String get() = "$postId:$commentId"

    /** The thing a top level reply in this thread should be posted against. */
    val rootFullname: String get() =
        if (commentId.isEmpty()) "t3_$postId" else "t1_$commentId"

    fun putInto(intent: Intent): Intent = intent
        .putExtra(EXTRA_ACCOUNT_NAME, accountName)
        .putExtra(EXTRA_POST_ID, postId)
        .putExtra(EXTRA_COMMENT_ID, commentId)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_SUBREDDIT_NAME, subredditName)
        .putExtra(EXTRA_ICON_URL, iconUrl)

    companion object {
        const val EXTRA_ACCOUNT_NAME = "EBTAN"
        const val EXTRA_POST_ID = "EBTPI"
        const val EXTRA_COMMENT_ID = "EBTCI"
        const val EXTRA_TITLE = "EBTT"
        const val EXTRA_SUBREDDIT_NAME = "EBTSN"
        const val EXTRA_ICON_URL = "EBTIU"

        fun from(intent: Intent): BubbleThread? {
            val postId = intent.getStringExtra(EXTRA_POST_ID) ?: return null
            return BubbleThread(
                accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty(),
                postId = postId,
                commentId = intent.getStringExtra(EXTRA_COMMENT_ID).orEmpty(),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                subredditName = intent.getStringExtra(EXTRA_SUBREDDIT_NAME).orEmpty(),
                iconUrl = intent.getStringExtra(EXTRA_ICON_URL)
            )
        }
    }
}

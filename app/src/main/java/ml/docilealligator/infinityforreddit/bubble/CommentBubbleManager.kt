package ml.docilealligator.infinityforreddit.bubble

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.FileProvider
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.activities.CommentBubbleActivity
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.comment.FetchComment
import ml.docilealligator.infinityforreddit.comment.SendComment
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.network.AnyAccountAccessTokenAuthenticator
import ml.docilealligator.infinityforreddit.subreddit.FetchSubredditData
import ml.docilealligator.infinityforreddit.subreddit.SubredditData
import ml.docilealligator.infinityforreddit.thing.SortType
import ml.docilealligator.infinityforreddit.utils.NotificationUtils
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Puts a Reddit comment thread into an Android chat bubble so it can be read alongside other
 * apps.
 *
 * The bubble is deliberately not a messaging feature: nothing is stored, nothing is polled in
 * the background and the notification that carries the bubble is silent. A thread is fetched
 * only while its bubble is open, and a bubble only ever appears because the user asked for it.
 */
class CommentBubbleManager(
    private val applicationContext: Application,
    private val redditDataRoomDatabase: RedditDataRoomDatabase,
    private val customThemeWrapper: CustomThemeWrapper,
    private val executor: Executor,
    private val retrofit: Retrofit,
    private val oauthRetrofit: Retrofit,
    private val currentAccountSharedPreferences: SharedPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Opens [thread] as a bubble. [seedAuthor] and [seedBody] are only what the carrier
     * notification shows behind the bubble; the bubble itself loads the thread when it opens.
     */
    fun openBubble(thread: BubbleThread, seedAuthor: String, seedBody: String) {
        if (!bubblesSupported()) {
            return
        }
        scope.launch {
            val icon = loadIcon(thread)
            val person = Person.Builder()
                .setName(seedAuthor.ifEmpty { thread.title })
                .setKey(thread.threadId)
                .setIcon(icon)
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(
                applicationContext,
                ShortcutInfoCompat.Builder(applicationContext, thread.threadId)
                    .setLocusId(LocusIdCompat(thread.threadId))
                    .setShortLabel(thread.title.take(SHORT_LABEL_MAX_LENGTH))
                    .setLongLabel(thread.title)
                    .setIcon(icon)
                    .setPerson(person)
                    .setCategories(setOf(SHORTCUT_CATEGORY_CONVERSATION))
                    .setLongLived(true)
                    .setIntent(bubbleIntent(thread))
                    .build()
            )

            val notificationManager = NotificationUtils.getNotificationManager(applicationContext)
            notificationManager.createNotificationChannel(
                NotificationChannelCompat.Builder(
                    NotificationUtils.CHANNEL_ID_COMMENT_BUBBLES,
                    // Low: this notification is only a carrier for the bubble. It must never
                    // buzz, ring or peek like a message.
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
                    .setName(NotificationUtils.CHANNEL_COMMENT_BUBBLES)
                    .setVibrationEnabled(false)
                    .setSound(null, null)
                    .build()
            )

            val contentPendingIntent = PendingIntent.getActivity(
                applicationContext, notificationId(thread.threadId), bubbleIntent(thread),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val style = NotificationCompat.MessagingStyle(person)
                .setConversationTitle(thread.title)
                .setGroupConversation(true)
                .addMessage(seedBody.ifEmpty { thread.title }, System.currentTimeMillis(), person)

            val builder = NotificationCompat.Builder(
                applicationContext, NotificationUtils.CHANNEL_ID_COMMENT_BUBBLES
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(customThemeWrapper.colorPrimaryLightTheme)
                .setStyle(style)
                .setShortcutId(thread.threadId)
                .setLocusId(LocusIdCompat(thread.threadId))
                .addPerson(person)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent)
                .setBubbleMetadata(
                    NotificationCompat.BubbleMetadata.Builder(contentPendingIntent, icon)
                        .setDesiredHeight(BUBBLE_DESIRED_HEIGHT_DP)
                        .setAutoExpandBubble(true)
                        .setSuppressNotification(false)
                        .build()
                )

            try {
                notificationManager.notify(notificationId(thread.threadId), builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    /** Takes the bubble away entirely: no notification, no conversation shortcut. */
    fun removeBubble(threadId: String) {
        NotificationUtils.getNotificationManager(applicationContext)
            .cancel(notificationId(threadId))
        ShortcutManagerCompat.removeLongLivedShortcuts(applicationContext, listOf(threadId))
    }

    /** Loads the thread as a flat list of comments, newest state each time. Null on failure. */
    suspend fun fetchThread(thread: BubbleThread): List<Comment>? {
        val account = if (thread.accountName == Account.ANONYMOUS_ACCOUNT) {
            null
        } else {
            redditDataRoomDatabase.accountDao().getAccountData(thread.accountName)
        }
        val fetchRetrofit = if (account == null) retrofit else oauthRetrofit

        val comments = suspendCancellableCoroutine<List<Comment>?> { continuation ->
            FetchComment.fetchComments(
                executor, Handler(Looper.getMainLooper()), fetchRetrofit, account?.accessToken,
                account?.accountName ?: Account.ANONYMOUS_ACCOUNT, thread.postId,
                thread.commentId.ifEmpty { null }, SortType.Type.NEW, "0", true, CommentFilter(),
                object : FetchComment.FetchCommentListener {
                    override fun onFetchCommentSuccess(
                        expandedComments: ArrayList<Comment>?, parentId: String?,
                        children: ArrayList<String>?
                    ) {
                        continuation.resume(expandedComments ?: emptyList())
                    }

                    override fun onFetchCommentFailed() {
                        continuation.resume(null)
                    }
                }
            )
        }

        // "load more"/"continue thread" placeholders carry no body and would show as blank rows.
        return comments?.filter {
            it.placeholderType == Comment.NOT_PLACEHOLDER && it.fullName != null
        }
    }

    /** Replies to [replyTo], falling back to the thread root. Returns an error message or null. */
    suspend fun sendReply(thread: BubbleThread, text: String, replyTo: String?): String? {
        val account = redditDataRoomDatabase.accountDao().getAccountData(thread.accountName)
            ?: return applicationContext.getString(R.string.comment_bubble_no_account)

        val parentFullname = replyTo?.takeIf { it.isNotEmpty() } ?: thread.rootFullname

        val newAuthenticatorOauthRetrofit = oauthRetrofit.newBuilder()
            .client(
                OkHttpClient.Builder()
                    .authenticator(
                        AnyAccountAccessTokenAuthenticator(
                            retrofit, redditDataRoomDatabase, account, currentAccountSharedPreferences
                        )
                    )
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                    .build()
            )
            .build()

        val result: Result<Comment?> = suspendCancellableCoroutine { continuation ->
            SendComment.sendComment(
                applicationContext, executor, Handler(Looper.getMainLooper()), text, parentFullname,
                0, emptyList(), null, newAuthenticatorOauthRetrofit, account,
                object : SendComment.SendCommentListener {
                    override fun sendCommentSuccess(comment: Comment?) {
                        continuation.resume(Result.success(comment))
                    }

                    override fun sendCommentFailed(errorMessage: String?) {
                        continuation.resume(Result.failure(Exception(errorMessage)))
                    }
                }
            )
        }

        return if (result.isFailure) {
            result.exceptionOrNull()?.message
                ?: applicationContext.getString(R.string.comment_bubble_reply_failed)
        } else {
            null
        }
    }

    private fun bubbleIntent(thread: BubbleThread): Intent {
        return thread.putInto(
            Intent(applicationContext, CommentBubbleActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
        )
    }

    /**
     * Bubble icons have to be content URIs: the platform refuses icons of other types. The
     * bubble wears the subreddit's icon, the closest thing a thread has to a chat avatar.
     */
    private suspend fun loadIcon(thread: BubbleThread): IconCompat {
        val url = subredditIconUrl(thread)
        val bitmap = withContext(Dispatchers.IO) {
            val subredditIcon = if (url.isNullOrEmpty()) {
                null
            } else {
                try {
                    Glide.with(applicationContext).asBitmap().load(url)
                        .submit(ICON_SIZE_PX, ICON_SIZE_PX).get()
                } catch (e: Exception) {
                    null
                }
            }
            subredditIcon ?: launcherIconBitmap()
        }

        return bitmap?.let { iconContentUri(thread.threadId, it) }
            ?.let { IconCompat.createWithAdaptiveBitmapContentUri(it) }
            ?: IconCompat.createWithResource(applicationContext, R.mipmap.ic_launcher)
    }

    private suspend fun subredditIconUrl(thread: BubbleThread): String? {
        if (!thread.iconUrl.isNullOrEmpty()) {
            return thread.iconUrl
        }
        if (thread.subredditName.isEmpty()) {
            return null
        }

        val cached = redditDataRoomDatabase.subredditDao()
            .getSubredditData(thread.subredditName)?.iconUrl
        if (!cached.isNullOrEmpty()) {
            return cached
        }

        val account = if (thread.accountName == Account.ANONYMOUS_ACCOUNT) {
            null
        } else {
            redditDataRoomDatabase.accountDao().getAccountData(thread.accountName)
        }

        return suspendCancellableCoroutine<String?> { continuation ->
            FetchSubredditData.fetchSubredditData(
                executor, Handler(Looper.getMainLooper()),
                if (account == null) null else oauthRetrofit, retrofit,
                thread.subredditName, account?.accessToken,
                object : FetchSubredditData.FetchSubredditDataListener {
                    override fun onFetchSubredditDataSuccess(
                        subredditData: SubredditData?, nCurrentOnlineSubscribers: Int
                    ) {
                        continuation.resume(subredditData?.iconUrl)
                    }

                    override fun onFetchSubredditDataFail(isQuarantined: Boolean) {
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    private fun launcherIconBitmap(): Bitmap? {
        return try {
            val drawable = applicationContext.packageManager
                .getApplicationIcon(applicationContext.packageName)
            val bitmap = createBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun iconContentUri(threadId: String, bitmap: Bitmap): Uri? {
        return try {
            val directory = File(applicationContext.cacheDir, "bubble_icons")
            directory.mkdirs()
            val file = File(directory, "${threadId.hashCode()}.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val uri = FileProvider.getUriForFile(
                applicationContext, "${applicationContext.packageName}.provider", file
            )
            applicationContext.grantUriPermission(
                "com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val BUBBLE_DESIRED_HEIGHT_DP = 600
        private const val SHORT_LABEL_MAX_LENGTH = 40
        private const val ICON_SIZE_PX = 128
        private const val SHORTCUT_CATEGORY_CONVERSATION = "android.shortcut.conversation"

        /** Conversation bubbles anchored to a shortcut are only available from Android 11. */
        @JvmStatic
        fun bubblesSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        /**
         * Whether the user has left bubbles enabled for us. When they have not, the carrier
         * notification still appears but will never pop out as a bubble.
         */
        @JvmStatic
        fun bubblesAllowed(context: Context): Boolean {
            if (!bubblesSupported()) {
                return false
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            return notificationManager?.bubblePreference != NotificationManager.BUBBLE_PREFERENCE_NONE
        }

        @JvmStatic
        fun notificationId(threadId: String) =
            NotificationUtils.COMMENT_BUBBLE_NOTIFICATION_ID_BASE + (threadId.hashCode() and 0xFFFF)
    }
}

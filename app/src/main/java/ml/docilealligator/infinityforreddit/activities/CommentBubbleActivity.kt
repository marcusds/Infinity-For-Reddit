package ml.docilealligator.infinityforreddit.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.bubble.BubbleThread
import ml.docilealligator.infinityforreddit.bubble.CommentBubbleManager
import ml.docilealligator.infinityforreddit.viewmodels.BubbleThreadItem
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.customviews.compose.LocalAppTheme
import ml.docilealligator.infinityforreddit.customviews.compose.AppTheme
import ml.docilealligator.infinityforreddit.customviews.compose.CustomTextField
import ml.docilealligator.infinityforreddit.customviews.compose.LocalTypography
import ml.docilealligator.infinityforreddit.customviews.compose.PrimaryText
import ml.docilealligator.infinityforreddit.customviews.compose.SecondaryText
import ml.docilealligator.infinityforreddit.customviews.compose.ThemedTopAppBar
import ml.docilealligator.infinityforreddit.viewmodels.CommentBubbleViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Named

/**
 * The expanded content of a comment thread bubble: the thread rendered as a chat, with an
 * always visible reply box.
 */
class CommentBubbleActivity : BaseActivity() {
    @Inject
    @Named("default")
    lateinit var mSharedPreferences: SharedPreferences

    @Inject
    @Named("current_account")
    lateinit var mCurrentAccountSharedPreferences: SharedPreferences

    @Inject
    lateinit var mCustomThemeWrapper: CustomThemeWrapper

    @Inject
    lateinit var mCommentBubbleManager: CommentBubbleManager

    private lateinit var viewModel: CommentBubbleViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as Infinity).appComponent.inject(this)

        super.onCreate(savedInstanceState)

        val thread = BubbleThread.from(intent)
        if (thread == null) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
                enableEdgeToEdge()
            }
        }

        viewModel = ViewModelProvider.create(
            this,
            CommentBubbleViewModel.provideFactory(thread, mCommentBubbleManager)
        )[CommentBubbleViewModel::class.java]

        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = customThemeWrapper.isLightStatusBar

        val timeFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        setContent {
            AppTheme(customThemeWrapper.themeType, mSharedPreferences) {
                val items by viewModel.visibleItems.collectAsStateWithLifecycle()
                val openThreads by viewModel.openThreads.collectAsStateWithLifecycle()
                val isSending by viewModel.isSending.collectAsStateWithLifecycle()
                val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

                var replyText by remember { mutableStateOf("") }
                val listState = rememberLazyListState()
                val snackbarHostState = remember { SnackbarHostState() }

                BackHandler(enabled = openThreads.isNotEmpty()) {
                    viewModel.closeThread()
                }

                LaunchedEffect(items.size, openThreads.size) {
                    if (items.isNotEmpty()) {
                        listState.animateScrollToItem(items.size - 1)
                    }
                }

                LaunchedEffect(errorMessage) {
                    errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.errorMessageShown()
                    }
                }

                LaunchedEffect(isSending) {
                    if (!isSending && errorMessage == null) {
                        replyText = ""
                    }
                }

                Scaffold(
                    topBar = {
                        ThemedTopAppBar(
                            titleStringResId = R.string.comment_bubble_activity_label,
                            isImmersiveInterfaceEnabled = isImmersiveInterfaceEnabled,
                            windowInsetsController = windowInsetsController,
                            actions = {
                                IconButton(onClick = { viewModel.refresh() }) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.ic_refresh_day_night_24dp),
                                        tint = Color(LocalAppTheme.current.toolbarPrimaryTextAndIconColor),
                                        contentDescription = getString(R.string.comment_bubble_refresh)
                                    )
                                }

                                IconButton(onClick = {
                                    startActivity(
                                        Intent(
                                            this@CommentBubbleActivity,
                                            ViewPostDetailActivity::class.java
                                        ).apply {
                                            putExtra(
                                                ViewPostDetailActivity.EXTRA_POST_ID, thread.postId
                                            )
                                            putExtra(
                                                ViewPostDetailActivity.EXTRA_SINGLE_COMMENT_ID,
                                                thread.commentId
                                            )
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.ic_comment_toolbar_24dp),
                                        tint = Color(LocalAppTheme.current.toolbarPrimaryTextAndIconColor),
                                        contentDescription = null
                                    )
                                }

                                IconButton(onClick = {
                                    mCommentBubbleManager.removeBubble(thread.threadId)
                                    finish()
                                }) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.ic_delete_day_night_24dp),
                                        tint = Color(LocalAppTheme.current.toolbarPrimaryTextAndIconColor),
                                        contentDescription = null
                                    )
                                }
                            },
                            // Back only ever leaves the open thread. Closing the bubble itself
                            // is the system's job; removing it is the trash action.
                            showNavigationIcon = openThreads.isNotEmpty()
                        ) {
                            viewModel.closeThread()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    contentWindowInsets = if (isImmersiveInterfaceEnabled) {
                        WindowInsets.safeDrawing
                    } else {
                        WindowInsets.navigationBars
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(LocalAppTheme.current.backgroundColor))
                            .padding(innerPadding)
                    ) {
                        SecondaryText(
                            text = if (openThreads.isEmpty()) {
                                thread.title
                            } else {
                                getString(R.string.comment_bubble_thread_depth, openThreads.size)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        HorizontalDivider(color = Color(LocalAppTheme.current.dividerColor))

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items, key = { it.comment.fullName }) { item ->
                                MessageRow(
                                    item = item,
                                    timeFormatter = timeFormatter,
                                    openThreadLabel = { count ->
                                        resources.getQuantityString(
                                            R.plurals.comment_bubble_replies, count, count
                                        )
                                    },
                                    onOpenThread = { viewModel.openThread(item.comment.fullName) }
                                )
                            }
                        }

                        HorizontalDivider(color = Color(LocalAppTheme.current.dividerColor))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomTextField(
                                modifier = Modifier.weight(1f),
                                value = replyText,
                                placeholder = getString(R.string.comment_bubble_reply_hint),
                                maxLines = 4,
                                onValueChange = { replyText = it }
                            )

                            IconButton(
                                onClick = { viewModel.sendReply(replyText) },
                                enabled = !isSending && replyText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_send_black_24dp),
                                    tint = Color(LocalAppTheme.current.colorPrimary),
                                    contentDescription = getString(R.string.send)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::viewModel.isInitialized) {
            viewModel.refresh()
        }
    }

    override fun getDefaultSharedPreferences(): SharedPreferences = mSharedPreferences

    override fun getCurrentAccountSharedPreferences(): SharedPreferences =
        mCurrentAccountSharedPreferences

    override fun getCustomThemeWrapper(): CustomThemeWrapper = mCustomThemeWrapper

    override fun applyCustomTheme() {

    }
}

@Composable
private fun MessageRow(
    item: BubbleThreadItem,
    timeFormatter: DateFormat,
    openThreadLabel: (Int) -> String,
    onOpenThread: () -> Unit
) {
    val comment = item.comment
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isSelf) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Color(
                        if (item.isSelf) {
                            LocalAppTheme.current.colorPrimaryLightTheme
                        } else {
                            LocalAppTheme.current.cardViewBackgroundColor
                        }
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SecondaryText(
                text = comment.author,
                fontSize = LocalTypography.current.fontSize.size12
            )
            PrimaryText(text = comment.commentRawText.orEmpty())
            SecondaryText(
                text = timeFormatter.format(Date(comment.commentTimeMillis)),
                fontSize = LocalTypography.current.fontSize.size10,
                modifier = Modifier.align(Alignment.End)
            )

            // Only offer to drill in from replies; the thread root is already open.
            if (item.replyCount > 0 && !item.isThreadRoot) {
                HorizontalDivider(
                    color = Color(LocalAppTheme.current.dividerColor),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = openThreadLabel(item.replyCount),
                    color = Color(LocalAppTheme.current.colorAccent),
                    fontFamily = LocalTypography.current.fontFamily,
                    fontSize = LocalTypography.current.fontSize.size12,
                    modifier = Modifier
                        .clickable(onClick = onOpenThread)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

package ml.docilealligator.infinityforreddit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ml.docilealligator.infinityforreddit.bubble.BubbleThread
import ml.docilealligator.infinityforreddit.bubble.CommentBubbleManager
import ml.docilealligator.infinityforreddit.comment.Comment

/** A comment plus what the bubble needs to know about its place in the thread. */
data class BubbleThreadItem(
    val comment: Comment,
    val isSelf: Boolean,
    val replyCount: Int,
    val isThreadRoot: Boolean
)

class CommentBubbleViewModel(
    val thread: BubbleThread,
    private val commentBubbleManager: CommentBubbleManager
) : ViewModel() {
    private val comments = MutableStateFlow(emptyList<Comment>())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    /** Fullnames of the threads we have drilled into, deepest last. Empty means the top level. */
    private val _openThreads = MutableStateFlow(emptyList<String>())
    val openThreads = _openThreads.asStateFlow()

    /**
     * The comments to show: the one whose thread is open (if any) followed by its direct
     * replies, each carrying how many replies it has so the UI can offer to open it.
     */
    val visibleItems = combine(comments, _openThreads) { allComments, openThreads ->
        val replyCounts = allComments.groupingBy { it.parentId.orEmpty() }.eachCount()
        val currentRoot = openThreads.lastOrNull() ?: thread.rootFullname

        val rootComment = if (openThreads.isEmpty()) {
            null
        } else {
            allComments.firstOrNull { it.fullName == currentRoot }
        }

        buildList {
            rootComment?.let { add(it.toItem(replyCounts, isThreadRoot = true)) }
            allComments
                .filter { it.parentId == currentRoot }
                .forEach { add(it.toItem(replyCounts, isThreadRoot = false)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun Comment.toItem(replyCounts: Map<String, Int>, isThreadRoot: Boolean) =
        BubbleThreadItem(
            comment = this,
            isSelf = author == thread.accountName,
            replyCount = replyCounts[fullName] ?: 0,
            isThreadRoot = isThreadRoot
        )

    /** Where a reply typed right now would be posted. */
    private val replyTarget: String
        get() = openThreads.value.lastOrNull() ?: thread.rootFullname

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        refresh()
    }

    fun openThread(fullname: String) {
        _openThreads.value = _openThreads.value + fullname
    }

    /** Returns false when there is no thread left to leave, so the caller can close the bubble. */
    fun closeThread(): Boolean {
        val openThreads = _openThreads.value
        if (openThreads.isEmpty()) {
            return false
        }
        _openThreads.value = openThreads.dropLast(1)
        return true
    }

    fun refresh() {
        if (_isLoading.value) {
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            commentBubbleManager.fetchThread(thread)?.let { comments.value = it }
            _isLoading.value = false
        }
    }

    fun sendReply(text: String) {
        if (_isSending.value || text.isBlank()) {
            return
        }
        _isSending.value = true
        val replyTo = replyTarget
        viewModelScope.launch {
            val error = commentBubbleManager.sendReply(thread, text, replyTo)
            _errorMessage.value = error
            _isSending.value = false
            if (error == null) {
                refresh()
            }
        }
    }

    fun errorMessageShown() {
        _errorMessage.value = null
    }

    companion object {
        fun provideFactory(
            thread: BubbleThread,
            commentBubbleManager: CommentBubbleManager
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return CommentBubbleViewModel(thread, commentBubbleManager) as T
                }
            }
        }
    }
}

package streamix.social

import streamix.api.ActivityRef
import streamix.api.ActivityReplyRef
import streamix.api.ForumCommentRef
import streamix.api.ForumThreadRef
import streamix.api.NotificationRef
import streamix.api.SocialComment

interface SocialRepository {
    fun activities(userId: String?, page: Int, perPage: Int): List<ActivityRef>
    fun activity(activityId: String, viewerId: String?): ActivityRef?
    fun createActivity(authorId: String, type: String, text: String?, mediaId: Long?, mediaTitle: String?): ActivityRef
    fun deleteActivity(activityId: String, actorId: String): Boolean
    fun replies(activityId: String, viewerId: String?, page: Int, perPage: Int): List<ActivityReplyRef>
    fun createReply(activityId: String, authorId: String, text: String): ActivityReplyRef
    fun toggleActivityLike(activityId: String, userId: String): ActivityRef?
    fun toggleActivitySubscription(activityId: String, userId: String): ActivityRef?

    fun comments(mediaId: Long, viewerId: String?, parentCommentId: String?, page: Int, perPage: Int): List<SocialComment>
    fun commentReplies(commentId: String, viewerId: String?, page: Int, perPage: Int): List<SocialComment>
    fun createComment(mediaId: Long, authorId: String, content: String, parentCommentId: String?): SocialComment
    fun deleteComment(commentId: String, actorId: String): Boolean
    fun editComment(commentId: String, actorId: String, content: String): SocialComment?
    fun voteComment(commentId: String, userId: String, vote: Int?): SocialComment?

    fun forumThreads(query: String?, page: Int, perPage: Int, viewerId: String?): List<ForumThreadRef>
    fun forumThread(threadId: String, viewerId: String?): ForumThreadRef?
    fun createForumThread(authorId: String, title: String, body: String, mediaIds: List<Long>): ForumThreadRef
    fun deleteForumThread(threadId: String, actorId: String): Boolean
    fun forumComments(threadId: String, viewerId: String?, page: Int, perPage: Int): List<ForumCommentRef>
    fun createForumComment(threadId: String, authorId: String, content: String, parentCommentId: String?): ForumCommentRef
    fun toggleThreadLike(threadId: String, userId: String): ForumThreadRef?
    fun toggleThreadSubscription(threadId: String, userId: String): ForumThreadRef?

    fun notifications(userId: String, page: Int, perPage: Int): List<NotificationRef>
    fun unreadNotificationCount(userId: String): Int
    fun markNotificationRead(notificationId: String, userId: String): Boolean
}

package streamix.social

import streamix.api.SocialPage

class SocialService(
    private val repository: SocialRepository
) {
    fun feed(viewerId: String, page: Int = 1, perPage: Int = 20) =
        page(repository.activities(viewerId, page, perPage), page, perPage)

    fun activities(viewerId: String?, page: Int = 1, perPage: Int = 20) =
        page(repository.activities(viewerId, page, perPage), page, perPage)

    fun activity(id: String, viewerId: String?) =
        repository.activity(id, viewerId)

    fun replies(activityId: String, viewerId: String?, page: Int = 1, perPage: Int = 20) =
        page(repository.replies(activityId, viewerId, page, perPage), page, perPage)

    fun comments(mediaId: Long, viewerId: String?, parentCommentId: String?, page: Int = 1, perPage: Int = 20) =
        page(repository.comments(mediaId, viewerId, parentCommentId, page, perPage), page, perPage)

    fun forumThreads(query: String?, viewerId: String?, page: Int = 1, perPage: Int = 20) =
        page(repository.forumThreads(query, page, perPage, viewerId), page, perPage)

    fun forumThread(id: String, viewerId: String?) =
        repository.forumThread(id, viewerId)

    fun forumComments(threadId: String, viewerId: String?, page: Int = 1, perPage: Int = 20) =
        page(repository.forumComments(threadId, viewerId, page, perPage), page, perPage)

    fun notifications(userId: String, page: Int = 1, perPage: Int = 20) =
        page(repository.notifications(userId, page, perPage), page, perPage)

    fun unreadNotificationCount(userId: String) =
        repository.unreadNotificationCount(userId)

    private fun <T> page(items: List<T>, page: Int, perPage: Int): SocialPage<T> {
        val safePage = page.coerceAtLeast(1)
        val safePerPage = perPage.coerceIn(1, 100)
        return SocialPage(
            items = items,
            page = safePage,
            perPage = safePerPage,
            hasNextPage = items.size == safePerPage,
            total = null
        )
    }
}

        val users = read()
        val current = users.firstOrNull { it.id == user.id }
            ?: error("user not found: ${user.id}")
        require(users.none { it.id != user.id && it.username.equals(user.username, ignoreCase = true) }) {
            "username already exists"
        }

        val updated = current.copy(
            username = user.username,
            displayName = user.displayName,
            bio = user.bio,
            avatarUrl = user.avatarUrl,
            bannerUrl = user.bannerUrl,
            followingUserIds = current.followingUserIds,
            blockedUserIds = current.blockedUserIds,
            isAdmin = user.isAdmin,
            isModerator = user.isModerator
        )
        write(users.map { if (it.id == user.id) updated else it })
        updated.toPublic()
    }
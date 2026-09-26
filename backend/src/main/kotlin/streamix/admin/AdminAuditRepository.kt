package streamix.admin

import streamix.api.AdminAuditEntry

interface AdminAuditRepository {
    fun append(entry: AdminAuditEntry)
    fun recent(limit: Int = 100): List<AdminAuditEntry>
}

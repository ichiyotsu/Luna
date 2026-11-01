package com.m3browser

object SyncMerge {
    fun mergeEntries(local: List<PageEntry>, remote: List<PageEntry>): List<PageEntry> {
        val map = LinkedHashMap<String, PageEntry>()
        fun consider(entry: PageEntry) {
            if (entry.url.isBlank()) return
            val current = map[entry.url]
            map[entry.url] = when {
                current == null -> entry
                entry.updatedAt > current.updatedAt -> entry
                entry.updatedAt < current.updatedAt -> current
                entry.deleted -> entry
                else -> current
            }
        }
        local.forEach(::consider)
        remote.forEach(::consider)
        return map.values.toList()
    }

    fun pruneTombstones(items: List<PageEntry>, now: Long, maxAgeMs: Long = 180L * 24 * 60 * 60 * 1000): List<PageEntry> =
        items.filter { !it.deleted || now - it.updatedAt < maxAgeMs }

    fun mergeSettings(local: AppSettings, remote: AppSettings?): AppSettings =
        if (remote != null && remote.updatedAt > local.updatedAt) remote else local
}

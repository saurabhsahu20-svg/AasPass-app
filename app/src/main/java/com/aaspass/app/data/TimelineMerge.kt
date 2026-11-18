package com.aaspass.app.data

/**
 * Merge paginated history into an active Firestore window without showing a stale
 * copy of an edited/deleted item. Live snapshots win when the same id appears twice.
 * Caller can sort the result using its message timestamp.
 */
object TimelineMerge {
    fun <T> latestWins(live: List<T>, older: List<T>, idOf: (T) -> String): List<T> {
        val seen = HashSet<String>(live.size + older.size)
        val output = ArrayList<T>(live.size + older.size)
        for (item in live) if (seen.add(idOf(item))) output.add(item)
        for (item in older) if (seen.add(idOf(item))) output.add(item)
        return output
    }
}

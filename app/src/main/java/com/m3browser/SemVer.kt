package com.m3browser

object SemVer {
    fun parse(raw: String): List<Int> {
        val core = raw.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (core.isBlank()) return listOf(0)
        return core.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    /** True when [remote] (a tag or version) is strictly newer than [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parse(remote)
        val b = parse(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val da = a.getOrElse(i) { 0 }
            val db = b.getOrElse(i) { 0 }
            if (da != db) return da > db
        }
        return false
    }

    fun same(remote: String, local: String): Boolean = !isNewer(remote, local) && !isNewer(local, remote)
}

package com.example.network

/** Cycle through bounded discovery candidates instead of retrying one stale QR address forever. */
class RoomAddresses {
    private val candidates = mutableListOf<String>()
    private var next = 0
    @Synchronized fun offer(address: String) {
        if (!isLocalAddress(address) || address in candidates) return
        if (candidates.size == 8) { candidates.removeAt(0); next = 0 }
        candidates.add(address)
    }
    @Synchronized fun next(): String? {
        if (candidates.isEmpty()) return null
        val address = candidates[next % candidates.size]
        next = (next + 1) % candidates.size
        return address
    }
    companion object {
        fun isLocalAddress(address: String): Boolean {
            if (address.contains(':')) return address.length <= 80 && address.matches(Regex("[0-9a-fA-F:.%a-zA-Z0-9_-]+")) &&
                address != "::" && !address.startsWith("ff", ignoreCase = true)
            val parts = address.split('.')
            if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return false
            return parts[0].toInt() in 1..223 && parts.last().toInt() != 255
        }
    }
}

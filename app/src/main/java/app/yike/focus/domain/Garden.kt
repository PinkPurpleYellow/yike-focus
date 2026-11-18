package app.yike.focus.domain

/** Rewards are derived from durable records, never from timer callbacks or wall time. */
object Garden {
    fun earned(records: List<FocusRecord>): Set<String> = records.asSequence()
        .filter { it.completed }.map { it.id }.toSet()
    fun validUsed(records: List<FocusRecord>, used: Set<String>) = used.intersect(earned(records))
    fun available(records: List<FocusRecord>, used: Set<String>) = earned(records) - used
    fun harvests(records: List<FocusRecord>, used: Set<String>) = validUsed(records, used).size / 4
    fun stage(records: List<FocusRecord>, used: Set<String>) = validUsed(records, used).size % 4
    const val VARIETIES=6
    fun varietyCount(harvests: Int, index: Int): Int {
        require(harvests>=0 && index in 0 until VARIETIES)
        return if(harvests<=index) 0 else (harvests-1-index)/VARIETIES+1
    }
}

/** Monotonic input makes clock changes irrelevant to the secret gesture. */
class SecretTapGate {
    private var first = 0L
    private var taps = 0
    fun tap(now: Long): Boolean {
        if (taps == 0 || now - first > 5_000 || now < first) { first = now; taps = 0 }
        taps++
        if (taps < 7) return false
        taps = 0
        return true
    }
}

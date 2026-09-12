package fm.corus.android.domain

/** Caller-scoped summary from the already-loaded profile; never merged into a public user. */
object ProfileTrophySummary {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()
    fun remember(viewer:String?,profile:String,count:Int?) {
        if(viewer == null)return
        val key="$viewer:$profile"
        if(count == null || count < 0)values.remove(key) else values[key]=System.currentTimeMillis() to count
    }
    fun count(viewer:String?,profile:String):Int? = values["$viewer:$profile"]?.takeIf { System.currentTimeMillis()-it.first<300_000 }?.second
}

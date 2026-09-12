package fm.corus.android.domain

object CityChatPolicy {
    const val OWNER = "v9s6F4HKLLbEmfX3Tc6MX3lHfpl2"
    fun canEditIdentity(cityId: String?, uid: String?) = cityId.isNullOrBlank() || uid == OWNER
    fun canEditMessages(cityId: String?) = cityId.isNullOrBlank()
    fun canAddMembers(cityId: String?) = cityId.isNullOrBlank()
    fun canDeleteMessages(cityId: String?, uid: String?) = !cityId.isNullOrBlank() && uid == OWNER
    fun canLeave(cityId: String?, uid: String?) = cityId.isNullOrBlank() || uid != OWNER
}

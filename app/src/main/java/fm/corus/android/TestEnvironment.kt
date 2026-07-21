package fm.corus.android

object TestEnvironment {

    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val FUNCTIONS_PORT = 5001
    const val STORAGE_PORT = 9199

    val host: String = if (BuildConfig.DEBUG) BuildConfig.FIREBASE_EMULATOR_HOST.trim() else ""

    val isActive: Boolean = host.isNotEmpty()
}

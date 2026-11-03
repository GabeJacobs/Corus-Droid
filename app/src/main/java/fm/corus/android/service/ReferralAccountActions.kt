package fm.corus.android.service

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fm.corus.android.data.repository.UserRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReferralAccountEntryPoint {
    fun userRepository(): UserRepository
}

/**
 * Use the same server safety rules, persisted following set and profile caches as other follow
 * buttons.
 */
suspend fun followReferralInviter(context: Context, inviterId: String) {
    val uid = checkNotNull(FirebaseAuth.getInstance().currentUser?.uid)
    EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReferralAccountEntryPoint::class.java,
        )
        .userRepository()
        .followUser(uid, inviterId)
}

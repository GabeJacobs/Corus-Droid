package fm.corus.android.ui.screens.subscription

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Package
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.PurchaseOutcome
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PaywallSource(val subtitle: String, val analyticsName: String) {
    DEFAULT("Support Corus. Get Perks.", "default"),
    VINYL_PICKER("Customize your vinyl and more.", "vinyl_picker"),
    FRAME_PICKER("Customize your frame and more.", "frame_picker"),
    FLAIR_PICKER("Customize your badge flair and more.", "flair_picker"),
    SPIN_PICKER("Make your vinyl spin and more.", "spin_picker"),
    STYLE_PICKER("Customize your profile style and more.", "style_picker"),
    POST_LIMIT("Unlock unlimited posts.", "post_limit"),
    FIRST_POST("Support Corus. Get Perks.", "first_post"),
    TENTH_POST("Support Corus. Get Perks.", "tenth_post"),
    PLAYLIST_LIMIT("Unlock playlist generation.", "playlist_limit"),
    SAVE_LIMIT("Unlock unlimited saves.", "save_cap"),
    SETTINGS("Support Corus. Get Perks.", "settings"),
    NEW_RELEASE_FILTER("Unlock new release feeds.", "new_release_filter"),
}

@HiltViewModel
class CymbalClubViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val analyticsService: AnalyticsService,
    val remoteConfig: RemoteConfigService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val isClubMember = subscriptionRepository.isClubMember
    val packages = subscriptionRepository.packages

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val source: PaywallSource = savedStateHandle.get<String>("source")
        ?.let { name -> PaywallSource.entries.find { it.name == name } }
        ?: PaywallSource.DEFAULT

    val defaultPlan: String
        get() = if (remoteConfig.paywallDefaultYearly) "yearly" else "monthly"

    init {
        subscriptionRepository.fetchOfferings()
        viewModelScope.launch { subscriptionRepository.checkStatus() }
    }

    fun logPaywallShown() {
        analyticsService.logPaywallShown(source.analyticsName, defaultPlan)
    }

    fun logPaywallDismissed() {
        analyticsService.logPaywallDismissed()
    }

    fun purchase(activity: Activity, pkg: Package, planName: String) {
        _isPurchasing.value = true
        _errorMessage.value = null
        analyticsService.logPurchaseStarted(planName)
        subscriptionRepository.purchase(activity, pkg) { outcome ->
            _isPurchasing.value = false
            when (outcome) {
                is PurchaseOutcome.Success -> {
                    analyticsService.logPurchaseCompleted(planName)
                    _purchaseResult.value = PurchaseResult.Success
                }
                is PurchaseOutcome.Cancelled -> {
                    _purchaseResult.value = PurchaseResult.Cancelled
                }
                is PurchaseOutcome.Failed -> {
                    analyticsService.logPurchaseFailed(planName, outcome.error)
                    _errorMessage.value = "Something went wrong. Please try again."
                    _purchaseResult.value = PurchaseResult.Failed
                }
            }
        }
    }

    fun restorePurchases() {
        _isPurchasing.value = true
        _errorMessage.value = null
        subscriptionRepository.restorePurchases { success ->
            _isPurchasing.value = false
            if (success) {
                analyticsService.logPurchaseRestored()
                _purchaseResult.value = PurchaseResult.Restored
            } else {
                analyticsService.logPurchaseRestoreFailed("No active subscription found")
                _errorMessage.value = "No active subscription found."
                _purchaseResult.value = PurchaseResult.NothingToRestore
            }
        }
    }

    fun clearResult() {
        _purchaseResult.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    enum class PurchaseResult {
        Success, Failed, Cancelled, Restored, NothingToRestore
    }
}

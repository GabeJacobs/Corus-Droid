package fm.corus.android.ui.screens.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Package
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CymbalClubViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    val isClubMember = subscriptionRepository.isClubMember
    val packages = subscriptionRepository.packages

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult.asStateFlow()

    init {
        subscriptionRepository.fetchOfferings()
        subscriptionRepository.checkStatus()
    }

    fun purchase(activity: Activity, pkg: Package) {
        _isPurchasing.value = true
        subscriptionRepository.purchase(activity, pkg) { success ->
            _isPurchasing.value = false
            _purchaseResult.value = if (success) PurchaseResult.Success else PurchaseResult.Failed
        }
    }

    fun restorePurchases() {
        _isPurchasing.value = true
        subscriptionRepository.restorePurchases { success ->
            _isPurchasing.value = false
            _purchaseResult.value = if (success) PurchaseResult.Restored else PurchaseResult.NothingToRestore
        }
    }

    fun clearResult() {
        _purchaseResult.value = null
    }

    enum class PurchaseResult {
        Success, Failed, Restored, NothingToRestore
    }
}

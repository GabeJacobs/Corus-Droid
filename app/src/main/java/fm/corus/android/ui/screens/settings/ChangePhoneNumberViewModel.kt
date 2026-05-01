package fm.corus.android.ui.screens.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class CountryCode(
    val name: String,
    val flag: String,
    val dialCode: String,
) {
    companion object {
        val US = CountryCode("United States", "\uD83C\uDDFA\uD83C\uDDF8", "+1")
        val GB = CountryCode("United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7", "+44")
        val CA = CountryCode("Canada", "\uD83C\uDDE8\uD83C\uDDE6", "+1")
        val AU = CountryCode("Australia", "\uD83C\uDDE6\uD83C\uDDFA", "+61")
        val DE = CountryCode("Germany", "\uD83C\uDDE9\uD83C\uDDEA", "+49")
        val FR = CountryCode("France", "\uD83C\uDDEB\uD83C\uDDF7", "+33")
        val JP = CountryCode("Japan", "\uD83C\uDDEF\uD83C\uDDF5", "+81")
        val IN = CountryCode("India", "\uD83C\uDDEE\uD83C\uDDF3", "+91")
        val BR = CountryCode("Brazil", "\uD83C\uDDE7\uD83C\uDDF7", "+55")
        val MX = CountryCode("Mexico", "\uD83C\uDDF2\uD83C\uDDFD", "+52")

        val all = listOf(US, GB, CA, AU, DE, FR, JP, IN, BR, MX)
    }
}

@HiltViewModel
class ChangePhoneNumberViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _verificationCode = MutableStateFlow("")
    val verificationCode: StateFlow<String> = _verificationCode.asStateFlow()

    private val _selectedCountry = MutableStateFlow(CountryCode.US)
    val selectedCountry: StateFlow<CountryCode> = _selectedCountry.asStateFlow()

    private val _codeSent = MutableStateFlow(false)
    val codeSent: StateFlow<Boolean> = _codeSent.asStateFlow()

    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showSuccess = MutableStateFlow(false)
    val showSuccess: StateFlow<Boolean> = _showSuccess.asStateFlow()

    private val _lastSentAt = MutableStateFlow<Long?>(null)

    private var verificationId: String? = null

    fun onPhoneChanged(value: String) { _phoneNumber.value = value }
    fun onCodeChanged(value: String) { _verificationCode.value = value }
    fun onCountrySelected(country: CountryCode) { _selectedCountry.value = country }
    fun clearError() { _errorMessage.value = null }

    fun resendSecondsRemaining(): Int {
        val sentAt = _lastSentAt.value ?: return 0
        val elapsed = (System.currentTimeMillis() - sentAt) / 1000
        return maxOf(0, (60 - elapsed).toInt())
    }

    fun canResend(): Boolean = resendSecondsRemaining() == 0

    fun goBack() {
        _codeSent.value = false
        _verificationCode.value = ""
        _errorMessage.value = null
    }

    fun sendCode(activity: Activity) {
        val digits = _phoneNumber.value.filter { it.isDigit() }
        if (digits.length !in 4..15) {
            _errorMessage.value = "Please enter a valid phone number."
            return
        }

        _errorMessage.value = null
        _isSendingCode.value = true

        val fullNumber = "${_selectedCountry.value.dialCode}$digits"
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(fullNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-verify — update phone immediately
                    viewModelScope.launch { linkCredential(credential) }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    _isSendingCode.value = false
                    _errorMessage.value = "Something went wrong. Please try again."
                }

                override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vid
                    _lastSentAt.value = System.currentTimeMillis()
                    _codeSent.value = true
                    _isSendingCode.value = false
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verify() {
        val code = _verificationCode.value.filter { it.isDigit() }
        if (code.length != 6) {
            _errorMessage.value = "Please enter the 6-digit code."
            return
        }
        val vid = verificationId
        if (vid == null) {
            _errorMessage.value = "No verification in progress. Please request a new code."
            return
        }

        _errorMessage.value = null
        _isVerifying.value = true

        val credential = PhoneAuthProvider.getCredential(vid, code)
        viewModelScope.launch { linkCredential(credential) }
    }

    private suspend fun linkCredential(credential: PhoneAuthCredential) {
        try {
            val user = auth.currentUser ?: return
            user.updatePhoneNumber(credential).await()

            // Save to Firestore
            val digits = _phoneNumber.value.filter { it.isDigit() }
            val fullNumber = "${_selectedCountry.value.dialCode}$digits"
            userRepository.updateUserProfile(user.uid, mapOf("phoneNumber" to fullNumber))

            _isVerifying.value = false
            _isSendingCode.value = false
            _showSuccess.value = true
        } catch (e: Exception) {
            _isVerifying.value = false
            _isSendingCode.value = false
            val code = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
            _errorMessage.value = when (code) {
                "ERROR_INVALID_VERIFICATION_CODE" -> "Invalid verification code. Please try again."
                "ERROR_SESSION_EXPIRED" -> "Code expired. Please request a new one."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
                "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_PROVIDER_ALREADY_LINKED" ->
                    "This phone number is already linked to another account."
                "ERROR_REQUIRES_RECENT_LOGIN" ->
                    "For security, please sign out and sign back in, then try again."
                else -> "Something went wrong. Please try again."
            }
        }
    }
}

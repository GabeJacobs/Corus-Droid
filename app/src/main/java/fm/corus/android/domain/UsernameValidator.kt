package fm.corus.android.domain

object UsernameValidator {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 25

    sealed class Result {
        object Empty : Result()
        // Valid-so-far but under MIN_LENGTH. Distinct from Invalid so the UI can
        // stay neutral while the user is still typing toward 3 chars and only
        // surface the length message on an explicit submit tap.
        object TooShort : Result()
        object Valid : Result()
        data class Invalid(val message: String) : Result()
    }

    // Shared copy for both length failures (too short on submit, too long live)
    // and web's onboarding_username_invalid.
    val lengthRangeMessage: String
        get() = "$MIN_LENGTH-$MAX_LENGTH chars, must include a letter."

    fun clean(input: String): String =
        input.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(MAX_LENGTH)

    fun validate(input: String): Result {
        if (input.isEmpty()) return Result.Empty

        if (!input.all { it.isLetterOrDigit() || it == '_' || it == '.' }) {
            return Result.Invalid("Only letters, numbers, underscores, and periods")
        }

        if (input.contains("..")) {
            return Result.Invalid("Username can't have two periods in a row")
        }

        if (input.startsWith('.')) {
            return Result.Invalid("Username can't start with a period")
        }

        if (input.endsWith('.')) {
            return Result.Invalid("Username can't end with a period")
        }

        if (input.none { it.isLetter() }) {
            return Result.Invalid("Username must contain at least one letter")
        }

        // Length parity with web and the server rule (isValidUsername in
        // firestore.rules): 3 to 25 chars. Checked after the charset/letter
        // rules so length is the last thing standing between a well-formed
        // handle and validity.
        //   - Too long is a hard error shown live (a real limit was overshot);
        //     also the backstop for callers that skip clean() (which caps at 25).
        //   - Too short is NOT an error yet — the user is still typing toward 3
        //     chars, so return TooShort and let the UI stay neutral until submit.
        if (input.length > MAX_LENGTH) {
            return Result.Invalid(lengthRangeMessage)
        }
        if (input.length < MIN_LENGTH) {
            return Result.TooShort
        }

        return Result.Valid
    }

    // Reserved handles strangers can't register (brand, support, system,
    // impersonation-bait). Keep in sync with the iOS/web UsernameValidator
    // RESERVED sets and `reservedUsernames()` in backend/firestore.rules (the
    // server source of truth). Enforced via FirestoreDataSource.checkUsernameAvailable.
    val RESERVED: Set<String> = setOf(
        "corus", "corusapp", "corusofficial", "corusmedia", "corusfm",
        "corushq", "teamcorus", "corusclub", "official", "verified",
        "corushelp", "help", "support", "corussupport", "admin",
        "administrator", "moderator", "mod", "staff", "corusstaff",
        "team", "abuse", "report", "trust", "safety",
        "contact", "info", "feedback", "press", "media",
        "legal", "privacy", "security", "billing", "payments",
        "root", "system", "api", "www", "mail",
        "noreply", "notifications", "bot", "corusbot", "everyone",
        "founder", "ceo", "null", "anonymous", "user",
    )

    fun isReserved(input: String): Boolean = input.lowercase() in RESERVED
}

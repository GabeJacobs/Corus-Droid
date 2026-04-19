package fm.corus.android.domain

object UsernameValidator {

    const val MAX_LENGTH = 20

    sealed class Result {
        object Empty : Result()
        object Valid : Result()
        data class Invalid(val message: String) : Result()
    }

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

        if (input.none { it.isLetterOrDigit() }) {
            return Result.Invalid("Username must contain at least one letter or number")
        }

        return Result.Valid
    }
}

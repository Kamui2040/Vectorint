package io.github.kamui2040.vectorint.core

@JvmInline
value class AccountId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Account ID must not be blank" }
    }
}

data class Account(
    val id: AccountId,
    val name: String,
    val currentFunds: CurrentFunds,
    val includeInAvailableNow: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "Account name must not be blank" }
        require(name == name.trim()) { "Account name must not have surrounding whitespace" }
        require(name.length <= MAX_NAME_LENGTH) { "Account name is too long" }
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}

val LEGACY_DEFAULT_ACCOUNT_ID = AccountId("legacy-main")

fun CurrentFunds.asLegacyDefaultAccount(): Account =
    Account(
        id = LEGACY_DEFAULT_ACCOUNT_ID,
        name = "Main",
        currentFunds = this,
    )

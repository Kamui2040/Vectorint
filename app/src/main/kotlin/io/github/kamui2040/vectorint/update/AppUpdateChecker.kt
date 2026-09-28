package io.github.kamui2040.vectorint.update

internal interface AppUpdateChecker {
    fun checkForUpdate()
}

internal sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data object UpToDate : AppUpdateState

    data object UpdateStarted : AppUpdateState

    data object Cancelled : AppUpdateState

    data object Unavailable : AppUpdateState

    data object ReleasePageOpened : AppUpdateState

    data object Failed : AppUpdateState
}

internal const val VECTORINT_RELEASE_PAGE =
    "https://kamui2040.github.io/K2040-Android-Releases/apps/vectorint/"

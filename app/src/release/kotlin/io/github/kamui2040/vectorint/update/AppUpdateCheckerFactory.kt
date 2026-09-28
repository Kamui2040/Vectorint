package io.github.kamui2040.vectorint.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatActivity

internal fun createAppUpdateChecker(
    activity: AppCompatActivity,
    updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    onResult: (AppUpdateState) -> Unit,
): AppUpdateChecker = ExternalReleasePageUpdateChecker(activity, onResult)

private class ExternalReleasePageUpdateChecker(
    private val activity: AppCompatActivity,
    private val onResult: (AppUpdateState) -> Unit,
) : AppUpdateChecker {
    override fun checkForUpdate() {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VECTORINT_RELEASE_PAGE)))
            onResult(AppUpdateState.ReleasePageOpened)
        } catch (_: ActivityNotFoundException) {
            onResult(AppUpdateState.Failed)
        }
    }
}

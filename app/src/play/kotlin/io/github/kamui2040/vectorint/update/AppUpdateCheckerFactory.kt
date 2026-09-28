package io.github.kamui2040.vectorint.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

internal fun createAppUpdateChecker(
    activity: AppCompatActivity,
    updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    onResult: (AppUpdateState) -> Unit,
): AppUpdateChecker =
    PlayAppUpdateChecker(
        manager = AppUpdateManagerFactory.create(activity),
        updateLauncher = updateLauncher,
        onResult = onResult,
    )

private class PlayAppUpdateChecker(
    private val manager: AppUpdateManager,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val onResult: (AppUpdateState) -> Unit,
) : AppUpdateChecker {
    override fun checkForUpdate() {
        manager.appUpdateInfo
            .addOnSuccessListener { updateInfo ->
                val availability = updateInfo.updateAvailability()
                val canStartImmediateUpdate = updateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                when {
                    availability == UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                        onResult(AppUpdateState.UpToDate)
                    }

                    (
                        availability == UpdateAvailability.UPDATE_AVAILABLE ||
                            availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    ) &&
                        canStartImmediateUpdate -> {
                        val started =
                            runCatching {
                                manager.startUpdateFlowForResult(
                                    updateInfo,
                                    updateLauncher,
                                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                                )
                            }.getOrDefault(false)
                        onResult(if (started) AppUpdateState.UpdateStarted else AppUpdateState.Failed)
                    }

                    else -> onResult(AppUpdateState.Unavailable)
                }
            }.addOnFailureListener {
                onResult(AppUpdateState.Failed)
            }
    }
}

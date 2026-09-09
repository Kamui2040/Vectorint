package io.github.kamui2040.vectorint.backup

import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.UserSettings
import java.time.Instant

internal data class VectorintBackup(
    val createdAt: Instant,
    val data: BackupData,
    val settings: UserSettings,
    val sourceVersion: Int = VectorintBackupContract.VERSION,
)

internal object VectorintBackupContract {
    const val FORMAT = "io.github.kamui2040.vectorint.backup"
    const val VERSION = 6
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ACTIVITIES = 10_000
    const val MAX_RECURRING_ITEMS = 2_000
    const val MAX_CUSTOM_CATEGORIES = 500
    const val MAX_TAGS_PER_RECORD = 100
    const val MAX_TEXT_CHARS = 2_048
}

internal class InvalidVectorintBackup(
    message: String,
) : IllegalArgumentException(message)

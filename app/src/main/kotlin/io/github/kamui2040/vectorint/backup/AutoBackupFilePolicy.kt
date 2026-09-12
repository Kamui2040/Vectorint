package io.github.kamui2040.vectorint.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object AutoBackupFilePolicy {
    fun createFileName(
        instant: Instant,
        id: String,
    ): String {
        require(ID_PATTERN.matches(id))
        return "vectorint-auto-backup-${FILE_TIMESTAMP.format(instant)}-$id.json"
    }

    fun isManagedFileName(fileName: String): Boolean = FILE_PATTERN.matches(fileName)

    private val ID_PATTERN = Regex("^[0-9a-f]{8}$")
    private val FILE_PATTERN =
        Regex("^vectorint-auto-backup-\\d{8}T\\d{6}\\.\\d{3}Z-[0-9a-f]{8}\\.json$")
    private val FILE_TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC)
}

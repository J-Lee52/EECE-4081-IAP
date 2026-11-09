package com.example.eece4081_iap.data

import java.io.File
import java.io.IOException

/** Suffix of the temporary file a new copy is written to before it replaces the target. */
internal const val TEMP_SUFFIX = ".tmp"

/** Suffix of the previous copy while it is moved aside during a replacement. */
internal const val BACKUP_SUFFIX = ".bak"

/**
 * Replaces [target]'s contents with [text] so that an interruption never
 * leaves a half-written file, and a previous copy is never lost:
 *
 * 1. the new text is written to `<target>.tmp`;
 * 2. it is renamed over [target];
 * 3. if the file system will not rename over an existing file, the previous
 *    copy is first moved aside to `<target>.bak`, and put back if the
 *    replacement still fails.
 *
 * [rename] is the file move; tests replace it to simulate a failing file system.
 * Throws [IOException] if the new copy could not be put in place; [target] then
 * still holds its previous contents (or does not exist, if it never did).
 */
internal fun replaceFileSafely(target: File, text: String, rename: (File, File) -> Boolean) {
    val temp = File(target.path + TEMP_SUFFIX)
    temp.writeText(text, Charsets.UTF_8)
    if (rename(temp, target)) return

    val backup = File(target.path + BACKUP_SUFFIX)
    val hadPrevious = target.exists()
    if (hadPrevious) {
        backup.delete()
        if (!rename(target, backup)) {
            temp.delete()
            throw IOException("Cannot replace ${target.name}")
        }
    }
    if (!rename(temp, target)) {
        if (hadPrevious) rename(backup, target)
        temp.delete()
        throw IOException("Cannot replace ${target.name}")
    }
    if (hadPrevious) backup.delete()
}

/**
 * If the app stopped after moving [target]'s previous copy aside and before
 * putting its replacement in place, only `<target>.bak` exists. Puts it back.
 */
internal fun restoreInterruptedReplace(target: File, rename: (File, File) -> Boolean) {
    val backup = File(target.path + BACKUP_SUFFIX)
    if (!target.exists() && backup.isFile) rename(backup, target)
}

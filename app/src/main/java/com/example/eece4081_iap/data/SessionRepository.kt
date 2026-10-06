package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.ShootingSession

/**
 * Where finished sessions are kept so they survive closing the app (US07).
 * The engine and UI depend on this interface, not on how it stores data, so
 * the file-based version can later be replaced (for example by Room) without
 * changing them.
 */
interface SessionRepository {
    /** Saves [session]. Saving a session with an existing id replaces it. */
    fun save(session: ShootingSession)

    /** Every saved session, newest first. Unreadable entries are skipped. */
    fun loadAll(): List<ShootingSession>
}

package com.example.data.local

import android.util.Log
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Android-wide Database Write Coordinator (Room 11 P0-F).
 *
 * Guarantees:
 * 1. Concurrent Shared Reads: Multiple readers can read concurrently while database is active.
 * 2. Serialized Shared Writes: Exactly one write transaction/operation is permitted at any time across the entire process.
 * 3. Exclusive Maintenance: Full quiescence for backups, restores, and maintenance. Drains all active readers and writers.
 * 4. Cancellation & Failure Safety: Permits are released cleanly in `finally` blocks, preventing deadlocks or leaks.
 * 5. Reentrancy Protection: Coroutines holding a permit can reentrantly invoke coordinated blocks without deadlock.
 * 6. WAL Quiescence Protocol: Coordinates `PRAGMA wal_checkpoint(TRUNCATE)` before physical database copies.
 */
class DatabaseWriteCoordinator {

    private val writeMutex = Mutex()
    private val maintenanceMutex = Mutex()
    private val stateLock = Any()

    private val _activeReaders = MutableStateFlow(0)
    val activeReaders: StateFlow<Int> = _activeReaders.asStateFlow()

    private val _activeWriters = MutableStateFlow(0)
    val activeWriters: StateFlow<Int> = _activeWriters.asStateFlow()

    private val _isMaintenanceActive = MutableStateFlow(false)
    val isMaintenanceActive: StateFlow<Boolean> = _isMaintenanceActive.asStateFlow()

    val isWriteLocked: Boolean
        get() = writeMutex.isLocked

    val isMaintenanceLocked: Boolean
        get() = maintenanceMutex.isLocked

    /**
     * Coroutine context element indicating that the coroutine holds an active write permit.
     */
    private class WritePermitElement : CoroutineContext.Element {
        companion object Key : CoroutineContext.Key<WritePermitElement>
        override val key: CoroutineContext.Key<*> = Key
    }

    /**
     * Coroutine context element indicating that the coroutine holds an exclusive maintenance permit.
     */
    private class MaintenancePermitElement : CoroutineContext.Element {
        companion object Key : CoroutineContext.Key<MaintenancePermitElement>
        override val key: CoroutineContext.Key<*> = Key
    }

    /**
     * Returns true if the current coroutine context holds a write or maintenance permit.
     */
    suspend fun isWritePermitHeld(): Boolean {
        return coroutineContext[WritePermitElement] != null || coroutineContext[MaintenancePermitElement] != null
    }

    /**
     * Returns true if the current coroutine context holds an exclusive maintenance permit.
     */
    suspend fun isMaintenancePermitHeld(): Boolean {
        return coroutineContext[MaintenancePermitElement] != null
    }

    /**
     * Asserts that a write or maintenance permit is held in the calling coroutine context.
     * Throws [IllegalStateException] if uncoordinated write execution is attempted.
     */
    suspend fun requireWritePermit() {
        if (!isWritePermitHeld()) {
            throw IllegalStateException(
                "Database mutation attempted without acquiring DatabaseWriteCoordinator permit! " +
                "Direct uncoordinated write access is strictly prohibited."
            )
        }
    }

    /**
     * Executes [block] under a shared read permit.
     * Multiple readers may execute concurrently.
     * Suspends if exclusive maintenance is in progress.
     */
    suspend fun <T> withReadPermit(
        timeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
        block: suspend () -> T
    ): T {
        // If this coroutine already holds an exclusive maintenance permit, allow read immediately
        if (coroutineContext[MaintenancePermitElement] != null) {
            return block()
        }

        withTimeout(timeoutMs) {
            _isMaintenanceActive.first { !it }
        }

        synchronized(stateLock) {
            _activeReaders.value = _activeReaders.value + 1
        }

        try {
            return block()
        } finally {
            synchronized(stateLock) {
                _activeReaders.value = (_activeReaders.value - 1).coerceAtLeast(0)
            }
        }
    }

    /**
     * Executes [block] under a serialized write permit.
     * Exactly one write operation across all coroutines can execute at any given time.
     * Suspends if another write or exclusive maintenance is active.
     * Reentrant if called from within an existing write or maintenance permit.
     */
    suspend fun <T> withWritePermit(
        timeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS,
        block: suspend () -> T
    ): T {
        // Reentrancy check: if current coroutine already holds a write or maintenance permit, execute directly
        if (coroutineContext[WritePermitElement] != null || coroutineContext[MaintenancePermitElement] != null) {
            return block()
        }

        return withTimeout(timeoutMs) {
            writeMutex.withLock {
                _isMaintenanceActive.first { !it }

                synchronized(stateLock) {
                    _activeWriters.value = _activeWriters.value + 1
                }

                try {
                    withContext(WritePermitElement()) {
                        block()
                    }
                } finally {
                    synchronized(stateLock) {
                        _activeWriters.value = (_activeWriters.value - 1).coerceAtLeast(0)
                    }
                }
            }
        }
    }

    /**
     * Executes [block] under exclusive database maintenance mode.
     * 1. Acquires maintenance lock and write lock (blocking new writes and queued maintenance).
     * 2. Sets [isMaintenanceActive] to true (blocking new reads).
     * 3. Drains all existing active readers (waits until activeReaders == 0 with [drainTimeoutMs]).
     * 4. Executes [block] with [MaintenancePermitElement].
     * 5. In a finally block, resets [isMaintenanceActive] and releases locks.
     */
    suspend fun <T> withExclusiveMaintenance(
        drainTimeoutMs: Long = DEFAULT_MAINTENANCE_DRAIN_TIMEOUT_MS,
        totalTimeoutMs: Long = DEFAULT_MAINTENANCE_TOTAL_TIMEOUT_MS,
        block: suspend () -> T
    ): T {
        if (coroutineContext[MaintenancePermitElement] != null) {
            return block()
        }

        return withTimeout(totalTimeoutMs) {
            maintenanceMutex.withLock {
                writeMutex.withLock {
                    _isMaintenanceActive.value = true

                    try {
                        withTimeout(drainTimeoutMs) {
                            _activeReaders.first { it == 0 }
                        }

                        withContext(MaintenancePermitElement()) {
                            block()
                        }
                    } finally {
                        _isMaintenanceActive.value = false
                    }
                }
            }
        }
    }

    /**
     * Executes SQLite PRAGMA wal_checkpoint(TRUNCATE) safely under maintenance.
     */
    suspend fun checkpointWalTruncate(
        db: SupportSQLiteDatabase,
        maxRetries: Int = 3,
        retryDelayMs: Long = 250L
    ): WalCheckpointResult = withContext(Dispatchers.IO) {
        for (attempt in 1..maxRetries) {
            try {
                val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE);")
                try {
                    if (cursor.moveToFirst()) {
                        val busy = cursor.getInt(0)
                        val log = cursor.getInt(1)
                        val checkpointed = cursor.getInt(2)
                        if (busy == 0) {
                            return@withContext WalCheckpointResult.Success(checkpointed, log)
                        }
                        Log.w(TAG, "WAL checkpoint busy=1 (attempt $attempt/$maxRetries), retrying in ${retryDelayMs}ms...")
                    }
                } finally {
                    cursor.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "WAL checkpoint execution failed on attempt $attempt", e)
                if (attempt == maxRetries) {
                    return@withContext WalCheckpointResult.Error(e)
                }
            }
            delay(retryDelayMs)
        }
        WalCheckpointResult.Busy(retriesExhausted = true)
    }

    /**
     * 9-step WAL Quiescence and Safe Backup implementation.
     */
    suspend fun performWalSafeBackup(
        database: FinSmartDatabase,
        targetBackupFile: File,
        drainTimeoutMs: Long = DEFAULT_MAINTENANCE_DRAIN_TIMEOUT_MS
    ): WalBackupResult {
        return withExclusiveMaintenance(drainTimeoutMs = drainTimeoutMs) {
            withContext(Dispatchers.IO) {
                val db = database.openHelper.writableDatabase
                val checkpointRes = checkpointWalTruncate(db)
                when (checkpointRes) {
                    is WalCheckpointResult.Busy -> {
                        WalBackupResult.Failure(
                            "WAL Checkpoint gagal karena database sedang sibuk (busy=1) setelah percobaan bertahap. Backup dibatalkan demi keamanan data."
                        )
                    }
                    is WalCheckpointResult.Error -> {
                        WalBackupResult.Failure(
                            "WAL Checkpoint gagal: ${checkpointRes.exception.message}",
                            checkpointRes.exception
                        )
                    }
                    is WalCheckpointResult.Success -> {
                        val dbPath = db.path
                            ?: return@withContext WalBackupResult.Failure("Database path is null (in-memory database)")
                        val sourceFile = File(dbPath)
                        if (!sourceFile.exists()) {
                            return@withContext WalBackupResult.Failure("Source database file does not exist at $dbPath")
                        }

                        try {
                            targetBackupFile.parentFile?.mkdirs()
                            FileInputStream(sourceFile).use { input ->
                                FileOutputStream(targetBackupFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            WalBackupResult.Success(
                                backupFile = targetBackupFile,
                                bytesCopied = targetBackupFile.length(),
                                checkpointPages = checkpointRes.pagesCheckpointed
                            )
                        } catch (e: Exception) {
                            WalBackupResult.Failure("Failed to copy database file: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    sealed class WalCheckpointResult {
        data class Success(val pagesCheckpointed: Int, val logPages: Int) : WalCheckpointResult()
        data class Busy(val retriesExhausted: Boolean) : WalCheckpointResult()
        data class Error(val exception: Throwable) : WalCheckpointResult()
    }

    sealed class WalBackupResult {
        data class Success(val backupFile: File, val bytesCopied: Long, val checkpointPages: Int) : WalBackupResult()
        data class Failure(val reason: String, val cause: Throwable? = null) : WalBackupResult()
    }

    companion object {
        private const val TAG = "DbWriteCoordinator"
        const val DEFAULT_READ_TIMEOUT_MS = 15000L
        const val DEFAULT_WRITE_TIMEOUT_MS = 30000L
        const val DEFAULT_MAINTENANCE_DRAIN_TIMEOUT_MS = 5000L
        const val DEFAULT_MAINTENANCE_TOTAL_TIMEOUT_MS = 60000L

        @Volatile
        private var INSTANCE: DatabaseWriteCoordinator? = null

        fun getInstance(): DatabaseWriteCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseWriteCoordinator().also { INSTANCE = it }
            }
        }

        fun resetForTesting() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}

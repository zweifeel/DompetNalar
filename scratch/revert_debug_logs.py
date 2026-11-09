# Revert DatabaseWriteCoordinator.kt to clean state
path_db = 'app/src/main/java/com/example/data/local/DatabaseWriteCoordinator.kt'
with open(path_db, 'r', encoding='utf-8') as f:
    text_db = f.read()

# Remove debug logs in DatabaseWriteCoordinator.kt
old_block_db = '''        return withTimeout(timeoutMs) {
            val threadName = Thread.currentThread().name
            println("[DBCoord] withWritePermit: waiting for writeMutex (isLocked=" + writeMutex.isLocked + ") on " + threadName)
            if (writeMutex.isLocked) {
                println("[DBCoord] STACK OF WAITING COROUTINE:")
                Exception("Waiting on locked writeMutex").printStackTrace()
            }
            writeMutex.withLock {
                println("[DBCoord] withWritePermit: ACQUIRED writeMutex on " + threadName)
                Exception("Stack of holder who ACQUIRED writeMutex").printStackTrace()
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
                    println("[DBCoord] withWritePermit: RELEASED writeMutex on " + threadName)
                }
            }
        }'''

clean_block_db = '''        return withTimeout(timeoutMs) {
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
        }'''

assert old_block_db in text_db, 'old_block_db not found'
text_db = text_db.replace(old_block_db, clean_block_db, 1)
with open(path_db, 'w', encoding='utf-8') as f:
    f.write(text_db)
print('DatabaseWriteCoordinator.kt restored cleanly')

# Revert SessionManager.kt to clean state
path_sm = 'app/src/main/java/com/example/data/session/SessionManager.kt'
with open(path_sm, 'r', encoding='utf-8') as f:
    text_sm = f.read()

old_block_sm = '''        println("[SessionManager] startGuestSession: BEFORE insertUser")
        val generatedId = com.example.data.local.DatabaseWriteCoordinator.getInstance().withWritePermit {
            println("[SessionManager] startGuestSession: INSIDE withWritePermit, calling userDao.insertUser")
            val id = userDao.insertUser(guestUser)
            println("[SessionManager] startGuestSession: INSIDE withWritePermit, userDao.insertUser finished, id=" + id)
            id
        }
        println("[SessionManager] startGuestSession: AFTER insertUser, id=" + generatedId)'''

clean_block_sm = '''        val generatedId = com.example.data.local.DatabaseWriteCoordinator.getInstance().withWritePermit {
            userDao.insertUser(guestUser)
        }'''

assert old_block_sm in text_sm, 'old_block_sm not found'
text_sm = text_sm.replace(old_block_sm, clean_block_sm, 1)
with open(path_sm, 'w', encoding='utf-8') as f:
    f.write(text_sm)
print('SessionManager.kt restored cleanly')

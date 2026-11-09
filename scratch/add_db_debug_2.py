path = 'app/src/main/java/com/example/data/local/DatabaseWriteCoordinator.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''        return withTimeout(timeoutMs) {
            println("[DBCoord] withWritePermit: waiting for writeMutex (isLocked=" + writeMutex.isLocked + ")")
            writeMutex.withLock {
                println("[DBCoord] withWritePermit: acquired writeMutex, waiting for !isMaintenanceActive (active=" + _isMaintenanceActive.value + ")")
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

replacement = '''        return withTimeout(timeoutMs) {
            val threadName = Thread.currentThread().name
            println("[DBCoord] withWritePermit: waiting for writeMutex (isLocked=" + writeMutex.isLocked + ") on " + threadName)
            writeMutex.withLock {
                println("[DBCoord] withWritePermit: ACQUIRED writeMutex on " + threadName)
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

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated debug logging in DatabaseWriteCoordinator.kt')

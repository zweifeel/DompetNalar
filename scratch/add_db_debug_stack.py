path = 'app/src/main/java/com/example/data/local/DatabaseWriteCoordinator.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''        return withTimeout(timeoutMs) {
            val threadName = Thread.currentThread().name
            println("[DBCoord] withWritePermit: waiting for writeMutex (isLocked=" + writeMutex.isLocked + ") on " + threadName)'''

replacement = '''        return withTimeout(timeoutMs) {
            val threadName = Thread.currentThread().name
            println("[DBCoord] withWritePermit: waiting for writeMutex (isLocked=" + writeMutex.isLocked + ") on " + threadName)
            if (writeMutex.isLocked) {
                println("[DBCoord] STACK OF WAITING COROUTINE:")
                Exception("Waiting on locked writeMutex").printStackTrace()
            }'''

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Added stack trace for waiting coroutine')

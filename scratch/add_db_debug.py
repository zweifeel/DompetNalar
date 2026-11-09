path = 'app/src/main/java/com/example/data/local/DatabaseWriteCoordinator.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''        return withTimeout(timeoutMs) {
            writeMutex.withLock {
                _isMaintenanceActive.first { !it }'''

replacement = '''        return withTimeout(timeoutMs) {
            println("[DBCoord] withWritePermit: waiting for writeMutex (isLocked=" + writeMutex.isLocked + ")")
            writeMutex.withLock {
                println("[DBCoord] withWritePermit: acquired writeMutex, waiting for !isMaintenanceActive (active=" + _isMaintenanceActive.value + ")")
                _isMaintenanceActive.first { !it }'''

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Added debug prints successfully')

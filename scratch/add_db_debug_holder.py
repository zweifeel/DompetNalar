path = 'app/src/main/java/com/example/data/local/DatabaseWriteCoordinator.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''            writeMutex.withLock {
                println("[DBCoord] withWritePermit: ACQUIRED writeMutex on " + threadName)'''

replacement = '''            writeMutex.withLock {
                println("[DBCoord] withWritePermit: ACQUIRED writeMutex on " + threadName)
                Exception("Stack of holder who ACQUIRED writeMutex").printStackTrace()'''

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated stack trace for holder')

path = 'app/src/main/java/com/example/data/session/SessionManager.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''        val generatedId = com.example.data.local.DatabaseWriteCoordinator.getInstance().withWritePermit {
            userDao.insertUser(guestUser)
        }'''

replacement = '''        println("[SessionManager] startGuestSession: BEFORE insertUser")
        val generatedId = com.example.data.local.DatabaseWriteCoordinator.getInstance().withWritePermit {
            println("[SessionManager] startGuestSession: INSIDE withWritePermit, calling userDao.insertUser")
            val id = userDao.insertUser(guestUser)
            println("[SessionManager] startGuestSession: INSIDE withWritePermit, userDao.insertUser finished, id=" + id)
            id
        }
        println("[SessionManager] startGuestSession: AFTER insertUser, id=" + generatedId)'''

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Added logging in SessionManager.startGuestSession')

path = 'app/src/test/java/com/example/CriticalDataAuthSyncRemediationTest.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''        // Try local login with Master Dev identifier on Release simulation (isDebug = false)
        authViewModel.login(DeveloperAccountConfig.CONTACT_DEV_EMAIL, "AnyPassword123!", isDebug = false)
        ShadowLooper.idleMainLooper()

        val error = authViewModel.authError.value'''

replacement = '''        // Try local login with Master Dev identifier on Release simulation (isDebug = false)
        authViewModel.login(DeveloperAccountConfig.CONTACT_DEV_EMAIL, "AnyPassword123!", isDebug = false)
        var retries = 0
        while (authViewModel.authError.value == null && retries < 40) {
            ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
            retries++
        }

        val error = authViewModel.authError.value'''

text = text.replace('\r\n', '\n')
target = target.replace('\r\n', '\n')
replacement = replacement.replace('\r\n', '\n')

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated testMasterDevLogin_onRelease_requiresGoogleSignIn with retry loop')

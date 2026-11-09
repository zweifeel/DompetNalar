# 1. Update FinanceViewModel.kt
path_fvm = 'app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt'
with open(path_fvm, 'r', encoding='utf-8') as f:
    text_fvm = f.read()

target_fvm = '''                }
            }

            // Phase 1G.5: Ensure target developer account cannot be reintroduced via restore
            FinSmartDatabase.purgeTargetDeveloperAccountAtomically(database)
        }'''

replacement_fvm = '''                }

                // Phase 1G.6: Ensure target developer account cannot be reintroduced via restore,
                // executed under continuous exclusive maintenance lock (Reentrant via MaintenancePermitElement)
                FinSmartDatabase.purgeTargetDeveloperAccountAtomically(database)
            }
        }'''

text_fvm = text_fvm.replace('\r\n', '\n')
target_fvm = target_fvm.replace('\r\n', '\n')
replacement_fvm = replacement_fvm.replace('\r\n', '\n')

assert target_fvm in text_fvm, 'target_fvm not found'
text_fvm = text_fvm.replace(target_fvm, replacement_fvm, 1)

with open(path_fvm, 'w', encoding='utf-8') as f:
    f.write(text_fvm)

print('Updated FinanceViewModel.kt: moved purge inside withExclusiveMaintenance')

# 2. Update SessionManager.kt
path_sm = 'app/src/main/java/com/example/data/session/SessionManager.kt'
with open(path_sm, 'r', encoding='utf-8') as f:
    text_sm = f.read()

target_sm = '''        // Pada build Release, sesi tersimpan dengan role MASTER_DEV tidak boleh dipulihkan secara otomatis
        // semata-mata dari database lokal atau SharedPreferences tanpa re-autentikasi resmi.
        if (!isDebug && user.role == UserRole.MASTER_DEV) {
            prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
            updateState(SessionState.Unauthenticated)
            return false
        }'''

replacement_sm = '''        // Phase 1G.6: Akun target developer (Lukas / lunachroc07@gmail.com) tidak boleh dipulihkan dalam sesi apa pun
        if (user.username.equals("Lukas", ignoreCase = true) || user.email.equals("lunachroc07@gmail.com", ignoreCase = true)) {
            prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
            updateState(SessionState.Unauthenticated)
            return false
        }

        // Pada build Release, sesi tersimpan dengan role MASTER_DEV tidak boleh dipulihkan secara otomatis
        // semata-mata dari database lokal atau SharedPreferences tanpa re-autentikasi resmi.
        if (!isDebug && user.role == UserRole.MASTER_DEV) {
            prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
            updateState(SessionState.Unauthenticated)
            return false
        }'''

text_sm = text_sm.replace('\r\n', '\n')
target_sm = target_sm.replace('\r\n', '\n')
replacement_sm = replacement_sm.replace('\r\n', '\n')

assert target_sm in text_sm, 'target_sm not found'
text_sm = text_sm.replace(target_sm, replacement_sm, 1)

with open(path_sm, 'w', encoding='utf-8') as f:
    f.write(text_sm)

print('Updated SessionManager.kt: added permanent Lukas restoration guard')

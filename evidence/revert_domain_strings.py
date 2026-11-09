# 1. Update EntitlementManager.kt
with open(r'app\src\main\java\com\example\util\EntitlementManager.kt', 'r', encoding='utf-8') as f:
    c = f.read()

target = """    fun getFormattedExpiry(): String {
        return when {
            effectiveRole == UserRole.MASTER_DEV -> (if (LocaleHelper.isEnglish()) "Permanent (Master Developer)" else "Permanen (Master Developer)")
            expiresAt == null -> (if (LocaleHelper.isEnglish()) "Unlimited" else "Tidak Terbatas")
            expiresAt <= 0L -> "-"
            else -> {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", LocaleHelper.getActiveLocale())
                sdf.format(Date(expiresAt))
            }
        }
    }"""

replacement = """    fun getFormattedExpiry(): String {
        return when {
            effectiveRole == UserRole.MASTER_DEV -> "Permanen (Master Developer)"
            expiresAt == null -> "Tidak Terbatas"
            expiresAt <= 0L -> "-"
            else -> {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                sdf.format(Date(expiresAt))
            }
        }
    }"""

if target in c:
    c = c.replace(target, replacement)
    with open(r'app\src\main\java\com\example\util\EntitlementManager.kt', 'w', encoding='utf-8') as f:
        f.write(c)
    print("Success updating EntitlementManager.kt")
else:
    print("Failed to find target in EntitlementManager.kt")

# 2. Update SleepyKoalaStartupScreen.kt
with open(r'app\src\main\java\com\example\ui\components\SleepyKoalaStartupScreen.kt', 'r', encoding='utf-8') as f:
    s = f.read()

target_s = 'text = AppStrings.t("Menyiapkan DompetNalar...", "Setting up DompetNalar..."),'
replacement_s = 'text = "Menyiapkan DompetNalar...",'

if target_s in s:
    s = s.replace(target_s, replacement_s)
    with open(r'app\src\main\java\com\example\ui\components\SleepyKoalaStartupScreen.kt', 'w', encoding='utf-8') as f:
        f.write(s)
    print("Success updating SleepyKoalaStartupScreen.kt")
else:
    print("Failed to find target in SleepyKoalaStartupScreen.kt")

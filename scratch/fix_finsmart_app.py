with open('app/src/main/java/com/example/ui/FinSmartApp.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Add TextOverflow import if not present
if 'import androidx.compose.ui.text.style.TextOverflow' not in text:
    text = text.replace(
        'import androidx.compose.ui.unit.sp\n',
        'import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.text.style.TextOverflow\n'
    )

# 2. Update NavigationBarItem
old_nav_label = """                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        },"""

new_nav_label = """                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false,
                                letterSpacing = (-0.4).sp,
                                overflow = TextOverflow.Visible
                            )
                        },
                        alwaysShowLabel = true,"""

assert old_nav_label in text, "old_nav_label not found"
text = text.replace(old_nav_label, new_nav_label)

# 3. Wrap Scaffold in if (isStartupLoading) ... else
old_scaffold_start = """    androidx.compose.runtime.key(activeAppLanguage) {
        Scaffold("""

new_scaffold_start = """    androidx.compose.runtime.key(activeAppLanguage) {
        if (isStartupLoading) {
            SleepyKoalaStartupScreen(
                onLoadingComplete = { isStartupLoading = false }
            )
        } else {
            Scaffold("""

assert old_scaffold_start in text, "old_scaffold_start not found"
text = text.replace(old_scaffold_start, new_scaffold_start)

# 4. Remove old startup screen inside Scaffold and close the else block
old_startup_block = """            // SleepyKoala DompetNalar Animated Startup Screen (Single-play cold boot)
            if (isStartupLoading) {
                SleepyKoalaStartupScreen(
                    onLoadingComplete = { isStartupLoading = false }
                )
            }
        }
    }
}
}"""

new_startup_block = """        }
    }
}
}
}"""

assert old_startup_block in text, "old_startup_block not found"
text = text.replace(old_startup_block, new_startup_block)

with open('app/src/main/java/com/example/ui/FinSmartApp.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("FinSmartApp.kt updated successfully")

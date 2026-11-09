with open('app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt', 'r', encoding='utf-8') as f:
    text = f.read()

old_block = """            onSaveAsInstallmentPlan = { planToSave ->
                onSaveInstallmentPlan(planToSave)
            },
            onDismiss = {"""

new_block = """            onSaveAsInstallmentPlan = { planToSave ->
                onSaveInstallmentPlan(planToSave)
            },
            onSaveBatch = { debtsToSave, plansToSave ->
                debtsToSave.forEach { onSaveDebt(it) }
                plansToSave.forEach { (plan, _) ->
                    onSaveInstallmentPlan(plan)
                }
            },
            onDismiss = {"""

text = text.replace('\r\n', '\n')
old_block = old_block.replace('\r\n', '\n')
new_block = new_block.replace('\r\n', '\n')

assert old_block in text, "old_block not found in DebtPlannerModalDialog.kt"
text = text.replace(old_block, new_block)

with open('app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt', 'w', encoding='utf-8') as f:
    f.write(text.replace('\n', '\r\n'))

print("DebtPlannerModalDialog.kt updated successfully")

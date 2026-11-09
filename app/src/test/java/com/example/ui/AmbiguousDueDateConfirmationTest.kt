package com.example.ui

import com.example.data.model.PayLaterBillScanResult
import com.example.util.InstallmentDateHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit regression suite verifying the state lifecycle and gating logic of
 * ambiguous due-date user confirmation in PayLaterBillReviewDialog.
 */
class AmbiguousDueDateConfirmationTest {

    // Extracted state simulation mirroring PayLaterBillReviewDialog logic
    private class ReviewItemConfirmationStateHolder {
        val itemConfirmationMap = mutableMapOf<String, Boolean>()

        fun isDateAmbiguous(item: PayLaterBillScanResult): Boolean {
            return item.isDateAmbiguous ||
                    item.dateConfidenceStatus == "NEEDS_USER_CONFIRMATION" ||
                    item.dueIsoDate.isNullOrBlank() ||
                    !InstallmentDateHelper.isValidIsoDate(item.dueIsoDate)
        }

        fun isDateConfirmed(item: PayLaterBillScanResult): Boolean {
            val isAmbiguous = isDateAmbiguous(item)
            return if (!isAmbiguous) true else (itemConfirmationMap[item.id] == true)
        }

        fun onConfirmChecked(item: PayLaterBillScanResult, isDateValid: Boolean, checked: Boolean) {
            if (isDateValid) {
                itemConfirmationMap[item.id] = checked
            }
        }

        fun onDateFieldChanged(item: PayLaterBillScanResult) {
            if (isDateAmbiguous(item)) {
                itemConfirmationMap[item.id] = false
            }
        }

        fun onItemRemoved(item: PayLaterBillScanResult) {
            itemConfirmationMap.remove(item.id)
        }

        fun canSave(item: PayLaterBillScanResult, isDateValid: Boolean, providerName: String): Boolean {
            return isDateValid && isDateConfirmed(item) && providerName.isNotBlank()
        }
    }

    @Test
    fun test1_AmbiguousDateStartsUnconfirmed() {
        val holder = ReviewItemConfirmationStateHolder()
        val ambiguousItem = PayLaterBillScanResult(
            id = "bill-ambiguous-1",
            providerName = "GoPay Pinjam",
            dueDate = "2026-11-25",
            dueIsoDate = null,
            isDateAmbiguous = true,
            dateConfidenceStatus = "NEEDS_USER_CONFIRMATION"
        )

        assertTrue(holder.isDateAmbiguous(ambiguousItem))
        assertFalse("Ambiguous item must start with confirmation = false", holder.isDateConfirmed(ambiguousItem))
    }

    @Test
    fun test2_ValidInferredDateWithNoConfirmationCannotBeSaved() {
        val holder = ReviewItemConfirmationStateHolder()
        val ambiguousItem = PayLaterBillScanResult(
            id = "bill-ambiguous-2",
            providerName = "GoPay Pinjam",
            dueDate = "2026-11-25",
            dueIsoDate = null,
            isDateAmbiguous = true,
            dateConfidenceStatus = "NEEDS_USER_CONFIRMATION"
        )

        // Inferred date is valid calendar date (2026-11-25)
        val isDateValid = InstallmentDateHelper.isValidDate(2026, 11, 25)
        assertTrue(isDateValid)

        assertFalse(
            "Valid inferred date without explicit confirmation must NOT be saveable",
            holder.canSave(ambiguousItem, isDateValid = true, providerName = "GoPay Pinjam")
        )
    }

    @Test
    fun test3_ExplicitConfirmationEnablesSaveOnlyWhenDateIsValid() {
        val holder = ReviewItemConfirmationStateHolder()
        val ambiguousItem = PayLaterBillScanResult(
            id = "bill-ambiguous-3",
            providerName = "GoPay Later",
            dueDate = "2026-11-25",
            dueIsoDate = null,
            isDateAmbiguous = true
        )

        holder.onConfirmChecked(ambiguousItem, isDateValid = true, checked = true)
        assertTrue(holder.isDateConfirmed(ambiguousItem))
        assertTrue(
            "Explicit confirmation with valid date must enable Save",
            holder.canSave(ambiguousItem, isDateValid = true, providerName = "GoPay Later")
        )
    }

    @Test
    fun test4_InvalidDateDisablesSaveEvenAfterConfirmation() {
        val holder = ReviewItemConfirmationStateHolder()
        val ambiguousItem = PayLaterBillScanResult(
            id = "bill-ambiguous-4",
            providerName = "Shopee PayLater",
            dueDate = "2025-02-29",
            dueIsoDate = null,
            isDateAmbiguous = true
        )

        // 2025 is not a leap year -> 29 Feb is invalid
        val isDateValid = InstallmentDateHelper.isValidDate(2025, 2, 29)
        assertFalse(isDateValid)

        // User attempts to check confirm on invalid date
        holder.onConfirmChecked(ambiguousItem, isDateValid = isDateValid, checked = true)
        assertFalse(
            "Confirmation must not be accepted on invalid date",
            holder.isDateConfirmed(ambiguousItem)
        )
        assertFalse(
            "Save must be disabled for invalid calendar date",
            holder.canSave(ambiguousItem, isDateValid = false, providerName = "Shopee PayLater")
        )
    }

    @Test
    fun test5_ChangingDateAfterConfirmationResetsConfirmation() {
        val holder = ReviewItemConfirmationStateHolder()
        val ambiguousItem = PayLaterBillScanResult(
            id = "bill-ambiguous-5",
            providerName = "DANA Cicil",
            dueDate = "2026-11-15",
            dueIsoDate = null,
            isDateAmbiguous = true
        )

        // 1. Confirm date
        holder.onConfirmChecked(ambiguousItem, isDateValid = true, checked = true)
        assertTrue(holder.isDateConfirmed(ambiguousItem))

        // 2. User edits date field
        holder.onDateFieldChanged(ambiguousItem)
        assertFalse("Editing date field must invalidate previous confirmation", holder.isDateConfirmed(ambiguousItem))
        assertFalse("Save must be disabled after date edit until re-confirmed", holder.canSave(ambiguousItem, isDateValid = true, providerName = "DANA Cicil"))
    }

    @Test
    fun test6_NavigatingBetweenReviewItemsDoesNotTransferConfirmation() {
        val holder = ReviewItemConfirmationStateHolder()
        val itemA = PayLaterBillScanResult(
            id = "item-A",
            providerName = "GoPay Pinjam",
            dueDate = "2026-11-20",
            dueIsoDate = null,
            isDateAmbiguous = true
        )
        val itemB = PayLaterBillScanResult(
            id = "item-B",
            providerName = "Kredivo",
            dueDate = "2026-11-28",
            dueIsoDate = null,
            isDateAmbiguous = true
        )

        // Confirm item A
        holder.onConfirmChecked(itemA, isDateValid = true, checked = true)
        assertTrue("Item A must be confirmed", holder.isDateConfirmed(itemA))
        assertFalse("Item B must remain unconfirmed", holder.isDateConfirmed(itemB))

        // Removing item A clears only its key
        holder.onItemRemoved(itemA)
        assertFalse("Item A entry is cleaned up", holder.isDateConfirmed(itemA))
        assertFalse("Item B remains unconfirmed", holder.isDateConfirmed(itemB))
    }

    @Test
    fun test7_NonAmbiguousCompleteDateRetainsNormalSaveBehavior() {
        val holder = ReviewItemConfirmationStateHolder()
        val nonAmbiguousItem = PayLaterBillScanResult(
            id = "item-verified-1",
            providerName = "Shopee PayLater",
            dueDate = "2026-12-07",
            dueIsoDate = "2026-12-07",
            isDateAmbiguous = false,
            dateConfidenceStatus = "VERIFIED"
        )

        assertFalse(holder.isDateAmbiguous(nonAmbiguousItem))
        assertTrue("Non-ambiguous complete date is immediately confirmed", holder.isDateConfirmed(nonAmbiguousItem))
        assertTrue("Can save immediately without confirmation checkbox", holder.canSave(nonAmbiguousItem, isDateValid = true, providerName = "Shopee PayLater"))
    }

    @Test
    fun test8_BothSavePathsEnforceSameGatingRule() {
        val holder = ReviewItemConfirmationStateHolder()
        val ambiguousItem = PayLaterBillScanResult(
            id = "item-dual-save",
            providerName = "Akulaku",
            dueDate = "2026-11-10",
            dueIsoDate = null,
            isDateAmbiguous = true
        )

        val canSaveAsDebt = holder.canSave(ambiguousItem, isDateValid = true, providerName = "Akulaku")
        val canSaveAsPlan = holder.canSave(ambiguousItem, isDateValid = true, providerName = "Akulaku")

        // Both must be false before confirmation
        assertFalse(canSaveAsDebt)
        assertFalse(canSaveAsPlan)

        // After confirmation, both become true
        holder.onConfirmChecked(ambiguousItem, isDateValid = true, checked = true)
        val canSaveAsDebtAfter = holder.canSave(ambiguousItem, isDateValid = true, providerName = "Akulaku")
        val canSaveAsPlanAfter = holder.canSave(ambiguousItem, isDateValid = true, providerName = "Akulaku")
        assertTrue(canSaveAsDebtAfter)
        assertTrue(canSaveAsPlanAfter)
    }
}

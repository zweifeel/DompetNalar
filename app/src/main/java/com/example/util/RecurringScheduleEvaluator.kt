package com.example.util

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.DomainCurrency
import com.example.data.model.Money
import com.example.data.model.RecurringSchedule
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale

sealed class RecurringExecutionResult {
    data class Executed(val transactionId: Long, val cycleKey: String, val amount: Double, val walletName: String, val title: String) : RecurringExecutionResult()
    data class AlreadyExecuted(val cycleKey: String) : RecurringExecutionResult()
    object NotDue : RecurringExecutionResult()
    object StaleVersion : RecurringExecutionResult()
    data class ScheduleDisabled(val reason: String) : RecurringExecutionResult()
    data class Failed(val throwable: Throwable) : RecurringExecutionResult()
}

object RecurringScheduleEvaluator {

    /**
     * Evaluates whether a recurring schedule is due at the current instant according to
     * its timezone, start date, execution time, and recurrence interval.
     */
    fun isScheduleDue(schedule: RecurringSchedule, clock: Clock = Clock.systemDefaultZone()): Boolean {
        val zone = try { ZoneId.of(schedule.timezoneId) } catch (e: Exception) { ZoneOffset.UTC }
        val now = ZonedDateTime.now(clock.withZone(zone))

        // 1. Check startDate boundary
        if (now.toInstant().toEpochMilli() < schedule.startDate) return false

        // 2. Check execution time on current day
        val targetTime = LocalTime.of(schedule.executionHour, schedule.executionMinute)
        if (now.toLocalTime() < targetTime) return false

        return when (schedule.interval) {
            RecurringInterval.DAILY -> true
            RecurringInterval.WEEKLY -> {
                // ISO-8601 DayOfWeek: 1 = Monday .. 7 = Sunday
                now.dayOfWeek.value == schedule.dayOfWeek
            }
            RecurringInterval.MONTHLY -> {
                // Month-end Day Clamping:
                // If dayOfMonth is 31, but month has 28, 29, or 30 days, clamp to lengthOfMonth()
                val maxDayInMonth = now.toLocalDate().lengthOfMonth()
                val effectiveTargetDay = schedule.dayOfMonth.coerceAtMost(maxDayInMonth)
                now.dayOfMonth == effectiveTargetDay
            }
        }
    }

    /**
     * Computes the deterministic cycle identifier for a schedule at the current instant.
     * Uses lowercase `yyyy` for daily/monthly and `WeekFields.ISO` for weekly cycles.
     */
    fun computeCycleIdentifier(schedule: RecurringSchedule, clock: Clock = Clock.systemDefaultZone()): String {
        val zone = try { ZoneId.of(schedule.timezoneId) } catch (e: Exception) { ZoneOffset.UTC }
        val zdt = ZonedDateTime.now(clock.withZone(zone))
        return when (schedule.interval) {
            RecurringInterval.DAILY -> {
                String.format(Locale.ROOT, "%04d-%02d-%02d", zdt.year, zdt.monthValue, zdt.dayOfMonth)
            }
            RecurringInterval.WEEKLY -> {
                val weekBasedYear = zdt.get(WeekFields.ISO.weekBasedYear())
                val weekOfWeekBasedYear = zdt.get(WeekFields.ISO.weekOfWeekBasedYear())
                String.format(Locale.ROOT, "%04d-W%02d", weekBasedYear, weekOfWeekBasedYear)
            }
            RecurringInterval.MONTHLY -> {
                String.format(Locale.ROOT, "%04d-%02d", zdt.year, zdt.monthValue)
            }
        }
    }

    /**
     * Executes the 13-step atomic database transaction for a recurring schedule.
     */
    suspend fun executeScheduleTransaction(
        database: FinSmartDatabase,
        scheduleId: String,
        userId: Long,
        expectedVersion: Int? = null,
        clock: Clock = Clock.systemDefaultZone()
    ): RecurringExecutionResult {
        val scheduleDao = database.recurringScheduleDao()
        val walletDao = database.walletDao()
        val transactionDao = database.transactionDao()

        // 1 & 2. Load schedule scoped by scheduleId and userId
        val schedule = scheduleDao.getScheduleByIdAndUser(scheduleId, userId)
            ?: return RecurringExecutionResult.ScheduleDisabled("Schedule not found for user")

        // 3. State & version validation
        if (!schedule.isEnabled) {
            return RecurringExecutionResult.ScheduleDisabled("Schedule is disabled")
        }
        if (expectedVersion != null && schedule.version != expectedVersion) {
            return RecurringExecutionResult.StaleVersion
        }

        // 4. Timing & cycle evaluation
        if (!isScheduleDue(schedule, clock)) {
            return RecurringExecutionResult.NotDue
        }
        val cycleKey = computeCycleIdentifier(schedule, clock)
        if (schedule.lastExecutedCycle == cycleKey) {
            return RecurringExecutionResult.AlreadyExecuted(cycleKey)
        }

        val idempotencyKey = "rec_${schedule.id}_$cycleKey"

        return database.writeCoordinator.withWritePermit {
            try {
                database.withTransaction {
                // 5. Scoped wallet validation inside transaction
                val wallet = walletDao.getWalletById(schedule.walletId, schedule.userId)
                if (wallet == null) {
                    scheduleDao.setScheduleEnabled(schedule.id, schedule.userId, false)
                    return@withTransaction RecurringExecutionResult.ScheduleDisabled("Wallet not found or wrong owner")
                }
                if (wallet.isArchived) {
                    scheduleDao.setScheduleEnabled(schedule.id, schedule.userId, false)
                    return@withTransaction RecurringExecutionResult.ScheduleDisabled("Wallet is archived")
                }
                if (wallet.currencyCode.isNullOrBlank()) {
                    scheduleDao.setScheduleEnabled(schedule.id, schedule.userId, false)
                    return@withTransaction RecurringExecutionResult.ScheduleDisabled("Wallet currency is unassigned")
                }

                // 6 & 7. Check if idempotencyKey already exists
                val existingTx = transactionDao.getTransactionByIdempotencyKey(idempotencyKey)
                if (existingTx != null) {
                    scheduleDao.updateLastExecutedCycle(schedule.id, cycleKey)
                    return@withTransaction RecurringExecutionResult.AlreadyExecuted(cycleKey)
                }

                // 8. Atomic Financial Mutation via Money
                val currency = try {
                    DomainCurrency.fromCode(wallet.currencyCode)
                } catch (e: Exception) {
                    DomainCurrency.fromCode("IDR")
                }
                val currentMoney = Money.of(wallet.balance, currency)
                val amountMoney = Money.of(schedule.amount, currency)
                val newMoney = if (schedule.type == TransactionType.INCOME) {
                    currentMoney + amountMoney
                } else {
                    currentMoney - amountMoney
                }

                val newTx = TransactionItem(
                    id = 0L,
                    title = "${schedule.title} (Auto Recurring)",
                    amount = schedule.amount,
                    type = schedule.type,
                    category = schedule.category,
                    categoryIcon = schedule.categoryIcon,
                    walletId = schedule.walletId,
                    walletName = schedule.walletName,
                    timestamp = clock.millis(),
                    note = "Pencatatan berulang otomatis oleh DompetNalar Recurring Engine",
                    tags = "#recurring",
                    accountMode = AccountMode.PERSONAL.name,
                    idempotencyKey = idempotencyKey,
                    userId = schedule.userId
                )

                val insertedId = transactionDao.insertTransaction(newTx)
                walletDao.updateWallet(wallet.copy(balance = newMoney.toDouble().coerceAtLeast(0.0)), schedule.userId)
                scheduleDao.updateLastExecutedCycle(schedule.id, cycleKey)

                RecurringExecutionResult.Executed(
                    transactionId = insertedId,
                    cycleKey = cycleKey,
                    amount = schedule.amount,
                    walletName = schedule.walletName,
                    title = schedule.title
                )
            }
        } catch (e: SQLiteConstraintException) {
            // 11. Transaction rolled back completely.
            // 12. Query getTransactionByIdempotencyKey OUTSIDE transaction
            val committedTx = transactionDao.getTransactionByIdempotencyKey(idempotencyKey)
            return@withWritePermit if (committedTx != null) {
                // 13. Confirmed committed by concurrent worker: treat as clean success
                scheduleDao.updateLastExecutedCycle(schedule.id, cycleKey)
                RecurringExecutionResult.AlreadyExecuted(cycleKey)
            } else {
                // 14. Unrelated constraint failure
                RecurringExecutionResult.Failed(e)
            }
        } catch (e: Throwable) {
            return@withWritePermit RecurringExecutionResult.Failed(e)
            }
        }
    }
}

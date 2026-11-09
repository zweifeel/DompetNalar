package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * Entity representing a recurring installment plan / tenor contract (Cicilan Berjangka).
 * E.g., Cicilan Motor (35x), Cicilan Mobil, Elektronik, Pinjaman Pribadi berangsur.
 *
 * Scoped strictly to [userId] with foreign key cascade to User.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "installment_plans",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class InstallmentPlanItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val provider: String = "",
    val installmentAmount: Double,
    val totalTenor: Int,
    val paidCount: Int = 0,
    val frequency: String = "Bulanan",
    val startDate: String,
    val nextDueDate: String,
    val anchorDay: Int = 15,
    val totalRemainingPrincipal: Double? = null,
    val status: String = "AKTIF", // AKTIF, SELESAI, DIHENTIKAN
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastPaymentDate: Long? = null,
    val userId: Long? = null,
    @ColumnInfo(name = "totalAmountMinorUnits", defaultValue = "NULL")
    val totalAmountMinorUnits: Long? = null,
    @ColumnInfo(name = "currencyCode", defaultValue = "IDR")
    val currencyCode: String = "IDR"
) {
    /**
     * Sisa angsuran = Total tenor - Jumlah angsuran yang sudah dikonfirmasi lunas
     */
    val remainingTenor: Int
        get() = (totalTenor - paidCount).coerceAtLeast(0)

    val isCompleted: Boolean
        get() = paidCount >= totalTenor || status.equals("SELESAI", ignoreCase = true) || status.equals("COMPLETED", ignoreCase = true)

    val isActive: Boolean
        get() = !isCompleted && (status.equals("AKTIF", ignoreCase = true) || status.equals("ACTIVE", ignoreCase = true))

    val currentRunningInstallment: Int
        get() = if (isCompleted) totalTenor else (paidCount + 1).coerceAtMost(totalTenor)

    val currentPeriod: Int
        get() = currentRunningInstallment
}

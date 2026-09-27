package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Wallet::class, TransactionItem::class, CategoryItem::class, User::class],
    version = 2,
    exportSchema = false
)
abstract class FinSmartDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: FinSmartDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): FinSmartDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinSmartDatabase::class.java,
                    "finsmart_database"
                )
                    .addCallback(FinSmartDatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class FinSmartDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: FinSmartDatabase) {
            val walletDao = database.walletDao()
            val categoryDao = database.categoryDao()
            val userDao = database.userDao()

            if (walletDao.getWalletCount() == 0) {
                val initialWallets = listOf(
                    Wallet(
                        name = "Dompet Utama (BCA)",
                        type = WalletType.BANK,
                        accountNumber = "1234567890",
                        balance = 0.0,
                        colorHex = "#0B57D0",
                        iconName = "bca",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = true
                    ),
                    Wallet(
                        name = "GoPay",
                        type = WalletType.EWALLET,
                        accountNumber = "08123456789",
                        balance = 0.0,
                        colorHex = "#00897B",
                        iconName = "gopay",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = false
                    ),
                    Wallet(
                        name = "Uang Tunai",
                        type = WalletType.CASH,
                        accountNumber = "",
                        balance = 0.0,
                        colorHex = "#10B981",
                        iconName = "cash",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = false
                    ),
                    Wallet(
                        name = "Rekening Bisnis (Mandiri)",
                        type = WalletType.BANK,
                        accountNumber = "9876543210",
                        balance = 0.0,
                        colorHex = "#D97706",
                        iconName = "mandiri",
                        accountMode = AccountMode.BUSINESS.name,
                        isDefault = true
                    )
                )
                walletDao.insertWallets(initialWallets)
            }

            if (categoryDao.getCategoryCount() == 0) {
                val initialCategories = listOf(
                    CategoryItem(name = "Makanan & Minuman", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#EF4444"),
                    CategoryItem(name = "Transportasi & Bensin", type = TransactionType.EXPENSE, iconName = "commute", colorHex = "#F59E0B"),
                    CategoryItem(name = "Belanja & Hobi", type = TransactionType.EXPENSE, iconName = "shopping_bag", colorHex = "#EC4899"),
                    CategoryItem(name = "Tagihan & Utilitas", type = TransactionType.EXPENSE, iconName = "receipt", colorHex = "#8B5CF6"),
                    CategoryItem(name = "Kesehatan", type = TransactionType.EXPENSE, iconName = "medical_services", colorHex = "#06B6D4"),
                    CategoryItem(name = "Pendidikan", type = TransactionType.EXPENSE, iconName = "school", colorHex = "#3B82F6"),
                    CategoryItem(name = "Hiburan & Langganan", type = TransactionType.EXPENSE, iconName = "movie", colorHex = "#6366F1"),
                    CategoryItem(name = "Lain-lain", type = TransactionType.EXPENSE, iconName = "more_horiz", colorHex = "#64748B"),

                    CategoryItem(name = "Gaji Pokok", type = TransactionType.INCOME, iconName = "payments", colorHex = "#10B981"),
                    CategoryItem(name = "Bonus & Tunjangan", type = TransactionType.INCOME, iconName = "card_giftcard", colorHex = "#059669"),
                    CategoryItem(name = "Investasi & Dividen", type = TransactionType.INCOME, iconName = "trending_up", colorHex = "#047857"),
                    CategoryItem(name = "Penjualan / Omset", type = TransactionType.INCOME, iconName = "store", colorHex = "#0D9488"),
                    CategoryItem(name = "Pemasukan Lain", type = TransactionType.INCOME, iconName = "account_balance_wallet", colorHex = "#0284C7")
                )
                categoryDao.insertCategories(initialCategories)
            }

            if (userDao.getUserCount() == 0) {
                val initialUsers = listOf(
                    // Master Dev Lukas
                    User(
                        username = "Lukas",
                        email = "lunachroc07@gmail.com",
                        passwordHash = "kodegawa01",
                        role = UserRole.MASTER_DEV,
                        isApproved = true
                    ),
                    // Sample Member 1
                    User(
                        username = "Budi Santoso",
                        email = "budi.santoso@example.com",
                        passwordHash = "123456",
                        role = UserRole.FREE_MEMBER,
                        isApproved = true,
                        trialStartDate = System.currentTimeMillis() - (4L * 86400000L) // 4 days ago (trial expired)
                    ),
                    // Sample Member 2
                    User(
                        username = "Sarah Wijaya",
                        email = "sarah.w@example.com",
                        passwordHash = "123456",
                        role = UserRole.SMART_MASTER,
                        isApproved = true,
                        subscriptionExpiresAt = System.currentTimeMillis() + (30L * 86400000L)
                    )
                )
                userDao.insertUsers(initialUsers)
            }
        }
    }
}

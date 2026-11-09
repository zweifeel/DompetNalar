package com.example.data.chatbot.parser

import com.example.data.model.Wallet
import com.example.data.model.WalletType
import java.util.Locale

/**
 * Resolver nama dan alias dompet finansial deterministik (Local-First).
 *
 * Prinsip Keamanan & Integritas Data:
 * 1. Hanya mencocokkan terhadap daftar dompet aktual milik pengguna aktif ([availableWallets]).
 * 2. Case-insensitive matching dan normalisasi alias Indonesia ("tunai", "cash", "gopay", "bca", dll).
 * 3. DILARANG KERAS menggunakan fallback serampangan seperti `availableWallets.firstOrNull()`!
 * 4. Jika tidak ada dompet yang cocok, kembalikan [NotFound] agar sistem meminta klarifikasi.
 * 5. Jika lebih dari satu dompet cocok dengan derajat keyakinan sama, kembalikan [Ambiguous].
 */
object WalletResolver {

    sealed class Result {
        data class ExactMatch(val wallet: Wallet, val matchedToken: String) : Result()
        data class Ambiguous(val candidateWallets: List<Wallet>, val searchedToken: String) : Result()
        object NotFound : Result()
    }

    /**
     * Alias standar Indonesia untuk memetakan nama umum ke jenis atau nama dompet.
     */
    private val COMMON_ALIASES = mapOf(
        "tunai" to listOf("cash", "tunai", "dompet fisik"),
        "cash" to listOf("cash", "tunai"),
        "uang tunai" to listOf("cash", "tunai"),
        "gopay" to listOf("gopay", "go-pay"),
        "ovo" to listOf("ovo"),
        "dana" to listOf("dana"),
        "shopee" to listOf("shopee", "shopeepay", "spay"),
        "shopeepay" to listOf("shopeepay", "shopee"),
        "bca" to listOf("bca", "bank bca"),
        "bri" to listOf("bri", "bank bri"),
        "bni" to listOf("bni", "bank bni"),
        "mandiri" to listOf("mandiri", "bank mandiri", "livin"),
        "jago" to listOf("jago", "bank jago"),
        "seabank" to listOf("seabank", "sea bank"),
        "jenius" to listOf("jenius", "btpn"),
        "bsi" to listOf("bsi", "syariah")
    )

    /**
     * Mencari dompet yang disebut di dalam kalimat input pengguna.
     *
     * @param input Kalimat pengguna
     * @param availableWallets Daftar dompet aktif akun saat ini
     * @param excludeWalletId ID dompet yang dikecualikan (misal dompet sumber saat mencari dompet target transfer)
     */
    fun resolveWalletFromText(
        input: String,
        availableWallets: List<Wallet>,
        excludeWalletId: Long? = null
    ): Result {
        if (availableWallets.isEmpty()) return Result.NotFound
        val lowerInput = input.lowercase(Locale("id", "ID")).trim()
        val pool = if (excludeWalletId != null) availableWallets.filter { it.id != excludeWalletId } else availableWallets

        // 1. Pencocokan Nama Persis (Exact Substring Matching)
        // Cari nama dompet terpanjang yang muncul dalam kalimat (mengurangi false-positive nama pendek)
        val sortedByNameLength = pool.sortedByDescending { it.name.length }
        val exactNameMatches = sortedByNameLength.filter { w ->
            val wName = w.name.lowercase(Locale("id", "ID")).trim()
            wName.isNotBlank() && containsWordOrPhrase(lowerInput, wName)
        }

        if (exactNameMatches.size == 1) {
            val matched = exactNameMatches.first()
            return Result.ExactMatch(matched, matched.name)
        } else if (exactNameMatches.size > 1) {
            return Result.Ambiguous(exactNameMatches, exactNameMatches.first().name)
        }

        // 2. Pencocokan melalui Kamus Alias Finansial
        for ((aliasKey, aliases) in COMMON_ALIASES) {
            val aliasMatched = aliases.any { alias -> containsWordOrPhrase(lowerInput, alias) }
            if (aliasMatched) {
                // Cari dompet di pool yang namanya atau tipenya cocok dengan alias ini
                val matchingInPool = pool.filter { w ->
                    val wName = w.name.lowercase(Locale("id", "ID"))
                    if (aliasKey == "tunai" || aliasKey == "cash" || aliasKey == "uang tunai") {
                        w.type == WalletType.CASH || wName.contains("cash") || wName.contains("tunai")
                    } else {
                        aliases.any { a -> wName.contains(a) }
                    }
                }

                if (matchingInPool.size == 1) {
                    return Result.ExactMatch(matchingInPool.first(), aliasKey)
                } else if (matchingInPool.size > 1) {
                    return Result.Ambiguous(matchingInPool, aliasKey)
                }
            }
        }

        // 3. Pencocokan Tipikal Berdasarkan Tipe Dompet
        if (containsWordOrPhrase(lowerInput, "rekening") || containsWordOrPhrase(lowerInput, "bank")) {
            val bankWallets = pool.filter { it.type == WalletType.BANK }
            if (bankWallets.size == 1) {
                return Result.ExactMatch(bankWallets.first(), "bank")
            } else if (bankWallets.size > 1) {
                return Result.Ambiguous(bankWallets, "bank")
            }
        }

        // TIDAK DITEMUKAN: DILARANG FALLBACK KE FIRST OR NULL!
        return Result.NotFound
    }

    /**
     * Memeriksa keberadaan kata/frasa lengkap dalam teks dengan batas kata (word boundaries).
     */
    fun containsWordOrPhrase(text: String, wordOrPhrase: String): Boolean {
        val cleanWord = Regex.escape(wordOrPhrase.trim().lowercase())
        val pattern = Regex("(?i)(?:^|\\s|\\p{Punct})$cleanWord(?:$|\\s|\\p{Punct})")
        return pattern.containsMatchIn(text)
    }
}

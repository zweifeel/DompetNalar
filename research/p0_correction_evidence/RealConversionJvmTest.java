package research.p0_correction_evidence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class RealConversionJvmTest {

    public static class ConversionResult {
        public final String tier;
        public final Long minorUnits;
        public final String reason;

        public ConversionResult(String tier, Long minorUnits, String reason) {
            this.tier = tier;
            this.minorUnits = minorUnits;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "ConversionResult(tier=" + tier + ", minorUnits=" + minorUnits + ", reason=" + reason + ")";
        }
    }

    private static final Map<String, Integer> CURRENCY_EXPONENTS = new HashMap<>();
    static {
        CURRENCY_EXPONENTS.put("IDR", 2);
        CURRENCY_EXPONENTS.put("USD", 2);
        CURRENCY_EXPONENTS.put("EUR", 2);
        CURRENCY_EXPONENTS.put("JPY", 0);
        CURRENCY_EXPONENTS.put("BHD", 3);
    }

    // Proposed Technical Business Safety Ceiling (Rp 900 Miliar) - Pending Owner Approval
    public static final double PROPOSED_BUSINESS_CEILING_IDR = 900_000_000_000.0;

    // Objective Mathematical Maximum of signed Long (64-bit) for exponent e
    public static long getMathematicalMaxMajorUnits(int exponent) {
        long factor = (long) Math.pow(10, exponent);
        return Long.MAX_VALUE / factor;
    }

    // Objective IEEE 754 consecutive integer exactness limit: 2^53 - 1
    public static double getIeee754ExactMantissaLimit(int exponent) {
        long maxExactInteger = (1L << 53) - 1; // 9,007,199,254,740,991
        double factor = Math.pow(10, exponent);
        return maxExactInteger / factor;
    }

    public static ConversionResult convertLegacyReal(
        double amountReal,
        String currencyCode,
        String fieldName,
        boolean allowNegative
    ) {
        if (!CURRENCY_EXPONENTS.containsKey(currencyCode)) {
            return new ConversionResult("INVALID_QUARANTINE_REQUIRED", null, "UNKNOWN_CURRENCY_" + currencyCode);
        }
        int exponent = CURRENCY_EXPONENTS.get(currencyCode);
        long factorLong = (long) Math.pow(10, exponent);
        BigDecimal factorDec = BigDecimal.TEN.pow(exponent);

        if (Double.isNaN(amountReal)) {
            return new ConversionResult("INVALID_QUARANTINE_REQUIRED", null, "NAN_VALUE");
        }
        if (Double.isInfinite(amountReal)) {
            return new ConversionResult("INVALID_QUARANTINE_REQUIRED", null, "INFINITY_VALUE");
        }

        // Handle negative zero (-0.0)
        if (amountReal == 0.0) {
            return new ConversionResult("SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY", 0L, null);
        }

        // Semantics of negative values based on schema column
        if (amountReal < 0.0 && !allowNegative) {
            return new ConversionResult("INVALID_QUARANTINE_REQUIRED", null, "NEGATIVE_NOT_PERMITTED_FOR_" + fieldName);
        }

        double absAmount = Math.abs(amountReal);

        // Check Proposed Business Ceiling (IDR)
        if ("IDR".equals(currencyCode) && absAmount > PROPOSED_BUSINESS_CEILING_IDR) {
            return new ConversionResult("OVERFLOW", null, "EXCEEDS_PROPOSED_BUSINESS_CEILING_" + PROPOSED_BUSINESS_CEILING_IDR);
        }

        // Check Mathematical Long.MAX_VALUE Bound
        long maxMajor = getMathematicalMaxMajorUnits(exponent);
        if (absAmount > maxMajor) {
            return new ConversionResult("OVERFLOW", null, "EXCEEDS_SIGNED_LONG_MAX");
        }

        // Check IEEE 754 Exact Consecutive Integer Limit
        double maxExactDouble = getIeee754ExactMantissaLimit(exponent);
        if (absAmount > maxExactDouble) {
            return new ConversionResult("PRECISION_LOSS_SUSPECTED", null, "EXCEEDS_IEEE754_MANTISSA_LIMIT");
        }

        // Canonical decimal parsing via BigDecimal.valueOf(Double)
        BigDecimal decimal = BigDecimal.valueOf(amountReal);
        BigDecimal normalized = decimal.stripTrailingZeros();
        int effectiveScale = Math.max(0, normalized.scale());

        if (effectiveScale <= exponent) {
            // Exactly representable within currency decimal places
            long minorUnits = decimal.multiply(factorDec).setScale(0, RoundingMode.UNNECESSARY).longValue();
            return new ConversionResult("SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY", minorUnits, null);
        } else {
            // Scale exceeds exponent:
            // Conservative, mathematically honest policy:
            // Math.ulp(d) reflects representation granularity, NOT user intent.
            // Any genuine non-zero decimal beyond currency exponent CANNOT be proven to be noise!
            return new ConversionResult("PRECISION_LOSS_SUSPECTED", null, "EXCESS_FRACTIONAL_DECIMALS_SCALE_" + effectiveScale);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== EXECUTING NATIVE JVM TEST: RealConversionJvmTest ===");
        System.out.println("Java Vendor: " + System.getProperty("java.vendor"));
        System.out.println("Java Version: " + System.getProperty("java.version"));

        // 1. Standard IDR 150,000.00
        ConversionResult r1 = convertLegacyReal(150000.0, "IDR", "transactions.amount", false);
        System.out.println("1. Standard IDR: " + r1);
        if (!"SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY".equals(r1.tier) || r1.minorUnits != 15000000L) throw new AssertionError();

        // 2. Negative wallet balance (allowed)
        ConversionResult r2 = convertLegacyReal(-250000.50, "IDR", "wallets.balance", true);
        System.out.println("2. Negative wallet balance: " + r2);
        if (!"SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY".equals(r2.tier) || r2.minorUnits != -25000050L) throw new AssertionError();

        // 3. Negative transaction amount (forbidden)
        ConversionResult r3 = convertLegacyReal(-50000.0, "IDR", "transactions.amount", false);
        System.out.println("3. Negative transaction amount: " + r3);
        if (!"INVALID_QUARANTINE_REQUIRED".equals(r3.tier)) throw new AssertionError();

        // 4. Genuine excess fractional sen (15000.123 IDR)
        ConversionResult r4 = convertLegacyReal(15000.123, "IDR", "transactions.amount", false);
        System.out.println("4. Excess fractional sen (15000.123): " + r4);
        if (!"PRECISION_LOSS_SUSPECTED".equals(r4.tier)) throw new AssertionError();

        // 5. Negative Zero (-0.0)
        ConversionResult r5 = convertLegacyReal(-0.0, "IDR", "transactions.amount", false);
        System.out.println("5. Negative Zero (-0.0): " + r5);
        if (!"SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY".equals(r5.tier) || r5.minorUnits != 0L) throw new AssertionError();

        // 6. Rounding boundary edge case: 0.1 + 0.2 in Double arithmetic
        double doubleSum = 0.1 + 0.2; // 0.30000000000000004
        System.out.println("6a. Raw 0.1 + 0.2 double value: " + doubleSum);
        System.out.println("6b. Double.toString(0.1 + 0.2): " + Double.toString(doubleSum));
        ConversionResult r6 = convertLegacyReal(doubleSum, "USD", "transactions.amount", false);
        System.out.println("6c. 0.1 + 0.2 converted: " + r6);
        // Double.toString(0.1 + 0.2) outputs 17 decimal places, so conservative policy quarantines it
        if (!"PRECISION_LOSS_SUSPECTED".equals(r6.tier)) throw new AssertionError();

        // 7. NaN and Infinities
        ConversionResult r7a = convertLegacyReal(Double.NaN, "IDR", "transactions.amount", false);
        ConversionResult r7b = convertLegacyReal(Double.POSITIVE_INFINITY, "IDR", "transactions.amount", false);
        System.out.println("7. NaN: " + r7a + ", Inf: " + r7b);
        if (!"INVALID_QUARANTINE_REQUIRED".equals(r7a.tier) || !"INVALID_QUARANTINE_REQUIRED".equals(r7b.tier)) throw new AssertionError();

        // 8. Overflow at each exponent:
        // Exponent 0 (JPY): max major = Long.MAX_VALUE
        System.out.println("8a. JPY max major: " + getMathematicalMaxMajorUnits(0));
        // Exponent 2 (IDR/USD): max major = Long.MAX_VALUE / 100
        System.out.println("8b. IDR max major: " + getMathematicalMaxMajorUnits(2));
        // Exponent 3 (BHD): max major = Long.MAX_VALUE / 1000
        System.out.println("8c. BHD max major: " + getMathematicalMaxMajorUnits(3));

        // 9. Proposed Business Ceiling Check (Rp 1 Trillion > Rp 900 Miliar)
        ConversionResult r9 = convertLegacyReal(1_000_000_000_000.0, "IDR", "transactions.amount", false);
        System.out.println("9. Rp 1 Trillion vs Rp 900B ceiling: " + r9);
        if (!"OVERFLOW".equals(r9.tier)) throw new AssertionError();

        // 10. JPY 5,000 (exponent 0)
        ConversionResult r10 = convertLegacyReal(5000.0, "JPY", "transactions.amount", false);
        System.out.println("10. JPY 5,000 (exponent 0): " + r10);
        if (!"SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY".equals(r10.tier) || r10.minorUnits != 5000L) throw new AssertionError();

        System.out.println("\nALL 10 NATIVE JVM TESTS PASSED 100%!");
    }
}

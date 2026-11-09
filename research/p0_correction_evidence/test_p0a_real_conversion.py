import math
from decimal import Decimal, ROUND_HALF_EVEN

class ConversionResult:
    def __init__(self, tier, minor_units=None, reason=None):
        self.tier = tier
        self.minor_units = minor_units
        self.reason = reason

    def __repr__(self):
        return f"Result(tier={self.tier}, minor_units={self.minor_units}, reason={self.reason})"

# ISO 4217 Currency Exponents
CURRENCY_EXPONENTS = {
    "IDR": 2,
    "USD": 2,
    "EUR": 2,
    "JPY": 0,
    "BHD": 3
}

# Business Safety Ceiling (Separately approved for DompetNalar personal finance)
BUSINESS_SAFETY_CEILING_IDR = 900_000_000_000.0 # Rp 900 Miliar

def convert_legacy_real(
    amount_real: float,
    currency_code: str,
    field_name: str,
    allow_negative: bool = False
) -> ConversionResult:
    # 1. Check Currency Code Validity
    if currency_code not in CURRENCY_EXPONENTS:
        return ConversionResult("INVALID_QUARANTINE_REQUIRED", reason=f"UNKNOWN_CURRENCY_{currency_code}")

    exponent = CURRENCY_EXPONENTS[currency_code]
    factor = 10 ** exponent

    # 2. Check NaN and Infinities
    if math.isnan(amount_real):
        return ConversionResult("INVALID_QUARANTINE_REQUIRED", reason="NAN_VALUE")
    if math.isinf(amount_real):
        return ConversionResult("INVALID_QUARANTINE_REQUIRED", reason="INFINITY_VALUE")

    # 3. Handle Negative Zero (-0.0)
    if amount_real == 0.0:
        return ConversionResult("SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY", minor_units=0)

    # 4. Check Negative Values per Field Semantics
    if amount_real < 0.0 and not allow_negative:
        return ConversionResult("INVALID_QUARANTINE_REQUIRED", reason=f"NEGATIVE_NOT_PERMITTED_FOR_{field_name}")

    abs_amount = abs(amount_real)

    # 5. Check Business Safety Ceiling (For IDR)
    if currency_code == "IDR" and abs_amount > BUSINESS_SAFETY_CEILING_IDR:
        return ConversionResult("OVERFLOW", reason=f"EXCEEDS_BUSINESS_CEILING_{BUSINESS_SAFETY_CEILING_IDR}")

    # 6. Check Mathematical Max (Signed 64-bit Long)
    # Long.MAX_VALUE = 9,223,372,036,854,775,807
    max_major_units = 9223372036854775807 // factor
    if abs_amount > max_major_units:
        return ConversionResult("OVERFLOW", reason="EXCEEDS_SIGNED_LONG_MAX")

    # 7. Check IEEE 754 Consecutive Integer Exactness Limit (2^53 - 1)
    max_exact_double_units = (2**53 - 1) / factor
    if abs_amount > max_exact_double_units:
        return ConversionResult("PRECISION_LOSS_SUSPECTED", reason="EXCEEDS_IEEE754_EXACT_MANTISSA_LIMIT")

    # 8. Canonical Decimal Parsing via String representation (equivalent to BigDecimal.valueOf(Double))
    canonical_str = str(amount_real)
    d = Decimal(canonical_str)

    # Normalize trailing zero scale: e.g. "5000.0" -> 5000 has effective scale 0
    # In BigDecimal, stripTrailingZeros() eliminates trailing zero noise from float representation
    normalized_d = d.normalize()
    effective_scale = max(0, -normalized_d.as_tuple().exponent)

    # 9. Evaluate ULP and Fractional Excess
    ulp = math.ulp(amount_real)
    minor_unit_step = 1.0 / factor

    if effective_scale <= exponent:
        # Fits cleanly within currency exponent without fractional residual
        minor_units = int((d * factor).to_integral_exact(rounding=ROUND_HALF_EVEN))
        return ConversionResult("SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY", minor_units=minor_units)
    else:
        # Scale exceeds currency exponent:
        # Distinguish floating-point representation artifact from genuine legacy fractional sen
        residual = d % Decimal(str(minor_unit_step))
        if float(residual) < ulp:
            # Artifact noise: safely round HALF_EVEN under explicit normalization policy
            minor_units = int((d * factor).quantize(Decimal('1'), rounding=ROUND_HALF_EVEN))
            return ConversionResult("AMBIGUOUS_FLOATING_POINT_NOISE", minor_units=minor_units, reason="NOISE_NORMALIZED_HALF_EVEN")
        else:
            # Genuine excess fraction: must NOT be silently rounded!
            return ConversionResult("PRECISION_LOSS_SUSPECTED", reason=f"GENUINE_EXCESS_FRACTIONAL_SEN_SCALE_{effective_scale}")

# --- TEST SUITE ---
def run_conversion_tests():
    print("=== EXECUTING P0-A REAL CONVERSION ALGORITHM TEST SUITE ===")
    
    # Test 1: Clean standard IDR amount
    r1 = convert_legacy_real(150000.0, "IDR", "transactions.amount")
    print("1. Standard IDR 150,000:", r1)
    assert r1.tier == "SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY" and r1.minor_units == 15000000

    # Test 2: Negative wallet balance (allowed for wallets)
    r2 = convert_legacy_real(-250000.50, "IDR", "wallets.balance", allow_negative=True)
    print("2. Negative wallet balance -250,000.50:", r2)
    assert r2.tier == "SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY" and r2.minor_units == -25000050

    # Test 3: Negative transaction amount (FORBIDDEN for transactions)
    r3 = convert_legacy_real(-50000.0, "IDR", "transactions.amount", allow_negative=False)
    print("3. Negative transaction amount -50,000:", r3)
    assert r3.tier == "INVALID_QUARANTINE_REQUIRED" and "NEGATIVE_NOT_PERMITTED" in r3.reason

    # Test 4: Genuine excess fractional sen (15000.123 IDR - 3 decimals)
    r4 = convert_legacy_real(15000.123, "IDR", "transactions.amount")
    print("4. Genuine excess fractional sen 15,000.123 IDR:", r4)
    assert r4.tier == "PRECISION_LOSS_SUSPECTED"

    # Test 5: Negative Zero (-0.0)
    r5 = convert_legacy_real(-0.0, "IDR", "transactions.amount")
    print("5. Negative Zero -0.0:", r5)
    assert r5.tier == "SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY" and r5.minor_units == 0

    # Test 6: NaN
    r6 = convert_legacy_real(float("nan"), "IDR", "transactions.amount")
    print("6. NaN value:", r6)
    assert r6.tier == "INVALID_QUARANTINE_REQUIRED" and r6.reason == "NAN_VALUE"

    # Test 7: Infinity
    r7 = convert_legacy_real(float("inf"), "IDR", "transactions.amount")
    print("7. Infinity value:", r7)
    assert r7.tier == "INVALID_QUARANTINE_REQUIRED" and r7.reason == "INFINITY_VALUE"

    # Test 8: Exceeds Business Safety Ceiling (Rp 1 Trillion > Rp 900 Miliar)
    r8 = convert_legacy_real(1_000_000_000_000.0, "IDR", "transactions.amount")
    print("8. Exceeds Business Safety Ceiling (Rp 1 Trillion):", r8)
    assert r8.tier == "OVERFLOW" and "EXCEEDS_BUSINESS_CEILING" in r8.reason

    # Test 9: Zero exponent currency (JPY 5,000)
    r9 = convert_legacy_real(5000.0, "JPY", "transactions.amount")
    print("9. JPY 5,000 (exponent 0):", r9)
    assert r9.tier == "SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY" and r9.minor_units == 5000

    # Test 10: Unknown currency code
    r10 = convert_legacy_real(100.0, "XYZ", "transactions.amount")
    print("10. Unknown currency XYZ:", r10)
    assert r10.tier == "INVALID_QUARANTINE_REQUIRED" and "UNKNOWN_CURRENCY" in r10.reason

    print("\nALL 10 P0-A REAL CONVERSION TESTS PASSED PERFECTLY!")

run_conversion_tests()

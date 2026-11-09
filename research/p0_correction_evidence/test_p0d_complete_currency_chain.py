"""
Test P0-D Complete Currency Chain Verification
Covers all 6 tables:
- wallets
- transactions
- installment_plans
- installment_periods
- installment_payments
- installment_payment_allocations
"""
import sqlite3

def run_tests():
    con = sqlite3.connect(':memory:')
    con.execute('PRAGMA foreign_keys = ON')

    # DDL with Composite Foreign Keys for Currency Consistency
    con.execute('''CREATE TABLE wallets (
        id INTEGER PRIMARY KEY,
        currencyCode TEXT NOT NULL,
        name TEXT NOT NULL,
        CONSTRAINT uq_wallet_curr UNIQUE (id, currencyCode)
    )''')

    con.execute('''CREATE TABLE transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        walletId INTEGER NOT NULL,
        currencyCode TEXT NOT NULL,
        amountMinorUnits INTEGER NOT NULL,
        CONSTRAINT uq_tx_curr UNIQUE (id, currencyCode),
        CONSTRAINT fk_tx_wallet FOREIGN KEY (walletId, currencyCode)
            REFERENCES wallets(id, currencyCode)
            ON UPDATE RESTRICT ON DELETE RESTRICT
    )''')

    con.execute('''CREATE TABLE installment_plans (
        id TEXT PRIMARY KEY,
        currencyCode TEXT NOT NULL,
        totalAmountMinorUnits INTEGER NOT NULL,
        CONSTRAINT uq_plan_curr UNIQUE (id, currencyCode)
    )''')

    con.execute('''CREATE TABLE installment_periods (
        id TEXT PRIMARY KEY,
        planId TEXT NOT NULL,
        currencyCode TEXT NOT NULL,
        periodNumber INTEGER NOT NULL,
        amountDueMinorUnits INTEGER NOT NULL,
        CONSTRAINT uq_period_curr UNIQUE (id, currencyCode),
        CONSTRAINT fk_period_plan FOREIGN KEY (planId, currencyCode)
            REFERENCES installment_plans(id, currencyCode)
            ON UPDATE RESTRICT ON DELETE RESTRICT
    )''')

    con.execute('''CREATE TABLE installment_payments (
        id TEXT PRIMARY KEY,
        planId TEXT NOT NULL,
        transactionId INTEGER NOT NULL,
        currencyCode TEXT NOT NULL,
        totalMinorUnits INTEGER NOT NULL,
        isVoid INTEGER NOT NULL DEFAULT 0 CHECK(isVoid IN (0, 1)),
        CONSTRAINT uq_payment_curr UNIQUE (id, currencyCode),
        CONSTRAINT fk_pay_plan FOREIGN KEY (planId, currencyCode)
            REFERENCES installment_plans(id, currencyCode)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT fk_pay_tx FOREIGN KEY (transactionId, currencyCode)
            REFERENCES transactions(id, currencyCode)
            ON UPDATE RESTRICT ON DELETE RESTRICT
    )''')

    con.execute('''CREATE TABLE installment_payment_allocations (
        id TEXT PRIMARY KEY,
        paymentId TEXT NOT NULL,
        periodId TEXT NOT NULL,
        currencyCode TEXT NOT NULL,
        allocatedMinorUnits INTEGER NOT NULL,
        isVoid INTEGER NOT NULL DEFAULT 0 CHECK(isVoid IN (0, 1)),
        CONSTRAINT fk_alloc_payment FOREIGN KEY (paymentId, currencyCode)
            REFERENCES installment_payments(id, currencyCode)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT fk_alloc_period FOREIGN KEY (periodId, currencyCode)
            REFERENCES installment_periods(id, currencyCode)
            ON UPDATE RESTRICT ON DELETE RESTRICT
    )''')

    print("--- P0-D TEST SUITE STARTING ---")

    # 1. Valid multi-currency insert across all 6 tables (IDR and USD)
    con.execute("INSERT INTO wallets VALUES (1, 'IDR', 'Dompet Utama')")
    con.execute("INSERT INTO wallets VALUES (2, 'USD', 'Dollar Stash')")

    con.execute("INSERT INTO transactions VALUES (101, 1, 'IDR', 100000000)")
    con.execute("INSERT INTO transactions VALUES (102, 2, 'USD', 5000)")

    con.execute("INSERT INTO installment_plans VALUES ('plan_idr_1', 'IDR', 100000000)")
    con.execute("INSERT INTO installment_plans VALUES ('plan_usd_1', 'USD', 5000)")

    con.execute("INSERT INTO installment_periods VALUES ('per_idr_1', 'plan_idr_1', 'IDR', 1, 100000000)")
    con.execute("INSERT INTO installment_periods VALUES ('per_usd_1', 'plan_usd_1', 'USD', 1, 5000)")

    con.execute("INSERT INTO installment_payments VALUES ('pay_idr_1', 'plan_idr_1', 101, 'IDR', 100000000, 0)")
    con.execute("INSERT INTO installment_payments VALUES ('pay_usd_1', 'plan_usd_1', 102, 'USD', 5000, 0)")

    con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_idr_1', 'pay_idr_1', 'per_idr_1', 'IDR', 100000000, 0)")
    con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_usd_1', 'pay_usd_1', 'per_usd_1', 'USD', 5000, 0)")
    con.commit()
    print("Scenario 1 [Valid multi-currency insert (IDR & USD across all 6 tables)]: PASS")

    # 2. Mismatch wallet currency vs transaction currency (Wallet IDR, Tx USD)
    try:
        con.execute("INSERT INTO transactions VALUES (103, 1, 'USD', 2000)")
        raise AssertionError("Scenario 2 FAILED: should block transaction with mismatched currency vs wallet")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 2 [Mismatch wallet vs transaction currency blocked by fk_tx_wallet]: PASS ({e})")

    # 3. Mismatch transaction currency vs payment currency (Tx IDR, Payment USD)
    try:
        con.execute("INSERT INTO installment_payments VALUES ('pay_err_1', 'plan_usd_1', 101, 'USD', 5000, 0)")
        raise AssertionError("Scenario 3 FAILED: should block payment linking to mismatched tx currency")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 3 [Mismatch transaction vs payment currency blocked by fk_pay_tx]: PASS ({e})")

    # 4. Mismatch payment currency vs allocation currency (Payment IDR, Allocation USD)
    try:
        con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_err_1', 'pay_idr_1', 'per_usd_1', 'USD', 5000, 0)")
        raise AssertionError("Scenario 4 FAILED: should block allocation with mismatched currency vs payment")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 4 [Mismatch payment vs allocation currency blocked by fk_alloc_payment]: PASS ({e})")

    # 5. Mismatch plan currency vs period currency (Plan IDR, Period USD)
    try:
        con.execute("INSERT INTO installment_periods VALUES ('per_err_1', 'plan_idr_1', 'USD', 2, 50000000)")
        raise AssertionError("Scenario 5 FAILED: should block period with mismatched currency vs plan")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 5 [Mismatch plan vs period currency blocked by fk_period_plan]: PASS ({e})")

    # 6. Mismatch period currency vs allocation currency (Allocation IDR referencing Period USD)
    try:
        con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_err_2', 'pay_idr_1', 'per_usd_1', 'IDR', 50000000, 0)")
        raise AssertionError("Scenario 6 FAILED: should block allocation referencing mismatched period currency")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 6 [Mismatch period vs allocation currency blocked by fk_alloc_period]: PASS ({e})")

    # 7. UPDATE currency on child table (e.g. mutate currencyCode on allocation or payment)
    try:
        con.execute("UPDATE installment_payment_allocations SET currencyCode = 'EUR' WHERE id = 'alloc_idr_1'")
        raise AssertionError("Scenario 7 FAILED: should block updating currency on child table")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 7 [UPDATE currency on child blocked by foreign key constraint]: PASS ({e})")

    # 8. UPDATE currency on parent (e.g. mutate wallet currencyCode or plan currencyCode)
    try:
        con.execute("UPDATE wallets SET currencyCode = 'SGD' WHERE id = 1")
        raise AssertionError("Scenario 8 FAILED: should block updating currency on parent wallet with active references")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 8 [UPDATE currency on parent blocked by ON UPDATE RESTRICT]: PASS ({e})")

    try:
        con.execute("UPDATE installment_plans SET currencyCode = 'EUR' WHERE id = 'plan_idr_1'")
        raise AssertionError("Scenario 8b FAILED: should block updating currency on plan with active periods/payments")
    except sqlite3.IntegrityError as e:
        print(f"Scenario 8b [UPDATE currency on parent plan blocked by ON UPDATE RESTRICT]: PASS ({e})")

    print("\n--- ALL 8 P0-D SCENARIOS VERIFIED PASS ---")

if __name__ == '__main__':
    run_tests()

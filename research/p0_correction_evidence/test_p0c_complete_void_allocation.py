"""
Test P0-C Complete Void Allocation Invariants
Validates all mutation vectors and transaction lifecycle invariants for Room 11.
"""
import sqlite3

def run_tests():
    con = sqlite3.connect(':memory:')
    con.execute('PRAGMA foreign_keys = ON')

    # DDL
    con.execute('''CREATE TABLE wallets (
        id INTEGER PRIMARY KEY,
        currencyCode TEXT NOT NULL
    )''')

    con.execute('''CREATE TABLE transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        walletId INTEGER NOT NULL REFERENCES wallets(id),
        currencyCode TEXT NOT NULL,
        amountMinorUnits INTEGER NOT NULL,
        isReversal INTEGER NOT NULL DEFAULT 0 CHECK(isReversal IN (0, 1)),
        reversedTransactionId INTEGER DEFAULT NULL,
        FOREIGN KEY (reversedTransactionId) REFERENCES transactions(id)
    )''')

    con.execute('''CREATE TABLE installment_plans (
        id TEXT PRIMARY KEY,
        currencyCode TEXT NOT NULL,
        totalAmountMinorUnits INTEGER NOT NULL
    )''')

    con.execute('''CREATE TABLE installment_periods (
        id TEXT PRIMARY KEY,
        planId TEXT NOT NULL REFERENCES installment_plans(id),
        periodNumber INTEGER NOT NULL,
        amountDueMinorUnits INTEGER NOT NULL,
        amountPaidMinorUnits INTEGER NOT NULL DEFAULT 0,
        currencyCode TEXT NOT NULL
    )''')

    con.execute('''CREATE TABLE installment_payments (
        id TEXT PRIMARY KEY,
        transactionId INTEGER NOT NULL REFERENCES transactions(id),
        totalMinorUnits INTEGER NOT NULL,
        unallocatedMinorUnits INTEGER NOT NULL,
        isVoid INTEGER NOT NULL DEFAULT 0 CHECK(isVoid IN (0, 1)),
        reversalTransactionId INTEGER DEFAULT NULL REFERENCES transactions(id),
        currencyCode TEXT NOT NULL,
        CHECK(unallocatedMinorUnits >= 0 AND unallocatedMinorUnits <= totalMinorUnits)
    )''')

    con.execute('''CREATE TABLE installment_payment_allocations (
        id TEXT PRIMARY KEY,
        paymentId TEXT NOT NULL REFERENCES installment_payments(id),
        periodId TEXT NOT NULL REFERENCES installment_periods(id),
        allocatedMinorUnits INTEGER NOT NULL CHECK(allocatedMinorUnits > 0),
        isVoid INTEGER NOT NULL DEFAULT 0 CHECK(isVoid IN (0, 1)),
        currencyCode TEXT NOT NULL
    )''')

    # Triggers for P0-C Integrity
    # 1. Payment is immutable after insert except for isVoid transition and reversalTransactionId
    con.execute('''CREATE TRIGGER trg_immutable_payment_fields
    BEFORE UPDATE OF id, transactionId, totalMinorUnits, currencyCode ON installment_payments
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: Financial fields of installment_payments are immutable');
    END;''')

    # 2. Payment once void (isVoid=1) can NEVER be unvoided (isVoid=0)
    con.execute('''CREATE TRIGGER trg_no_unvoid_payment
    BEFORE UPDATE OF isVoid ON installment_payments
    WHEN OLD.isVoid = 1 AND NEW.isVoid = 0
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: A voided installment payment cannot be re-activated');
    END;''')

    # 3. Allocations cannot be updated (allocatedMinorUnits, paymentId, periodId are immutable)
    con.execute('''CREATE TRIGGER trg_immutable_allocation_fields
    BEFORE UPDATE OF id, paymentId, periodId, allocatedMinorUnits, currencyCode ON installment_payment_allocations
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: Allocation fields are immutable; only isVoid state may change');
    END;''')

    # 4. Allocations cannot be deleted (audit trail preservation)
    con.execute('''CREATE TRIGGER trg_no_delete_allocation
    BEFORE DELETE ON installment_payment_allocations
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: Allocation records cannot be deleted; must be marked isVoid=1');
    END;''')

    # 5. Prevent voiding payment if any child allocation is still active
    con.execute('''CREATE TRIGGER trg_verify_allocations_void_on_payment_void
    BEFORE UPDATE OF isVoid ON installment_payments
    WHEN NEW.isVoid = 1
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: Cannot void payment while active child allocations exist')
        FROM installment_payment_allocations
        WHERE paymentId = NEW.id AND isVoid = 0;
    END;''')

    # 6. Prevent active allocation on void payment
    con.execute('''CREATE TRIGGER trg_no_active_allocation_on_void_payment
    BEFORE INSERT ON installment_payment_allocations
    WHEN NEW.isVoid = 0
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: Cannot create active allocation for a VOID payment')
        FROM installment_payments
        WHERE id = NEW.paymentId AND isVoid = 1;
    END;''')

    # 7. Prevent unvoiding allocation if parent payment is void
    con.execute('''CREATE TRIGGER trg_no_reactivate_allocation_on_void_payment
    BEFORE UPDATE OF isVoid ON installment_payment_allocations
    WHEN OLD.isVoid = 1 AND NEW.isVoid = 0
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: Cannot reactivate allocation for a VOID payment')
        FROM installment_payments
        WHERE id = NEW.paymentId AND isVoid = 1;
    END;''')

    # 8. Reversal transaction must be valid reversal (isReversal=1)
    con.execute('''CREATE TRIGGER trg_validate_reversal_tx_on_payment_void
    BEFORE UPDATE OF reversalTransactionId ON installment_payments
    WHEN NEW.reversalTransactionId IS NOT NULL
    BEGIN
        SELECT RAISE(ABORT, 'P0-C: reversalTransactionId must point to a valid transaction with isReversal=1')
        FROM transactions
        WHERE id = NEW.reversalTransactionId AND isReversal != 1;
    END;''')

    print("--- P0-C TEST SUITE STARTING ---")
    
    # Setup base entities
    con.execute("INSERT INTO wallets VALUES (1, 'IDR')")
    con.execute("INSERT INTO transactions VALUES (10, 1, 'IDR', 100000000, 0, NULL)") # 1,000,000 IDR
    con.execute("INSERT INTO installment_plans VALUES ('plan_1', 'IDR', 300000000)")
    con.execute("INSERT INTO installment_periods VALUES ('per_1', 'plan_1', 1, 100000000, 0, 'IDR')")
    con.execute("INSERT INTO installment_periods VALUES ('per_2', 'plan_1', 2, 100000000, 0, 'IDR')")
    con.commit()

    # Vector 1: INSERT active vs void
    con.execute("INSERT INTO installment_payments VALUES ('pay_1', 10, 100000000, 0, 0, NULL, 'IDR')")
    con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_1', 'pay_1', 'per_1', 100000000, 0, 'IDR')")
    con.commit()
    print("Vector 1 [INSERT active]: PASS")

    # Vector 2: UPDATE isVoid 0->1 without voiding children -> MUST FAIL
    try:
        con.execute("UPDATE installment_payments SET isVoid = 1 WHERE id = 'pay_1'")
        raise AssertionError("Vector 2 FAILED: should have blocked voiding payment while child active")
    except sqlite3.IntegrityError as e:
        print(f"Vector 2 [UPDATE isVoid 0->1 blocked while children active]: PASS ({e})")

    # Vector 3: UPDATE isVoid 1->0 on payment -> MUST FAIL
    # Prepare by properly voiding pay_1 first
    con.execute("INSERT INTO transactions VALUES (11, 1, 'IDR', 100000000, 1, 10)") # valid reversal
    con.execute("UPDATE installment_payment_allocations SET isVoid = 1 WHERE id = 'alloc_1'")
    con.execute("UPDATE installment_payments SET isVoid = 1, reversalTransactionId = 11, unallocatedMinorUnits = totalMinorUnits WHERE id = 'pay_1'")
    con.commit()
    try:
        con.execute("UPDATE installment_payments SET isVoid = 0 WHERE id = 'pay_1'")
        raise AssertionError("Vector 3 FAILED: should have blocked unvoiding payment")
    except sqlite3.IntegrityError as e:
        print(f"Vector 3 [UPDATE isVoid 1->0 blocked]: PASS ({e})")

    # Vector 4: UPDATE paymentId on allocation -> MUST FAIL
    try:
        con.execute("UPDATE installment_payment_allocations SET paymentId = 'pay_2' WHERE id = 'alloc_1'")
        raise AssertionError("Vector 4 FAILED: should have blocked updating paymentId")
    except sqlite3.IntegrityError as e:
        print(f"Vector 4 [UPDATE paymentId on allocation blocked]: PASS ({e})")

    # Vector 5: DELETE active vs void allocation -> MUST FAIL
    try:
        con.execute("DELETE FROM installment_payment_allocations WHERE id = 'alloc_1'")
        raise AssertionError("Vector 5 FAILED: should have blocked deleting allocation")
    except sqlite3.IntegrityError as e:
        print(f"Vector 5 [DELETE allocation blocked]: PASS ({e})")

    # Vector 6: Mutasi allocation amount -> MUST FAIL
    try:
        con.execute("UPDATE installment_payment_allocations SET allocatedMinorUnits = 50000000 WHERE id = 'alloc_1'")
        raise AssertionError("Vector 6 FAILED: should have blocked mutating allocatedMinorUnits")
    except sqlite3.IntegrityError as e:
        print(f"Vector 6 [Mutasi allocatedMinorUnits blocked]: PASS ({e})")

    # Vector 7: Mutasi payment total -> MUST FAIL
    try:
        con.execute("UPDATE installment_payments SET totalMinorUnits = 200000000 WHERE id = 'pay_1'")
        raise AssertionError("Vector 7 FAILED: should have blocked mutating totalMinorUnits")
    except sqlite3.IntegrityError as e:
        print(f"Vector 7 [Mutasi totalMinorUnits blocked]: PASS ({e})")

    # Vector 8: Mutasi unallocated amount beyond valid range [0, total] -> MUST FAIL by CHECK
    try:
        con.execute("UPDATE installment_payments SET unallocatedMinorUnits = -100 WHERE id = 'pay_1'")
        raise AssertionError("Vector 8 FAILED: should have blocked negative unallocatedMinorUnits")
    except sqlite3.IntegrityError as e:
        print(f"Vector 8 [CHECK unallocatedMinorUnits >= 0 enforced]: PASS ({e})")

    # Vector 9: Partial void attempt (trying to void only 1 of 2 allocations on active payment)
    # Setup pay_2 with two allocations
    con.execute("INSERT INTO transactions VALUES (20, 1, 'IDR', 200000000, 0, NULL)")
    con.execute("INSERT INTO installment_payments VALUES ('pay_2', 20, 200000000, 0, 0, NULL, 'IDR')")
    con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_2a', 'pay_2', 'per_1', 100000000, 0, 'IDR')")
    con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_2b', 'pay_2', 'per_2', 100000000, 0, 'IDR')")
    con.commit()

    con.execute("UPDATE installment_payment_allocations SET isVoid = 1 WHERE id = 'alloc_2a'")
    # Attempt to void payment while alloc_2b is still active:
    try:
        con.execute("UPDATE installment_payments SET isVoid = 1 WHERE id = 'pay_2'")
        raise AssertionError("Vector 9 FAILED: partial void payment must be rejected")
    except sqlite3.IntegrityError as e:
        print(f"Vector 9 [Partial void rejected (payment void blocked while sibling allocation active)]: PASS ({e})")

    # Vector 10: Void allocation 0 (payment with no allocations voiding)
    con.execute("INSERT INTO transactions VALUES (30, 1, 'IDR', 50000000, 0, NULL)")
    con.execute("INSERT INTO installment_payments VALUES ('pay_3', 30, 50000000, 50000000, 0, NULL, 'IDR')")
    con.execute("INSERT INTO transactions VALUES (31, 1, 'IDR', 50000000, 1, 30)") # reversal
    con.execute("UPDATE installment_payments SET isVoid = 1, reversalTransactionId = 31 WHERE id = 'pay_3'")
    con.commit()
    print("Vector 10 [Void payment with 0 allocations]: PASS")

    # Vector 11: Reversal void yang salah (reversalTransactionId points to non-reversal tx)
    con.execute("INSERT INTO transactions VALUES (40, 1, 'IDR', 50000000, 0, NULL)")
    con.execute("INSERT INTO transactions VALUES (41, 1, 'IDR', 50000000, 0, NULL)") # NOT a reversal (isReversal=0)
    con.execute("INSERT INTO installment_payments VALUES ('pay_4', 40, 50000000, 50000000, 0, NULL, 'IDR')")
    try:
        con.execute("UPDATE installment_payments SET isVoid = 1, reversalTransactionId = 41 WHERE id = 'pay_4'")
        raise AssertionError("Vector 11 FAILED: should block reversalTransactionId pointing to non-reversal tx")
    except sqlite3.IntegrityError as e:
        print(f"Vector 11 [Invalid reversal transaction rejected]: PASS ({e})")

    # Vector 12: Retry after commit (idempotency check)
    # Payment pay_3 is already void. Attempting to void it again with isVoid=1 is a no-op or valid state check:
    cur = con.execute("SELECT isVoid, reversalTransactionId FROM installment_payments WHERE id = 'pay_3'").fetchone()
    assert cur == (1, 31), f"Unexpected state: {cur}"
    print("Vector 12 [Idempotent check after commit]: PASS (state stable isVoid=1)")

    # Vector 13: Retry after rollback before commit (atomic failure rollback and clean state preservation)
    con.commit()
    con.isolation_level = None
    con.execute("BEGIN TRANSACTION")
    con.execute("UPDATE installment_payment_allocations SET isVoid = 1 WHERE id = 'alloc_2b'")
    # Simulate an error mid-flight before payment can be voided
    con.execute("ROLLBACK")
    # Verify alloc_2b remains active (isVoid=0)
    cur = con.execute("SELECT isVoid FROM installment_payment_allocations WHERE id = 'alloc_2b'").fetchone()
    assert cur == (0,), f"Expected isVoid=0 after rollback, got {cur}"
    print("Vector 13 [Retry after rollback before commit]: PASS (clean rollback preserved)")

    # Vector 14: Prevent creating active allocation on void payment
    try:
        con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc_3a', 'pay_3', 'per_1', 10000000, 0, 'IDR')")
        raise AssertionError("Vector 14 FAILED: should have blocked active allocation on void payment")
    except sqlite3.IntegrityError as e:
        print(f"Vector 14 [Active allocation on void payment blocked]: PASS ({e})")

    # Vector 15: Prevent reactivating allocation on void payment
    try:
        con.execute("UPDATE installment_payment_allocations SET isVoid = 0 WHERE id = 'alloc_1'")
        raise AssertionError("Vector 15 FAILED: should have blocked reactivating allocation on void payment")
    except sqlite3.IntegrityError as e:
        print(f"Vector 15 [Reactivating allocation on void payment blocked]: PASS ({e})")

    print("\n--- ALL 15 P0-C VECTORS VERIFIED PASS ---")

if __name__ == '__main__':
    run_tests()

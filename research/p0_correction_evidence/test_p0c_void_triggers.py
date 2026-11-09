import sqlite3

con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys = ON')

con.execute('''CREATE TABLE installment_payments (
    id TEXT PRIMARY KEY,
    totalMinorUnits INTEGER NOT NULL,
    unallocatedMinorUnits INTEGER NOT NULL,
    isVoid INTEGER NOT NULL DEFAULT 0 CHECK(isVoid IN (0, 1)),
    reversalTransactionId INTEGER DEFAULT NULL
)''')

con.execute('''CREATE TABLE installment_payment_allocations (
    id TEXT PRIMARY KEY,
    paymentId TEXT NOT NULL REFERENCES installment_payments(id),
    allocatedMinorUnits INTEGER NOT NULL,
    isVoid INTEGER NOT NULL DEFAULT 0 CHECK(isVoid IN (0, 1))
)''')

# Trigger 1: Prevent marking payment as void if any child allocation is still active (isVoid = 0)
con.execute('''CREATE TRIGGER trg_verify_allocations_void_on_payment_void
BEFORE UPDATE OF isVoid ON installment_payments
WHEN NEW.isVoid = 1
BEGIN
    SELECT RAISE(ABORT, 'Cannot void payment: active non-void child allocations exist')
    FROM installment_payment_allocations
    WHERE paymentId = NEW.id AND isVoid = 0;
END;''')

# Trigger 2: Prevent creating active allocation on void payment
con.execute('''CREATE TRIGGER trg_no_active_allocation_on_void_payment
BEFORE INSERT ON installment_payment_allocations
WHEN NEW.isVoid = 0
BEGIN
    SELECT RAISE(ABORT, 'Cannot create active allocation for a VOID payment')
    FROM installment_payments
    WHERE id = NEW.paymentId AND isVoid = 1;
END;''')

# Trigger 3: Prevent reactivating allocation on void payment
con.execute('''CREATE TRIGGER trg_no_reactivate_allocation_on_void_payment
BEFORE UPDATE OF isVoid ON installment_payment_allocations
WHEN NEW.isVoid = 0
BEGIN
    SELECT RAISE(ABORT, 'Cannot reactivate allocation for a VOID payment')
    FROM installment_payments
    WHERE id = NEW.paymentId AND isVoid = 1;
END;''')

# Seed test data: payment pay1 with allocation alloc1
con.execute("INSERT INTO installment_payments VALUES ('pay1', 1000000, 0, 0, NULL)")
con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc1', 'pay1', 1000000, 0)")

# Test 1: Try to mark payment as void while alloc1 is active (isVoid=0)
try:
    con.execute("UPDATE installment_payments SET isVoid = 1, reversalTransactionId = 201, unallocatedMinorUnits = totalMinorUnits WHERE id = 'pay1'")
    print("1. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("1. Correctly blocked voiding payment while child allocation active:", e)

# Test 2: Valid void sequence: void child allocation first, then void payment
con.execute("UPDATE installment_payment_allocations SET isVoid = 1 WHERE id = 'alloc1'")
con.execute("UPDATE installment_payments SET isVoid = 1, reversalTransactionId = 201, unallocatedMinorUnits = totalMinorUnits WHERE id = 'pay1'")
print("2. Valid void sequence: SUCCESS!")

# Test 3: Try to insert new active allocation for void payment
try:
    con.execute("INSERT INTO installment_payment_allocations VALUES ('alloc2', 'pay1', 500000, 0)")
    print("3. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("3. Correctly blocked active allocation on void payment:", e)

# Test 4: Try to reactivate alloc1 on void payment
try:
    con.execute("UPDATE installment_payment_allocations SET isVoid = 0 WHERE id = 'alloc1'")
    print("4. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("4. Correctly blocked reactivating allocation on void payment:", e)

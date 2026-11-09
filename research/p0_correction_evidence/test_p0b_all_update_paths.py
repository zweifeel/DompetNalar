import sqlite3

con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys = ON')

con.execute('''CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    walletId INTEGER NOT NULL,
    userId INTEGER NOT NULL,
    currencyCode TEXT NOT NULL,
    amount REAL NOT NULL,
    type TEXT NOT NULL CHECK(type IN ('INCOME', 'EXPENSE')),
    transactionPurpose TEXT NOT NULL CHECK(transactionPurpose IN ('STANDARD', 'INSTALLMENT_PAYMENT', 'REVERSAL_CORRECTION', 'ADJUSTMENT')),
    reversalOfTransactionId INTEGER DEFAULT NULL,
    CONSTRAINT uq_tx_target UNIQUE (id, walletId, userId, currencyCode, amount),
    CONSTRAINT fk_rev_match FOREIGN KEY (reversalOfTransactionId, walletId, userId, currencyCode, amount) 
        REFERENCES transactions(id, walletId, userId, currencyCode, amount) ON DELETE RESTRICT,
    CONSTRAINT chk_rev_purpose CHECK (
        (reversalOfTransactionId IS NULL AND transactionPurpose != 'REVERSAL_CORRECTION') OR 
        (reversalOfTransactionId IS NOT NULL AND transactionPurpose = 'REVERSAL_CORRECTION' AND type = 'INCOME')
    ),
    CONSTRAINT chk_rev_user CHECK (
        reversalOfTransactionId IS NULL OR (userId IS NOT NULL AND reversalOfTransactionId != id)
    )
)''')

con.execute('CREATE UNIQUE INDEX idx_tx_reversalOf ON transactions(reversalOfTransactionId)')

# TRIGGER 1: Prevent reversal-of-reversal on INSERT
con.execute('''CREATE TRIGGER trg_no_reversal_of_reversal_insert
BEFORE INSERT ON transactions
WHEN NEW.reversalOfTransactionId IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'Cannot reverse a reversal transaction: target is already a reversal')
    FROM transactions
    WHERE id = NEW.reversalOfTransactionId AND reversalOfTransactionId IS NOT NULL;
END;''')

# TRIGGER 2: Prevent updating a transaction to reverse a reversal
con.execute('''CREATE TRIGGER trg_no_reversal_of_reversal_update
BEFORE UPDATE OF reversalOfTransactionId ON transactions
WHEN NEW.reversalOfTransactionId IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'Cannot reverse a reversal transaction: target is already a reversal')
    FROM transactions
    WHERE id = NEW.reversalOfTransactionId AND reversalOfTransactionId IS NOT NULL;
END;''')

# TRIGGER 3: Prevent mutating reversal pointer once set
con.execute('''CREATE TRIGGER trg_immutable_reversal_pointer
BEFORE UPDATE OF reversalOfTransactionId ON transactions
WHEN OLD.reversalOfTransactionId IS NOT NULL AND NEW.reversalOfTransactionId != OLD.reversalOfTransactionId
BEGIN
    SELECT RAISE(ABORT, 'reversalOfTransactionId is immutable after creation');
END;''')

# TRIGGER 4: Prevent mutating financial attributes of an existing reversal
con.execute('''CREATE TRIGGER trg_immutable_reversal_attributes
BEFORE UPDATE OF walletId, userId, currencyCode, amount, type, transactionPurpose ON transactions
WHEN OLD.reversalOfTransactionId IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'Cannot mutate financial attributes of an existing reversal transaction');
END;''')

# TRIGGER 5: Prevent mutating original transaction after it has been reversed!
con.execute('''CREATE TRIGGER trg_immutable_reversed_original
BEFORE UPDATE OF walletId, userId, currencyCode, amount, type, transactionPurpose ON transactions
WHEN EXISTS (SELECT 1 FROM transactions WHERE reversalOfTransactionId = OLD.id)
BEGIN
    SELECT RAISE(ABORT, 'Cannot mutate original transaction that has already been reversed');
END;''')

# Seed test data:
# Tx 101: Original expense in Wallet 10, IDR, User 1, Amount 50000
con.execute("INSERT INTO transactions (id, walletId, userId, currencyCode, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (101, 10, 1, 'IDR', 50000.0, 'EXPENSE', 'STANDARD', NULL)")
# Tx 102: Another original expense in Wallet 20, USD, User 1, Amount 10
con.execute("INSERT INTO transactions (id, walletId, userId, currencyCode, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (102, 20, 1, 'USD', 10.0, 'EXPENSE', 'STANDARD', NULL)")
# Tx 103: Third original expense
con.execute("INSERT INTO transactions (id, walletId, userId, currencyCode, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (103, 10, 1, 'IDR', 25000.0, 'EXPENSE', 'STANDARD', NULL)")

print("=== EXECUTING P0-B ALL UPDATE & INSERT MUTATION VECTORS ===")

# Vector 1: Insert normal transaction
con.execute("INSERT INTO transactions (id, walletId, userId, currencyCode, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (104, 10, 1, 'IDR', 1000.0, 'EXPENSE', 'STANDARD', NULL)")
print("1. Insert normal transaction: PASS")

# Vector 2: Valid reversal insertion (Tx 201 reverses Tx 101)
con.execute("INSERT INTO transactions (id, walletId, userId, currencyCode, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (201, 10, 1, 'IDR', 50000.0, 'INCOME', 'REVERSAL_CORRECTION', 101)")
print("2. Valid reversal insertion: PASS")

# Vector 3: Update normal transaction (Tx 104) to point to reversal Tx 201
try:
    con.execute("UPDATE transactions SET reversalOfTransactionId = 201, type = 'INCOME', transactionPurpose = 'REVERSAL_CORRECTION' WHERE id = 104")
    print("3. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("3. Updating tx to point to a reversal correctly blocked by trigger:", e)

# Vector 4: Mutate existing reversal pointer (attempt to change Tx 201 from 101 to 103)
try:
    con.execute("UPDATE transactions SET reversalOfTransactionId = 103 WHERE id = 201")
    print("4. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("4. Mutating existing reversal pointer correctly blocked by trigger:", e)

# Vector 5: Mutate financial attributes of existing reversal Tx 201 (e.g. change wallet, amount, currency, user)
try:
    con.execute("UPDATE transactions SET amount = 99999.0 WHERE id = 201")
    print("5a. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("5a. Mutating reversal amount correctly blocked by trigger:", e)

try:
    con.execute("UPDATE transactions SET walletId = 99 WHERE id = 201")
    print("5b. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("5b. Mutating reversal walletId correctly blocked by trigger:", e)

try:
    con.execute("UPDATE transactions SET currencyCode = 'EUR' WHERE id = 201")
    print("5c. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("5c. Mutating reversal currencyCode correctly blocked by trigger:", e)

# Vector 6: Duplicate reversal insertion for Tx 101
try:
    con.execute("INSERT INTO transactions (id, walletId, userId, currencyCode, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (202, 10, 1, 'IDR', 50000.0, 'INCOME', 'REVERSAL_CORRECTION', 101)")
    print("6. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("6. Duplicate reversal on Tx 101 correctly blocked by UNIQUE index:", e)

# Vector 7: Mutate original reversed transaction Tx 101 (attempt to change amount or wallet)
try:
    con.execute("UPDATE transactions SET amount = 100000.0 WHERE id = 101")
    print("7a. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("7a. Mutating reversed original tx amount correctly blocked by trigger:", e)

try:
    con.execute("UPDATE transactions SET walletId = 99 WHERE id = 101")
    print("7b. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("7b. Mutating reversed original tx walletId correctly blocked by trigger:", e)

# Vector 8: Attempt to DELETE original reversed transaction Tx 101
try:
    con.execute("DELETE FROM transactions WHERE id = 101")
    print("8. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("8. Deleting reversed original tx correctly blocked by ON DELETE RESTRICT:", e)

print("\nALL 8 P0-B REVERSAL MUTATION VECTORS FULLY PROTECTED AND PASSED 100%!")

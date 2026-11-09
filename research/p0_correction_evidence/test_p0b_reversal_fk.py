import sqlite3

con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys = ON')

con.execute('''CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    walletId INTEGER NOT NULL,
    userId INTEGER,
    amount REAL NOT NULL,
    type TEXT NOT NULL CHECK(type IN ('INCOME', 'EXPENSE')),
    transactionPurpose TEXT NOT NULL CHECK(transactionPurpose IN ('STANDARD', 'INSTALLMENT_PAYMENT', 'REVERSAL_CORRECTION', 'ADJUSTMENT')),
    reversalOfTransactionId INTEGER DEFAULT NULL,
    CONSTRAINT uq_tx_target UNIQUE (id, walletId, userId, amount),
    CONSTRAINT fk_rev_match FOREIGN KEY (reversalOfTransactionId, walletId, userId, amount) 
        REFERENCES transactions(id, walletId, userId, amount) ON DELETE RESTRICT,
    CONSTRAINT chk_rev_purpose CHECK (
        (reversalOfTransactionId IS NULL AND transactionPurpose != 'REVERSAL_CORRECTION') OR 
        (reversalOfTransactionId IS NOT NULL AND transactionPurpose = 'REVERSAL_CORRECTION' AND type = 'INCOME')
    ),
    CONSTRAINT chk_rev_user CHECK (
        reversalOfTransactionId IS NULL OR (userId IS NOT NULL AND reversalOfTransactionId != id)
    )
)''')

con.execute('CREATE UNIQUE INDEX idx_tx_reversalOf ON transactions(reversalOfTransactionId)')

# TRIGGER 1: Prevent reversal of a reversal
con.execute('''CREATE TRIGGER trg_no_reversal_of_reversal
BEFORE INSERT ON transactions
WHEN NEW.reversalOfTransactionId IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'Cannot reverse a reversal transaction: target is already a reversal')
    FROM transactions
    WHERE id = NEW.reversalOfTransactionId AND reversalOfTransactionId IS NOT NULL;
END;''')

# TRIGGER 2: Prevent mutating reversal pointer after creation
con.execute('''CREATE TRIGGER trg_immutable_reversal_pointer
BEFORE UPDATE OF reversalOfTransactionId ON transactions
WHEN OLD.reversalOfTransactionId IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'reversalOfTransactionId is immutable after creation');
END;''')

# Seed distinct original transactions for isolated testing:
con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (101, 10, 1, 50000.0, 'EXPENSE', 'STANDARD', NULL)")
con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (102, 10, 1, 50000.0, 'EXPENSE', 'STANDARD', NULL)")
con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (103, 10, 1, 50000.0, 'EXPENSE', 'STANDARD', NULL)")
con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (104, 10, 1, 50000.0, 'EXPENSE', 'STANDARD', NULL)")

# 1. Valid reversal: Tx 201 reverses 101
con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (201, 10, 1, 50000.0, 'INCOME', 'REVERSAL_CORRECTION', 101)")
print("1. Valid reversal: SUCCESS")

# 2. Reversal with WRONG WALLET on unreversed Tx 102 (wallet=20 instead of 10)
try:
    con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (202, 20, 1, 50000.0, 'INCOME', 'REVERSAL_CORRECTION', 102)")
    print("2. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("2. Wrong wallet correctly blocked by composite FK:", e)

# 3. Reversal with WRONG AMOUNT on unreversed Tx 103 (amount=40000 instead of 50000)
try:
    con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (203, 10, 1, 40000.0, 'INCOME', 'REVERSAL_CORRECTION', 103)")
    print("3. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("3. Wrong amount correctly blocked by composite FK:", e)

# 4. Reversal with WRONG USER on unreversed Tx 104 (user=2 instead of 1)
try:
    con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (204, 10, 2, 50000.0, 'INCOME', 'REVERSAL_CORRECTION', 104)")
    print("4. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("4. Wrong user correctly blocked by composite FK:", e)

# 5. Reversal of reversal: try to reverse Tx 201
try:
    con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (205, 10, 1, 50000.0, 'INCOME', 'REVERSAL_CORRECTION', 201)")
    print("5. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("5. Reversal of reversal correctly blocked by trigger:", e)

# 6. Duplicate reversal on Tx 101
try:
    con.execute("INSERT INTO transactions (id, walletId, userId, amount, type, transactionPurpose, reversalOfTransactionId) VALUES (206, 10, 1, 50000.0, 'INCOME', 'REVERSAL_CORRECTION', 101)")
    print("6. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("6. Duplicate reversal correctly blocked by UNIQUE index:", e)

# 7. Update reversal pointer after creation (try to redirect 201 from 101 to 102)
try:
    con.execute("UPDATE transactions SET reversalOfTransactionId = 102 WHERE id = 201")
    print("7. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("7. Mutating reversal pointer correctly blocked by trigger:", e)

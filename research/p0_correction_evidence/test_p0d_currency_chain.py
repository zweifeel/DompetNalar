import sqlite3

con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys = ON')

# 1. Wallets
con.execute('''CREATE TABLE wallets (
    id INTEGER PRIMARY KEY,
    currencyCode TEXT NOT NULL,
    userId INTEGER NOT NULL,
    CONSTRAINT uq_wallet_curr UNIQUE (id, currencyCode, userId)
)''')

# 2. Transactions (links to wallet with currency check!)
con.execute('''CREATE TABLE transactions (
    id INTEGER PRIMARY KEY,
    walletId INTEGER NOT NULL,
    currencyCode TEXT NOT NULL,
    userId INTEGER NOT NULL,
    amount REAL NOT NULL,
    CONSTRAINT uq_tx_curr UNIQUE (id, currencyCode, userId),
    CONSTRAINT fk_tx_wallet FOREIGN KEY (walletId, currencyCode, userId) 
        REFERENCES wallets(id, currencyCode, userId)
)''')

# 3. Installment Plans
con.execute('''CREATE TABLE installment_plans (
    id TEXT PRIMARY KEY,
    currencyCode TEXT NOT NULL,
    userId INTEGER NOT NULL,
    CONSTRAINT uq_plan_curr UNIQUE (id, currencyCode, userId)
)''')

# 4. Installment Payments (links to plan AND transaction with currency check!)
con.execute('''CREATE TABLE installment_payments (
    id TEXT PRIMARY KEY,
    planId TEXT NOT NULL,
    transactionId INTEGER NOT NULL,
    currencyCode TEXT NOT NULL,
    userId INTEGER NOT NULL,
    CONSTRAINT fk_pay_plan FOREIGN KEY (planId, currencyCode, userId) 
        REFERENCES installment_plans(id, currencyCode, userId),
    CONSTRAINT fk_pay_tx FOREIGN KEY (transactionId, currencyCode, userId) 
        REFERENCES transactions(id, currencyCode, userId)
)''')

# Seed test data:
# Wallet 10 is IDR, Wallet 20 is USD
con.execute("INSERT INTO wallets VALUES (10, 'IDR', 1)")
con.execute("INSERT INTO wallets VALUES (20, 'USD', 1)")

# Plan 1 is IDR
con.execute("INSERT INTO installment_plans VALUES ('plan_idr', 'IDR', 1)")

# Tx 101 from Wallet 10 (IDR)
con.execute("INSERT INTO transactions VALUES (101, 10, 'IDR', 1, 50000.0)")
# Tx 102 from Wallet 20 (USD)
con.execute("INSERT INTO transactions VALUES (102, 20, 'USD', 1, 10.0)")

# 1. Try to insert transaction from Wallet 10 (IDR) with mismatched currency (USD)
try:
    con.execute("INSERT INTO transactions VALUES (103, 10, 'USD', 1, 10.0)")
    print("1. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("1. Mismatched tx currency vs wallet blocked by FK:", e)

# 2. Valid payment: IDR plan paid by IDR tx 101 with IDR currency
con.execute("INSERT INTO installment_payments VALUES ('pay_1', 'plan_idr', 101, 'IDR', 1)")
print("2. Valid payment with complete currency chain: SUCCESS!")

# 3. Invalid payment: IDR plan paid by USD tx 102
try:
    con.execute("INSERT INTO installment_payments VALUES ('pay_2', 'plan_idr', 102, 'USD', 1)")
    print("3. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("3. USD payment for IDR plan blocked by fk_pay_plan:", e)

# 4. Invalid payment: USD plan currency spoofed but tx is IDR
try:
    con.execute("INSERT INTO installment_payments VALUES ('pay_3', 'plan_idr', 102, 'IDR', 1)")
    print("4. UNEXPECTED SUCCESS!")
except sqlite3.IntegrityError as e:
    print("4. Spoofed payment currency vs tx blocked by fk_pay_tx:", e)

import sqlite3
import json

con = sqlite3.connect(':memory:')

con.execute('''CREATE TABLE legacy_plans (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    provider TEXT NOT NULL,
    installmentAmount REAL NOT NULL,
    totalTenor INTEGER NOT NULL,
    paidCount INTEGER NOT NULL,
    frequency TEXT NOT NULL,
    startDate TEXT NOT NULL,
    nextDueDate TEXT NOT NULL,
    anchorDay INTEGER NOT NULL,
    totalRemainingPrincipal REAL,
    status TEXT NOT NULL,
    notes TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    lastPaymentDate INTEGER,
    userId INTEGER
)''')

# Fixture 1: Valid Plan
con.execute("""INSERT INTO legacy_plans VALUES (
    'valid-1', 'Cicilan Motor', 'Adira', 1000000.0, 12, 3, 'Bulanan',
    '2026-01-01', '2026-04-01', 1, 9000000.0, 'AKTIF', 'Lancar', 1000, 1500, 1
)""")

# Fixture 2: Invalid Plan (Counter Anomaly: paidCount 36 > totalTenor 12)
con.execute("""INSERT INTO legacy_plans VALUES (
    'invalid-counter', 'Laptop Asus', 'PayLater', 1500000.0, 12, 36, 'Bulanan',
    '2023-01-01', '2023-02-01', 1, NULL, 'AKTIF', 'Data rusak', 1000, NULL, 1
)""")

# Fixture 3: Orphan Plan (userId IS NULL)
con.execute("""INSERT INTO legacy_plans VALUES (
    'orphan-1', 'Cicilan Warisan', 'Kredivo', 500000.0, 6, 1, 'Bulanan',
    '2022-01-01', '2022-02-01', 1, 2500000.0, 'AKTIF', 'Legacy', 500, NULL, NULL
)""")

# Fixture 4: Invalid Dates & Non-Positive Amount
con.execute("""INSERT INTO legacy_plans VALUES (
    'invalid-amount-date', 'Kulkas', 'FIF', 0.0, 10, 0, 'Bulanan',
    'invalid-date', 'corrupt', 1, NULL, 'AKTIF', 'Corrupt', 1000, NULL, 1
)""")

# Target table with Room 11 schema
con.execute('''CREATE TABLE installment_plans_new (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    provider TEXT NOT NULL,
    installmentAmount REAL NOT NULL,
    totalTenor INTEGER NOT NULL,
    paidCount INTEGER NOT NULL,
    frequency TEXT NOT NULL,
    startDate TEXT NOT NULL,
    nextDueDate TEXT NOT NULL,
    anchorDay INTEGER NOT NULL,
    totalRemainingPrincipal REAL,
    status TEXT NOT NULL,
    notes TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    lastPaymentDate INTEGER,
    userId INTEGER,
    currencyCode TEXT,
    legacyPaidCount INTEGER,
    quarantineReason TEXT,
    legacyRawPayload TEXT,
    migrationProvenance TEXT NOT NULL,
    requiresManualReview INTEGER NOT NULL
)''')

# Universal SQL serialization without relying on json_object() (supported on all Android versions API 24+)
migration_sql = """
INSERT INTO installment_plans_new (
    id, name, provider, installmentAmount, totalTenor, paidCount,
    frequency, startDate, nextDueDate, anchorDay, totalRemainingPrincipal,
    status, notes, createdAt, lastPaymentDate, userId,
    currencyCode, legacyPaidCount, quarantineReason, legacyRawPayload,
    migrationProvenance, requiresManualReview
)
SELECT 
    id, name, provider, installmentAmount, totalTenor, paidCount,
    frequency, startDate, nextDueDate, anchorDay, totalRemainingPrincipal,
    CASE 
        WHEN userId IS NULL THEN 'LEGACY_CORRUPTED_QUARANTINE'
        WHEN paidCount < 0 OR totalTenor <= 0 THEN 'LEGACY_CORRUPTED_QUARANTINE'
        WHEN paidCount > totalTenor THEN 'LEGACY_CORRUPTED_QUARANTINE'
        WHEN installmentAmount <= 0.0 THEN 'LEGACY_CORRUPTED_QUARANTINE'
        WHEN status NOT IN ('AKTIF', 'SELESAI', 'DIHENTIKAN') THEN 'LEGACY_CORRUPTED_QUARANTINE'
        WHEN length(startDate) != 10 OR substr(startDate, 5, 1) != '-' OR substr(startDate, 8, 1) != '-' THEN 'LEGACY_CORRUPTED_QUARANTINE'
        WHEN length(nextDueDate) != 10 OR substr(nextDueDate, 5, 1) != '-' OR substr(nextDueDate, 8, 1) != '-' THEN 'LEGACY_CORRUPTED_QUARANTINE'
        ELSE status
    END,
    notes, createdAt, lastPaymentDate, userId,
    NULL, paidCount,
    CASE 
        WHEN userId IS NULL THEN 'ORPHAN_CONTRACT_NO_USER_OWNERSHIP'
        WHEN paidCount < 0 OR totalTenor <= 0 THEN 'NEGATIVE_OR_ZERO_TENOR'
        WHEN paidCount > totalTenor THEN 'PAID_COUNT_EXCEEDS_TENOR'
        WHEN installmentAmount <= 0.0 THEN 'NON_POSITIVE_INSTALLMENT_AMOUNT'
        WHEN status NOT IN ('AKTIF', 'SELESAI', 'DIHENTIKAN') THEN 'UNKNOWN_LEGACY_STATUS_STRING'
        WHEN length(startDate) != 10 OR substr(startDate, 5, 1) != '-' OR substr(startDate, 8, 1) != '-' THEN 'INVALID_START_DATE_FORMAT'
        WHEN length(nextDueDate) != 10 OR substr(nextDueDate, 5, 1) != '-' OR substr(nextDueDate, 8, 1) != '-' THEN 'INVALID_DUE_DATE_FORMAT'
        ELSE NULL
    END,
    CASE 
        WHEN userId IS NULL 
          OR paidCount < 0 OR totalTenor <= 0 
          OR paidCount > totalTenor 
          OR installmentAmount <= 0.0 
          OR status NOT IN ('AKTIF', 'SELESAI', 'DIHENTIKAN')
          OR length(startDate) != 10 OR substr(startDate, 5, 1) != '-' OR substr(startDate, 8, 1) != '-'
          OR length(nextDueDate) != 10 OR substr(nextDueDate, 5, 1) != '-' OR substr(nextDueDate, 8, 1) != '-'
        THEN '{' ||
            '"id":"' || replace(id, '"', '\"') || '",' ||
            '"name":"' || replace(name, '"', '\"') || '",' ||
            '"provider":"' || replace(provider, '"', '\"') || '",' ||
            '"installmentAmount":' || installmentAmount || ',' ||
            '"totalTenor":' || totalTenor || ',' ||
            '"paidCount":' || paidCount || ',' ||
            '"frequency":"' || replace(frequency, '"', '\"') || '",' ||
            '"startDate":"' || replace(startDate, '"', '\"') || '",' ||
            '"nextDueDate":"' || replace(nextDueDate, '"', '\"') || '",' ||
            '"anchorDay":' || anchorDay || ',' ||
            '"totalRemainingPrincipal":' || COALESCE(totalRemainingPrincipal, 'null') || ',' ||
            '"status":"' || replace(status, '"', '\"') || '",' ||
            '"notes":"' || replace(notes, '"', '\"') || '",' ||
            '"createdAt":' || createdAt || ',' ||
            '"lastPaymentDate":' || COALESCE(lastPaymentDate, 'null') || ',' ||
            '"userId":' || COALESCE(userId, 'null') ||
        '}'
        ELSE NULL
    END,
    CASE 
        WHEN userId IS NULL 
          OR paidCount < 0 OR totalTenor <= 0 
          OR paidCount > totalTenor 
          OR installmentAmount <= 0.0 
          OR status NOT IN ('AKTIF', 'SELESAI', 'DIHENTIKAN')
          OR length(startDate) != 10 OR substr(startDate, 5, 1) != '-' OR substr(startDate, 8, 1) != '-'
          OR length(nextDueDate) != 10 OR substr(nextDueDate, 5, 1) != '-' OR substr(nextDueDate, 8, 1) != '-'
        THEN 'V10_LEGACY_QUARANTINED'
        ELSE 'V10_LEGACY_VALID'
    END,
    CASE 
        WHEN userId IS NULL 
          OR paidCount < 0 OR totalTenor <= 0 
          OR paidCount > totalTenor 
          OR installmentAmount <= 0.0 
          OR status NOT IN ('AKTIF', 'SELESAI', 'DIHENTIKAN')
          OR length(startDate) != 10 OR substr(startDate, 5, 1) != '-' OR substr(startDate, 8, 1) != '-'
          OR length(nextDueDate) != 10 OR substr(nextDueDate, 5, 1) != '-' OR substr(nextDueDate, 8, 1) != '-'
        THEN 1
        ELSE 0
    END
FROM legacy_plans;
"""

con.execute(migration_sql)

cur = con.cursor()
cur.execute("SELECT id, status, quarantineReason, migrationProvenance, requiresManualReview, legacyRawPayload FROM installment_plans_new")
rows = cur.fetchall()

print("MIGRATION RESULTS:")
for r in rows:
    print(f"ID: {r[0]}")
    print(f"  Status: {r[1]}")
    print(f"  Reason: {r[2]}")
    print(f"  Provenance: {r[3]}")
    print(f"  Review: {r[4]}")
    if r[5]:
        print(f"  Payload: {r[5]}")
        # Verify valid JSON parsing
        parsed = json.loads(r[5])
        print(f"  JSON Verified: {parsed['id']} (userId={parsed['userId']})")
    else:
        print("  Payload: NULL (Valid row)")
    print()

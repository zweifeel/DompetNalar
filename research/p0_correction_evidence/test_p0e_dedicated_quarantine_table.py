"""
Test P0-E Dedicated Quarantine Table Verification
Proves that corrupted legacy installment plans are isolated into
a dedicated quarantine table with typed raw columns, typeof() storage classes,
quarantine reason codes, and timestamp, keeping Room 11 production tables completely clean.
"""
import sqlite3
import time

def run_tests():
    con = sqlite3.connect(':memory:')
    con.execute('PRAGMA foreign_keys = ON')

    # Legacy schema (Room 10 installment_plans)
    con.execute('''CREATE TABLE legacy_installment_plans (
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

    con.execute('''CREATE TABLE legacy_installment_periods (
        id TEXT PRIMARY KEY,
        planId TEXT NOT NULL REFERENCES legacy_installment_plans(id),
        periodNumber INTEGER NOT NULL,
        amountDue REAL NOT NULL,
        amountPaid REAL NOT NULL DEFAULT 0.0
    )''')

    # Room 11 Production Table
    con.execute('''CREATE TABLE installment_plans (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        provider TEXT NOT NULL,
        totalAmountMinorUnits INTEGER NOT NULL CHECK(totalAmountMinorUnits > 0),
        currencyCode TEXT NOT NULL,
        totalTenor INTEGER NOT NULL CHECK(totalTenor > 0),
        status TEXT NOT NULL CHECK(status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
        startDate TEXT NOT NULL,
        nextDueDate TEXT NOT NULL,
        userId INTEGER NOT NULL,
        createdAt INTEGER NOT NULL
    )''')

    # Dedicated Quarantine Table
    con.execute('''CREATE TABLE legacy_corrupted_installment_plans (
        quarantineId INTEGER PRIMARY KEY AUTOINCREMENT,
        raw_id TEXT,
        raw_name TEXT,
        raw_provider TEXT,
        raw_installmentAmount REAL,
        raw_totalTenor INTEGER,
        raw_paidCount INTEGER,
        raw_frequency TEXT,
        raw_startDate TEXT,
        raw_nextDueDate TEXT,
        raw_anchorDay INTEGER,
        raw_totalRemainingPrincipal REAL,
        raw_status TEXT,
        raw_notes TEXT,
        raw_createdAt INTEGER,
        raw_lastPaymentDate INTEGER,
        raw_userId INTEGER,
        typeof_id TEXT NOT NULL,
        typeof_installmentAmount TEXT NOT NULL,
        typeof_totalTenor TEXT NOT NULL,
        typeof_paidCount TEXT NOT NULL,
        typeof_userId TEXT NOT NULL,
        quarantineReasonCode TEXT NOT NULL,
        detectedAt INTEGER NOT NULL
    )''')

    # Fixture 1: Valid Plan
    con.execute("""INSERT INTO legacy_installment_plans VALUES (
        'plan_valid', 'Motor Honda', 'FIF', 1000000.0, 12, 0, 'MONTHLY',
        '2026-01-01', '2026-02-01', 1, 12000000.0, 'AKTIF', 'Valid plan', 1700000000, NULL, 1
    )""")
    for i in range(1, 13):
        con.execute(f"INSERT INTO legacy_installment_periods VALUES ('per_valid_{i}', 'plan_valid', {i}, 1000000.0, 0.0)")

    # Fixture 2: Counter Anomaly: totalTenor=12 but only 5 periods exist
    con.execute("""INSERT INTO legacy_installment_plans VALUES (
        'plan_counter_anomaly', 'Laptop', 'PayLater', 1500000.0, 12, 0, 'MONTHLY',
        '2026-01-01', '2026-02-01', 1, 18000000.0, 'AKTIF', 'Missing periods', 1700000000, NULL, 1
    )""")
    for i in range(1, 6): # Only 5 periods
        con.execute(f"INSERT INTO legacy_installment_periods VALUES ('per_ano_{i}', 'plan_counter_anomaly', {i}, 1500000.0, 0.0)")

    # Fixture 3: Orphan Plan: 0 periods exist
    con.execute("""INSERT INTO legacy_installment_plans VALUES (
        'plan_orphan', 'Orphaned Phone', 'Kredivo', 500000.0, 6, 0, 'MONTHLY',
        '2026-01-01', '2026-02-01', 1, 3000000.0, 'AKTIF', 'No periods exist', 1700000000, NULL, 1
    )""")

    # Fixture 4: Invalid dates: startDate > nextDueDate / invalid chron
    con.execute("""INSERT INTO legacy_installment_plans VALUES (
        'plan_invalid_dates', 'Furniture', 'HomeCredit', 500000.0, 6, 0, 'MONTHLY',
        '2026-12-01', '2026-01-01', 1, 3000000.0, 'AKTIF', 'StartDate in future of due date', 1700000000, NULL, 1
    )""")
    for i in range(1, 7):
        con.execute(f"INSERT INTO legacy_installment_periods VALUES ('per_date_{i}', 'plan_invalid_dates', {i}, 500000.0, 0.0)")

    # Fixture 5: Invalid amounts: installmentAmount <= 0
    con.execute("""INSERT INTO legacy_installment_plans VALUES (
        'plan_invalid_amount', 'TV LED', 'Akulaku', -50000.0, 6, 0, 'MONTHLY',
        '2026-01-01', '2026-02-01', 1, 0.0, 'AKTIF', 'Negative amount', 1700000000, NULL, 1
    )""")
    for i in range(1, 7):
        con.execute(f"INSERT INTO legacy_installment_periods VALUES ('per_amt_{i}', 'plan_invalid_amount', {i}, -50000.0, 0.0)")

    # Fixture 6: Unparseable status / frequency enum
    con.execute("""INSERT INTO legacy_installment_plans VALUES (
        'plan_unparseable_enum', 'AC Sharp', 'AEON', 400000.0, 6, 0, 'SOMETIMES_LATER',
        '2026-01-01', '2026-02-01', 1, 2400000.0, 'CORRUPTED_STATUS', 'Bad enums', 1700000000, NULL, 1
    )""")
    for i in range(1, 7):
        con.execute(f"INSERT INTO legacy_installment_periods VALUES ('per_enum_{i}', 'plan_unparseable_enum', {i}, 400000.0, 0.0)")

    con.commit()

    print("--- P0-E QUARANTINE PIPELINE MIGRATION ---")
    now_ts = int(time.time())

    # Step 1: Migrate Corrupted Records to Quarantine Table
    quarantine_insert_sql = f"""
    INSERT INTO legacy_corrupted_installment_plans (
        raw_id, raw_name, raw_provider, raw_installmentAmount, raw_totalTenor,
        raw_paidCount, raw_frequency, raw_startDate, raw_nextDueDate, raw_anchorDay,
        raw_totalRemainingPrincipal, raw_status, raw_notes, raw_createdAt,
        raw_lastPaymentDate, raw_userId,
        typeof_id, typeof_installmentAmount, typeof_totalTenor, typeof_paidCount, typeof_userId,
        quarantineReasonCode, detectedAt
    )
    SELECT 
        p.id, p.name, p.provider, p.installmentAmount, p.totalTenor,
        p.paidCount, p.frequency, p.startDate, p.nextDueDate, p.anchorDay,
        p.totalRemainingPrincipal, p.status, p.notes, p.createdAt,
        p.lastPaymentDate, p.userId,
        typeof(p.id), typeof(p.installmentAmount), typeof(p.totalTenor), typeof(p.paidCount), typeof(p.userId),
        CASE 
            WHEN p.userId IS NULL THEN 'ERR_ORPHAN_NO_USER'
            WHEN p.installmentAmount <= 0.0 THEN 'ERR_NON_POSITIVE_AMOUNT'
            WHEN p.totalTenor <= 0 THEN 'ERR_NON_POSITIVE_TENOR'
            WHEN p.startDate > p.nextDueDate THEN 'ERR_INVALID_DATE_CHRONOLOGY'
            WHEN p.status NOT IN ('AKTIF', 'SELESAI', 'DIHENTIKAN') THEN 'ERR_UNPARSEABLE_STATUS_ENUM'
            WHEN p.frequency NOT IN ('MONTHLY', 'WEEKLY', 'Bulanan', 'Mingguan') THEN 'ERR_UNPARSEABLE_FREQUENCY_ENUM'
            WHEN (SELECT count(*) FROM legacy_installment_periods per WHERE per.planId = p.id) = 0 THEN 'ERR_ORPHAN_ZERO_PERIODS'
            WHEN (SELECT count(*) FROM legacy_installment_periods per WHERE per.planId = p.id) != p.totalTenor THEN 'ERR_PERIOD_COUNT_MISMATCH'
            ELSE 'ERR_UNKNOWN'
        END,
        {now_ts}
    FROM legacy_installment_plans p
    WHERE 
        p.userId IS NULL
        OR p.installmentAmount <= 0.0
        OR p.totalTenor <= 0
        OR p.startDate > p.nextDueDate
        OR p.status NOT IN ('AKTIF', 'SELESAI', 'DIHENTIKAN')
        OR p.frequency NOT IN ('MONTHLY', 'WEEKLY', 'Bulanan', 'Mingguan')
        OR (SELECT count(*) FROM legacy_installment_periods per WHERE per.planId = p.id) = 0
        OR (SELECT count(*) FROM legacy_installment_periods per WHERE per.planId = p.id) != p.totalTenor;
    """
    con.execute(quarantine_insert_sql)

    # Step 2: Migrate Valid Records to Production Table
    prod_insert_sql = """
    INSERT INTO installment_plans (
        id, name, provider, totalAmountMinorUnits, currencyCode, totalTenor,
        status, startDate, nextDueDate, userId, createdAt
    )
    SELECT 
        p.id, p.name, p.provider, CAST(ROUND(p.installmentAmount * p.totalTenor * 100) AS INTEGER), 'IDR', p.totalTenor,
        CASE p.status
            WHEN 'AKTIF' THEN 'ACTIVE'
            WHEN 'SELESAI' THEN 'COMPLETED'
            WHEN 'DIHENTIKAN' THEN 'CANCELLED'
        END,
        p.startDate, p.nextDueDate, p.userId, p.createdAt
    FROM legacy_installment_plans p
    WHERE p.id NOT IN (SELECT raw_id FROM legacy_corrupted_installment_plans);
    """
    con.execute(prod_insert_sql)
    con.commit()

    # Verification
    cur = con.cursor()
    cur.execute("SELECT id, name, totalAmountMinorUnits, status FROM installment_plans")
    prod_rows = cur.fetchall()

    cur.execute("SELECT raw_id, quarantineReasonCode, typeof_installmentAmount, typeof_userId FROM legacy_corrupted_installment_plans ORDER BY raw_id")
    quarantine_rows = cur.fetchall()

    print(f"Production Plans Count: {len(prod_rows)}")
    for r in prod_rows:
        print(f"  PROD: {r[0]} | Name: {r[1]} | TotalMinor: {r[2]} | Status: {r[3]}")
    assert len(prod_rows) == 1, f"Expected exactly 1 valid plan in production, got {len(prod_rows)}"
    assert prod_rows[0][0] == 'plan_valid'

    print(f"\nQuarantined Plans Count: {len(quarantine_rows)}")
    for r in quarantine_rows:
        print(f"  QUARANTINE: {r[0]} | Reason: {r[1]} | typeof(amount): {r[2]} | typeof(userId): {r[3]}")
    assert len(quarantine_rows) == 5, f"Expected exactly 5 quarantined plans, got {len(quarantine_rows)}"

    expected_reasons = {
        'plan_counter_anomaly': 'ERR_PERIOD_COUNT_MISMATCH',
        'plan_orphan': 'ERR_ORPHAN_ZERO_PERIODS',
        'plan_invalid_dates': 'ERR_INVALID_DATE_CHRONOLOGY',
        'plan_invalid_amount': 'ERR_NON_POSITIVE_AMOUNT',
        'plan_unparseable_enum': 'ERR_UNPARSEABLE_STATUS_ENUM'
    }

    for r in quarantine_rows:
        plan_id = r[0]
        reason = r[1]
        assert reason == expected_reasons[plan_id], f"Mismatch for {plan_id}: expected {expected_reasons[plan_id]}, got {reason}"

    print("\n--- ALL 6 P0-E FIXTURES VERIFIED PASS ---")

if __name__ == '__main__':
    run_tests()

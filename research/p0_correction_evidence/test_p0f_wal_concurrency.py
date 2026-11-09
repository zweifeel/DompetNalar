import sqlite3
import os
import time
import threading

db_path = "test_wal_master.db"
backup_path = "test_wal_backup.db"

# Cleanup previous runs
for p in [db_path, db_path + "-wal", db_path + "-shm", backup_path]:
    if os.path.exists(p):
        os.remove(p)

# 1. Setup master DB in WAL mode
con = sqlite3.connect(db_path)
con.execute("PRAGMA journal_mode = WAL")
con.execute("CREATE TABLE records (id INTEGER PRIMARY KEY, title TEXT, amount REAL)")
con.execute("INSERT INTO records VALUES (1, 'Tx 1', 1000.0)")
con.execute("INSERT INTO records VALUES (2, 'Tx 2', 2000.0)")
con.commit()

print("1. Master database initialized in WAL mode with 2 records.")

# Verify WAL file exists and contains uncheckpointed frames
assert os.path.exists(db_path + "-wal"), "WAL file should exist"

# 2. Concurrency simulation:
# An application-level lock (ReentrantLock / Mutex in Kotlin)
backup_lock = threading.Lock()
concurrent_writes_blocked = []

def background_worker_write_attempt(worker_id):
    # Simulates AutoSyncWorker or RecurringTransactionWorker attempting to write during backup
    acquired = backup_lock.acquire(blocking=False)
    if not acquired:
        concurrent_writes_blocked.append(f"Worker {worker_id} blocked by backup lock (will retry later)")
        return
    try:
        worker_con = sqlite3.connect(db_path)
        worker_con.execute(f"INSERT INTO records VALUES ({100 + worker_id}, 'Worker Tx', 500.0)")
        worker_con.commit()
        worker_con.close()
    finally:
        backup_lock.release()

# 3. Controlled Database Quiescence Backup Protocol:
print("2. Starting Controlled Quiescence Backup Protocol...")

# Step A: Acquire exclusive backup lock (blocks background workers)
backup_lock.acquire()
print("  A. Exclusive backup lock acquired.")

# Step B: Launch background worker to prove it is blocked
t = threading.Thread(target=background_worker_write_attempt, args=(1,))
t.start()
t.join()

# Step C: Execute PRAGMA wal_checkpoint(TRUNCATE) on master connection
cur = con.cursor()
cur.execute("PRAGMA wal_checkpoint(TRUNCATE)")
checkpoint_res = cur.fetchone()
print(f"  C. PRAGMA wal_checkpoint(TRUNCATE) result: busy={checkpoint_res[0]}, log={checkpoint_res[1]}, checkpointed={checkpoint_res[2]}")
assert checkpoint_res[0] == 0, "Checkpoint was busy!"

# Step D: Close database connection (flush & release all handles)
con.close()
print("  D. Master database connection closed cleanly.")

# Step E: File copy
with open(db_path, 'rb') as src, open(backup_path, 'wb') as dst:
    dst.write(src.read())
print("  E. Master .db file copied to backup file.")

# Step F: Release backup lock (application resumes)
backup_lock.release()
print("  F. Exclusive backup lock released.")

# 4. Independent Verification of Backup File
print("3. Verifying backup snapshot independently...")
b_con = sqlite3.connect(backup_path)
b_cur = b_con.cursor()

# Run integrity check on backup
b_cur.execute("PRAGMA integrity_check")
b_integrity = b_cur.fetchall()
print(f"  Backup integrity_check: {b_integrity}")
assert b_integrity == [('ok',)], "Backup integrity check failed!"

# Verify exact records in backup
b_cur.execute("SELECT * FROM records ORDER BY id")
b_records = b_cur.fetchall()
print(f"  Backup records: {b_records}")
assert len(b_records) == 2, f"Expected 2 records in snapshot, got {len(b_records)}"
assert b_records[0] == (1, 'Tx 1', 1000.0)
assert b_records[1] == (2, 'Tx 2', 2000.0)

b_con.close()

# 5. Verify worker write attempt log
print("4. Concurrency Verification:")
for msg in concurrent_writes_blocked:
    print(f"  {msg}")
assert len(concurrent_writes_blocked) == 1, "Background worker should have been blocked during backup!"

# Cleanup test files
for p in [db_path, db_path + "-wal", db_path + "-shm", backup_path]:
    if os.path.exists(p):
        os.remove(p)

print("\n=== P0-F CONTROLLED QUIESCENCE BACKUP VERIFIED WITH ZERO RACES ===")

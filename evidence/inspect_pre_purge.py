import sqlite3, os

evidence_dir = os.path.abspath('evidence/phase1g8/pre_purge')
db_file = os.path.join(evidence_dir, 'finsmart_database')
conn = sqlite3.connect(f'file:{db_file}?mode=ro', uri=True)
c = conn.cursor()

c.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_master_table'")
tables = [row[0] for row in c.fetchall()]
print('All database tables:', tables)

print('\nDetailed table breakdown for Lukas (id=2):')
for tbl in tables:
    c.execute(f"PRAGMA table_info({tbl})")
    cols = [col[1] for col in c.fetchall()]
    if 'userId' in cols:
        c.execute(f"SELECT COUNT(*) FROM {tbl} WHERE userId = 2")
        cnt = c.fetchone()[0]
        print(f" Table {tbl}: {cnt} records with userId = 2")
    elif tbl == 'users':
        c.execute("SELECT COUNT(*) FROM users WHERE id = 2")
        cnt = c.fetchone()[0]
        print(f" Table users: {cnt} records with id = 2")

print("\nLukas user row:")
c.execute("SELECT * FROM users WHERE id = 2")
print(c.fetchall())

print("\nLukas wallets:")
c.execute("SELECT * FROM wallets WHERE userId = 2")
for w in c.fetchall():
    print(" ", w)

conn.close()

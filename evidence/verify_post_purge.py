import sqlite3, os

pre_dir = os.path.abspath('evidence/phase1g8/pre_purge')
post_dir = os.path.abspath('evidence/phase1g8/post_purge')

pre_db = os.path.join(pre_dir, 'finsmart_database')
post_db = os.path.join(post_dir, 'finsmart_database')

conn_pre = sqlite3.connect(f'file:{pre_db}?mode=ro', uri=True)
c_pre = conn_pre.cursor()

conn_post = sqlite3.connect(f'file:{post_db}?mode=ro', uri=True)
c_post = conn_post.cursor()

print("="*60)
print("1. SQLITE PRAGMA INTEGRITY & FK CHECKS (POST-PURGE)")
print("="*60)
c_post.execute("PRAGMA integrity_check;")
print("PRAGMA integrity_check:", c_post.fetchall())

c_post.execute("PRAGMA foreign_key_check;")
fk_violations = c_post.fetchall()
print("PRAGMA foreign_key_check:", fk_violations)
assert len(fk_violations) == 0, "FK violations found!"

print("\n" + "="*60)
print("2. TARGET IDENTITY VERIFICATION (LUKAS)")
print("="*60)
c_post.execute("SELECT COUNT(*) FROM users WHERE email = 'lunachroc07@gmail.com' OR username = 'Lukas'")
target_user_cnt = c_post.fetchone()[0]
print(f"Users matching Lukas / lunachroc07@gmail.com: {target_user_cnt}")
assert target_user_cnt == 0, "Target Lukas still exists in users table!"

c_post.execute("SELECT COUNT(*) FROM users WHERE id = 2")
target_id_cnt = c_post.fetchone()[0]
print(f"Users matching target id 2: {target_id_cnt}")
assert target_id_cnt == 0, "Target ID 2 still exists in users table!"

print("\n" + "="*60)
print("3. PER-TABLE TARGET REMNANTS & ORPHANS CHECK")
print("="*60)
c_post.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_master_table'")
tables = [row[0] for row in c_post.fetchall()]

for tbl in tables:
    c_post.execute(f"PRAGMA table_info({tbl})")
    cols = [col[1] for col in c_post.fetchall()]
    if 'userId' in cols:
        # Check target id 2 records
        c_post.execute(f"SELECT COUNT(*) FROM {tbl} WHERE userId = 2")
        cnt_target = c_post.fetchone()[0]
        # Check orphan records (userId not in users)
        c_post.execute(f"SELECT COUNT(*) FROM {tbl} WHERE userId NOT IN (SELECT id FROM users)")
        cnt_orphan = c_post.fetchone()[0]
        print(f"Table {tbl:30}: target records (userId=2) = {cnt_target}, orphan records = {cnt_orphan}")
        assert cnt_target == 0, f"Target records found in {tbl}!"
        assert cnt_orphan == 0, f"Orphan records found in {tbl}!"
    elif tbl == 'users':
        print(f"Table {tbl:30}: target records = {target_user_cnt}")

print("\n" + "="*60)
print("4. SURVIVING USERS COMPARISON (PRE vs POST)")
print("="*60)
c_pre.execute("SELECT id, username, email, role, isGuest FROM users WHERE id != 2 ORDER BY id")
surviving_pre = c_pre.fetchall()

c_post.execute("SELECT id, username, email, role, isGuest FROM users ORDER BY id")
surviving_post = c_post.fetchall()

print(f"Pre-purge surviving users count: {len(surviving_pre)}")
print(f"Post-purge surviving users count: {len(surviving_post)}")
assert surviving_pre == surviving_post, "Surviving users modified or missing!"

for u in surviving_post:
    uid = u[0]
    username = u[1]
    email = u[2]
    role = u[3]
    print(f"\nUser: id={uid}, username={username}, email={email}, role={role}")
    for tbl in tables:
        c_post.execute(f"PRAGMA table_info({tbl})")
        cols = [col[1] for col in c_post.fetchall()]
        if 'userId' in cols:
            c_pre.execute(f"SELECT COUNT(*) FROM {tbl} WHERE userId = ?", (uid,))
            cnt_pre = c_pre.fetchone()[0]
            c_post.execute(f"SELECT COUNT(*) FROM {tbl} WHERE userId = ?", (uid,))
            cnt_post = c_post.fetchone()[0]
            print(f"  {tbl:30}: pre={cnt_pre}, post={cnt_post}")
            assert cnt_pre == cnt_post, f"Record count mismatch in {tbl} for user {uid}!"

conn_pre.close()
conn_post.close()
print("\n" + "="*60)
print("ALL POST-PURGE VERIFICATION CHECKS PASSED PERFECTLY!")
print("="*60)

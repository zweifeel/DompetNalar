import sqlite3, os

restart_dir = os.path.abspath('evidence/phase1g8/restart_persistence')
restart_db = os.path.join(restart_dir, 'finsmart_database')

conn = sqlite3.connect(f'file:{restart_db}?mode=ro', uri=True)
c = conn.cursor()

print("="*60)
print("1. RESTART PERSISTENCE: PRAGMA CHECKS")
print("="*60)
c.execute("PRAGMA integrity_check;")
print("PRAGMA integrity_check:", c.fetchall())

c.execute("PRAGMA foreign_key_check;")
fk_violations = c.fetchall()
print("PRAGMA foreign_key_check:", fk_violations)
assert len(fk_violations) == 0, "FK violations on restart!"

print("\n" + "="*60)
print("2. RESTART PERSISTENCE: TARGET ABSENCE")
print("="*60)
c.execute("SELECT COUNT(*) FROM users WHERE email = 'lunachroc07@gmail.com' OR username = 'Lukas'")
cnt_lukas = c.fetchone()[0]
print("Lukas user count:", cnt_lukas)
assert cnt_lukas == 0, "Lukas was recreated!"

c.execute("SELECT COUNT(*) FROM users WHERE id = 2")
cnt_id2 = c.fetchone()[0]
print("User ID 2 count:", cnt_id2)
assert cnt_id2 == 0, "ID 2 was recreated!"

print("\n" + "="*60)
print("3. RESTART PERSISTENCE: SURVIVING USERS CHECK")
print("="*60)
c.execute("SELECT id, username, email, role, isGuest FROM users ORDER BY id")
users = c.fetchall()
print(f"Surviving users count: {len(users)}")
for u in users:
    print(" ", u)
assert len(users) == 2, f"Expected 2 surviving users, found {len(users)}"
assert users[0][0] == 1 and users[0][1] == "Tamu DompetNalar"
assert users[1][0] == 3 and users[1][1] == "GoogleReviewer"

print("\n" + "="*60)
print("RESTART PERSISTENCE FULLY VERIFIED!")
print("="*60)
conn.close()

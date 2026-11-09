import sqlite3

def run_sequence_test():
    con = sqlite3.connect(':memory:')
    con.execute('CREATE TABLE t (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT)')
    
    # Case 1: Insert 1, 2, 3, 4, 5 then delete 5
    con.execute("INSERT INTO t VALUES (1, 'A')")
    con.execute("INSERT INTO t VALUES (2, 'B')")
    con.execute("INSERT INTO t VALUES (3, 'C')")
    con.execute("INSERT INTO t VALUES (4, 'D')")
    con.execute("INSERT INTO t VALUES (5, 'E')")
    con.execute("DELETE FROM t WHERE id = 5")
    
    cur = con.cursor()
    cur.execute("SELECT seq FROM sqlite_sequence WHERE name = 't'")
    prev_seq = cur.fetchone()[0]
    cur.execute("SELECT MAX(id) FROM t")
    max_id = cur.fetchone()[0]
    
    print(f"Before rebuild: prev_seq={prev_seq}, max_id={max_id}")
    assert prev_seq == 5, f"Expected prev_seq=5, got {prev_seq}"
    assert max_id == 4, f"Expected max_id=4, got {max_id}"
    
    # High-water mark calculation:
    target_seq = max(prev_seq if prev_seq is not None else 0, max_id if max_id is not None else 0)
    print(f"Calculated high-water mark target_seq={target_seq}")
    assert target_seq == 5, "Target sequence should be 5 to prevent ID reuse!"
    
    # Table Rebuild Simulation
    con.execute('CREATE TABLE t_new (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT)')
    con.execute('INSERT INTO t_new (id, title) SELECT id, title FROM t')
    con.execute('DROP TABLE t')
    con.execute('ALTER TABLE t_new RENAME TO t')
    
    # Apply High-Water Mark Preservation SQL
    con.execute(f"""
    DELETE FROM sqlite_sequence WHERE name = 't';
    """)
    if target_seq > 0:
        con.execute(f"""
        INSERT INTO sqlite_sequence (name, seq) VALUES ('t', {target_seq});
        """)
    
    cur.execute("SELECT seq FROM sqlite_sequence WHERE name = 't'")
    restored_seq = cur.fetchone()[0]
    print(f"After rebuild: restored_seq={restored_seq}")
    assert restored_seq == 5, f"Expected restored_seq=5, got {restored_seq}"
    
    # Now insert next record: MUST be 6, NOT 5!
    con.execute("INSERT INTO t (title) VALUES ('F')")
    cur.execute("SELECT id, title FROM t WHERE title = 'F'")
    new_record = cur.fetchone()
    print(f"Newly inserted record after sequence restore: id={new_record[0]}, title={new_record[1]}")
    assert new_record[0] == 6, f"CRITICAL FAILURE: Expected id=6, but got {new_record[0]} (reused deleted ID 5!)"
    
    print("SUCCESS: High-water mark strictly preserved, ID 5 was not reused!")

run_sequence_test()

path = 'app/src/test/java/com/example/P0P1FeaturesRegressionTest.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        financeRepo = FinanceRepository(walletDao = db.walletDao(), database = db)
        authViewModel = AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Seed users
        db.userDao().insertUser(User(id = testUser1Id, username = "User One", email = "user1@example.com", role = UserRole.FREE_MEMBER))
        db.userDao().insertUser(User(id = testUser2Id, username = "User Two", email = "user2@example.com", role = UserRole.FREE_MEMBER))

        // Reset Session
        SessionManager.logout()
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
    }'''

replacement = '''    @Before
    fun setUp() = runBlocking {
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        SessionManager.logout()

        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        financeRepo = FinanceRepository(walletDao = db.walletDao(), database = db)
        authViewModel = AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Seed users
        db.userDao().insertUser(User(id = testUser1Id, username = "User One", email = "user1@example.com", role = UserRole.FREE_MEMBER))
        db.userDao().insertUser(User(id = testUser2Id, username = "User Two", email = "user2@example.com", role = UserRole.FREE_MEMBER))
    }'''

text = text.replace('\r\n', '\n')
target = target.replace('\r\n', '\n')
replacement = replacement.replace('\r\n', '\n')

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated P0P1FeaturesRegressionTest successfully')

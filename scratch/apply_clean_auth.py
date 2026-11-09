# Update AuthViewModel.kt without withContext around targetCandidate
path_auth = 'app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt'
with open(path_auth, 'r', encoding='utf-8') as f:
    text_auth = f.read()

target_auth = '''        // Initialize canonical SessionManager and restore session or enter guest mode
        SessionManager.initialize(application)
        viewModelScope.launch {
            // Phase 1G.5: P0 Controlled maintenance entry point for developer account purge.
            // Executed strictly on Dispatchers.IO and completed BEFORE session is restored.
            val targetEmail = "lunachroc07@gmail.com"
            val targetUsername = "Lukas"
            val targetCandidate = withContext(Dispatchers.IO) {
                try {
                    database.userDao().getUserByEmail(targetEmail)
                        ?: database.userDao().getUserByIdentifier(targetUsername)
                } catch (_: Exception) {
                    null
                }
            }

            if (targetCandidate != null) {
                withContext(Dispatchers.IO) {
                    val purgeResult = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(database)
                    if (purgeResult.isFailure) {
                        Log.e("AuthViewModel", "Target developer account maintenance purge encountered an issue: ${purgeResult.exceptionOrNull()?.message}")
                    }
                }
            }'''

replacement_auth = '''        // Initialize canonical SessionManager and restore session or enter guest mode
        SessionManager.initialize(application)
        viewModelScope.launch {
            // Phase 1G.5: P0 Controlled maintenance entry point for developer account purge.
            // Executed strictly on Dispatchers.IO and completed BEFORE session is restored.
            val targetEmail = "lunachroc07@gmail.com"
            val targetUsername = "Lukas"
            val targetCandidate = try {
                database.userDao().getUserByEmail(targetEmail)
                    ?: database.userDao().getUserByIdentifier(targetUsername)
            } catch (_: Exception) {
                null
            }

            if (targetCandidate != null) {
                withContext(Dispatchers.IO) {
                    val purgeResult = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(database)
                    if (purgeResult.isFailure) {
                        Log.e("AuthViewModel", "Target developer account maintenance purge encountered an issue: ${purgeResult.exceptionOrNull()?.message}")
                    }
                }
            }'''

assert target_auth in text_auth, 'target_auth not found'
text_auth = text_auth.replace(target_auth, replacement_auth, 1)
with open(path_auth, 'w', encoding='utf-8') as f:
    f.write(text_auth)
print('AuthViewModel.kt updated cleanly')

# Revert P0P1FeaturesRegressionTest.kt setUp to original
path_test = 'app/src/test/java/com/example/P0P1FeaturesRegressionTest.kt'
with open(path_test, 'r', encoding='utf-8') as f:
    text_test = f.read()

target_test = '''    @Before
    fun setUp(): Unit = runBlocking {
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

replacement_test = '''    @Before
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

text_test = text_test.replace('\r\n', '\n')
target_test = target_test.replace('\r\n', '\n')
replacement_test = replacement_test.replace('\r\n', '\n')

assert target_test in text_test, 'target_test not found'
text_test = text_test.replace(target_test, replacement_test, 1)
with open(path_test, 'w', encoding='utf-8') as f:
    f.write(text_test)
print('P0P1FeaturesRegressionTest.kt restored cleanly')

path = 'app/src/test/java/com/example/P0P1FeaturesRegressionTest.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '''    @Before
    fun setUp() = runBlocking {'''

replacement = '''    @Before
    fun setUp(): Unit = runBlocking {'''

assert target in text, 'target not found'
new_text = text.replace(target, replacement, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Added : Unit to setUp')

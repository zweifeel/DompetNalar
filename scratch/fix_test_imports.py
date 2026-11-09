path_test = 'app/src/test/java/com/example/Phase1gUiLocalizationAndAccountRemovalTest.kt'
with open(path_test, 'r', encoding='utf-8') as f:
    text_test = f.read()

target = '''import com.example.data.model.FeedbackItem'''

replacement = '''import com.example.data.model.BackupDataPayload
import com.example.data.model.FeedbackItem
import com.example.data.session.SessionManager'''

assert target in text_test, 'target not found'
new_text = text_test.replace(target, replacement, 1)

with open(path_test, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Added missing imports to Phase1gUiLocalizationAndAccountRemovalTest.kt')

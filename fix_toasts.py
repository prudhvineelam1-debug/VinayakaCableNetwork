import os

strings_en = """
    <!-- Final Toasts -->
    <string name="saved_in_downloads">Saved in Downloads</string>
    <string name="csv_saved_in_downloads">CSV Saved in Downloads</string>
    <string name="csv_error_prefix">CSV Error: %1$s</string>
"""

strings_te = """
    <!-- Final Toasts -->
    <string name="saved_in_downloads">డౌన్‌లోడ్స్‌లో సేవ్ చేయబడింది</string>
    <string name="csv_saved_in_downloads">సిఎస్‌వి డౌన్‌లోడ్స్‌లో సేవ్ చేయబడింది</string>
    <string name="csv_error_prefix">సిఎస్‌వి లోపం: %1$s</string>
"""

def append_strings(filepath, strings):
    with open(filepath, 'r') as f:
        content = f.read()
    content = content.replace("</resources>", f"{strings}\n</resources>")
    with open(filepath, 'w') as f:
        f.write(content)

append_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values/strings.xml', strings_en)
append_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values-te/strings.xml', strings_te)

replacements_kotlin = {
    'ReceiptActivity.kt': [
        ('Toast.makeText(this, "Error sharing receipt: ${e.message}", Toast.LENGTH_LONG).show()', 'Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()')
    ],
    'CustomerListActivity.kt': [
        ('Toast.makeText(this, "Error: $msg", Toast.LENGTH_LONG).show()', 'Toast.makeText(this, getString(R.string.error_prefix, msg), Toast.LENGTH_LONG).show()'),
        ('Toast.makeText(this, "Saved in Downloads", Toast.LENGTH_LONG).show()', 'Toast.makeText(this, getString(R.string.saved_in_downloads), Toast.LENGTH_LONG).show()'),
        ('Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()', 'Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()'),
        ('Toast.makeText(this, "CSV Saved in Downloads", Toast.LENGTH_LONG).show()', 'Toast.makeText(this, getString(R.string.csv_saved_in_downloads), Toast.LENGTH_LONG).show()'),
        ('Toast.makeText(this, "CSV Error: ${e.message}", Toast.LENGTH_LONG).show()', 'Toast.makeText(this, getString(R.string.csv_error_prefix, e.message), Toast.LENGTH_LONG).show()')
    ]
}

src_dir = '/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/java/com/saimega/vinayakacablenetwork'
for file_name, reps in replacements_kotlin.items():
    file_path = os.path.join(src_dir, file_name)
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            content = f.read()
        for old, new in reps:
            content = content.replace(old, new)
        with open(file_path, 'w') as f:
            f.write(content)

print("Remaining Toasts updated.")

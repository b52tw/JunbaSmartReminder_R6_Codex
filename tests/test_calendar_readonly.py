"""Guard the user contract: Junba must never write Google calendar data or remove its notifications."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

class CalendarReadonlyTest(unittest.TestCase):
    def test_manifest_allows_calendar_reads_but_not_writes(self):
        ns = '{http://schemas.android.com/apk/res/android}'
        names = {node.get(ns+'name') for node in ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().findall('uses-permission')}
        self.assertIn('android.permission.READ_CALENDAR', names)
        self.assertNotIn('android.permission.WRITE_CALENDAR', names)
        self.assertNotIn('android.permission.INTERNET', names)

    def test_source_calendar_has_no_mutation_entry_points(self):
        for path in (ROOT/'app/src/main/java/tw/junba/reminder').glob('*.java'):
            code = path.read_text()
            self.assertNotRegex(code, r'getContentResolver\(\)\s*\.\s*(insert|update|delete|bulkInsert|applyBatch)\s*\(', path.name)
            self.assertNotRegex(code, r'Intent\s*\.\s*ACTION_(INSERT|EDIT|DELETE)', path.name)

    def test_google_notifications_are_never_removed_by_listener(self):
        code = (ROOT/'app/src/main/java/tw/junba/reminder/CalendarNotificationListener.java').read_text()
        self.assertNotRegex(code, r'\bcancelNotifications?\s*\(')
        self.assertNotRegex(code, r'\bsnoozeNotification\s*\(')

if __name__ == '__main__':
    unittest.main()

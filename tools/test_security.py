#!/usr/bin/env python3
"""Offline source/configuration regression checks, not an Android runtime pentest."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/pl/sergeyst/gdanskmonitor"
RES = ROOT / "app/src/main/res"


class SecurityRegressionTest(unittest.TestCase):
    def test_backup_and_transfer_exclude_private_storage(self):
        android = "{http://schemas.android.com/apk/res/android}"
        app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
        self.assertEqual("false", app.get(android + "allowBackup"))
        self.assertEqual("@xml/data_extraction_rules", app.get(android + "dataExtractionRules"))
        scopes = ET.parse(RES / "xml/data_extraction_rules.xml").getroot()
        for scope in ("cloud-backup", "device-transfer"):
            excluded = {e.get("domain") for e in scopes.find(scope).findall("exclude") if e.get("path") == "."}
            self.assertTrue({"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"} <= excluded)

    def test_webview_defence_in_depth(self):
        source = (JAVA / "PortalWebSession.java").read_text()
        for policy in ("s.setAllowFileAccess(false)", "s.setAllowContentAccess(false)", "s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)", "s.setSafeBrowsingEnabled(true)", "cm.setAcceptThirdPartyCookies(webView, false)", "workers.setServiceWorkerClient", "return blockedResource(request)", "ssl.cancel()"):
            self.assertIn(policy, source)
        self.assertNotIn("ssl.proceed()", source)
        self.assertNotIn("addJavascriptInterface", source)

    def test_cleanup_holds_lock_until_cookie_callback(self):
        source = (JAVA / "PortalWebSession.java").read_text().split("private void cleanup(", 1)[1]
        self.assertIn("WebStorage.getInstance().deleteAllData()", source)
        self.assertIn("webView.clearCache(true)", source)
        self.assertLess(source.index("removeAllCookies(removed ->"), source.index("PORTAL_BUSY.set(false)"))

    def test_storage_does_not_silently_replace_failed_decryption(self):
        source = (JAVA / "SecureStore.java").read_text()
        catch = source.split("catch (Exception e)", 1)[1]
        self.assertIn("throw new StorageException()", catch)
        self.assertNotIn("return fallback", catch)
        self.assertIn("key(false)", source)
        self.assertIn("if (prefs.contains(name)) getEncrypted(name, null)", source)
        repo = (JAVA / "Repository.java").read_text()
        self.assertIn("public void verifyReadable()", repo)
        self.assertEqual(2, repo.count("verifyReadable();"))

    def test_notifications_do_not_contain_identity_or_case_text(self):
        source = (JAVA / "MonitorWorker.java").read_text().split("private static void notifyChange", 1)[1]
        self.assertIn("R.string.private_change", source)
        self.assertIn("PendingIntent.FLAG_IMMUTABLE", source)
        for sensitive in ("a.name", "a.login", "a.password", "BigTextStyle", "summary"):
            self.assertNotIn(sensitive, source)

    def test_screen_protection_covers_dialogs(self):
        source = (JAVA / "MainActivity.java").read_text()
        self.assertIn("private void showSecure(AlertDialog dialog)", source)
        self.assertIn("dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)", source)
        self.assertIn('getBool("protect_screen", false)', source)

    def test_stopped_worker_closes_web_session(self):
        source = (JAVA / "MonitorWorker.java").read_text()
        self.assertIn("@Override public void onStopped()", source)
        self.assertIn("if (isStopped())", source)
        self.assertIn("holder[0].destroy()", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)

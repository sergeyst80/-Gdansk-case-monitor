#!/usr/bin/env python3
"""Offline structural checks, not a substitute for an Xcode build or iPhone tests."""
from pathlib import Path
import hashlib
import json
import plistlib
import re
import struct
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "GdanskCaseMonitor"
ANDROID = next((candidate for parent in ROOT.parents for candidate in (parent, parent / "GdanskCaseMonitorAndroid")
                if (candidate / "app/src/main/assets/portal_adapter.js").is_file()), None)
LANGUAGES = ("en", "ru", "pl", "uk", "de", "fr", "es", "pt", "zh", "ja", "ar", "hi")


def strings(language):
    text = (APP / "Resources" / (language + ".lproj") / "Localizable.strings").read_text()
    values = {}
    for line in text.splitlines():
        match = re.fullmatch(r'("(?:[^"\\]|\\.)*") = ("(?:[^"\\]|\\.)*");', line)
        assert match, line
        key, value = map(json.loads, match.groups())
        assert key not in values, key
        values[key] = value
    return values


class ProjectTests(unittest.TestCase):
    def test_localizations_and_format_arguments(self):
        english = strings("en")
        for language in LANGUAGES:
            values = strings(language)
            self.assertEqual(set(english), set(values), language)
            for key, value in values.items():
                self.assertTrue(value.strip())
                self.assertEqual(re.findall(r"%(?:lld|@)", english[key]), re.findall(r"%(?:lld|@)", value), (language, key))

    def test_used_literal_resource_keys(self):
        english = strings("en")
        for source in APP.glob("*.swift"):
            for key in re.findall(r'(?:text|message|phase|progress)\("([a-z_]+)"', source.read_text()):
                self.assertIn(key, english, (source.name, key))

    def test_portal_adapter_and_ca_match_android(self):
        if ANDROID is None:
            self.skipTest("Standalone iOS checkout: Android sources not present for parity check")
        for name, source in (("portal_adapter.js", ANDROID / "app/src/main/assets/portal_adapter.js"),
                             ("certum_dv_tls_g2_r39.pem", ANDROID / "app/src/main/res/raw/certum_dv_tls_g2_r39.pem")):
            self.assertEqual(hashlib.sha256(source.read_bytes()).digest(), hashlib.sha256((APP / "Resources" / name).read_bytes()).digest())

    def test_plists_and_scheme(self):
        info = plistlib.loads((APP / "Info.plist").read_bytes())
        self.assertEqual(info["BGTaskSchedulerPermittedIdentifiers"], ["pl.sergeyst.gdanskmonitor.ios.refresh"])
        self.assertEqual(info["UIBackgroundModes"], ["fetch"])
        self.assertNotIn("NSAppTransportSecurity", info)
        privacy = plistlib.loads((APP / "PrivacyInfo.xcprivacy").read_bytes())
        self.assertFalse(privacy["NSPrivacyTracking"])
        scheme = ET.parse(ROOT / "GdanskCaseMonitor.xcodeproj/xcshareddata/xcschemes/GdanskCaseMonitor.xcscheme")
        self.assertEqual(len(scheme.findall(".//TestableReference")), 1)

    def test_project_references(self):
        project = (ROOT / "GdanskCaseMonitor.xcodeproj/project.pbxproj").read_text()
        objects = re.findall(r"^  ([A-F0-9]{24}) =", project, re.M)
        self.assertEqual(len(objects), len(set(objects)))
        refs = set(re.findall(r"\b[A-F0-9]{24}\b", project))
        self.assertEqual(set(objects), refs)
        for source in APP.glob("*.swift"):
            self.assertIn("path = " + source.name + ";", project)
        for language in LANGUAGES:
            self.assertIn(language + ".lproj/Localizable.strings", project)
        self.assertIn("PrivacyInfo.xcprivacy", project)
        self.assertIn("ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon", project)

    def test_icon_is_opaque_and_complete(self):
        icons = APP / "Assets.xcassets/AppIcon.appiconset"
        catalog = json.loads((icons / "Contents.json").read_text())
        data = (icons / catalog["images"][0]["filename"]).read_bytes()
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        width, height, depth, color_type = struct.unpack(">IIBB", data[16:26])
        self.assertEqual((width, height, depth, color_type), (1024, 1024, 8, 2))

    def test_credential_and_trust_policy(self):
        keychain = (APP / "KeychainVault.swift").read_text()
        self.assertIn("kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly", keychain)
        self.assertIn("kSecAttrSynchronizable as String: false", keychain)
        portal = (APP / "PortalSession.swift").read_text()
        self.assertIn(".nonPersistent()", portal)
        self.assertIn("SecTrustEvaluateWithError(trust, nil)", portal)
        self.assertIn("SecPolicyCreateSSL(true, Self.host as CFString)", portal)
        self.assertIn(".cancelAuthenticationChallenge", portal)
        translator = (APP / "PortalTranslator.swift").read_text()
        self.assertNotIn("account.password", translator)
        self.assertIn("where result.fields[index].canTranslate", translator)
        self.assertIn("allowsCellularAccess: false", translator)

    def test_security_hardening(self):
        portal = (APP / "PortalSession.swift").read_text()
        self.assertIn("url.user == nil && url.password == nil", portal)
        self.assertIn("config.userContentController.add(rules)", portal)
        self.assertIn(".reloadIgnoringLocalCacheData", portal)
        self.assertIn("try Task.checkCancellation()", portal)
        keychain = (APP / "KeychainVault.swift").read_text().split("func save(", 1)[1]
        self.assertLess(keychain.index("_ = try load()"), keychain.index("SecItemUpdate"))
        notify = (APP / "MonitorStore.swift").read_text().split("private func notifyChange", 1)[1]
        self.assertIn('text("private_change")', notify)
        self.assertNotIn("account.name", notify)
        self.assertNotIn("snapshot", notify)

    def test_webkit_content_rules_allow_only_portal_tls(self):
        source = (APP / "PortalSession.swift").read_text()
        encoded = re.search(r'private static let resourceRules = #"(.*)"#', source).group(1)
        rules = json.loads(encoded)
        self.assertEqual(rules[0]["action"]["type"], "block")
        allowed = re.compile(rules[1]["trigger"]["url-filter"], re.I)
        for url in ("https://klient.gdansk.uw.gov.pl/", "https://klient.gdansk.uw.gov.pl:443/path", "wss://klient.gdansk.uw.gov.pl/stream"):
            self.assertIsNotNone(allowed.search(url), url)
        for url in ("http://klient.gdansk.uw.gov.pl/", "https://klient.gdansk.uw.gov.pl.evil.test/", "https://user@klient.gdansk.uw.gov.pl/", "https://klient.gdansk.uw.gov.pl:444/", "file:///private/data"):
            self.assertIsNone(allowed.search(url), url)


if __name__ == "__main__":
    unittest.main(verbosity=2)

#!/usr/bin/env python3
"""Offline checks: complete translations, format arguments and packaged locales."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
LANGUAGES = ("en", "ru", "pl", "uk", "de", "fr", "es", "pt", "zh", "ja", "ar", "hi")


def strings(language):
    folder = "values" if language == "en" else "values-" + language
    return {e.attrib["name"]: e.text or "" for e in ET.parse(RES / folder / "strings.xml").getroot()}


class LocalizationTest(unittest.TestCase):
    def test_all_languages_are_complete(self):
        english = strings("en")
        for language in LANGUAGES:
            with self.subTest(language=language):
                values = strings(language)
                expected = set(english) if language == "en" else set(english) - {"app_name"}
                self.assertEqual(expected, set(values))
                self.assertTrue(all(v.strip() for v in values.values()))

    def test_format_arguments_match(self):
        english = strings("en")
        for language in LANGUAGES:
            for key, value in strings(language).items():
                with self.subTest(language=language, key=key):
                    self.assertEqual(re.findall(r"%\d+\$[sd]", english[key]), re.findall(r"%\d+\$[sd]", value))

    def test_english_is_explicit_default(self):
        source = (ROOT / "app/src/main/java/pl/sergeyst/gdanskmonitor/I18n.java").read_text()
        self.assertIn('getPlain("language", "en")', source)
        self.assertIn('config.setLocales(new LocaleList(locale))', source)
        self.assertIn('config.setLayoutDirection(locale)', source)
        self.assertIn('putPlain("language", code)', source)
        for language in LANGUAGES:
            self.assertIn('"' + language + '"', source)

    def test_ui_resources_exist(self):
        english = strings("en")
        for file in (ROOT / "app/src/main/java").rglob("*.java"):
            for name in re.findall(r"R\.string\.(\w+)", file.read_text()):
                self.assertIn(name, english, (file.name, name))

    def test_direction_and_language_picker(self):
        ns = "{http://schemas.android.com/apk/res/android}"
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        self.assertEqual("true", manifest.find("application").get(ns + "supportsRtl"))
        activity = (ROOT / "app/src/main/java/pl/sergeyst/gdanskmonitor/MainActivity.java").read_text()
        self.assertIn("super.attachBaseContext(I18n.context(base))", activity)
        self.assertIn("setSingleChoiceItems(I18n.NAMES", activity)
        self.assertIn("dialog.dismiss(); recreate()", activity)


if __name__ == "__main__":
    unittest.main(verbosity=2)

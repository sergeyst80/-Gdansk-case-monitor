import XCTest
@testable import GdanskCaseMonitor

final class PolicyTests: XCTestCase {
    func testDefaultSettings() {
        let settings = AppSettings()
        XCTAssertEqual(settings.language, "en")
        XCTAssertFalse(settings.autoRefresh)
        XCTAssertFalse(settings.translate)
        XCTAssertEqual(settings.interval, 30)
    }
    func testIntervalsAndLanguages() {
        XCTAssertTrue(AppSettings.intervals.allSatisfy { (15...1440).contains($0) })
        XCTAssertEqual(AppSettings.languages.count, 12)
        XCTAssertEqual(AppSettings.languages.count, AppSettings.languageNames.count)
    }
    func testTranslationExcludesIdentity() {
        for key in ["name", "caseNumber", "filedDate", "login", "password", "unknown", ""] {
            XCTAssertFalse(PortalField(key: key, label: "", value: "Example").canTranslate)
        }
        for key in ["stage", "stageDescription", "notes", "documents"] {
            XCTAssertTrue(PortalField(key: key, label: "", value: "Example").canTranslate)
        }
    }
    func testHashExcludesLabelsButNotValues() {
        let field = PortalField(key: "stage", label: "Original label", value: "Original value")
        let original = Snapshot(fields: [field], lines: [])
        var copy = original
        copy.fields[0].label = "Translated label"
        XCTAssertEqual(original.fingerprint, copy.fingerprint)
        copy.fields[0].value = "Changed value"
        XCTAssertNotEqual(original.fingerprint, copy.fingerprint)
        XCTAssertEqual(original.fields[0].value, "Original value")
    }
    func testLoginMask() {
        XCTAssertEqual(Account(name: "Example", login: "12", password: "Example").maskedLogin, "••••")
        XCTAssertEqual(Account(name: "Example", login: "123456", password: "Example").maskedLogin, "12••••56")
    }
    @MainActor func testPortalHostAllowlist() {
        XCTAssertTrue(PortalSession.trusted(URL(string: "https://klient.gdansk.uw.gov.pl/")!))
        XCTAssertFalse(PortalSession.trusted(URL(string: "http://klient.gdansk.uw.gov.pl/")!))
        XCTAssertFalse(PortalSession.trusted(URL(string: "https://klient.gdansk.uw.gov.pl.example.com/")!))
        XCTAssertFalse(PortalSession.trusted(URL(string: "https://klient.gdansk.uw.gov.pl:444/")!))
        XCTAssertFalse(PortalSession.trusted(URL(string: "https://user@klient.gdansk.uw.gov.pl/")!))
        XCTAssertFalse(PortalSession.trusted(URL(string: "file:///private/data")!))
        XCTAssertFalse(PortalSession.trusted(URL(string: "https://klient.gdansk.uw.gov.pl./")!))
    }
    func testAdapterSnapshotDecoding() throws {
        let data = Data("{\"fields\":[{\"key\":\"stage\",\"label\":\"Stage\",\"value\":\"Example\"}],\"lines\":[],\"loggedIn\":true}".utf8)
        let snapshot = try JSONDecoder().decode(Snapshot.self, from: data)
        XCTAssertEqual(snapshot.fields[0].key, "stage")
    }
}

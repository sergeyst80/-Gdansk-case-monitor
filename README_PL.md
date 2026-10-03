# Gdańsk Case Monitor

[English](README.md) · [Русский](README_RU.md) · [Polski](README_PL.md)

Aplikacja do sprawdzania statusu spraw dla wielu kont w portalu
`https://klient.gdansk.uw.gov.pl/`.

Repozytorium zawiera dwie wersje:

- **Android 8.0+** — projekt źródłowy w katalogu głównym repozytorium oraz
  podpisany plik APK w wersji **1.2.1** w folderze [release/](release/).
- **iPhone / iPad, iOS 17+** — oddzielny projekt źródłowy w folderze [ios/](ios/).
  Przygotowany do budowania na komputerze Mac; kompilacja nie została jeszcze
  potwierdzona, a plik IPA do instalacji nie jest dostępny.

## Android: instalacja

Samodzielna aplikacja na Androida obsługująca wiele kont portalu.

Aktualna wersja: **1.2.1**, wymagany Android 8.0 lub nowszy. Pobierz:
[release/GdanskCaseMonitor-1.2.1.apk](release/GdanskCaseMonitor-1.2.1.apk).
Sumy kontrolne: [release/SHA256SUMS](release/SHA256SUMS).
Zainstaluj aktualizację na poprzedniej wersji release bez odinstalowywania
aplikacji: zachowano klucz podpisu i format zaszyfrowanych danych.

## iPhone / iOS: projekt i budowanie

Folder [ios/](ios/) zawiera projekt Xcode, konfigurację CocoaPods,
tłumaczenie lokalne ML Kit, 12 języków interfejsu, przechowywanie danych
w Keychain, odizolowane sesje WebView, testy i polecenia budowania na Macu.
Wymagane narzędzia, instrukcje budowania i instrukcja użytkownika
(obecnie w języku rosyjskim): [ios/README.md](ios/README.md).

Dziewięć testów strukturalnych przechodzi na Linuksie. Kod Swift nie został
jeszcze skompilowany, testy XCTest nie zostały uruchomione, a podpisany plik
IPA nie jest dostępny. W Actions znajduje się ręcznie uruchamiany workflow
**Check iPhone project (unsigned simulator)** dla macOS. Nie używa kluczy
podpisu Apple ani nie eksportuje pliku IPA. Aby zainstalować aplikację
na iPhonie, wybierz swój zespół podpisujący w Xcode; hasło konta Apple
nie jest przechowywane w projekcie.

Poniższe sekcje opisują instrukcje, funkcje i budowanie **wersji na Androida**.
Szczegóły dotyczące iOS znajdują się w osobnym pliku README tej wersji.

## Android: instrukcje użytkownika

- [Angielska — ilustrowana instrukcja dla wersji 1.2.1](docs/USER_GUIDE_EN.md)
- [Rosyjska — ilustrowana instrukcja dla wersji 1.2.1](docs/USER_GUIDE_RU.md)
- PDF: [angielski](docs/USER_GUIDE_EN.pdf) · [rosyjski](docs/USER_GUIDE_RU.pdf)

Ponowny eksport do PDF wymaga Chromium i `markdown-it` 14.1.0:
`node tools/export_guides.cjs /ścieżka/do/node_modules/markdown-it`.
Eksport odbywa się lokalnie, bez łączenia z portalem i używania sekretów.

## Android: funkcje

- Dodawanie, edytowanie i usuwanie wielu użytkowników.
- Wyświetlanie wszystkich użytkowników i ostatnich danych spraw na jednym ekranie.
- Odświeżanie wszystkich użytkowników lub sprawdzanie wybranego użytkownika.
- Szyfrowanie loginów, haseł i zapisanych danych za pomocą AES-256-GCM;
  klucz jest przechowywany w Android Keystore.
- Czyszczenie plików cookies WebView pomiędzy kontami.
- Okresowe sprawdzanie w tle za pomocą AndroidX WorkManager zamiast
  stale działającej usługi pierwszoplanowej.
- Powiadomienia systemowe o zmianach danych sprawy.
- Sprawdzanie tylko przy dostępnym połączeniu sieciowym.
- CAPTCHA i MFA nie są omijane.

## Android: dlaczego WorkManager

W Androidzie 15+ czas działania usługi pierwszoplanowej typu `dataSync`
jest ograniczony do łącznie sześciu godzin na 24 godziny. Dlatego okresowe
monitorowanie korzysta z WorkManager. Domyślny interwał wynosi około
30 minut; rzeczywisty czas może się zmieniać ze względu na harmonogram
systemu Android i zasady oszczędzania energii.

## Android: budowanie

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- compileSdk: 36
- minSdk: 26
- WorkManager: 2.12.0
- Java: 17

Otwórz katalog główny repozytorium w Android Studio i wybierz
`Build > Build APK(s)`.

Z wiersza poleceń, po zainstalowaniu Android SDK i Gradle:

```bash
gradle :app:assembleDebug
```

Plik wynikowy:

`app/build/outputs/apk/debug/app-debug.apk`

Budowanie pliku APK debug zweryfikowano 2 października 2026 roku przy użyciu
obrazu Docker `ghcr.io/cirruslabs/android-sdk:36` na ARM64. Plik wykonywalny
AAPT2 dla x86-64 uruchamiano przez `qemu-x86_64`, z pakietami
`libc6-amd64-cross` i `libstdc++6-amd64-cross` zainstalowanymi w kontenerze.
Skrypt pośredniczący musi mieć nazwę `aapt2` i być wskazany parametrem
`-Pandroid.aapt2FromMavenOverride=/usr/local/bin/aapt2`.
Podpis APK sprawdzono poleceniem `apksigner verify`; ta weryfikacja
nie obejmowała uruchomienia aplikacji na fizycznym telefonie.

## Android: testy logowania i danych

2 października 2026 roku adapter `app/src/main/assets/portal_adapter.js`
przetestowano na działającym portalu przy użyciu lokalnego Chromium:
logowanie powiodło się i pobrano sześć niepustych pól. Przeszło także
12 testów przeglądarkowych dotyczących HTML/Vaadin, oczekiwania na załadowanie,
odrzucenia logowania, MFA i odczytu pól z Shadow DOM. Są to testy adaptera
w Chromium, a nie testy Android WebView na telefonie.

Lokalny test logowania wykorzystuje `tools/test_portal_login.py`, Chromium,
Python i `websocket-client`. Test odczytuje `.secrets/portal-test.env`,
wysyła dane logowania wyłącznie do hosta HTTPS `klient.gdansk.uw.gov.pl`
i wypisuje tylko etapy przetwarzania oraz liczbę pól. Tymczasowy profil
przeglądarki jest usuwany po zakończeniu testu. Katalog `.secrets/` jest
wykluczony z Git; nie dodawaj go do archiwów przeznaczonych do publikacji.

## Historia zmian

Historia wersji Android i przygotowania projektu iOS znajduje się
w [CHANGELOG.md](CHANGELOG.md) (obecnie w języku rosyjskim).

Projekty pozostałych platform znajdują się oddzielnie w lokalnym katalogu
sąsiednim `../gdansk_case_monitor_apps/`; nie są częścią tego repozytorium.

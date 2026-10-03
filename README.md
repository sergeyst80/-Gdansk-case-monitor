# Gdańsk Case Monitor

Приложение для проверки статуса дел нескольких учетных записей на портале
`https://klient.gdansk.uw.gov.pl/`.

В этом репозитории представлены две версии:

- **Android 8.0+** — исходный проект в корне репозитория и готовый подписанный
  APK версии **1.2.1** в папке [release/](release/).
- **iPhone / iPad, iOS 17+** — отдельный исходный проект в папке [ios/](ios/).
  Подготовлен для сборки на Mac; компиляция ещё не подтверждена, готового IPA нет.

## Android: установка

Самостоятельное Android-приложение для нескольких учетных записей портала.

Актуальный релиз: **1.2.1**, Android 8.0+. Установка:
[release/GdanskCaseMonitor-1.2.1.apk](release/GdanskCaseMonitor-1.2.1.apk).
Контрольные суммы: [release/SHA256SUMS](release/SHA256SUMS).
Устанавливайте поверх прежней release-версии, не удаляя приложение:
ключ подписи и формат зашифрованных данных сохранены.

## iPhone / iOS: проект и сборка

В папке [ios/](ios/) находятся Xcode-проект, CocoaPods, локальный ML Kit-перевод,
12 языков, Keychain, изолированные сессии WebView, тесты и команды сборки на Mac.
Необходимые инструменты, инструкции сборки и руководство:
[ios/README.md](ios/README.md).

На Linux проходят 9 структурных проверок. Swift-код ещё не компилировался,
XCTest не запускался; готового подписанного IPA нет. Для проверки на macOS
в Actions добавлен ручной workflow **Check iPhone project (unsigned simulator)**.
Он не использует Apple-ключи и не выпускает IPA. Для установки на iPhone
выберите свою команду подписи в Xcode; пароль Apple ID не хранится в проекте.

Далее описаны руководства, реализация и сборка **Android-версии**.
Особенности iOS описаны отдельно в её README.

## Android: руководство пользователя / User guide

- [English — illustrated guide for version 1.2.1](docs/USER_GUIDE_EN.md)
- [Русский — иллюстрированное руководство для версии 1.2.1](docs/USER_GUIDE_RU.md)
- PDF: [English](docs/USER_GUIDE_EN.pdf) · [Русский](docs/USER_GUIDE_RU.pdf)

Для повторного экспорта PDF нужны Chromium и `markdown-it` 14.1.0:
`node tools/export_guides.cjs /путь/к/node_modules/markdown-it`.
Экспорт выполняется локально, без обращения к порталу или использования секретов.

## Android: реализовано

- добавление / редактирование / удаление нескольких пользователей;
- все пользователи и последние сведения отображаются на одном экране;
- `Обновить всех` и отдельная проверка выбранного пользователя;
- логины, пароли и сохраненные сведения зашифрованы AES-256-GCM; ключ хранится в Android Keystore;
- cookies WebView очищаются между учетными записями;
- фоновая периодическая проверка через AndroidX WorkManager, а не постоянно работающий foreground service;
- системное уведомление при изменении данных;
- проверка выполняется только при наличии сети;
- CAPTCHA/MFA не обходятся.

## Android: почему WorkManager

Для Android 15+ постоянно работающий `dataSync` foreground service ограничен суммарно 6 часами за 24 часа. Для периодического мониторинга Android рекомендует WorkManager. В проекте используется периодическая работа примерно раз в 30 минут; Android может немного сдвигать фактическое время ради энергосбережения.

## Android: сборка

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- compileSdk: 36
- minSdk: 26
- WorkManager: 2.12.0
- Java: 17

В Android Studio откройте корень проекта и выполните `Build > Build APK(s)`.

Из CLI при установленном Android SDK/Gradle:

```bash
gradle :app:assembleDebug
```

Результат:

`app/build/outputs/apk/debug/app-debug.apk`

Сборка debug APK проверена 2 октября 2026 года в Docker-образе
`ghcr.io/cirruslabs/android-sdk:36` на ARM64. Для x86-64 AAPT2 использовался
`qemu-x86_64` с пакетами `libc6-amd64-cross` и `libstdc++6-amd64-cross` внутри
контейнера. Обёртка должна называться `aapt2` и передаваться через
`-Pandroid.aapt2FromMavenOverride=/usr/local/bin/aapt2`. Подпись APK проверена
через `apksigner verify`; проверка работы на телефоне ещё не выполнялась.

## Android: проверка входа и данных

2 октября 2026 года адаптер `app/src/main/assets/portal_adapter.js` проверен
на действующем портале через локальный Chromium: вход успешен, получены
6 непустых полей. Также прошли 12 браузерных проверок HTML/Vaadin,
ожидания загрузки, отказа входа, MFA и извлечения полей из Shadow DOM.
Это проверка адаптера в Chromium, а не запуск Android WebView на телефоне.

Для локального теста используется `tools/test_portal_login.py`
(Chromium, Python и `websocket-client`). Тест читает
`.secrets/portal-test.env`, отправляет данные только при HTTPS-адресе
`klient.gdansk.uw.gov.pl` и выводит лишь этапы и количество полей.
Временный профиль браузера удаляется после теста. Каталог `.secrets/`
исключён из Git; не включайте его в архивы для публикации.

## История изменений

Версии Android и подготовка iOS описаны в [CHANGELOG.md](CHANGELOG.md).

Проекты других платформ вынесены в соседнюю папку
[gdansk_case_monitor_apps](../gdansk_case_monitor_apps/README.md).

<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин файлового менеджера. Проверка файлов APK, APKS, XAPK, APKM, APKZ и AAB без установки</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Языки

******

Текущий README.md доступен на следующих языках:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- Русский [ru] # текущий
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### Введение

******

APK Inspector предоставляет основное действие для проверки пакетов Android в файловом менеджере. Он анализирует ограниченный приватный снимок приложения и никогда не изменяет и не устанавливает исходный пакет.

******

### Возможности

******

- Регистрирует основное действие Explorer Action v2 для файлов APK, APKS, XAPK, APKM, APKZ и AAB.
- Декодирует текстовые и бинарные manifest APK, protobuf manifest AAB и метаданные bundletool toc.pb.
- Показывает идентификатор пакета, версию, диапазон SDK, запрошенные разрешения, компоненты, подходящие устройству split APK, ресурсы OBB и структурные проблемы.
- Определяет наличие схем подписи APK V1, V2 и V3 без заявления об их криптографической корректности.
- Показывает форматированный Android manifest в отдельном режиме только для чтения.
- Предоставляет отдельный шлюз ACTION_VIEW для специальных MIME типов пакетов Android.

******

### Поддерживаемые форматы

******

Основное действие Explorer точно соответствует следующим расширениям:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### Интерфейс плагина

******

Хост обнаруживает и запускает плагин со следующими идентификаторами:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
```

Версия 1 выполняет только проверку. В ней нет кнопки установки, разрешения на установку, установщика пакетов, редактора исходного файла или перечисления каталогов. Потоки установки хоста остаются независимыми. Если плагин недоступен, хост использует резервное действие.

Требуется сборка хоста 5269 или новее.

******

### Безопасность

******

Защищенный шлюз Explorer проверяет протокол v2, главную файловую поверхность, ID действия, иерархию content URI, точный ClipData, имя файла, расширение, MIME тип, размер и права только на чтение. Публичный шлюз ACTION_VIEW принимает только специальные MIME типы пакетов. Входные данные один раз копируются в ограниченный приватный снимок только для чтения с вычислением SHA-256. Родительский URI из протокола никогда не перечисляется.

******

### Ограничения безопасности

******

- Максимальный размер входных данных: 4 GiB.
- Одно действие принимает один целевой файл и сохраняет не менее 128 MiB свободного места в кеше.
- Количество, имена и объявленные размеры записей, общий размер, сканирование вложенных APK, метаданные, protobuf и вывод manifest ограничены.
- Внешний ACTION_VIEW отклоняет application/zip, application/octet-stream, а также права на запись, постоянный и префиксный доступ.
- V1-V3 проверяются только на наличие. Для V4 нужен отдельный файл idsig, который не входит в этот протокол.
- Плагин никогда не устанавливает пакеты и не запрашивает доступ к хранилищу, сети или установке пакетов.

******

### История версий

******

# v1.0.1

###### 2026/08/08

* `Исправление` Возврат корректной привязки к службе Explorer Action при включении в центре плагинов
* `Улучшение` Более краткие название и описание плагина и более естественная пользовательская документация

# v1.0.0

###### 2026/08/02

* `Функция` Плагин APK Inspector с ID `apk-inspector`, ID действия `inspect-android-package`, движком `explorer-action` и вариантом `default`
* `Функция` Основная проверка только для чтения по протоколу Explorer Action v2 для файлов APK, APKS, XAPK, APKM, APKZ и AAB
* `Функция` Декодирование только для чтения текстовых и бинарных manifest APK, protobuf manifest AAB и метаданных bundletool `toc.pb`
* `Функция` Сведения о пакете, запрошенные разрешения, компоненты, подходящие устройству split APK, ресурсы OBB, структурные находки, форматированный manifest и наличие подписей APK V1-V3
* `Функция` Раздельные защищенный шлюз Explorer и шлюз Android `ACTION_VIEW` с точным MIME, лимит 4 GiB и ограниченный приватный снимок только для чтения с SHA-256
* `Функция` Локализованные метаданные, интерфейс, инструкции, README и журналы на испанском, французском, русском, арабском, японском, корейском, английском, упрощенном китайском, традиционном китайском Гонконга и Тайваня
* `Зависимость` Добавлен Gson версии 2.13.2

##### Другие версии

* [CHANGELOG-ru.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-ru.md)

******

### Сборка

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Релизная сборка:

```powershell
.\gradlew.bat :app:assembleRelease
```

Параметры сборки берутся из version.properties. Текущий минимальный SDK равен 24, а целевой SDK равен 36.

******

### Структура ресурсов

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml локализует метаданные плагина и текст интерфейса. plugin_instruction.md содержит инструкции для пользователя. .python/generate_markdown.py создает локализованные README и журналы изменений из исходных JSON файлов.

******

### Ссылки

******

- Документация AutoJs6: https://docs.autojs6.com
- Безопасный обмен файлами в Android: https://developer.android.com/training/secure-file-sharing

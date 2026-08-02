<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>Glubokaia proverka APK, konteinerov split-paketov i Android App Bundle tolko dlia chteniia v AutoJs6 Explorer</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Iazyki

******

Tekushchii README.md podderzhivaet sleduiushchie iazyki:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Francais [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Espanol [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- Russkii [ru] # tekushchii
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### Vvedenie

******

Plugin AutoJs6 APK Inspector predostavliaet osnovnoe deistvie proverki Android-paketov na glavnoi stranitse failov. On analiziruet ogranichennyi privatnyi snimok i ne izmeniaet i ne ustanavlivaet iskhodnyi fail.

******

### Vozmozhnosti

******

- Registriruet osnovnoe deistvie Explorer Action v2 dlia APK, APKS, XAPK, APKM, APKZ i AAB.
- Dekodiruet tekstovye i binarnye APK Manifest, protobuf Manifest AAB i metadata bundletool toc.pb.
- Pokazyvaet identifikator paketa, versiiu, SDK, razresheniia, komponenty, split dlia ustroistva, OBB i strukturnye problemy.
- Obnaruzhivaet nalichie skhem podpisi APK V1, V2 i V3 bez utverzhdeniia ob ikh kriptograficheskoi korrektnosti.
- Pokazyvaet formatirovannyi Android Manifest v otdelnom rezhime tolko dlia chteniia.
- Predostavliaet otdelnyi shliuz ACTION_VIEW dlia spetsialnykh MIME Android-paketov.

******

### Podderzhivaemye formaty

******

Osnovnoe deistvie Explorer tochno sootvetstvuet etim rasshireniiam:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### Interfeis plugina

******

AutoJs6 nakhodit i zapuskaet plugin so sleduiushchimi identifikatorami:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
supported ABIs: unrestricted (supportedAbis = emptyArray())
```

Versiia 1 tolko proveriaet. Net knopki ili razresheniia ustanovki, ustanovshchika, redaktora iskhodnika i perechisleniia katalogov. Potoki ustanovki khosta otdeleny. Esli plugin nedostupen, AutoJs6 ispolzuet rezervnoe deistvie khosta.

Plugin polnostiu realizovan na JVM i ne soderzhit nativnykh bibliotek. On obiavliaet supportedAbis = emptyArray() i vypuskaetsia kak odin ABI-nezavisimyi APK. Trebuetsia AutoJs6 build 5269 ili novee.

******

### Bezopasnost

******

Zashchishchennyi shliuz Explorer proveriaet protokol v2, glavnuiu poverkhnost failov, ID deistviia, ierarkhiiu content URI, tochnyi ClipData, imia, rasshirenie, MIME, razmer i prava tolko na chtenie. ACTION_VIEW prinimaet tolko spetsialnye MIME. Vkhod kopiruetsia odin raz v ogranichennyi privatnyi snimok tolko dlia chteniia s vychisleniem SHA-256. Roditelskii URI ne perechisliaetsia.

******

### Ogranicheniia bezopasnosti

******

- Maksimalnyi razmer: 4 GiB.
- Odin tselevoi fail na deistvie i ne menee 128 MiB rezerva kesha.
- Kolichestvo, imena i razmery zapisei, vlozhennyi APK, metadata, protobuf i vyvod Manifest ogranicheny.
- Vneshnii ACTION_VIEW otkloniaet application/zip, application/octet-stream i prava zapisi, postoiannye ili prefix.
- V1-V3 proveriaiut tolko nalichie. V4 trebuet otdelnyi idsig vne etogo protokola.
- Plugin nikogda ne ustanavlivaet pakety i ne zaprashivaet prava k khranilishchu, seti ili ustanovke.

******

### Istoriia versii

******

# v1.0.0

###### 2026/08/02

* `Функция` Плагин APK Inspector с ID `apk-inspector`, ID действия `inspect-android-package`, движком `explorer-action` и вариантом `default`
* `Функция` Основная проверка только для чтения по протоколу Explorer Action v2 для файлов APK, APKS, XAPK, APKM, APKZ и AAB
* `Функция` Декодирование только для чтения текстовых и бинарных manifest APK, protobuf manifest AAB и метаданных bundletool `toc.pb`
* `Функция` Сведения о пакете, запрошенные разрешения, компоненты, подходящие устройству split APK, ресурсы OBB, структурные находки, форматированный manifest и наличие подписей APK V1-V3
* `Функция` Раздельные защищенный шлюз Explorer и шлюз Android `ACTION_VIEW` с точным MIME, лимит 4 GiB и ограниченный приватный снимок только для чтения с SHA-256
* `Функция` Чистая реализация JVM без нативной библиотеки, неограниченные ABI через `supportedAbis = emptyArray()`, один независимый от ABI APK и требование AutoJs6 build 5269
* `Функция` Локализованные метаданные, интерфейс, инструкции, README и журналы на испанском, французском, русском, арабском, японском, корейском, английском, упрощенном китайском, традиционном китайском Гонконга и Тайваня
* `Зависимость` Добавлен Gson версии 2.13.2

##### Drugie versii

* [CHANGELOG-ru.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-ru.md)

******

### Sborka

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Reliznaia sborka:

```powershell
.\gradlew.bat :app:assembleRelease
```

Parametry berutsia iz version.properties. Minimalnyi SDK 24, tselevoi SDK 36.

******

### Struktura resursov

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml lokalizuet metadata i interfeis. plugin_instruction.md soderzhit instruktsii dlia khosta. .python/generate_markdown.py sozdaet lokalizovannye README i zhurnaly iz JSON.

******

### Ssylki

******

- Dokumentatsiia AutoJs6: https://docs.autojs6.com
- Bezopasnyi obmen failami Android: https://developer.android.com/training/secure-file-sharing

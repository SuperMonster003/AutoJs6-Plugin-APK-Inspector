<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>Inspeccion profunda y de solo lectura para APK, contenedores divididos y Android App Bundles en AutoJs6 Explorer</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Francais [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- Espanol [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Russkii [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### Introduccion

******

El plugin AutoJs6 APK Inspector proporciona la accion principal de inspeccion para paquetes Android en la pagina principal de archivos. Analiza una instantanea privada y limitada sin modificar ni instalar el archivo de origen.

******

### Funciones

******

- Registra una accion principal Explorer Action v2 para APK, APKS, XAPK, APKM, APKZ y AAB.
- Decodifica Manifest APK de texto o binarios, Manifest protobuf AAB y metadatos bundletool toc.pb.
- Muestra identidad, version, SDK, permisos, componentes, splits para el dispositivo, recursos OBB y problemas estructurales.
- Detecta la presencia de esquemas de firma APK V1, V2 y V3 sin afirmar validez criptografica.
- Muestra un Android Manifest formateado en un visor separado de solo lectura.
- Proporciona una puerta ACTION_VIEW separada para tipos MIME dedicados de paquetes Android.

******

### Formatos compatibles

******

La accion principal de Explorer coincide exactamente con estas extensiones:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### Interfaz del plugin

******

AutoJs6 descubre y ejecuta el plugin con las siguientes identidades:

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

La version 1 solo inspecciona. No incluye boton o permiso de instalacion, instalador, editor de origen ni enumeracion de directorios. Los flujos de instalacion del host permanecen separados. Si el plugin no esta disponible, AutoJs6 usa su alternativa del host.

El plugin esta implementado por completo en la JVM y no contiene bibliotecas nativas. Declara supportedAbis = emptyArray() y se publica como un APK independiente de ABI. Requiere AutoJs6 build 5269 o posterior.

******

### Seguridad

******

La puerta Explorer protegida valida el protocolo v2, la superficie principal, ID de accion, jerarquia content URI, ClipData exacto, nombre, extension, MIME, tamano y permisos de solo lectura. ACTION_VIEW acepta solo MIME dedicados. La entrada se copia una vez a una instantanea privada, limitada y de solo lectura mientras se calcula SHA-256. El URI padre nunca se enumera.

******

### Limites de seguridad

******

- Tamano maximo de entrada: 4 GiB.
- Un archivo de destino por accion y al menos 128 MiB de reserva de cache.
- El numero, nombre y tamano de entradas, el analisis APK anidado, metadatos, protobuf y salida Manifest tienen limites.
- ACTION_VIEW externo rechaza application/zip, application/octet-stream y permisos de escritura, persistentes o de prefijo.
- V1-V3 solo comprueban presencia. V4 requiere una entrada idsig separada fuera de este protocolo.
- El plugin nunca instala paquetes ni solicita permisos de almacenamiento, red o instalacion.

******

### Historial de versiones

******

# v1.0.0

###### 2026/08/02

* `Función` Plugin APK Inspector con ID `apk-inspector`, ID de acción `inspect-android-package`, motor `explorer-action` y variante `default`
* `Función` Inspección principal de solo lectura mediante Explorer Action v2 para archivos APK, APKS, XAPK, APKM, APKZ y AAB
* `Función` Decodificación de solo lectura de manifests APK de texto y binarios, manifests protobuf AAB y metadatos bundletool `toc.pb`
* `Función` Detalles del paquete, permisos solicitados, componentes, APK divididos compatibles con el dispositivo, recursos OBB, hallazgos estructurales, manifest formateado y presencia de firmas APK V1-V3
* `Función` Pasarelas separadas para Explorer protegido y Android `ACTION_VIEW` con MIME exacto, límite de 4 GiB y copia privada limitada de solo lectura calculada con SHA-256
* `Función` Implementación JVM pura sin biblioteca nativa, ABI sin restricciones mediante `supportedAbis = emptyArray()`, un APK independiente de ABI y compilación 5269 de AutoJs6 requerida
* `Función` Metadatos, interfaz, instrucciones, README e historiales localizados en español, francés, ruso, árabe, japonés, coreano, inglés, chino simplificado, chino tradicional de Hong Kong y chino tradicional de Taiwán
* `Dependencia` Añadido Gson versión 2.13.2

##### Para mas versiones

* [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

******

### Compilacion

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Compilacion de lanzamiento:

```powershell
.\gradlew.bat :app:assembleRelease
```

Los parametros proceden de version.properties. El SDK minimo es 24 y el SDK objetivo es 36.

******

### Estructura de recursos

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml localiza metadatos e interfaz. plugin_instruction.md contiene instrucciones para el host. .python/generate_markdown.py genera README y registros localizados desde JSON.

******

### Enlaces

******

- Documentacion de AutoJs6: https://docs.autojs6.com
- Uso compartido seguro de archivos en Android: https://developer.android.com/training/secure-file-sharing

<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>Complemento del gestor de archivos. Inspeccionar archivos APK, APKS, XAPK, APKM, APKZ y AAB sin instalarlos</p>

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
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### Introducción

******

APK Inspector proporciona la acción principal de inspección para paquetes Android en el gestor de archivos. Analiza una instantánea privada y limitada sin modificar ni instalar el archivo de origen.

******

### Funciones

******

- Registra una acción principal Explorer Action v2 para APK, APKS, XAPK, APKM, APKZ y AAB.
- Decodifica Manifest APK de texto o binarios, Manifest protobuf AAB y metadatos bundletool toc.pb.
- Muestra identidad, versión, SDK, permisos, componentes, splits para el dispositivo, recursos OBB y problemas estructurales.
- Detecta la presencia de esquemas de firma APK V1, V2 y V3 sin afirmar validez criptográfica.
- Muestra un Android Manifest formateado en un visor separado de solo lectura.
- Proporciona una puerta ACTION_VIEW separada para tipos MIME dedicados de paquetes Android.

******

### Formatos compatibles

******

La acción principal de Explorer coincide exactamente con estas extensiones:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### Interfaz del plugin

******

El host descubre y ejecuta el complemento con las siguientes identidades:

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

La versión 1 solo inspecciona. No incluye botón o permiso de instalación, instalador, editor de origen ni enumeración de directorios. Los flujos de instalación del host permanecen separados. Si el complemento no está disponible, el host usa su acción alternativa.

Se requiere la compilación 5269 o posterior del host.

******

### Seguridad

******

La puerta Explorer protegida valida el protocolo v2, la superficie principal, ID de acción, jerarquía content URI, ClipData exacto, nombre, extensión, MIME, tamaño y permisos de solo lectura. ACTION_VIEW acepta solo MIME dedicados. La entrada se copia una vez a una instantánea privada, limitada y de solo lectura mientras se calcula SHA-256. El URI padre nunca se enumera.

******

### Límites de seguridad

******

- Tamaño máximo de entrada: 4 GiB.
- Un archivo de destino por acción y al menos 128 MiB de reserva de caché.
- El número, nombre y tamaño de entradas, el análisis APK anidado, metadatos, protobuf y salida Manifest tienen límites.
- ACTION_VIEW externo rechaza application/zip, application/octet-stream y permisos de escritura, persistentes o de prefijo.
- V1-V3 solo comprueban presencia. V4 requiere una entrada idsig separada fuera de este protocolo.
- El plugin nunca instala paquetes ni solicita permisos de almacenamiento, red o instalación.

******

### Historial de versiones

******

# v1.0.1

###### 2026/08/08

* `Corrección` Devolver un enlace válido al servicio Explorer Action al activarlo desde el centro de complementos
* `Mejora` Acortar el nombre y la descripción del complemento y hacer más natural la documentación de usuario

# v1.0.0

###### 2026/08/02

* `Función` Plugin APK Inspector con ID `apk-inspector`, ID de acción `inspect-android-package`, motor `explorer-action` y variante `default`
* `Función` Inspección principal de solo lectura mediante Explorer Action v2 para archivos APK, APKS, XAPK, APKM, APKZ y AAB
* `Función` Decodificación de solo lectura de manifests APK de texto y binarios, manifests protobuf AAB y metadatos bundletool `toc.pb`
* `Función` Detalles del paquete, permisos solicitados, componentes, APK divididos compatibles con el dispositivo, recursos OBB, hallazgos estructurales, manifest formateado y presencia de firmas APK V1-V3
* `Función` Pasarelas separadas para Explorer protegido y Android `ACTION_VIEW` con MIME exacto, límite de 4 GiB y copia privada limitada de solo lectura calculada con SHA-256
* `Función` Metadatos, interfaz, instrucciones, README e historiales localizados en español, francés, ruso, árabe, japonés, coreano, inglés, chino simplificado, chino tradicional de Hong Kong y chino tradicional de Taiwán
* `Dependencia` Añadido Gson versión 2.13.2

##### Para más versiones

* [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

******

### Compilación

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Compilación de lanzamiento:

```powershell
.\gradlew.bat :app:assembleRelease
```

Los parámetros proceden de version.properties. El SDK mínimo es 24 y el SDK objetivo es 36.

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

- Documentación de AutoJs6: https://docs.autojs6.com
- Uso compartido seguro de archivos en Android: https://developer.android.com/training/secure-file-sharing

<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Inspecciona paquetes de instalación y su contenido</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

### Idiomas (Languages)

Este README está disponible en los siguientes idiomas:

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

### Presentación

APK Inspector es un plugin complementario del gestor de archivos de AutoJs6. Toca un archivo APK, APKS, XAPK, APKM, APKZ o AAB en el gestor de archivos y se abrirá al instante un informe de inspección que muestra, en una sola pantalla, cómo se llama la aplicación, qué versión es, qué permisos solicita y si puede instalarse en este dispositivo. El paquete nunca se instala y el archivo original nunca se modifica.

El informe tiene cuatro secciones: «Detalles del paquete» muestra el nombre de la aplicación, el icono, el nombre del paquete, la versión, el rango de SDK, la verificación de firmas y el linaje de certificados de firma, el tamaño del archivo y la suma SHA-256; «Componentes» enumera cada APK dividido (split) y cada recurso OBB del paquete, marca las partes que coinciden con este dispositivo (en un AAB, los módulos), agrupa las actividades/alias, servicios, receptores y proveedores declarados en el manifest por cantidad y estado exported explícito, resume las bibliotecas nativas .so por ABI, tamaño sin comprimir y compatibilidad con el dispositivo, y enumera los archivos classes*.dex estándar con sus tamaños sin comprimir; «Permisos solicitados» agrupa los permisos del sistema por nivel de protección y destaca primero los permisos peligrosos de ejecución con una breve explicación; «Hallazgos de seguridad y compatibilidad» resume los problemas estructurales y el veredicto de compatibilidad. El botón «Ver manifest» abre el AndroidManifest completo y formateado.

### Puntos destacados

- Mejora progresiva del diálogo del host: en un AutoJs6 compatible, el diálogo existente de información APK conserva todos sus campos nativos y las acciones de instalación/manifiesto y después añade un resumen de inspección localizado, acotado y vinculado a SHA-256; si el plugin falta, está desactivado, es antiguo, incompatible o falla, el diálogo base no cambia.
- Diseño para texto grande y pantallas compactas: con escalas de fuente de 1,5×/2,0×, el encabezado del informe se apila cuando falta espacio, los títulos compactos permanecen completos, los SHA-256 y nombres de permisos largos se ajustan sin puntos suspensivos y la búsqueda del manifest en horizontal evita la extracción de IME a pantalla completa.
- Apariencia adaptativa: el informe y el visor del manifiesto siguen el modo claro/oscuro del sistema; en Android 12 y versiones posteriores, Material You también deriva la paleta del fondo de pantalla, mientras los colores semánticos y los iconos de las barras del sistema con contraste adaptado mantienen legibles ambas páginas.
- Metadatos del contenedor: lee los metadatos de SAI APKS, XAPK y APKMirror APKM con un límite de 1 MiB y muestra la herramienta/versión del formato, la versión de la app declarada y una entrada de icono existente sin reemplazar los datos del manifest del APK.
- Un toque para inspeccionar: el informe se abre directamente desde el gestor de archivos de AutoJs6, sin instalación, extracción ni acceso a la red.
- Seis formatos: APK estándar, formatos de varios splits (APKS, XAPK, APKM, APKZ) y el formato de distribución de tiendas AAB; APKS cubre las exportaciones de bundletool y de SAI.
- Versión y compatibilidad: muestra el nombre del paquete, el nombre y código de versión y el SDK mínimo/objetivo/máximo, comparados con la versión de Android del dispositivo antes de instalar.
- Transparencia de permisos: agrupa los permisos solicitados por nivel de protección (ejecución/peligrosos, firma/protegidos y normales), destaca primero los de ejecución y añade una explicación de una línea; los niveles no disponibles siguen visibles y etiquetados.
- Análisis de splits: enumera cada entrada APK y cada recurso OBB de un bundle y marca los splits seleccionados para este dispositivo (base, idioma, densidad de pantalla, ABI).
- Simulación de configuración: cambia el idioma, la densidad de pantalla y la ABI dentro del informe de un bundle; el plugin vuelve a ejecutar localmente el mismo selector acotado sobre la instantánea privada y muestra los APK añadidos o eliminados respecto al dispositivo real.
- Configuración y entrega AAB: decodifica de forma acotada BundleConfig.pb y anota los módulos base, feature, asset, ML, AI y SDK con metadatos de instalación, condicionales, bajo demanda, fast-follow, fusión y extracción; una configuración o manifest dañado o demasiado grande solo degrada su propia anotación.
- Alternativa de identidad mediante recursos: cuando Android no puede cargar directamente un AAB o un bundle demasiado grande, resuelve la etiqueta de la aplicación y un icono ráster para el idioma y la densidad actuales desde resources.pb de AAB o resources.arsc de APK, sin extraer todo el APK anidado.
- Visibilidad de componentes del manifest: cuenta actividades/alias, servicios, receptores de difusión y proveedores de contenido en los splits APK seleccionados o módulos AAB examinados, agrupando los valores explícitos de android:exported como exportado, no exportado o sin especificar/resolver.
- Resumen de bibliotecas nativas: agrupa los archivos .so de splits APK seleccionados o módulos AAB por ABI y tamaño sin comprimir, marcando la ABI preferida del dispositivo, las alternativas compatibles y las arquitecturas no compatibles, sin extraer el contenido.
- Resumen DEX: enumera en orden natural los archivos classes*.dex estándar de splits APK seleccionados o módulos AAB, con los tamaños sin comprimir por archivo y total, sin extraer, decodificar ni descompilar su contenido.
- Reutilización del informe: mantén pulsada cualquier fila principal de detalles del paquete para copiar su valor sin etiqueta, o comparte el informe exacto de la pantalla como `text/plain` mediante el panel de Android; el texto permanece en memoria y no crea archivos ni requiere permiso de almacenamiento.
- Verificación de firmas y certificados: verifica criptográficamente los esquemas V2, V3 y V3.1 del APK; informa de la presencia de V1; muestra cada certificado de firma actual y el linaje de rotación verificado, con funciones antiguas/nuevas y huellas SHA-256.
- Verificación de archivos auxiliares V4/V4.1: AutoJs6 deriva únicamente el archivo adyacente exacto `<nombre del APK>.idsig` y concede un descriptor de solo lectura acotado; el plugin verifica los datos firmados, el certificado y la clave pública, el resumen APK V2/V3 correspondiente, la raíz fs-verity, el árbol Merkle incrustado y cualquier firmante de rotación V3.1.
- Manifests legibles: los manifests binarios de APK y protobuf de AAB se decodifican a XML en un visor independiente de solo lectura, con números de línea, resaltado semántico de sintaxis y búsqueda acotada sin distinguir mayúsculas, resaltado de coincidencias y navegación anterior/siguiente.
- Accesibilidad TalkBack y RTL: las secciones del informe exponen semántica de encabezado, cada acción con icono tiene una etiqueta hablada, las filas interactivas personalizadas ofrecen objetivos de 48 dp, se anuncian los resultados dinámicos y la interfaz árabe se refleja según el idioma.
- Comprobación de integridad: el SHA-256 se calcula mientras se lee el archivo, listo para compararlo con las sumas publicadas oficialmente.
- Chequeo estructural: detecta la falta del APK base, splits duplicados o sin dependencias, incoherencias de versión o de paquete, y marca cada hallazgo como bloqueante [!] o informativo [i].

### Cómo usarlo

1. Descarga e instala APK Inspector y actívalo en el centro de plugins de AutoJs6 (se requiere AutoJs6 con código de versión 5277 o superior).
2. Abre el gestor de archivos de AutoJs6 y localiza el paquete que quieras examinar (APK, APKS, XAPK, APKM, APKZ o AAB).
3. Toca el archivo o elige «Inspeccionar paquete Android» en su menú; el informe aparece tras un instante.
4. Recorre el informe de arriba abajo: icono y nombre de la aplicación, detalles del paquete, componentes, permisos solicitados y hallazgos de seguridad y compatibilidad.
5. Para APKS, XAPK, APKM o APKZ, elige el idioma, la densidad de pantalla y la ABI, y aplica la simulación para comparar los APK seleccionados con el dispositivo real.
6. Mantén pulsada una fila de «Detalles del paquete» para copiar su valor o toca «Compartir informe» en la barra para enviar el texto exacto de la pantalla mediante el panel de Android.
7. Toca «Ver manifest» para leer el AndroidManifest completo y pulsa atrás para volver al gestor de archivos.

> Otras aplicaciones también pueden entregar un paquete a APK Inspector mediante «Abrir con» (ACTION_VIEW), siempre que usen una URI content con un tipo MIME de paquete Android dedicado. El plugin es estrictamente de solo lectura y no ofrece ningún punto de instalación.

### Formatos compatibles

La acción principal del gestor de archivos coincide exactamente con estas extensiones (sin distinguir mayúsculas):

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

APKS, XAPK, APKM y APKZ son formatos contenedores que agrupan varios APK divididos; AAB es el formato App Bundle que se envía a las tiendas de aplicaciones: aquí puede inspeccionarse, pero debe convertirse con una herramienta como bundletool antes de poder instalarse.

### Preguntas frecuentes

#### ¿Puede este plugin instalar APKs?

No, y es deliberado. El plugin no solicita permiso de instalación y no tiene ningún botón de instalar; su función es mostrarte el contenido de un paquete antes de instalarlo. La instalación sigue a cargo del instalador del sistema o del flujo propio del anfitrión.

#### ¿Por qué algunos archivos no se pueden inspeccionar?

Motivos habituales: el archivo supera el límite de 8 GiB; la caché del dispositivo tiene poco espacio (deben quedar libres al menos 128 MiB); el bundle supera los límites de análisis en número o tamaño de entradas; otra aplicación modificó el archivo durante la lectura; o el archivo está estructuralmente dañado. El mensaje de error indica el motivo concreto.

#### ¿La detección de firmas demuestra que un paquete es seguro?

No. El plugin verifica criptográficamente la integridad y las pruebas del firmante para V2, V3, V3.1, V4 y V4.1, y muestra las huellas y el linaje de rotación; pero una firma válida solo demuestra que el paquete no ha cambiado desde que ese firmante lo firmó, no que el firmante o la aplicación sean fiables. Compara la huella y el SHA-256 con una fuente oficial.

#### ¿Por qué los archivos AAB indican que «requieren conversión antes de instalarse»?

AAB es un formato de distribución pensado para las tiendas de aplicaciones; un dispositivo Android no puede instalarlo directamente. El plugin decodifica su manifest protobuf y su estructura de módulos, y usa resources.pb para resolver la etiqueta localizada y un icono ráster adaptado a la densidad, pero instalarlo sigue exigiendo convertirlo antes a APK(s) con una herramienta como bundletool.

### Permisos y seguridad

El plugin no solicita permisos de almacenamiento, red ni instalación de paquetes. Solo accede al paquete seleccionado mediante la URI content temporal de solo lectura concedida por el anfitrión; un `.idsig` opcional solo está disponible mediante un descriptor acotado del anfitrión para el archivo exacto `<nombre del APK>.idsig`, sin enumeración del directorio ni rutas adyacentes arbitrarias. Antes de la inspección, ambas entradas se copian a instantáneas de solo lectura en la caché privada de la aplicación (el SHA-256 se calcula al copiar el paquete), y todo el análisis ocurre sobre ellas; `.idsig` está limitado a 40 MiB y las instantáneas caducadas se limpian en un máximo de 24 horas. Las solicitudes del gestor de archivos se validan campo a campo, incluida la versión del protocolo, los identificadores de solicitud y acción, los metadatos del objetivo, la versión del anfitrión, la forma de la URI, el nombre, el tamaño, las concesiones de solo lectura y el Binder de sesión. Las solicitudes «Abrir con» solo se aceptan con tipos MIME de paquete dedicados; se rechazan application/zip, application/octet-stream y cualquier concesión de escritura, persistente o por prefijo.

Para evitar que archivos manipulados agoten los recursos del dispositivo, el análisis está acotado como sigue, y los archivos que superan un límite se rechazan con un mensaje:

- Un archivo puede ocupar como máximo 8 GiB, durante la copia deben quedar libres al menos 128 MiB de caché, y cada acción procesa exactamente un archivo objetivo.
- Se analizan como máximo 262144 entradas de archivo, se examinan como máximo 4096 entradas APK por bundle, y los nombres de entrada se limitan a 4096 caracteres.
- El tamaño declarado de una entrada no puede superar 8 GiB, y el total declarado no puede superar 64 GiB.
- El examen de manifests de APK anidados se limita a 16 GiB, los metadatos del bundle a 4 MiB, y el APK temporal usado para cargar el icono y la etiqueta a 8 GiB.
- AAB BundleConfig.pb se limita a 4 MiB. Las anotaciones de entrega comparten el examen AAB de 512 manifests / 64 MiB y conservan como máximo 128 valores de condición por módulo; los límites y metadatos dañados quedan aislados y señalados.
- La alternativa mediante recursos lee como máximo 64 MiB por tabla y 8 MiB por icono, con un presupuesto compartido de 16 GiB para localizar la tabla y el icono en un APK anidado; un límite, un recurso mal formado o una referencia no resuelta solo desactiva esa alternativa y se etiqueta claramente.
- La clasificación de permisos examina como máximo 2048 solicitudes, muestra hasta 512 nombres seguros y únicos, y limita cada explicación cargada a 240 caracteres; las omisiones y los niveles no disponibles se etiquetan claramente.
- Las estadísticas de componentes examinan como máximo 4096 declaraciones por manifest y 512 manifests de módulos AAB con un presupuesto de entrada compartido de 64 MiB; las omisiones, los valores exported no resueltos y los fallos por manifest se etiquetan claramente.
- Las estadísticas nativas conservan como máximo 32768 entradas .so y muestran 64 directorios ABI. Se leen hasta 4096 APK anidados seleccionados con un presupuesto compartido de 16 GiB y se retienen como máximo 32 MiB de directorio central por APK; los límites y fallos producen resultados parciales claramente etiquetados.
- Las estadísticas DEX cuentan todas las entradas estándar examinadas, pero muestran como máximo 128 rutas en orden natural; comparten el recorrido acotado del directorio central con el resumen de bibliotecas nativas, por lo que el contenido DEX nunca se extrae, decodifica ni descompila.

### Interfaz del plugin

El anfitrión (AutoJs6) descubre e invoca el plugin mediante las siguientes identidades, indicadas aquí para desarrolladores de plugins y de anfitriones:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
host file information capability: v1
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5277
```

La versión actual solo realiza inspección de solo lectura: no hay botón de instalación, permiso de instalación ni instalador de paquetes, el archivo original nunca se modifica y no se enumeran directorios. V4 usa únicamente el candidato `.idsig` exacto derivado por el anfitrión, copia su descriptor acotado de solo lectura a una instantánea privada y cierra la sesión del anfitrión inmediatamente después. En hosts compatibles, la capacidad v1 de información de archivos del host puede añadir al diálogo existente de información APK un resumen localizado y acotado, vinculado al SHA-256 del origen analizado, mientras el informe Activity completo sigue disponible. Si el plugin falta, está desactivado, es antiguo, incompatible o falla, el diálogo base no cambia y el host usa silenciosamente su alternativa normal.

### Roadmap

Las capacidades anteriores y los elementos marcados de la Roadmap reflejan lo implementado; el trabajo previsto, como un análisis más profundo de bundles y AAB, se registra en la Roadmap, y los elementos sin marcar no son capacidades actuales.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### Historial de versiones

#### v1.2.1

_2026/09/19_

- `Corrección` Advertencias de lectura de SDK XML v4 con AGP 9.1 y comprobaciones de alineación nativa de APK activadas por error al ensamblar pruebas unitarias JVM, mediante los plugins de compilación compartidos 1.8.3
- `Mejora` compileSdk y targetSdk suben a 37 (Android 17); el comportamiento del plugin no depende del nuevo objetivo

#### v1.2.0

_2026/09/13_

- `Función` Historial de versiones local desde la interfaz con traducciones y alternativa en inglés
- `Mejora` Comprobación de la firma completa, los APK esperados y la documentación reproducible de cada versión

#### v1.1.1

_2026/09/12_

- `Función` Se añadió una comprobación de preparación para páginas de 16 KB en los archivos APK / AAB inspeccionados directamente: solo se leen la cabecera ELF y la tabla de cabeceras de programa de cada biblioteca nativa de 64 bits (`arm64-v8a` / `x86_64` / `riscv64`, como máximo 64 KiB por entrada) para verificar que cada segmento `PT_LOAD` esté alineado a al menos 16 KB; si el manifiesto declara `extractNativeLibs="false"`, también se verifican los desplazamientos de datos ZIP de las bibliotecas sin comprimir. El veredicto (listo / no listo / sin verificar / sin bibliotecas de 64 bits / no evaluado para APK anidados en un contenedor) aparece en la sección de bibliotecas nativas y en el resumen de información de archivo del anfitrión, y un veredicto de no listo se incluye en los hallazgos

##### Historial completo

- [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

### Compilación

```powershell
.\gradlew.bat :app:assembleDebug
```

Compilación release:

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:prepareReleaseArtifacts
.\gradlew.bat :app:verifyReleaseArtifacts
```

Los parámetros de compilación y firma provienen de version.properties y sign.properties; el mínimo actual es Android 7.0 (SDK 24) con SDK objetivo 37.

`prepareReleaseArtifacts` compila y copia los APK de lanzamiento en releases/, conserva la regla de nombre `autojs6-plugin-apk-inspector-v<versión>-<CRC32>.apk` y genera un archivo `.sha256` por APK junto con un `SHA256SUMS` ordenado; `verifyReleaseArtifacts` comprueba de forma independiente el CRC32 del nombre, el SHA-256 real, los archivos auxiliares y el manifiesto.

Los archivos README y CHANGELOG se generan con .python/generate_markdown.py a partir de las fuentes JSON y plantillas de .readme/ y .changelog/ (10 idiomas). Para cambiar la documentación, edita las fuentes JSON y vuelve a ejecutar el script en lugar de editar el Markdown generado.

### Enlaces

- Documentación de AutoJs6: https://docs.autojs6.com
- Compartición segura de archivos en Android: https://developer.android.com/training/secure-file-sharing


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/docs/16kb.md)

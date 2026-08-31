<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="APK Inspector" width="128" />
  </p>

  <h1>APK Inspector</h1>

  <p>Plugin del gestor de archivos de AutoJs6: toca un archivo APK o AAB para ver su versión, permisos, firmas y compatibilidad con el dispositivo, sin instalarlo</p>

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

Motivos habituales: el archivo supera el límite de 4 GiB; la caché del dispositivo tiene poco espacio (deben quedar libres al menos 128 MiB); el bundle supera los límites de análisis en número o tamaño de entradas; otra aplicación modificó el archivo durante la lectura; o el archivo está estructuralmente dañado. El mensaje de error indica el motivo concreto.

#### ¿La detección de firmas demuestra que un paquete es seguro?

No. El plugin verifica criptográficamente la integridad y las pruebas del firmante para V2, V3, V3.1, V4 y V4.1, y muestra las huellas y el linaje de rotación; pero una firma válida solo demuestra que el paquete no ha cambiado desde que ese firmante lo firmó, no que el firmante o la aplicación sean fiables. Compara la huella y el SHA-256 con una fuente oficial.

#### ¿Por qué los archivos AAB indican que «requieren conversión antes de instalarse»?

AAB es un formato de distribución pensado para las tiendas de aplicaciones; un dispositivo Android no puede instalarlo directamente. El plugin decodifica su manifest protobuf y su estructura de módulos, y usa resources.pb para resolver la etiqueta localizada y un icono ráster adaptado a la densidad, pero instalarlo sigue exigiendo convertirlo antes a APK(s) con una herramienta como bundletool.

### Permisos y seguridad

El plugin no solicita permisos de almacenamiento, red ni instalación de paquetes. Solo accede al paquete seleccionado mediante la URI content temporal de solo lectura concedida por el anfitrión; un `.idsig` opcional solo está disponible mediante un descriptor acotado del anfitrión para el archivo exacto `<nombre del APK>.idsig`, sin enumeración del directorio ni rutas adyacentes arbitrarias. Antes de la inspección, ambas entradas se copian a instantáneas de solo lectura en la caché privada de la aplicación (el SHA-256 se calcula al copiar el paquete), y todo el análisis ocurre sobre ellas; `.idsig` está limitado a 40 MiB y las instantáneas caducadas se limpian en un máximo de 24 horas. Las solicitudes del gestor de archivos se validan campo a campo, incluida la versión del protocolo, los identificadores de solicitud y acción, los metadatos del objetivo, la versión del anfitrión, la forma de la URI, el nombre, el tamaño, las concesiones de solo lectura y el Binder de sesión. Las solicitudes «Abrir con» solo se aceptan con tipos MIME de paquete dedicados; se rechazan application/zip, application/octet-stream y cualquier concesión de escritura, persistente o por prefijo.

Para evitar que archivos manipulados agoten los recursos del dispositivo, el análisis está acotado como sigue, y los archivos que superan un límite se rechazan con un mensaje:

- Un archivo puede ocupar como máximo 4 GiB, durante la copia deben quedar libres al menos 128 MiB de caché, y cada acción procesa exactamente un archivo objetivo.
- Se analizan como máximo 16384 entradas de archivo, se examinan como máximo 512 entradas APK por bundle, y los nombres de entrada se limitan a 1024 caracteres.
- El tamaño declarado de una entrada no puede superar 4 GiB, y el total declarado no puede superar 8 GiB.
- El examen de manifests de APK anidados se limita a 256 MiB, los metadatos del bundle a 1 MiB, y el APK temporal usado para cargar el icono y la etiqueta a 512 MiB.
- AAB BundleConfig.pb se limita a 1 MiB. Las anotaciones de entrega comparten el examen AAB de 128 manifests / 16 MiB y conservan como máximo 128 valores de condición por módulo; los límites y metadatos dañados quedan aislados y señalados.
- La alternativa mediante recursos lee como máximo 32 MiB por tabla y 4 MiB por icono, con un presupuesto compartido de 512 MiB para localizar la tabla y el icono en un APK anidado; un límite, un recurso mal formado o una referencia no resuelta solo desactiva esa alternativa y se etiqueta claramente.
- La clasificación de permisos examina como máximo 2048 solicitudes, muestra hasta 512 nombres seguros y únicos, y limita cada explicación cargada a 240 caracteres; las omisiones y los niveles no disponibles se etiquetan claramente.
- Las estadísticas de componentes examinan como máximo 4096 declaraciones por manifest y 128 manifests de módulos AAB con un presupuesto de entrada compartido de 16 MiB; las omisiones, los valores exported no resueltos y los fallos por manifest se etiquetan claramente.
- Las estadísticas nativas conservan como máximo 4096 entradas .so y muestran 64 directorios ABI. Se leen hasta 512 APK anidados seleccionados con un presupuesto compartido de 256 MiB y se retienen como máximo 8 MiB de directorio central por APK; los límites y fallos producen resultados parciales claramente etiquetados.
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
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5277
```

La versión actual solo realiza inspección de solo lectura: no hay botón de instalación, permiso de instalación ni instalador de paquetes, el archivo original nunca se modifica y no se enumeran directorios. V4 usa únicamente el candidato `.idsig` exacto derivado por el anfitrión, copia su descriptor acotado de solo lectura a una instantánea privada y cierra la sesión del anfitrión inmediatamente después. Si el plugin falta o está desactivado, el anfitrión recurre en silencio a su acción predeterminada.

### Roadmap

Las capacidades anteriores y los elementos marcados de la Roadmap reflejan lo implementado; el trabajo previsto, como un análisis más profundo de bundles y AAB, se registra en la Roadmap, y los elementos sin marcar no son capacidades actuales.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### Historial de versiones

#### v1.1.0

_2026/08/30_

- `Aviso` Requiere AutoJs6 con código de versión 5277 o posterior para el protocolo Explorer Action v22 y el acceso acotado al archivo V4
- `Función` Se añadieron diseños adaptables para fuentes de 1,5×/2,0×, orientación horizontal y pantallas de 320 dp: encabezado apilado cuando falta espacio, títulos compactos completos, ajuste sin elipsis de SHA-256 y permisos largos, y búsqueda del manifest sin extracción IME a pantalla completa
- `Función` Se añadió accesibilidad TalkBack y RTL al informe y al visor de manifest: encabezados semánticos compatibles, acciones con iconos etiquetadas, objetivos personalizados de 48 dp, anuncios de resultados dinámicos, diseño según el idioma y navegación árabe reflejada
- `Función` Se añadieron al visor de manifest de solo lectura números de línea, resaltado XML adaptado al tema Material y búsqueda acotada sin distinguir mayúsculas, con coincidencias resaltadas, navegación anterior/siguiente circular y restauración del estado
- `Función` Se añadieron temas Material 3 claro/oscuro que siguen el sistema para el informe y el visor del manifiesto, colores dinámicos Material You en Android 12 y versiones posteriores e iconos de las barras del sistema adaptados al contraste
- `Función` Se añadieron resúmenes acotados de metadatos del contenedor para SAI APKS `meta.sai_v1/v2.json`, XAPK `manifest.json` y APKMirror APKM `info.json`, que muestran la herramienta/versión del formato, la versión de la app declarada en los metadatos y una entrada de icono existente; los metadatos dañados o mayores de 1 MiB quedan aislados y señalados
- `Función` Se añadió simulación de configuración dentro de los informes APKS, XAPK, APKM y APKZ: al cambiar idioma, densidad de pantalla y ABI, se repite localmente la selección acotada de splits sobre la misma instantánea privada y se muestran el estado compatible/no válido y los APK añadidos o eliminados respecto al dispositivo real; el informe principal sigue basándose en el dispositivo real
- `Función` Se añadieron metadatos acotados de configuración y entrega AAB: se decodifican los ajustes bundletool/type/split/compression/optimization de BundleConfig.pb y se anotan módulos base, feature, asset, ML, AI y SDK con entrega durante la instalación, condicional, bajo demanda, fast-follow, fusión y extracción; los metadatos dañados, demasiado grandes u omitidos quedan aislados y señalados
- `Función` Se añadió una alternativa acotada para la etiqueta y el icono ráster mediante resources.pb de AAB y resources.arsc de APK, resueltos según el idioma y la densidad actuales sin extraer APK anidados demasiado grandes; los fallos de tabla, icono y límite de examen permanecen aislados y claramente etiquetados
- `Función` Se añadió la copia mediante pulsación prolongada de los valores principales del paquete y el envío del informe de texto exacto mediante el panel de Android; el contenido permanece en memoria, no solicita permiso de almacenamiento ni crea archivos
- `Función` Se añadió un resumen DEX acotado que enumera en orden natural los archivos classes*.dex estándar de splits APK seleccionados y módulos AAB, con tamaños sin comprimir por archivo y total, compartiendo el recorrido del directorio central de bibliotecas nativas sin extraer, decodificar ni descompilar el contenido DEX
- `Función` Se añadió un resumen acotado de bibliotecas nativas que agrupa los archivos .so de splits APK seleccionados y módulos AAB por ABI y tamaño sin comprimir, marcando las ABI preferidas, alternativas compatibles y no compatibles sin extraer su contenido
- `Función` Se añadieron estadísticas acotadas de componentes del manifest para actividades/alias, servicios, receptores de difusión y proveedores de contenido en splits APK seleccionados y módulos AAB examinados, agrupadas por estado android:exported explícito y con etiquetas para resultados parciales
- `Función` Se añadió verificación criptográfica en el dispositivo para los esquemas APK V2, V3, V3.1, V4 y V4.1, incluidos resúmenes de contenido, pruebas del firmante, raíces fs-verity, árboles Merkle incrustados y coincidencia con el esquema complementario
- `Función` Se añadieron campos detallados de certificados y linajes de rotación verificados con funciones antiguas/actuales, indicadores de capacidad y huellas SHA-256
- `Función` Se añadió el almacenamiento temporal acotado de `.idsig` mediante un descriptor de solo lectura derivado exactamente por el anfitrión; no se permite enumerar directorios ni acceder a archivos adyacentes arbitrarios
- `Función` Se agruparon los permisos solicitados por `protectionLevel` en ejecución/peligrosos, firma/protegidos y normales; los permisos de ejecución se destacan primero con explicaciones acotadas de una línea, y los niveles no disponibles siguen visibles y etiquetados
- `Corrección` Se corrigió la desaparición de la acción para ver el manifest tras cambiar el idioma, el tema u otra recreación de la actividad, sustituyendo de forma segura la instantánea privada anterior de solo lectura
- `Corrección` Se evitó que el panel de búsqueda del manifest quedara inaccesible cuando el teclado virtual reducía las pantallas compactas o con texto grande; ahora el teclado solo aparece al tocar el campo de búsqueda ya enfocado
- `Mejora` Se reforzaron la validación de solicitudes Explorer Action v22 y las instantáneas privadas inmutables, con límites de 4 GiB para el paquete y 40 MiB para idsig, comprobaciones de identidad y cierre inmediato de la sesión
- `Mejora` Se añadieron muestras oficiales de `apksigner` de Build Tools 37 para firmas válidas, alteradas, múltiples, con rotación V3.1/V4.1, ausentes y mal formadas
- `Mejora` Se añadió una matriz de aislamiento por secciones con paquetes reales que cubre los límites de producción de permisos, componentes del manifiesto, bibliotecas nativas y DEX, además de tablas de recursos excedidas y directorios centrales anidados dañados; cada muestra comprueba que las secciones no afectadas siguen completas y que los avisos parciales se conservan al compartir texto
- `Mejora` Se añadió una matriz de referencia reproducible para la selección de bundletool 1.18.2: `build-apks` procesa un AAB mínimo anonimizado y el `ExtractApksCommand` compartido por `install-apks` registra los APK de instalación para tres perfiles de idioma/densidad/ABI; las pruebas unitarias verifican cada conjunto simulado, la ausencia de duplicados y la estabilidad entre ejecuciones
- `Mejora` Se añadió una matriz reproducible de revisión de capturas con 36 casos: vertical de 411/320 dp y horizontal compacta, fuentes 1,0x/1,5x/2,0x, temas claro/oscuro y LTR/RTL; su ejecutor ADB conserva el estado del dispositivo, audita la accesibilidad de los controles, el orden de lectura y los objetivos táctiles de 48 dp, y genera pruebas PNG/XML y una hoja de contactos
- `Mejora` Se añadió una matriz determinista de 24 muestras sin datos privados que cubre APK/APKS/XAPK/APKM/APKZ/AAB con entradas normales, estructuralmente dañadas, fuera de límite e incompatibles con el dispositivo; un generador de biblioteca estándar, un manifiesto SHA-256 y pruebas de contrato JVM verifican la reproducción byte a byte, los resultados del analizador, el tamaño compacto y la ausencia de código, material de firma y datos de usuario
- `Mejora` Se añadieron matrices exhaustivas de pruebas unitarias de rechazo seguro para la validación de solicitudes de paquetes, la preparación en caché privada, las protecciones de archivos Android y el análisis de bloques de firma APK; se ejercita cada motivo de rechazo enumerado, incluidos metadatos malformados, rutas inseguras, límites de recursos, estructuras truncadas y cancelación

#### v1.0.1

_2026/08/08_

- `Corrección` Corregido que el anfitrión no pudiera vincularse al servicio del plugin tras activarlo en el centro de plugins; la acción «Inspeccionar paquete Android» ahora funciona inmediatamente después de activarlo
- `Mejora` Nombre y descripción del plugin simplificados, con una documentación de usuario más natural de leer

#### v1.0.0

_2026/08/02_

- `Aviso` Primera versión pública; requiere AutoJs6 con código de versión 5269 o superior
- `Función` Toca un archivo APK, APKS, XAPK, APKM, APKZ o AAB en el gestor de archivos de AutoJs6 para abrir un informe de inspección de solo lectura (ID de plugin `apk-inspector`, ID de acción `inspect-android-package`)
- `Función` El informe muestra el nombre y el icono de la aplicación, el nombre del paquete, la versión, el rango de SDK, los permisos solicitados, los splits y recursos OBB, los problemas estructurales y la presencia de los esquemas de firma V1-V3
- `Función` Los manifests APK de texto y binarios, los manifests protobuf de AAB y los metadatos `toc.pb` de bundletool se decodifican automáticamente, con un visor aparte para el manifest formateado
- `Función` Otras aplicaciones pueden entregar un paquete mediante «Abrir con» (ACTION_VIEW) usando los tipos MIME dedicados de paquetes Android
- `Función` Antes de la inspección, el archivo se copia a una instantánea privada de solo lectura con cálculo de SHA-256 (límite de 4 GiB); el plugin no solicita permisos de almacenamiento, red ni instalación
- `Función` Incluye 10 idiomas para la interfaz, las instrucciones, el README y el CHANGELOG: chino simplificado, chino tradicional (Hong Kong y Taiwán), inglés, francés, español, japonés, coreano, ruso y árabe
- `Dependencia` Añadido Gson 2.13.2

##### Historial completo

- [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

### Compilación

```powershell
.\gradlew.bat :app:assembleDebug
```

Compilación release:

```powershell
.\gradlew.bat :app:assembleRelease
```

Los parámetros de compilación y firma provienen de version.properties y sign.properties; el mínimo actual es Android 7.0 (SDK 24) con SDK objetivo 36.

Los archivos README y CHANGELOG se generan con .python/generate_markdown.py a partir de las fuentes JSON y plantillas de .readme/ y .changelog/ (10 idiomas). Para cambiar la documentación, edita las fuentes JSON y vuelve a ejecutar el script en lugar de editar el Markdown generado.

### Enlaces

- Documentación de AutoJs6: https://docs.autojs6.com
- Compartición segura de archivos en Android: https://developer.android.com/training/secure-file-sharing

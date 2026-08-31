# Historial de versiones

## v1.1.0

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
- `Mejora` Se añadieron pruebas de regresión de seguridad con Robolectric para ambas actividades de entrada exportadas, que cubren acciones suplantadas, permisos URI excesivos, declaraciones superiores a 4 GiB y cancelación concurrente del ciclo de vida; las solicitudes rechazadas nunca inician la inspección ni abren contenido sobredimensionado, y las sesiones del host Explorer se cierran exactamente una vez

## v1.0.1

_2026/08/08_

- `Corrección` Corregido que el anfitrión no pudiera vincularse al servicio del plugin tras activarlo en el centro de plugins; la acción «Inspeccionar paquete Android» ahora funciona inmediatamente después de activarlo
- `Mejora` Nombre y descripción del plugin simplificados, con una documentación de usuario más natural de leer

## v1.0.0

_2026/08/02_

- `Aviso` Primera versión pública; requiere AutoJs6 con código de versión 5269 o superior
- `Función` Toca un archivo APK, APKS, XAPK, APKM, APKZ o AAB en el gestor de archivos de AutoJs6 para abrir un informe de inspección de solo lectura (ID de plugin `apk-inspector`, ID de acción `inspect-android-package`)
- `Función` El informe muestra el nombre y el icono de la aplicación, el nombre del paquete, la versión, el rango de SDK, los permisos solicitados, los splits y recursos OBB, los problemas estructurales y la presencia de los esquemas de firma V1-V3
- `Función` Los manifests APK de texto y binarios, los manifests protobuf de AAB y los metadatos `toc.pb` de bundletool se decodifican automáticamente, con un visor aparte para el manifest formateado
- `Función` Otras aplicaciones pueden entregar un paquete mediante «Abrir con» (ACTION_VIEW) usando los tipos MIME dedicados de paquetes Android
- `Función` Antes de la inspección, el archivo se copia a una instantánea privada de solo lectura con cálculo de SHA-256 (límite de 4 GiB); el plugin no solicita permisos de almacenamiento, red ni instalación
- `Función` Incluye 10 idiomas para la interfaz, las instrucciones, el README y el CHANGELOG: chino simplificado, chino tradicional (Hong Kong y Taiwán), inglés, francés, español, japonés, coreano, ruso y árabe
- `Dependencia` Añadido Gson 2.13.2

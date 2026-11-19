APK Inspector examina paquetes APK, APKS, XAPK, APKM, APKZ y AAB antes de instalarlos: muestra el nombre de la app, la versión, los permisos, los componentes, las firmas y más, e indica si el paquete se puede instalar en este dispositivo. Solo lee el archivo: nunca instala el paquete ni modifica el archivo original.

### Primeros pasos

1. Comprueba que este plugin esté activado en el "Centro de plugins" de AutoJs6.
2. Abre el gestor de archivos de AutoJs6 y localiza el paquete.
3. Toca el archivo o elige "Inspeccionar paquete Android" en su menú (en AutoJs6 6.8.1 y posteriores está en el menú de primer nivel "APK Inspector").
4. Revisa el informe de arriba abajo: "Detalles del paquete", "Componentes", "Permisos solicitados" y "Hallazgos de seguridad y compatibilidad".

### Funciones principales

- **Detalles del paquete**: muestra el nombre del paquete, la versión y los SDK mínimo y objetivo, comparados con este dispositivo; mantén pulsado un campo para copiar su valor.
- **Verificación de firmas**: verifica los esquemas de firma V2, V3 y otros, y muestra las huellas de los certificados de firma.
- **Permisos solicitados**: agrupa los permisos en tres grupos (tiempo de ejecución, firma y normales), con los de tiempo de ejecución arriba y una breve explicación.
- **Componentes**: resume las activities, services, receivers y providers e indica si cada uno está exportado.
- **Hallazgos de seguridad y compatibilidad**: señala problemas como un APK base ausente, splits duplicados o versiones o nombres de paquete que no coinciden, y distingue los bloqueos de los avisos.
- **Simulación de configuración del dispositivo**: en conjuntos como APKS, cambia el idioma, la densidad de pantalla y la ABI, y toca "Aplicar configuración simulada" para comparar los splits elegidos con este dispositivo.
- **Ver manifest**: muestra AndroidManifest como XML con números de línea y resaltado de sintaxis; usa "Buscar en el manifest" para buscar texto.
- **Compartir informe**: envía un informe de texto igual al de la pantalla mediante el menú de compartir del sistema, sin crear archivos.

### Requisitos

- Android 7.0 o posterior.
- AutoJs6 6.8.0 o posterior.
- No necesita permisos de almacenamiento, red ni instalación; el plugin solo lee el archivo que seleccionaste en el gestor de archivos.

### Preguntas frecuentes

**Motivos por los que el plugin no instala paquetes**

El plugin sirve para examinar un paquete antes de instalarlo, por lo que no solicita permiso de instalación ni tiene botón de instalar. Instala los paquetes con el instalador del sistema o con el proceso de instalación de AutoJs6.

**Motivos por los que algunos archivos no se pueden examinar**

Las causas habituales son un archivo de más de 8 GiB, menos de 128 MiB de espacio libre en la caché, un archivo modificado por otra app durante la lectura o un archivo dañado. El mensaje de error de la página del informe indica la causa.

**Significado de una firma válida**

Una firma válida solo muestra que el archivo no ha cambiado desde que se firmó; no demuestra que el firmante o la app sean de confianza. Compara la huella del certificado o el SHA-256 con los valores publicados por la fuente oficial.

**Motivos por los que los archivos AAB no se instalan directamente**

AAB es un formato de distribución para tiendas de aplicaciones que los dispositivos Android no pueden instalar directamente. El plugin muestra su manifest y su estructura de módulos; conviértelo a APK o APKS con una herramienta como bundletool antes de instalarlo.

Más detalles en la [página del proyecto](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector).

# APK Inspector

APK Inspector proporciona la acción principal de inspección de solo lectura en el Explorer principal de AutoJs6 para archivos APK, APKS, XAPK, APKM, APKZ y AAB.

Muestra detalles del paquete, permisos solicitados, componentes, APK divididos compatibles con el dispositivo, recursos OBB, hallazgos estructurales, presencia de firmas APK V1-V3 y un manifest Android formateado.

El plugin requiere AutoJs6 build 5269+. Está implementado por completo en la JVM y no depende del ABI del dispositivo.

Límites de seguridad y privacidad:

- El origen se abre mediante acceso temporal de solo lectura con URI `content`.
- La entrada se copia una vez a una copia privada de solo lectura, limitada a 4 GiB, mientras se calcula SHA-256.
- El plugin no solicita permisos de almacenamiento, red ni instalación de paquetes.
- El plugin nunca instala ni modifica un paquete. Los esquemas de firma solo se comprueban por presencia.

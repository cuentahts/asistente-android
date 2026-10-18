# Asistente para Android — proyecto fuente (versión 0.1.0)

**Este ZIP NO contiene una APK ya compilada ni es el asistente completo solicitado.** Es un proyecto Kotlin Android inicial, preparado para que GitHub Actions intente generar una APK de depuración. No se ha probado en un Samsung A16 ni se ha compilado en este entorno, porque aquí no está instalado el SDK Android ni Gradle.

## Qué incluye realmente
- Bienvenida con nombre del asistente configurable y apelativo opcional.
- Pantalla sencilla de chat; historial persistente por conversación; nuevo chat.
- Recordatorios **como notas/recuerdos**, mediante «recuerda ...» o «guarda ...», siempre con confirmación; lista, papelera y restauración.
- Hora y fecha tomadas del teléfono.
- Control de la linterna mediante CameraManager previa confirmación.
- Intento de abrir una aplicación indicando su nombre, cuando Android la exponga al proyecto.
- Reconocimiento de voz bajo demanda a través del servicio de voz instalado en el teléfono (puede depender de conexión y de servicios externos).
- Respuestas habladas por TTS opcional; según el motor instalado puede usar internet.
- Modo claro/oscuro.
- Gemini como servicio opcional, introduciendo tu propia clave API en Ajustes (no incluida, no garantiza gratuidad permanente).
- Exportación e importación **JSON sin cifrar** usando el selector de documentos de Android. Puedes elegir tu microSD si el selector la ofrece. NO guarda la base de datos viva en la microSD.
- Workflow de compilación de APK de depuración en GitHub Actions.

## No incluye todavía
- Modelo de IA local sin internet, razonamiento offline general, análisis de imágenes, PDFs o grabaciones, transcripción de clases.
- Escucha permanente, detección de nombre personalizable con pantalla apagada, reconocimiento biométrico del hablante, PIN/huella integrada o modo invitados.
- Control de pantalla por accesibilidad, lectura de todas las apps, notificaciones/correos/redes sociales, controles de música de otras apps, portapapeles en segundo plano.
- Alarmas, temporizadores y recordatorios programados; calendarios, detección de choques y rutinas de clases.
- Modo silencio, geocercas, ahorro nocturno, vibración por prioridad, procesos persistentes.
- Exportación cifrada, backups selectivos, transferencia de almacenamiento activo, retención temporal y seguridad avanzada.
- Garantía de compatibilidad con cualquier teléfono o con ejecución permanente en segundo plano.

## Cómo convertirlo en APK sin Android Studio
1. Crea una cuenta en https://github.com si no tienes.
2. Crea un repositorio **privado** nuevo llamado `asistente-android` sin inicializar con README.
3. Descomprime este ZIP en tu computadora.
4. En GitHub abre el repositorio, selecciona **Add file → Upload files** y sube el **contenido** de la carpeta `Asistente`, conservando carpetas y archivos ocultos. Debes asegurarte de que existe `.github/workflows/android.yml` en la raíz del repositorio. Si la web omite carpetas ocultas o cambia la estructura, utiliza GitHub Desktop para subir la carpeta entera.
5. Confirma los cambios en la rama `main`.
6. Abre la pestaña **Actions**. Si GitHub solicita habilitar workflows, revísalos y habilítalos.
7. Busca **Crear APK Android**. Se ejecutará automáticamente al subir archivos a `main`, o puedes usar **Run workflow**.
8. Si la ejecución termina en verde, abre esa ejecución, baja hasta **Artifacts** y descarga `Asistente-APK` (archivo ZIP).
9. Extrae del ZIP descargado `app-debug.apk` y transfiérelo a tu celular (USB, Drive o transferencia local).
10. Desde el teléfono abre `app-debug.apk`. Android puede pedir autorizar **Instalar apps desconocidas** para tu gestor de archivos. Habilita solo para esa fuente de confianza; instala y luego revoca ese permiso si quieres.
11. Al abrir verás la bienvenida y podrás escoger nombre.

Si Actions aparece en rojo, la APK NO fue generada. Copia el error de los registros de compilación; debe resolverse antes de instalar nada. La APK `debug` está firmada con clave de depuración del runner y **no es adecuada para distribución pública ni para garantizar actualizaciones compatibles**. Respaldar datos antes de instalar versiones nuevas.

## Almacenamiento en microSD
Android puede permitir escoger archivos de exportación en la tarjeta microSD desde el selector del sistema. La base activa del prototipo está en las preferencias privadas internas de la aplicación. Las copias JSON no están cifradas; **no las subas a servicios públicos ni las compartas si contienen datos privados**.

## Cómo conseguir una clave Gemini si decides usarla
Entra a https://aistudio.google.com/apikey, inicia sesión y crea una clave bajo tu propia cuenta si el servicio está disponible para ti. Comprueba las cuotas y condiciones vigentes. Guarda la clave solo en el teléfono. La versión inicial la almacena en preferencias privadas sin cifrado adicional: no es aconsejable para datos de alta sensibilidad. La app no incluye ninguna clave ni factura servicios automáticamente.

## Importante
Este proyecto se entrega como **base verificable y ampliable**, no como la realización de todas las características imaginadas. En particular, otorgar permisos no hace posible saltar el bloqueo de Android ni acceder irrestrictamente a otras aplicaciones. Antes de que contenga datos reales y confidenciales, requiere mejoras de autenticación, cifrado, manejo de errores, pruebas y auditoría de seguridad.

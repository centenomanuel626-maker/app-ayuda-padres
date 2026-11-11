# App de ayuda para mis padres

App Android con botones grandes para que mis padres resuelvan solos los problemas del celular, y un panel en mi celular para ver cómo están y recibir avisos.

- **Celular de los padres:** 4 categorías (Internet, Sonido y pantalla, Ayuda y Mi celular) con 29 botones. Abajo siempre están “Llamar a Manuel” y “Emergencia”. Tiene voz, comandos de voz, modo práctica, alarmas de medicinas, lupa, contactos con foto, alerta contra estafas, revisión de seguridad, quitar apps que no usan, vigilancia de instalaciones y modo pantalla de inicio.
- **Celular de Manuel:** la misma app en modo “Mi panel”: batería, conexión, “Estoy bien”, resultado de seguridad, avisos y categorías visibles a distancia.

> Mantén este repositorio **privado**: incluye la llave con la que se firma la app (`app/familia.keystore`).

## 1. Supabase (ya está listo)

El proyecto **Ayuda Padres** (`shbhfmkwphfofnijhxgd`) ya tiene las tablas y las reglas de seguridad de `supabase/schema.sql`, y la app ya trae su dirección y su clave pública. No hay que hacer nada.

Para usar otro proyecto, pega `supabase/schema.sql` en su **SQL Editor** y crea en GitHub los secretos `SUPABASE_URL` y `SUPABASE_ANON_KEY` (Settings → Secrets and variables → Actions).

## 2. Compilar el APK en GitHub

1. Ve a **Actions → Compilar APK → Run workflow** (también se compila solo con cada cambio en `main`).
2. Cuando termine (unos 5 minutos), abre la ejecución y descarga **app-ayuda-padres**. Dentro está `app-release.apk`.

## 3. Instalar en tu celular (primero)

1. Pasa el APK a tu celular e instálalo (Android pedirá permitir instalar desde esa app; acéptalo solo para esto).
2. Elige **“En mi celular”**.
3. Copia el **código de familia** que aparece. Lo vas a escribir en los celulares de tus padres.

## 4. Instalar en el celular de tus padres

1. Instala el mismo APK.
2. Elige **“En el celular de mis padres”** y llena: cómo los saluda, tu nombre, tu teléfono **con código de país** (ej. `+57 300 123 4567`), el código de familia y un PIN.
3. Se abre la configuración: toca cada permiso con ❌ y acéptalo. En Xiaomi activa también **Inicio automático**.
4. Agrega sus **medicinas** (nombre y hora) y sus **contactos con foto**.
5. Marca las **apps aprobadas** (WhatsApp, el banco, etc.) y, si quieres, prende el **modo pantalla de inicio**.
6. Al final, **apaga “Instalar apps desconocidas”** para el navegador, los archivos y la app que usaste para pasar el APK.
7. Instala **AnyDesk** desde la Play Store para la ayuda a distancia.

Para volver a la configuración: en la pantalla principal, **mantén presionado “Hola, mamá”** y escribe el PIN.

## Lo que Android no deja hacer (y cómo lo resuelve la app)

| Qué | Por qué | Qué hace la app |
|---|---|---|
| Prender el wifi, los datos o el Bluetooth | Android 10+ no lo permite a las apps | Abre el panel correcto y explica qué tocar, en voz alta |
| Poner la contraseña de bloqueo | Protegido por el sistema | Abre la pantalla con instrucciones |
| Desinstalar apps sin preguntar | Protegido por el sistema | Abre la confirmación del sistema; las de fábrica se desactivan |
| Reiniciar el celular | Protegido por el sistema | Muestra los pasos con un dibujo del modelo |
| Iniciar la videollamada de WhatsApp | WhatsApp no lo permite | Abre el chat; falta tocar la cámara |
| Avisos al instante en tu celular | Sin servidor de notificaciones push, Android revisa cada 15 minutos | Las emergencias también salen al instante por WhatsApp o SMS |
| Detectar un espionaje muy profesional | Puede esconderse de cualquier app | La revisión de seguridad busca 10 señales; un verde baja la sospecha pero no la descarta |

Esta app no se puede publicar en Google Play tal como está (usa permisos que Play restringe, como ver todas las apps y filtrar llamadas). Está pensada para instalarse directamente en los celulares de la familia.

## Estructura

- `app/src/main/java/com/manuel/ayudapadres/`
  - `MainActivity.kt`, `Screens.kt`, `Acciones.kt`, `Catalog.kt`: pantalla de los padres y lo que hace cada botón
  - `PanelActivity.kt`: panel de Manuel
  - `SetupActivity.kt`, `SettingsActivity.kt`, `Perms.kt`: primera configuración y permisos
  - `Cloud.kt`: conexión con Supabase
  - `ReportJobService.kt`: reporte cada 15 minutos y avisos
  - `SecurityCheck.kt`, `InstallWatch.kt`, `ScamCallScreeningService.kt`: seguridad
  - `Alarms.kt`, `AlarmReceiver.kt`, `MedAlarmActivity.kt`: medicinas y “Estoy bien” diario
  - `MagnifierActivity.kt`: lupa
- `supabase/schema.sql`: tablas y reglas de seguridad
- `.github/workflows/build.yml`: compilación del APK

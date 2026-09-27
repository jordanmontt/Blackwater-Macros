# Blackwater Macros

Aplicación personal de seguimiento de **calorías, macros y peso**. Dos clientes:

- **Web** (Next.js): mobile-first, modo claro/oscuro, datos en la nube con cuentas por
  invitación, y un modo demo sin cuenta.
- **Android** (Kotlin/Compose, software libre pensado para F-Droid): funciona completa
  **sin cuenta y sin conexión**; la cuenta es opcional y sincroniza con la web.

> ¿Vas a modificar el código? Lee [TECHNICAL.md](./TECHNICAL.md): arquitectura,
> modelo de datos, flujo de autenticación, API, tests y convenciones.

## Funcionalidades

- **Comidas por día**: título, ingredientes con cantidad y notas. Dos modos de
  registro de nutrición: *por ingrediente* (la app suma calorías, proteína,
  carbohidratos y grasa) o *solo total* (introduces únicamente el total de la comida).
  Doble toque en la fecha para volver a hoy.
- **Recomendaciones** de calorías (TMB, nivel de actividad calculado a partir de tus días
  de gimnasio y minutos de caminata, TDEE, rango según objetivo) y de proteína (por
  masa magra en definición si registras tu % de grasa), con barras de progreso de lo
  que llevas comido frente al objetivo.
- **Gasto medido**: con 4 semanas de comidas y pesajes frecuentes, la app mide tu gasto
  real por balance energético y lo muestra con su margen de error junto al estimado.
- **Perfil** (Ajustes → Perfil): objetivo deportivo y datos corporales.
- **Plantillas**: guarda comidas repetitivas ("Desayuno") y aplícalas en un toque.
- **Progreso**: un selector de periodo para toda la pantalla; peso actual,
  tendencia (media móvil de 7 días), cambio, ritmo semanal y grasa corporal con su
  gráfico; calorías diarias con la franja del objetivo; «Promedio de macros» de los
  días registrados con el reparto de calorías; y los registros de peso (varios al día,
  con fecha y hora autocompletadas).
- **Página de metodología** (`/metodologia`): explica con fórmulas y referencias
  cómo se calcula cada métrica.
- **Exportación CSV** de comidas y pesos (en Android también importación, con el
  mismo formato que la web).

## Pila técnica

| Capa | Tecnología |
|---|---|
| Frontend + Backend | Next.js (App Router) · React · TypeScript |
| Estilo | Tailwind CSS v4 · shadcn/ui · next-themes |
| Gráficas | recharts |
| Base de datos | PostgreSQL en [Neon](https://neon.tech) (plan gratuito) |
| ORM | Drizzle ORM + drizzle-kit |
| Autenticación | Usuario/contraseña con scrypt + sesiones en base de datos (cookie httpOnly) |
| Tests | Vitest · Testing Library |
| Android | Kotlin · Jetpack Compose · Room · WorkManager · Retrofit (sin servicios de Google) |

Los usuarios se crean manualmente (no hay registro público).

## Puesta en marcha local

> **En un clon nuevo hay **dos cosas que hacer una sola vez**:
>
> 1. **Crear `DATABASE_URL`** → `cp .env.example .env.local` y pega tu connection
>    string de Neon. Sin esto la app cae en las peticiones a la API (no hay BD).
> 2. **Instalar el hook de git** → `npm run hooks:install`. Crea un aviso local que
>    te recuerda al hacer commit si tocas los tests del core TS/Kotlin y olvidas su
>    espejo. El script está en el repo, pero el hook instalado es solo de tu
>    máquina y hay que regenerarlo en cada clon.

1. Crea una cuenta gratuita en Neon y un proyecto → copia la *connection string*.
2. Instala dependencias y configura el entorno:

   ```bash
   npm install
   cp .env.example .env.local   # pega tu DATABASE_URL
   ```

3. Crea las tablas:

   ```bash
   npm run db:push
   ```

4. Crea tu usuario:

   ```bash
   npm run create-user -- miusuario miclave
   ```

5. Arranca:

   ```bash
   npm run dev
   ```

> Requiere Node ≥ 20.19 (recomendado 22 LTS).

### Probar sin crear cuenta: modo demo

En la pantalla de **Iniciar sesión** pulsa **Explora datos de demo**. Se genera un
dataset local (~45 días de comidas, pesos y plantillas) que se guarda solo en tu
navegador (`sessionStorage`) y puedes editar libremente. Se reinicia al cerrar la
pestaña o desde la barra demo ("Iniciar sesión").

## App Android (Kotlin/Compose)

La app nativa Android está en `android/`, estructurada en dos módulos:
`core` (JVM puro, sin Android — lógica de cálculo portada y testada) y `app`
(UI + red). Es el mismo backend desplegado; usa `Authorization: Bearer <token>`.

**Diseño: local primero.** La app funciona completa sin cuenta: todo se guarda en el
teléfono (Room) y abre directamente en **Comidas**. La cuenta es opcional (Ajustes →
**Cuenta** → Iniciar sesión, solo por invitación): al conectarla, los datos se sincronizan
en segundo plano con el mismo backend que la web (`PUT /api/<tipo>/:id` idempotente +
listas completas). Funciona sin conexión: lo que registras en el gimnasio se guarda al
instante y se sube solo cuando vuelve la red, aunque cierres la app.

- **Comidas**, **Progreso** (calculado en el teléfono) y **Ajustes**
  (cuenta y sincronización, **Perfil**, plantillas, exportar/importar CSV con el mismo
  formato que la web, borrar los datos del teléfono, tema Sistema/Claro/Oscuro, idioma,
  Metodología y Admin).
- Idiomas: inglés (por defecto), español, francés, italiano y alemán — según el idioma
  del teléfono o elegido en Ajustes → Idioma.
- Con cuenta, una nube en la cabecera de Comidas indica si todo está sincronizado.
- Al iniciar sesión con datos locales se pregunta si subirlos o descartarlos; al cerrar
  sesión se borran del teléfono (avisando si hay cambios sin sincronizar).

Detalles en `TECHNICAL.md` §14.

### Compilar

```bash
cd android
./gradlew test lint build      # toda la suite (core + app) y lint
```

El APK de prueba queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

### URL del backend

La URL base se inyecta por BuildConfig, con valor por defecto
`https://blackwater-macros.jordanmontt.fr/`. Para apuntar a otra (p. ej. local):

```bash
./gradlew :app:installDebug -Papp.baseUrl=http://10.0.2.2:3000/
```

### Probarla en un emulador

La app se instala con las herramientas de Android (SDK vía Homebrew en este repo
de desarrollo). Con un emulador encendido y visible por `adb`:

```bash
cd android
./gradlew :app:installDebug     # instala app-debug.apk en el emulador
adb shell am start -n com.blackwatermacros.app/.MainActivity
```

La app abre directamente en **Comidas** y funciona sin cuenta; añade una comida con
el botón **+**. Para probar la sincronización, **Ajustes → Cuenta → Iniciar sesión**
(el emulador usa la red del ordenador y alcanza el backend de producción).

> El archivo `android/local.properties` (ruta del SDK) es específico de tu máquina
> y **no se sube** (está en `.gitignore`).

## Scripts útiles

| Comando | Descripción |
|---|---|
| `npm run dev` | Servidor de desarrollo |
| `npm test` | Ejecuta toda la suite de tests |
| `npx vitest run tests/behavior` | Solo pruebas de comportamiento |
| `npx vitest run tests/unit` | Solo pruebas unitarias |
| `npm run typecheck` | Comprobación de tipos |
| `npm run lint` | ESLint |
| `npm run db:push` | Sincroniza el esquema con la base de datos |
| `npm run create-user -- u p` | Crea (o actualiza) un usuario |
| `npm run core:sync-check` | Comprueba que TS y Kotlin del core van en sincronía |
| `npm run hooks:install` | Instala el aviso de sincronización al hacer commit |
| `scripts/logo/render-icons.sh` | Regenera todos los iconos (Android y web) desde el logo vectorial |

## Cómo funciona (visión de alto nivel)

El proyecto es **una app web (Next.js) que es a la vez su propio backend**: sirve
las páginas (React) y las mismas rutas `/api/*` actúan como servidor que guarda los
datos en PostgreSQL (Neon). El navegador habla con esas rutas API y el servidor es
quien hace de "fuente de verdad" (autorización, validación, cálculos y persistencia).

Hay **dos mundos de código**:

- **El servidor** (`src/server/` + `src/app/api/`): repositorios (acceso a BD) y
  servicios (lógica de negocio). Filtra siempre por el usuario de la sesión.
- **El cliente** (`src/lib/` + `src/components/` + `src/app/`): componentes React,
  llamadas a la API (`src/lib/api.ts`) y un **modo demo** que funciona sin servidor
  (`src/lib/demo-api.ts` / `demo-store.ts`) guardando datos en `sessionStorage`.

La **lógica pura de cálculo** (suma de macros, proteína, calorías/BMR, fechas,
estadísticas, CSV) vive aislada en `src/lib/core/` — **sin dependencias del
navegador ni del servidor** — para poder reimplementarse 1:1 en Android/Kotlin.

## Decisiones de diseño importantes

- **La web es un cliente fino; Android es "local primero".** En la web, el backend
  guarda la lógica (autenticación, validación, persistencia). Android guarda todo en el
  teléfono y funciona sin cuenta; con cuenta, sincroniza en segundo plano con la
  **misma API** (subidas idempotentes `PUT /:id` + listas completas).
- **Los tests son la fuente de verdad.** La suite del `core` es la especificación
  compartida entre web y Android: cada test web tiene su espejo Kotlin, velado por
  `npm run core:sync-check`.
- **Duplicar código es una decisión, y es aceptable.** Para un proyecto en
  solitario se prefiere duplicar la matemática pura (anclada a tests) antes que la
  alternativa de compilarlo a WebAssembly para la web.
- **La web tiene una carpeta `core`** (`src/lib/core/`) con toda la lógica
  importante, reimplementada 1:1 en Kotlin.

El detalle de cada capa está en [TECHNICAL.md](./TECHNICAL.md).

## Estructura

```
src/
  app/                 Páginas (React) y rutas API (App Router)
    api/               Backend serverless: meals, templates, weights, stats, export…
  components/          Componentes de UI (shadcn/ui + propios)
  i18n/es.ts           Textos en español centralizados (listo para más idiomas)
  lib/                 Lógica del cliente (llamadas API, demo, caché)
    api.ts             Cliente HTTP hacia /api/*
    demo-api.ts, demo-store.ts   Modo demo (sin servidor, sessionStorage)
    core/              Algoritmos puros sin dependencias (se portan a Android/Kotlin)
  server/
    auth/              Hash de contraseñas (scrypt) y tokens de sesión
    db/                Esquema Drizzle (src/server/db/schema.ts) y cliente Postgres
    repositories/      Acceso a datos (inyectables, fáciles de simular en tests)
    services/          Lógica de negocio pura sin HTTP
    api-auth.ts        Resuelve la sesión: cookie (web) o Bearer (Android/API)
tests/
  behavior/            Pruebas de comportamiento (caja negra, requisitos del usuario)
  unit/                Pruebas técnicas de piezas puras (el core, casos límite)
scripts/
  logo/                Fuente vectorial del logo y script que genera todos los iconos
docs/
  api.md               Contrato de la API (incluye el protocolo de sincronización Android)
  ANDROID-TEST-SPEC.md Tests Android, contrato core TS ⇄ Kotlin y checklist de mantenimiento
android/               App nativa Android: módulos :core (algoritmos) y :app (UI + datos)
TECHNICAL.md           Arquitectura detallada (fuente de verdad para desarrolladores)
```

El detalle completo de cada capa (flujo de una petición, esquema de base de datos,
referencia de la API, patrones de React y de tests) está en
[TECHNICAL.md](./TECHNICAL.md).

## Tests

- **`npm test`** ejecuta toda la suite (Vitest).
- **`tests/unit/`**: piezas puras (los algoritmos de `src/lib/core/`) con casos
  límite. Son la "especificación" que se comparte con Android.
- **`tests/behavior/`**: requisitos del usuario desde fuera (rutas API, flujos de
  pantallas), sin importar cómo se guarde nada.

> **Importante para mantenerlo (solo/a):** el core se implementa dos veces —
> TypeScript (web) y Kotlin (Android). Si tocas un test de `tests/unit/`, **tienes
> que tocar también su espejo Kotlin** (y viceversa). `npm run core:sync-check`
> avisa si olvidas uno de los dos lados y **bloquea el merge en CI**. Las reglas
> completas y los tests de Android están en `docs/ANDROID-TEST-SPEC.md`.

## Despliegue (gratis)

1. Sube el repo a GitHub.
2. En [Vercel](https://vercel.com), importa el repositorio con el plan Hobby
   (gratis para uso personal) y añade la variable `DATABASE_URL`.
3. Aplica el esquema contra la base de producción (`DATABASE_URL=… npm run db:push`)
   y crea tu usuario con `create-user`.
4. Añade tu dominio propio en *Settings → Domains* y apunta el CNAME que indica Vercel.

## Notas de diseño

- **Aislamiento por usuario**: todas las consultas filtran por `user_id` de la sesión.
- **Zona horaria**: cada cliente envía su fecha local (`YYYY-MM-DD`) para anclar
  los días; los pesos se guardan como instancias exactas en UTC.
- **Sesiones**: token opaco de 32 bytes en cookie httpOnly, 90 días de validez,
  revocable en base de datos.

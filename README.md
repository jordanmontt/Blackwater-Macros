# Blackwater Macros

Aplicación personal de seguimiento de **calorías, proteína y peso**.
Minimalista, mobile-first, con modo claro/oscuro y datos en la nube.

> ¿Vas a modificar el código? Lee [TECHNICAL.md](./TECHNICAL.md): arquitectura,
> modelo de datos, flujo de autenticación, API, tests y convenciones.

## Funcionalidades

- **Comidas por día**: título, ingredientes con cantidad y notas. Dos modos de
  registro de nutrición: *por ingrediente* (la app suma calorías y proteína) o
  *solo total* (introduces únicamente el total de la comida).
- **Plantillas**: guarda comidas repetitivas ("Desayuno") y aplícalas en un toque.
- **Peso**: varios registros al día con fecha y hora autocompletadas.
- **Estadísticas**: series diarias de kcal y proteína con línea de tendencia
  (media móvil de 7 días) y día pico marcado, evolución del peso con tendencia,
  ritmo semanal (kg/semana), cambio total, medias semanales y mínimos/máximos.
- **Página de metodología** (`/metodologia`): explica con fórmulas y referencias
  cómo se calcula cada métrica.
- **Exportación CSV** de comidas y pesos.
- **Futuras métricas**: el esquema ya reserva `carbs`/`fat` en ingredientes y
  columnas totales; activarlas no requiere migrar nada más que añadir campos.

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

**Estado actual:** app funcional con la mayoría de pantallas de la web. Tras el
**Iniciar sesión** (`POST /api/auth/login` → `GET /api/auth/session`) hay un shell
de **4 pestañas persistentes** que refleja la navegación web: **Comidas**, **Peso**,
**Estadísticas** y **Ajustes** (mismo orden, iconos seleccionados y títulos).

- **Comidas/Hoy** (`GET /api/meals` por día): navegador de fechas, totales diarios,
  tarjeta de recomendaciones (paramétrica por objetivo), fila de *Aplicar plantilla*, lista
  reordenable arrastrando (`PATCH /api/meals/reorder`) y formulario **Nueva / Editar
  comida** en hoja inferior con los dos modos (**Por ingrediente** / **Solo total**),
  más borrar con confirmación (`DELETE /api/meals`).
- **Peso** (`/api/weights`): registro de pesos (kg, % grasa, fecha, nota), resumen del peso
  actual con variación a 7 días, gráfico de evolución + grasa (Canvas propio) y lista
  agrupada por día con editar/borrar.
- **Estadísticas** (`/api/stats`): selector de rango (7/30/90 días o todo), resumen de peso,
  gráfico peso+grasa, medias semanales, resumen de macros y gráficas de tendencia de
  kcal/proteína/peso/grasa con media móvil de 7 días y día pico.
- **Ajustes**: apariencia (claro/oscuro), objetivo deportivo, perfil con **autoguardado**
  (los campos se validan y persisten con debounce de 500 ms), recomendaciones, plantillas y
  **Cerrar sesión**. Enlace a **Metodología**; si el usuario es admin, enlace a **Admin**.
- **Metodología** y **Admin** (solo admin: crear/editar/borrar usuarios) como páginas
  empujadas encima del shell.

Gráficas dibujadas a mano con Compose Canvas (sin librería). Export CSV de la web queda
**pendiente en Android** (botones inhabilitados). El token se guarda solo en memoria (se
vuelve a pedir al reiniciar); el modo offline/local sigue pendiente (ver
`docs/ANDROID-PLAN.md`).

### Compilar

```bash
cd android
./gradlew test lint build      # toda la suite (core + app) y lint
```

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

El emulador usa la red del ordenador, así que alcanza el backend de producción
sin más configuración. Inicia sesión, entra en **Ver comidas de hoy** y añade una
comida con el botón **+**.

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

- **El backend guarda la lógica; las apps son solo interfaz.** Autenticación,
  validación, cálculos y persistencia están en el backend. La web y la app
  Android son UIs que hablan con la **misma API**.
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
docs/
  TECHNICAL.md         Arquitectura detallada (fuente de verdad para desarrolladores)
  api.md               Contrato de la API
  ANDROID-PLAN.md      Plan de la app Android y contrato de mantenimiento
  ANDROID-TEST-SPEC.md Cómo se espejan los tests web ⇄ Kotlin
android/               (en construcción) La futura app nativa Android/Kotlin
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
> avisa si olvidas uno de los dos lados y **bloquea el merge en CI**. La app Android
> y su calendario de trabajo están en `docs/ANDROID-PLAN.md`.

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

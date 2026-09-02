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

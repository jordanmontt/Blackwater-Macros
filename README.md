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

### Datos de ejemplo

Para probar la app con datos realistas (~45 días de comidas, pesos y plantillas):

```bash
npm run seed            # crea usuario "demo" con contraseña "demo1234"
npm run seed -- demo otraclave   # credenciales personalizadas
```

El script es idempotente: borra los datos previos del usuario demo y vuelve a generarlos.

> Requiere Node ≥ 20.19 (recomendado 22 LTS).

### Reiniciar los datos demo en producción

El despliegue y el desarrollo comparten la misma base de datos Neon, así que los
scripts que ejecutas en local actúan directamente sobre el sitio en producción:

1. Ejecuta `npm run seed` en tu máquina (usa el `DATABASE_URL` de `.env.local`).
2. Recarga la web: el usuario `demo / demo1234` vuelve a tener ~45 días de datos
   que terminan hoy. Tus demás cuentas no se tocan.

Esto es útil para mostrar la app a otras personas con datos frescos: comparte las
credenciales demo, y cuando quieras "reiniciar la demostración", vuelve a lanzar
el comando.

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
| `npm run seed` | Datos de ejemplo para el usuario demo |

## Estructura

```
src/
  app/                 Páginas y rutas API (App Router)
    api/               Backend serverless: meals, templates, weights, stats, export…
  components/          Componentes de UI (shadcn/ui + propios)
  i18n/es.ts           Textos en español centralizados (listo para más idiomas)
  lib/                 Lógica pura compartida (fechas, nutrición, estadísticas, CSV)
  server/
    auth/              Hash de contraseñas (scrypt) y tokens de sesión
    db/                Esquema Drizzle y cliente Postgres
    repositories/      Acceso a datos (inyectables, fáciles de simular en tests)
    services/          Lógica de negocio pura sin HTTP
tests/
  behavior/            Pruebas de comportamiento: requisitos del usuario, caja negra
  unit/                Pruebas técnicas de piezas puras: casos límite y detalles
```

El detalle completo de cada capa (flujo de una petición, esquema de base de datos,
referencia de la API, patrones de React y de tests) está en
[TECHNICAL.md](./TECHNICAL.md).

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

import { describe, expect, it, vi, beforeEach } from "vitest";
import type { UserRow } from "@/server/db/schema";
import {
  createMemoryWorld,
  authenticateWith,
  seedUser,
  jsonRequest,
  routeParams,
} from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";

const holder = vi.hoisted(() => ({
  world: null as MemoryWorld | null,
  authCookie: { value: "" },
}));

vi.mock("@/server/composition", () => ({
  get repositories() {
    return holder.world?.repositories;
  },
  get serviceDeps() {
    return holder.world?.serviceDeps;
  },
}));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "bw_session" && holder.authCookie.value
        ? { name, value: holder.authCookie.value }
        : undefined,
  }),
}));

import { GET as getUsers, POST as postUser } from "@/app/api/admin/users/route";
import {
  PATCH as patchUser,
  DELETE as deleteUser,
} from "@/app/api/admin/users/[id]/route";
import { POST as postLogin } from "@/app/api/auth/login/route";

holder.world = createMemoryWorld();

const world = holder.world;

const ADMIN_USER = "jefe";
const ADMIN_PASS = "clave-secreta-1";
const OTHER_PASS = "otra-secreta-1";

async function seedAdmin(): Promise<UserRow> {
  const user = await seedUser(world!, ADMIN_USER, ADMIN_PASS, true);
  authenticateWith(holder.authCookie, world!, user.id);
  return user;
}

async function seedNormal(): Promise<UserRow> {
  const user = await seedUser(world!, "ray", OTHER_PASS);
  authenticateWith(holder.authCookie, world!, user.id);
  return user;
}

describe("rutas de administración de usuarios", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  describe("control de acceso", () => {
    it("sin sesión devuelve 401 en todas las operaciones", async () => {
      expect((await getUsers(new Request("http://test/api/admin/users"))).status).toBe(401);
      expect((await postUser(jsonRequest("/api/admin/users", {}))).status).toBe(401);
      expect((await patchUser(jsonRequest("/api/admin/users/u-2", {}), routeParams("u-2"))).status).toBe(401);
      expect((await deleteUser(new Request("http://test/api/admin/users/u-2"), routeParams("u-2"))).status).toBe(401);
    });

    it("un usuario normal devuelve 403 en todas las operaciones", async () => {
      await seedNormal();
      expect((await getUsers(new Request("http://test/api/admin/users"))).status).toBe(403);
      expect(
        (await postUser(jsonRequest("/api/admin/users", { username: "nuevo", password: OTHER_PASS }))).status,
      ).toBe(403);
      expect(
        (await patchUser(jsonRequest("/api/admin/users/u-2", { isAdmin: true }), routeParams("u-2"))).status,
      ).toBe(403);
      expect((await deleteUser(new Request("http://test/api/admin/users/u-2"), routeParams("u-2"))).status).toBe(403);
    });
  });

  describe("GET /api/admin/users", () => {
    it("lista los usuarios sin exponer la contraseña", async () => {
      await seedAdmin();
      await seedUser(world!, "ana", OTHER_PASS, true);
      await seedUser(world!, "bea", OTHER_PASS);

      const res = await getUsers(new Request("http://test/api/admin/users"));
      expect(res.status).toBe(200);
      const { users } = await res.json();
      const usernames = users.map((u: { username: string }) => u.username);
      expect(usernames).toEqual([ADMIN_USER, "ana", "bea"]);
      expect(users[0].isAdmin).toBe(true);
      expect(users[2].isAdmin).toBe(false);
      for (const user of users) {
        expect(user.passwordHash).toBeUndefined();
        expect(user.password_hash).toBeUndefined();
        expect(typeof user.createdAt).toBe("string");
      }
    });
  });

  describe("POST /api/admin/users", () => {
    it("crea un usuario que puede iniciar sesión", async () => {
      await seedAdmin();
      const res = await postUser(
        jsonRequest("/api/admin/users", { username: "luis", password: "mi-clave-123" }),
      );
      expect(res.status).toBe(201);
      expect(await res.json()).toEqual({ ok: true });

      const created = await world!.repositories.users.findByUsername("luis");
      expect(created).not.toBeNull();
      expect(created!.isAdmin).toBe(false);

      const login = await postLogin(
        jsonRequest("/api/auth/login", { username: "luis", password: "mi-clave-123" }),
      );
      expect(login.status).toBe(200);
    });

    it("rechaza un usuario duplicado con 409", async () => {
      await seedAdmin();
      const res = await postUser(
        jsonRequest("/api/admin/users", { username: "luis", password: "mi-clave-123" }),
      );
      expect(res.status).toBe(201);

      const dup = await postUser(
        jsonRequest("/api/admin/users", { username: "luis", password: "mi-clave-123" }),
      );
      expect(dup.status).toBe(409);
      expect(await dup.json()).toEqual({ error: "El usuario ya existe" });
    });

    it("rechaza el duplicado aunque cambie las mayúsculas", async () => {
      await seedAdmin();
      await postUser(jsonRequest("/api/admin/users", { username: "luis", password: "mi-clave-123" }));
      const dup = await postUser(
        jsonRequest("/api/admin/users", { username: "Luis", password: "mi-clave-123" }),
      );
      expect(dup.status).toBe(409);
    });

    it("valida el cuerpo (400) en nombre o contraseña", async () => {
      await seedAdmin();
      const short = await postUser(
        jsonRequest("/api/admin/users", { username: "luis", password: "corta" }),
      );
      expect(short.status).toBe(400);
      expect((await short.json()).error).toContain("8 caracteres");

      const noName = await postUser(
        jsonRequest("/api/admin/users", { username: "ab", password: "mi-clave-123" }),
      );
      expect(noName.status).toBe(400);
    });
  });

  describe("PATCH /api/admin/users/[id]", () => {
    it("cambia el nombre de usuario", async () => {
      const admin = await seedAdmin();
      await seedUser(world!, "ana", OTHER_PASS);
      const res = await patchUser(
        jsonRequest("/api/admin/users/u-2", { username: "analia" }),
        routeParams("u-2"),
      );
      expect(res.status).toBe(200);
      const { user } = await res.json();
      expect(user.username).toBe("analia");
      expect(user.isAdmin).toBe(false);
      const login = await postLogin(
        jsonRequest("/api/auth/login", { username: "analia", password: OTHER_PASS }),
      );
      expect(login.status).toBe(200);
      expect(admin.username).toBe(ADMIN_USER);
    });

    it("rechaza un nombre que ya usa otro usuario (409)", async () => {
      await seedAdmin();
      await seedUser(world!, "ana", OTHER_PASS);
      await seedUser(world!, "bea", OTHER_PASS);
      const res = await patchUser(
        jsonRequest("/api/admin/users/u-2", { username: "bea" }),
        routeParams("u-2"),
      );
      expect(res.status).toBe(409);
      expect(await res.json()).toEqual({ error: "El usuario ya existe" });
    });

    it("restablece la contraseña", async () => {
      await seedAdmin();
      await seedUser(world!, "ana", OTHER_PASS);
      const res = await patchUser(
        jsonRequest("/api/admin/users/u-2", { password: "nueva-clave-99" }),
        routeParams("u-2"),
      );
      expect(res.status).toBe(200);

      const oldLogin = await postLogin(
        jsonRequest("/api/auth/login", { username: "ana", password: OTHER_PASS }),
      );
      expect(oldLogin.status).toBe(401);

      const newLogin = await postLogin(
        jsonRequest("/api/auth/login", { username: "ana", password: "nueva-clave-99" }),
      );
      expect(newLogin.status).toBe(200);
    });

    it("concede y retira el rol de administrador", async () => {
      await seedAdmin();
      const bea = await seedUser(world!, "bea", OTHER_PASS);
      const promote = await patchUser(
        jsonRequest("/api/admin/users/u-2", { isAdmin: true }),
        routeParams(bea.id),
      );
      expect(promote.status).toBe(200);
      expect((await promote.json()).user.isAdmin).toBe(true);

      const demote = await patchUser(
        jsonRequest("/api/admin/users/u-2", { isAdmin: false }),
        routeParams("u-2"),
      );
      expect(demote.status).toBe(200);
      expect((await demote.json()).user.isAdmin).toBe(false);
    });

    it("no permite que te quites el rol a ti mismo (400)", async () => {
      const admin = await seedAdmin();
      const res = await patchUser(
        jsonRequest("/api/admin/users/self.id", { isAdmin: false }),
        routeParams(admin.id),
      );
      expect(res.status).toBe(400);
      expect(await res.json()).toEqual({
        error: "No puedes quitarte el rol de administrador a ti mismo",
      });
    });

    it("exige al menos un cambio (400)", async () => {
      await seedAdmin();
      const res = await patchUser(jsonRequest("/api/admin/users/u-2", {}), routeParams("u-2"));
      expect(res.status).toBe(400);
      expect(await res.json()).toEqual({ error: "No hay cambios que aplicar" });
    });
  });

  describe("DELETE /api/admin/users/[id]", () => {
    it("borra a otro usuario", async () => {
      await seedAdmin();
      await seedUser(world!, "ana", OTHER_PASS);
      expect(world!.data.users.size).toBe(2);

      const res = await deleteUser(undefined as unknown as Request, routeParams("u-2"));
      expect(res.status).toBe(200);
      expect(await res.json()).toEqual({ ok: true });
      expect(world!.data.users.size).toBe(1);
      expect(await world!.repositories.users.findByUsername("ana")).toBeNull();
    });

    it("no permite borrarte a ti mismo (400)", async () => {
      const admin = await seedAdmin();
      const res = await deleteUser(undefined as unknown as Request, routeParams(admin.id));
      expect(res.status).toBe(400);
      expect(await res.json()).toEqual({ error: "No puedes borrarte a ti mismo" });
    });

    it("devuelve 404 si el usuario no existe", async () => {
      await seedAdmin();
      const res = await deleteUser(undefined as unknown as Request, routeParams("no-existe"));
      expect(res.status).toBe(404);
    });
  });
});
import assert from "node:assert/strict";
import test from "node:test";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { once } from "node:events";
import { MODULE_PATHS } from "../src/lib/policy";

test("production login, server token proxy, all module pages, public pages and logout", {
  skip: !existsSync(".next/BUILD_ID"), timeout: 90_000,
}, async () => {
  const mock = createServer(async (request, response) => {
    response.setHeader("Content-Type", "application/json");
    const url = new URL(request.url || "/", "http://backend");
    if (url.pathname === "/api/v1/auth/login") {
      let body = "";
      for await (const chunk of request) body += chunk;
      const credentials = JSON.parse(body);
      if (credentials.username !== "fixture" || credentials.password !== "fixture-password") {
        response.writeHead(401); response.end('{"error":"Invalid credentials"}'); return;
      }
      response.end(JSON.stringify({ token: "private-fixture-token", fullName: "Fixture User", roleName: "Administrátor" })); return;
    }
    if (url.pathname === "/api/v1/website/pages/public") {
      if (url.searchParams.get("slug") !== "/public-test") { response.writeHead(404); response.end("{}"); return; }
      response.end('{"title":"Public fixture","content":"<script>unsafe</script>"}'); return;
    }
    if (url.pathname === "/api/v1/settings/public") { response.end('{"deliveryFee":100}'); return; }
    if (url.pathname === "/api/v1/catalog/products") { response.end("[]"); return; }
    if (url.pathname === "/api/v1/website/pages/visit") { response.end("{}"); return; }
    if (request.headers.authorization !== "Bearer private-fixture-token") {
      response.writeHead(401); response.end("{}"); return;
    }
    if (url.pathname === "/api/v1/auth/me") { response.end('{"administrator":true,"modules":["planning"]}'); return; }
    if (url.pathname === "/api/v1/planning/roles") { response.end('["Driver","Planner"]'); return; }
    if (url.pathname === "/api/v1/accounting/invoices/test/pdf") {
      response.setHeader("Content-Type", "application/pdf");
      response.setHeader("Content-Disposition", "attachment; filename=test.pdf");
      response.end("%PDF-fixture"); return;
    }
    response.end("{}");
  });
  mock.listen(0, "127.0.0.1");
  await once(mock, "listening");
  const backendAddress = mock.address();
  assert.ok(backendAddress && typeof backendAddress === "object");
  const portProbe = createServer();
  portProbe.listen(0, "127.0.0.1");
  await once(portProbe, "listening");
  const address = portProbe.address();
  assert.ok(address && typeof address === "object");
  const port = address.port;
  await new Promise<void>((resolve) => portProbe.close(() => resolve()));
  const dir = await mkdtemp(path.join(tmpdir(), "erp-next-runtime-test-"));
  const server = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", String(port)], {
    env: { ...process.env, SESSION_DIR: dir, BACKEND_URL: `http://127.0.0.1:${backendAddress.port}` },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let output = "";
  server.stdout.on("data", (chunk) => { output += chunk.toString(); });
  server.stderr.on("data", (chunk) => { output += chunk.toString(); });
  const base = `http://127.0.0.1:${port}`;
  try {
    let ready = false;
    for (let i = 0; i < 100; i++) {
      try { ready = (await fetch(`${base}/login`)).ok; } catch { /* Wait for server startup. */ }
      if (ready) break;
      await new Promise((resolve) => setTimeout(resolve, 200));
    }
    assert.ok(ready, output);
    const privatePage = await fetch(`${base}/planning`, { redirect: "manual" });
    assert.equal(privatePage.status, 307);
    const badOrigin = await fetch(`${base}/api/auth/login`, { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "fixture", password: "fixture-password" }) });
    assert.equal(badOrigin.status, 403);
    const login = await fetch(`${base}/api/auth/login`, { method: "POST",
      headers: { Origin: base, "Content-Type": "application/json" },
      body: JSON.stringify({ username: "fixture", password: "fixture-password" }) });
    assert.equal(login.status, 200);
    const cookie = login.headers.get("set-cookie")?.split(";")[0];
    assert.ok(cookie);
    assert.ok(login.headers.get("set-cookie")?.includes("HttpOnly"));
    assert.ok(!cookie.includes("private-fixture-token"));
    assert.ok(!(await login.text()).includes("private-fixture-token"));
    const headers = { Cookie: cookie };
    const me = await fetch(`${base}/api/backend/auth/me`, { headers });
    assert.equal(me.status, 200);
    const roles = await fetch(`${base}/api/backend/planning/roles`, { headers });
    assert.deepEqual(await roles.json(), ["Driver", "Planner"]);
    assert.equal((await fetch(`${base}/api/backend/settings`)).status, 401);
    assert.equal((await fetch(`${base}/api/backend/settings/public`)).status, 200);
    for (const module of MODULE_PATHS) {
      const page = await fetch(`${base}/${module}`, { headers, redirect: "manual" });
      const html = await page.text();
      assert.equal(page.status, 200, `${module}: ${html}`);
      if (module === "planning") {
        assert.match(html, /href="\/assets\/base\.css\?v=20261008-1"/);
        assert.match(html, /href="\/assets\/planning\.css\?v=20261008-1"/);
        assert.match(html, /href="\/assets\/workforce\.css\?v=20261008-1"/);
      }
    }
    const pdf = await fetch(`${base}/api/backend/accounting/invoices/test/pdf`, { headers });
    assert.equal(pdf.headers.get("content-type"), "application/pdf");
    assert.equal(pdf.headers.get("content-disposition"), "attachment; filename=test.pdf");
    assert.equal((await fetch(`${base}/eshop`)).status, 200);
    assert.equal((await fetch(`${base}/unknown-page`)).status, 404);
    const published = await fetch(`${base}/public-test`);
    assert.equal(published.status, 200);
    assert.ok(!(await published.text()).includes("<script>unsafe</script>"));
    const logout = await fetch(`${base}/api/auth/logout`, { method: "POST", headers: { ...headers, Origin: base } });
    assert.equal(logout.status, 200);
    assert.equal((await fetch(`${base}/api/backend/auth/me`, { headers })).status, 401);
  } finally {
    server.kill("SIGTERM");
    if (server.exitCode === null) await once(server, "exit");
    await new Promise<void>((resolve) => mock.close(() => resolve()));
    await rm(dir, { recursive: true });
  }
});

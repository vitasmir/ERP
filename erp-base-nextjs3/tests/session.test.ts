import assert from "node:assert/strict";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import {
  destroySession, expired, IDLE_SESSION_AGE, MAX_SESSION_AGE, newSession, newSessionId,
  readSession, saveSession, sessionCookie, sessionId, withSessionLock,
} from "../src/lib/session";
import { sameOrigin } from "../src/lib/server";

test("sessions persist opaque tokens and guest carts with login idle and absolute expiry", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "erp-nextjs3-session-"));
  const previous = process.env.SESSION_DIR;
  process.env.SESSION_DIR = directory;
  try {
    const id = newSessionId();
    assert.match(id, /^[a-f0-9]{64}$/);
    const session = newSession();
    session.cart["11111111-1111-1111-1111-111111111111"] = 2;
    await saveSession(id, session);
    const restored = await readSession(id);
    assert.ok(restored);
    assert.deepEqual(restored.cart, session.cart);
    assert.equal(restored.expiresAt, session.expiresAt);
    assert.equal(restored.lastActivityAt, session.lastActivityAt);
    assert.equal(restored.token, undefined);
    const filename = (await readdir(directory))[0];
    assert.ok(!filename.includes(id));
    assert.match(await readFile(path.join(directory, filename), "utf8"), /"cart"/);
    session.token = "synthetic-private-token";
    const now = Date.now();
    session.lastActivityAt = now;
    session.expiresAt = now + MAX_SESSION_AGE;
    assert.equal(expired(session, now + IDLE_SESSION_AGE - 1), false);
    assert.equal(expired(session, now + IDLE_SESSION_AGE), true);
    session.lastActivityAt = now + MAX_SESSION_AGE - 1;
    assert.equal(expired(session, now + MAX_SESSION_AGE), true);
    session.expiresAt = now - 1;
    await saveSession(id, session);
    assert.equal(await readSession(id), null);
    assert.equal((await readdir(directory)).length, 0);
    await destroySession(id);
  } finally {
    if (previous === undefined) delete process.env.SESSION_DIR; else process.env.SESSION_DIR = previous;
    await rm(directory, { recursive: true });
  }
});

test("same-session requests serialize instead of losing cart updates", async () => {
  const order: string[] = [];
  await Promise.all([
    withSessionLock("fixture", async () => {
      order.push("first-start");
      await new Promise((resolve) => setTimeout(resolve, 20));
      order.push("first-end");
    }),
    withSessionLock("fixture", async () => { order.push("second"); }),
  ]);
  assert.deepEqual(order, ["first-start", "first-end", "second"]);
});

test("cookies are HttpOnly and paths cannot become session filenames", () => {
  const id = newSessionId();
  assert.match(sessionCookie(id), /HttpOnly; SameSite=Lax/);
  assert.equal(sessionId(new Request("http://localhost", { headers: { Cookie: `erp_nextjs3_session=${id}` } })), id);
  assert.equal(sessionId(new Request("http://localhost", { headers: { Cookie: "erp_nextjs3_session=../../etc/passwd" } })), undefined);
});

test("mutations require matching origin protocol, host and published port", () => {
  const request = (origin: string) => new Request("http://localhost:3000/eshop", { headers: { Origin: origin } });
  assert.equal(sameOrigin(request("http://localhost:3000")), true);
  assert.equal(sameOrigin(request("http://localhost:4201")), false);
  assert.equal(sameOrigin(request("https://localhost:3000")), false);
  assert.equal(sameOrigin(request("http://evil.invalid:3000")), false);
  assert.equal(sameOrigin(new Request("http://localhost:3000")), false);
  assert.equal(sameOrigin(new Request("http://localhost:3000", { headers: { Referer: "http://localhost:3000/eshop" } })), true);
});

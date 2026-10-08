import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, rm, readdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { createSession, readSession, destroySession, isExpired, IDLE_SESSION_AGE, MAX_SESSION_AGE } from "../src/lib/session-store";

test("server-side session has opaque identifier, sliding idle and absolute limits", async () => {
  const dir = await mkdtemp(path.join(tmpdir(), "erp-next-session-test-"));
  const previous = process.env.SESSION_DIR;
  process.env.SESSION_DIR = dir;
  try {
    const id = await createSession({ token: "fixture-backend-token", userName: "Test User", roleName: "Administrator" });
    assert.match(id, /^[a-f0-9]{64}$/);
    assert.equal(id.includes("fixture-backend-token"), false);
    const session = await readSession(id);
    assert.ok(session);
    assert.equal(session.token, "fixture-backend-token");
    assert.equal(session.expiresAt - session.lastActivityAt, MAX_SESSION_AGE * 1000);
    assert.equal(isExpired(session, session.lastActivityAt + IDLE_SESSION_AGE * 1000), true);
    assert.equal(isExpired({ ...session, lastActivityAt: session.expiresAt }, session.expiresAt), true);
    assert.ok(await readSession(id, true));
    assert.equal(await readSession("../../etc/passwd"), null);
    await destroySession(id);
    assert.equal(await readSession(id), null);
    assert.deepEqual(await readdir(dir), []);
  } finally {
    if (previous === undefined) delete process.env.SESSION_DIR; else process.env.SESSION_DIR = previous;
    await rm(dir, { recursive: true });
  }
});

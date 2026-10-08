import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";

test("PHP CSS and JavaScript assets are used byte-for-byte", async () => {
  const source = path.resolve("../erp-base-php/public/assets");
  const files = await readdir(source);
  assert.ok(files.length > 0);
  for (const filename of files) {
    assert.deepEqual(
      await readFile(path.join("public/assets", filename)),
      await readFile(path.join(source, filename)),
      filename,
    );
  }
});

test("the frontend contains no Twig templates, template engine or Twig dependency", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8")) as {
    dependencies: Record<string, string>;
  };
  assert.ok(!Object.hasOwn(packageJson.dependencies, "twig"));
  for (const directory of ["src", "public"]) {
    const files = await readdir(directory, { recursive: true });
    assert.ok(!files.some((file) => String(file).endsWith(".twig")), directory);
  }
});

test("role marks use each role card's saved color", async () => {
  const styles = await readFile("public/assets/roles.css", "utf8");
  assert.match(styles, /\.role-mark\.custom\s*\{\s*background:\s*var\(--card-color,\s*#d9ed62\);/);
});

test("user avatars use each user's saved color", async () => {
  const styles = await readFile("public/assets/base.css", "utf8");
  assert.match(styles, /\.table-avatar\s*\{\s*display:\s*inline-grid;[\s\S]*?background:\s*var\(--card-color,\s*#dce9c7\);/);
});

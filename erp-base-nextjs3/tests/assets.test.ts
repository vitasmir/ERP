import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";

test("PHP assets are unchanged except for the proven Next.js calendar dialog fix", async () => {
  const source = path.resolve("../erp-base-php/public/assets");
  const files = await readdir(source);
  assert.ok(files.length > 0);
  for (const filename of files) {
    assert.deepEqual(
      await readFile(path.join("public/assets", filename)),
      await readFile(path.join(filename === "base.js" ? "../erp-base-nextjs2/public/assets" : source, filename)),
      filename,
    );
  }
});

test("the calendar opens in the native modal layer and Escape closes only the calendar", async () => {
  const script = await readFile("public/assets/base.js", "utf8");
  const styles = await readFile("public/compatibility.css", "utf8");
  assert.match(script, /const datePickerModal = document\.createElement\("dialog"\)/);
  assert.match(script, /datePickerModal\.showModal\(\)/);
  assert.match(script, /datePickerModal\.close\(\)/);
  assert.match(script, /datePickerModal\.addEventListener\("cancel", \(event\) => \{\s*event\.preventDefault\(\);\s*closeDatePicker\(\);/);
  assert.match(script, /event\.key === "Escape" && datePickerModal\.open\) \{\s*event\.preventDefault\(\);\s*closeDatePicker\(\);/);
  assert.match(styles, /dialog\.date-picker-modal \{[^}]*width: 100%;[^}]*height: 100%;[^}]*margin: 0;[^}]*padding: 0;[^}]*border: 0;[^}]*background: transparent;/);
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

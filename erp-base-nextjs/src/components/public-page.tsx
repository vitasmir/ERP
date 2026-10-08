import { notFound } from "next/navigation";
import { backendFetch } from "@/lib/backend";
import { record, text, type Json } from "@/lib/data";

export async function PublicPage({ slug }: { slug: string }) {
  let page;
  let missing = false;
  try {
    const response = await backendFetch(`website/pages/public?slug=${encodeURIComponent(slug)}`);
    missing = response.status === 404;
    if (!missing) {
      if (!response.ok) throw new Error(`Website backend returned ${response.status}.`);
      page = record(await response.json() as Json);
    }
  } catch (error) {
    console.error("Could not load public page.", slug, error);
    throw new Error("Stránku nelze načíst. Backend není dostupný.");
  }
  if (missing || !page) notFound();
  let visitError = "";
  try {
    const visit = await backendFetch(`website/pages/visit?slug=${encodeURIComponent(slug)}`, { method: "POST" });
    if (!visit.ok) throw new Error(`Visit tracking returned ${visit.status}.`);
  } catch (error) {
    console.error("Could not record website visit.", slug, error);
    visitError = "Návštěvu stránky se nepodařilo zaznamenat.";
  }
  return <main className="public-page"><nav><a href="/eshop">E-shop</a> · <a href="/login">ERP</a></nav>
    <h1>{text(page.title)}</h1>{visitError && <p role="alert">{visitError}</p>}<p className="page-content">{text(page.content)}</p>
    {page.hasContactForm === true && <p>Pro kontakt využijte zákaznickou podporu.</p>}</main>;
}

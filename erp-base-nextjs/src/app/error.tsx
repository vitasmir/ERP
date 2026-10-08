"use client";

export default function ErrorPage({ reset }: { reset: () => void }) {
  return <main className="public-page"><h1>Stránku nelze zobrazit</h1>
    <p role="alert">Při načítání došlo k chybě. Zkuste stránku načíst znovu.</p>
    <button onClick={reset}>Zkusit znovu</button><a href="/apps">Aplikace</a></main>;
}

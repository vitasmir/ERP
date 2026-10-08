import { PublicPage } from "@/components/public-page";

export default async function Page({ params }: { params: Promise<{ slug: string[] }> }) {
  return <><link rel="stylesheet" href="/assets/website.css?v=20261008-1" />
    <PublicPage slug={`/${(await params).slug.join("/")}`} /></>;
}

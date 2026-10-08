import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = { title: "Retail ERP", description: "Retail ERP – Next.js frontend" };

export default function RootLayout({ children }: { children: ReactNode }) {
  return <html lang="cs"><head>
    <link rel="stylesheet" href="/assets/base.css?v=20261008-1" />
  </head><body>{children}</body></html>;
}

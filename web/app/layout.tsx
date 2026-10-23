import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Header } from "@/components/header";
import Link from "next/link";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "Eleições 2026 · Dados para acompanhar",
    template: "%s · Eleições 2026",
  },
  description:
    "Acompanhe pesquisas eleitorais e entenda os dados do segundo turno presidencial de 2026.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <Header />
        <main className="site-main">{children}</main>
        <footer className="site-footer">
          <span>
            Eleições 2026 <span className="footer-dot">·</span> Dados para
            acompanhar, contexto para entender.
          </span>
          <Link href="/metodologia">Transparência e metodologia ↗</Link>
        </footer>
      </body>
    </html>
  );
}

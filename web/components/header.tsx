"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChartNoAxesColumnIncreasing, ArrowUpRight } from "lucide-react";

const links = [
  ["/", "Visão geral"],
  ["/pesquisas", "Pesquisas"],
  ["/metodologia", "Metodologia"],
  ["/apuracao", "Apuração"],
];
export function Header() {
  const pathname = usePathname();
  return (
    <header className="site-header">
      <div className="header-inner">
        <Link href="/" className="brand">
          <span className="brand-icon">
            <ChartNoAxesColumnIncreasing size={21} />
          </span>
          Eleições <span className="text-muted-foreground">2026</span>
        </Link>
        <nav aria-label="Navegação principal">
          {links.map(([href, title]) => (
            <Link
              key={href}
              href={href}
              aria-current={pathname === href ? "page" : undefined}
              className={pathname === href ? "active" : ""}
            >
              {title}
            </Link>
          ))}
        </nav>
        <Link href="/admin/pesquisas" className="admin-link">
          Cadastrar pesquisa <ArrowUpRight size={15} />
        </Link>
      </div>
    </header>
  );
}

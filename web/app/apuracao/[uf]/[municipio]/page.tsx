import { notFound } from "next/navigation";
import { OfficialDashboard } from "@/components/official-dashboard";
export default async function MunicipalityPage({ params }: { params: Promise<{ uf: string; municipio: string }> }) {
 const { uf, municipio } = await params;
 if (!"ac al ap am ba ce df es go ma mt ms mg pa pb pr pe pi rj rn rs ro rr sc sp se to".split(" ").includes(uf.toLowerCase()) || !/^\d{5}$/.test(municipio)) notFound();
 return <OfficialDashboard uf={uf.toLowerCase()} municipality={municipio} />;
}

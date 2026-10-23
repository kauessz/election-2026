import { notFound } from "next/navigation";
import { OfficialDashboard } from "@/components/official-dashboard";
const states = new Set("ac al ap am ba ce df es go ma mt ms mg pa pb pr pe pi rj rn rs ro rr sc sp se to".split(" "));
export default async function StatePage({ params }: { params: Promise<{ uf: string }> }) {
 const { uf } = await params;
 if (!states.has(uf.toLowerCase())) notFound();
 return <OfficialDashboard uf={uf.toLowerCase()} />;
}

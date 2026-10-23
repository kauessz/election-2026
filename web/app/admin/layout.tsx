import { notFound } from "next/navigation";

export const dynamic = "force-dynamic";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  if (process.env.ADMIN_UI_ENABLED !== "true") notFound();
  return children;
}

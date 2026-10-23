import { Skeleton } from "@/components/ui/skeleton";
export default function Loading() {
  return (
    <div
      className="space-y-8 py-16"
      role="status"
      aria-label="Carregando página"
    >
      <Skeleton className="h-12 w-2/3" />
      <Skeleton className="h-5 w-1/2" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

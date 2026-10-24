/** Presentation only: never use formatted strings as inputs to calculations. */
export const publicNumber = (value: number) =>
  value.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const publicPercent = (value: number) => `${publicNumber(value)}%`;
export const modelNumber = (value: number) =>
  value.toLocaleString("pt-BR", { maximumFractionDigits: 4 });
export const modelPercent = (value: number) => `${modelNumber(value)}%`;

export function updatedTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo",
      }).format(new Date(value)).replace(",", "")
    : "—";
}

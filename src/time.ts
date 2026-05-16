export const SHANGHAI_TIME_ZONE = "Asia/Shanghai";

function dateTimePart(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes) {
  return parts.find((part) => part.type === type)?.value ?? "00";
}

export function formatShanghaiTime(ms: number): string {
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: SHANGHAI_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    hourCycle: "h23",
  }).formatToParts(new Date(ms));

  return `${dateTimePart(parts, "hour")}:${dateTimePart(parts, "minute")}:${dateTimePart(parts, "second")}`;
}

export function formatShanghaiDateTime(ms: number): string {
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: SHANGHAI_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    hourCycle: "h23",
  }).formatToParts(new Date(ms));

  return [
    `${dateTimePart(parts, "year")}-${dateTimePart(parts, "month")}-${dateTimePart(parts, "day")}`,
    `${dateTimePart(parts, "hour")}:${dateTimePart(parts, "minute")}:${dateTimePart(parts, "second")}`,
  ].join(" ");
}

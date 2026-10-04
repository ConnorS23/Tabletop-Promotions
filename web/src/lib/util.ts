import type { Device } from "./types";

export const MEDIA_BUCKET = "media";

/** Devices sync every 30s; treat anything seen in the last 2 minutes as online. */
export function isOnline(d: Pick<Device, "last_seen_at">) {
  return !!d.last_seen_at && Date.now() - new Date(d.last_seen_at).getTime() < 2 * 60 * 1000;
}

export function timeAgo(iso: string | null) {
  if (!iso) return "never";
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.round(s / 60)}m ago`;
  if (s < 86400) return `${Math.round(s / 3600)}h ago`;
  return `${Math.round(s / 86400)}d ago`;
}

export function fileExt(name: string) {
  const i = name.lastIndexOf(".");
  return i >= 0 ? name.slice(i + 1).toLowerCase() : "bin";
}

export function errorMessage(e: unknown) {
  if (e && typeof e === "object" && "message" in e) return String((e as { message: unknown }).message);
  return String(e);
}

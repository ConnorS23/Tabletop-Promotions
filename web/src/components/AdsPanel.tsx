"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Ad, Restaurant } from "@/lib/types";
import { MEDIA_BUCKET, errorMessage, fileExt } from "@/lib/util";
import { Alert, Badge, Button, Card, Input } from "./ui";

function toLocalInput(iso: string | null) {
  if (!iso) return "";
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export default function AdsPanel({ restaurant }: { restaurant: Restaurant }) {
  const supabase = createClient();
  const [ads, setAds] = useState<Ad[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    const { data, error } = await supabase.from("ads").select("*").eq("restaurant_id", restaurant.id).order("position").order("created_at");
    if (error) setError(error.message);
    setAds((data ?? []) as Ad[]);
    setLoading(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restaurant.id]);

  useEffect(() => {
    load();
  }, [load]);

  const publicUrl = (path: string) => supabase.storage.from(MEDIA_BUCKET).getPublicUrl(path).data.publicUrl;

  async function upload(files: FileList | null) {
    if (!files?.length) return;
    setError(null);
    let position = ads.reduce((m, a) => Math.max(m, a.position), 0);
    try {
      for (const file of Array.from(files)) {
        const isVideo = file.type.startsWith("video/");
        if (!isVideo && !file.type.startsWith("image/")) throw new Error(`${file.name} is not an image or video`);
        setUploading(file.name);
        const path = `${restaurant.id}/ads/${crypto.randomUUID()}.${fileExt(file.name)}`;
        const { error: upErr } = await supabase.storage.from(MEDIA_BUCKET).upload(path, file, { contentType: file.type });
        if (upErr) throw upErr;
        const { error: insErr } = await supabase.from("ads").insert({
          restaurant_id: restaurant.id,
          title: file.name.replace(/\.[^.]+$/, ""),
          storage_path: path,
          media_type: isVideo ? "video" : "image",
          position: ++position,
        });
        if (insErr) throw insErr;
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setUploading(null);
      if (fileRef.current) fileRef.current.value = "";
      load();
    }
  }

  async function update(id: string, patch: Partial<Ad>) {
    setAds((prev) => prev.map((a) => (a.id === id ? { ...a, ...patch } : a)));
    const { error } = await supabase.from("ads").update(patch).eq("id", id);
    if (error) {
      setError(error.message);
      load();
    }
  }

  async function move(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= ads.length) return;
    const reordered = [...ads];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    setAds(reordered);
    const results = await Promise.all(reordered.map((a, i) => supabase.from("ads").update({ position: i + 1 }).eq("id", a.id)));
    const failed = results.find((r) => r.error);
    if (failed?.error) setError(failed.error.message);
    load();
  }

  async function remove(ad: Ad) {
    if (!confirm(`Delete "${ad.title || "this ad"}"?`)) return;
    const { error } = await supabase.from("ads").delete().eq("id", ad.id);
    if (error) return setError(error.message);
    await supabase.storage.from(MEDIA_BUCKET).remove([ad.storage_path]);
    load();
  }

  return (
    <Card
      title={`Advertisements (${ads.length})`}
      actions={
        <>
          <input ref={fileRef} type="file" accept="image/*,video/*" multiple className="hidden" onChange={(e) => upload(e.target.files)} />
          <Button onClick={() => fileRef.current?.click()} disabled={!!uploading}>
            {uploading ? `Uploading ${uploading}…` : "+ Upload ads"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <p className="text-sm text-gray-500">
          Ads play in this order on every display. Images show for {restaurant.slide_seconds}s by default (change in Settings); videos play to
          the end unless you set a duration. Landscape 16:10 or 16:9 images look best.
        </p>
        {loading ? (
          <p className="text-sm text-gray-400">Loading…</p>
        ) : ads.length === 0 ? (
          <div className="rounded-lg border border-dashed border-gray-300 p-10 text-center text-sm text-gray-500">No ads yet — upload images or videos.</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {ads.map((ad, i) => (
              <li key={ad.id} className="flex flex-wrap items-center gap-4 py-3">
                <div className="flex flex-col">
                  <button className="px-1 text-gray-400 hover:text-gray-800 disabled:opacity-30" disabled={i === 0} onClick={() => move(i, -1)} title="Move up">
                    ▲
                  </button>
                  <button className="px-1 text-gray-400 hover:text-gray-800 disabled:opacity-30" disabled={i === ads.length - 1} onClick={() => move(i, 1)} title="Move down">
                    ▼
                  </button>
                </div>
                <div className="h-20 w-32 shrink-0 overflow-hidden rounded-md bg-gray-900">
                  {ad.media_type === "video" ? (
                    <video src={publicUrl(ad.storage_path)} className="h-full w-full object-contain" muted />
                  ) : (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={publicUrl(ad.storage_path)} alt={ad.title} className="h-full w-full object-contain" />
                  )}
                </div>
                <div className="min-w-48 flex-1 space-y-2">
                  <div className="flex items-center gap-2">
                    <Input defaultValue={ad.title} onBlur={(e) => e.target.value !== ad.title && update(ad.id, { title: e.target.value })} className="max-w-xs" />
                    <Badge color={ad.media_type === "video" ? "blue" : "gray"}>{ad.media_type}</Badge>
                    {!ad.active && <Badge color="orange">paused</Badge>}
                  </div>
                  <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500">
                    <label className="flex items-center gap-1">
                      Duration
                      <Input
                        type="number"
                        min={1}
                        max={600}
                        placeholder={ad.media_type === "video" ? "full" : String(restaurant.slide_seconds)}
                        defaultValue={ad.duration_seconds ?? ""}
                        onBlur={(e) => {
                          const v = e.target.value ? Number(e.target.value) : null;
                          if (v !== ad.duration_seconds) update(ad.id, { duration_seconds: v });
                        }}
                        className="w-20 py-1"
                      />
                      s
                    </label>
                    <label className="flex items-center gap-1">
                      From
                      <Input
                        type="datetime-local"
                        defaultValue={toLocalInput(ad.starts_at)}
                        onBlur={(e) => update(ad.id, { starts_at: e.target.value ? new Date(e.target.value).toISOString() : null })}
                        className="w-auto py-1"
                      />
                    </label>
                    <label className="flex items-center gap-1">
                      Until
                      <Input
                        type="datetime-local"
                        defaultValue={toLocalInput(ad.ends_at)}
                        onBlur={(e) => update(ad.id, { ends_at: e.target.value ? new Date(e.target.value).toISOString() : null })}
                        className="w-auto py-1"
                      />
                    </label>
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button variant="secondary" onClick={() => update(ad.id, { active: !ad.active })}>
                    {ad.active ? "Pause" : "Resume"}
                  </Button>
                  <Button variant="danger" onClick={() => remove(ad)}>
                    Delete
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}

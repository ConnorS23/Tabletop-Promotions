"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { MenuRow, Restaurant } from "@/lib/types";
import { MEDIA_BUCKET, errorMessage, timeAgo } from "@/lib/util";
import { Alert, Button, Card } from "./ui";

export default function MenuPanel({ restaurant }: { restaurant: Restaurant }) {
  const supabase = createClient();
  const [menu, setMenu] = useState<MenuRow | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    const { data } = await supabase.from("menus").select("*").eq("restaurant_id", restaurant.id).maybeSingle();
    setMenu(data as MenuRow | null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restaurant.id]);

  useEffect(() => {
    load();
  }, [load]);

  async function upload(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (file.type !== "application/pdf") return setError("Please choose a PDF file.");
    setBusy(true);
    try {
      const path = `${restaurant.id}/menu/${crypto.randomUUID()}.pdf`;
      const { error: upErr } = await supabase.storage.from(MEDIA_BUCKET).upload(path, file, { contentType: "application/pdf" });
      if (upErr) throw upErr;
      const { error: dbErr } = await supabase
        .from("menus")
        .upsert({ restaurant_id: restaurant.id, storage_path: path, file_name: file.name, updated_at: new Date().toISOString() });
      if (dbErr) throw dbErr;
      if (menu) await supabase.storage.from(MEDIA_BUCKET).remove([menu.storage_path]);
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function remove() {
    if (!menu || !confirm("Remove the menu from all displays?")) return;
    const { error } = await supabase.from("menus").delete().eq("restaurant_id", restaurant.id);
    if (error) return setError(error.message);
    await supabase.storage.from(MEDIA_BUCKET).remove([menu.storage_path]);
    load();
  }

  const url = menu ? supabase.storage.from(MEDIA_BUCKET).getPublicUrl(menu.storage_path).data.publicUrl : null;

  return (
    <Card
      title="PDF menu"
      actions={
        <>
          <input ref={fileRef} type="file" accept="application/pdf" className="hidden" onChange={(e) => upload(e.target.files?.[0])} />
          {menu && (
            <Button variant="danger" onClick={remove}>
              Remove
            </Button>
          )}
          <Button onClick={() => fileRef.current?.click()} disabled={busy}>
            {busy ? "Uploading…" : menu ? "Replace PDF" : "Upload PDF"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {menu && url ? (
          <>
            <p className="text-sm text-gray-500">
              <b className="text-gray-900">{menu.file_name}</b> · updated {timeAgo(menu.updated_at)} · guests can scroll and pinch-zoom it on
              the table display.
            </p>
            <iframe src={url} className="h-[600px] w-full rounded-lg border border-gray-200" title="Menu preview" />
          </>
        ) : (
          <div className="rounded-lg border border-dashed border-gray-300 p-10 text-center text-sm text-gray-500">No menu uploaded yet.</div>
        )}
      </div>
    </Card>
  );
}

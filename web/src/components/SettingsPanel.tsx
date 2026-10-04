"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import type { Restaurant } from "@/lib/types";
import { Alert, Button, Card, Input, Label } from "./ui";

export default function SettingsPanel({ restaurant, isSuperAdmin }: { restaurant: Restaurant; isSuperAdmin: boolean }) {
  const router = useRouter();
  const supabase = createClient();
  const [form, setForm] = useState({
    name: restaurant.name,
    settings_pin: restaurant.settings_pin,
    slide_seconds: restaurant.slide_seconds,
    idle_timeout_seconds: restaurant.idle_timeout_seconds,
  });
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setSaved(false);
    const { error } = await supabase.from("restaurants").update(form).eq("id", restaurant.id);
    setBusy(false);
    if (error) return setError(error.message);
    setSaved(true);
    router.refresh();
  }

  async function del() {
    if (!confirm(`Delete ${restaurant.name}, all its ads and its menu? Linked displays will return to pairing mode.`)) return;
    const { error } = await supabase.from("restaurants").delete().eq("id", restaurant.id);
    if (error) return setError(error.message);
    router.push("/dashboard");
  }

  return (
    <div className="space-y-6">
      <Card title="Display settings">
        <form onSubmit={save} className="max-w-xl space-y-4">
          {error && <Alert>{error}</Alert>}
          {saved && <Alert kind="success">Saved. Displays pick up changes on their next sync (within ~30s).</Alert>}
          <div>
            <Label>Restaurant name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </div>
          <div>
            <Label hint="(4–8 digits, staff use this to open the display's settings)">Settings PIN</Label>
            <Input
              value={form.settings_pin}
              onChange={(e) => setForm({ ...form, settings_pin: e.target.value.replace(/\D/g, "") })}
              pattern="[0-9]{4,8}"
              maxLength={8}
              inputMode="numeric"
              className="font-mono"
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label hint="(seconds)">Default time per image ad</Label>
              <Input type="number" min={2} max={120} value={form.slide_seconds} onChange={(e) => setForm({ ...form, slide_seconds: Number(e.target.value) })} />
            </div>
            <div>
              <Label hint="(seconds)">Return to ads after idle</Label>
              <Input
                type="number"
                min={10}
                max={3600}
                value={form.idle_timeout_seconds}
                onChange={(e) => setForm({ ...form, idle_timeout_seconds: Number(e.target.value) })}
              />
            </div>
          </div>
          <Button type="submit" disabled={busy}>
            {busy ? "Saving…" : "Save settings"}
          </Button>
        </form>
      </Card>
      {isSuperAdmin && (
        <Card title="Danger zone">
          <Button variant="danger" onClick={del}>
            Delete restaurant
          </Button>
        </Card>
      )}
    </div>
  );
}

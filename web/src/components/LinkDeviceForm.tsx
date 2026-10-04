"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Restaurant } from "@/lib/types";
import { Alert, Button, Input, Label, Select } from "./ui";

export default function LinkDeviceForm({
  initialCode = "",
  fixedRestaurantId,
  onLinked,
}: {
  initialCode?: string;
  fixedRestaurantId?: string;
  onLinked?: (restaurantId: string) => void;
}) {
  const supabase = createClient();
  const [code, setCode] = useState(initialCode.toUpperCase());
  const [name, setName] = useState("");
  const [restaurantId, setRestaurantId] = useState(fixedRestaurantId ?? "");
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (fixedRestaurantId) return;
    supabase
      .from("restaurants")
      .select("*")
      .order("name")
      .then(({ data }) => {
        const list = (data ?? []) as Restaurant[];
        setRestaurants(list);
        if (list.length === 1) setRestaurantId(list[0].id);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fixedRestaurantId]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setSuccess(null);
    const { error } = await supabase.rpc("link_device", {
      p_pair_code: code.trim().toUpperCase(),
      p_restaurant_id: restaurantId,
      p_name: name.trim() || null,
    });
    setBusy(false);
    if (error) return setError(error.message);
    setSuccess(`Display ${code.toUpperCase()} linked! It will start showing your ads within a few seconds.`);
    setCode("");
    setName("");
    onLinked?.(restaurantId);
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      {error && <Alert>{error}</Alert>}
      {success && <Alert kind="success">{success}</Alert>}
      <div className="grid gap-4 sm:grid-cols-3">
        <div>
          <Label hint="(shown on the display)">Pairing code</Label>
          <Input
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="ABC123"
            maxLength={6}
            className="font-mono tracking-widest uppercase"
            required
          />
        </div>
        <div>
          <Label hint="(optional)">Display name</Label>
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Table 4" />
        </div>
        {!fixedRestaurantId && (
          <div>
            <Label>Restaurant</Label>
            <Select value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)} required>
              <option value="" disabled>
                Choose…
              </option>
              {restaurants.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </Select>
          </div>
        )}
      </div>
      <Button type="submit" disabled={busy || code.trim().length < 6 || !restaurantId}>
        {busy ? "Linking…" : "Link display"}
      </Button>
    </form>
  );
}

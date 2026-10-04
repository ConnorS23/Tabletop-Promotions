"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import { Alert, Button, Input } from "@/components/ui";

export default function CreateRestaurant() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { data, error } = await createClient().from("restaurants").insert({ name: name.trim() }).select("id").single();
    setBusy(false);
    if (error) return setError(error.message);
    router.push(`/restaurants/${data.id}`);
  }

  if (!open) return <Button onClick={() => setOpen(true)}>+ New restaurant</Button>;

  return (
    <form onSubmit={create} className="flex flex-wrap items-center gap-2">
      {error && <Alert>{error}</Alert>}
      <Input autoFocus placeholder="Restaurant name" value={name} onChange={(e) => setName(e.target.value)} required className="w-64" />
      <Button type="submit" disabled={busy}>
        Create
      </Button>
      <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
        Cancel
      </Button>
    </form>
  );
}

"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Profile } from "@/lib/types";
import { Alert, Badge, Button, Card, Input } from "./ui";

export default function ManagersPanel({ restaurantId }: { restaurantId: string }) {
  const supabase = createClient();
  const [members, setMembers] = useState<Profile[]>([]);
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const { data, error } = await supabase.from("restaurant_members").select("user_id, profiles(*)").eq("restaurant_id", restaurantId);
    if (error) setError(error.message);
    setMembers(((data ?? []) as unknown as { profiles: Profile }[]).map((m) => m.profiles).filter(Boolean));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restaurantId]);

  useEffect(() => {
    load();
  }, [load]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const { error } = await supabase.rpc("add_member_by_email", { p_restaurant_id: restaurantId, p_email: email });
    if (error) return setError(error.message);
    setEmail("");
    load();
  }

  async function remove(p: Profile) {
    if (!confirm(`Remove ${p.email} from this restaurant?`)) return;
    const { error } = await supabase.from("restaurant_members").delete().eq("restaurant_id", restaurantId).eq("user_id", p.id);
    if (error) return setError(error.message);
    load();
  }

  return (
    <Card title="Managers">
      <div className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <ul className="divide-y divide-gray-100">
          {members.map((m) => (
            <li key={m.id} className="flex items-center justify-between py-2.5">
              <div>
                <div className="font-medium">{m.full_name || m.email}</div>
                <div className="text-sm text-gray-500">{m.email}</div>
              </div>
              <div className="flex items-center gap-3">
                {m.role === "super_admin" && <Badge color="orange">super admin</Badge>}
                <Button variant="ghost" onClick={() => remove(m)}>
                  Remove
                </Button>
              </div>
            </li>
          ))}
        </ul>
        <form onSubmit={add} className="flex max-w-md gap-2">
          <Input type="email" placeholder="manager@email.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Button type="submit">Add manager</Button>
        </form>
        <p className="text-xs text-gray-400">The person must create an account first (Sign up on the login page).</p>
      </div>
    </Card>
  );
}

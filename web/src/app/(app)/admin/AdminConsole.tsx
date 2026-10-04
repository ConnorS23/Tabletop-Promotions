"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import type { Profile, Restaurant, Role } from "@/lib/types";
import { Alert, Badge, Button, Card, Input, Label, Select } from "@/components/ui";
import DevicesPanel from "@/components/DevicesPanel";

const TABS = ["Displays", "Restaurants", "Users", "Platform"] as const;
type Tab = (typeof TABS)[number];

export default function AdminConsole({
  restaurants,
  users,
  masterPin,
  currentUserId,
}: {
  restaurants: Restaurant[];
  users: Profile[];
  masterPin: string;
  currentUserId: string;
}) {
  const router = useRouter();
  const supabase = createClient();
  const [tab, setTab] = useState<Tab>("Displays");
  const [pin, setPin] = useState(masterPin);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function setRole(user: Profile, role: Role) {
    setError(null);
    const { error } = await supabase.rpc("set_user_role", { p_user_id: user.id, p_role: role });
    if (error) return setError(error.message);
    router.refresh();
  }

  async function savePin(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setNotice(null);
    const { error } = await supabase.from("app_settings").update({ master_pin: pin, updated_at: new Date().toISOString() }).eq("id", 1);
    if (error) return setError(error.message);
    setNotice("Master PIN saved. Displays pick it up on their next sync.");
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Super admin</h1>
        <p className="text-sm text-gray-500">Manage every display, restaurant and user on the platform.</p>
      </div>
      <div className="flex gap-1 border-b border-gray-200">
        {TABS.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              tab === t ? "border-orange-500 text-orange-600" : "border-transparent text-gray-500 hover:text-gray-800"
            }`}
          >
            {t}
          </button>
        ))}
      </div>
      {error && <Alert>{error}</Alert>}
      {notice && <Alert kind="success">{notice}</Alert>}

      {tab === "Displays" && <DevicesPanel restaurants={restaurants} />}

      {tab === "Restaurants" && (
        <Card title={`Restaurants (${restaurants.length})`}>
          <ul className="divide-y divide-gray-100">
            {restaurants.map((r) => (
              <li key={r.id} className="flex items-center justify-between py-2.5">
                <div>
                  <div className="font-medium">{r.name}</div>
                  <div className="font-mono text-xs text-gray-400">{r.id}</div>
                </div>
                <Link href={`/restaurants/${r.id}`} className="text-sm font-medium text-orange-600 hover:underline">
                  Manage →
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {tab === "Users" && (
        <Card title={`Users (${users.length})`}>
          <ul className="divide-y divide-gray-100">
            {users.map((u) => (
              <li key={u.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5">
                <div>
                  <div className="font-medium">
                    {u.full_name || u.email} {u.id === currentUserId && <Badge>you</Badge>}
                  </div>
                  <div className="text-sm text-gray-500">{u.email}</div>
                </div>
                <Select
                  value={u.role}
                  onChange={(e) => setRole(u, e.target.value as Role)}
                  className="w-40 py-1"
                  disabled={u.id === currentUserId}
                >
                  <option value="manager">Manager</option>
                  <option value="super_admin">Super admin</option>
                </Select>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {tab === "Platform" && (
        <Card title="Master settings PIN">
          <form onSubmit={savePin} className="max-w-md space-y-3">
            <p className="text-sm text-gray-500">
              Unlocks the settings screen on <b>every</b> display, including unlinked ones (long-press the top-right corner of the pairing screen).
            </p>
            <div>
              <Label hint="(4–8 digits)">Master PIN</Label>
              <Input
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
                pattern="[0-9]{4,8}"
                maxLength={8}
                className="font-mono"
                required
              />
            </div>
            <Button type="submit">Save</Button>
          </form>
        </Card>
      )}
    </div>
  );
}

"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Device, Restaurant } from "@/lib/types";
import { isOnline, timeAgo } from "@/lib/util";
import { Alert, Badge, Button, Card, Input, OnlineDot, Select } from "./ui";
import LinkDeviceForm from "./LinkDeviceForm";

const COMMANDS = [
  { value: "refresh", label: "Refresh content" },
  { value: "clear_cache", label: "Clear cache & re-download" },
  { value: "restart_app", label: "Restart app" },
];

/**
 * Lists displays. With [restaurantId] it shows that restaurant's displays; without it
 * (super admin) it shows every display, including unlinked ones, with a restaurant picker.
 */
export default function DevicesPanel({ restaurantId, restaurants }: { restaurantId?: string; restaurants?: Restaurant[] }) {
  const supabase = createClient();
  const [devices, setDevices] = useState<Device[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [filter, setFilter] = useState("");

  const load = useCallback(async () => {
    let q = supabase.from("devices").select("*").order("created_at", { ascending: false });
    if (restaurantId) q = q.eq("restaurant_id", restaurantId);
    const { data, error } = await q;
    if (error) setError(error.message);
    setDevices((data ?? []) as Device[]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restaurantId]);

  useEffect(() => {
    load();
    const t = setInterval(load, 15000);
    return () => clearInterval(t);
  }, [load]);

  async function rename(d: Device, name: string) {
    if (name === d.name) return;
    const { error } = await supabase.from("devices").update({ name }).eq("id", d.id);
    if (error) setError(error.message);
    load();
  }

  async function sendCommand(d: Device, command: string) {
    const { error } = await supabase.from("devices").update({ pending_command: command }).eq("id", d.id);
    if (error) return setError(error.message);
    setNotice(`Command sent to ${d.name || d.pair_code}. It runs on the next sync (within ~30s).`);
    load();
  }

  async function unlink(d: Device) {
    if (!confirm(`Unlink ${d.name || d.pair_code}? It will go back to showing its pairing QR code.`)) return;
    const { error } = await supabase.rpc("unlink_device", { p_device_id: d.id });
    if (error) return setError(error.message);
    load();
  }

  async function assign(d: Device, rid: string) {
    if (!rid) return unlink(d);
    const { error } = await supabase.rpc("link_device", { p_pair_code: d.pair_code, p_restaurant_id: rid });
    if (error) return setError(error.message);
    load();
  }

  async function remove(d: Device) {
    if (!confirm(`Permanently delete device ${d.pair_code}? If it is still running it will re-register with a new code.`)) return;
    const { error } = await supabase.from("devices").delete().eq("id", d.id);
    if (error) return setError(error.message);
    load();
  }

  const restaurantName = (id: string | null) => restaurants?.find((r) => r.id === id)?.name;
  const shown = devices.filter((d) => {
    if (!filter) return true;
    const f = filter.toLowerCase();
    return [d.name, d.pair_code, d.id, restaurantName(d.restaurant_id) ?? ""].some((s) => s.toLowerCase().includes(f));
  });

  return (
    <div className="space-y-6">
      {restaurantId && (
        <Card title="Link a new display">
          <LinkDeviceForm fixedRestaurantId={restaurantId} onLinked={load} />
        </Card>
      )}
      <Card
        title={`Displays (${devices.length})`}
        actions={
          <>
            {!restaurantId && <Input placeholder="Search…" value={filter} onChange={(e) => setFilter(e.target.value)} className="w-48 py-1.5" />}
            <Button variant="secondary" onClick={load}>
              Refresh
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          {error && <Alert>{error}</Alert>}
          {notice && <Alert kind="success">{notice}</Alert>}
          {shown.length === 0 ? (
            <p className="text-sm text-gray-500">No displays yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase text-gray-400">
                  <tr>
                    <th className="py-2 pr-3">Status</th>
                    <th className="py-2 pr-3">Name</th>
                    <th className="py-2 pr-3">Code / ID</th>
                    {restaurants && <th className="py-2 pr-3">Restaurant</th>}
                    <th className="py-2 pr-3">Last seen</th>
                    <th className="py-2 pr-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {shown.map((d) => (
                    <tr key={d.id} className="align-middle">
                      <td className="py-2.5 pr-3">
                        <div className="flex items-center gap-2">
                          <OnlineDot online={isOnline(d)} />
                          {d.pending_command && <Badge color="orange">{d.pending_command} pending</Badge>}
                        </div>
                      </td>
                      <td className="py-2.5 pr-3">
                        <Input defaultValue={d.name} placeholder="e.g. Table 4" onBlur={(e) => rename(d, e.target.value)} className="w-40 py-1" />
                      </td>
                      <td className="py-2.5 pr-3">
                        <div className="font-mono font-semibold">{d.pair_code}</div>
                        <div className="font-mono text-xs text-gray-400" title={d.id}>
                          {d.id.slice(0, 8)}… {d.app_version && `· v${d.app_version}`}
                        </div>
                      </td>
                      {restaurants && (
                        <td className="py-2.5 pr-3">
                          <Select value={d.restaurant_id ?? ""} onChange={(e) => assign(d, e.target.value)} className="w-44 py-1">
                            <option value="">— Unlinked —</option>
                            {restaurants.map((r) => (
                              <option key={r.id} value={r.id}>
                                {r.name}
                              </option>
                            ))}
                          </Select>
                        </td>
                      )}
                      <td className="py-2.5 pr-3 text-gray-500">{timeAgo(d.last_seen_at)}</td>
                      <td className="py-2.5 pr-3">
                        <div className="flex gap-2">
                          <Select
                            value=""
                            onChange={(e) => e.target.value && sendCommand(d, e.target.value)}
                            className="w-36 py-1"
                            disabled={!d.restaurant_id && !restaurants}
                          >
                            <option value="">Send command…</option>
                            {COMMANDS.map((c) => (
                              <option key={c.value} value={c.value}>
                                {c.label}
                              </option>
                            ))}
                          </Select>
                          {d.restaurant_id && (
                            <Button variant="secondary" onClick={() => unlink(d)} className="py-1">
                              Unlink
                            </Button>
                          )}
                          {restaurants && (
                            <Button variant="danger" onClick={() => remove(d)} className="py-1">
                              Delete
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}

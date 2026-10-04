import Link from "next/link";
import { getSessionProfile } from "@/lib/supabase/server";
import type { Device, Restaurant } from "@/lib/types";
import { isOnline } from "@/lib/util";
import CreateRestaurant from "./CreateRestaurant";

export const dynamic = "force-dynamic";

export default async function Dashboard() {
  const { supabase, profile } = await getSessionProfile();
  const [{ data: restaurants }, { data: devices }, { data: ads }] = await Promise.all([
    supabase.from("restaurants").select("*").order("name"),
    supabase.from("devices").select("id, restaurant_id, last_seen_at").not("restaurant_id", "is", null),
    supabase.from("ads").select("id, restaurant_id"),
  ]);

  const list = (restaurants ?? []) as Restaurant[];
  const devs = (devices ?? []) as Pick<Device, "id" | "restaurant_id" | "last_seen_at">[];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Restaurants</h1>
          <p className="text-sm text-gray-500">
            {profile?.role === "super_admin" ? "You are a super admin and can see every restaurant." : "Restaurants you manage."}
          </p>
        </div>
        <CreateRestaurant />
      </div>

      {list.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-300 bg-white p-12 text-center text-gray-500">
          No restaurants yet. Create one to start linking table displays.
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((r) => {
            const rd = devs.filter((d) => d.restaurant_id === r.id);
            const online = rd.filter(isOnline).length;
            const adCount = (ads ?? []).filter((a) => a.restaurant_id === r.id).length;
            return (
              <Link
                key={r.id}
                href={`/restaurants/${r.id}`}
                className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-orange-300 hover:shadow"
              >
                <h2 className="text-lg font-semibold">{r.name}</h2>
                <div className="mt-3 flex gap-4 text-sm text-gray-500">
                  <span>
                    <b className="text-gray-900">{rd.length}</b> displays ({online} online)
                  </span>
                  <span>
                    <b className="text-gray-900">{adCount}</b> ads
                  </span>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}

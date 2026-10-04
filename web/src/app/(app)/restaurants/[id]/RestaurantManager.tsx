"use client";

import { useState } from "react";
import Link from "next/link";
import type { Restaurant } from "@/lib/types";
import AdsPanel from "@/components/AdsPanel";
import MenuPanel from "@/components/MenuPanel";
import DevicesPanel from "@/components/DevicesPanel";
import SettingsPanel from "@/components/SettingsPanel";
import ManagersPanel from "@/components/ManagersPanel";

const TABS = ["Ads", "Menu", "Displays", "Settings", "Managers"] as const;
type Tab = (typeof TABS)[number];

export default function RestaurantManager({ restaurant, isSuperAdmin }: { restaurant: Restaurant; isSuperAdmin: boolean }) {
  const [tab, setTab] = useState<Tab>("Ads");

  return (
    <div className="space-y-6">
      <div>
        <Link href="/dashboard" className="text-sm text-gray-500 hover:text-gray-800">
          ← Restaurants
        </Link>
        <h1 className="mt-1 text-2xl font-bold">{restaurant.name}</h1>
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
      {tab === "Ads" && <AdsPanel restaurant={restaurant} />}
      {tab === "Menu" && <MenuPanel restaurant={restaurant} />}
      {tab === "Displays" && <DevicesPanel restaurantId={restaurant.id} />}
      {tab === "Settings" && <SettingsPanel restaurant={restaurant} isSuperAdmin={isSuperAdmin} />}
      {tab === "Managers" && <ManagersPanel restaurantId={restaurant.id} />}
    </div>
  );
}

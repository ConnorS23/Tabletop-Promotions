import { notFound } from "next/navigation";
import { getSessionProfile } from "@/lib/supabase/server";
import type { Restaurant } from "@/lib/types";
import RestaurantManager from "./RestaurantManager";

export const dynamic = "force-dynamic";

export default async function RestaurantPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { supabase, profile } = await getSessionProfile();
  const { data } = await supabase.from("restaurants").select("*").eq("id", id).maybeSingle();
  if (!data) notFound();
  return <RestaurantManager restaurant={data as Restaurant} isSuperAdmin={profile?.role === "super_admin"} />;
}

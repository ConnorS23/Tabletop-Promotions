import { redirect } from "next/navigation";
import { getSessionProfile } from "@/lib/supabase/server";
import type { Profile, Restaurant } from "@/lib/types";
import AdminConsole from "./AdminConsole";

export const dynamic = "force-dynamic";

export default async function AdminPage() {
  const { supabase, profile } = await getSessionProfile();
  if (profile?.role !== "super_admin") redirect("/dashboard");

  const [{ data: restaurants }, { data: users }, { data: settings }] = await Promise.all([
    supabase.from("restaurants").select("*").order("name"),
    supabase.from("profiles").select("*").order("created_at"),
    supabase.from("app_settings").select("master_pin").eq("id", 1).single(),
  ]);

  return (
    <AdminConsole
      restaurants={(restaurants ?? []) as Restaurant[]}
      users={(users ?? []) as Profile[]}
      masterPin={settings?.master_pin ?? ""}
      currentUserId={profile.id}
    />
  );
}

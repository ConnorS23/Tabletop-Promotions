import Link from "next/link";
import { redirect } from "next/navigation";
import { getSessionProfile } from "@/lib/supabase/server";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const { user, profile } = await getSessionProfile();
  if (!user) redirect("/login");
  const isAdmin = profile?.role === "super_admin";

  return (
    <div className="min-h-screen">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center gap-6 px-6 py-3">
          <Link href="/dashboard" className="flex items-center gap-2 font-bold">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-orange-500 text-white">T</span>
            TableAds
          </Link>
          <nav className="flex gap-1 text-sm">
            <Link href="/dashboard" className="rounded-md px-3 py-1.5 text-gray-600 hover:bg-gray-100">
              Restaurants
            </Link>
            <Link href="/link" className="rounded-md px-3 py-1.5 text-gray-600 hover:bg-gray-100">
              Link a display
            </Link>
            {isAdmin && (
              <Link href="/admin" className="rounded-md px-3 py-1.5 font-medium text-orange-600 hover:bg-orange-50">
                Super admin
              </Link>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm text-gray-500">
            <span className="hidden sm:inline">{profile?.full_name || user.email}</span>
            <form action="/auth/signout" method="post">
              <button className="rounded-md px-3 py-1.5 hover:bg-gray-100">Sign out</button>
            </form>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
    </div>
  );
}

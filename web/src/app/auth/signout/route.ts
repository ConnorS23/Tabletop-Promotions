import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";

export async function POST() {
  const supabase = await createClient();
  await supabase.auth.signOut();
  // Relative Location keeps the browser on whatever host it used (request.url reflects the bind address).
  return new NextResponse(null, { status: 303, headers: { Location: "/login" } });
}

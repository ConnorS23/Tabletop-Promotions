export type Role = "manager" | "super_admin";

export interface Profile {
  id: string;
  email: string | null;
  full_name: string;
  role: Role;
  created_at: string;
}

export interface Restaurant {
  id: string;
  name: string;
  settings_pin: string;
  slide_seconds: number;
  idle_timeout_seconds: number;
  created_at: string;
}

export interface Device {
  id: string;
  pair_code: string;
  restaurant_id: string | null;
  name: string;
  app_version: string | null;
  pending_command: string | null;
  last_seen_at: string | null;
  linked_at: string | null;
  created_at: string;
}

export interface Ad {
  id: string;
  restaurant_id: string;
  title: string;
  storage_path: string;
  media_type: "image" | "video";
  duration_seconds: number | null;
  position: number;
  active: boolean;
  starts_at: string | null;
  ends_at: string | null;
  created_at: string;
}

export interface MenuRow {
  restaurant_id: string;
  storage_path: string;
  file_name: string;
  updated_at: string;
}

import { Card } from "@/components/ui";
import LinkDeviceForm from "@/components/LinkDeviceForm";

export default async function LinkPage({ searchParams }: { searchParams: Promise<{ code?: string }> }) {
  const { code } = await searchParams;
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Link a table display</h1>
        <p className="text-sm text-gray-500">Scan the QR code on a new display, or type the 6-character code it shows.</p>
      </div>
      <Card>
        <LinkDeviceForm initialCode={code ?? ""} />
      </Card>
    </div>
  );
}

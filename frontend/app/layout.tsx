import type { Metadata } from "next";
import "./globals.css";
import AnalyticsTracker from "../components/AnalyticsTracker";

const tenant = process.env.GRACE_TENANT_SLUG ?? "default";

export const metadata: Metadata = {
  title: "GRACE",
  description: "Guided Research, Access, Collaboration and Education"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body><AnalyticsTracker tenant={tenant} />{children}</body>
    </html>
  );
}

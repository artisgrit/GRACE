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
      <body>
        <a className="skip-link" href="#main-content">Skip to main content</a>
        <AnalyticsTracker tenant={tenant} />
        <div id="main-content" tabIndex={-1}>{children}</div>
      </body>
    </html>
  );
}

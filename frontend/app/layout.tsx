import type { Metadata } from "next";
import "./globals.css";

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
      <body>{children}</body>
    </html>
  );
}

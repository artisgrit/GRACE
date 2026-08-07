"use client";

import { useEffect, useMemo, useState } from "react";

type Appearance = {
  tenantSlug: string;
  activeThemeId: string;
  brand: Record<string, unknown>;
  tokens: Record<string, string>;
  layout: Record<string, unknown>;
  navigation: Record<string, unknown>;
  components: unknown[];
  customCss?: string | null;
};

type IdentityProvider = {
  providerKey: string;
  providerType: string;
  displayName: string;
  enabled: boolean;
  defaultProvider: boolean;
  discoveryRules: Record<string, unknown>;
  attributeMappings: Record<string, unknown>;
  roleMappings: Record<string, unknown>;
  configuration: Record<string, unknown>;
};

type Integration = {
  connectorKey: string;
  connectorType: string;
  displayName: string;
  enabled: boolean;
  configurationSchema: Record<string, unknown>;
  configuration: Record<string, unknown>;
  mappingProfile: Record<string, unknown>;
  schedule: Record<string, unknown>;
  health: Record<string, unknown>;
};

type AuditEvent = {
  id: string;
  area: string;
  action: string;
  summary: string;
  createdAt: string;
};

const tenant = "default";

function apiBase() {
  return process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "") ?? "http://localhost:8080/api/v1";
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase()}${path}`, {
    ...init,
    headers: {
      "content-type": "application/json",
      ...(init?.headers ?? {})
    }
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with ${response.status}`);
  }

  return response.json() as Promise<T>;
}

function JsonEditor({
  label,
  value,
  onChange
}: {
  label: string;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const [text, setText] = useState(() => JSON.stringify(value, null, 2));
  const [error, setError] = useState("");

  useEffect(() => {
    setText(JSON.stringify(value, null, 2));
  }, [value]);

  return (
    <label className="field field-block">
      <span>{label}</span>
      <textarea
        value={text}
        rows={8}
        onChange={(event) => {
          const next = event.target.value;
          setText(next);
          try {
            onChange(JSON.parse(next));
            setError("");
          } catch {
            setError("JSON is not valid yet");
          }
        }}
      />
      {error ? <small role="status">{error}</small> : null}
    </label>
  );
}

export default function AdminConsole() {
  const [appearance, setAppearance] = useState<Appearance | null>(null);
  const [providers, setProviders] = useState<IdentityProvider[]>([]);
  const [integrations, setIntegrations] = useState<Integration[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<IdentityProvider | null>(null);
  const [selectedIntegration, setSelectedIntegration] = useState<Integration | null>(null);
  const [message, setMessage] = useState("Loading configuration...");

  const cssVariables = useMemo(() => {
    if (!appearance) return {};
    return {
      "--grace-color-primary": appearance.tokens.colorPrimary,
      "--grace-color-secondary": appearance.tokens.colorSecondary,
      "--grace-color-accent": appearance.tokens.colorAccent,
      "--grace-background": appearance.tokens.background,
      "--grace-surface": appearance.tokens.surface,
      "--grace-text": appearance.tokens.text,
      "--grace-muted-text": appearance.tokens.mutedText,
      "--grace-border": appearance.tokens.border,
      "--grace-radius": appearance.tokens.radius,
      "--grace-container-width": appearance.tokens.containerWidth,
      "--grace-font-heading": appearance.tokens.fontHeading,
      "--grace-font-body": appearance.tokens.fontBody
    } as React.CSSProperties;
  }, [appearance]);

  async function load() {
    try {
      const [nextAppearance, nextProviders, nextIntegrations, nextAudit] = await Promise.all([
        request<Appearance>(`/tenants/${tenant}/appearance`),
        request<IdentityProvider[]>(`/tenants/${tenant}/identity-providers`),
        request<Integration[]>(`/tenants/${tenant}/integrations`),
        request<AuditEvent[]>(`/tenants/${tenant}/audit-events`)
      ]);
      setAppearance(nextAppearance);
      setProviders(nextProviders);
      setIntegrations(nextIntegrations);
      setAuditEvents(nextAudit);
      setSelectedProvider(nextProviders[0] ?? null);
      setSelectedIntegration(nextIntegrations[0] ?? null);
      setMessage("Configuration loaded");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Configuration could not be loaded");
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function saveAppearance() {
    if (!appearance) return;
    await request<Appearance>(`/tenants/${tenant}/appearance`, {
      method: "PUT",
      body: JSON.stringify(appearance)
    });
    setMessage("Appearance saved");
    await load();
  }

  async function saveProvider() {
    if (!selectedProvider) return;
    await request<IdentityProvider>(`/tenants/${tenant}/identity-providers`, {
      method: "PUT",
      body: JSON.stringify(selectedProvider)
    });
    setMessage("Identity provider saved");
    await load();
  }

  async function saveIntegration() {
    if (!selectedIntegration) return;
    await request<Integration>(`/tenants/${tenant}/integrations`, {
      method: "PUT",
      body: JSON.stringify(selectedIntegration)
    });
    setMessage("Integration saved");
    await load();
  }

  return (
    <main className="admin-shell" style={cssVariables}>
      <header className="admin-header">
        <div>
          <p className="eyebrow">Administration</p>
          <h1>GRACE configuration centre</h1>
        </div>
        <a className="nav-link" href="/">
          Public site
        </a>
      </header>

      <p className="notice" role="status">
        {message}
      </p>

      <section className="admin-grid">
        <article className="panel panel-wide">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Appearance</p>
              <h2>Brand, tokens and layout</h2>
            </div>
            <button type="button" onClick={saveAppearance} disabled={!appearance}>
              Save appearance
            </button>
          </div>

          {appearance ? (
            <>
              <div className="form-grid">
                <label className="field">
                  <span>Institution name</span>
                  <input
                    value={String(appearance.brand.institutionName ?? "")}
                    onChange={(event) =>
                      setAppearance({
                        ...appearance,
                        brand: { ...appearance.brand, institutionName: event.target.value }
                      })
                    }
                  />
                </label>
                <label className="field">
                  <span>Logo placement</span>
                  <select
                    value={String(appearance.brand.logoPlacement ?? "header-left")}
                    onChange={(event) =>
                      setAppearance({
                        ...appearance,
                        brand: { ...appearance.brand, logoPlacement: event.target.value }
                      })
                    }
                  >
                    <option value="header-left">Header left</option>
                    <option value="header-center">Header centre</option>
                    <option value="header-right">Header right</option>
                    <option value="sidebar">Sidebar</option>
                  </select>
                </label>
                <label className="field">
                  <span>Primary colour</span>
                  <input
                    type="color"
                    value={appearance.tokens.colorPrimary}
                    onChange={(event) =>
                      setAppearance({
                        ...appearance,
                        tokens: { ...appearance.tokens, colorPrimary: event.target.value }
                      })
                    }
                  />
                </label>
                <label className="field">
                  <span>Secondary colour</span>
                  <input
                    type="color"
                    value={appearance.tokens.colorSecondary}
                    onChange={(event) =>
                      setAppearance({
                        ...appearance,
                        tokens: { ...appearance.tokens, colorSecondary: event.target.value }
                      })
                    }
                  />
                </label>
              </div>
              <JsonEditor
                label="Layout JSON"
                value={appearance.layout}
                onChange={(value) => setAppearance({ ...appearance, layout: value as Record<string, unknown> })}
              />
              <JsonEditor
                label="Navigation JSON"
                value={appearance.navigation}
                onChange={(value) => setAppearance({ ...appearance, navigation: value as Record<string, unknown> })}
              />
            </>
          ) : null}
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Identity</p>
              <h2>Federated login providers</h2>
            </div>
            <button type="button" onClick={saveProvider} disabled={!selectedProvider}>
              Save provider
            </button>
          </div>

          <div className="choice-list">
            {providers.map((provider) => (
              <button
                key={provider.providerKey}
                type="button"
                className={selectedProvider?.providerKey === provider.providerKey ? "choice active" : "choice"}
                onClick={() => setSelectedProvider(provider)}
              >
                {provider.displayName}
              </button>
            ))}
          </div>

          {selectedProvider ? (
            <>
              <label className="field">
                <span>Display name</span>
                <input
                  value={selectedProvider.displayName}
                  onChange={(event) => setSelectedProvider({ ...selectedProvider, displayName: event.target.value })}
                />
              </label>
              <label className="check-field">
                <input
                  type="checkbox"
                  checked={selectedProvider.enabled}
                  onChange={(event) => setSelectedProvider({ ...selectedProvider, enabled: event.target.checked })}
                />
                Enabled
              </label>
              <JsonEditor
                label="Attribute mappings"
                value={selectedProvider.attributeMappings}
                onChange={(value) =>
                  setSelectedProvider({ ...selectedProvider, attributeMappings: value as Record<string, unknown> })
                }
              />
              <JsonEditor
                label="Role mappings"
                value={selectedProvider.roleMappings}
                onChange={(value) =>
                  setSelectedProvider({ ...selectedProvider, roleMappings: value as Record<string, unknown> })
                }
              />
            </>
          ) : null}
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Interoperability</p>
              <h2>Connector registry</h2>
            </div>
            <button type="button" onClick={saveIntegration} disabled={!selectedIntegration}>
              Save connector
            </button>
          </div>

          <div className="choice-list">
            {integrations.map((integration) => (
              <button
                key={integration.connectorKey}
                type="button"
                className={selectedIntegration?.connectorKey === integration.connectorKey ? "choice active" : "choice"}
                onClick={() => setSelectedIntegration(integration)}
              >
                {integration.displayName}
              </button>
            ))}
          </div>

          {selectedIntegration ? (
            <>
              <label className="field">
                <span>Display name</span>
                <input
                  value={selectedIntegration.displayName}
                  onChange={(event) =>
                    setSelectedIntegration({ ...selectedIntegration, displayName: event.target.value })
                  }
                />
              </label>
              <label className="check-field">
                <input
                  type="checkbox"
                  checked={selectedIntegration.enabled}
                  onChange={(event) =>
                    setSelectedIntegration({ ...selectedIntegration, enabled: event.target.checked })
                  }
                />
                Enabled
              </label>
              <JsonEditor
                label="Configuration"
                value={selectedIntegration.configuration}
                onChange={(value) =>
                  setSelectedIntegration({ ...selectedIntegration, configuration: value as Record<string, unknown> })
                }
              />
              <JsonEditor
                label="Mapping profile"
                value={selectedIntegration.mappingProfile}
                onChange={(value) =>
                  setSelectedIntegration({ ...selectedIntegration, mappingProfile: value as Record<string, unknown> })
                }
              />
            </>
          ) : null}
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Audit</p>
            <h2>Recent configuration activity</h2>
          </div>
        </div>
        <div className="audit-list">
          {auditEvents.length ? (
            auditEvents.map((event) => (
              <div key={event.id}>
                <strong>
                  {event.area}: {event.action}
                </strong>
                <span>{event.summary}</span>
                <time>{new Date(event.createdAt).toLocaleString()}</time>
              </div>
            ))
          ) : (
            <p>No audit events recorded yet.</p>
          )}
        </div>
      </section>
    </main>
  );
}

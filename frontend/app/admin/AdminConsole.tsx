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

type CurrentUser = { tenantSlug: string; email: string; displayName: string; permissions: string[] };
type User = { id: string; email: string; displayName: string; enabled: boolean; roles: string[] };
type Role = { id: string; name: string; displayName: string; permissions: string[] };

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/grace${path}`, {
    ...init,
    headers: {
      "content-type": "application/json",
      ...(init?.headers ?? {})
    }
  });

  if (response.status === 401) {
    window.location.assign("/login");
    throw new Error("Your session has expired");
  }
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
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [tenant, setTenant] = useState("");
  const [appearance, setAppearance] = useState<Appearance | null>(null);
  const [providers, setProviders] = useState<IdentityProvider[]>([]);
  const [integrations, setIntegrations] = useState<Integration[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<IdentityProvider | null>(null);
  const [selectedIntegration, setSelectedIntegration] = useState<Integration | null>(null);
  const [selectedRole, setSelectedRole] = useState<Role | null>(null);
  const [newUser, setNewUser] = useState({ email: "", displayName: "", password: "", roles: ["LIBRARIAN"] });
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
      const meResponse = await fetch("/api/auth/me", { cache: "no-store" });
      if (!meResponse.ok) {
        window.location.assign("/login");
        return;
      }
      const me = await meResponse.json() as CurrentUser;
      setCurrentUser(me);
      setTenant(me.tenantSlug);
      const [nextAppearance, nextProviders, nextIntegrations, nextAudit, nextUsers, nextRoles] = await Promise.all([
        request<Appearance>(`/tenants/${me.tenantSlug}/appearance`),
        request<IdentityProvider[]>(`/tenants/${me.tenantSlug}/identity-providers`),
        request<Integration[]>(`/tenants/${me.tenantSlug}/integrations`),
        request<AuditEvent[]>(`/tenants/${me.tenantSlug}/audit-events`),
        request<User[]>(`/tenants/${me.tenantSlug}/users`),
        request<Role[]>(`/tenants/${me.tenantSlug}/roles`)
      ]);
      setAppearance(nextAppearance);
      setProviders(nextProviders);
      setIntegrations(nextIntegrations);
      setAuditEvents(nextAudit);
      setUsers(nextUsers);
      setRoles(nextRoles);
      setSelectedProvider(nextProviders[0] ?? null);
      setSelectedIntegration(nextIntegrations[0] ?? null);
      setSelectedRole(nextRoles[0] ?? null);
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

  async function createUser() {
    await request<User>(`/tenants/${tenant}/users`, {
      method: "POST",
      body: JSON.stringify(newUser)
    });
    setNewUser({ email: "", displayName: "", password: "", roles: ["LIBRARIAN"] });
    setMessage("User created");
    await load();
  }

  async function saveRolePermissions() {
    if (!selectedRole) return;
    await request<Role>(`/tenants/${tenant}/roles/${selectedRole.id}/permissions`, {
      method: "PUT",
      body: JSON.stringify({ permissions: selectedRole.permissions })
    });
    setMessage("Role permissions saved");
    await load();
  }

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
    window.location.assign("/login");
  }

  return (
    <main className="admin-shell" style={cssVariables}>
      <header className="admin-header">
        <div>
          <p className="eyebrow">Administration</p>
          <h1>GRACE configuration centre</h1>
          {currentUser ? <p className="signed-in">{currentUser.displayName} | {currentUser.email}</p> : null}
        </div>
        <nav className="admin-actions" aria-label="Account and site">
          <a className="nav-link" href="/admin/guides">Guides</a>
          <a className="nav-link" href="/admin/resources">Resources</a>
          <a className="nav-link" href="/">Public site</a>
          <button className="secondary-button" type="button" onClick={signOut}>Sign out</button>
        </nav>
      </header>

      <p className="notice" role="status">
        {message}
      </p>

      <section className="admin-grid">
        <article className="panel panel-wide">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Access control</p>
              <h2>Users and roles</h2>
            </div>
            <button type="button" onClick={createUser} disabled={!newUser.email || !newUser.displayName || newUser.password.length < 12}>
              Create user
            </button>
          </div>
          <div className="access-grid">
            <div>
              <div className="form-grid">
                <label className="field"><span>Name</span><input value={newUser.displayName} onChange={(event) => setNewUser({ ...newUser, displayName: event.target.value })} /></label>
                <label className="field"><span>Email</span><input type="email" value={newUser.email} onChange={(event) => setNewUser({ ...newUser, email: event.target.value })} /></label>
                <label className="field"><span>Temporary password</span><input type="password" value={newUser.password} onChange={(event) => setNewUser({ ...newUser, password: event.target.value })} /></label>
                <label className="field">
                  <span>Initial role</span>
                  <select value={newUser.roles[0]} onChange={(event) => setNewUser({ ...newUser, roles: [event.target.value] })}>
                    {roles.map((role) => <option key={role.id} value={role.name}>{role.displayName}</option>)}
                  </select>
                </label>
              </div>
              <div className="user-list" aria-label="Tenant users">
                {users.map((user) => <div key={user.id}><strong>{user.displayName}</strong><span>{user.email}</span><span>{user.roles.join(", ")}</span></div>)}
              </div>
            </div>
            <div>
              <div className="choice-list">
                {roles.map((role) => (
                  <button key={role.id} type="button" className={selectedRole?.id === role.id ? "choice active" : "choice"} onClick={() => setSelectedRole(role)}>
                    {role.displayName}
                  </button>
                ))}
              </div>
              {selectedRole ? (
                <>
                  <JsonEditor label={`${selectedRole.displayName} permissions`} value={selectedRole.permissions} onChange={(value) => setSelectedRole({ ...selectedRole, permissions: value as string[] })} />
                  <button type="button" onClick={saveRolePermissions}>Save permissions</button>
                </>
              ) : null}
            </div>
          </div>
        </article>
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

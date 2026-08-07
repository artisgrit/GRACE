export default function Home() {
  return (
    <main className="shell">
      <section className="intro">
        <p className="eyebrow">GRACE</p>
        <h1>Guided Research, Access, Collaboration and Education</h1>
        <p>
          The local Docker stack is running. Backend, search, storage, cache,
          database, and identity services are ready for development.
        </p>
        <a className="primary-link" href="/admin">
          Open configuration centre
        </a>
      </section>
      <section className="status" aria-label="Service status">
        <div>
          <span>Frontend</span>
          <strong>Ready</strong>
        </div>
        <div>
          <span>Backend API</span>
          <strong>http://localhost:8080</strong>
        </div>
        <div>
          <span>Keycloak</span>
          <strong>http://localhost:8180</strong>
        </div>
      </section>
    </main>
  );
}

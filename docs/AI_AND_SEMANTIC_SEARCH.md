# AI and Semantic Search

GRACE Phase 8 adds an optional tenant-scoped AI gateway. It is disabled by default, and ordinary PostgreSQL/OpenSearch discovery remains available when AI is disabled, over quota, or unreachable.

## Architecture

- `AiProvider` isolates model-provider details from search and assistant workflows.
- `HttpAiProvider` supports Ollama and OpenAI-compatible chat and embedding APIs.
- `AiService` combines lexical relevance with cosine similarity from persisted embeddings.
- The assistant retrieves approved published GRACE records and asks the configured model to answer with numbered citations.
- `/admin/ai` provides tenant controls; `/assistant` is the public research-assistant surface.

Embeddings are stored as JSON arrays in PostgreSQL to keep the default deployment self-contained. A dedicated vector engine can replace this persistence boundary without changing the public API.

## Tenant Configuration

AI administrators need the `ai.configure` permission. Configure these values in `/admin/ai`:

- Provider: `OLLAMA` or `OPENAI_COMPATIBLE`
- Base URL, chat model, and embedding model
- Approved source types used for indexing and retrieval
- Temperature, output token limit, timeout, and optional monthly quota
- Private-network access, explicitly enabled for local model servers
- API key environment-variable name, when required

GRACE stores only the environment-variable name. Add the corresponding secret to the backend process or container environment. Raw provider keys must never be entered in the browser or persisted in the database.

For a local Ollama instance reachable from the backend container, configure an appropriate container-reachable URL, enable private-network access, select installed models, save, and use **Rebuild embeddings**.

## Privacy and Security

- AI is opt-in and disabled for every tenant by default.
- Prompts and generated answers are not retained. The database enforces `retain_prompts = false`.
- Usage records contain operation, model, status, approximate units, and bounded error text only.
- Provider redirects are disabled and requests have connection and response timeouts.
- Loopback, link-local, and private addresses are blocked unless the tenant explicitly allows a private endpoint.
- RAG context contains only approved, published tenant content and is labelled as untrusted reference data in the system instruction.
- The provider is instructed to cite sources and admit insufficient context. Generated answers remain advisory and expose their source links for verification.

## API

Public, rate-limit-ready endpoints:

```text
POST /api/v1/public/tenants/{tenant}/ai/search?size=10
POST /api/v1/public/tenants/{tenant}/ai/assistant
```

Both accept `{"question":"..."}`. Hybrid search returns lexical results when semantic retrieval is unavailable. The assistant returns explicit `DISABLED`, `NO_SOURCES`, `QUOTA_EXCEEDED`, or `PROVIDER_UNAVAILABLE` states instead of breaking conventional discovery.

Authenticated administration endpoints:

```text
GET  /api/v1/tenants/{tenant}/ai/configuration
PUT  /api/v1/tenants/{tenant}/ai/configuration
POST /api/v1/tenants/{tenant}/ai/reindex
GET  /api/v1/tenants/{tenant}/ai/usage
```

Rebuild embeddings after publishing substantial content changes or changing the embedding model. Provider-dependent indexing and generation require a reachable configured model; they are intentionally not replaced with synthetic vectors or uncited placeholder answers.

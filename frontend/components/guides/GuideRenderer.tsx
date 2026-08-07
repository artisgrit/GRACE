import type { GuideBlock, GuideDetail } from "../../lib/guide-types";

function text(value: unknown) {
  return typeof value === "string" ? value : "";
}

function Block({ block }: { block: GuideBlock }) {
  const content = block.content;
  if (block.blockType === "HEADING") {
    const value = text(content.text);
    return <h2 className="guide-heading-block">{value}</h2>;
  }
  if (block.blockType === "RICH_TEXT") {
    return <p className="guide-text-block">{text(content.text)}</p>;
  }
  if (block.blockType === "CALLOUT") {
    return (
      <aside className="guide-callout">
        {content.title ? <strong>{text(content.title)}</strong> : null}
        <p>{text(content.text)}</p>
      </aside>
    );
  }
  if (block.blockType === "LINK_LIST") {
    const items = Array.isArray(content.items) ? content.items as Array<{ label?: string; url?: string }> : [];
    return (
      <section className="guide-link-block">
        {content.title ? <h2>{text(content.title)}</h2> : null}
        <ul>{items.map((item, index) => <li key={`${item.url}-${index}`}><a href={item.url}>{item.label || item.url}</a></li>)}</ul>
      </section>
    );
  }
  if (block.blockType === "CODE") {
    return <pre className="guide-code-block"><code>{text(content.text)}</code></pre>;
  }
  return <div className="guide-unsupported-block">{block.blockType.replaceAll("_", " ")}</div>;
}

export default function GuideRenderer({ guide }: { guide: GuideDetail }) {
  return (
    <article className="guide-document">
      <header className="guide-document-header">
        <p className="eyebrow">{guide.guideTypeName}</p>
        <h1>{guide.title}</h1>
        <p>{guide.description}</p>
        <div className="guide-byline"><span>Maintained by {guide.ownerName}</span><span>Revision {guide.revisionNumber}</span></div>
      </header>
      <nav className="guide-page-nav" aria-label="Guide pages">
        {guide.pages.map((page) => <a key={page.id} href={`#page-${page.id}`}>{page.title}</a>)}
      </nav>
      <div className="guide-page-content">
        {guide.pages.map((page) => (
          <section key={page.id} id={`page-${page.id}`} className="guide-rendered-page">
            <h2>{page.title}</h2>
            <div className="guide-block-stack">{page.blocks.map((block) => <Block key={block.id} block={block} />)}</div>
          </section>
        ))}
      </div>
    </article>
  );
}

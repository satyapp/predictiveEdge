import { useEffect, useMemo, useState } from 'react';

type AnalysisModule = 'technical' | 'swot';

const TRENDLYNE_SCRIPT = 'https://cdn-static.trendlyne.com/static/js/webwidgets/tl-widgets.js';

function widgetUrl(module: AnalysisModule, symbol: string) {
  const widget = module === 'technical' ? 'technical-widget' : 'swot-widget';
  const stockCode = encodeURIComponent(symbol.trim().toUpperCase());
  return `https://trendlyne.com/web-widget/${widget}/Poppins/${stockCode}/?posCol=00A25B&primaryCol=006AFF&negCol=EB3B00&neuCol=F7941E`;
}

function TrendlyneWidget({ module, symbol }: { module: AnalysisModule; symbol: string }) {
  const url = useMemo(() => widgetUrl(module, symbol), [module, symbol]);

  useEffect(() => {
    // Trendlyne discovers widget blockquotes when its script executes. A fresh script element
    // is intentional so widgets opened after the initial page load are also discovered.
    const script = document.createElement('script');
    script.async = true;
    script.src = TRENDLYNE_SCRIPT;
    script.charset = 'utf-8';
    script.dataset.predictiveEdgeTrendlyne = 'true';
    document.body.appendChild(script);
    return () => script.remove();
  }, [url]);

  return <blockquote
    className="trendlyne-widgets"
    data-get-url={url}
    data-theme="light"
    aria-label={`Trendlyne ${module === 'technical' ? 'technical analysis' : 'SWOT analysis'} for ${symbol}`}
  />;
}

export function TrendlyneStockAnalysis({ symbol }: { symbol: string }) {
  const [module, setModule] = useState<AnalysisModule>();

  return <section className="trendlyne-analysis" aria-label={`Trendlyne analysis for ${symbol}`}>
    <div className="trendlyne-analysis-heading">
      <div><small>TRENDLYNE · {symbol}</small><b>External stock analysis</b></div>
      <span>Structured evidence → Ollama Stage‑1</span>
    </div>
    <div className="trendlyne-module-tabs" role="tablist" aria-label={`Trendlyne modules for ${symbol}`}>
      <button type="button" role="tab" aria-selected={module === 'technical'} className={module === 'technical' ? 'active' : ''} onClick={() => setModule(module === 'technical' ? undefined : 'technical')}>Technical analysis</button>
      <button type="button" role="tab" aria-selected={module === 'swot'} className={module === 'swot' ? 'active' : ''} onClick={() => setModule(module === 'swot' ? undefined : 'swot')}>Fundamental · SWOT</button>
    </div>
    {module && <div className="trendlyne-widget-panel" role="tabpanel">
      <TrendlyneWidget key={`${symbol}:${module}`} module={module} symbol={symbol} />
      <p>The embedded widget is for display. PredictiveEdge obtains AI-readable Trendlyne evidence through the configured server-side MCP connector.</p>
    </div>}
  </section>;
}

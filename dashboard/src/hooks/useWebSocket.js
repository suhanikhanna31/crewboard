import { useEffect, useRef, useState } from "react";

/** Live socket with exponential-backoff reconnect. Returns "live" | "reconnecting" | "connecting". */
export function useWebSocket(url, onMessage) {
  const [status, setStatus] = useState("connecting");
  const handler = useRef(onMessage);
  handler.current = onMessage;

  useEffect(() => {
    if (!url) return;
    let ws, timer, closed = false, attempt = 0;
    const connect = () => {
      ws = new WebSocket(url);
      ws.onopen = () => { attempt = 0; setStatus("live"); };
      ws.onmessage = (e) => handler.current(JSON.parse(e.data));
      ws.onclose = () => {
        if (closed) return;
        setStatus("reconnecting");
        timer = setTimeout(connect, Math.min(30000, 1000 * 2 ** attempt++));
      };
    };
    connect();
    return () => { closed = true; clearTimeout(timer); ws && ws.close(); };
  }, [url]);

  return status;
}

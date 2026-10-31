import { useCallback, useRef, useState, useEffect } from "react";

type MessageHandler<T> = (data: T) => void;
type WebSocketUrl = string | (() => string | Promise<string>);

export function useWebSocket<T>(url: WebSocketUrl, options?: {
  onMessage?: MessageHandler<T>;
  reconnect?: boolean;
  reconnectInterval?: number;
}) {
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const attemptRef = useRef(0);
  const manuallyClosedRef = useRef(true);
  const { onMessage, reconnect = true, reconnectInterval = 3000 } = options ?? {};

  const [connected, setConnected] = useState(false);
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Event | null>(null);

  const connect = useCallback(function openConnection() {
    if (wsRef.current && wsRef.current.readyState !== WebSocket.CLOSED) return;
    manuallyClosedRef.current = false;
    if (reconnectTimerRef.current !== null) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }


    // URL用チケットの取得中に切断・再接続されても、古い取得結果から別ソケットを開かないための世代番号。
    const attempt = ++attemptRef.current;
    const open = async () => {
      try {
        const resolvedUrl = typeof url === 'function' ? await url() : url;
        if (manuallyClosedRef.current || attempt !== attemptRef.current) return;

        const ws = new WebSocket(resolvedUrl);

        // イベント発火が遅れた旧ソケットで、現在の接続状態や受信データを上書きしない。
        ws.onopen = () => {
          if (wsRef.current !== ws) return;
          setConnected(true);
          setError(null);
        };

        ws.onmessage = (event) => {
          if (wsRef.current !== ws) return;
          try {
            const parsed = JSON.parse(event.data) as T;
            setData(parsed);
            onMessage?.(parsed);
          } catch (e) {
            console.error('WS parse error:', e);
          }
        };

        ws.onclose = () => {
          if (wsRef.current !== ws) return;
          setConnected(false);
          wsRef.current = null;
          if (reconnect && !manuallyClosedRef.current) {
            reconnectTimerRef.current = setTimeout(openConnection, reconnectInterval);
          }
        };

        ws.onerror = (e) => {
          if (wsRef.current !== ws) return;
          setError(e);
        };

        wsRef.current = ws;
      } catch (cause) {
        if (manuallyClosedRef.current || attempt !== attemptRef.current) return;
        setConnected(false);
        setError(cause instanceof Event ? cause : new Event('websocket-ticket-error'));
        if (reconnect) {
          reconnectTimerRef.current = setTimeout(openConnection, reconnectInterval);
        }
      }
    };

    void open();
  }, [url, onMessage, reconnect, reconnectInterval]);

  const disconnect = useCallback(() => {

    // 手動OFFでは再接続予約と取得中のURLを両方無効化し、closeイベントからの自動復帰も抑止する。
    manuallyClosedRef.current = true;
    attemptRef.current += 1;
    if (reconnectTimerRef.current !== null) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    const ws = wsRef.current;
    wsRef.current = null;
    ws?.close();
    setConnected(false);
  }, []);

  const send = useCallback((message: unknown) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(message));
    }
  }, []);

  useEffect(() => {
    return () => {
      manuallyClosedRef.current = true;
      attemptRef.current += 1;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
      const ws = wsRef.current;
      wsRef.current = null;
      ws?.close();
    };
  }, []);

  return {
    data,
    connected,
    error,
    connect,
    disconnect,
    send,
  };
}

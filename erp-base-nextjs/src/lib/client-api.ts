"use client";

import { useCallback, useEffect, useState } from "react";
import type { Json } from "./data";

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
  }
}

export async function api(path: string, method = "GET", body?: Json | FormData): Promise<Json> {
  const response = await fetch(`/api/backend/${path.replace(/^\/+/, "")}`, {
    method,
    cache: "no-store",
    headers: body instanceof FormData || body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
  });
  const content = await response.text();
  if (!response.ok) {
    let message = `Požadavek byl odmítnut (HTTP ${response.status}).`;
    try {
      const parsed: { error?: string; detail?: string; message?: string } = JSON.parse(content);
      message = parsed.error || parsed.detail || parsed.message || message;
    } catch {
      // Non-JSON error pages must not be rendered as HTML.
    }
    throw new ApiError(message, response.status);
  }
  return content ? JSON.parse(content) as Json : null;
}

export function useApi(path: string | null) {
  const [data, setData] = useState<Json>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(Boolean(path));
  const [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((value) => value + 1), []);
  useEffect(() => {
    let active = true;
    if (!path) return;
    setLoading(true);
    setError("");
    api(path).then((value) => {
      if (active) setData(value);
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : "Data nelze načíst.");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [path, revision]);
  return { data, error, loading, reload };
}

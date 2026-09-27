"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { FoodProduct } from "@/lib/core/foods";
import { lookupBarcode } from "@/lib/foods/foods-client";
import { currentLanguage, t } from "@/i18n";

type Status = "starting" | "scanning" | "cameraError" | "lookingUp" | "notFound" | "offline";

interface Detector {
  detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]>;
}

const FORMATS = ["ean_13", "ean_8", "upc_a", "upc_e", "code_128", "qr_code"];

/**
 * A barcode detector: the browser's own when it has one (Chrome on Android),
 * otherwise the zxing-wasm ponyfill loaded on demand, with its .wasm served
 * from this site (public/wasm) rather than a CDN.
 */
async function createDetector(): Promise<Detector> {
  const native = (globalThis as { BarcodeDetector?: new (options: { formats: string[] }) => Detector }).BarcodeDetector;
  if (native) return new native({ formats: FORMATS });
  const { BarcodeDetector, prepareZXingModule } = await import("barcode-detector/ponyfill");
  prepareZXingModule({
    overrides: {
      locateFile: (path: string, prefix: string) => (path.endsWith(".wasm") ? `/wasm/${path}` : prefix + path),
    },
  });
  return new BarcodeDetector({ formats: FORMATS as never });
}

/**
 * Scans a barcode with the camera (or takes it typed) and looks the product
 * up in Open Food Facts. The video is only shown on screen: nothing is saved.
 */
export function BarcodeScanner({ onFound }: { onFound: (product: FoodProduct) => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const [status, setStatus] = useState<Status>("starting");
  const [code, setCode] = useState("");

  async function lookUp(value: string) {
    setStatus("lookingUp");
    try {
      const product = await lookupBarcode(value, currentLanguage());
      if (product) onFound(product);
      else setStatus("notFound");
    } catch {
      setStatus("offline");
    }
  }

  useEffect(() => {
    let stream: MediaStream | null = null;
    let timer: ReturnType<typeof setInterval> | null = null;
    let stopped = false;

    async function start() {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error("no camera");
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" }, audio: false });
        if (stopped || !video.current) return;
        video.current.srcObject = stream;
        await video.current.play();
        const detector = await createDetector();
        setStatus("scanning");
        let busy = false;
        timer = setInterval(async () => {
          if (busy || stopped || !video.current || video.current.readyState < 2) return;
          busy = true;
          try {
            const found = await detector.detect(video.current);
            const value = found[0]?.rawValue;
            if (value && !stopped) {
              stopped = true;
              if (timer) clearInterval(timer);
              stream?.getTracks().forEach((track) => track.stop());
              setCode(value);
              void lookUp(value);
            }
          } finally {
            busy = false;
          }
        }, 300);
      } catch {
        if (!stopped) setStatus("cameraError");
      }
    }
    void start();
    return () => {
      stopped = true;
      if (timer) clearInterval(timer);
      stream?.getTracks().forEach((track) => track.stop());
    };
    // Runs once: the camera starts when the scanner opens and stops when it closes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const message =
    status === "cameraError"
      ? t.addFood.cameraError
      : status === "lookingUp"
        ? t.addFood.lookingUp
        : status === "notFound"
          ? t.addFood.notFound
          : status === "offline"
            ? t.addFood.needsInternet
            : t.addFood.scanHint;

  return (
    <div className="space-y-3">
      {status !== "cameraError" ? (
        <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-black">
          <video ref={video} className="size-full object-cover" muted playsInline />
          <div className="pointer-events-none absolute inset-x-8 top-1/2 h-0.5 -translate-y-1/2 bg-tertiary/80" />
        </div>
      ) : null}
      <p className="text-sm text-muted-foreground" role="status">
        {message}
      </p>
      <form
        className="flex gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          const value = code.replace(/\D/g, "");
          if (value.length >= 6) void lookUp(value);
        }}
      >
        <Input
          inputMode="numeric"
          aria-label={t.addFood.manualCode}
          placeholder={t.addFood.manualCode}
          value={code}
          onChange={(event) => setCode(event.target.value)}
        />
        <Button type="submit" variant="outline" disabled={status === "lookingUp"}>
          {t.addFood.lookUp}
        </Button>
      </form>
    </div>
  );
}

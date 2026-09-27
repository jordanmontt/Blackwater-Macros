import type { AiImage } from "@/lib/core/ai-providers";

/** Longest side of a photo sent to the AI: enough to read a label, small to upload. */
export const MAX_PHOTO_SIDE = 1024;
export const MAX_PHOTOS = 5;

/** The size that fits `max` on the longest side, never enlarging. */
export function fitWithin(width: number, height: number, max = MAX_PHOTO_SIDE): { width: number; height: number } {
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/**
 * A photo as a small JPEG for the AI. Only in memory: nothing is written to
 * storage, the gallery or our server (docs/AI-PLAN.md principle 4).
 */
export async function downscalePhoto(file: Blob, quality = 0.8): Promise<AiImage> {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  try {
    const size = fitWithin(bitmap.width, bitmap.height);
    const canvas = document.createElement("canvas");
    canvas.width = size.width;
    canvas.height = size.height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("no canvas");
    context.drawImage(bitmap, 0, 0, size.width, size.height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
    if (!blob) throw new Error("no jpeg");
    return { mimeType: "image/jpeg", data: await toBase64(blob) };
  } finally {
    bitmap.close();
  }
}

async function toBase64(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  return btoa(binary);
}

import Image from "next/image";

interface LogoProps {
  size?: "login" | "header";
  priority?: boolean;
  className?: string;
}

const LOGO_SIZES = {
  login: { width: 320, height: 320, displayClass: "size-20" },
  header: { width: 128, height: 128, displayClass: "size-8" },
} as const;

export function Logo({ size = "header", priority = false, className }: LogoProps) {
  const config = LOGO_SIZES[size];
  return (
    <Image
      src="/logo.png"
      alt="Blackwater Macros"
      width={config.width}
      height={config.height}
      priority={priority}
      className={className ?? `${config.displayClass} rounded-full`}
    />
  );
}

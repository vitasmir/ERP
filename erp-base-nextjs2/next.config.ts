import type { NextConfig } from "next";

const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  outputFileTracingIncludes: { "/*": ["./templates/**/*.twig"] },
};

export default config;

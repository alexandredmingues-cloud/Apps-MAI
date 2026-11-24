import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        emeraldDeep: "#0B251C",
        emeraldMid: "#143D30",
        emeraldSurface: "#1B4D3D",
        ivoryWarm: "#F7F6F2",
        ivoryCard: "#FFFFFF",
        obsidianInk: "#111615",
        goldAntique: "#C6A868",
        goldChampagne: "#DFC28D",
        slateMuted: "#5A6360",
        borderSubtle: "#E2DFD7",
      },
      fontFamily: {
        display: ["var(--font-playfair)", "Georgia", "serif"],
        sans: ["var(--font-jakarta)", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;

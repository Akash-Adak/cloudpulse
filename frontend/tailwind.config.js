/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        ground: "#E8EDF0",   // cool concrete
        panel: "#FFFFFF",
        ink: "#14202B",
        muted: "#53626F",
        rule: "#C9D3DA",
        wash: "#F3F6F8",
        up: "#1B7A4B",
        down: "#BF3A2D",
        idle: "#9A6B12",
        signal: "#1A5A89",   // interactive blue
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', "system-ui", "-apple-system", "Segoe UI", "sans-serif"],
      },
    },
  },
  plugins: [],
};

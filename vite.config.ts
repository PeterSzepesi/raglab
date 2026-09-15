import { defineConfig } from "vite";

export default defineConfig({
  build: {
    emptyOutDir: false,
    minify: true,
    sourcemap: false,
    lib: {
      entry: "src/frontend/main.ts",
      formats: ["es"],
      fileName: () => "app.js"
    },
    outDir: "src/main/resources/static/assets",
    rollupOptions: {
      output: { assetFileNames: "[name][extname]" }
    }
  }
});

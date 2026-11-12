import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  test: {
    include: ["src/**/*.test.ts", "tests/**/*.test.ts"],
    // 실제 Supabase에 붙는 테스트는 npm run test:live로만 돈다
    exclude: ["**/node_modules/**", "tests/**/*.live.test.ts"],
    setupFiles: ["dotenv/config"],
  },
});

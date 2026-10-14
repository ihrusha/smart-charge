import "dotenv/config";
import app from "./app.js";
import { db } from "./db.js";

const port = Number(process.env.PORT) || 3003;

async function start() {
  await db.query("SELECT 1");

  const server = app.listen(port, () => {
    console.log(`Auth service listening on port ${port}`);
  });

  async function shutdown(signal: string) {
    console.log(`${signal} received; shutting down`);

    server.close(async (error: Error | undefined) => {
      if (error) {
        console.error("Error during shutdown:", error);
        process.exitCode = 1;
      }

      await db.end();
    });
  }

  process.on("SIGTERM", () => void shutdown("SIGTERM"));
  process.on("SIGINT", () => void shutdown("SIGINT"));
}

start().catch((error) => {
  console.error("Failed to start auth service:", error);
  process.exit(1);
});
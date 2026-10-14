import dotenv from "dotenv";

dotenv.config();
const port = Number(process.env.PORT) || 3003;

async function start() {
  const [{ default: app }, { db }] = await Promise.all([
    import("./app"),
    import("./db"),
  ]);

  await db.query("SELECT 1");

  const server = app.listen(port, () => {
    console.log(`Auth service listening on port ${port}`);
  });

  async function shutdown(signal: string) {
    console.log(`${signal} received; shutting down`);

    server.close(async (error) => {
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
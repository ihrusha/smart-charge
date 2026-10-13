import "dotenv";
import app from "./app";

const port = Number(process.env.PORT) || 3001;

const server = app.listen(port, () => {
  console.log(
    `Auth service listening on port ${port}`,
  );
});

function shutdown(signal: string) {
  console.log(`${signal} received; shutting down`);

  server.close((error) => {
    if (error) {
      console.error("Error during shutdown:", error);
      process.exitCode = 1;
    }
  });
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
import { Pool } from "pg";

const connectionString = process.env.DATABASE_URL;

if (!connectionString) {
  throw new Error("DATABASE_URL is required");
}

export const db = new Pool({
  connectionString,
  // Use SSL configuration appropriate to your deployment environment.
});

db.on("error", (error) => {
  console.error("Unexpected PostgreSQL pool error:", error);
});
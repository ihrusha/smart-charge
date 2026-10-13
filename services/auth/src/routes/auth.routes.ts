import { Router } from "express";
import argon2 from "argon2";
import { db } from "../db";
import jwt from "jsonwebtoken";
import { requireAuth } from "../middlewares/require-auth";

const router = Router();

router.post("/register", async (req, res, next) => {
  const { email, password } = req.body ?? {};

  if (typeof email !== "string" || typeof password !== "string") {
    return res.status(400).json({ error: "Email and password are required" });
  }

  const normalizedEmail = email.trim().toLowerCase();

  if (
    normalizedEmail.length > 254 ||
    !/^\S+@\S+\.\S+$/.test(normalizedEmail) ||
    password.length < 12 ||
    password.length > 128
  ) {
    return res.status(400).json({
      error: "Provide a valid email and a password between 12 and 128 characters",
    });
  }

  try {
    const passwordHash = await argon2.hash(password);

    const result = await db.query(
      `INSERT INTO users (email, password_hash)
       VALUES ($1, $2)
       RETURNING id, email, created_at`,
      [normalizedEmail, passwordHash],
    );

    return res.status(201).json({ user: result.rows[0] });
  } catch (error) {
    // PostgreSQL error code 23505 means a unique constraint was violated.
    if ((error as { code?: string }).code === "23505") {
      return res.status(409).json({ error: "An account with that email already exists" });
    }

    return next(error);
  }
});

const jwtSecret = process.env.JWT_SECRET;

if (!jwtSecret) {
  throw new Error("JWT_SECRET is required");
}

router.post("/login", async (req, res, next) => {
  const { email, password } = req.body ?? {};

  if (typeof email !== "string" || typeof password !== "string") {
    return res.status(400).json({ error: "Email and password are required" });
  }

  const normalizedEmail = email.trim().toLowerCase();

  try {
    const result = await db.query(
      `SELECT id, email, password_hash
       FROM users
       WHERE LOWER(email) = LOWER($1)`,
      [normalizedEmail],
    );

    const user = result.rows[0];

    // Use the same response for an unknown email and an incorrect password.
    if (!user || !(await argon2.verify(user.password_hash, password))) {
      return res.status(401).json({ error: "Invalid email or password" });
    }

    const accessToken = jwt.sign(
      {},
      jwtSecret,
      {
        algorithm: "HS256",
        subject: user.id,
        issuer: "smartcharge-auth",
        audience: "smartcharge-api",
        expiresIn: "15m",
      },
    );

    return res.status(200).json({
      accessToken,
      tokenType: "Bearer",
      expiresIn: 900,
    });
  } catch (error) {
    return next(error);
  }
});

router.get("/me", requireAuth, async (_req, res, next) => {
  try {
    const result = await db.query(
      `SELECT id, email, created_at
       FROM users
       WHERE id = $1`,
      [res.locals.userId],
    );

    const user = result.rows[0];

    if (!user) {
      return res.status(404).json({ error: "User not found" });
    }

    return res.status(200).json({ user });
  } catch (error) {
    return next(error);
  }
});

export default router;
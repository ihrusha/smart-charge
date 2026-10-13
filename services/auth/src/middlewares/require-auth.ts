import type { RequestHandler } from "express";
import jwt from "jsonwebtoken";

const jwtSecret = process.env.JWT_SECRET;

if (!jwtSecret) {
  throw new Error("JWT_SECRET is required");
}

export const requireAuth: RequestHandler = (req, res, next) => {
  const match = req.get("authorization")?.match(/^Bearer\s+(.+)$/i);

  if (!match) {
    return res.status(401).json({ error: "Authentication required" });
  }

  try {
    const payload = jwt.verify(match[1], jwtSecret, {
      algorithms: ["HS256"],
      issuer: "smartcharge-auth",
      audience: "smartcharge-api",
    });

    if (typeof payload === "string" || typeof payload.sub !== "string") {
      return res.status(401).json({ error: "Invalid token" });
    }

    // This ID came from a token whose signature and claims were verified.
    res.locals.userId = payload.sub;
    return next();
  } catch {
    return res.status(401).json({ error: "Invalid or expired token" });
  }
};
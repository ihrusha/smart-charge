exports.up = (pgm) => {
  pgm.createTable("users", {
    id: {
      type: "uuid",
      primaryKey: true,
      default: pgm.func("gen_random_uuid()"),
    },
    email: {
      type: "text",
      notNull: true,
    },
    password_hash: {
      type: "text",
      notNull: true,
    },
    created_at: {
      type: "timestamptz",
      notNull: true,
      default: pgm.func("now()"),
    },
    updated_at: {
        type: "timestamptz",
        notNull: true,
        default: pgm.func("now()"),
    },
  });

  // Require email addresses to be unique regardless of letter casing.
  pgm.sql(
    "CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email))",
  );
};

exports.down = (pgm) => {
  pgm.dropTable("users");
};
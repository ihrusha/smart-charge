db = db.getSiblingDB("smartcharge_reservations");
db.createUser({
  user: "reservations_app",
  pwd: process.env.APP_PASSWORD,
  roles: [{ role: "readWrite", db: "smartcharge_reservations" }],
});
db = db.getSiblingDB("smartcharge_bornes");
db.createUser({
  user: "bornes_app",
  pwd: process.env.APP_PASSWORD,
  roles: [{ role: "readWrite", db: "smartcharge_bornes" }],
});
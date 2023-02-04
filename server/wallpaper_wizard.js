const express = require("express");
const request = require("request");
const multer = require("multer");
const fs = require("fs");
const crypto = require("crypto");
const sqlite3 = require("sqlite3");
const swaggerUi = require("swagger-ui-express");
const swaggerDocument = require("./openapi.json");

const connection = new sqlite3.Database("data/wallpaper_wizard.db");

function generateRandomFilename(filename) {
  const randomString = crypto.randomBytes(8).toString("hex");
  const parts = filename.split(".");
  return `${randomString}.${parts[1]}`;
}

function getRandomInt(max) {
  min = 0;
  max = Math.floor(max);
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getWallpaperByTags(tags, callback) {
  tags_query_string = "";
  if (tags.length >= 1) {
    tags_query_string += `WHERE tags like '%${tags[0]}%'`;
    for (let tag of tags.slice(1)) {
      tags_query_string += ` AND tags like '%${tag}%'`;
    }
  }
  connection.all(
    `SELECT * FROM wallpaper ${tags_query_string};`,
    (errors, results) => {
      console.log(results);
      callback(errors, results);
    }
  );
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const directory = `data/uploads/`;
    if (!fs.existsSync(directory)) {
      fs.mkdirSync(directory);
    }
    cb(null, directory);
  },
  filename: (req, file, cb) => {
    req.filename = generateRandomFilename(file.originalname);
    cb(null, req.filename);
  },
});

const upload = multer({ storage: storage });

const app = express();
let pwd = process.cwd();

app.get("/tags", (req, res) => {
  connection.all(`SELECT tags FROM wallpaper;`, (error, results) => {
    if (error) {
      res.statusCode = 404;
      res.send(
        JSON.stringify({
          message: "An Error occured while querying for tags",
          error: error,
        })
      );
    }
    tags = [];
    for (let row of results) {
      tags.push(...row.tags.split(";"));
    }
    tags = Array.from(new Set(tags));
    console.log(tags);
    let return_tags = [];
    for (let tag in tags) {
      if (tags[tag] !== "") {
        return_tags.push(tags[tag]);
      }
    }
    res.statusCode = 200;
    res.send(JSON.stringify({ tags: return_tags }));
  });
});

app.get("/wallpaper/list", (req, res) => {
  connection.all(`SELECT * FROM wallpaper;`, (error, results) => {
    if (error) {
      res.statusCode = 404;
      res.send(
        JSON.stringify({
          message: "An Error occured while querying for wallpapers",
          error: error,
        })
      );
    }
    wallpaper_info_array = [];
    for (let row of results) {
      tags = row.tags.split(";");
      wallpaper_info_array.push({ name: row.name, tags: tags });
    }
    res.statusCode = 200;
    res.send(JSON.stringify({ wallpapers: wallpaper_info_array }));
  });
});

app.get("/wallpaper/:wallpaperName", (req, res) => {
  connection.all(
    `SELECT * FROM wallpaper WHERE name='${req.params.wallpaperName}';`,
    (error, results) => {
      if (error || results.length == 0) {
        res.statusCode = 404;
        res.send(
          JSON.stringify({
            message: "An Error occured while querying for the wallpaper",
            error: error,
          })
        );
      }
      res.statusCode = 200;
      res.header("crop", results[0].crop)
      res.sendFile(`${pwd}/data/uploads/${results[0].name}`);
    }
  );
});

app.get("/wallpaper", (req, res) => {
  if (req.query.sync) {
    connection.all(
      `SELECT * FROM sync WHERE date >=  datetime('now', '-1 day') AND sync_name='${req.query.sync}' ORDER BY date DESC LIMIT 1;`,
      (error, results) => {
        if (error) throw error;
        if (results.length == 0) {
          var tags = req.query.tags ? req.query.tags.split(";") : [];
          getWallpaperByTags(tags, (error, results) => {
            if (error) throw error;
            if (results.length == 0) {
              res.statusCode = 404;
              res.send(
                JSON.stringify({
                  message: "Cant find a wallpaper with your tags",
                })
              );
            } else {
              let chosen_wallpaper = results[getRandomInt(results.length - 1)];
              connection.all(
                `INSERT INTO sync (sync_name, date, wallpaper) VALUES ('${req.query.sync}', CURRENT_TIMESTAMP, '${chosen_wallpaper.name}');`,
                (error, results) => {
                  if (error) {
                    res.statusCode = 500;
                    res.send(
                      JSON.stringify({
                        message: "Cant save your sync to the database",
                      })
                    );
                  } else {
                    res.header("crop", chosen_wallpaper.crop)
                    res.sendFile(
                      `${pwd}/data/uploads/${chosen_wallpaper.name}`
                    );
                  }
                }
              );
            }
          });
        } else {
          let chosen_wallpaper = results[0];
          let pwd = process.cwd();
          res.header("crop", chosen_wallpaper.crop)
          res.sendFile(`${pwd}/data/uploads/${chosen_wallpaper.wallpaper}`);
        }
      }
    );
  } else {
    var tags = [];
    if (req.query.tags != undefined) {
      tags = req.query.tags.split(";");
    }
    getWallpaperByTags(tags, (error, results) => {
      if (error) throw error;
      if (results.length == 0) {
        res.statusCode = 404;
        res.send(
          JSON.stringify({
            message: "Cant find a wallpaper with your tags",
          })
        );
      } else {
        let chosen_wallpaper = results[getRandomInt(results.length - 1)];
        let pwd = process.cwd();
        connection.all(
          `INSERT INTO sync (sync_name, date, wallpaper) VALUES ('${req.query.sync}', CURRENT_TIMESTAMP, '${chosen_wallpaper.name}');`,
          (error, results) => {
            res.header("crop", chosen_wallpaper.crop)
            res.sendFile(`${pwd}/data/uploads/${chosen_wallpaper.name}`);
          }
        );
      }
    });
  }
});

app.get("/crop/:wallpaper_name", (req, res) => {
  connection.all(`SELECT crop FROM wallpaper WHERE name='${wallpaper_name}'`)
});

app.use("/api-docs", swaggerUi.serve, swaggerUi.setup(swaggerDocument));

app.post("/wallpaper", upload.single("image"), (req, res) => {
  console.log(req.file);
  console.log(req.query);
  let tags = req.query.tags.endsWith(";")
    ? req.query.tags.substring(0, req.query.tags.length - 1)
    : req.query.tags;
  let crop = req.query.crop ? req.query.crop : ""
  connection.run(
    `
    INSERT INTO wallpaper (name, tags, crop)
    VALUES ('${req.filename}', '${tags}', '${crop}');
  `,
    (error) => {
      if (error) throw error;
      console.log("Data inserted");
    }
  );
  res.send("Image received");
});

app.put("/wallpaper/:wallpaper_name", (req, res) => {
  connection.run(
    `
    UPDATE wallpaper SET tags='${req.query.tags}'
    WHERE name='${req.params.wallpaper_name}';
  `,
    (error, results) => {
      if (error) throw error;
      console.log(results);
    }
  );
  res.send("Updated image");
});

app.listen(3000, () => {
  console.log("Server listening on port 3000");

  connection.all(
    `SELECT name FROM sqlite_master WHERE type='table' and name like 'wallpaper';`,
    (error, results) => {
      if (error) throw error;

      if (results.length === 0) {
        // Table does not exist, create it
        connection.run(
          `
        CREATE TABLE wallpaper (
          name VARCHAR(255) NOT NULL,
	  tags VARCHAR(255) NOT NULL,
    crop VARCHAR(255) NOT NULL
        )
      `,
          (error) => {
            if (error) throw error;
            console.log("Table created");
          }
        );
      } else {
        console.log("Table already exists");
      }
    }
  );
  connection.all(
    `SELECT name FROM sqlite_master WHERE type='table' and name like 'sync';`,
    (error, results) => {
      if (error) throw error;

      if (results.length === 0) {
        // Table does not exist, create it
        connection.run(
          `
        CREATE TABLE sync (
          sync_name VARCHAR(255) NOT NULL,
	  date DATE NOT NULL,
          wallpaper VARCHAR(255) NOT NULL
        )
      `,
          (error) => {
            if (error) throw error;
            console.log("Table created");
          }
        );
      } else {
        console.log("Table already exists");
      }
    }
  );
});

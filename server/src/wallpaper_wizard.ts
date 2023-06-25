import { Request, Response } from "express";

import express = require("express");
import multer = require("multer");
import { nextTick } from "process";
const fs = require("fs");
const crypto_lib = require("crypto");
const sqlite3 = require("sqlite3");
const swaggerUi = require("swagger-ui-express");
const swaggerDocument = require("./openapi.json");
const imageThumbnail = require("image-thumbnail");

const connection = new sqlite3.Database("./data/wallpaper_wizard.db");

interface WallpaperRowScheme {
  name: string;
  tags: string;
  crop: string;
}
interface SyncRowScheme {
  sync: string;
  wallpaper: string;
}

function generateRandomFilename(filename: string) {
  const randomString = crypto_lib.randomBytes(8).toString("hex");
  const parts = filename.split(".");
  return `${randomString}.${parts[parts.length - 1]}`;
}

function getRandomInt(max: number) {
  let min: number = 0;
  max = Math.floor(max);
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getWallpaperByTags(tags: Array<string>, callback: Function) {
  let tags_query_string: String = "";
  if (tags.length >= 1) {
    tags_query_string += `WHERE tags like '%${tags[0]}%'`;
    for (let tag of tags.slice(1)) {
      tags_query_string += ` AND tags like '%${tag}%'`;
    }
  }
  connection.all(
    `SELECT * FROM wallpaper ${tags_query_string};`,
    (errors: Error, results: Array<WallpaperRowScheme>) => {
      console.log(results);
      callback(errors, results);
    }
  );
}

const storage = multer.diskStorage({
  destination: (req: Request, file: Express.Multer.File, cb: Function) => {
    const directory = `data/uploads/`;
    cb(null, directory);
  },
  filename: (req: Express.Request, file: Express.Multer.File, cb: Function) => {
    file.filename = generateRandomFilename(file.originalname);
    cb(null, file.filename);
  },
});

const upload = multer({ storage: storage });

const app = express();
let pwd = process.cwd();

app.get("/tags", (req: Request, res: Response) => {
  console.log("Request: /tags");
  let db_query: string = `SELECT tags FROM wallpaper;`;
  console.log(db_query);
  connection.all(
    db_query,
    (error: Error, results: Array<WallpaperRowScheme>) => {
      if (error) {
        res.statusCode = 404;
        res.send(
          JSON.stringify({
            message: "An Error occured while querying for tags \n",
            error: error,
          })
        );
      }
      let tags: Array<String> = [];
      for (let row of results) {
        tags.push(...row.tags.split(";"));
      }
      tags = Array.from(new Set(tags));
      console.log("Found: " + tags + "\n");
      let return_tags = [];
      for (let tag in tags) {
        if (tags[tag] !== "") {
          return_tags.push(tags[tag]);
        }
      }
      res.statusCode = 200;
      res.send(JSON.stringify({ tags: return_tags }));
    }
  );
});

app.get("/wallpaper/list", (req: Request, res: Response) => {
  console.log("Request: /wallpaper/list");
  let db_query: string = `SELECT * FROM wallpaper;`;
  console.log(db_query);
  connection.all(
    db_query,
    (error: Error, results: Array<{ tags: string; name: string }>) => {
      if (error) {
        res.statusCode = 404;
        res.send(
          JSON.stringify({
            message: "An Error occured while querying for wallpapers",
            error: error,
          })
        );
      }
      let wallpaper_info_array: Array<object> = [];
      for (let row of results) {
        let tags: Array<String> = row.tags.split(";");
        wallpaper_info_array.push({ name: row.name, tags: tags });
      }
      console.log("Found: " + wallpaper_info_array + "\n");
      res.statusCode = 200;
      res.send(JSON.stringify({ wallpapers: wallpaper_info_array }));
    }
  );
});

app.get("/wallpaper/:wallpaperName", (req: Request, res: Response) => {
  console.log(`Request: /wallpaper/${req.params.wallpaperName}`);
  let db_query: string = `SELECT * FROM wallpaper WHERE name='${req.params.wallpaperName}';`;
  console.log(db_query);
  connection.all(
    db_query,
    (error: Error, results: Array<{ name: string; crop: string }>) => {
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
      res.header("crop", results[0].crop);
      res.sendFile(`${pwd}/data/uploads/${results[0].name}`);
    }
  );
});

app.get("/thumbnail/:wallpaperName", (req: Request, res: Response) => {
  console.log(`Request: /thumbnail/${req.params.wallpaperName}`);
  let db_query: string = `SELECT * FROM wallpaper WHERE name='${req.params.wallpaperName}';`;
  console.log(db_query);
  connection.all(
    db_query,
    (error: Error, results: Array<{ name: string; crop: string }>) => {
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
      res.header("crop", results[0].crop);
      res.sendFile(`${pwd}/data/uploads/thumbnails/thumb_${results[0].name}`);
    }
  );
});

app.get("/wallpaper", (req: express.Request, res: Response) => {
  console.log(
    "Request: /wallpaper, sync=" +
      req.query.sync +
      " tags=" +
      req.query.tags +
      " follow=" +
      req.query.follow
  );

  if (req.query.sync) {
    let db_query = `SELECT * FROM sync WHERE sync_name='${req.query.sync}' ORDER BY date DESC LIMIT 1;`;
    console.log(db_query);
    connection.all(db_query, (error: Error, results: Array<SyncRowScheme>) => {
      if (results.length == 0 || req.query.follow != "true") {
        var tags: Array<string> =
          typeof req.query.tags == "string" ? req.query.tags.split(";") : [];
        getWallpaperByTags(
          tags,
          (error: Error, results: Array<WallpaperRowScheme>) => {
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
              let db_query = `INSERT INTO sync (sync_name, date, wallpaper) VALUES ('${req.query.sync}', CURRENT_TIMESTAMP, '${chosen_wallpaper.name}');`;
              console.log(db_query);
              connection.all(
                db_query,
                (error: Error, results: Array<SyncRowScheme>) => {
                  if (error) {
                    res.statusCode = 500;
                    res.send(
                      JSON.stringify({
                        message: "Cant save your sync to the database",
                      })
                    );
                  } else {
                    res.header("crop", chosen_wallpaper.crop);
                    res.sendFile(
                      `${pwd}/data/uploads/${chosen_wallpaper.name}`
                    );
                  }
                }
              );
            }
          }
        );
      } else {
        let chosen_wallpaper = results[0];
        let pwd = process.cwd();
        let db_query = `SELECT * FROM wallpaper WHERE name='${chosen_wallpaper.wallpaper}';`;
        console.log(db_query);
        connection.all(
          db_query,
          (errors: Error, results: Array<WallpaperRowScheme>) => {
            if (errors) {
              res.statusCode = 500;
              res.send("Error while getting info on the wallpaper");
            }
            console.log(results);
            res.header("crop", results[0].crop);
            res.sendFile(`${pwd}/data/uploads/${chosen_wallpaper.wallpaper}`);
          }
        );
      }
    });
  } else {
    var tags: Array<string> = [];
    if (typeof req.query.tags == "string") {
      tags = req.query.tags.split(";");
    }
    getWallpaperByTags(
      tags,
      (error: Error, results: Array<WallpaperRowScheme>) => {
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

          res.header("crop", chosen_wallpaper.crop);
          res.sendFile(`${pwd}/data/uploads/${chosen_wallpaper.name}`);
        }
      }
    );
  }
});

app.get("/crop/:wallpaper_name", (req: Request, res: Response) => {
  connection.all(
    `SELECT crop FROM wallpaper WHERE name='${req.params.wallpaper_name}'`
  );
});

app.use("/api-docs", swaggerUi.serve, swaggerUi.setup(swaggerDocument));

app.post(
  "/wallpaper",
  upload.single("image"),
  (req: Request, res: Response, next: Function) => {
    imageThumbnail(req.file?.path).then((thumbnail: Buffer) => {
      fs.open(
        `data/uploads/thumbnails/thumb_${req.file?.filename}`,
        "w",
        function (err: Error, fd: number) {
          if (err) {
            res.status(500).send("Problem generating a thumbnail");
          }
          fs.write(fd, thumbnail, () => {
            next();
          });
        }
      );
    });
  },
  (req: Request, res: Response) => {
    console.log(req.query);
    if (typeof req.query.tags != "string") return;
    let tags: string = req.query.tags.endsWith(";")
      ? req.query.tags.substring(0, req.query.tags.length - 1)
      : req.query.tags;
    let crop = req.query.crop ? req.query.crop : "";
    connection.run(
      `
    INSERT INTO wallpaper (name, tags, crop)
    VALUES ('${req.file!!.filename}', '${tags}', '${crop}');
  `,
      (error: Error) => {
        if (error) throw error;
        console.log("Data inserted");
      }
    );
    res.send("Image received");
  },
  function (err: Error, req: Request, res: Response) {
    console.error(err);
    res.status(500).send("An error occurred");
  }
);

app.put("/wallpaper/:wallpaper_name", (req, res) => {
  connection.run(
    `
    UPDATE wallpaper SET tags='${req.query.tags}', crop='${req.query.crop}'
    WHERE name='${req.params.wallpaper_name}';
  `,
    (error: Error, results: Array<string>) => {
      if (error) throw error;
      console.log(results);
    }
  );
  res.send("Updated wallpaper");
});

app.listen(3000, () => {
  console.log("Server listening on port 3000");
  if (!fs.existsSync("data")) {
    fs.mkdirSync("data");
  }
  if (!fs.existsSync("data/uploads")) {
    fs.mkdirSync("data/uploads");
  }
  if (!fs.existsSync("data/uploads/thumbnails")) {
    fs.mkdirSync("data/uploads/thumbnails");
  }

  connection.all(
    `SELECT name FROM sqlite_master WHERE type='table' and name like 'wallpaper';`,
    (error: Error, results: Array<string>) => {
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
          (error: Error) => {
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
    (error: Error, results: Array<string>) => {
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
          (error: Error) => {
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

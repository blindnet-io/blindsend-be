# Blindsend back-end

This project is a server, back-end for [blindsend](https://github.com/blindnet-io/blindsend), an open source tool for private, end-to-end encrypted file exchange between two parties.

Blindsend back-end provides a REST API for managing file exchange workflow. There are implementations for Google Cloud Storage and PostgreSQL for storing encrypted files. 

Blindsend back-end is intended for two usage scenarios:
1. To be deployed together with [blindsend front-end](https://github.com/blindnet-io/blindsend-fe), and serve as a SaaS application. A demo is avalable [here](https://blindsend.work)
2. To be deployed as a file exchange API and used in external software projects for file exchange. A [Java library](https://github.com/blindnet-io/blindsend-examples-java) that relies on blindsend API for private file exchange is currently under development

## Installation instructions

To build blindsend back-end, you need to have [sbt](https://www.scala-sbt.org/download.html) installed.

### Build and run PostgreSQL

This section provides details on how to run and configure PostgreSQL database usign Docker. If you do not have Docker Engine intalled, you can follow instructions for installation [here](https://docs.docker.com/engine/install/). Alternativelly you can run and configure PostgreSQL directly without using Docker. 

To run PostgreSQL with Docker:
1. Create `Dockerfile` with the following content
```Dockerfile
FROM postgres:11
ENV POSTGRES_DB blindsend
ENV POSTGRES_USER default
ENV POSTGRES_PASSWORD changeme
ADD blindsend.sql /docker-entrypoint-initdb.d/
```
2. Create `blindsend.sql` file with the following content
```SQL
CREATE TABLE IF NOT EXISTS FilesLO (
    id varchar NOT NULL,
    fileoid oid NOT NULL
);
```
3. Run
```bash
sudo docker build -t blindsend-db .
sudo docker run -p 5432:5432 -d --name blindsend-db-instance blindsend-db
 ```

### Running blindsend back-end

To run blindsend back-end:
1. Create a configuration file `app.conf` in the folder of your choice
```conf
domain = <your_domain>
assets-dir = dist # where the front-end files are
cors = false # true if front-end is on different domain
max-file-size = 150994944 # maximum encrypted file size in bytes
storage = {
  type = postgres-large-objects
  host = "localhost:5432"
  db = blindsend
  user = default
  pass = changeme
}
link-repo = {
  type = in-memory # where file metadata is stored
}
```
If you are testing an instance of blindsend on your local machine, specify `http://0.0.0.0:9000` in the `domain` field.  
If using the Google Cloud Storage, obtain [`account.json` file](https://cloud.google.com/docs/authentication/getting-started).
```conf
storage = {
  type = google-cloud-storage
  account-file-path = account.json # relative path to account.json file to root of project or .jar file
  bucket-name = blindsend-files
  token-refresh-rate = 600 # how often to fetch a new access token
}
```

2. In the project root run the following command to create `blindsend.jar` file in `target/scala-2.13/` folder
```bash
sbt assembly
```
3. Ensure `app.conf` is in the root of project or `blindsend.jar` executable, run
```bash
java -jar blindsend.jar
```

## Front-end integration

To include a front-end client for your blindsend back-end, follow the instructions in the [blindsend front-end](https://github.com/blindnet-io/blindsend-fe) to create a `dist` folder, and put it together with your `blindsend.jar` and `app.conf` files (and `account.json` if using Google Cloud Storage) before running blindsend back-end.

## Security

It is strongly recommended that your blindsend back-end instance is running on https. Otherwise, it should never be used for purposes other than testing.

## Design

Back-end acts merely as a proxy for file transfers, encryption/decryption happens on front-end.  

Currently, file metadata is stored in-memory. So after the server is restarted, file metadata and corresponding link ids are deleted. It will be changed to PostreSQL in the future.  

File retention period is currently not handled. Files must be deleted manually.  

Chunked file upload works only with Google Cloud Storage. For PostgreSQL, make sure to set `uploadChunkSize` in `globals.ts` file in the [front-end project](https://github.com/blindnet-io/blindsend-fe) to be same as `max-file-size`. This means the entire file will be loaded into memory and encrypted on front-end side. Watch out for the browsers not handling well a lot of data in memory, so `max-file-size` shouldn't be more than 100-200 Mb.

## Libraries

- [Typelevel stack](https://typelevel.org/), mainly [http4s](https://http4s.org/), [fs2](https://fs2.io/), [cats-effect](https://typelevel.org/cats-effect/) and [doobie](https://tpolecat.github.io/doobie/)
- [Bouncy Castle](https://bouncycastle.org/)

## Current status
This project has been started by [blindnet.io](https://blindnet.io/) and is currently under development.
### TODOS
- code refactoring
- code documentation
- implement file metadata storage
- implement file retention policy
- authentication
- tests
- document http routes
- document design
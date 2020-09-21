# Blindsend server

This project is a server for [blindsend](https://github.com/blindnet-io/blindsend), an open source tool for private, end-to-end encrypted file exchange between two parties. It provides a REST API for managing file exchange workflow. 

Blindsend server is intended for two usage scenarios:
1. To be deployed together with [blindsend web UI](https://github.com/blindnet-io/blindsend-fe), and serve as a SaaS application. A demo is avalable [here](https://blindsend.xyz)
2. To be deployed as a file exchange API and used in external software projects for private file exchange. A [Java library](https://github.com/blindnet-io/blindsend-java-lib) that relies on blindsend API for private file exchange is currently under development

## Installation instructions

To build blindsend server, you need to have [sbt](https://www.scala-sbt.org/download.html) installed.

### Setting up file storage

Blindsend supports two options for storign files, PostgresSQL and Google Cloud Storage.

#### PostgreSQL

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

#### Google Cloud Storage

To use Google Cloud Storage, you need to [create a bucket](https://cloud.google.com/storage/docs/creating-buckets) on GCP, and then to obtain the [`account.json` file](https://cloud.google.com/docs/authentication/getting-started).

### Configuration

Before running blindsend server, create a configuration file `app.conf` in the folder of your choice. If testing an instance of blindsend on your local machine, specify `http://0.0.0.0:9000` in the `domain` field (this field is used as a prefix for file exchange links). 

If using PostgreSQL for file storage:
```conf
domain = <your_domain>
assets-dir = dist # where the web ui files are
cors = false # true if web ui is on different domain
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

If using Google Cloud Storage:
```conf
domain = <your_domain>
assets-dir = dist # where the web ui files are
cors = false # true if web ui is on different domain
max-file-size = 150994944 # maximum encrypted file size in bytes
storage = {
  type = google-cloud-storage
  account-file-path = account.json # relative path to account.json file to root of project or .jar file
  bucket-name = <your_bucket_name>
  token-refresh-rate = 600 # how often to fetch a new access token
}
link-repo = {
  type = in-memory # where file metadata is stored
}
``` 

### Running blindsend server

To run blindsend server:
1. In the project root run the following command to create `blindsend.jar` file in `target/scala-2.13/` folder
```bash
sbt assembly
```
2. Ensure `app.conf` is in the same location as `blindsend.jar` executable, and run
```bash
java -jar blindsend.jar
```
This will run the API on `http://0.0.0.0:9000/api`.

## Web client integration

To include a web client for your blindsend server, follow the instructions in the [blindsend web UI project](https://github.com/blindnet-io/blindsend-fe) to create a `dist` folder, and put it together with your `blindsend.jar` and `app.conf` files (and `account.json` if using Google Cloud Storage) before running the server.

## Security

It is strongly recommended that your blindsend server instance is running on https. Otherwise, it should never be used for purposes other than testing.

## Design

Blindsend server acts merely as a proxy for file exchange, encryption/decryption takes place on the client side.  

Currently, file metadata is stored in-memory. So after the server is restarted, file metadata and corresponding link ids are deleted. We are currently working to support PostreSQL for file metadata storage.  

File retention period is currently not handled and will be supported in the future. Files therefore must be deleted manually.   

## Dependencies

- [Typelevel stack](https://typelevel.org/), mainly [http4s](https://http4s.org/), [fs2](https://fs2.io/), [cats-effect](https://typelevel.org/cats-effect/) and [doobie](https://tpolecat.github.io/doobie/)
- [Bouncy Castle](https://bouncycastle.org/)

## What's coming next?

We are currently working to provide:
- File metadata storage in PostgreSQL
- File retention mechanism
- ... and other features and improvements

## Current status
This project has been started by [blindnet.io](https://blindnet.io/) and is currently under development.
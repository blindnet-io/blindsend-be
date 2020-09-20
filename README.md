# Blindsend back-end

This project is a back-end for [blindsend](https://github.com/blindnet-io/blindsend), an open source tool for private, end-to-end encrypted file exchange between two parties.

Blindsend back-end provides a REST API for managing file exchange workflow. This open source version uses PostgreSQL for storing encrypted files. 

Blindsend back-end is intended for two usage scenarios:
1. To be deployed together with [blindsend front-end](https://github.com/blindnet-io/blindsend-fe), and serve as a SaaS application. A demo is avalable [here](https://blindsend.xyz)
2. To be deployed as a private file exchange API and used in external software projects for private file exchange. A [Java library](https://github.com/blindnet-io/blindsend-examples-java) that relies on blindsend API for private file exchange is currently under development

## Installation instructions

To install blindsend back-end, you need to have [sbt](https://www.scala-sbt.org/download.html) installed.

### Install and run PostgreSQL

This section provides details on how to run and configure PostgreSQL database usign Docker. If you do not have Docker Engine intalled, you can follow instructions for installation [here](https://docs.docker.com/engine/install/). Alternativelly you can run and configure PostgreSQL directly without using Docker. 

To run PostgreSQL with Docker:
1. Create a folder `postgres-blindsend` 
2. Create a `Dockerfile` in `postgres-blindsend` with the following content
```Dockerfile
FROM postgres:11
ENV POSTGRES_DB blindsend
ENV POSTGRES_USER default
ENV POSTGRES_PASSWORD changeme
ADD blindsend.sql /docker-entrypoint-initdb.d/
```
3. Create `blindsend.sql` file in `postgres-blindsend` with the following content
```SQL
CREATE TABLE IF NOT EXISTS FilesLO (
    id varchar NOT NULL,
    fileoid oid NOT NULL
);
```
4. Inside `postgres-blindsend` run
```bash
 sudo docker build -t blindsend-db .
 sudo docker run -p 5432:5432 -d --name db blindsend-db
 ```

 ### Running blindsend back-end

 To run blindsend back-end:
 1. Create a configuration file `app.conf` in the folder of your choice
 ```conf
domain = <your_domain>
assets-dir = dist
cors = true
max-file-size = 150994944
storage = {
  type = postgres-large-objects
  host = "localhost:5432"
  db = blindsend
  user = default
  pass = changeme
}
link-repo = {
  type = in-memory
}
If you are testing an instance of blindsend on your local machine, specify `http://0.0.0.0:9000` in the `domain` field.
```
 2. In the project root run the following command to create `blindsend.jar` file in `target/scala-2.13/` folder
 ```bash
 sbt assembly
 ```
 3. Move `blindsend.jar` to the same folder where your `app.conf` is, navigate to it and run
 ```bash
 java -jar blindsend.jar
 ```
 This will run blindsend back-end on `{domain}/api`.

 ## Front-end integration

 To include a front-end client for your blindsend back-end, follow the instructions in the [blindsend front-end](https://github.com/blindnet-io/blindsend-fe) to create a `dist` folder, and put it together with your `blindsend.jar` and `app.conf` files before running blindsend back-end. The front-end will be reachable at `{domain}`.

 ## Security
 
 It is strongly recommended that your blindsend back-end instance is running on https. Otherwise, it should never be used for purposes other than testing.

 ## Current status
This project has been started by [blindnet.io](https://blindnet.io/) and is currently under development.
# Docker Config
This is a sample server setup using docker-compose.

## Set up
Create a directory for the service e.g. `/srv/sensor_recorder`. Navigate into this new directory.  
Create the directories `src` `data` `conf/nginx/`.  
Place the `Webserver` directory in `./src/`.
Place the `default.conf` in `./conf/nginx/`.
Run `docker-compose up -d`.
Open the url `localhost:80`

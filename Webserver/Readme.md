# Flask Webserver
This webserver manges your recording files. The WearOS application uploads its different files which can be observed or downloaded within the frontend.

# How does it work
## Authentication
Due to anonymity, I didn't want user Accounts. Therefore, I use a basic auth for the frontend and a token auth for the upload process.
The user-credentials for basic auth are specified in the config file. There is also the server secret key for token auth which has to be delivered at each POST.

To request this token, a device has to be authenticated. In order to do this, the device request the token with a specific identifier. All open requests with their identifiers can be displayed in the frontend. The user can then grant the request for a identifier. The next request from this device is responded with the server secret. At this time the open request is deleted in order to be safe, that no other device can use the same identifier to get the token.

## Upload
To ensure, that all files from a run can be brought back together on the server, the device also sends an uuid with every post. This id is requested beforehand for each dataset.

## Storage
The uploaded files are stored in the `./uploads/` directory. I differentiate between sensor recorder files and tf model files. Sensor recording files are stored in `./uploads/recordings/` and tf model files in `./uploads/tf_models/`.  
Each uploaded file comes with a uuid. For each new uuid, the server creates a new folder accordingly. If this folder already exists all other files are stored in this one. 
At the same time, a zip file is created which also contains all files. This is used to facilitate the download of a whole set.

## Configuration
At the first start a new config file `./conf/config.yml` is created. This already contains a random generated server secret and all other default values.  
**please change the values for username and password**  
These settings can also be managed via the frontend.

# How to set up
## Manual
First install all requirements by `pip install -r requirements.txt`. After that, you can launch the flask server.  
You should use a specific WSGI server to run the flask service in production. I use `gunicorn`. To run the service you can type:  
`gunicorn --bind 0.0.0.0:5000 app:app`
I also to not deliver the uploads and static files over flask. I use a nginx server to do this. In its config you can set the locations as follows:
```
location /uploads {
    alias   /uploads;
}
location /static {
    alias   /static;
}
location / {
       proxy_http_version 1.1;

       proxy_set_header X-FORWARDED-FOR $remote_addr;
       proxy_set_header Upgrade $http_upgrade;
       proxy_set_header Connection "Upgrade"; 
       proxy_set_header Host $host;
       proxy_set_header Connection "";
       proxy_redirect off;
       proxy_read_timeout 300;
       proxy_pass 0.0.0.0:5000;
    }

```

In order to use the authentication from flask for the upload location you can use:
```
location /uploads {
        auth_request /auth;
        alias   /uploads;
    }
    
    location /auth {
        internal;
        proxy_pass http://0.0.0.0:5000/auth;
        proxy_pass_request_body off;
        proxy_set_header        Content-Length "";
        proxy_set_header        X-Original-URI $request_uri;
    }
```

## Docker way
I provide a dockerfile that does roughly what has just been described in the Manual section. You should also use a separate nginx service for all static files as seen before.  
See [DockerConfig](https://github.com/Alexsogge/MasterProject/DockerConfig) for my docker-compose setup.




FROM python:3.7-alpine
WORKDIR /code
ENV FLASK_APP=app.py
ENV FLASK_RUN_HOST=0.0.0.0
RUN apk add --no-cache git musl-dev linux-headers g++ gcc build-base libpng-dev openblas-dev freetype-dev py3-numpy-dev jpeg-dev zlib-dev freetype-dev lcms2-dev openjpeg-dev tiff-dev tk-dev tcl-dev

COPY requirements.txt requirements.txt
RUN pip install -r requirements.txt
EXPOSE 5000
COPY . .
# CMD ["flask", "run"]
CMD gunicorn --bind 0.0.0.0:5000 app:app


FROM python:3.7-slim
ENV FLASK_APP=app.py
ENV FLASK_RUN_HOST=0.0.0.0
# RUN apk add --no-cache --update git musl-dev linux-headers g++ gcc build-base libpng-dev openblas-dev freetype-dev py3-numpy-dev jpeg-dev zlib-dev freetype-dev lcms2-dev openjpeg-dev tiff-dev tk-dev tcl-dev cmake

# RUN apk add --no-cache --update protoc protobuf-dev protobuf protobuf-c libprotoc

RUN apt-get update && apt-get install -y --no-install-recommends \
         build-essential \
         git \
         vim \
         libprotoc-dev \
         protobuf-compiler \
         cmake \
         ca-certificates && \
    rm -rf /var/lib/apt/lists/*

RUN pip install --upgrade pip
RUN pip install torch torchvision torchaudio --extra-index-url https://download.pytorch.org/whl/cpu


WORKDIR /code
COPY requirements.txt requirements.txt
RUN pip install -r requirements.txt
ADD "https://www.random.org/cgi-bin/randbyte?nbytes=10&format=h" skipcache
RUN pip install -U git+https://github.com/Alexsogge/MasterProject.git#subdirectory=scripts 
RUN pip install -U git+https://gitlab.tf.uni-freiburg.de/henkela/handwashing_personalizer.git#subdirectory=src 

EXPOSE 5000
COPY . .
COPY ./static/* ./static/
# CMD ["flask", "run"]
ENV PYTHONUNBUFFERED=TRUE
CMD gunicorn -t 120 --bind 0.0.0.0:5000 app:app --workers=4 --enable-stdio-inheritance 


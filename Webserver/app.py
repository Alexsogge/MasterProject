import os
from flask import Flask
from flask_migrate import Migrate

from views import view, db
from config import UPLOAD_FOLDER, RECORDINGS_FOLDER, TFMODEL_FOLDER, config

app = Flask(__name__)
app.register_blueprint(view, url_prefix=config.url_prefix)
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///test.db'
db.init_app(app)

migrate = Migrate(app, db)


if not os.path.exists(UPLOAD_FOLDER):
    os.mkdir(UPLOAD_FOLDER)

if not os.path.exists(RECORDINGS_FOLDER):
    os.mkdir(RECORDINGS_FOLDER)

if not os.path.exists(TFMODEL_FOLDER):
    os.mkdir(TFMODEL_FOLDER)

if __name__ == '__main__':
    app.run()


import os
from flask import Flask, Response, redirect
from flask_migrate import Migrate

from views import view, db
from config import UPLOAD_FOLDER, RECORDINGS_FOLDER, TFMODEL_FOLDER, PARTICIPANT_FOLDER, config
from personalization_tools.pseudo_model_settings import pseudo_model_settings

import admin

app = Flask(__name__)
app.register_blueprint(view, url_prefix=config.url_prefix)
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['SQLALCHEMY_DATABASE_URI'] = config.database_uri
app.secret_key = config.server_secret
db.init_app(app)

migrate = Migrate(app, db)


if not os.path.exists(UPLOAD_FOLDER):
    os.mkdir(UPLOAD_FOLDER)

if not os.path.exists(RECORDINGS_FOLDER):
    os.mkdir(RECORDINGS_FOLDER)

if not os.path.exists(TFMODEL_FOLDER):
    os.mkdir(TFMODEL_FOLDER)

if not os.path.exists(PARTICIPANT_FOLDER):
    os.mkdir(PARTICIPANT_FOLDER)


@app.context_processor
def utility_processor():
    def get_pseudo_label_filter_description(filter_name):
        description = ''
        for setting in pseudo_model_settings[filter_name]:
            description += str(setting) + '\n'
        return description
    return dict(get_pseudo_label_filter_description=get_pseudo_label_filter_description)


admin.init(app, db)

if __name__ == '__main__':
    app.run()


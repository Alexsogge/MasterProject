from typing import Union

from flask import Response, redirect

from models import Recording, RecordingStats, Participant, ParticipantStats, ParticipantsTagSetting
from flask_admin import Admin
from flask_admin.contrib import sqla
from flask_basicauth import BasicAuth
from werkzeug.exceptions import HTTPException

from config import config

basic_auth: Union[None, BasicAuth] = None
class AuthException(HTTPException):
    def __init__(self, message):
        # python 3
        super().__init__(message, Response(
            message, 401,
            {'WWW-Authenticate': 'Basic realm="Login Required"'}
        ))

class ModelView(sqla.ModelView):
    def is_accessible(self):
        global basic_auth
        if not basic_auth.authenticate():
            raise AuthException('Not authenticated. Refresh the page.')
        else:
            return True

    def inaccessible_callback(self, name, **kwargs):
        global basic_auth
        return redirect(basic_auth.challenge())

class RecordingView(ModelView):
    column_searchable_list = ['path', ]
    column_list = ['id', 'path', 'stats']

class RecordingStatView(ModelView):
    column_list = ('id', )
    column_searchable_list = ('id', )

class ParticipantView(ModelView):
    column_list = ('id', 'android_id', 'alias', 'stats', 'tag_settings')
    column_searchable_list = ('id', 'android_id', 'alias')

class ParticipantStatsView(ModelView):
    column_list = ('id', )
    column_searchable_list = ('id', )

class ParticipantTagSettingsView(ModelView):
    column_list = ('id', )
    column_searchable_list = ('id', )

def init(app, db):
    global basic_auth
    app.config['BASIC_AUTH_USERNAME'] = config.user
    app.config['BASIC_AUTH_PASSWORD'] = config.user_pw
    basic_auth = BasicAuth(app)

    admin = Admin(app, name='Dashboard', url=config.url_prefix + '/admin')
    admin.add_view(RecordingView(Recording, db.session))
    admin.add_view(RecordingStatView(RecordingStats, db.session))

    admin.add_view(ParticipantView(Participant, db.session, category='Participant'))
    admin.add_view(ParticipantStatsView(ParticipantStats, db.session, category='Participant'))
    admin.add_view(ParticipantTagSettingsView(ParticipantsTagSetting, db.session, category='Participant'))
import datetime
import os
from dateutil import parser

from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()


class AuthenticationRequest(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    identifier = db.Column(db.String(256), unique=True, nullable=False)
    granted = db.Column(db.Boolean, default=False)
    used = db.Column(db.Boolean, default=False)

    def __repr__(self):
        return f'<AuthRequest[{self.id}]: {self.identifier}>'

    def __str__(self):
        return f'<[{self.id}]: {self.identifier} | {self.granted} | {self.used}>'


participant_to_recording = db.Table('participant_to_recording',
                                    db.Column('recording_id', db.Integer,
                                              db.ForeignKey('recording.id', primary_key=True)),
                                    db.Column('participant_id', db.Integer,
                                              db.ForeignKey('participant.id', primary_key=True))
                                    )


class Participant(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    android_id = db.Column(db.String(256), nullable=True)
    alias = db.Column(db.String(256), nullable=True)
    recordings = db.relationship('Recording', secondary=participant_to_recording, lazy='subquery',
                                 backref=db.backref('participants', lazy=True))

    def get_name(self):
        if self.alias is not None:
            return self.alias
        if self.android_id is not None:
            return self.android_id
        return self.id

class Recording(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    path = db.Column(db.String(256), nullable=False)
    alias = db.Column(db.String(256), nullable=True)
    description = db.Column(db.TEXT, default='')

    last_changed = db.Column(db.DateTime, default=datetime.datetime.utcnow)

    stats = db.relationship('RecordingStats', backref='recording', lazy=True)
    meta_info = db.relationship('MetaInfo', backref='recording', lazy=True)

    def get_name(self):
        if self.alias is not None:
            return self.alias
        else:
            return os.path.basename(os.path.normpath(self.path))



class RecordingStats(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    duration = db.Column(db.Integer, default=0)
    count_hand_washes_total = db.Column(db.Integer, default=0)
    count_hand_washes_manual = db.Column(db.Integer, default=0)
    count_hand_washes_detected_total = db.Column(db.Integer, default=0)
    count_evaluation_yes = db.Column(db.Integer, default=0)
    count_evaluation_no = db.Column(db.Integer, default=0)

    recording_id = db.Column(db.Integer, db.ForeignKey('recording.id'), nullable=False)


class MetaInfo(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    ml_model = db.Column(db.String(256), nullable=True)
    ml_settings = db.Column(db.String(512), nullable=True)
    app_version = db.Column(db.String(64), nullable=True)
    version_code = db.Column(db.String(64), nullable=True)

    date = db.Column(db.DateTime, nullable=True)
    start_time_stamp = db.Column(db.Integer, nullable=True)
    run_number = db.Column(db.Integer, nullable=True)

    recording_id = db.Column(db.Integer, db.ForeignKey('recording.id'), nullable=False)

    def load_from_dict(self, info_dict):
        if 'ml_model' in info_dict:
            self.ml_model = info_dict['ml_model']
        if 'ml_settings' in info_dict:
            self.ml_settings = info_dict['ml_settings']
        if 'app_version' in info_dict:
            self.app_version = info_dict['app_version']
        if 'version_code' in info_dict:
            self.version_code = info_dict['version_code']
        if 'date' in info_dict:
            self.date = parser.isoparse(info_dict['date'])
        if 'start_time_stamp' in info_dict:
            self.start_time_stamp = int(info_dict['start_time_stamp'])
        if 'run_number' in info_dict:
            self.run_number = int(info_dict['run_number'])

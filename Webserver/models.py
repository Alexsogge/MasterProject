import datetime
import os
from typing import Union

from dateutil import parser

from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import desc

from tools import get_session_size, convert_size, get_size_color

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
                                 backref=db.backref('participants', lazy=True),
                                 order_by='Recording.last_changed')

    stats_id = db.Column(db.Integer, db.ForeignKey('participant_stats.id'), nullable=True)
    stats = db.relationship('ParticipantStats', backref=db.backref('participants', lazy=True), cascade="all,delete")

    def get_name(self):
        if self.alias is not None and not '':
            return self.alias
        if self.android_id is not None:
            return self.android_id
        return self.id

    def get_stat_entries(self):
        if self.stats_id is None:
            return dict()
        else:
            return self.stats.get_entries(len(self.recordings))

    def get_sorted_recordings(self):
        records = Recording.query.filter(Recording.participants.contains(self)).order_by(desc(Recording.last_changed)).all()
        return records


class Recording(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    path = db.Column(db.String(256), nullable=False)
    alias = db.Column(db.String(256), nullable=True)
    description = db.Column(db.TEXT, default='')

    last_changed = db.Column(db.DateTime, default=datetime.datetime.utcnow)
    session_size = db.Column(db.Integer, default=0)

    stats_id = db.Column(db.Integer, db.ForeignKey('recording_stats.id'), nullable=True)
    stats = db.relationship('RecordingStats', backref=db.backref('recording', lazy=True), cascade="all,delete")

    meta_info_id = db.Column(db.Integer, db.ForeignKey('meta_info.id'), nullable=True)
    meta_info = db.relationship('MetaInfo', backref=db.backref('recording', lazy=True), cascade="all,delete")

    evaluations = db.relationship('RecordingEvaluation', backref='recording', lazy=True, cascade="all,delete")

    @property
    def base_name(self) -> str:
        return os.path.basename(os.path.normpath(self.path))

    def get_name(self):
        if self.alias is not None:
            return self.alias
        else:
            return self.base_name

    def update_session_size(self):
        self.session_size = get_session_size(self.path)

    def get_file_size(self):
        if self.session_size is None:
            self.session_size = 0
        return convert_size(self.session_size)

    def get_file_size_color(self):
        if self.session_size is None:
            self.session_size = 0
        return get_size_color(self.session_size)

    def get_last_changed(self):
        return self.last_changed.strftime('%d/%m/%Y, %H:%M:%S')

    def get_zip_path(self):
        return self.base_name + '/.' + self.base_name + '.zip'

    def update_last_changed(self):
        self.last_changed = datetime.datetime.utcnow()

    def get_evaluation_dict(self):
        eva_dict = dict()
        eva_dict['compulsive'] = 0
        eva_dict['tense'] = [0, 0, 0, 0, 0]
        eva_dict['urge'] = [0, 0, 0, 0, 0]
        for evaluation in self.evaluations:
            eva_dict['compulsive'] += evaluation.compulsive
            eva_dict['tense'][evaluation.tense-1] += 1
            eva_dict['urge'][evaluation.urge-1] += 1

        return eva_dict


class RecordingStats(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    duration = db.Column(db.Integer, default=0)
    count_hand_washes_total = db.Column(db.Integer, default=0)
    count_hand_washes_manual = db.Column(db.Integer, default=0)
    count_hand_washes_detected_total = db.Column(db.Integer, default=0)
    count_evaluation_yes = db.Column(db.Integer, default=0)
    count_evaluation_no = db.Column(db.Integer, default=0)


    def get_stats(self):
        stat_dict = dict()
        stat_dict['recorded hours'] = f'{(self.duration/60)/60:.2f}'
        stat_dict['total hand washes'] = self.count_hand_washes_total
        stat_dict['manual tagged hand washes'] = self.count_hand_washes_manual
        stat_dict['detected hand washes'] = self.count_hand_washes_detected_total
        stat_dict['evaluated yes'] = self.count_evaluation_yes
        stat_dict['evaluated no'] = self.count_evaluation_no
        return stat_dict


class MetaInfo(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    ml_model = db.Column(db.String(256), nullable=True)
    ml_settings = db.Column(db.String(512), nullable=True)
    app_version = db.Column(db.String(64), nullable=True)
    version_code = db.Column(db.String(64), nullable=True)

    date = db.Column(db.DateTime, nullable=True)
    start_time_stamp = db.Column(db.Integer, nullable=True)
    run_number = db.Column(db.Integer, nullable=True)
    android_id = db.Column(db.String(256), nullable=True)


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
        if 'android_id' in info_dict:
            self.android_id = info_dict['android_id']


class RecordingEvaluation(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    compulsive = db.Column(db.Boolean, default=False)
    tense = db.Column(db.Integer, default=0)
    urge = db.Column(db.Integer, default=0)

    recording_id = db.Column(db.Integer, db.ForeignKey('recording.id'), nullable=False)

class ParticipantStats(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    duration = db.Column(db.Integer, default=0)
    count_hand_washes_total = db.Column(db.Integer, default=0)
    count_hand_washes_manual = db.Column(db.Integer, default=0)
    count_hand_washes_detected_total = db.Column(db.Integer, default=0)
    count_evaluation_yes = db.Column(db.Integer, default=0)
    count_evaluation_no = db.Column(db.Integer, default=0)

    def clean(self):
        self.duration = 0
        self.count_hand_washes_total = 0
        self.count_hand_washes_manual = 0
        self.count_hand_washes_detected_total = 0
        self.count_evaluation_yes = 0
        self.count_evaluation_no = 0

    def get_stats(self):
        stat_dict = dict()
        stat_dict['recorded hours'] = (self.duration/60)/60
        stat_dict['total hand washes'] = self.count_hand_washes_total
        stat_dict['manual tagged hand washes'] = self.count_hand_washes_manual
        stat_dict['detected hand washes'] = self.count_hand_washes_detected_total
        stat_dict['evaluated yes'] = self.count_evaluation_yes
        stat_dict['evaluated no'] = self.count_evaluation_no
        return stat_dict

    def calc_new_stats(self, stats: RecordingStats):
        self.duration += stats.duration
        self.count_hand_washes_total += stats.count_hand_washes_total
        self.count_hand_washes_manual += stats.count_hand_washes_manual
        self.count_hand_washes_detected_total += stats.count_hand_washes_detected_total
        self.count_evaluation_yes += stats.count_evaluation_yes
        self.count_evaluation_no += stats.count_evaluation_no


    def get_averages(self, count_total):
        stat_dict = dict()
        stat_dict['recorded hours'] = ((self.duration/count_total)/60)/60
        stat_dict['total hand washes'] = self.count_hand_washes_total/count_total
        stat_dict['manual tagged hand washes'] = self.count_hand_washes_manual/count_total
        stat_dict['detected hand washes'] = self.count_hand_washes_detected_total/count_total
        stat_dict['evaluated yes'] = self.count_evaluation_yes/count_total
        stat_dict['evaluated no'] = self.count_evaluation_no/count_total
        return stat_dict

    def get_entries(self, count_total):
        stat_dict = dict()
        all_stats = self.get_stats()
        avg_stats = self.get_averages(count_total)

        for key in all_stats.keys():
            stat_dict[key] = (f'{all_stats[key]:.2f}', f'{avg_stats[key]:.2f}')

        return  stat_dict


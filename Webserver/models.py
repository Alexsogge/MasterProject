import datetime
import os
from typing import Union, Dict, List

import numpy as np
from dateutil import parser

from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import desc
import json

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

recording_to_tag = db.Table('recording_to_tag',
                            db.Column('recording_tag_id', db.Integer,
                                      db.ForeignKey('recording_tag.id', primary_key=True)),
                            db.Column('recording_id', db.Integer,
                                      db.ForeignKey('recording.id', primary_key=True))
                            )


class Participant(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    android_id = db.Column(db.String(256), nullable=True)
    alias = db.Column(db.String(256), nullable=True)
    recordings = db.relationship('Recording', secondary=participant_to_recording, lazy='subquery',
                                 backref=db.backref('participants', lazy=True),
                                 order_by='Recording.last_changed')

    is_active = db.Column(db.Boolean, default=False)

    stats_id = db.Column(db.Integer, db.ForeignKey('participant_stats.id'), nullable=True)
    stats = db.relationship('ParticipantStats', backref=db.backref('participants', lazy=True), cascade="all,delete")

    tag_settings = db.relationship('ParticipantsTagSetting', backref='participant', lazy=True, cascade="all,delete")

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
            return self.stats.get_entries(len(self.get_observed_recordings()))

    def get_sorted_recordings(self):
        records = Recording.query.filter(Recording.participants.contains(self)).order_by(
            desc(Recording.last_changed)).all()
        return records

    def check_for_set_active(self):
        if Participant.query.filter_by(android_id=self.android_id, is_active=True).first() is None:
            self.is_active = True

    def get_active_color(self):
        if self.is_active:
            return 'green'
        return 'black'

    def update_tag_settings(self):
        all_tags = RecordingTag.query.all()
        for tag in all_tags:
            tag_setting = ParticipantsTagSetting.query.filter_by(participant=self, recording_tag=tag).first()
            if tag_setting is None:
                include_for_statistics = False
                not_include_for_statistics = False
                if tag.default_include_for_statistics is not None:
                    include_for_statistics = tag.default_include_for_statistics
                    not_include_for_statistics = not include_for_statistics
                tag_setting = ParticipantsTagSetting(recording_tag=tag,
                                                     include_for_statistics=include_for_statistics,
                                                     not_include_for_statistics=not_include_for_statistics)
                self.tag_settings.append(tag_setting)
                db.session.add(tag_setting)
        db.session.commit()


    def get_observed_recordings(self):
        required_tags = []
        forbidden_tags = []
        for tag_setting in self.tag_settings:
            if tag_setting.include_for_statistics:
                required_tags.append(tag_setting.recording_tag)
            if tag_setting.not_include_for_statistics:
                forbidden_tags.append(tag_setting.recording_tag)

        observed_recordings = []

        for recording in self.recordings:
            is_allowed = True
            for tag in recording.tags:
                if tag in forbidden_tags:
                    is_allowed = False
                    break
            for tag in required_tags:
                if tag not in recording.tags:
                    is_allowed = False
                    break
            if is_allowed:
                observed_recordings.append(recording)

        return observed_recordings


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

    tags = db.relationship('RecordingTag', secondary=recording_to_tag, lazy='subquery',
                           backref=db.backref('recordings', lazy=True))

    calculations_id = db.Column(db.Integer, db.ForeignKey('recording_calculations.id'), nullable=True)
    calculations = db.relationship('RecordingCalculations', backref=db.backref('recording', lazy=True), cascade="all,delete")

    highlight = False

    @property
    def base_name(self) -> str:
        return os.path.basename(os.path.normpath(self.path))

    def __repr__(self):
        return f'<Recording {self.id}> {self.base_name}'

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
            eva_dict['tense'][evaluation.tense - 1] += 1
            eva_dict['urge'][evaluation.urge - 1] += 1

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
        stat_dict['recorded hours'] = f'{(self.duration / 60) / 60:.2f}'
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

    daily_duration = db.Column(db.Integer, default=0)
    daily_count_hand_washes_total = db.Column(db.Integer, default=0)
    daily_count_hand_washes_manual = db.Column(db.Integer, default=0)
    daily_count_hand_washes_detected_total = db.Column(db.Integer, default=0)
    daily_count_evaluation_yes = db.Column(db.Integer, default=0)
    daily_count_evaluation_no = db.Column(db.Integer, default=0)

    def clean(self):
        self.duration = 0
        self.count_hand_washes_total = 0
        self.count_hand_washes_manual = 0
        self.count_hand_washes_detected_total = 0
        self.count_evaluation_yes = 0
        self.count_evaluation_no = 0

        self.daily_duration = 0
        self.daily_count_hand_washes_total = 0
        self.daily_count_hand_washes_manual = 0
        self.daily_count_hand_washes_detected_total = 0
        self.daily_count_evaluation_yes = 0
        self.daily_count_evaluation_no = 0

    def get_stats(self):
        stat_dict = dict()
        stat_dict['recorded hours'] = (self.duration / 60) / 60
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
        if count_total == 0:
            count_total = 1
        stat_dict = dict()
        stat_dict['recorded hours'] = ((self.duration / count_total) / 60) / 60
        stat_dict['total hand washes'] = self.count_hand_washes_total / count_total
        stat_dict['manual tagged hand washes'] = self.count_hand_washes_manual / count_total
        stat_dict['detected hand washes'] = self.count_hand_washes_detected_total / count_total
        stat_dict['evaluated yes'] = self.count_evaluation_yes / count_total
        stat_dict['evaluated no'] = self.count_evaluation_no / count_total
        return stat_dict

    def get_daily_averages(self):
        stat_dict = dict()
        stat_dict['recorded hours'] = (self.daily_duration / 60) / 60
        stat_dict['total hand washes'] = self.daily_count_hand_washes_total
        stat_dict['manual tagged hand washes'] = self.daily_count_hand_washes_manual
        stat_dict['detected hand washes'] = self.daily_count_hand_washes_detected_total
        stat_dict['evaluated yes'] = self.daily_count_evaluation_yes
        stat_dict['evaluated no'] = self.daily_count_evaluation_no
        return stat_dict

    def get_entries(self, count_total):
        stat_dict = dict()
        all_stats = self.get_stats()
        avg_stats = self.get_averages(count_total)
        avg_daily = self.get_daily_averages()

        for key in all_stats.keys():
            stat_dict[key] = (f'{all_stats[key]:.2f}', f'{avg_stats[key]:.2f}', f'{avg_daily[key]:.2f}')

        return stat_dict

    def calc_daily_stats(self, stats_per_day: Dict[datetime.date, List[RecordingStats]]):
        if len(stats_per_day) > 0:
            for day in stats_per_day.keys():
                for stat in stats_per_day[day]:
                    self.daily_duration += stat.duration
                    self.daily_count_hand_washes_total += stat.count_hand_washes_total
                    self.daily_count_hand_washes_manual += stat.count_hand_washes_manual
                    self.daily_count_hand_washes_detected_total += stat.count_hand_washes_detected_total
                    self.daily_count_evaluation_yes += stat.count_evaluation_yes
                    self.daily_count_evaluation_no += stat.count_evaluation_no

            self.daily_duration /= len(stats_per_day.keys())
            self.daily_count_hand_washes_total /= len(stats_per_day.keys())
            self.daily_count_hand_washes_manual /= len(stats_per_day.keys())
            self.daily_count_hand_washes_detected_total /= len(stats_per_day.keys())
            self.daily_count_evaluation_yes /= len(stats_per_day.keys())
            self.daily_count_evaluation_no /= len(stats_per_day.keys())
        else:
            self.daily_duration = 0
            self.daily_count_hand_washes_total = 0
            self.daily_count_hand_washes_manual = 0
            self.daily_count_hand_washes_detected_total = 0
            self.daily_count_evaluation_yes = 0
            self.daily_count_evaluation_no = 0


class ParticipantsTagSetting(db.Model):
    id = db.Column(db.Integer, primary_key=True)

    participant_id = db.Column(db.Integer, db.ForeignKey('participant.id'), nullable=False)

    recording_tag_id = db.Column(db.Integer, db.ForeignKey('recording_tag.id'))
    recording_tag = db.relationship('RecordingTag')

    include_for_statistics = db.Column(db.Boolean)
    not_include_for_statistics = db.Column(db.Boolean, default=False)

    def next_state(self):
        if self.include_for_statistics:
            self.include_for_statistics = False
            self.not_include_for_statistics = True
        elif self.not_include_for_statistics:
            self.not_include_for_statistics = False
        else:
            self.include_for_statistics = True
        db.session.commit()

    def get_outline(self):
        if self.include_for_statistics:
            return 'btn-outline-success'
        elif self.not_include_for_statistics:
            return 'btn-outline-danger'
        else:
            return 'btn-outline-secondary'

    def is_checked(self):
        return self.include_for_statistics or self.not_include_for_statistics

class RecordingTag(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(128))
    icon_name = db.Column(db.String(128))
    icon_color = db.Column(db.String(256), default='black')
    description = db.Column(db.String(512), default='')

    default_include_for_statistics = db.Column(db.Boolean, nullable=True)


default_recording_tags = {'no data': {'icon_name': 'fas fa-exclamation', 'icon_color': 'red',
                                      'default_include_for_statistics': False,
                                      'description': 'Short or to less movement'},
                          'false trigger': {'icon_name': 'fas fa-hand-holding-water', 'icon_color': 'red',
                                            'default_include_for_statistics': None,
                                            'description': 'Manual hand wash marker at obviously wrong spots'},
                          'faulty parts': {'icon_name': 'fas fa-thumbs-down', 'icon_color': 'orange',
                                           'default_include_for_statistics': None,
                                           'description': 'There are some parts where data seems wrong'},
                          'good': {'icon_name': 'fas fa-thumbs-up', 'icon_color': 'green',
                                   'default_include_for_statistics': None,
                                   'description': 'Everything ok'},
                          'default': {'icon_name': 'fas fa-bookmark', 'icon_color': 'grey',
                                   'default_include_for_statistics': True,
                                   'description': 'Default tag for each recording'}
                          }

class RecordingCalculations(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    variance = db.Column(db.String(1024), nullable=True)


    def store_variance(self, variance: np.ndarray):
        variance_json = json.dumps(variance.tolist())
        self.variance = variance_json
        db.session.commit()

    def get_variance(self) -> Union[None, np.ndarray]:
        if self.variance is None:
            return None
        return np.asarray(json.loads(self.variance))

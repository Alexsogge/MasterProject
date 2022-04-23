import datetime
import time
import os
import pathlib
from typing import Union, Dict, List

import numpy as np
from dateutil import parser

from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import desc
import json
import personalization as personalization_pipe

from tools import get_session_size, convert_size, get_size_color, find_newest_torch_file
from config import PARTICIPANT_FOLDER

from typing import TYPE_CHECKING, List, Dict

if TYPE_CHECKING:
    from personalization_tools.dataset import Dataset

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


# personalization_to_recording = db.Table('personalization_to_recording',
#                                     db.Column('recording_id', db.Integer,
#                                               db.ForeignKey('recording.id', primary_key=True)),
#                                     db.Column('personalization_id', db.Integer,
#                                               db.ForeignKey('personalization.id', primary_key=True))
#                                     )


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

    personalizations = db.relationship('Personalization', backref='participant', lazy=True, cascade="all,delete",
                                       order_by='Personalization.version')

    enable_personalization = db.Column(db.Boolean, default=False)

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

    def get_metrics(self):
        if self.stats_id is None:
            return []
        else:
            print(self.stats.get_metrics())
            return self.stats.get_metrics()

    def get_path(self):
        my_path = os.path.join(PARTICIPANT_FOLDER, str(self.id))
        if not os.path.exists(my_path):
            os.mkdir(my_path)
        return my_path

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

    def get_recordings_per_day(self):
        recordings_per_day: Dict[datetime.date, List['RecordingStats']] = dict()
        for recording in self.get_observed_recordings():
            if recording.meta_info is not None:
                date = recording.meta_info.date.date()
                if date not in recordings_per_day:
                    recordings_per_day[date] = []
                recordings_per_day[date].append(recording)

        return recordings_per_day

    def get_best_personalization(self):
        current_personalization: Union[None, 'Personalization'] = None
        for personalization in self.personalizations:
            if current_personalization is None or (current_personalization.f1 is not None and current_personalization.f1 < personalization.f1):
                current_personalization = personalization
        return current_personalization

    def create_personalization_quality_test_plots(self, personalization: 'Personalization',
                                                  test_recordings: Dict['RecordingForPersonalization', 'Dataset'],
                                                  base_model, prediction_buffer=None):
        triggers = []
        participant_path = self.get_path()
        for recording, dataset in test_recordings.items():
            fig_name = os.path.join(participant_path, f'quality_plot_test_{personalization.id}_{recording.id}.svg')
            triggers.append(personalization_pipe.create_personalization_quality_test_plot(dataset, base_model,
                                                                                          personalization.model_torch_path,
                                                                                          personalization.mean_kernel_width,
                                                                                          personalization.mean_threshold,
                                                                                          fig_name,
                                                                                          prediction_buffer=prediction_buffer))
        return triggers

    def create_personalization_pseudo_plots(self, personalization: 'Personalization',
                                            recordings: Dict['RecordingForPersonalization', 'Dataset']):
        participant_path = self.get_path()
        for recording, dataset in recordings.items():
            fig_name = os.path.join(participant_path, f'pseudo_labels_{personalization.id}_{recording.id}.svg')
            personalization_pipe.create_personalization_pseudo_plot(dataset, fig_name)

    def run_personalization(self, target_filter='alldeepconv_correctbyconvlstm3filter6', use_best=False):
        start_time = time.time()
        current_personalization: Union[None, 'Personalization'] = None
        already_covered_recordings = []
        test_recordings = []
        for personalization in self.personalizations:
            print()
            if current_personalization is None or current_personalization.version < personalization.version:
                current_personalization = personalization

            for recording in personalization.recordings:
                already_covered_recordings.append(recording.recording)
                if recording.used_for_testing:
                    test_recordings.append(recording)

        current_iteration = 0
        uncovered_recordings = []
        print('already covered:', already_covered_recordings)
        exclude_tag = RecordingTag.query.filter_by(name='exclude personalization').first()
        evaluation_tag = RecordingTag.query.filter_by(name='use as evaluation').first()
        for recording in self.recordings:
            if recording not in already_covered_recordings and (
                    exclude_tag not in recording.tags or evaluation_tag in recording.tags):
                print('add', recording)
                uncovered_recordings.append(recording)

        if len(uncovered_recordings) == 0:
            print('nothing new')
            return

        general_model = find_newest_torch_file(full_path=True)
        base_model = general_model
        base_personalization = None
        if use_best:
            base_personalization = self.get_best_personalization()
        else:
            base_personalization = current_personalization
        if base_personalization is not None:
            base_model = base_personalization.model_torch_path
            current_iteration = base_personalization.iteration

        if current_personalization is None:
            new_personalization = Personalization(version=0, iteration=0, participant=self)
        else:
            new_personalization = Personalization(version=current_personalization.version + 1,
                                                  iteration=current_iteration,
                                                  participant=self)
        new_personalization.used_filter = target_filter

        if base_personalization is not None:
            new_personalization.based_personalization = base_personalization

        db.session.add(new_personalization)
        for recording in uncovered_recordings:
            recording_entry = RecordingForPersonalization(recording=recording)
            db.session.add(recording_entry)
            new_personalization.recordings.append(recording_entry)

        collection = personalization_pipe.build_datasets_from_recordings(new_personalization.recordings)
        personalization_pipe.clean_collection(collection)

        usable_recordings = new_personalization.get_usable_recordings()
        if len(usable_recordings) == 0:
            print('cleaned all recordings')
            db.session.rollback()
            return

        collection = {k: v for k, v in collection.items() if k in usable_recordings}

        print('usable recordings:', collection.keys())
        personalization_pipe.process_collection(list(collection.values()), general_model)

        test_recordings_collection: Dict[
            'RecordingForPersonalization', 'Dataset'] = personalization_pipe.build_datasets_from_recordings(
            test_recordings)
        test_recordings_collection = personalization_pipe.split_test_from_collection(collection, current_iteration,
                                                                                     test_recordings_collection,
                                                                                     evaluation_tag)
        print('test recordings:', list(test_recordings_collection.keys()))
        print('train recordings:', list(collection.keys()))

        personalization_pipe.personalize_model(collection, base_model, new_personalization.model_torch_path,
                                               target_filter=target_filter)
        new_personalization.iteration += len(collection)

        personalization_pipe.convert_pytorch_to_onnx(new_personalization.model_torch_path)
        personalization_pipe.convert_onnx_to_ort(new_personalization.model_onnx_path)

        best_model_settings = personalization_pipe.calc_model_settings(list(test_recordings_collection.values()),
                                                                       general_model,
                                                                       new_personalization.model_torch_path)

        print('best setting', best_model_settings[0])
        new_personalization.mean_kernel_width = best_model_settings[0][1][0]
        new_personalization.mean_threshold = best_model_settings[0][1][1]
        new_personalization.false_diff_relative = best_model_settings[0][1][2]
        new_personalization.correct_diff_relative = best_model_settings[0][1][3]

        end_time = time.time()
        new_personalization.required_time = end_time - start_time
        db.session.commit()

        print('create plots')
        triggers = self.create_personalization_quality_test_plots(new_personalization, test_recordings_collection, general_model)
        self.create_personalization_pseudo_plots(new_personalization, collection)

        print('create metrics')
        sum_hand_washes = 0
        sum_correct_hand_washes = 0
        sum_false_hand_washes = 0
        for dataset in test_recordings_collection.values():
            sum_hand_washes += len(dataset.feedback_areas.labeled_regions_hw)

        for trigger in triggers:
            sum_correct_hand_washes += trigger[3]
            sum_false_hand_washes += trigger[2]

        sensitivity = sum_correct_hand_washes / sum_hand_washes
        precision = sum_correct_hand_washes / (sum_false_hand_washes + sum_correct_hand_washes)

        f1 = 2 * ((precision * sensitivity) / (precision + sensitivity))

        print('sensitivity:\t', sensitivity)
        print('precision:\t', precision)
        print('f1:\t\t', f1)

        new_personalization.sensitivity = sensitivity
        new_personalization.precision = precision
        new_personalization.f1 = f1

        db.session.commit()
        print('finished')

    def rerun_tests_on_personalization(self):
        general_model = find_newest_torch_file(full_path=True)
        test_recordings = []
        for personalization in self.personalizations:
            test_recordings += personalization.get_test_recordings()
        test_recordings_collection: Dict[
            'RecordingForPersonalization', 'Dataset'] = personalization_pipe.build_datasets_from_recordings(
            test_recordings)

        for recording in test_recordings_collection.keys():
            self.personalizations[0].recordings.append(recording)

        prediction_buffer = None
        print('test recordings:', list(test_recordings_collection.keys()))
        for personalization in self.personalizations:
            print('Test:', personalization)

            best_model_settings = personalization_pipe.calc_model_settings(list(test_recordings_collection.values()),
                                                                           general_model,
                                                                           personalization.model_torch_path,
                                                                           prediction_buffer=prediction_buffer)

            print('best setting', best_model_settings[0])
            personalization.mean_kernel_width = best_model_settings[0][1][0]
            personalization.mean_threshold = best_model_settings[0][1][1]
            personalization.false_diff_relative = best_model_settings[0][1][2]
            personalization.correct_diff_relative = best_model_settings[0][1][3]
            triggers = self.create_personalization_quality_test_plots(personalization, test_recordings_collection,
                                                                      general_model, prediction_buffer=prediction_buffer)
            print('create metrics')
            sum_hand_washes = 0
            sum_correct_hand_washes = 0
            sum_false_hand_washes = 0
            for dataset in test_recordings_collection.values():
                sum_hand_washes += len(dataset.feedback_areas.labeled_regions_hw)

            for trigger in triggers:
                sum_correct_hand_washes += trigger[3]
                sum_false_hand_washes += trigger[2]

            sensitivity = sum_correct_hand_washes / sum_hand_washes
            precision = sum_correct_hand_washes / (sum_false_hand_washes + sum_correct_hand_washes)

            f1 = 2 * ((precision * sensitivity) / (precision + sensitivity))

            print('sensitivity:\t', sensitivity)
            print('precision:\t', precision)
            print('f1:\t\t', f1)

            personalization.sensitivity = sensitivity
            personalization.precision = precision
            personalization.f1 = f1

            db.session.commit()






    def create_manual_prediction(self, recording: 'Recording', personalization: 'Personalization'):
        manual_prediction = ManualPrediction(based_personalization=personalization, based_recording=recording)
        db.session.add(manual_prediction)
        db.session.commit()
        print('new prediction', manual_prediction.id)

        fig_name = manual_prediction.get_path()
        general_model = find_newest_torch_file(full_path=True)
        personalization_pipe.create_manual_prediction(recording, personalization, general_model, fig_name)
        print('finished')
        return manual_prediction

    def get_personal_model(self):
        return self.get_best_personalization()


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

    evaluations = db.relationship('RecordingEvaluation', backref='recording', lazy=True, cascade="all,delete",
                                  order_by='RecordingEvaluation.timestamp')

    tags = db.relationship('RecordingTag', secondary=recording_to_tag, lazy='subquery',
                           backref=db.backref('recordings', lazy=True))

    calculations_id = db.Column(db.Integer, db.ForeignKey('recording_calculations.id'), nullable=True)
    calculations = db.relationship('RecordingCalculations', backref=db.backref('recording', lazy=True),
                                   cascade="all,delete")

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

    def get_zip_name(self):
        return self.base_name + '/.' + self.base_name + '.zip'

    def get_zip_path(self):
        return self.path + '/.' + self.base_name + '.zip'

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

    def get_files(self):
        files = []
        for file in os.listdir(self.path):
            if os.path.isfile(os.path.join(self.path, file)):
                files.append(file)
        return files


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
    timestamp = db.Column(db.DateTime, default=datetime.datetime.utcnow)

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

    amount_covered_days = db.Column(db.Integer, default=0)

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

        stat_dict['amount'] = (count_total, 1, self.amount_covered_days)
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
            self.amount_covered_days = len(stats_per_day.keys())
        else:
            self.daily_duration = 0
            self.daily_count_hand_washes_total = 0
            self.daily_count_hand_washes_manual = 0
            self.daily_count_hand_washes_detected_total = 0
            self.daily_count_evaluation_yes = 0
            self.daily_count_evaluation_no = 0
            self.amount_covered_days = 0

    def get_metrics(self):
        sensitivity = self.count_evaluation_yes / (self.count_evaluation_yes + self.count_hand_washes_manual)
        precision = self.count_evaluation_yes / (
                    self.count_evaluation_yes + (self.count_hand_washes_detected_total - self.count_evaluation_yes))
        f1 = 2 * ((precision * sensitivity) / (precision + sensitivity))

        return sensitivity, precision, f1


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
                                      'description': 'Default tag for each recording'},
                          'exclude personalization': {'icon_name': 'fa-solid fa-person-circle-xmark',
                                                      'icon_color': 'red',
                                                      'default_include_for_statistics': None,
                                                      'description': 'Do not use this dataset for personalization'},
                          'use as evaluation': {'icon_name': 'fa-solid fa-chart-line',
                                                'icon_color': 'blue',
                                                'default_include_for_statistics': None,
                                                'description': 'Just use for evaluation of personalization'}
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


class Personalization(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    version = db.Column(db.Integer, default=0)
    iteration = db.Column(db.Integer, default=0)

    participant_id = db.Column(db.Integer, db.ForeignKey('participant.id'), nullable=False)
    recordings = db.relationship('RecordingForPersonalization', lazy='select', cascade="all,delete",
                                 backref=db.backref('personalization', lazy='joined'))

    mean_threshold = db.Column(db.Float, default=0.59)
    mean_kernel_width = db.Column(db.Integer, default=20)
    false_diff_relative = db.Column(db.Float, default=0)
    correct_diff_relative = db.Column(db.Float, default=0)
    used_filter = db.Column(db.String, default='alldeepconv_correctbyconvlstm3filter6')
    required_time = db.Column(db.Float, default=0)

    sensitivity = db.Column(db.Float, default=0)
    precision = db.Column(db.Float, default=0)
    f1 = db.Column(db.Float, default=0)

    based_personalization_id = db.Column(db.Integer, db.ForeignKey('personalization.id'))
    based_personalization = db.relationship('Personalization', remote_side=[id])

    @property
    def model_base_path(self):
        return os.path.join(self.participant.get_path(), 'personalized_model_' + str(self.version))

    @property
    def model_torch_path(self):
        return self.model_base_path + '.pt'

    @property
    def model_onnx_path(self):
        return self.model_base_path + '.onnx'

    @property
    def model_ort_path(self):
        ort_path = self.model_base_path + '.ort'
        if not os.path.exists(ort_path):
            ort_path = self.model_base_path + '.all.ort'
        return ort_path

    @property
    def ort_name(self):
        return os.path.basename(self.model_ort_path)

    @property
    def ort_download_path(self):
        path = pathlib.Path(self.model_ort_path)
        return pathlib.Path(*path.parts[2:])

    @property
    def settings_name(self):
        return os.path.splitext(self.ort_name)[0] + '.json'

    def get_settings(self):
        torch_file = find_newest_torch_file(full_path=True)
        settings_file = os.path.splitext(torch_file)[0] + '.json'
        with open(settings_file) as json_file:
            settings = json.load(json_file)
        settings['mean_threshold'] = self.mean_threshold
        settings['mean_kernel_size'] = self.mean_kernel_width
        return settings

    def get_usable_recordings(self) -> List['RecordingForPersonalization']:
        usable_recordings = []
        for recording in self.recordings:
            if not recording.unusable:
                usable_recordings.append(recording)
        return usable_recordings

    def get_test_recordings(self):
        test_recordings = []

        for recording in self.recordings:
            if recording.used_for_testing:
                test_recordings.append(recording)

        return test_recordings


class RecordingForPersonalization(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    personalization_id = db.Column(db.Integer, db.ForeignKey('personalization.id'), nullable=False)

    recording_id = db.Column(db.Integer, db.ForeignKey('recording.id'))
    recording = db.relationship('Recording')

    used_for_training = db.Column(db.Boolean, default=False)
    used_for_testing = db.Column(db.Boolean, default=False)
    unusable = db.Column(db.Boolean, default=False)

    def __repr__(self):
        return f'[{self.id}] {self.recording}'


class ManualPrediction(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    based_personalization_id = db.Column(db.Integer, db.ForeignKey('personalization.id'))
    based_personalization = db.relationship('Personalization')

    based_recording_id = db.Column(db.Integer, db.ForeignKey('recording.id'))
    based_recording = db.relationship('Recording')

    false_diff_relative = db.Column(db.Float, default=0)
    correct_diff_relative = db.Column(db.Float, default=0)

    def get_path(self):
        participant_path = self.based_personalization.participant.get_path()
        manual_plots_path = os.path.join(participant_path, 'manual_predictions')
        if not os.path.exists(manual_plots_path):
            os.mkdir(manual_plots_path)
        fig_name = os.path.join(manual_plots_path, f'manual_prediction_{self.id}.svg')
        return fig_name


class RecordEntryComment(db.Model):
    id = db.Column(db.Integer, primary_key=True)

    based_recording_id = db.Column(db.Integer, db.ForeignKey('recording.id'))
    based_recording = db.relationship('Recording')

    file_name = db.Column(db.String)
    comment = db.Column(db.String)

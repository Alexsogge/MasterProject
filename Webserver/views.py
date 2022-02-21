import json
import os
import re
import traceback
from datetime import datetime, timedelta, time
from typing import List

import numpy as np
from dateutil import parser

from flask import jsonify, request, render_template, redirect, send_from_directory, abort, Blueprint, url_for

from werkzeug.utils import secure_filename
from sqlalchemy import desc, and_
import uuid
import shutil

from auth_requests import *
from preview_builder import generate_plot_data
from data_factory import DataFactory

from authentication import basic_auth, token_auth, open_auth_requests
from tools import *

from models import db, AuthenticationRequest, Participant, Recording, RecordingStats, MetaInfo, ParticipantStats,\
    RecordingEvaluation, RecordingTag, ParticipantsTagSetting, default_recording_tags, RecordingCalculations




view = Blueprint('views', __name__, template_folder='templates', static_folder='static')

nano_sec = 0.000000001

@view.app_template_filter()
def is_boolean(input):
    return type(input) is bool or input == 'True' or input == 'False' or input == 'on' or input == 'off'

@view.app_template_filter()
def is_number(input):
    return type(input) is int or (type(input) is str and input.isnumeric())


@view.app_template_filter()
def render_is_checked(input):
    if bool(input):
        return 'checked'
    return ''


@view.route('/')
def index():
    # return render_template('index.html')
    return redirect(url_for('views.list_recordings'))


@view.route('/tokenauth/request/')
def request_token():
    identifier = request.args.get('identifier')
    db_identifiers = AuthenticationRequest.query.all()
    # print('DB identifiers:', db_identifiers)
    if identifier is None or identifier == '':
        return jsonify({'status': 'error', 'msg': 'no identifier'})

    auth_request: AuthenticationRequest = AuthenticationRequest.query.filter_by(identifier=identifier).first()
    # print('auth_request', auth_request)
    if auth_request is None:
        # open_auth_requests.new_request(identifier)

        new_auth_request = AuthenticationRequest(identifier=identifier)
        db.session.add(new_auth_request)
        db.session.commit()
        return jsonify({'status': 'new', 'msg': 'created request'})

    else:
        if auth_request.used:
            auth_request.used = False
            auth_request.granted = False
            db.session.commit()
            return jsonify({'status': 'new', 'msg': 'created request'})
        else:
            if not auth_request.granted:
                return jsonify({'status': 'pending', 'msg': 'waiting for confirmation'})
            else:
                auth_request.used = True
                auth_request.granted = False
                db.session.commit()
                return jsonify({'status': 'grant', 'token': config.client_secret})


@view.route('/tokenauth/check/')
@basic_auth.login_required
def get_open_auth_requests():
    return render_template('list_requests.html', open_requests=AuthenticationRequest.query.filter_by(used=False))


@view.route('/tokenauth/grant/<int:auth_id>/')
@basic_auth.login_required
def grant_auth_request(auth_id):
    auth_request = AuthenticationRequest.query.filter_by(id=auth_id).first()
    if auth_request is not None:
        auth_request.granted = True
        db.session.commit()
    return redirect(url_for('views.get_open_auth_requests'))

@view.route('/tokenauth/grant-create/<int:auth_id>/')
@basic_auth.login_required
def grant_create_auth_request(auth_id):
    auth_request = AuthenticationRequest.query.filter_by(id=auth_id).first()
    if auth_request is not None:
        auth_request.granted = True
        db.session.commit()

        part = r'(.*)?\[(.*)].*'
        match = re.search(part, auth_request.identifier)
        android_id = ''
        alias = auth_request.identifier
        if match is not None:
            alias = match.group(1)
            android_id = match.group(2)

        return render_template('edit_participant.html', participant_id=None, alias=alias, android_id=android_id, is_active=True)
    return redirect(url_for('views.get_open_auth_requests'))

@view.route('/settings/', methods=['GET', 'POST'])
@basic_auth.login_required
def settings():
    if request.method == 'POST':
        print('Save new settings')
        settings_values = config.get_config_values().copy()
        for key in settings_values.keys():
            settings_values[key] = request.form.get(key)
            print("save ", key, settings_values[key])
            if is_boolean(settings_values[key]):
                settings_values[key] = bool(settings_values[key])
            if settings_values[key] is None and is_boolean(config.get_config_values()[key]):
                settings_values[key] = False
            if is_number(settings_values[key]):
                print("save as number")
                settings_values[key] = int(settings_values[key])
        print(settings_values)
        config.save_config(settings_values)
    print(config.get_config_values())
    setting_hints = {'database_uri': 'dialect+driver://username:password@host:port/database'}
    return render_template('settings.html', setting_entries=config.get_config_values(), setting_hints=setting_hints)


@view.route('/tfmodel/', methods=['GET', 'POST'])
@basic_auth.login_required
def tfmodel():
    upload_info_text = None
    upload_error_text = None
    sensors = {1: 'ACCELEROMETER', 4: 'GYROSCOPE', 2: 'MAGNETIC FIELD', 11: 'ROTATION VECTOR'}
    filename = "tf_settings"

    old_settings = dict()
    old_settings_file = get_tf_model_settings_file()
    if old_settings_file is not None:
        with open(os.path.join(TFMODEL_FOLDER, old_settings_file)) as json_file:
            old_settings = json.load(json_file)
        filename = os.path.splitext(old_settings_file)[0]

    if request.method == 'POST':
        print('Save new tf model', request.form)
        do_save_file = True
        if 'file' not in request.files:
            upload_error_text = 'Info: no new file was selected'
        else:
            file = request.files['file']
            # if user does not select file, browser also
            # submit an empty part without filename
            if file.filename == '':
                upload_error_text = 'Info: no new file was selected'
            else:
                if file and is_allowed_file(file.filename):
                    filename = os.path.splitext(secure_filename(file.filename))[0]
                    file_extension = os.path.splitext(secure_filename(file.filename))[1]
                    file_path = os.path.join(TFMODEL_FOLDER, filename + file_extension)
                    if file_extension == '.ort':
                        missing_optypes = check_valid_ort_model(file.read())
                        if len(missing_optypes) > 0:
                            upload_error_text = 'Error: Missing operations: ' + str(missing_optypes)
                            do_save_file = False
                    if do_save_file:
                        file.seek(0)
                        file.save(file_path)
                        upload_info_text = 'Uploaded ' + filename

                else:
                    upload_error_text = 'Error: no valid file'

        settings_dict = dict()
        settings_dict['frame_size'] = int(request.form.get('frameSizeInput'))
        settings_dict['mean_threshold'] = float(request.form.get('meanThresholdInput'))
        settings_dict['mean_kernel_size'] = int(request.form.get('meanKernelWidthInput'))
        settings_dict['notification_cool_down'] = int(request.form.get('notificationCoolDownInput'))
        required_sensors = request.form.getlist('requiredSensorsSelect')
        for i in range(len(required_sensors)):
            required_sensors[i] = int(required_sensors[i])
        settings_dict['required_sensors'] = required_sensors
        old_settings = settings_dict

        if do_save_file:
            with open(os.path.join(TFMODEL_FOLDER, filename + '.json'), 'w') as outfile:
                json.dump(settings_dict, outfile)
            if upload_info_text is None:
                upload_info_text = 'Saved new settings'
            else:
                upload_info_text = 'Saved new settings and model ' + filename

    newest_tf_file = find_newest_tf_file()
    all_model_files = list()
    all_model_file_paths = list()
    all_model_settings = list()
    for file in os.listdir(TFMODEL_FOLDER):
        if '.tflite' not in file and '.ort' not in file:
            continue
        all_model_files.append(file)
        all_model_file_paths.append(file)
        settings_name = os.path.splitext(file)[0] + '.json'
        all_model_settings.append(settings_name)

    return render_template('tfmodel.html', upload_info_text=upload_info_text, upload_error_text=upload_error_text,
                           sensors=sensors, old_settings=old_settings, all_model_files=all_model_files,
                           all_model_file_paths=all_model_file_paths, all_model_settings=all_model_settings,
                           newest_tf_file=newest_tf_file)


@view.route('/tfmodel/select/<string:tf_model>/')
@basic_auth.login_required
def select_tfmodel(tf_model):
    tf_file = os.path.join(TFMODEL_FOLDER, tf_model)
    print('tf file:', tf_file)
    if os.path.exists(tf_file):
        os.utime(tf_file, None)
        print('update time')
    settings_file = os.path.join(TFMODEL_FOLDER, os.path.splitext(tf_model)[0] + '.json')
    if os.path.exists(settings_file):
        os.utime(settings_file, None)
    return redirect(url_for('views.tfmodel'))


@view.route('/recording/list/')
@basic_auth.login_required
def list_recordings():
    filter_args = {}
    if len(request.args) > 0:
        filter_args = request.args.to_dict(flat=True)

    all_recordings = Recording.query.order_by(desc(Recording.last_changed)).all()
    recordings = []

    if len(filter_args) > 0:
        for recording in all_recordings:
            skip = False
            for key, value in filter_args.items():
                meta_info = None
                if recording.meta_info is not None:
                    meta_info = recording.meta_info

                if hasattr(recording, key):
                    if str(value) not in str(getattr(recording, key)):
                        skip = True
                        break
                elif meta_info is not None and hasattr(meta_info, key):
                    if str(value) not in str(getattr(meta_info, key)):
                        skip = True
                        break
                else:
                    skip = True
                    break
            if not skip:
                recordings.append(recording)
    else:
        recordings = all_recordings

    # recording_directories = [x[0] for x in os.walk(RECORDINGS_FOLDER)]

    return render_template('list_recordings.html', recordings=recordings, sorting=None,
                           current_filter=filter_args)

@view.route('/recording/get/<int:recording_id>/')
@basic_auth.login_required
def get_recording(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    recording_files = []
    description = recording.description
    path = recording.path
    total_size = 0
    meta_info_file = None

    for file in sorted(os.listdir(path)):
        if os.path.splitext(file)[1] == '.json' and 'metaInfo' in file:
            meta_info_file = os.path.join(path, file)
        total_size += os.path.getsize(os.path.join(path, file))
        if config.hide_mic_files and '.zip' in file and contains_mic_files(file, path):
            continue
        recording_files.append(file)

    meta_info = {}
    if meta_info_file is not None:
        with open(meta_info_file) as json_file:
            meta_info = json.load(json_file)

    sensor_data_file = None
    sensor_data_flattened_file = None
    generated_data_size = 0
    data_file = os.path.join(path, DataFactory.sensor_data_file_name)
    if os.path.exists(data_file):
        sensor_data_file = os.path.join(recording.base_name, DataFactory.sensor_data_file_name)
        generated_data_size += os.path.getsize(data_file)
    data_file = os.path.join(path, DataFactory.sensor_data_flattened_file_name)
    if os.path.exists(data_file):
        sensor_data_flattened_file = os.path.join(recording.base_name, DataFactory.sensor_data_flattened_file_name)
        generated_data_size += os.path.getsize(data_file)

    total_size = convert_size(total_size)
    generated_data_size = convert_size(generated_data_size)

    participants = recording.participants

    all_tags = RecordingTag.query.all()

    evaluations_plot = None
    evaluations_plot_path = os.path.join(recording.path, 'evaluation_graph.png')
    if os.path.isfile(evaluations_plot_path):
        evaluations_plot = os.path.join(recording.base_name, 'evaluation_graph.png')


    return render_template('show_recording.html', recording=recording,
                           files=recording_files, total_size=total_size, sensor_data_file=sensor_data_file,
                           sensor_data_flattened_file=sensor_data_flattened_file,
                           generated_data_size=generated_data_size, meta_info=meta_info,
                           participants=participants, all_tags=all_tags, evaluations_plot=evaluations_plot)


@view.route('/recording/plot/<int:recording_id>/')
@basic_auth.login_required
def plot_recording(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    plot_file = os.path.join(recording.path, 'data_plot.svg')
    if not os.path.exists(plot_file):
        plot_file = os.path.join(recording.path, 'data_plot.png')
        if not os.path.exists(plot_file):
            try:
                generate_plot_data(recording.path)
            except Exception as e:
                traceback.print_tb(e.__traceback__)
                return render_template('error_show_recording_plot.html', recording=recording, error=e, traceback=traceback.format_exc())

    if os.path.exists(plot_file):
        if recording.id not in prepared_plot_data.copy():
            try:
                get_plot_data(recording)
            except Exception as e:
                traceback.print_tb(e.__traceback__)
                return render_template('error_show_recording_plot.html', recording=recording, error=e, traceback=traceback.format_exc())
        plot_file = os.path.join(recording.base_name, 'data_plot.png')
        return render_template('show_recording_plot.html', recording=recording, plot=plot_file)

    return render_template('error_show_recording_plot.html', recording=recording)


@view.route('/recording/clean/')
@view.route('/recording/clean/<int:recording_id>/')
@basic_auth.login_required
def clean_recording(recording_id=None):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    clean_session_directory(recording.path)
    return redirect(url_for('views.get_recording', recording_id=recording.id))


@view.route('/recording/new/', methods=['GET', 'POST'])
@token_auth.login_required
def new_recording():
    # secret_token = request.args.get('token')
    # if secret_token is None or secret_token != config.client_secret:
    #     return jsonify({'status': 'not authenticated'})

    print('User: ', token_auth.current_user())

    if request.method == 'GET':
        new_uuid = uuid.uuid4()
        print('Deliver new uuid:', new_uuid)
        return jsonify({'status': 'success', 'uuid': new_uuid})
    elif request.method == 'POST':
        print('Get new records')
        request_uuid = request.args.get('uuid')
        print('Id: ', request_uuid)
        if request_uuid is None or request_uuid == '':
            print('No uuid')
            return jsonify({'status': 'error, now uuid'})
        if 'file' not in request.files:
            print('No file part')
            return jsonify({'status': 'error, now file'})
        file = request.files['file']
        if file.filename == '':
            print('No selected file')
            return jsonify({'status': 'error, now selected file'})
        if file and is_allowed_file(file.filename):
            filename = secure_filename(file.filename)
            if config.rename_mic_files and 'mic' in filename and '.zip' in filename:
                numbering = filename.split('_')[-1]
                filename = generate_random_string(16) + '_' + numbering
            upload_path = os.path.join(RECORDINGS_FOLDER, request_uuid)
            if not os.path.isdir(upload_path):
                os.mkdir(upload_path)
                description_file = open(os.path.join(upload_path, "README.md"), 'x')

            file.save(os.path.join(upload_path, filename))
            add_file_to_zip(filename, upload_path, request_uuid)

            recording = Recording.query.filter_by(path=upload_path).first()
            if recording is None:
                print('new recording')
                recording = Recording(path=upload_path, description='')
                db.session.add(recording)
            recording.update_session_size()
            recording.update_last_changed()

            if os.path.splitext(filename)[1] == '.json' and 'metaInfo' in filename:
                meta_info_file = os.path.join(upload_path, filename)
                meta_info = {}
                if meta_info_file is not None:
                    with open(meta_info_file) as json_file:
                        meta_info = json.load(json_file)
                meta_info_m = MetaInfo()
                meta_info_m.load_from_dict(meta_info)
                recording.meta_info = meta_info_m
                if 'android_id' in meta_info:
                    participant = Participant.query.filter_by(android_id=meta_info['android_id'], is_active=True).order_by(Participant.id.desc()).first()
                    if participant is None:
                        participant = Participant(android_id=meta_info['android_id'])
                        participant.check_for_set_active()
                        db.session.add(participant)
                    if participant is not None:
                        participant.recordings.append(recording)

                db.session.add(meta_info_m)

            db.session.commit()
            return jsonify({'status': 'success, uploaded ' + file.filename})

        return jsonify({'status': 'success'})
    return jsonify({'status': 'error'})

@view.route('/recording/delete/')
@view.route('/recording/delete/<int:recording_id>/')
@basic_auth.login_required
def delete_recording(recording_id=None):
    if recording_id is None:
        return redirect(url_for('views.list_recordings'))
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    file = recording.path
    if os.path.exists(file):
        shutil.rmtree(file)
    db.session.delete(recording)
    db.session.commit()
    return redirect(url_for('views.list_recordings'))



@view.route('/recordingfile/delete/<int:recording_id>/<string:file_name>/')
@basic_auth.login_required
def delete_recording_file(recording_id, file_name):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    file = os.path.join(recording.path, file_name)
    if os.path.exists(file):
        os.remove(file)
    return redirect(url_for('views.get_recording', recording_id=recording.id))


@view.route('/recording/description/<int:recording_id>/', methods=['GET', 'POST'])
@basic_auth.login_required
def recording_description(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    description_file_path = os.path.join(recording.path, "README.md")

    if request.method == 'GET':
        description_file = open(description_file_path, 'r')
        return jsonify({'description': description_file.read()})
    elif request.method == 'POST':
        new_description = request.form.get('description')
        description_file = open(description_file_path, 'w')
        description_file.write(new_description)
        description_file.close()
        recording.description = new_description
        db.session.commit()

    return redirect(url_for('views.get_recording', recording_id=recording.id))


@view.route('/recording/data/<int:recording_id>/')
def recording_data(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    plot_data = get_plot_data(recording)

    start_point = float(request.args.get('start'))
    end_point = float(request.args.get('end'))

    series = plot_data.get_series(start_point, end_point)

    series['start'] = get_time_offset(recording)
    offset_date = series['start']

    # series.append(plot_data.annotations)
    # series.append(plot_data.time_stamp_series)

    # print(time_stamp_series)
    return jsonify(series)
    # return jsonify({'data': {'series': series, 'annotations': prepared_plot_data[recording].annotations}})
    # return jsonify({'data': {'series': series}})


@view.route('/recording/add_marker/<int:recording_id>/')
def add_marker(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    plot_data = get_plot_data(recording)

    marker_x = request.args.get('x')
    print("new marker at: ", marker_x)
    print(plot_data.marker_time_stamps)
    target_date = parser.parse(marker_x)
    offset_date = get_time_offset(recording)
    target_date = target_date.replace(tzinfo=offset_date.tzinfo)
    time_diff = target_date-offset_date
    # offset_date.replace(tzinfo=None)
    #print('target:', target_date)
    #print('offset:', offset_date)
    #print('diff:', time_diff)

#
    millis = time_diff.total_seconds() * 1000
    millis += 250
    # print('millis:', millis)
    nanos = millis * 1000000
    # print('nanos:', nanos)


    # print("new tartget times: ", offset_date + timedelta(milliseconds=millis))

    meta_data = recording.meta_info
    nanos += meta_data.start_time_stamp
    #print("new nanos:", nanos)

    existing_marker_index = None
    for i, ex_marker in enumerate(plot_data.marker_time_stamps):
        if np.abs(ex_marker[0] - millis) < 1500:
            existing_marker_index = i

    if existing_marker_index is None:
        new_timestamps = np.zeros((plot_data.marker_time_stamps.shape[0]+1, 2))
        new_timestamps[:plot_data.marker_time_stamps.shape[0]] = plot_data.marker_time_stamps
        new_timestamps[-1] = np.array((millis, 1))
        plot_data.marker_time_stamps = new_timestamps[new_timestamps[:, 0].argsort()]
        #print("new sorted markers:", plot_data.marker_time_stamps)
        np.save(os.path.join(plot_data.path, 'data_array_marker.npy'), plot_data.marker_time_stamps)
        csv_file = search_file_of_recording(recording, r'marker_time_stamps_.*\.csv')
        add_row_in_csv(csv_file, (nanos, 1))
    else:
        existing_marker = plot_data.marker_time_stamps[existing_marker_index]
        plot_data.marker_time_stamps = np.delete(plot_data.marker_time_stamps, existing_marker_index, 0)
        np.save(os.path.join(plot_data.path, 'data_array_marker.npy'), plot_data.marker_time_stamps)
        csv_file = search_file_of_recording(recording, r'marker_time_stamps_.*\.csv')
        new_csv_data = []
        for old_csv in read_csv(csv_file):
            print(old_csv[0], 'is close to', nanos, np.isclose(old_csv[0], nanos, atol=1500000000))
            if not np.isclose(old_csv[0], nanos, atol=1500000000):
                new_csv_data.append(old_csv)
            else:
                print('found close')

        save_csv(csv_file, new_csv_data)
        #remove_row_in_csv(csv_file, existing_marker_index)
    return jsonify({'status': 'success'})





@view.route('/recording/series/<int:recording_id>/')
def recording_series(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    plot_data = get_plot_data(recording)

    start_point = float(request.args.get('start'))
    end_point = float(request.args.get('end'))

    series = plot_data.get_series(start_point, end_point)

    # series.append(plot_data.annotations)
    # series.append(plot_data.time_stamp_series)

    # print(time_stamp_series)

    return jsonify({'data': {'series': series, 'annotations': prepared_plot_data[recording.id].annotations}})
    # return jsonify({'data': {'series': series}})


@view.route('/recording/np/generate/<int:recording_id>/')
def generate_numpy_data(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    data_factory = DataFactory(recording)
    data_factory.generate_np_sensor_data_file()
    return redirect(url_for('views.get_recording', recording_id=recording.id))


@view.route('/recording/np/delete/<int:recording_id>/')
def delete_numpy_data(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    path = recording.path
    os.remove(os.path.join(path, DataFactory.sensor_data_file_name))
    os.remove(os.path.join(path, DataFactory.sensor_data_flattened_file_name))
    return redirect(url_for('views.get_recording', recording_id=recording.id))


def generate_recording_stats(recording):
    print('Generate stats for recording:', recording.get_name())
    rec_stats = RecordingStats()
    recording.stats = rec_stats

    for evaluation in recording.evaluations[:]:
        recording.evaluations.remove(evaluation)
        db.session.delete(evaluation)


    data_factory = DataFactory(recording, init_all=False)
    data_factory.read_stat_files()
    duration = data_factory.data_processor.sensor_decoder.max_time_stamp - data_factory.data_processor.sensor_decoder.min_time_stamp
    duration *= 0.000000001

    evaluations = data_factory.get_evaluations()
    manual_ts = data_factory.get_manual_hw_ts()

    count_hand_washes_manual = manual_ts.shape[0]
    count_hand_washes_detected_total = 0
    count_evaluation_yes = 0
    count_evaluation_no = 0

    for i in range(evaluations.shape[0]):
        skip = False
        for manual in manual_ts:
            if evaluations[i][0] == manual[0]:
                skip = True
                break
        if skip:
            continue
        count_hand_washes_detected_total += 1
        if evaluations[i][1] == 1:
            count_evaluation_yes += 1
            # if i > 1 and evaluations[i][0] - evaluations[i-1][0] < 25000000000 and evaluations[i-1][1] == 0:
            #     count_hand_washes_detected_total -= 1
        if evaluations[i][1] == -1:
            count_evaluation_no += 1
            # if i > 1 and evaluations[i][0] - evaluations[i - 1][0] < 25000000000 and evaluations[i-1][1] == 0:
            #     count_hand_washes_detected_total -= 1

    count_hand_washes_total = count_evaluation_yes + count_hand_washes_manual

    rec_stats.duration = duration
    rec_stats.count_hand_washes_total = count_hand_washes_total
    rec_stats.count_hand_washes_manual = count_hand_washes_manual
    rec_stats.count_hand_washes_detected_total = count_hand_washes_detected_total
    rec_stats.count_evaluation_yes = count_evaluation_yes
    rec_stats.count_evaluation_no = count_evaluation_no

    for evaluation in evaluations:
        if evaluation[1] == 1:
            rec_evaluation = RecordingEvaluation()
            rec_evaluation.compulsive = bool(evaluation[2])
            rec_evaluation.tense = int(evaluation[3])
            rec_evaluation.urge = int(evaluation[4])
            timestamp = (recording.meta_info.start_time_stamp - evaluation[0]) * nano_sec
            timestamp = recording.meta_info.date + timedelta(seconds=timestamp)
            rec_evaluation.timestamp = timestamp
            recording.evaluations.append(rec_evaluation)
            db.session.add(rec_evaluation)

    db.session.add(rec_stats)

    generate_recording_evaluations_plot(recording)

    return rec_stats


@view.route('/recording/statsupdate/<int:recording_id>/')
@basic_auth.login_required
def update_recording_stats(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    if recording.stats is None:
        generate_recording_stats(recording)
    else:
        rect_stat = recording.stats
        recording.stats = None
        db.session.delete(rect_stat)
        generate_recording_stats(recording)
    db.session.commit()
    return redirect(url_for('views.get_recording', recording_id=recording.id))


def create_recording_calculations(recording):
    data_factory = DataFactory(recording, False)
    variance = data_factory.calc_variance()
    calculations = RecordingCalculations()
    calculations.store_variance(variance)

    recording.calculations = calculations

    db.session.add(calculations)
    db.session.commit()



@view.route('/recording/calc-characteristics/<int:recording_id>/')
@basic_auth.login_required
def calc_recording_characteristics(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    if recording.calculations is None:
        create_recording_calculations(recording)
    else:
        db.session.delete(recording.calculations)
        create_recording_calculations(recording)

    return redirect(url_for('views.get_recording', recording_id=recording.id))


@view.route('/recording/toggle-tag/<int:recording_id>/')
@basic_auth.login_required
def toggle_recording_tag(recording_id):
    recording = Recording.query.filter_by(id=recording_id).first_or_404()
    tag_id = request.args.get('tag_id')
    tag = RecordingTag.query.filter_by(id=tag_id).first_or_404()
    if tag in recording.tags:
        recording.tags.remove(tag)
    else:
        recording.tags.append(tag)

    db.session.commit()

    return redirect(url_for('views.get_recording', recording_id=recording.id))


@view.route('/participant/list/')
@basic_auth.login_required
def list_participants():
    return render_template('list_participants.html', participants=Participant.query.all())

@view.route('/participant/get/<int:participant_id>/')
@basic_auth.login_required
def get_participant(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()
    recordings = participant.get_sorted_recordings()
    participant.update_tag_settings()

    evaluations_plot = None
    evaluations_plot_path = os.path.join(PARTICIPANT_FOLDER, os.path.join(str(participant.id), 'evaluation_graph.png'))
    if os.path.isfile(evaluations_plot_path):
        evaluations_plot = os.path.join(str(participant.id), 'evaluation_graph.png')


    for recording in participant.get_observed_recordings():
        recording.highlight = True


    return render_template('show_participant.html', participant=participant, recordings=recordings, highlight_tags=True, evaluations_plot=evaluations_plot)

@view.route('/participant/update/', methods=['GET', 'POST'])
@view.route('/participant/update/<int:participant_id>/', methods=['GET', 'POST'])
@basic_auth.login_required
def update_participant(participant_id=None):
    participant = None
    if participant_id is not None:
        participant = Participant.query.filter_by(id=participant_id).first_or_404()
    if request.method == 'POST':
        if participant is None:
            participant = Participant(android_id=request.form.get('android_id'))
            participant.check_for_set_active()
            db.session.add(participant)

        participant.android_id = request.form.get('android_id')
        participant.alias = request.form.get('alias')
        participant.is_active = request.form.get('is_active') is not None
        db.session.commit()
        if participant.is_active:
            activate_participant(participant.id)
        print('get participant:', participant.id)
        return redirect(url_for('views.get_participant', participant_id=participant.id))

    alias = ''
    android_id = ''
    is_active = True
    if participant is not None:
        participant_id = participant.id
        alias = participant.alias
        android_id = participant.android_id
        is_active = participant.is_active

    return render_template('edit_participant.html', participant_id=participant_id, alias=alias, android_id=android_id, is_active=is_active)


@view.route('/participant/delete/<int:participant_id>/')
@basic_auth.login_required
def delete_participant(participant_id=None):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()
    db.session.delete(participant)
    db.session.commit()

    return redirect(url_for('views.list_participants'))

@view.route('/participant/assign/<int:participant_id>/')
@basic_auth.login_required
def assign_recordings_to_participant(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()

    start = request.args.get('start')
    end = request.args.get('end')
    ignore_id = request.args.get('ignoreid') == 'true'


    start = datetime.strptime(start, '%d/%m/%Y')
    end = datetime.strptime(end, '%d/%m/%Y')
    end = datetime.combine(end, time.max)

    recordings = db.session.query(Recording).filter(
        and_(Recording.last_changed >= start, Recording.last_changed <= end)
    ).all()

    for recording in recordings:
        if recording.meta_info is not None:
            if not ignore_id and recording.meta_info.android_id != participant.android_id:
                continue
        for old_participant in recording.participants:
            if old_participant != participant:
                old_participant.recordings.remove(recording)
        participant.recordings.append(recording)

    db.session.commit()

    return redirect(url_for('views.get_participant', participant_id=participant.id))


@view.route('/participant/unassign/<int:participant_id>/')
@basic_auth.login_required
def unassign_recordings_to_participant(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()

    start = request.args.get('start')
    end = request.args.get('end')

    start = datetime.strptime(start, '%d/%m/%Y')
    end = datetime.strptime(end, '%d/%m/%Y')
    end = datetime.combine(end, time.max)

    recordings = db.session.query(Recording).filter(
        and_(Recording.last_changed >= start, Recording.last_changed <= end)
    ).filter(Recording.participants.contains(participant)).all()

    for recording in recordings:
        participant.recordings.remove(recording)

    db.session.commit()

    return redirect(url_for('views.get_participant', participant_id=participant.id))


@view.route('/participant/activate/<int:participant_id>/')
@basic_auth.login_required
def activate_participant(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()
    for parti in Participant.query.filter_by(android_id=participant.android_id, is_active=True):
        parti.is_active = False
    participant.is_active = True
    db.session.commit()
    return redirect(url_for('views.get_participant', participant_id=participant.id))

@view.route('/participant/inactivate/<int:participant_id>/')
@basic_auth.login_required
def inactivate_participant(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()
    participant.is_active = False
    db.session.commit()
    return redirect(url_for('views.get_participant', participant_id=participant.id))


@view.route('/participant/statsupdate/<int:participant_id>/')
@basic_auth.login_required
def update_participant_stats(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()

    if participant.stats_id is None:
        stats = ParticipantStats()
        participant.stats = stats
        db.session.add(stats)
    else:
        stats = participant.stats

    stats.clean()

    stats_per_day: Dict[datetime.date, List[RecordingStats]] = dict()


    for recording in participant.get_observed_recordings():
        if recording.stats is None:
            rec_stats = generate_recording_stats(recording)
        else:
            rec_stats = recording.stats
        stats.calc_new_stats(rec_stats)
        if recording.meta_info is not None:
            date = recording.meta_info.date.date()
            if date not in stats_per_day:
                stats_per_day[date] = []
            stats_per_day[date].append(recording.stats)

    stats.calc_daily_stats(stats_per_day)
    db.session.commit()

    participant_update_evaluation_graph(participant.id)

    return redirect(url_for('views.get_participant', participant_id=participant.id))


@view.route('/participant/tag-no-data/<int:participant_id>/')
@basic_auth.login_required
def participant_tag_no_data_recordings(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()

    for recording in participant.recordings:
        if recording.calculations is None:
            create_recording_calculations(recording)
        if np.min(recording.calculations.get_variance()) < config.no_data_variance_threshold:
            tag = RecordingTag.query.filter_by(name='no data').first_or_404()
            if tag not in recording.tags:
                recording.tags.append(tag)

    db.session.commit()
    return redirect(url_for('views.get_participant', participant_id=participant.id))


@view.route('/participant/update-evaluation-graph/<int:participant_id>/')
@basic_auth.login_required
def participant_update_evaluation_graph(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()

    evaluation_averages = dict()
    for day, recordings in participant.get_recordings_per_day().items():
        evaluation_sum = {'compulsive': 0, 'tense': 0, 'urge': 0, 'handwashes': 0}
        count_evaluations = 0
        for recording in recordings:
            for evaluation in recording.evaluations:
                evaluation_sum['compulsive'] += evaluation.compulsive
                evaluation_sum['tense'] += evaluation.tense
                evaluation_sum['urge'] += evaluation.urge
                count_evaluations += 1
            evaluation_sum['handwashes'] += recording.stats.count_hand_washes_total

        if count_evaluations > 0:
            evaluation_sum['compulsive'] /= count_evaluations
            evaluation_sum['tense'] /= count_evaluations
            evaluation_sum['urge'] /= count_evaluations
        evaluation_averages[day] = evaluation_sum

    plot_directory = os.path.join(PARTICIPANT_FOLDER, str(participant.id))
    if not os.path.exists(plot_directory):
        os.mkdir(plot_directory)

    create_participant_evaluation_graph(plot_directory, evaluation_averages)

    return redirect(url_for('views.get_participant', participant_id=participant.id))

@view.route('/tag/list/')
@basic_auth.login_required
def list_tags():
    return render_template('list_tags.html', tags=RecordingTag.query.all())

@view.route('/tag/update/', methods=['GET', 'POST'])
@view.route('/tag/update/<int:tag_id>/', methods=['GET', 'POST'])
@basic_auth.login_required
def update_tag(tag_id=None):
    tag = None
    if tag_id is not None:
        tag = RecordingTag.query.filter_by(id=tag_id).first_or_404()
    if request.method == 'POST':
        if tag is None:
            tag = RecordingTag(name=request.form.get('name'), icon_name=request.form.get('icon_name'),
                               icon_color=request.form.get('icon_color'), description=request.form.get('description'),
                               default_include_for_statistics=request.form.get('default_include_for_statistics'))
            db.session.add(tag)

        tag.name = request.form.get('name')
        tag.icon_name = request.form.get('icon_name')
        tag.icon_color = request.form.get('icon_color')
        tag.description = request.form.get('description')
        tag.default_include_for_statistics = request.form.get('default_include_for_statistics')

        db.session.commit()
        return redirect(url_for('views.list_tags'))

    name = ''
    icon_name = ''
    icon_color = ''
    description = ''
    default_include_for_statistics = True

    if tag is not None:
        name = tag.name
        icon_name = tag.icon_name
        icon_color = tag.icon_color
        description = tag.description
        default_include_for_statistics = tag.default_include_for_statistics

    return render_template('edit_tag.html', tag_id=tag_id, name=name, icon_name=icon_name,
                           icon_color=icon_color, description=description,
                           default_include_for_statistics=default_include_for_statistics)


@view.route('/participant/tagsetting-update/<int:participant_id>/')
@basic_auth.login_required
def update_tag_setting(participant_id):
    participant = Participant.query.filter_by(id=participant_id).first_or_404()
    tag_setting_id = request.args.get('setting_id')
    tag_setting = ParticipantsTagSetting.query.filter_by(id=tag_setting_id, participant=participant).first_or_404()
    tag_setting.next_state()

    return redirect(url_for('views.update_participant_stats', participant_id=participant.id))

@view.route('/tfmodel/get/latest/')
def get_latest_tf_model():
    if not os.path.exists(TFMODEL_FOLDER):
        abort(404, description="Resource not found")

    latest_model = find_newest_tf_file()

    print("found file ", latest_model)
    if latest_model is not None:
        return send_from_directory(TFMODEL_FOLDER, path=latest_model, as_attachment=True)
    abort(404, description="Resource not found")


@view.route('/tfmodel/get/settings/')
def get_latest_tf_model_settings():
    latest_model_settings = get_tf_model_settings_file()

    print("found file ", latest_model_settings)
    if latest_model_settings is not None:
        return send_from_directory(TFMODEL_FOLDER, path=latest_model_settings, as_attachment=True)
    abort(404, description="Resource not found")


@view.route('/tfmodel/check/latest/')
def check_latest_tf_model():
    if not os.path.exists(TFMODEL_FOLDER):
        abort(404, description="Resource not found")

    latest_model = find_newest_tf_file()

    print("found file ", latest_model)
    if latest_model is not None:
        return jsonify({'activeModel': latest_model})
    abort(404, description="Resource not found")


@view.route('/trigger/index_recordings/')
def index_recordings():
    recording_directories = os.listdir(RECORDINGS_FOLDER)
    recording_infos = dict()

    for directory in recording_directories:
        short_description = ""
        long_description = ""
        description_file = os.path.join(RECORDINGS_FOLDER, os.path.join(directory, "README.md"))
        if os.path.exists(description_file):
            with open(description_file, 'r') as desc_file:
                short_description = desc_file.readline()
                long_description = short_description + desc_file.read()
        changed_time_stamp = os.stat(os.path.join(RECORDINGS_FOLDER, directory)).st_ctime

        # since directories creation time changes if a file was edited, we have to find the oldest file within them
        meta_info_file = None
        for file in os.listdir(os.path.join(RECORDINGS_FOLDER, directory)):
            if os.path.splitext(file)[1] == '.json' and 'metaInfo' in file:
                meta_info_file = os.path.join(RECORDINGS_FOLDER, os.path.join(directory, file))
            tmp_c_time = os.stat(os.path.join(RECORDINGS_FOLDER, os.path.join(directory, file))).st_ctime
            if tmp_c_time < changed_time_stamp:
                changed_time_stamp = tmp_c_time

        meta_info = {}
        if meta_info_file is not None:
            with open(meta_info_file) as json_file:
                meta_info = json.load(json_file)
        meta_info['description'] = long_description

        session_size = get_session_size(os.path.join(RECORDINGS_FOLDER, directory))
        change_time_string = datetime.fromtimestamp(changed_time_stamp).strftime('%d/%m/%Y, %H:%M:%S')
        recording_infos[directory] = [change_time_string, changed_time_stamp, short_description,
                                      convert_size(session_size), get_size_color(session_size), meta_info]

        path = os.path.join(RECORDINGS_FOLDER, directory)
        if Recording.query.filter_by(path=path).first() is not None:
            continue
        participant = None
        if 'android_id' in meta_info:
            participant = Participant.query.filter_by(android_id=meta_info['android_id']).first()
            if participant is None:
                participant = Participant(android_id=meta_info['android_id'])
                participant.check_for_set_active()
                db.session.add(participant)


        recording_m = Recording(path=path, description=long_description, last_changed=datetime.fromtimestamp(changed_time_stamp))
        recording_m.session_size = session_size
        meta_info_m = MetaInfo()
        meta_info_m.load_from_dict(meta_info)
        recording_m.meta_info = meta_info_m
        if participant is not None:
            participant.recordings.append(recording_m)

        db.session.add(meta_info_m)
        db.session.add(recording_m)
    db.session.commit()

    return redirect(url_for('views.settings'))


@view.route('/trigger/update_recordings/')
def update_recordings():
    for recording in Recording.query.all():
        recording.update_session_size()
        meta_info_file = None
        for file in os.listdir(recording.path):
            if os.path.splitext(file)[1] == '.json' and 'metaInfo' in file:
                meta_info_file = os.path.join(recording.path, file)

        meta_info = {}
        if meta_info_file is not None:
            with open(meta_info_file) as json_file:
                meta_info = json.load(json_file)

        if recording.meta_info is not None:
            recording.meta_info.load_from_dict(meta_info)

        if RecordingTag.query.filter_by(name='default').filter(RecordingTag.recordings.contains(recording)).first() is None:
            default_tag = RecordingTag.query.filter_by(name='default').first_or_404()
            recording.tags.append(default_tag)

    db.session.commit()
    return redirect(url_for('views.settings'))


@view.route('/trigger/reindex-participants/')
def reindex_participants():
    for participant in Participant.query.all():
        participant.check_for_set_active()
        if participant.stats is not None:
            db.session.delete(participant.stats)
            participant.stats = None
    db.session.commit()
    return redirect(url_for('views.settings'))

@view.route('/trigger/create-default-recording-tags/')
def create_default_tags():

    for tag_name, tag_vals in default_recording_tags.items():
        tag = RecordingTag.query.filter_by(name=tag_name).first()
        if tag is None:
            tag = RecordingTag(name=tag_name, icon_name=tag_vals['icon_name'])
            db.session.add(tag)
        for value_name, value in tag_vals.items():
            setattr(tag, value_name, value)

    db.session.commit()

    update_recordings()
    return redirect(url_for('views.settings'))

@view.route("/auth")
@basic_auth.login_required
def nginx_auth():
    if basic_auth.get_auth():
        return 'Authentication granted'
    else:
        return 'Not authorized', 401


# just for debug
@view.route('/static/<path:path>')
def send_js(path):
    return send_from_directory('js', path)


@view.route('/uploads/recordings/<path:path>')
def uploaded_recording_file(path):
    return send_from_directory(RECORDINGS_FOLDER, path)

@view.route('/uploads/participants/<path:path>')
def uploaded_participant_file(path):
    return send_from_directory(PARTICIPANT_FOLDER, path)

@view.route('/uploads/tf_models/<path:path>')
def uploaded_tf_file(path):
    return send_from_directory(TFMODEL_FOLDER, path)

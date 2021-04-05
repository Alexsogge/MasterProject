import json
import math
import random
import string
from typing import Dict
from zipfile import ZipFile
from datetime import datetime

from flask import Flask, jsonify, request, render_template, redirect, send_from_directory, abort, flash, session
from flask_httpauth import HTTPBasicAuth, HTTPTokenAuth
from werkzeug.utils import secure_filename
from werkzeug.security import generate_password_hash, check_password_hash
import uuid
import os
import shutil

from config import Config
from auth_requests import *

from preview_builder import generate_plot_data, get_data_array
from plot_data import PlotData

UPLOAD_FOLDER = 'uploads'
ALLOWED_EXTENSIONS = {'zip', 'mkv', 'csv', '3gp', 'tflite'}
PACK_MIC_FILES = False

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

RECORDINGS_FOLDER = os.path.join(app.config['UPLOAD_FOLDER'], 'recordings')
TFMODEL_FOLDER = os.path.join(app.config['UPLOAD_FOLDER'], 'tf_models')


basic_auth = HTTPBasicAuth()
token_auth = HTTPTokenAuth('Bearer')

config = Config()
open_auth_requests: AuthRequests = AuthRequests()

prepared_plot_data: Dict[str, PlotData] = dict()

if not os.path.exists(UPLOAD_FOLDER):
    os.mkdir(UPLOAD_FOLDER)

if not os.path.exists(RECORDINGS_FOLDER):
    os.mkdir(RECORDINGS_FOLDER)

if not os.path.exists(TFMODEL_FOLDER):
    os.mkdir(TFMODEL_FOLDER)

def is_allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def generate_random_string(string_length: int):
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=string_length))


@token_auth.verify_token
def verify_token(token):
    print('verify: ', token)
    if token == config.client_secret:
        return 'authenticated'


@basic_auth.verify_password
def verify_password(username, password):
    if username == config.user and password == config.user_pw:
        return username


@app.template_filter()
def is_boolean(input):
    return type(input) is bool or input == 'True' or input == 'False' or input == 'on' or input == 'off'


@app.template_filter()
def render_is_checked(input):
    if bool(input):
        return 'checked'
    return ''


@app.route('/')
def index():
    return render_template('index.html')


@app.route('/tokenauth/request/')
def request_token():
    identifier = request.args.get('identifier')
    if identifier is None or identifier == '':
        return jsonify({'status': 'error', 'msg': 'no identifier'})

    auth_request: AuthRequest = open_auth_requests.get_request(identifier)
    if auth_request is None:
        open_auth_requests.new_request(identifier)
        return jsonify({'status': 'new', 'msg': 'created request'})

    else:
        if not auth_request.granted:
            return jsonify({'status': 'pending', 'msg': 'waiting for confirmation'})
        else:
            open_auth_requests.remove_request(auth_request)
            return jsonify({'status': 'grant', 'token': config.client_secret})


@app.route('/tokenauth/check/')
@basic_auth.login_required
def get_open_auth_requests():
    return render_template('list_requests.html', open_requests=open_auth_requests.open_auth_requests)


@app.route('/settings/', methods=['GET', 'POST'])
@basic_auth.login_required
def settings():

    if request.method == 'POST':
        print('Save new settings')
        settings_values = config.get_config_values().copy()
        for key in settings_values.keys():
            settings_values[key] = request.form.get(key)
            if is_boolean(settings_values[key]):
                settings_values[key] = bool(settings_values[key])
            if settings_values[key] is None and is_boolean(config.get_config_values()[key]):
                settings_values[key] = False
        print(settings_values)
        config.save_config(settings_values)

    return render_template('settings.html', setting_entries=config.get_config_values())



@app.route('/tfmodel/', methods=['GET', 'POST'])
@basic_auth.login_required
def tfmodel():
    upload_info_text = None
    upload_error_text = None
    sensors = {1: 'ACCELEROMETER', 4: 'GYROSCOPE', 2: 'MAGNETIC FIELD', 11: 'ROTATION VECTOR'}

    old_settings = dict()
    old_settings_file = get_tf_model_settings_file()
    if old_settings_file is not None:
        with open(os.path.join(TFMODEL_FOLDER, old_settings_file)) as json_file:
            old_settings = json.load(json_file)



    if request.method == 'POST':
        print('Save new tf model', request.form)
        print('window size', request.form.get('frameSizeInput'))
        if 'file' not in request.files:
            return render_template('tfmodel.html', upload_info_text=upload_info_text, upload_error_text='Error: no file Part', sensors=sensors, old_settings=old_settings)
        file = request.files['file']
        # if user does not select file, browser also
        # submit an empty part without filename
        if file.filename == '':
            return render_template('tfmodel.html', upload_info_text=upload_info_text, upload_error_text='Error: no selected file', sensors=sensors, old_settings=old_settings)
        if file and is_allowed_file(file.filename):
            filename = os.path.splitext(secure_filename(file.filename))[0]

            settings_dict = dict()
            settings_dict['frame_size'] = int(request.form.get('frameSizeInput'))
            settings_dict['positive_prediction_time'] = int(request.form.get('positivePredictionTimeInput'))
            settings_dict['positive_prediction_counter'] = int(request.form.get('positivePredictionCounterInput'))
            required_sensors = request.form.getlist('requiredSensorsSelect')
            for i in range(len(required_sensors)):
                required_sensors[i] = int(required_sensors[i])
            settings_dict['required_sensors'] = required_sensors
            old_settings = settings_dict

            print("settings: ", settings_dict)

            with open(os.path.join(TFMODEL_FOLDER, filename + '.json'), 'w') as outfile:
                json.dump(settings_dict, outfile)
            file.save(os.path.join(TFMODEL_FOLDER, filename + '.tflite'))
            upload_info_text = 'Uploaded ' + filename
        else:
            upload_error_text = 'Error: no valid file'

    return render_template('tfmodel.html', upload_info_text=upload_info_text, upload_error_text=upload_error_text, sensors=sensors, old_settings=old_settings)



@app.route('/tokenauth/grant/<int:auth_id>/')
@basic_auth.login_required
def grant_auth_request(auth_id):
    auth_request = open_auth_requests.get_by_id(auth_id)
    if auth_request is not None:
        auth_request.granted = True
    return redirect('/tokenauth/check/')


@app.route('/recording/list/')
@basic_auth.login_required
def list_recordings():

    recording_directories = os.listdir(RECORDINGS_FOLDER)
    recording_infos = dict()

    for directory in recording_directories:
        short_description = ""
        description_file = os.path.join(RECORDINGS_FOLDER, os.path.join(directory, "README.md"))
        if os.path.exists(description_file):
            short_description = open(description_file, 'r').readline()
        changed_time_stamp = os.stat(os.path.join(RECORDINGS_FOLDER, directory)).st_ctime

        # since directories creation time changes if a file was edited, we have to find the oldest file within them
        for file in os.listdir(os.path.join(RECORDINGS_FOLDER, directory)):
            tmp_c_time = os.stat(os.path.join(RECORDINGS_FOLDER, os.path.join(directory, file))).st_ctime
            if tmp_c_time < changed_time_stamp:
                changed_time_stamp = tmp_c_time

        change_time_string = datetime.fromtimestamp(changed_time_stamp).strftime('%d/%m/%Y, %H:%M:%S')
        recording_infos[directory] = [change_time_string, changed_time_stamp, short_description]

    recordings_sort = sorted(recording_infos.keys(), key=lambda key: recording_infos[key][1], reverse=True)


    # recording_directories = [x[0] for x in os.walk(RECORDINGS_FOLDER)]

    return render_template('list_recordings.html', recordings=recording_infos, sorting=recordings_sort)


@app.route('/recording/get/<string:recording>/')
@basic_auth.login_required
def get_recording(recording):
    recording_files = []
    description = ""
    path = os.path.join(RECORDINGS_FOLDER, recording)
    for file in os.listdir(path):
        if config.hide_mic_files and '.zip' in file and contains_mic_files(file, path):
            continue
        if file == 'README.md':
            description = open(os.path.join(RECORDINGS_FOLDER, os.path.join(recording, "README.md")), 'r').read()
        recording_files.append(file)

    return render_template('show_recording.html', recording_name=recording, files=recording_files,
                           description=description)


@app.route('/recording/plot/<string:recording>/')
@basic_auth.login_required
def plot_recording(recording):
    plot_file = os.path.join(os.path.join(RECORDINGS_FOLDER, recording), 'data_plot.svg')
    if not os.path.exists(plot_file):
        plot_file = os.path.join(os.path.join(RECORDINGS_FOLDER, recording), 'data_plot.png')
        if not os.path.exists(plot_file):
            generate_plot_data(os.path.join(RECORDINGS_FOLDER, recording))

    if os.path.exists(plot_file):
        if recording not in prepared_plot_data.copy():
            get_plot_data(recording)

        return render_template('show_recording_plot.html', recording_name=recording, plot=plot_file)

    return render_template('error_show_recording_plot.html', recording_name=recording)

@app.route('/recording/new/', methods=['GET', 'POST'])
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
            return jsonify({'status': 'success, uploaded ' + file.filename})

        return jsonify({'status': 'success'})
    return jsonify({'status': 'error'})


@app.route('/recording/delete/<string:recording>/')
@basic_auth.login_required
def delete_recording(recording):
    file = os.path.join(RECORDINGS_FOLDER, recording)
    if os.path.exists(file):
        shutil.rmtree(file)
    return redirect('/recording/list/')


@app.route('/recordingfile/delete/<string:recording>/<string:file_name>/')
@basic_auth.login_required
def delete_recording_file(recording, file_name):
    file = os.path.join(RECORDINGS_FOLDER, os.path.join(recording, file_name))
    if os.path.exists(file):
        os.remove(file)
    return redirect(f'/recording/get/{recording}/')


@app.route('/recording/description/<string:recording>/', methods=['GET', 'POST'])
@basic_auth.login_required
def recording_description(recording):
    print('User: ', token_auth.current_user())

    description_file_path = os.path.join(RECORDINGS_FOLDER, os.path.join(recording, "README.md"))

    if request.method == 'GET':
        description_file = open(description_file_path, 'r')
        return jsonify({'description': description_file.read()})
    elif request.method == 'POST':
        print('Get new description')
        new_description = request.form.get('description')
        description_file = open(description_file_path, 'w')
        description_file.write(new_description)
        description_file.close()

    return redirect(f'/recording/get/{recording}/')


@app.route('/recording/data/<string:recording>/')
def recording_data(recording):
    print('User: ', token_auth.current_user())
    plot_data = get_plot_data(recording)

    start_point = float(request.args.get('start'))
    end_point = float(request.args.get('end'))

    series = plot_data.get_series(start_point, end_point)

    # series.append(plot_data.annotations)
    #series.append(plot_data.time_stamp_series)

    # print(time_stamp_series)


    return jsonify({'data': {'series': series, 'annotations': prepared_plot_data[recording].annotations}})
    #return jsonify({'data': {'series': series}})


@app.route('/tfmodel/get/latest/')
def get_latest_tf_model():

    latest_model = None
    latest_time_stamp = 0

    if not os.path.exists(TFMODEL_FOLDER):
        abort(404, description="Resource not found")

    for file in os.listdir(TFMODEL_FOLDER):
        if 'tflite' in file:
            tmp_c_time = os.stat(os.path.join(TFMODEL_FOLDER, file)).st_ctime
            if tmp_c_time > latest_time_stamp:
                latest_time_stamp = tmp_c_time
                latest_model = file

    print("found file ", latest_model)
    if latest_model is not None:
        return send_from_directory(TFMODEL_FOLDER, filename=latest_model, as_attachment=True)
    abort(404, description="Resource not found")


@app.route('/tfmodel/get/settings/')
def get_latest_tf_model_settings():
    latest_model_settings = get_tf_model_settings_file()

    print("found file ", latest_model_settings)
    if latest_model_settings is not None:
        return send_from_directory(TFMODEL_FOLDER, filename=latest_model_settings, as_attachment=True)
    abort(404, description="Resource not found")


def get_tf_model_settings_file():
    latest_model_settings = None
    latest_time_stamp = 0

    if not os.path.exists(TFMODEL_FOLDER):
        return None

    for file in os.listdir(TFMODEL_FOLDER):
        if 'json' in file:
            tmp_c_time = os.stat(os.path.join(TFMODEL_FOLDER, file)).st_ctime
            if tmp_c_time > latest_time_stamp:
                latest_time_stamp = tmp_c_time
                latest_model_settings = file

    return latest_model_settings


@app.route("/auth")
@basic_auth.login_required
def nginx_auth():
    if basic_auth.get_auth():
        return 'Authentication granted'
    else:
        return 'Not authorized', 401


def add_file_to_zip(file_name, directory, directory_uuid):
    if not config.pack_mic_files and '.zip' in file_name and contains_mic_files(file_name, directory):
        return

    zip_file_name = os.path.join(directory, '.' + directory_uuid + '.zip')

    with ZipFile(zip_file_name, 'a') as zip_file:
        sub_file = os.path.join(directory, file_name)
        zip_file.write(sub_file, file_name)
        print('added', file_name, ' to archive')


def contains_mic_files(file, path):
    zip = ZipFile(os.path.join(path, file))
    for containing_file in zip.namelist():
        if 'mic' in containing_file:
            return True
    return False


def get_plot_data(recording):
    if recording not in prepared_plot_data:
        if len(prepared_plot_data) > 2:
            oldest_data = None
            oldest_ts = math.inf
            for key, data in prepared_plot_data.copy().items():
                if data.last_access < oldest_ts:
                    oldest_ts = data.last_access
                    oldest_data = key
                if oldest_data in prepared_plot_data:
                    del prepared_plot_data[oldest_data]
        prepared_plot_data[recording] = PlotData(recording, os.path.join(RECORDINGS_FOLDER, recording))
    return prepared_plot_data[recording]


# just for debug
@app.route('/static/<path:path>')
def send_js(path):
    return send_from_directory('js', path)


@app.route('/uploads/<path:path>')
def send_png(path):
    return send_from_directory('uploads', path)


if __name__ == '__main__':
    app.run()

import random
import string
from zipfile import ZipFile
from datetime import datetime

from flask import Flask, jsonify, request, render_template, redirect
from flask_httpauth import HTTPBasicAuth, HTTPTokenAuth
from werkzeug.utils import secure_filename
from werkzeug.security import generate_password_hash, check_password_hash
import uuid
import os
import shutil

from config import Config
from auth_requests import *

from preview_builder import generate_plot_data, get_data_array

UPLOAD_FOLDER = 'uploads'
ALLOWED_EXTENSIONS = {'zip', 'mkv', 'csv', '3gp'}
PACK_MIC_FILES = False

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

basic_auth = HTTPBasicAuth()
token_auth = HTTPTokenAuth('Bearer')

config = Config()
open_auth_requests = AuthRequests()


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


@app.route('/auth/request/')
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
            return jsonify({'status': 'grant', 'token': config.client_secret})


@app.route('/auth/check/')
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




@app.route('/auth/grant/<int:auth_id>/')
@basic_auth.login_required
def grant_auth_request(auth_id):
    auth_request = open_auth_requests.get_by_id(auth_id)
    if auth_request is not None:
        auth_request.granted = True
    return redirect('/auth/check/')


@app.route('/recording/list/')
@basic_auth.login_required
def list_recordings():

    recording_directories = os.listdir(UPLOAD_FOLDER)
    recording_infos = dict()

    for directory in recording_directories:
        short_description = ""
        description_file = os.path.join(UPLOAD_FOLDER, os.path.join(directory, "README.md"))
        if os.path.exists(description_file):
            short_description = open(description_file, 'r').readline()
        changed_time_stamp = os.stat(os.path.join(UPLOAD_FOLDER, directory)).st_ctime

        # since directories creation time changes if a file was edited, we have to find the oldest file within them
        for file in os.listdir(os.path.join(UPLOAD_FOLDER, directory)):
            tmp_c_time = os.stat(os.path.join(UPLOAD_FOLDER, os.path.join(directory, file))).st_ctime
            if tmp_c_time < changed_time_stamp:
                changed_time_stamp = tmp_c_time

        change_time_string = datetime.fromtimestamp(changed_time_stamp).strftime('%d/%m/%Y, %H:%M:%S')
        recording_infos[directory] = [change_time_string, changed_time_stamp, short_description]

    recordings_sort = sorted(recording_infos.keys(), key=lambda key: recording_infos[key][1], reverse=True)


    # recording_directories = [x[0] for x in os.walk(UPLOAD_FOLDER)]

    return render_template('list_recordings.html', recordings=recording_infos, sorting=recordings_sort)


@app.route('/recording/get/<string:recording>/')
@basic_auth.login_required
def get_recording(recording):
    recording_files = []
    description = ""
    for file in os.listdir(os.path.join(UPLOAD_FOLDER, recording)):
        if '.3gp' in file and config.hide_mic_files:
            continue
        if file == 'README.md':
            description = open(os.path.join(UPLOAD_FOLDER, os.path.join(recording, "README.md")), 'r').read()
        recording_files.append(file)

    return render_template('show_recording.html', recording_name=recording, files=recording_files,
                           description=description)


@app.route('/recording/plot/<string:recording>/')
@basic_auth.login_required
def plot_recording(recording):
    plot_file = os.path.join(os.path.join(UPLOAD_FOLDER, recording), 'data_plot.png')
    if not os.path.exists(plot_file):
        generate_plot_data(os.path.join(UPLOAD_FOLDER, recording))

    if os.path.exists(plot_file):
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
            if os.path.splitext(filename)[1] == '.3gp' and config.rename_mic_files:
                numbering = filename.split('_')[-1]
                filename = generate_random_string(16) + '_' + numbering
            upload_path = os.path.join(app.config['UPLOAD_FOLDER'], request_uuid)
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
    file = os.path.join(UPLOAD_FOLDER, recording)
    if os.path.exists(file):
        shutil.rmtree(file)
    return redirect('/recording/list/')


@app.route('/recordingfile/delete/<string:recording>/<string:file_name>/')
@basic_auth.login_required
def delete_recording_file(recording, file_name):
    file = os.path.join(UPLOAD_FOLDER, os.path.join(recording, file_name))
    if os.path.exists(file):
        os.remove(file)
    return redirect(f'/recording/get/{recording}/')


@app.route('/recording/description/<string:recording>/', methods=['GET', 'POST'])
@basic_auth.login_required
def recording_description(recording):
    print('User: ', token_auth.current_user())

    description_file_path = os.path.join(UPLOAD_FOLDER, os.path.join(recording, "README.md"))

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
    recording_folder = os.path.join(UPLOAD_FOLDER, recording)
    recording_data_array, hand_wash_time_stamps = get_data_array(recording_folder)
    # print(recording_data_array.shape)

    series = []
    for i in range(3):
        series_entry = dict()
        series_entry['name'] = 'axis ' + str(i)

        series_entry['data'] = recording_data_array[:, [0, i+1]].tolist()
        series.append(series_entry)

    annotations = []
    time_stamp_series = dict()
    time_stamp_series['name'] = 'hand wash'
    time_stamp_series['data'] = []
    for i, time_stamp in enumerate(hand_wash_time_stamps[:, 0]):
        time_stamp_series['data'].append({'x': time_stamp, 'y': 13, 'id': f'ts_{i}', 'marker': { 'fillColor': '#BF0B23', 'radius': 10 }})
        annotation_entry = {'type': 'verticalLine', 'typeOptions': {'point': f'ts_{i}'}}
        annotations.append(annotation_entry)

    series.append(time_stamp_series)
    # print(time_stamp_series)


    return jsonify({'data': {'series': series, 'annotations': annotations}})


def add_file_to_zip(file_name, directory, directory_uuid):
    if os.path.splitext(file_name)[1] == '.3gp' and not config.pack_mic_files:
        return

    zip_file_name = os.path.join(directory, directory_uuid + '.zip')

    with ZipFile(zip_file_name, 'a') as zip_file:
        sub_file = os.path.join(directory, file_name)
        zip_file.write(sub_file, file_name)
        print('added', file_name, ' to archive')


if __name__ == '__main__':
    app.run()

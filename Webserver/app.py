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

from preview_builder import generate_plot_data

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
        changed_time_stamp = os.path.getmtime(os.path.join(UPLOAD_FOLDER, directory))
        change_time_string = datetime.fromtimestamp(changed_time_stamp).strftime('%d/%m/%Y, %H:%M:%S')
        recording_infos[directory] = [change_time_string, changed_time_stamp]

    recordings_sort = sorted(recording_infos.keys(), key=lambda key: recording_infos[key][1], reverse=True)


    # recording_directories = [x[0] for x in os.walk(UPLOAD_FOLDER)]

    return render_template('list_recordings.html', recordings=recording_infos, sorting=recordings_sort)


@app.route('/recording/get/<string:recording>/')
@basic_auth.login_required
def get_recording(recording):
    recording_files = [] 
    for file in os.listdir(os.path.join(UPLOAD_FOLDER, recording)):
        if '.3gp' not in file:
            recording_files.append(file)
    

    return render_template('show_recording.html', recording_name=recording, files=recording_files)


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
            if os.path.splitext(filename)[1] == '.3gp':
                filename = generate_random_string(16) + '.3gp'
            upload_path = os.path.join(app.config['UPLOAD_FOLDER'], request_uuid)
            if not os.path.isdir(upload_path):
                os.mkdir(upload_path)
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


def add_file_to_zip(file_name, directory, directory_uuid):
    if not PACK_MIC_FILES and os.path.splitext(file_name)[1] == '.3gp':
        return

    zip_file_name = os.path.join(directory, directory_uuid + '.zip')

    with ZipFile(zip_file_name, 'a') as zip_file:
        sub_file = os.path.join(directory, file_name)
        zip_file.write(sub_file, file_name)
        print('added', file_name, ' to archive')


if __name__ == '__main__':
    app.run()

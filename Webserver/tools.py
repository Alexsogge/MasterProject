import os
import string
import math
from random import random
from zipfile import ZipFile
from typing import Dict

from plot_data import PlotData
from config import ALLOWED_EXTENSIONS, TFMODEL_FOLDER, RECORDINGS_FOLDER, config

prepared_plot_data: Dict[str, PlotData] = dict()


def is_allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def generate_random_string(string_length: int):
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=string_length))


def find_newest_tf_file():
    latest_model = None
    latest_time_stamp = 0
    for file in os.listdir(TFMODEL_FOLDER):
        if 'tflite' in file:
            tmp_c_time = os.stat(os.path.join(TFMODEL_FOLDER, file)).st_ctime
            if tmp_c_time > latest_time_stamp:
                latest_time_stamp = tmp_c_time
                latest_model = file
    return latest_model


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


def add_file_to_zip(file_name, directory, directory_uuid):
    if not config.pack_mic_files and '.zip' in file_name and contains_mic_files(file_name, directory):
        return

    zip_file_name = os.path.join(directory, '.' + directory_uuid + '.zip')

    with ZipFile(zip_file_name, 'a') as zip_file:
        sub_file = os.path.join(directory, file_name)
        zip_file.write(sub_file, file_name)
        print('added', file_name, ' to archive')


def contains_mic_files(file, path):
    zip_file = ZipFile(os.path.join(path, file))
    for containing_file in zip_file.namelist():
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


def convert_size(size_bytes):
    if size_bytes == 0:
        return "0B"
    size_name = ("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    i = int(math.floor(math.log(size_bytes, 1024)))
    p = math.pow(1024, i)
    s = round(size_bytes / p, 2)
    return "%s %s" % (s, size_name[i])

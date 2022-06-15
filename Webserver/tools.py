import json
import os
import shutil
import string
import math
import tempfile
from datetime import timedelta
from random import random
from zipfile import ZipFile, BadZipfile
from typing import Dict
import csv
import re

# from util.ort_format_model.utils import _extract_ops_and_types_from_ort_models as _extract_ops_and_types_from_ort_models
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.dates as mdates

from data_factory import DataFactory
from plot_data import PlotData
from config import ALLOWED_EXTENSIONS, TFMODEL_FOLDER, RECORDINGS_FOLDER, PARTICIPANT_FOLDER, config
from ort_helpers import *

from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from models import Recording, Participant, RecordingEvaluation, RecordingStats

prepared_plot_data: Dict[int, PlotData] = dict()
nano_sec = 0.000000001

to_clean_files = ['.npy', '.png', '.svg']


def is_allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def generate_random_string(string_length: int):
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=string_length))


def find_newest_tf_file():
    latest_model = None
    latest_time_stamp = 0
    for file in os.listdir(TFMODEL_FOLDER):
        if 'tflite' in file or 'ort' in file:
            tmp_c_time = os.stat(os.path.join(TFMODEL_FOLDER, file)).st_ctime
            if tmp_c_time > latest_time_stamp:
                latest_time_stamp = tmp_c_time
                latest_model = file
    return latest_model

def find_newest_torch_file(full_path=False):
    latest_model = None
    latest_time_stamp = 0
    for file in os.listdir(TFMODEL_FOLDER):
        file_ext = os.path.splitext(file)[1]
        if file_ext == '.pt':
            tmp_c_time = os.stat(os.path.join(TFMODEL_FOLDER, file)).st_ctime
            if tmp_c_time > latest_time_stamp:
                latest_time_stamp = tmp_c_time
                latest_model = file
    if not full_path:
        return latest_model
    else:
        return os.path.join(TFMODEL_FOLDER, latest_model)


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
    try:
        if not config.pack_mic_files and '.zip' in file_name and contains_mic_files(file_name, directory):
            return

        zip_file_name = os.path.join(directory, '.' + directory_uuid + '.zip')

        with ZipFile(zip_file_name, 'a') as zip_file:
            sub_file = os.path.join(directory, file_name)
            zip_file.write(sub_file, file_name)
            # print('added', file_name, ' to archive')
    except BadZipfile:
        print('Err:', file_name, 'bad zipfile')

def remove_file_from_zip(zip_path, *filenames):
    tempdir = tempfile.mkdtemp()
    try:
        tempname = os.path.join(tempdir, 'new.zip')
        with ZipFile(zip_path, 'r') as zipread:
            with ZipFile(tempname, 'w') as zipwrite:
                for item in zipread.infolist():
                    if item.filename not in filenames:
                        data = zipread.read(item.filename)
                        zipwrite.writestr(item, data)
        shutil.move(tempname, zip_path)
    finally:
        shutil.rmtree(tempdir)


def contains_mic_files(file, path):
    zip_file = ZipFile(os.path.join(path, file))
    for containing_file in zip_file.namelist():
        if 'mic' in containing_file:
            return True
    return False


def get_plot_data(recording: 'Recording'):
    if recording.id not in prepared_plot_data:
        if len(prepared_plot_data) > 2:
            oldest_data = None
            oldest_ts = math.inf
            for key, data in prepared_plot_data.copy().items():
                if data.last_access < oldest_ts:
                    oldest_ts = data.last_access
                    oldest_data = key
                if oldest_data in prepared_plot_data:
                    del prepared_plot_data[oldest_data]
        prepared_plot_data[recording.id] = PlotData(recording)
    return prepared_plot_data[recording.id]


def convert_size(size_bytes):
    if size_bytes == 0:
        return "0B"
    size_name = ("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    i = int(math.floor(math.log(size_bytes, 1024)))
    p = math.pow(1024, i)
    s = round(size_bytes / p, 2)
    return "%s %s" % (s, size_name[i])


def get_session_size(path):
    total_size = 0
    for file in os.listdir(path):
        if is_allowed_file(file):
            total_size += os.path.getsize(os.path.join(path, file))
    return total_size


def get_size_color(size):
    if size < config.session_size_red:
        return 'red'
    if size < config.session_size_orange:
        return 'orange'
    return 'black'


def clean_session_directory(path):
    for file in os.listdir(path):
        if os.path.splitext(file)[1] in to_clean_files:
            os.remove(os.path.join(path, file))


def extract_ops_from_ort_model(file):
    buffer = bytearray(file)
    model = InferenceSession.GetRootAsInferenceSession(buffer, 0).Model()
    graph = model.Graph()
    return process_graph(graph)

def process_graph(graph):
    '''
    Process one level of the Graph, descending into any subgraphs when they are found
    :param outer_scope_value_typeinfo: Outer scope NodeArg dictionary from ancestor graphs
    '''
    # Merge the TypeInfo for all values in this level of the graph with the outer scope value TypeInfo.

    required_optypes = set()
    for i in range(0, graph.NodesLength()):
        node = graph.Nodes(i)
        optype = node.OpType().decode()
        required_optypes.add(optype)

    return required_optypes



def check_valid_ort_model(file):
    # required_ops, op_type_processors = _extract_ops_and_types_from_ort_models(path, True)
    #print(required_ops)
    required_optypes = extract_ops_from_ort_model(file)
    missing_optypes = set()
    for op_type in required_optypes:
        if op_type not in config.available_optypes:
            missing_optypes.add(op_type)
    return missing_optypes


def get_time_offset(recording: 'Recording'):
    time_offset = "1970-01-01T00:00:00.000Z"
    meta_info = recording.meta_info
    if meta_info is not None and meta_info.date is not None:
        time_offset = meta_info.date
    return time_offset


def search_file_of_recording(recording: 'Recording', search_query):
    rec_path = recording.path
    regex = re.compile(search_query)
    for file in os.listdir(rec_path):
        if regex.search(file):
            print("found file:", file)
            return os.path.join(rec_path, file)
    return None


def save_csv(path, data: np.ndarray):
    with open(path, 'w', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
        csv_writer.writerows(data)


def read_csv(path):
    lines = []
    with open(path, 'r', newline='') as inp:
        for i, row in enumerate(csv.reader(inp, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)):
          lines.append(row)
    return lines


def add_row_in_csv(path, row):
    with open(path, 'a', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
        csv_writer.writerow(row)


def remove_row_in_csv(path, row_index):
    old_lines = []
    with open(path, 'r', newline='') as inp:
        for i, row in enumerate(csv.reader(inp, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)):
            if i != row_index:
                old_lines.append(row)
    with open(path, 'w', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
        for row in old_lines:
            csv_writer.writerow(row)


def create_participant_evaluation_graph(directory, evaluations):
    fig, ax = plt.subplots(figsize=(20, 7))


    if len(evaluations) < 2:
        return
    day_range = sorted(evaluations)[-1] - sorted(evaluations)[0]
    day_range = int(day_range.days / 5)

    if day_range < 1:
        day_range = 1

    plt.gca().xaxis.set_major_formatter(mdates.DateFormatter('%d/%m/%Y'))
    plt.gca().xaxis.set_major_locator(mdates.DayLocator(interval=day_range))
    plt.rcParams.update({'font.size': 22})


    compulsive = [evaluations[evaluation_key]['compulsive'] for evaluation_key in sorted(evaluations)]
    tense = [evaluations[evaluation_key]['tense'] for evaluation_key in sorted(evaluations)]
    urge = [evaluations[evaluation_key]['urge'] for evaluation_key in sorted(evaluations)]

    hand_washes = [evaluations[evaluation_key]['handwashes'] for evaluation_key in sorted(evaluations)]

    ax.plot(sorted(evaluations), np.array(compulsive)*5, label='compulsive * 5')
    ax.plot(sorted(evaluations), np.array(tense), label='tense')
    ax.plot(sorted(evaluations), np.array(urge), label='urge')
    ax.plot(sorted(evaluations), np.array(hand_washes), label='hand washes', alpha=0.5)


    for item in ax.get_xticklabels():
        item.set_fontsize(22)
    for item in ax.get_yticklabels():
        item.set_fontsize(22)
    ax.grid()
    fig.tight_layout()
    fig.legend()
    plt.gcf().autofmt_xdate()
    fig.savefig(os.path.join(directory, 'evaluation_graph.png'), dpi=400)


def generate_recording_evaluations_plot(recording: 'Recording'):
    if len(recording.evaluations) == 0:
        return

    fig, ax = plt.subplots(figsize=(20, 7))
    time_formatter = mdates.DateFormatter('%H:%M')
    ax.xaxis.set_major_formatter(time_formatter)
    plt.rcParams.update({'font.size': 22})

    x_vals = []
    y_vals_compulsive = []
    y_vals_tense = []
    y_vals_urge = []

    for evaluation in recording.evaluations:
        x_vals.append(evaluation.timestamp)
        y_vals_compulsive.append(evaluation.compulsive * 5)
        y_vals_tense.append(evaluation.tense)
        y_vals_urge.append(evaluation.urge)

    ax.scatter(x_vals, y_vals_compulsive, label='compulsive * 5', s=300, marker='x')
    ax.scatter(x_vals, y_vals_tense, label='tense', s=300, marker='p')
    ax.scatter(x_vals, y_vals_urge, label='urge', s=300, marker='>')


    for item in ax.get_xticklabels():
        item.set_fontsize(22)
    for item in ax.get_yticklabels():
        item.set_fontsize(22)
    ax.grid()
    fig.tight_layout()
    fig.legend()
    plt.gcf().autofmt_xdate()
    fig.savefig(os.path.join(recording.path, 'evaluation_graph.png'), dpi=400)
    fig.clear()
    plt.close()

if __name__ == '__main__':
    check_valid_ort_model('/tmp/model_trained_lstm_16_09_21___12_49_08.all.ort')

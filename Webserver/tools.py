import os
import string
import math
from random import random
from zipfile import ZipFile
from typing import Dict

# from util.ort_format_model.utils import _extract_ops_and_types_from_ort_models as _extract_ops_and_types_from_ort_models

from plot_data import PlotData
from config import ALLOWED_EXTENSIONS, TFMODEL_FOLDER, RECORDINGS_FOLDER, config
from ort_helpers import *

prepared_plot_data: Dict[str, PlotData] = dict()


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


def extract_ops_from_ort_model(path):
    file = open(path, 'rb').read()
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



def check_valid_ort_model(path):
    # required_ops, op_type_processors = _extract_ops_and_types_from_ort_models(path, True)
    #print(required_ops)
    required_optypes = extract_ops_from_ort_model(path)
    missing_optypes = set()
    for op_type in required_optypes:
        if op_type not in config.available_optypes:
            missing_optypes.add(op_type)
    return missing_optypes


if __name__ == '__main__':
    check_valid_ort_model('/tmp/model_trained_lstm_16_09_21___12_49_08.all.ort')

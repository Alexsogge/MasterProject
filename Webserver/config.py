from typing import List

from yaml import load, dump
import random
import string
import os


version = 1.5
secret_key_iterations = 42

ALLOWED_EXTENSIONS = {'zip', 'mkv', 'csv', '3gp', 'tflite', 'json', 'avi', 'ort'}
UPLOAD_FOLDER = 'uploads'

RECORDINGS_FOLDER = os.path.join(UPLOAD_FOLDER, 'recordings')
TFMODEL_FOLDER = os.path.join(UPLOAD_FOLDER, 'tf_models')

config_values = {'version': version, 'upload_directory': 'uploads', 'url_prefix': '',
                 'client_secret': '', 'user': 'admin', 'user_pw': 'admin', 'session_size_red': 100000,
                 'session_size_orange': 1000000, 'hide_mic_files': True, 'rename_mic_files': True,
                 'pack_mic_files': False,
                 'available_optypes': ['Softmax', 'Gather', 'Shape', 'Gemm', 'Unsqueeze', 'Concat', 'Reshape',
                                       'FusedGemm', 'Squeeze', 'Expand', 'LSTM']}

class Config:
    config_file = 'conf/config.yml'
    file_encoding = 'utf-8'

    upload_directory: str = None
    url_prefix: str = ''
    client_secret: str = None

    user: str = None
    user_pw: str = None
    session_size_red = None
    session_size_orange = None

    hide_mic_files: bool = None
    rename_mic_files: bool = None
    pack_mic_files: bool = None
    available_optypes: List = None

    def __init__(self):
        print('load config')
        self.read()

    def read(self):
        global version
        if os.path.exists(self.config_file):
            old_values = dict()
            with open(self.config_file, 'r+') as config_file:
                config_yml = load(config_file)
                for key, value in config_yml.items():
                    if key == 'version':
                        continue
                    if hasattr(self, key):
                        setattr(self, key, value)
                        old_values[key] = value

                if config_yml['version'] != version:
                    self.create(old_values)
        else:
            self.create({})

    def get_config_values(self, get_all=False):
        global config_values
        hide_values = ['upload_directory']
        my_values = dict()
        for key in config_values.keys():
            if key == 'version' or not get_all and key in hide_values:
                continue
            my_values[key] = getattr(self, key)
        return my_values

    def create(self, overwrite_values):
        global secret_key_iterations
        global config_values
        print('create config ', self.config_file)
        if not os.path.exists(os.path.dirname(self.config_file)):
            os.makedirs(os.path.dirname(self.config_file))
        with open(self.config_file, 'w', encoding=self.file_encoding) as config_file:
            local_config = config_values.copy()
            new_secret_token = ''.join(random.SystemRandom().choice(string.ascii_uppercase +
                                                                    string.ascii_lowercase +
                                                                    string.digits
                                                                    ) for _ in range(secret_key_iterations))
            local_config['client_secret'] = new_secret_token

            for key, value in overwrite_values.items():
                if key in local_config:
                    local_config[key] = value

            dump(local_config, config_file)
        self.read()

    def save_config(self, config_entries: dict):
        old_config = self.get_config_values(True)
        old_config.update(config_entries)
        self.create(old_config)


config = Config()


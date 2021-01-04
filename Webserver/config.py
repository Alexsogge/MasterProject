from yaml import load, dump
import random
import string
import os

version = 1.0
secret_key_iterations = 42

config_values = {'version': version, 'upload_directory': 'uploads',
                 'client_secret': '', 'user': 'admin', 'user_pw': ''}

class Config:
    config_file = './config.yml'
    file_encoding = 'utf-8'

    upload_directory = None
    client_secret = None

    user = None
    user_pw = None


    def __init__(self):
        print('load config')
        self.read()

    def read(self):
        global version
        if os.path.exists(self.config_file):
            with open(self.config_file, 'r+') as config_file:
                config_yml = load(config_file)

                for key, value in config_yml.items():
                    if key == 'version':
                        continue
                    if hasattr(self, key):
                        setattr(self, key, value)

                if config_yml['version'] != version:
                    self.create()
        else:
            self.create()

    def create(self):
        global secret_key_iterations
        print('create config ', self.config_file)
        with open(self.config_file, 'w', encoding=self.file_encoding) as config_file:
            local_config = config_values.copy()
            new_secret_token = ''.join(random.SystemRandom().choice(string.ascii_uppercase +
                                                                    string.ascii_lowercase +
                                                                    string.digits
                                                                    ) for _ in range(secret_key_iterations))
            local_config['client_secret'] = new_secret_token
            new_user_pw = input("set new user pw: ")
            local_config['user_pw'] = new_user_pw
            dump(local_config, config_file)






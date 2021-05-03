from flask_httpauth import HTTPBasicAuth, HTTPTokenAuth

from auth_requests import AuthRequests
from config import config

basic_auth = HTTPBasicAuth()
token_auth = HTTPTokenAuth('Bearer')

open_auth_requests: AuthRequests = AuthRequests()


@token_auth.verify_token
def verify_token(token):
    print('verify: ', token)
    if token == config.client_secret:
        return 'authenticated'


@basic_auth.verify_password
def verify_password(username, password):
    if username == config.user and password == config.user_pw:
        return username

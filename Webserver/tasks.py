from flask_apscheduler import APScheduler
from models import *

scheduler = APScheduler()

UPDATE_USER_STATS_TASK_ID = 'update-user-statistics-task-id'


def update_user_statistics_task():
    pass

def run_tasks():
    scheduler.add_job(id=UPDATE_USER_STATS_TASK_ID, func=update_user_statistics_task, trigger='interval', minutes=5)

def init_scheduler(app):
    scheduler.init_app(app)
    scheduler.start()
    run_tasks()



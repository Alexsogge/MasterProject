from app import db, app
from data_factory import DataFactory
from models import Recording, RecordingCalculations
import numpy as np

import sys

def create_db():
    with app.app_context():
        db.create_all()


def calc_recording_characteristics():
    with app.app_context():
        print('Start calculation...')
        for recording in Recording.query.all():
            if recording.calculations is None:
                print('Calc characteristics for', recording.get_name())
                try:
                    data_factory = DataFactory(recording, False)
                    variance = data_factory.calc_variance()
                except Exception as e:
                    print('Error while reading file. Set Dummy entry')
                    variance = np.array([0.0, 0.0, 0.0])
                calculations = RecordingCalculations()
                calculations.store_variance(variance)

                recording.calculations = calculations

                db.session.add(calculations)
                db.session.commit()
            else:
                print('skip', recording.get_name())

if __name__ == '__main__':
    if len(sys.argv) == 1:
        print('usage: manage.py <command>')
    else:
        if sys.argv[1] == 'create_db':
            create_db()

        if sys.argv[1] == 'calc_characteristics':
            calc_recording_characteristics()
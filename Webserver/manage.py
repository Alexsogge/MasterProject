from app import db, app
from data_factory import DataFactory
from models import Recording, RecordingCalculations, Participant
import numpy as np
from views import generate_recording_stats

import sys
import argparse

arg_parser = argparse.ArgumentParser(description='Execute manual operations')

arg_parser.add_argument('Action',
                        metavar='action_name',
                        type=str,
                        help='name of action',
                        choices=['create_db', 'calc_characteristics', 'build_personalized_models', 'rerun_tests',
                                 'recreate_stats'])

arg_parser.add_argument('-p', '--participants',
                        dest='participants',
                        nargs='+',
                        help='specify participants')

arg_parser.add_argument('-b', '--best',
                        dest='use_best',
                        action='store_true',
                        help='use best personalization as base')

arg_parser.add_argument('-f', '--filter',
                        dest='filter',
                        default='alldeepconv_correctbyconvlstm3filter6',
                        help='specify pseudo label filter setting')

arg_parser.add_argument('-ur', '--use_regularization',
                        dest='use_regularization',
                        action='store_true',
                        help='use L2-SP regularization')



args = arg_parser.parse_args()

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


def build_personalized_models():
    with app.app_context():
        if args.participants:
            observed_participants = args.participants
            observed_participants = [int(part_id) for part_id in observed_participants]
            print('Start personalization for ', observed_participants)
        else:
            observed_participants = None
            print('Start personalization...')

        for participant in Participant.query.filter_by(is_active=True, enable_personalization=True):
            if observed_participants is None or participant.id in observed_participants:
                print(participant.get_name())
                participant.run_personalization(target_filter=args.filter, use_best=args.use_best,
                                                use_regularization=args.use_regularization)


def rerun_tests_on_personalizations():
    with app.app_context():
        if args.participants:
            observed_participants = args.participants
            observed_participants = [int(part_id) for part_id in observed_participants]
            print('Start rerun tests for ', observed_participants)
        else:
            observed_participants = None
            print('Start rerun tests...')

        for participant in Participant.query.filter_by(is_active=True, enable_personalization=True):
            if observed_participants is None or participant.id in observed_participants:
                print(participant.get_name())
                participant.rerun_tests_on_personalization()


def recreate_recording_stats():
    with app.app_context():
        for recording in Recording.query.all():
            if recording.stats is not None:
                db.session.delete(recording.stats)
                rec_stats = generate_recording_stats(recording, db)
                recording.stats = rec_stats
        db.session.commit()


if __name__ == '__main__':
    if len(sys.argv) == 1:
        print('usage: manage.py <command>')
    else:
        print('execute:', sys.argv)
        if args.Action == 'create_db':
            create_db()

        if args.Action == 'calc_characteristics':
            calc_recording_characteristics()

        if args.Action == 'build_personalized_models':
            build_personalized_models()

        if args.Action == 'rerun_tests':
            rerun_tests_on_personalizations()

        if args.Action == 'recreate_stats':
            recreate_recording_stats()
